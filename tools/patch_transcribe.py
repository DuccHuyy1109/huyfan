import io, sys, os

root = sys.argv[1]  # .../huyfan


def patch(rel, pairs):
    p = os.path.join(root, rel)
    s = io.open(p, encoding='utf-8').read()
    for a, b in pairs:
        if a not in s:
            raise SystemExit(rel + ' NOT FOUND: ' + a[:90])
        s = s.replace(a, b, 1)
    io.open(p, 'w', encoding='utf-8').write(s)


# Turn mang theo âm thanh (cho Gemini nghe thẳng)
patch('ai/AiModels.kt', [
    ('''data class Turn(val role: String, val text: String, val image: String? = null) {''',
     '''data class Turn(val role: String, val text: String, val image: String? = null, val audioWav: ByteArray? = null) {'''),
])

patch('ai/Clients.kt', [
    # Gemini: gửi âm thanh dạng inline_data audio/wav
    ('''            t.image?.let { com.hankukko.huyfan.data.ChatImages.base64(it) }?.let { b64 ->
                parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", b64)))
            }''', '''            t.image?.let { com.hankukko.huyfan.data.ChatImages.base64(it) }?.let { b64 ->
                parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", b64)))
            }
            t.audioWav?.let { wav ->
                val b64 = android.util.Base64.encodeToString(wav, android.util.Base64.NO_WRAP)
                parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "audio/wav").put("data", b64)))
            }'''),
    ('''                if (last != null && last.role == t.role) out[out.lastIndex] = Turn(t.role, last.text + "\\n" + t.text, t.image ?: last.image)''',
     '''                if (last != null && last.role == t.role) out[out.lastIndex] = Turn(t.role, last.text + "\\n" + t.text, t.image ?: last.image, t.audioWav ?: last.audioWav)'''),
    # Whisper (Groq / OpenAI): nhận dạng giọng nói, tự biết ngôn ngữ
    ('''class OpenAiCompatClient(private val http: OkHttpClient) {

    private val noJsonMode = mutableSetOf<String>()''', '''class OpenAiCompatClient(private val http: OkHttpClient) {

    private val noJsonMode = mutableSetOf<String>()

    data class Transcript(val text: String, val language: String, val noSpeech: Double)

    /** POST {base}/audio/transcriptions (Whisper) – không chỉ định ngôn ngữ để model tự nhận ra Việt / Hàn. */
    suspend fun transcribe(baseUrl: String, key: String, wav: ByteArray, model: String, prompt: String): Transcript {
        val body = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("file", "speech.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("temperature", "0")
            .addFormDataPart("prompt", prompt)
            .build()
        val req = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/audio/transcriptions")
            .header("Authorization", "Bearer $key")
            .post(body)
            .build()
        val t0 = System.currentTimeMillis()
        http.newCall(req).await().use { res ->
            val text = res.body?.string().orEmpty()
            Log.i("HuyFanAI", "whisper $model → HTTP ${res.code} in ${System.currentTimeMillis() - t0}ms")
            if (!res.isSuccessful) throw ProviderHttpException(res.code, text)
            val o = JSONObject(text)
            val segs = o.optJSONArray("segments") ?: JSONArray()
            val noSpeech = if (segs.length() == 0) 0.0 else
                (0 until segs.length()).minOf { segs.optJSONObject(it)?.optDouble("no_speech_prob", 0.0) ?: 0.0 }
            return Transcript(o.optString("text").trim(), o.optString("language"), noSpeech)
        }
    }'''),
])

patch('ai/AiService.kt', [
    ('''    /** Gọi AI trả JSON theo [schema] riêng (vd hội thoại nhập vai) – vẫn có dự phòng Gemini ↔ Groq. */''', '''    /** Kết quả nhận dạng 1 câu nói: ngôn ngữ ("vi" / "ko" / "" nếu không phải lời nói) + chữ. */
    data class Heard(val lang: String, val text: String, val via: String)

    @Volatile
    private var whisperModel: String? = null

    /**
     * Nhận dạng 1 câu nói mà KHÔNG cần biết trước ngôn ngữ (dịch tự động):
     * ưu tiên Whisper trên Groq (nhanh, tự nhận ra Việt/Hàn), không có thì để Gemini nghe thẳng âm thanh.
     */
    suspend fun transcribeAuto(wav: ByteArray): Heard {
        val s = settings.get()
        val groq = s.openaiKey.isNotBlank() && s.openaiBaseUrl.contains("groq.com")
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
        }
        // Gemini nghe thẳng âm thanh
        if (s.geminiKey.isBlank()) throw AiException("no key", "Cần Groq key hoặc Gemini key để dịch tự động.")
        val system = """Bạn nhận 1 đoạn ghi âm ngắn trong cuộc nói chuyện giữa người Việt và người Hàn.
Chép lại CHÍNH XÁC lời nói (đúng ngôn ngữ gốc, có dấu, có dấu câu) và cho biết ngôn ngữ: "vi" (tiếng Việt) hoặc "ko" (tiếng Hàn).
Không có lời nói rõ ràng (chỉ tiếng ồn, nhạc, im lặng) → lang = "none", text = "". Tuyệt đối không bịa.
Chỉ trả JSON: {"lang":"vi","text":""}"""
        val schema = JSONObject().put("type", "OBJECT")
            .put("properties", JSONObject().put("lang", JSONObject().put("type", "STRING")).put("text", JSONObject().put("type", "STRING")))
            .put("required", org.json.JSONArray(listOf("lang", "text")))
        val (raw, _) = callGemini(s, system, listOf(Turn(Turn.USER, "Chép lại đoạn ghi âm này.", audioWav = wav)), schema)
        val o = ReplyParser.extractJson(raw) ?: return Heard("", "", "gemini")
        val text = o.optString("text").trim()
        val lang = o.optString("lang").lowercase()
        if (text.isBlank() || lang == "none" || isHallucination(text)) return Heard("", "", "gemini")
        val hangul = text.count { it in '\\uAC00'..'\\uD7A3' }
        return Heard(if (lang.startsWith("ko") || hangul * 2 >= text.count { it.isLetter() }) "ko" else "vi", text, "gemini")
    }

    /** Câu Whisper hay "bịa" ra khi chỉ có tiếng ồn / im lặng. */
    private fun isHallucination(t: String): Boolean {
        val x = t.lowercase().trim().trimEnd('.', '!', '?', '…')
        if (x.length < 2) return true
        val bad = listOf(
            "cảm ơn các bạn đã theo dõi", "hãy subscribe", "đăng ký kênh", "ghiền mì gõ", "hẹn gặp lại các bạn",
            "시청해 주셔서 감사합니다", "구독과 좋아요", "구독 좋아요", "mbc 뉴스", "끝까지 시청", "감사합니다 시청",
            "thank you for watching", "subtitles by",
        )
        return bad.any { x.contains(it) }
    }

    /** Gọi AI trả JSON theo [schema] riêng (vd hội thoại nhập vai) – vẫn có dự phòng Gemini ↔ Groq. */'''),
])
print('ok')
