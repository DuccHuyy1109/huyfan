package com.hankukko.huyfan.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

/**
 * Nghe từ khoá đánh thức ("huy phan" = HUY FAN) hoàn toàn offline bằng Vosk
 * với ngữ pháp giới hạn → nhẹ CPU, không gửi âm thanh lên mạng.
 */
class WakeWordEngine(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var loading = false
    private var recognizer: Recognizer? = null
    private var service: SpeechService? = null
    private var phrases: List<String> = emptyList()
    private var minConf = 0.9
    private var needHits = 2
    private var hits = 0
    private var lastTrigger = 0L

    var onWake: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    val isLoaded: Boolean get() = model != null
    val isListening: Boolean get() = service != null

    fun load(onLoaded: () -> Unit, onFail: (String) -> Unit) {
        if (model != null) { onLoaded(); return }
        if (loading) return
        loading = true
        StorageService.unpack(
            context, ASSET_DIR, TARGET_DIR,
            { m ->
                loading = false
                Log.i(TAG, "model loaded")
                model = m
                onLoaded()
            },
            { e ->
                loading = false
                Log.w(TAG, "model load failed", e)
                onFail(e.message ?: "Không giải nén được mô hình")
            }
        )
    }

    /**
     * [minConfidence]: độ tin cậy tối thiểu từng từ (kết quả cuối câu).
     * [stableHits]: số nhịp nghe liên tiếp (~0,2 s/nhịp) câu gọi phải đứng ở cuối đoạn đang nghe.
     */
    fun start(wakePhrases: List<String>, minConfidence: Double, stableHits: Int): Boolean {
        val m = model ?: return false
        stop()
        phrases = wakePhrases
        minConf = minConfidence
        needHits = stableHits.coerceAtLeast(1)
        hits = 0
        return try {
            // Từ mồi: tiếng ồn/câu nói thường sẽ khớp vào đây thay vì bị ép thành câu gọi
            val grammar = JSONArray().apply {
                wakePhrases.forEach { put(it) }
                DISTRACTORS.forEach { put(it) }
                put("[unk]")
            }.toString()
            val rec = Recognizer(m, SAMPLE_RATE, grammar).apply { setWords(true) }
            recognizer = rec
            service = SpeechService(rec, SAMPLE_RATE).also { it.startListening(listener) }
            Log.i(TAG, "listening for $wakePhrases")
            true
        } catch (e: Exception) {
            Log.w(TAG, "start failed", e)
            stop()
            onError?.invoke(e.message ?: "Không mở được micro")
            false
        }
    }

    fun stop() {
        service?.let {
            runCatching { it.stop() }
            runCatching { it.shutdown() }
        }
        service = null
        recognizer?.let { runCatching { it.close() } }
        recognizer = null
    }

    fun release() {
        stop()
        model?.let { runCatching { it.close() } }
        model = null
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) = checkPartial(hypothesis)
        override fun onResult(hypothesis: String?) = check(hypothesis)
        override fun onFinalResult(hypothesis: String?) = check(hypothesis)
        override fun onError(exception: Exception?) {
            main.post { onError?.invoke(exception?.message ?: "Lỗi micro") }
        }

        override fun onTimeout() {}
    }

    /**
     * Nơi ồn (TV, người nói liên tục) bộ nhận diện không thấy chỗ ngắt câu → câu gọi bị chìm trong đoạn dài.
     * Vì vậy bắt ngay khi "huy phan" nằm ở CUỐI đoạn đang nghe và giữ nguyên vài nhịp liên tiếp.
     */
    private fun checkPartial(hypothesis: String?) {
        if (hypothesis.isNullOrBlank()) return
        val partial = runCatching { JSONObject(hypothesis).optString("partial") }.getOrDefault("").trim()
        val tokens = partial.split(' ').filter { it.isNotBlank() && it != "[unk]" }
        if (tokens.isEmpty()) { hits = 0; return }
        fun endsWith(phrase: String): Boolean {
            val parts = phrase.split(' ').filter { it.isNotBlank() }
            // cho phép tối đa 1 từ "lạc" phía sau (vd "huy phan ơi")
            return (0..1).any { extra ->
                val end = tokens.size - extra
                end >= parts.size && tokens.subList(end - parts.size, end) == parts
            }
        }
        // Tính cả cách gọi gần giống (mất âm "n" cuối, "huy" → "khi"…) cho dễ gọi
        val hit = (phrases + aliasesOf(phrases)).any(::endsWith)
        if (hit) {
            hits++
            if (hits >= needHits) {
                hits = 0
                fire("partial '$partial'")
                return
            }
        } else {
            hits = 0
        }
        // Đoạn quá dài → làm mới để câu gọi tiếp theo không bị chìm
        if (tokens.size > MAX_PARTIAL_WORDS) {
            Log.d(TAG, "reset long partial (${tokens.size} words)")
            main.post { if (service != null) start(phrases, minConf, needHits) }
        }
    }

    private fun fire(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastTrigger < 2500) return
        lastTrigger = now
        Log.i(TAG, "wake: $reason")
        main.post { onWake?.invoke() }
    }

    private fun check(hypothesis: String?) {
        hits = 0
        if (hypothesis.isNullOrBlank()) return
        val o = runCatching { JSONObject(hypothesis) }.getOrNull() ?: return
        val text = o.optString("text").trim()
        if (text.isEmpty() || text == "[unk]") return
        val words = o.optJSONArray("result")
        val tokens = (0 until (words?.length() ?: 0)).mapNotNull { words?.optJSONObject(it) }
            .map { it.optString("word") to it.optDouble("conf", 0.0) }

        for (phrase in phrases + aliasesOf(phrases)) {
            val parts = phrase.split(' ').filter { it.isNotBlank() }
            // Tìm cụm từ liên tiếp khớp câu gọi, lấy độ tin cậy thấp nhất
            for (i in 0..tokens.size - parts.size) {
                if (parts.indices.all { tokens[i + it].first == parts[it] }) {
                    val conf = parts.indices.minOf { tokens[i + it].second }
                    Log.i(TAG, "candidate '$text' ~ '$phrase' conf=${"%.2f".format(conf)} need=${"%.2f".format(minConf)}")
                    if (conf >= minConf) fire("final '$text'")
                    return
                }
            }
        }
        Log.d(TAG, "heard: $text (conf ${tokens.joinToString(" ") { "%.2f".format(it.second) }})")
    }

    companion object {
        private const val TAG = "WakeWord"
        private const val SAMPLE_RATE = 16000f
        private const val ASSET_DIR = "vosk-model-vn"
        private const val TARGET_DIR = "vosk-model-vn"
        private const val MAX_PARTIAL_WORDS = 12
        /**
         * Mô hình tiếng Việt hay nghe "huy phan" thành "khi pha", "huyền phan", "quy pha"… (thấy trong log khi gọi thật).
         * Câu gọi có 2 từ nên khó trùng câu nói thường → nhận luôn các cách gần giống cho dễ gọi.
         */
        private val HUY_LIKE = listOf("huy", "khi", "quy", "uy", "tuy", "thuy", "huyền")
        private val PHAN_LIKE = listOf("phan", "pha")

        fun aliasesOf(phrases: List<String>): List<String> =
            if ("huy phan" !in phrases) emptyList()
            else HUY_LIKE.flatMap { a -> PHAN_LIKE.map { b -> "$a $b" } }.filter { it !in phrases }

        /** Âm tiết tiếng Việt thông dụng (có trong từ điển mô hình) làm "chỗ trút" cho âm thanh không phải câu gọi. */
        private val DISTRACTORS = listOf(
            "huy", "phan", "hai", "ban", "hay", "khi", "phải", "fan", "anh", "em", "không", "có", "là", "gì",
            "này", "đi", "được", "rồi", "ơi", "vậy", "thì", "nhé", "nha", "bạn", "tôi", "mình", "ông", "à",
            "ừ", "ạ", "và", "với", "cho", "của", "một", "người", "nói", "biết", "làm", "đây", "kia", "thôi",
            "huyền", "phong", "hoa", "quy", "uy", "tuy", "thuy", "phát", "pha", "vân", "xin", "chào",
        )
    }
}
