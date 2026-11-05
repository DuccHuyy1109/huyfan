package com.hankukko.huyfan.voice

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.hankukko.huyfan.data.AppSettings
import com.hankukko.huyfan.data.TtsMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** 1 lựa chọn giọng của máy (Google/Samsung TTS). */
data class VoiceOption(val name: String, val label: String)

/**
 * Đọc văn bản song ngữ.
 * - Giọng máy: 2 bộ đọc riêng (tiếng Việt / tiếng Hàn), tách câu thành đoạn Hangul / không Hangul
 *   rồi đọc NỐI TIẾP từng đoạn bằng đúng bộ đọc → chạy được cả với Samsung TTS (vốn bỏ qua lệnh đổi ngôn ngữ giữa chừng).
 * - Giọng AI (Gemini TTS): 1 giọng tự nhiên đọc liền cả Việt lẫn Hàn; lỗi/chậm → tự dùng giọng máy.
 */
class Speaker(
    private val context: Context,
    private val settings: StateFlow<AppSettings>,
    http: OkHttpClient,
    private val scope: CoroutineScope,
) {
    private val main = Handler(Looper.getMainLooper())
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()
    private val ids = AtomicInteger()
    private var chainToken = 0

    private val aiTts = GeminiTts(http)
    private val player = PcmPlayer()
    private var aiJob: Job? = null
    private val aiCache = object : LinkedHashMap<String, Pcm>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pcm>?) = size > 24
    }

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking

    /** TTS đã sẵn sàng chưa – màn Cài đặt dựa vào đây để kiểm tra giọng đọc. */
    private val _ready = MutableStateFlow(false)
    val readyState: StateFlow<Boolean> = _ready

    private val vi = Locale("vi", "VN")
    /** Giọng của ngôn ngữ đang học (Hàn hoặc Anh) */
    private val ko = com.hankukko.huyfan.Lang.current.ttsLocale

    /** 1 bộ đọc gắn với 1 ngôn ngữ. */
    private inner class Engine(val locale: Locale) {
        var tts: TextToSpeech? = null
        var ready = false
        var engineName: String? = null
        var voiceName: String = ""
        /** Đã tự chuyển sang Google vì bộ đọc được chọn không có ngôn ngữ này */
        var fellBack = false
        private var requested: String? = null

        /** Người dùng chọn bộ đọc [engine]; nếu nó không có ngôn ngữ này thì tự dùng Google. */
        fun ensure(engine: String, voice: String) {
            if (requested == engine && tts != null) {
                // Cùng lựa chọn → chỉ cập nhật giọng. Khi đã tự chuyển sang Google, danh sách giọng trong
                // Cài đặt chính là giọng Google → vẫn áp; tên không có trong bộ đọc thì applyVoice tự dùng mặc định.
                if (voiceName != voice) { voiceName = voice; if (ready) applyVoice() }
                return
            }
            requested = engine
            fellBack = false
            start(engine, voice)
        }

        private fun start(engine: String, voice: String) {
            tts?.shutdown()
            ready = false
            engineName = engine
            voiceName = voice
            tts = TextToSpeech(context.applicationContext, { status ->
                main.post {
                    ready = status == TextToSpeech.SUCCESS
                    val avail = if (ready) tts?.isLanguageAvailable(locale) ?: -2 else -2
                    Log.i(TAG, "init ${locale.language} engine=${engine.ifBlank { "default" }} status=$status lang=$avail")
                    // Bộ đọc không có ngôn ngữ này (vd Samsung TTS không có tiếng Việt → đọc bằng giọng Anh)
                    // → tự chuyển riêng ngôn ngữ này sang Google TTS
                    val google = preferredEngine()
                    if (ready && avail < TextToSpeech.LANG_AVAILABLE && google.isNotBlank() && engine != google) {
                        Log.i(TAG, "${locale.language}: $engine không hỗ trợ → dùng $google")
                        fellBack = true
                        start(google, voice)
                        return@post
                    }
                    if (ready) {
                        tts?.setOnUtteranceProgressListener(progress)
                        applyVoice()
                    }
                    updateReady()
                }
            }, engine.ifBlank { null })
        }

        fun applyVoice() {
            val t = tts ?: return
            val v = if (voiceName.isBlank()) null else runCatching { t.voices?.firstOrNull { it.name == voiceName } }.getOrNull()
            val r = if (v != null) t.setVoice(v) else t.setLanguage(locale)
            Log.i(TAG, "${locale.language} voice want='${voiceName.ifBlank { "mặc định" }}' set=$r now='${runCatching { t.voice?.name }.getOrNull()}'" +
                (if (v?.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true) " (chưa tải dữ liệu giọng)" else ""))
        }

        fun available(): Boolean = ready && (tts?.isLanguageAvailable(locale) ?: -2) >= TextToSpeech.LANG_AVAILABLE

        fun voices(): List<Voice> = runCatching {
            tts?.voices.orEmpty().filter { it.locale.language == locale.language }
                .sortedWith(compareBy<Voice>({ it.isNetworkConnectionRequired }, { it.name }))
        }.getOrDefault(emptyList())
    }

    private val viE = Engine(vi)
    private val koE = Engine(ko)

    private fun updateReady() {
        _ready.value = viE.ready && koE.ready
    }

    fun init() {
        val s = settings.value
        val def = preferredEngine()
        viE.ensure(s.viTtsEngine.ifBlank { s.ttsEngine.ifBlank { def } }, s.viVoice)
        koE.ensure(s.koTtsEngine.ifBlank { s.ttsEngine.ifBlank { def } }, s.koVoice)
    }

    private fun preferredEngine(): String {
        val google = "com.google.android.tts"
        return runCatching {
            context.packageManager.getPackageInfo(google, 0)
            google
        }.getOrDefault("")
    }

    fun engines(): List<Pair<String, String>> =
        (viE.tts ?: koE.tts)?.engines?.map { it.name to it.label }.orEmpty()

    /** Danh sách giọng của bộ đọc đang chọn cho từng ngôn ngữ. */
    fun voiceOptions(korean: Boolean): List<VoiceOption> {
        val list = (if (korean) koE else viE).voices()
        return list.mapIndexed { i, v ->
            val tags = buildList {
                add(if (v.isNetworkConnectionRequired) "cần mạng" else "offline")
                if (v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true) add("chưa tải")
            }
            // Google: vi-vn-x-gft-local → "gft"; Samsung: ko-KR-SMTf00 → "SMTf00"
            val code = v.name.split('-', '#', '_').firstOrNull { p ->
                p.length in 3..8 && p.lowercase() !in setOf("local", "network", "language") && p.lowercase() != locale(korean)
            }.orEmpty()
            VoiceOption(v.name, "Giọng ${i + 1}" + (if (code.isNotBlank()) " ($code)" else "") + " · ${tags.joinToString(" · ")}")
        }
    }

    private fun locale(korean: Boolean) = if (korean) ko.language else "vi"

    /** Trả về (có giọng Việt, có giọng Hàn). */
    fun languageSupport(): Pair<Boolean, Boolean> = viE.available() to koE.available()

    fun installVoiceDataIntent(): Intent =
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            _speaking.value = true
        }

        override fun onDone(utteranceId: String?) = fire(utteranceId)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = fire(utteranceId)

        override fun onError(utteranceId: String?, errorCode: Int) {
            Log.w(TAG, "utterance $utteranceId error $errorCode")
            fire(utteranceId)
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            if (utteranceId != null) callbacks.remove(utteranceId)
        }
    }

    private fun fire(id: String?) {
        // Câu nghe thử không có callback → tự báo đã đọc xong
        if (id == "preview") { _speaking.value = false; return }
        val cb = id?.let { callbacks.remove(it) } ?: return
        main.post { cb() }
    }

    // ------------------------------------------------------------------ Đọc

    /** Đọc câu trả lời; onDone luôn được gọi đúng 1 lần (kể cả khi lỗi), trừ khi bị stop(). */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        main.post {
            stopInternal()
            val token = ++chainToken
            val s = settings.value
            val cleaned = clean(text)
            if (cleaned.isBlank()) { onDone?.invoke(); return@post }
            if (s.ttsMode == TtsMode.AI && s.geminiKey.isNotBlank()) {
                speakAi(cleaned, s, token, onDone)
            } else {
                speakDevice(cleaned, token, slowKorean = false, onDone)
            }
        }
    }

    /**
     * Đọc CẢ câu bằng 1 giọng (không tách theo chữ): dùng cho bản dịch – câu tiếng Hàn có "90%", "K"…
     * vẫn đọc bằng giọng Hàn ("구십 퍼센트") để người Hàn nghe hiểu; câu tiếng Việt đọc giọng Việt.
     */
    fun speakAs(text: String, korean: Boolean, onDone: (() -> Unit)? = null) {
        main.post {
            stopInternal()
            val token = ++chainToken
            val s = settings.value
            val cleaned = clean(text)
            if (cleaned.isBlank()) { onDone?.invoke(); return@post }
            if (s.ttsMode == TtsMode.AI && s.geminiKey.isNotBlank()) {
                speakAi(cleaned, s, token, onDone)
                return@post
            }
            _speaking.value = true
            val segs = listOf(cleaned to korean)
            val ready = if (korean) koE.ready else viE.ready
            if (!ready) {
                init()
                waitReady(token, 0) { playChain(segs, 0, token, slowKorean = false, onDone) }
            } else {
                playChain(segs, 0, token, slowKorean = false, onDone)
            }
        }
    }

    /** Đọc riêng tiếng Hàn (bấm loa trên thẻ từ vựng) bằng giọng máy cho tức thì, chậm hơn chút để nghe rõ. */
    fun speakKorean(text: String) {
        main.post {
            stopInternal()
            val token = ++chainToken
            _speaking.value = true
            val segs = listOf(text.trim() to true)
            if (!koE.ready) {
                init()
                waitReady(token, 0) { playChain(segs, 0, token, slowKorean = true, onDone = null) }
            } else {
                playChain(segs, 0, token, slowKorean = true, onDone = null)
            }
        }
    }

    /** Nghe thử 1 giọng máy. */
    fun previewDevice(korean: Boolean, voice: String) {
        main.post {
            stopInternal()
            val e = if (korean) koE else viE
            val t = e.tts ?: return@post
            val v = runCatching { t.voices?.firstOrNull { it.name == voice } }.getOrNull()
            val r = if (v != null) t.setVoice(v) else t.setLanguage(e.locale)
            Log.i(TAG, "preview ${e.locale.language} '$voice' set=$r features=${v?.features}")
            t.setSpeechRate(settings.value.speechRate)
            t.speak(if (korean) (if (com.hankukko.huyfan.Lang.isEn) "Hello! Nice to meet you." else "안녕하세요! 만나서 반가워요.") else "Xin chào, HUY FAN đây. Hôm nay học gì nào?", TextToSpeech.QUEUE_FLUSH, null, "preview")
            // Trả lại giọng đang chọn cho lần đọc sau
            main.postDelayed({ e.applyVoice() }, 300)
        }
    }

    /** Nghe thử 1 giọng AI. onResult(null) = thành công, khác null = câu báo lỗi. */
    fun previewAi(voice: String, onResult: (String?) -> Unit) {
        main.post {
            stopInternal()
            val token = ++chainToken
            val s = settings.value
            if (s.geminiKey.isBlank()) { onResult("Cần Gemini API key trước"); return@post }
            _speaking.value = true
            aiJob = scope.launch {
                val text = "Xin chào, HUY FAN đây! 안녕하세요! Hôm nay mình học tiếng Hàn nhé."
                val r = runCatching { aiTts.synth(s.geminiBaseUrl, s.geminiKey, text, voice) }
                if (token != chainToken) return@launch
                r.onSuccess { pcm ->
                    player.play(pcm, s.speechRate) { main.post { _speaking.value = false } }
                    main.post { onResult(null) }
                }.onFailure { e ->
                    main.post { _speaking.value = false; onResult(e.message ?: "Lỗi giọng AI") }
                }
            }
        }
    }

    private fun speakAi(text: String, s: AppSettings, token: Int, onDone: (() -> Unit)?) {
        _speaking.value = true
        val key = s.aiVoice + "|" + text
        aiJob = scope.launch {
            val cached = synchronized(aiCache) { aiCache[key] }
            val pcm = cached ?: withTimeoutOrNull(AI_TIMEOUT_MS) {
                runCatching { aiTts.synth(s.geminiBaseUrl, s.geminiKey, text, s.aiVoice) }
                    .onFailure { Log.w(TAG, "AI TTS failed: ${it.message}") }
                    .getOrNull()
            }
            if (token != chainToken) return@launch
            if (pcm != null) {
                synchronized(aiCache) { aiCache[key] = pcm }
                player.play(pcm, s.speechRate) {
                    main.post { if (token == chainToken) { _speaking.value = false; onDone?.invoke() } }
                }
            } else {
                Log.i(TAG, "AI TTS chậm/lỗi → dùng giọng máy")
                main.post { if (token == chainToken) speakDevice(text, token, slowKorean = false, onDone) }
            }
        }
    }

    private fun speakDevice(text: String, token: Int, slowKorean: Boolean, onDone: (() -> Unit)?) {
        val segments = segment(text)
        if (segments.isEmpty()) { onDone?.invoke(); return }
        Log.i(TAG, "speak ${segments.joinToString(" | ") { (s, k) -> (if (k) "KO:" else "VI:") + s.take(30) }}")
        _speaking.value = true
        if (!viE.ready && !koE.ready) {
            init()
            // Chờ bộ đọc khởi động tối đa ~4 s
            waitReady(token, 0) { playChain(segments, 0, token, slowKorean, onDone) }
            return
        }
        playChain(segments, 0, token, slowKorean, onDone)
    }

    private fun waitReady(token: Int, tries: Int, then: () -> Unit) {
        if (token != chainToken) return
        if (viE.ready || koE.ready || tries >= 13) { then(); return }
        main.postDelayed({ waitReady(token, tries + 1, then) }, 300)
    }

    /** Đọc nối tiếp từng đoạn bằng đúng bộ đọc của ngôn ngữ đó. */
    private fun playChain(
        segs: List<Pair<String, Boolean>>,
        idx: Int,
        token: Int,
        slowKorean: Boolean,
        onDone: (() -> Unit)?,
    ) {
        if (token != chainToken) return
        if (idx >= segs.size) {
            _speaking.value = false
            onDone?.invoke()
            return
        }
        val (text, korean) = segs[idx]
        val e = if (korean) koE else viE
        val t = e.tts
        if (t == null || !e.ready) {
            playChain(segs, idx + 1, token, slowKorean, onDone)
            return
        }
        val id = "u" + ids.incrementAndGet()
        var advanced = false
        val next = {
            if (!advanced) {
                advanced = true
                playChain(segs, idx + 1, token, slowKorean, onDone)
            }
        }
        callbacks[id] = next
        // Lưới an toàn: engine không báo xong → vẫn đi tiếp
        main.postDelayed({ if (callbacks.remove(id) != null) next() }, 3000L + text.length * 140L)
        val rate = settings.value.speechRate.coerceIn(0.5f, 2.0f)
        t.setSpeechRate(if (korean && slowKorean) rate * 0.85f else rate)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    private fun stopInternal() {
        chainToken++
        callbacks.clear()
        aiJob?.cancel()
        player.stop()
        runCatching { viE.tts?.stop() }
        runCatching { koE.tts?.stop() }
        _speaking.value = false
    }

    fun stop() {
        main.post { stopInternal() }
    }

    companion object {
        private const val TAG = "HuyFanVoice"
        /** Giọng AI chậm quá mức này → dùng giọng máy cho kịp */
        private const val AI_TIMEOUT_MS = 5000L

        private val hangulRun = Regex("[\\uAC00-\\uD7A3\\u1100-\\u11FF\\u3130-\\u318F]+(?:[\\s\\d.,!?~·…-]*[\\uAC00-\\uD7A3\\u1100-\\u11FF\\u3130-\\u318F]+)*[.!?~]?")
        private val noise = Regex("[*_#`>|\\[\\]{}]|[\\p{So}\\p{Cs}\\uFE0F\\u200D]|[\\u4E00-\\u9FFF]")

        fun clean(text: String): String = text.replace(noise, " ").replace(Regex("\\s+"), " ").trim()

        private val quoted = Regex("[\"“”]([^\"“”]{1,200})[\"“”]")
        private val vietWord = Regex("[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]", RegexOption.IGNORE_CASE)

        /** Tiếng Anh: AI đặt từ/câu tiếng Anh trong ngoặc kép → đọc đoạn đó bằng giọng Anh. */
        private fun segmentEnglish(text: String): List<Pair<String, Boolean>> {
            val out = mutableListOf<Pair<String, Boolean>>()
            var last = 0
            for (m in quoted.findAll(text)) {
                val before = text.substring(last, m.range.first).trim()
                if (before.any { it.isLetterOrDigit() }) out += before to false
                val en = m.groupValues[1].trim()
                if (en.any { it.isLetterOrDigit() }) out += en to true
                last = m.range.last + 1
            }
            val tail = text.substring(last).trim()
            if (tail.any { it.isLetterOrDigit() }) out += tail to false
            // Cả câu không dấu tiếng Việt, nhiều chữ Latin (vd câu tiếng Anh thuần) → đọc giọng Anh
            if (out.size == 1 && !out[0].second && !vietWord.containsMatchIn(out[0].first) &&
                out[0].first.count { it in 'a'..'z' || it in 'A'..'Z' } >= 4
            ) return listOf(out[0].first to true)
            return out
        }

        /** Tách văn bản thành các đoạn (nội dung, là ngôn ngữ đang học?). */
        fun segment(text: String): List<Pair<String, Boolean>> {
            if (com.hankukko.huyfan.Lang.isEn) return segmentEnglish(text)
            val out = mutableListOf<Pair<String, Boolean>>()
            var last = 0
            for (m in hangulRun.findAll(text)) {
                val before = text.substring(last, m.range.first).trim()
                if (before.any { it.isLetterOrDigit() }) out += before to false
                out += m.value.trim() to true
                last = m.range.last + 1
            }
            val tail = text.substring(last).trim()
            if (tail.any { it.isLetterOrDigit() }) out += tail to false
            return mergeNeutral(out)
        }

        /** Chữ cái tiếng Việt có dấu → chắc chắn là tiếng Việt. */
        private val vietMark = Regex("[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]", RegexOption.IGNORE_CASE)

        /**
         * Đoạn "trung tính" (số, %, chữ Latin không dấu như "K", "90%", "A형"…) nằm sát tiếng Hàn
         * → đọc bằng giọng Hàn cho liền mạch, không chen giọng Việt vào giữa câu Hàn.
         */
        private fun mergeNeutral(segs: List<Pair<String, Boolean>>): List<Pair<String, Boolean>> {
            if (segs.size < 2) return segs
            fun neutral(t: String) = !vietMark.containsMatchIn(t) && t.length <= 24 && t.split(' ').size <= 3
            val marked = segs.mapIndexed { i, (t, ko) ->
                val nearKo = segs.getOrNull(i - 1)?.second == true || segs.getOrNull(i + 1)?.second == true
                t to (ko || (nearKo && neutral(t)))
            }
            val out = mutableListOf<Pair<String, Boolean>>()
            marked.forEach { (t, ko) ->
                val last = out.lastOrNull()
                if (last != null && last.second == ko) out[out.lastIndex] = (last.first + " " + t) to ko else out += t to ko
            }
            return out
        }
    }
}
