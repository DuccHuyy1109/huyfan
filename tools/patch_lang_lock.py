import io, sys, os

root = sys.argv[1]  # .../app/src/main
J = 'java/com/hankukko/huyfan/'


def patch(rel, pairs):
    p = os.path.join(root, rel)
    s = io.open(p, encoding='utf-8').read()
    for a, b in pairs:
        if a not in s:
            raise SystemExit(rel + ' NOT FOUND: ' + a[:90])
        s = s.replace(a, b, 1)
    io.open(p, 'w', encoding='utf-8').write(s)


# ---- Whisper: ép ngôn ngữ + điểm tin cậy ----
patch(J + 'ai/Clients.kt', [
    ('''    data class Transcript(val text: String, val language: String, val noSpeech: Double)

    /** POST {base}/audio/transcriptions (Whisper) – không chỉ định ngôn ngữ để model tự nhận ra Việt / Hàn. */
    suspend fun transcribe(baseUrl: String, key: String, wav: ByteArray, model: String, prompt: String): Transcript {
        val body = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("file", "speech.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("temperature", "0")
            .addFormDataPart("prompt", prompt)
            .build()''', '''    /** [logprob] = độ tin cậy trung bình (gần 0 = chắc chắn, càng âm càng đoán mò). */
    data class Transcript(val text: String, val language: String, val noSpeech: Double, val logprob: Double = 0.0)

    /**
     * POST {base}/audio/transcriptions (Whisper).
     * [language] = null → model tự nhận ra ngôn ngữ; "vi"/"ko" → ép nghe theo ngôn ngữ đó.
     */
    suspend fun transcribe(baseUrl: String, key: String, wav: ByteArray, model: String, prompt: String, language: String? = null): Transcript {
        val builder = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("file", "speech.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("temperature", "0")
            .addFormDataPart("prompt", prompt)
        if (language != null) builder.addFormDataPart("language", language)
        val body = builder.build()'''),
    ('''            Log.i("HuyFanAI", "whisper $model → HTTP ${res.code} in ${System.currentTimeMillis() - t0}ms")''',
     '''            Log.i("HuyFanAI", "whisper $model${language?.let { " [$it]" } ?: ""} → HTTP ${res.code} in ${System.currentTimeMillis() - t0}ms")'''),
    ('''            return Transcript(o.optString("text").trim(), o.optString("language"), noSpeech)''',
     '''            var lp = 0.0
            var dur = 0.0
            for (i in 0 until segs.length()) {
                val sg = segs.optJSONObject(i) ?: continue
                val d = (sg.optDouble("end", 0.0) - sg.optDouble("start", 0.0)).coerceAtLeast(0.1)
                lp += sg.optDouble("avg_logprob", 0.0) * d
                dur += d
            }
            return Transcript(o.optString("text").trim(), o.optString("language"), noSpeech, if (dur > 0) lp / dur else 0.0)'''),
])

# ---- AiService: chỉ chấp nhận Việt / Hàn, đoán sai → nghe lại theo từng ngôn ngữ; hạn mức Groq ----
patch(J + 'ai/AiService.kt', [
    ('''    /** [quick] = chữ tạm khi đang nói: chỉ dùng Whisper (nhanh), lỗi thì thôi, không gọi Gemini. */
    suspend fun transcribeAuto(wav: ByteArray, quick: Boolean = false): Heard {
        val s = settings.get()
        val groq = s.openaiKey.isNotBlank() && s.openaiBaseUrl.contains("groq.com")
        if (quick && !groq) return Heard("", "", "none")
        if (groq) {
            val r = runCatching {
                val model = whisperModel ?: run {
                    val all = runCatching { openai.listModels(s.openaiBaseUrl, s.openaiKey) }.getOrDefault(emptyList())
                    (all.firstOrNull { it.contains("whisper-large-v3-turbo") } ?: all.firstOrNull { it.contains("whisper") } ?: "whisper-large-v3-turbo")
                        .also { whisperModel = it }
                }
                openai.transcribe(s.openaiBaseUrl, s.openaiKey, wav, model, "Hội thoại tiếng Việt và 한국어 대화.")
            }.onFailure { Log.w("HuyFanAI", "whisper lỗi: ${it.message?.take(200)}") }.getOrNull()
            if (r != null) {
                val text = r.text
                if (r.noSpeech > 0.6 || text.isBlank() || isHallucination(text)) return Heard("", "", "whisper")
                val hangul = text.count { it in '\\uAC00'..'\\uD7A3' }
                val lang = when {
                    hangul * 2 >= text.count { it.isLetter() } -> "ko"
                    r.language.contains("korean", true) && hangul > 0 -> "ko"
                    else -> "vi"
                }
                return Heard(lang, text, "whisper")
            }
            if (quick) return Heard("", "", "whisper")
        }''', '''    // ---- Hạn mức Whisper trên Groq (gói miễn phí: 20 lượt / phút) ----
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
        val m = Regex("""try again in (?:(\\d+)m)?([\\d.]+)(ms|s)""").find(body)
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
        val prompt = when (language) {
            "vi" -> "Xin chào, tôi là Huy. Hôm nay bạn thế nào?"
            "ko" -> "안녕하세요, 저는 후이입니다. 오늘 어떠세요?"
            else -> "Hội thoại tiếng Việt và 한국어 대화."
        }
        openai.transcribe(s.openaiBaseUrl, s.openaiKey, wav, model, prompt, language)
    }.onFailure { Log.w("HuyFanAI", "whisper lỗi: ${it.message?.take(200)}"); whisperLimited(it) }.getOrNull()

    private fun hangulRatio(t: String): Double {
        val letters = t.count { it.isLetter() }
        return if (letters == 0) 0.0 else t.count { it in '\\uAC00'..'\\uD7A3' }.toDouble() / letters
    }

    /** Whisper chỉ được hiểu là Việt hoặc Hàn: "vi" / "ko", ngôn ngữ khác (Anh, Nhật…) → null. */
    private fun whisperLang(r: OpenAiCompatClient.Transcript): String? {
        val l = r.language.lowercase()
        val h = hangulRatio(r.text)
        return when {
            (l.contains("korean") || l == "ko") && h >= 0.5 -> "ko"
            (l.contains("vietnamese") || l == "vi") && h < 0.2 -> "vi"
            else -> null
        }
    }

    /**
     * [quick] = chữ tạm khi đang nói: chỉ dùng Whisper (nhanh), lỗi / hết lượt thì thôi, không gọi Gemini.
     * Chỉ chấp nhận tiếng Việt hoặc tiếng Hàn: Whisper đoán ra ngôn ngữ khác → nghe lại ép theo cả 2 rồi chọn bản tin cậy hơn.
     */
    suspend fun transcribeAuto(wav: ByteArray, quick: Boolean = false): Heard {
        val s = settings.get()
        val groq = s.openaiKey.isNotBlank() && s.openaiBaseUrl.contains("groq.com")
        if (quick && !groq) return Heard("", "", "none")
        // Chữ tạm chỉ dùng lượt dư (chừa 8 lượt/phút cho câu đã chốt)
        if (groq && takeWhisper(1, keep = if (quick) 8 else 0)) {
            val r = whisper(s, wav, null)
            if (r != null) {
                if (r.noSpeech > 0.6 || r.text.isBlank() || isHallucination(r.text)) return Heard("", "", "whisper")
                val lang = whisperLang(r)
                if (lang != null) return Heard(lang, r.text, "whisper")
                Log.i("HuyFanAI", "whisper đoán '${r.language}' (${r.text.take(40)}) → không phải Việt/Hàn")
                // Chữ tạm: thà không hiện còn hơn hiện sai ngôn ngữ
                if (quick) return Heard("", "", "whisper")
                // Nghe lại, ép lần lượt tiếng Việt và tiếng Hàn → lấy bản Whisper tự tin hơn
                if (takeWhisper(2, keep = 0)) {
                    val (vi, ko) = coroutineScope {
                        val a = async { whisper(s, wav, "vi") }
                        val b = async { whisper(s, wav, "ko") }
                        a.await() to b.await()
                    }
                    fun score(t: OpenAiCompatClient.Transcript?, korean: Boolean): Double {
                        if (t == null || t.text.isBlank() || isHallucination(t.text)) return Double.NEGATIVE_INFINITY
                        val h = hangulRatio(t.text)
                        // Ép Hàn mà ra toàn chữ Latin (hoặc ngược lại) = đang dịch/bịa → trừ điểm nặng
                        val penalty = if (korean) (if (h < 0.5) 2.0 else 0.0) else (if (h > 0.2) 2.0 else 0.0)
                        return t.logprob - penalty
                    }
                    val sv = score(vi, false)
                    val sk = score(ko, true)
                    Log.i("HuyFanAI", "nghe lại: vi=${"%.2f".format(sv)} '${vi?.text?.take(30)}' · ko=${"%.2f".format(sk)} '${ko?.text?.take(30)}'")
                    if (sv.isFinite() || sk.isFinite()) {
                        return if (sk > sv) Heard("ko", ko!!.text, "whisper-ko") else Heard("vi", vi!!.text, "whisper-vi")
                    }
                }
                // Không đủ lượt → để Gemini nghe (chỉ được trả Việt/Hàn)
            }
        }
        if (quick) return Heard("", "", "whisper")'''),
    ('''    /** Câu Whisper hay "bịa" ra khi chỉ có tiếng ồn / im lặng. */''', '''    private companion object {
        const val WHISPER_RPM = 20
    }

    /** Câu Whisper hay "bịa" ra khi chỉ có tiếng ồn / im lặng. */'''),
    ('''        val system = """Bạn nhận 1 đoạn ghi âm ngắn trong cuộc nói chuyện giữa người Việt và người Hàn.
Chép lại CHÍNH XÁC lời nói (đúng ngôn ngữ gốc, có dấu, có dấu câu) và cho biết ngôn ngữ: "vi" (tiếng Việt) hoặc "ko" (tiếng Hàn).''', '''        val system = """Bạn nhận 1 đoạn ghi âm ngắn trong cuộc nói chuyện giữa người Việt và người Hàn.
Chép lại CHÍNH XÁC lời nói (đúng ngôn ngữ gốc, có dấu, có dấu câu) và cho biết ngôn ngữ: "vi" (tiếng Việt) hoặc "ko" (tiếng Hàn).
Người nói CHỈ dùng tiếng Việt hoặc tiếng Hàn: nghe giống tiếng Anh/ngôn ngữ khác thì đó là tiếng Việt hoặc tiếng Hàn phát âm chưa rõ → chọn ngôn ngữ gần nhất và chép bằng ngôn ngữ đó, KHÔNG dịch, KHÔNG chép tiếng Anh.'''),
])
print('ok')
