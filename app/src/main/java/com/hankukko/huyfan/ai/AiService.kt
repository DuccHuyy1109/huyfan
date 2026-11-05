package com.hankukko.huyfan.ai

import android.util.Log
import com.hankukko.huyfan.data.AppSettings
import com.hankukko.huyfan.data.Provider
import com.hankukko.huyfan.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** Điều phối: chọn nhà cung cấp, tự chọn model, dự phòng khi lỗi, dịch lỗi sang tiếng Việt. */
class AiService(private val httpClient: OkHttpClient, private val settings: SettingsRepository) {

    companion object {
        private const val BENCH_TIMEOUT_S = 25L
        private const val MAX_MODEL_TRIES = 4
        /** Chờ model hiện tại bao lâu thì bắn thêm model song song */
        private const val HEDGE_MS = 7000L
        /** Chờ API chính bao lâu thì gọi song song API dự phòng (Groq) */
        private const val PROVIDER_HEDGE_MS = 6000L
        /** API chính vừa chậm/lỗi → trong TROUBLE_MS tới gọi dự phòng sớm hơn nhiều */
        private const val PROVIDER_HEDGE_FAST_MS = 1500L
        private const val TROUBLE_MS = 10 * 60_000L
        private const val GROQ_MODEL = "llama-3.3-70b-versatile"
        private val RETRYABLE = setOf(404, 429, 500, 503)
        /** Groq gói miễn phí: Whisper tối đa 20 lượt / phút (chừa 2 lượt an toàn) */
        private const val WHISPER_RPM = 18
        /** Phiên dịch trực tiếp: API chính chậm quá 2,5 s → gọi thêm dự phòng */
        private const val LIVE_HEDGE_MS = 2500L
    }

    private val gemini = GeminiClient(httpClient)
    private val openai = OpenAiCompatClient(httpClient)

    /** Thời điểm hết "giai đoạn API chính trục trặc" (quá tải giờ cao điểm thường kéo dài vài chục phút). */
    @Volatile
    private var primaryTroubleUntil = 0L

    suspend fun ask(system: String, turns: List<Turn>): AssistantReply {
        val s = settings.get()
        return withProviders(s) { p -> call(p, s, system, turns) }
    }

    /** Kết quả nhận dạng 1 câu nói: ngôn ngữ ("vi" / "ko" / "" nếu không phải lời nói) + chữ. */
    data class Heard(val lang: String, val text: String, val via: String)

    @Volatile
    private var whisperModel: String? = null

    /**
     * Nhận dạng 1 câu nói mà KHÔNG cần biết trước ngôn ngữ (dịch tự động):
     * ưu tiên Whisper trên Groq (nhanh, tự nhận ra Việt/Hàn), không có thì để Gemini nghe thẳng âm thanh.
     */
    /** Có nhận dạng nhanh (Whisper trên Groq) để hiện chữ ngay khi đang nói không. */
    suspend fun hasFastTranscribe(): Boolean {
        val s = settings.get()
        return s.openaiKey.isNotBlank() && s.openaiBaseUrl.contains("groq.com")
    }

    // ---- Hạn mức Whisper trên Groq (gói miễn phí: 20 lượt / phút) ----
    private val whisperCalls = ArrayDeque<Long>()

    @Volatile
    private var whisperBlockedUntil = 0L

    /** Giữ chỗ [n] lượt gọi Whisper, luôn chừa lại [keep] lượt cho việc quan trọng hơn. */
    private fun takeWhisper(n: Int, keep: Int): Boolean = synchronized(whisperCalls) {
        val now = System.currentTimeMillis()
        if (now < whisperBlockedUntil) return false
        while (whisperCalls.isNotEmpty() && now - whisperCalls.first() > 60_000) whisperCalls.removeFirst()
        if (WHISPER_RPM - whisperCalls.size - n < keep) return false
        repeat(n) { whisperCalls.addLast(now) }
        true
    }

    /** Bị Groq chặn (429) → nghỉ đúng thời gian Groq yêu cầu. */
    private fun whisperLimited(e: Throwable) {
        val body = (e as? ProviderHttpException)?.takeIf { it.code == 429 }?.body ?: return
        val m = Regex("""try again in (?:(\d+)m)?([\d.]+)(ms|s)""").find(body)
        val wait = if (m == null) 20_000L else {
            val min = m.groupValues[1].toLongOrNull() ?: 0L
            val v = m.groupValues[2].toDoubleOrNull() ?: 20.0
            min * 60_000 + (if (m.groupValues[3] == "ms") v else v * 1000).toLong()
        }
        whisperBlockedUntil = System.currentTimeMillis() + wait.coerceIn(1_000, 120_000) + 300
        Log.w("HuyFanAI", "whisper hết hạn mức → nghỉ ${wait}ms (dùng Gemini nghe thay)")
    }

    private suspend fun whisper(s: AppSettings, wav: ByteArray, language: String?): OpenAiCompatClient.Transcript? = runCatching {
        val model = whisperModel ?: run {
            val all = runCatching { openai.listModels(s.openaiBaseUrl, s.openaiKey) }.getOrDefault(emptyList())
            (all.firstOrNull { it.contains("whisper-large-v3-turbo") } ?: all.firstOrNull { it.contains("whisper") } ?: "whisper-large-v3-turbo")
                .also { whisperModel = it }
        }
        // Không gửi câu gợi ý: nghe không rõ là Whisper chép lại nguyên câu gợi ý thành "lời nói"
        openai.transcribe(s.openaiBaseUrl, s.openaiKey, wav, model, null, language)
    }.onFailure { Log.w("HuyFanAI", "whisper lỗi: ${it.message?.take(200)}"); whisperLimited(it) }.getOrNull()

    private fun hangulRatio(t: String): Double {
        val letters = t.count { it.isLetter() }
        return if (letters == 0) 0.0 else t.count { it in '\uAC00'..'\uD7A3' }.toDouble() / letters
    }

    /** Whisper chỉ được hiểu là Việt hoặc Hàn: "vi" / "ko", ngôn ngữ khác (Anh, Nhật…) → null. */
    private val vietMark = Regex("[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]", RegexOption.IGNORE_CASE)

    /** Mã Whisper của ngôn ngữ đang học. Kết quả trả về app vẫn ghi "ko" = ngôn ngữ đang học. */
    private val target: String get() = if (com.hankukko.huyfan.Lang.isEn) "en" else "ko"

    private fun whisperLang(r: OpenAiCompatClient.Transcript): String? {
        val l = r.language.lowercase()
        val h = hangulRatio(r.text)
        return when {
            com.hankukko.huyfan.Lang.isEn && (l.contains("english") || l == "en") && h == 0.0 && !vietMark.containsMatchIn(r.text) -> "ko"
            !com.hankukko.huyfan.Lang.isEn && (l.contains("korean") || l == "ko") && h >= 0.5 -> "ko"
            (l.contains("vietnamese") || l == "vi") && h < 0.2 -> "vi"
            else -> null
        }
    }

    /**
     * [quick] = chữ tạm khi đang nói: chỉ dùng Whisper (nhanh), lỗi / hết lượt thì thôi, không gọi Gemini.
     * Chỉ chấp nhận tiếng Việt hoặc tiếng Hàn: Whisper đoán ra ngôn ngữ khác → nghe lại ép theo cả 2 rồi chọn bản tin cậy hơn.
     */
    suspend fun transcribeAuto(wav: ByteArray, quick: Boolean = false, speechMs: Long = 0): Heard {
        val s = settings.get()
        val groq = s.openaiKey.isNotBlank() && s.openaiBaseUrl.contains("groq.com")
        if (quick && !groq) return Heard("", "", "none")
        // Nói bao lâu thì được tối đa bấy nhiêu chữ (~9 âm tiết / giây + 2): tiếng động ngắn mà ra cả câu dài = Whisper bịa
        val maxUnits = if (speechMs > 0) 2 + 9 * speechMs / 1000.0 else Double.MAX_VALUE
        fun tooLong(t: String) = speechUnits(t) > maxUnits
        // Chữ tạm chỉ dùng lượt dư (chừa 8 lượt/phút cho câu đã chốt)
        if (groq && takeWhisper(1, keep = if (quick) 9 else 0)) {
            val r = whisper(s, wav, null)
            if (r != null) {
                val tag = "tin cậy ${"%.2f".format(r.logprob)} · không-lời ${"%.2f".format(r.noSpeech)} · ${speechUnits(r.text)} chữ/${speechMs}ms"
                // Im lặng / tiếng động: Whisper chắc "không có lời", hoặc khá chắc mà chữ lại đoán mò / là câu hay bịa
                val silent = r.noSpeech > 0.8 || (r.noSpeech > 0.5 && (r.logprob < -0.7 || isSilenceFiller(r.text)))
                if (silent || r.text.isBlank() || isHallucination(r.text) || r.logprob < -1.5 || r.compression > 2.4 || tooLong(r.text)) {
                    if (r.text.isNotBlank()) Log.i("HuyFanAI", "bỏ ($tag): '${r.text.take(40)}'")
                    return Heard("", "", "whisper")
                }
                // Tiếng Anh: "Thank you." trên nền ồn mà Whisper không chắc = câu bịa
                if (com.hankukko.huyfan.Lang.isEn && isNoiseThanks(r.text) && r.logprob < -0.6) {
                    Log.i("HuyFanAI", "bỏ ($tag): '${r.text.take(40)}' – câu cảm ơn nghi bịa")
                    return Heard("", "", "whisper")
                }
                val lang = whisperLang(r)
                if (lang != null) {
                    Log.i("HuyFanAI", "nghe [$lang] ($tag): '${r.text.take(40)}'")
                    return Heard(lang, r.text, "whisper")
                }
                Log.i("HuyFanAI", "whisper đoán '${r.language}' (${r.text.take(40)}) → không phải Việt/Hàn")
                // "Thank you." / "Спасибо." / "ありがとう" trên tiếng ồn = Whisper bịa, không phải lời nói
                if (!com.hankukko.huyfan.Lang.isEn && isNoiseThanks(r.text)) return Heard("", "", "whisper")
                // Chữ tạm: thà không hiện còn hơn hiện sai ngôn ngữ
                if (quick) return Heard("", "", "whisper")
                // Nghe lại, ép lần lượt tiếng Việt và tiếng Hàn → lấy bản Whisper tự tin hơn
                if (takeWhisper(2, keep = 0)) {
                    val (vi, ko) = coroutineScope {
                        val a = async { whisper(s, wav, "vi") }
                        val b = async { whisper(s, wav, target) }
                        a.await() to b.await()
                    }
                    fun score(t: OpenAiCompatClient.Transcript?, korean: Boolean): Double {
                        if (t == null || t.text.isBlank() || isHallucination(t.text) || t.compression > 2.4 || tooLong(t.text) || t.noSpeech > 0.9) return Double.NEGATIVE_INFINITY
                        val h = hangulRatio(t.text)
                        // Ép Hàn mà ra toàn chữ Latin (hoặc ngược lại) = đang dịch/bịa → trừ điểm nặng
                        val words = t.text.split(Regex("\\s+")).count { w -> w.any { it.isLetter() } }
                        val penalty = when {
                            com.hankukko.huyfan.Lang.isEn && korean -> if (h > 0.0 || vietMark.containsMatchIn(t.text)) 2.0 else 0.0
                            com.hankukko.huyfan.Lang.isEn -> if (h > 0.2 || !looksVietnamese(t.text) || (words >= 2 && !vietMark.containsMatchIn(t.text))) 2.0 else 0.0
                            korean -> if (h < 0.5) 2.0 else 0.0
                            else -> if (h > 0.2 || !looksVietnamese(t.text)) 2.0 else 0.0
                        }
                        return t.logprob - penalty
                    }
                    // Cả 2 lần nghe lại đều lỗi (hết lượt Groq / mạng) → không bỏ câu, để Gemini nghe thay
                    if (vi != null || ko != null) {
                        val sv = score(vi, false)
                        val sk = score(ko, true)
                        Log.i("HuyFanAI", "nghe lại: vi=${"%.2f".format(sv)} '${vi?.text?.take(30)}' · ko=${"%.2f".format(sk)} '${ko?.text?.take(30)}'")
                        // Cả 2 bản đều đoán mò → coi như nghe không rõ, không dịch bừa
                        if (maxOf(sv, sk) < -1.3) return Heard("", "", "whisper")
                        // 1 bản là câu bịa quen thuộc ("Cảm ơn các bạn đã theo dõi"…) → đoạn này chỉ là tiếng ồn
                        if (vi?.text?.let(::isHallucination) == true || ko?.text?.let(::isHallucination) == true) {
                            Log.i("HuyFanAI", "nghe lại ra câu bịa → bỏ (chỉ là tiếng ồn)")
                            return Heard("", "", "whisper")
                        }
                        return if (sk > sv) Heard("ko", ko!!.text, "whisper-ko") else Heard("vi", vi!!.text, "whisper-vi")
                    }
                }
                // Không đủ lượt → để Gemini nghe (chỉ được trả Việt/Hàn)
            }
        }
        if (quick) return Heard("", "", "whisper")
        // Gemini nghe thẳng âm thanh
        if (s.geminiKey.isBlank()) throw AiException("no key", "Cần Groq key hoặc Gemini key để dịch tự động.")
        val system = if (com.hankukko.huyfan.Lang.isEn) """Bạn nhận 1 đoạn ghi âm ngắn trong cuộc nói chuyện giữa người Việt và người nói tiếng Anh.
Chép lại CHÍNH XÁC lời nói (đúng ngôn ngữ gốc, có dấu, có dấu câu) và cho biết ngôn ngữ: "vi" (tiếng Việt) hoặc "en" (tiếng Anh).
Người nói CHỈ dùng tiếng Việt hoặc tiếng Anh: nghe giống ngôn ngữ khác thì chọn ngôn ngữ gần nhất trong 2 ngôn ngữ này và chép bằng ngôn ngữ đó, KHÔNG dịch.
Không có lời nói rõ ràng (chỉ tiếng ồn, nhạc, im lặng) → lang = "none", text = "". Tuyệt đối không bịa.
Chỉ trả JSON: {"lang":"vi","text":""}""" else """Bạn nhận 1 đoạn ghi âm ngắn trong cuộc nói chuyện giữa người Việt và người Hàn.
Chép lại CHÍNH XÁC lời nói (đúng ngôn ngữ gốc, có dấu, có dấu câu) và cho biết ngôn ngữ: "vi" (tiếng Việt) hoặc "ko" (tiếng Hàn).
Người nói CHỈ dùng tiếng Việt hoặc tiếng Hàn: nghe giống tiếng Anh/ngôn ngữ khác thì đó là tiếng Việt hoặc tiếng Hàn phát âm chưa rõ → chọn ngôn ngữ gần nhất và chép bằng ngôn ngữ đó, KHÔNG dịch, KHÔNG chép tiếng Anh.
Không có lời nói rõ ràng (chỉ tiếng ồn, nhạc, im lặng) → lang = "none", text = "". Tuyệt đối không bịa.
Chỉ trả JSON: {"lang":"vi","text":""}"""
        val schema = JSONObject().put("type", "OBJECT")
            .put("properties", JSONObject().put("lang", JSONObject().put("type", "STRING")).put("text", JSONObject().put("type", "STRING")))
            .put("required", org.json.JSONArray(listOf("lang", "text")))
        val (raw, _) = callGemini(s, system, listOf(Turn(Turn.USER, "[ghi âm]", audioWav = wav)), schema)
        val o = ReplyParser.extractJson(raw) ?: return Heard("", "", "gemini")
        val text = o.optString("text").trim()
        val lang = o.optString("lang").lowercase()
        if (text.isBlank() || lang == "none" || isHallucination(text) || tooLong(text)) {
            if (text.isNotBlank()) Log.i("HuyFanAI", "Gemini nghe ra câu bịa / quá dài → bỏ: '${text.take(40)}'")
            return Heard("", "", "gemini")
        }
        val hangul = text.count { it in '\uAC00'..'\uD7A3' }
        if (com.hankukko.huyfan.Lang.isEn) {
            val en = lang.startsWith("en") || (!lang.startsWith("vi") && !vietMark.containsMatchIn(text))
            return Heard(if (en) "ko" else "vi", text, "gemini")
        }
        return Heard(if (lang.startsWith("ko") || hangul * 2 >= text.count { it.isLetter() }) "ko" else "vi", text, "gemini")
    }

    /** Số "chữ" để so với thời gian nói: âm tiết tiếng Hàn + từ chữ Latin (tiếng Việt mỗi từ = 1 âm tiết). */
    private fun speechUnits(t: String): Int {
        val hangul = t.count { it in '\uAC00'..'\uD7A3' }
        val words = t.split(Regex("\\s+")).count { w -> w.any { it.isLetter() && it !in '\uAC00'..'\uD7A3' } }
        return hangul + words
    }

    /** Âm tiết tiếng Việt: phụ âm đầu + nguyên âm (1–3 chữ) + phụ âm cuối. */
    private val viSyllable = Regex(
        "^(ngh|ng|nh|ch|gh|gi|kh|ph|qu|th|tr|[bcdđghklmnprstvx])?" +
            "[aăâeêioôơuưyáàảãạắằẳẵặấầẩẫậéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ]{1,3}" +
            "(ng|nh|ch|[cmnpt])?$"
    )

    /** Chữ có thật là tiếng Việt không (≥ 60% từ là âm tiết tiếng Việt hợp lệ). */
    private fun looksVietnamese(t: String): Boolean {
        val words = t.lowercase().split(Regex("[\\s,.!?…;:\"“”()\\-]+"))
            .filter { it.isNotBlank() && !it.all(Char::isDigit) }
        if (words.isEmpty()) return false
        return words.count { viSyllable.matches(it) }.toDouble() / words.size >= 0.6
    }

    /** Câu Whisper hay bịa trên khoảng lặng ("감사합니다", "Cảm ơn"…) – chỉ bỏ khi Whisper cũng thấy "không có lời". */
    private fun isSilenceFiller(t: String): Boolean {
        val x = t.lowercase().trim().trim('.', '!', '?', '…', ' ')
        return isNoiseThanks(x) || x in setOf("감사합니다", "고맙습니다", "cảm ơn", "cảm ơn bạn", "cảm ơn các bạn", "xin cảm ơn")
    }

    /** Câu cảm ơn ngắn bằng ngôn ngữ khác – Whisper hay bịa ra khi chỉ nghe thấy tiếng ồn. */
    private fun isNoiseThanks(t: String): Boolean {
        val x = t.lowercase().trim().trim('.', '!', '?', '…', ' ', '。', '！')
        return x in setOf(
            "thank you", "thanks", "thank you very much", "thank you so much", "thanks for watching",
            "спасибо", "спасибо за просмотр", "merci", "gracias", "danke", "grazie", "obrigado", "obrigada",
            "продолжение следует", "субтитры сделал dimatorzok", "редактор субтитров", "to be continued",
            "sous-titres réalisés par la communauté d'amara.org", "untertitel der amara.org-community",
            "ご視聴ありがとうございました", "字幕由amara.org社区提供",
            "ありがとうございました", "ありがとうございます", "ありがとう", "谢谢", "謝謝", "谢谢观看", "dziękuję",
        )
    }

    /** Câu Whisper hay "bịa" ra khi chỉ có tiếng ồn / im lặng. */
    private fun isHallucination(t: String): Boolean {
        val x = t.lowercase().trim().trimEnd('.', '!', '?', '…')
        if (x.isEmpty()) return true
        // Từ 1 chữ vẫn là lời nói thật: 네, 응, 아, 예 / ừ, ờ, à, ạ
        if (x.length == 1 && !(x[0] in '\uAC00'..'\uD7A3' || x[0] in "ừờàạơ")) return true
        val bad = listOf(
            "cảm ơn các bạn đã theo dõi", "hãy subscribe", "đăng ký kênh", "ghiền mì gõ", "hẹn gặp lại các bạn",
            "시청해 주셔서 감사합니다", "구독과 좋아요", "구독 좋아요", "mbc 뉴스", "끝까지 시청", "감사합니다 시청",
            "thank you for watching", "subtitles by",
            // câu kết video YouTube Whisper thuộc lòng
            "không bỏ lỡ những video", "video hấp dẫn", "những video tiếp theo", "video tiếp theo", "nhấn like", "bấm like",
            "nhấn chuông", "đăng ký cho kênh", "cảm ơn các bạn đã xem", "la la school",
            "시청해 주셔서", "시청해주셔서", "다음 영상에서", "구독 부탁", "좋아요와 구독", "알림 설정", "자막 제공", "kbs 뉴스", "sbs 뉴스",
            // Gemini chép lại câu lệnh thay vì lời nói
            "chép lại đoạn ghi âm", "đoạn ghi âm này", "ghi âm]", "녹음 내용을", "받아적어",
            // câu gợi ý cũ bị Whisper chép lại
            "hội thoại tiếng việt", "한국어 대화", "저는 후이입니다", "hôm nay bạn thế nào",
        )
        return bad.any { x.contains(it) }
    }

    /** Gọi AI trả JSON theo [schema] riêng (vd hội thoại nhập vai) – vẫn có dự phòng Gemini ↔ Groq. */
    suspend fun askJson(system: String, turns: List<Turn>, schema: JSONObject?, live: Boolean = false): Pair<String, String> {
        val s = settings.get()
        return withProviders(s, if (live) LIVE_HEDGE_MS else null) { p -> callRaw(p, s, system, turns, schema) }
    }

    /** Chạy API chính; chậm/lỗi → gọi song song API dự phòng (nếu có key), lấy bên trả lời trước. */
    /** [hedge] = chờ API chính tối đa bao lâu rồi gọi song song API dự phòng (null = mặc định). */
    private suspend fun <T> withProviders(s: AppSettings, hedge: Long? = null, run: suspend (Provider) -> T): T {
        val primary = s.provider
        val secondary = if (primary == Provider.GEMINI) Provider.OPENAI else Provider.GEMINI
        if (!(s.fallback && hasCredentials(secondary, s))) {
            return try {
                run(primary)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw toAiException(e, primary)
            }
        }
        // Có API dự phòng: API chính chậm quá PROVIDER_HEDGE_MS hoặc lỗi → gọi song song API dự phòng, lấy bên trả lời trước
        return coroutineScope {
            val ch = Channel<Pair<Provider, Result<T>>>(Channel.UNLIMITED)
            val jobs = mutableListOf<Job>()
            fun start(p: Provider) {
                jobs += launch { ch.send(p to runCatching { run(p) }) }
            }
            start(primary)
            var pending = 1
            var backupStarted = false
            var firstError: Throwable? = null
            val troubled = System.currentTimeMillis() < primaryTroubleUntil
            val hedgeMs = minOf(if (troubled) PROVIDER_HEDGE_FAST_MS else PROVIDER_HEDGE_MS, hedge ?: Long.MAX_VALUE)
            while (pending > 0) {
                val r = if (backupStarted) ch.receive() else withTimeoutOrNull(hedgeMs) { ch.receive() }
                if (r == null) {
                    Log.i("HuyFanAI", "$primary chậm (${hedgeMs}ms${if (troubled) ", đang quá tải" else ""}) → gọi thêm $secondary")
                    if (!troubled) primaryTroubleUntil = System.currentTimeMillis() + TROUBLE_MS
                    start(secondary); backupStarted = true; pending++
                    continue
                }
                pending--
                val value = r.second.getOrNull()
                if (value != null) {
                    // API chính trả lời nhanh trở lại → thôi gọi dự phòng sớm
                    if (r.first == primary && !backupStarted) primaryTroubleUntil = 0L
                    jobs.forEach { it.cancel() }
                    return@coroutineScope value
                }
                val e = r.second.exceptionOrNull()!!
                if (e is CancellationException) throw e
                Log.w("HuyFanAI", "${r.first} lỗi: ${e.javaClass.simpleName}: ${e.message?.take(200)}")
                if (r.first == primary) primaryTroubleUntil = System.currentTimeMillis() + TROUBLE_MS
                // Báo lỗi của API chính (thường đúng bản chất hơn lỗi của API dự phòng)
                if (firstError == null || r.first == primary) firstError = e
                if (!backupStarted) {
                    start(secondary); backupStarted = true; pending++
                }
            }
            throw toAiException(firstError ?: IllegalStateException("no provider"), primary)
        }
    }

    private fun hasCredentials(p: Provider, s: AppSettings) = when (p) {
        Provider.GEMINI -> s.geminiKey.isNotBlank()
        Provider.OPENAI -> s.openaiKey.isNotBlank() && s.openaiBaseUrl.isNotBlank()
    }

    /** Key Groq (gsk_…) nhưng link dự phòng trỏ chỗ khác (vd bấm nhầm preset) → Groq từ chối mọi câu (400). */
    private fun groqMismatch(s: AppSettings) =
        s.openaiKey.trim().startsWith("gsk_") && !s.openaiBaseUrl.contains("groq.com")

    private suspend fun call(p: Provider, s0: AppSettings, system: String, turns: List<Turn>): AssistantReply {
        val t0 = System.currentTimeMillis()
        val (raw, model) = callRaw(p, s0, system, turns, PromptBuilder.geminiSchema())
        return ReplyParser.parse(raw).copy(model = model, latencyMs = System.currentTimeMillis() - t0)
    }

    private suspend fun callRaw(p: Provider, s0: AppSettings, system: String, turns: List<Turn>, schema: JSONObject?): Pair<String, String> {
        var s = s0
        if (p == Provider.OPENAI && groqMismatch(s)) {
            val model = if (s.openaiModel.isBlank() || s.openaiModel.startsWith("gemini")) GROQ_MODEL else s.openaiModel
            Log.i("HuyFanAI", "key Groq nhưng link là ${s.openaiBaseUrl} → tự chuyển về Groq ($model)")
            s = s.copy(openaiBaseUrl = com.hankukko.huyfan.data.DEFAULT_OPENAI_BASE, openaiModel = model)
            settings.update { it.copy(openaiBaseUrl = s.openaiBaseUrl, openaiModel = model) }
        }
        return when (p) {
            Provider.GEMINI -> callGemini(s, system, turns, schema)
            Provider.OPENAI -> {
                if (s.openaiBaseUrl.isBlank()) throw AiException("no base", "Chưa nhập link API (OpenAI-compatible) trong Cài đặt.")
                if (turns.any { it.image != null }) {
                    val vm = pickVisionModel(s)
                    if (vm != null) return openai.generate(s.openaiBaseUrl, s.openaiKey, vm, system, turns, s.temperature) to vm
                    // Ảnh nằm ở câu hiện tại thì bắt buộc phải đọc ảnh; ảnh cũ thì bỏ, trả lời theo chữ
                    if (turns.lastOrNull { it.role == Turn.USER }?.image != null) {
                        throw AiException("no vision", "API dự phòng không có model đọc được ảnh – đợi Gemini rảnh rồi hỏi lại nha.")
                    }
                    val textOnly = turns.map { if (it.image != null) it.copy(image = null, text = "[Ảnh đã gửi trước đó] ${it.text}") else it }
                    return openai.generate(s.openaiBaseUrl, s.openaiKey, s.openaiModel, system, textOnly, s.temperature) to s.openaiModel
                }
                try {
                    openai.generate(s.openaiBaseUrl, s.openaiKey, s.openaiModel, system, turns, s.temperature) to s.openaiModel
                } catch (e: ProviderHttpException) {
                    // Model mặc định bị gỡ/đổi tên → tự chọn model chat phù hợp từ danh sách
                    if (e.code != 404 && !(e.code == 400 && e.body.contains("model", ignoreCase = true))) throw e
                    val picked = pickOpenAiModel(s) ?: throw e
                    if (picked == s.openaiModel) throw e
                    Log.i("HuyFanAI", "openai model ${s.openaiModel} → $picked")
                    settings.update { it.copy(openaiModel = picked) }
                    openai.generate(s.openaiBaseUrl, s.openaiKey, picked, system, turns, s.temperature) to picked
                }
            }
        }
    }

    private suspend fun callGemini(s: AppSettings, system: String, turns: List<Turn>, schema: JSONObject?): Pair<String, String> {
        if (s.geminiKey.isBlank()) {
            throw AiException("no key", "Chưa có Gemini API key. Vào Cài đặt → AI & API dán key vào nha.")
        }
        val auto = s.geminiModel.isBlank()
        // Alias "-latest" chỉ là phương án tạm khi chưa dò được danh sách → dò lại
        val cached = s.geminiResolvedModel.takeUnless { it.isBlank() || it.contains("-latest") }
        val primary = if (auto) cached ?: resolveGeminiModel(s) else s.geminiModel

        // Gửi song song kiểu "hedging": model đầu chậm quá HEDGE_MS hoặc lỗi 503/429/404 → bắn thêm model kế tiếp,
        // model nào trả lời đúng trước thì lấy. Gemini free hay quá tải nên cách này giúp trả lời nhanh và ổn định.
        val models = (listOf(primary) + fallbackModels(s, primary)).distinct().take(MAX_MODEL_TRIES)
        val (out, m) = hedged(models) { model ->
            gemini.generate(s.geminiBaseUrl, s.geminiKey, model, system, turns, s.temperature, s.fastMode, genHttp, schema)
        }
        if (auto && m != primary) {
            Log.i("HuyFanAI", "switched model $primary → $m")
            settings.update { it.copy(geminiResolvedModel = m) }
        }
        return out to m
    }

    @Volatile
    private var visionModel: String? = null

    /** Model đọc được ảnh trên API dự phòng (Groq: Llama 4 Scout/Maverick…). */
    private suspend fun pickVisionModel(s: AppSettings): String? {
        visionModel?.let { return it }
        val all = runCatching { openai.listModels(s.openaiBaseUrl, s.openaiKey) }.getOrNull() ?: return null
        val prefs = listOf("llama-4-scout", "llama-4-maverick", "vision", "-vl", "gpt-4o", "gemini")
        for (p in prefs) all.firstOrNull { it.contains(p, ignoreCase = true) }?.let { visionModel = it; return it }
        return null
    }

    /** Chọn model chat tốt nhất từ /models của API chuẩn OpenAI (Groq, OpenRouter...). */
    private suspend fun pickOpenAiModel(s: AppSettings): String? {
        val all = runCatching { openai.listModels(s.openaiBaseUrl, s.openaiKey) }.getOrNull() ?: return null
        val bad = listOf("whisper", "tts", "guard", "embed", "vision", "audio", "speech", "image", "moderation")
        val ok = all.filter { m -> bad.none { m.contains(it, ignoreCase = true) } }
        val prefs = listOf("llama-3.3-70b", "llama-4", "gpt-oss-120b", "qwen", "llama", "gpt-oss", "gemma", "mixtral")
        for (p in prefs) ok.firstOrNull { it.contains(p, ignoreCase = true) }?.let { return it }
        return ok.firstOrNull()
    }

    private fun retryable(e: Throwable) = when (e) {
        // Model không hỗ trợ kiểu gọi này (vd chọn nhầm model đọc giọng) → thử model khác
        is ProviderHttpException -> e.code in RETRYABLE ||
            (e.code == 400 && listOf("not enabled for this model", "not supported", "is not found for API version")
                .any { e.body.contains(it, ignoreCase = true) })
        is SocketTimeoutException -> true
        is IOException -> true
        else -> false
    }

    /** Chạy [call] cho từng model, "rải" thêm model kế khi model trước chậm/lỗi; trả kết quả thành công đầu tiên. */
    private suspend fun hedged(models: List<String>, call: suspend (String) -> String): Pair<String, String> = coroutineScope {
        val results = Channel<Pair<String, Result<String>>>(Channel.UNLIMITED)
        val jobs = mutableListOf<Job>()
        var next = 0
        var running = 0
        var lastError: Throwable? = null

        fun launchNext(): Boolean {
            if (next >= models.size) return false
            val m = models[next++]
            running++
            jobs += launch { results.send(m to runCatching { call(m) }) }
            return true
        }

        launchNext()
        while (running > 0) {
            val r = withTimeoutOrNull(HEDGE_MS) { results.receive() }
            if (r == null) {
                // Đang chậm → bắn thêm 1 model song song (nếu còn)
                if (launchNext()) Log.i("HuyFanAI", "hedge: slow → also trying ${models[next - 1]}")
                continue
            }
            running--
            val (m, res) = r
            val value = res.getOrNull()
            if (value != null) {
                jobs.forEach { it.cancel() }
                return@coroutineScope value to m
            }
            val e = res.exceptionOrNull()!!
            if (e is CancellationException) throw e
            if (!retryable(e)) {
                jobs.forEach { it.cancel() }
                throw e
            }
            lastError = e
            if (running == 0 && !launchNext()) break
            if (running < 2) launchNext()
        }
        throw lastError ?: IllegalStateException("no model")
    }

    @Volatile
    private var modelCache: List<String> = emptyList()

    /** Model dự phòng: các bản Lite (nhanh, ít quá tải) trước, rồi tới bản thường; mới nhất trước. */
    private suspend fun fallbackModels(s: AppSettings, exclude: String): List<String> {
        if (modelCache.isEmpty()) {
            modelCache = runCatching { gemini.listModels(s.geminiBaseUrl, s.geminiKey) }.getOrDefault(emptyList())
        }
        val cands = GeminiClient.benchCandidates(modelCache, limit = 12).filter { it != exclude }
        return cands.filter { it.contains("lite") } + cands.filterNot { it.contains("lite") }
    }

    /** Mỗi lần thử 1 model tối đa 20 s – quá thì chuyển model khác. */
    private val genHttp: OkHttpClient by lazy {
        httpClient.newBuilder().readTimeout(25, TimeUnit.SECONDS).build()
    }

    /** Hỏi API danh sách model rồi chọn Flash tốt nhất; lưu lại để lần sau khỏi hỏi. */
    suspend fun resolveGeminiModel(s: AppSettings): String {
        val picked = runCatching {
            val all = gemini.listModels(s.geminiBaseUrl, s.geminiKey)
            modelCache = all
            Log.i("HuyFanAI", "models: ${all.filter { it.contains("flash") }.joinToString()}")
            GeminiClient.pickBest(all, s.preferLite)
        }.onFailure { Log.w("HuyFanAI", "listModels failed: ${it.javaClass.simpleName}: ${it.message?.take(300)}") }
            .getOrNull() ?: if (s.preferLite) "gemini-flash-lite-latest" else "gemini-flash-latest"
        Log.i("HuyFanAI", "resolved model: $picked")
        settings.update { it.copy(geminiResolvedModel = picked) }
        return picked
    }

    suspend fun listModels(p: Provider): Result<List<String>> = runCatching {
        val s = settings.get()
        when (p) {
            Provider.GEMINI -> {
                if (s.geminiKey.isBlank()) throw AiException("no key", "Nhập Gemini API key trước đã.")
                gemini.listModels(s.geminiBaseUrl, s.geminiKey)
                    .filter { GeminiClient.isChatModel(it) }
                    .sortedWith(compareByDescending<String> { GeminiClient.versionOf(it) }.thenBy { it })
            }
            Provider.OPENAI -> openai.listModels(s.openaiBaseUrl, s.openaiKey)
        }
    }.recoverCatching { throw toAiException(it, p) }

    data class Bench(val model: String, val ms: Long, val ok: Boolean, val note: String)

    /**
     * Gửi cùng 1 câu hỏi thật tới nhiều model Flash song song, đo thời gian trả lời đúng định dạng.
     * Giúp chọn model nhanh nhất cho key/khu vực của người dùng.
     */
    suspend fun benchmark(): Result<List<Bench>> = runCatching {
        val s = settings.get()
        if (s.geminiKey.isBlank()) throw AiException("no key", "Nhập Gemini API key trước đã.")
        val models = GeminiClient.benchCandidates(gemini.listModels(s.geminiBaseUrl, s.geminiKey))
        val system = PromptBuilder.system(s, emptyList(), emptyList())
        val turns = listOf(Turn(Turn.USER, "\"cảm ơn\" tiếng Hàn là gì?"))
        val client = benchHttp
        coroutineScope {
            models.map { m ->
                async {
                    val t0 = System.currentTimeMillis()
                    try {
                        val raw = gemini.generate(s.geminiBaseUrl, s.geminiKey, m, system, turns, s.temperature, s.fastMode, client)
                        val r = ReplyParser.parse(raw)
                        Bench(m, System.currentTimeMillis() - t0, r.vocab.isNotEmpty(), r.vocab.firstOrNull()?.ko ?: r.reply.take(30))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val msg = when (e) {
                            is ProviderHttpException -> "HTTP ${e.code}"
                            is SocketTimeoutException -> "quá ${BENCH_TIMEOUT_S}s"
                            else -> e.javaClass.simpleName
                        }
                        Bench(m, System.currentTimeMillis() - t0, false, msg)
                    }
                }
            }.awaitAll()
        }.sortedWith(compareByDescending<Bench> { it.ok }.thenBy { it.ms })
            .onEach { Log.i("HuyFanAI", "bench ${it.model}: ${it.ms}ms ok=${it.ok} ${it.note}") }
    }.recoverCatching { throw toAiException(it, Provider.GEMINI) }

    private val benchHttp: OkHttpClient by lazy {
        httpClient.newBuilder()
            .readTimeout(BENCH_TIMEOUT_S, TimeUnit.SECONDS)
            .callTimeout(BENCH_TIMEOUT_S + 5, TimeUnit.SECONDS)
            .build()
    }

    /** Gửi 1 câu thử, trả về "model · độ trễ". */
    suspend fun test(p: Provider): Result<String> = runCatching {
        val s = settings.get().copy(provider = p, fallback = false)
        val t0 = System.currentTimeMillis()
        val r = call(p, s, PromptBuilder.system(s, emptyList(), emptyList()), listOf(Turn(Turn.USER, "\"cảm ơn\" tiếng Hàn là gì?")))
        "OK · ${r.model} · ${System.currentTimeMillis() - t0} ms\n${r.reply}"
    }.recoverCatching { throw toAiException(it, p) }

    private fun toAiException(e: Throwable, p: Provider): AiException {
        Log.w("HuyFanAI", "$p failed: ${e.javaClass.simpleName}: ${e.message?.take(300)}")
        if (e is AiException) return e
        val name = if (p == Provider.GEMINI) "Gemini" else "API"
        return when (e) {
            is ProviderHttpException -> {
                val b = e.body
                val msg = when {
                    e.code == 400 && (b.contains("API_KEY_INVALID") || b.contains("API key not valid")) ->
                        "API key $name không hợp lệ. Kiểm tra lại key trong Cài đặt nha."
                    e.code == 400 && b.contains("location", ignoreCase = true) ->
                        "$name chưa hỗ trợ khu vực của ông (lỗi vị trí)."
                    e.code == 401 -> "API key $name sai hoặc đã bị thu hồi."
                    e.code == 403 -> "Key $name không có quyền dùng model này."
                    e.code == 404 -> "Không tìm thấy model trên $name. Vào Cài đặt chọn model khác nha."
                    e.code == 429 -> "Hết hạn mức miễn phí của $name rồi ông ơi (429). Đợi chút hoặc thêm Groq key dự phòng trong Cài đặt."
                    e.code >= 500 -> "Máy chủ $name đang quá tải (${e.code}). Thử lại sau chút, hoặc thêm Groq key dự phòng trong Cài đặt → AI & API."
                    else -> "$name báo lỗi ${e.code}."
                }
                AiException(e.message ?: "http", msg, e.code)
            }
            is UnknownHostException -> AiException("offline", "Mất mạng rồi ông ơi, kiểm tra Wi-Fi/4G giúp tui.")
            is SocketTimeoutException -> AiException("timeout", "$name đang quá tải nên trả lời quá lâu. Thử lại sau chút, hoặc thêm Groq key dự phòng trong Cài đặt → AI & API.")
            is IOException -> AiException(e.message ?: "io", "Lỗi kết nối tới $name: ${e.message}")
            else -> AiException(e.message ?: "error", "Lỗi xử lý câu trả lời: ${e.message}")
        }
    }
}
