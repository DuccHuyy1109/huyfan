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


# ---- Whisper: bỏ câu gợi ý (bị chép lại khi nghe không rõ), đọc thêm độ lặp vòng ----
patch(J + 'ai/Clients.kt', [
    ('''    data class Transcript(val text: String, val language: String, val noSpeech: Double, val logprob: Double = 0.0)''',
     '''    data class Transcript(
        val text: String,
        val language: String,
        val noSpeech: Double,
        val logprob: Double = 0.0,
        /** > 2,4 = chữ lặp đi lặp lại (Whisper bị "kẹt vòng" khi đoán mò). */
        val compression: Double = 0.0,
    )'''),
    ('''    suspend fun transcribe(baseUrl: String, key: String, wav: ByteArray, model: String, prompt: String, language: String? = null): Transcript {''',
     '''    suspend fun transcribe(baseUrl: String, key: String, wav: ByteArray, model: String, prompt: String?, language: String? = null): Transcript {'''),
    ('''            .addFormDataPart("temperature", "0")
            .addFormDataPart("prompt", prompt)
        if (language != null)''', '''            .addFormDataPart("temperature", "0")
        if (!prompt.isNullOrBlank()) builder.addFormDataPart("prompt", prompt)
        if (language != null)'''),
    ('''            var lp = 0.0
            var dur = 0.0''', '''            var lp = 0.0
            var dur = 0.0
            var comp = 0.0'''),
    ('''                lp += sg.optDouble("avg_logprob", 0.0) * d
                dur += d''', '''                lp += sg.optDouble("avg_logprob", 0.0) * d
                dur += d
                comp = maxOf(comp, sg.optDouble("compression_ratio", 0.0))'''),
    ('''noSpeech, if (dur > 0) lp / dur else 0.0)''', '''noSpeech, if (dur > 0) lp / dur else 0.0, comp)'''),
])

patch(J + 'ai/AiService.kt', [
    # Không gửi câu gợi ý nữa
    ('''        val prompt = when (language) {
            "vi" -> "Xin chào, tôi là Huy. Hôm nay bạn thế nào?"
            "ko" -> "안녕하세요, 저는 후이입니다. 오늘 어떠세요?"
            else -> "Hội thoại tiếng Việt và 한국어 대화."
        }
        openai.transcribe(s.openaiBaseUrl, s.openaiKey, wav, model, prompt, language)''',
     '''        // Không gửi câu gợi ý: nghe không rõ là Whisper chép lại nguyên câu gợi ý thành "lời nói"
        openai.transcribe(s.openaiBaseUrl, s.openaiKey, wav, model, null, language)'''),
    # Lọc câu đoán mò ngay lượt nghe đầu
    ('''                if (r.noSpeech > 0.6 || r.text.isBlank() || isHallucination(r.text)) return Heard("", "", "whisper")
                val lang = whisperLang(r)''', '''                if (r.noSpeech > 0.6 || r.text.isBlank() || isHallucination(r.text)) return Heard("", "", "whisper")
                if (r.logprob < -1.2 || r.compression > 2.4) {
                    Log.i("HuyFanAI", "whisper không chắc (tin cậy ${"%.2f".format(r.logprob)}, lặp ${"%.1f".format(r.compression)}): '${r.text.take(40)}' → bỏ")
                    return Heard("", "", "whisper")
                }
                val lang = whisperLang(r)'''),
    ('''                        if (t == null || t.text.isBlank() || isHallucination(t.text)) return Double.NEGATIVE_INFINITY''',
     '''                        if (t == null || t.text.isBlank() || isHallucination(t.text) || t.compression > 2.4) return Double.NEGATIVE_INFINITY'''),
    ('''                    if (sv.isFinite() || sk.isFinite()) {
                        return if (sk > sv) Heard("ko", ko!!.text, "whisper-ko") else Heard("vi", vi!!.text, "whisper-vi")
                    }''', '''                    // Cả 2 bản đều đoán mò → coi như nghe không rõ, không dịch bừa
                    if (maxOf(sv, sk) < -1.0) return Heard("", "", "whisper")
                    return if (sk > sv) Heard("ko", ko!!.text, "whisper-ko") else Heard("vi", vi!!.text, "whisper-vi")'''),
    ('''            "thank you for watching", "subtitles by",''', '''            "thank you for watching", "subtitles by",
            // câu gợi ý cũ bị Whisper chép lại
            "hội thoại tiếng việt", "한국어 대화", "저는 후이입니다", "hôm nay bạn thế nào",'''),
])

# ---- VAD: cắt câu chuẩn hơn khi có tiếng ồn nền ----
patch(J + 'voice/VadRecorder.kt', [
    ('''            var nextPartial = PARTIAL_FIRST_MS''', '''            var nextPartial = PARTIAL_FIRST_MS
            var voicedRun = 0'''),
    ('''                seg.reset(); speaking = false; silentMs = 0; segMs = 0; speechMs = 0; loud = 0''',
     '''                seg.reset(); speaking = false; silentMs = 0; segMs = 0; speechMs = 0; loud = 0; voicedRun = 0'''),
    ('''                        append(copy); segMs += FRAME_MS
                        if (db > floor + 7) { silentMs = 0; speechMs += FRAME_MS } else silentMs += FRAME_MS''',
     '''                        append(copy); segMs += FRAME_MS
                        if (db > floor + 7) {
                            speechMs += FRAME_MS
                            voicedRun++
                            // Phải có tiếng nói liền ~90 ms mới tính là nói tiếp; tiếng lạch cạch ngắn không làm câu dài ra
                            if (voicedRun >= 3) silentMs = 0 else silentMs += FRAME_MS
                        } else {
                            voicedRun = 0
                            silentMs += FRAME_MS
                            // Tiếng ồn nền tăng dần (quạt, xe, TV) → ngưỡng tự nâng theo
                            floor = floor * 0.99 + db * 0.01
                        }'''),
    ('''                        if (silentMs >= END_SILENCE_MS || segMs >= MAX_SEGMENT_MS) flush()''',
     '''                        // Câu đã dài → ngừng ngắn hơn cũng tính là hết câu (dịch từng câu, không dồn cục)
                        val endMs = if (segMs > LONG_SEGMENT_MS) SHORT_END_SILENCE_MS else END_SILENCE_MS
                        if (silentMs >= endMs || segMs >= MAX_SEGMENT_MS) flush()'''),
    ('''        private const val MAX_SEGMENT_MS = 12_000''', '''        private const val MAX_SEGMENT_MS = 12_000
        private const val LONG_SEGMENT_MS = 5_000
        private const val SHORT_END_SILENCE_MS = 450'''),
])
print('ok')
