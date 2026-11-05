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


# ======================= VadRecorder: chỉ gửi đoạn có GIỌNG NGƯỜI =======================
V = J + 'voice/VadRecorder.kt'
patch(V, [
    ('''    private val onSegment: (pcm: ByteArray, ms: Long) -> Unit,''',
     '''    /** [speechMs] = thời gian thật sự có tiếng nói trong câu (để kiểm tra chữ nhận dạng có hợp lý không). */
    private val onSegment: (pcm: ByteArray, ms: Long, speechMs: Long) -> Unit,'''),
    ('''        private const val PARTIAL_EVERY_MS = 1_500''', '''        private const val PARTIAL_EVERY_MS = 1_500
        /** Câu phải có ít nhất chừng này ms giọng người (dây thanh rung) – tiếng gõ, sột soạt, lạch cạch không có. */
        private const val MIN_VOICED_MS = 120

        /**
         * Khung 30 ms này có phải giọng người không: giọng nói (nguyên âm) dao động lặp lại đều theo cao độ 80–400 Hz,
         * còn tiếng gõ / sột soạt / quạt thì không → đo tự tương quan theo cao độ.
         */
        fun isVoiced(f: ShortArray, n: Int): Boolean {
            var best = 0.0
            var lag = 40 // 400 Hz
            while (lag <= 200) { // 80 Hz
                var s = 0.0
                var e1 = 0.0
                var e2 = 0.0
                var i = 0
                while (i < n - lag) {
                    val a = f[i].toDouble()
                    val b = f[i + lag].toDouble()
                    s += a * b; e1 += a * a; e2 += b * b
                    i++
                }
                if (e1 > 0 && e2 > 0) {
                    val r = s / sqrt(e1 * e2)
                    if (r > best) best = r
                }
                lag += 2
            }
            return best > 0.55
        }'''),
    ('''            var nextPartial = PARTIAL_FIRST_MS
            var voicedRun = 0''', '''            var nextPartial = PARTIAL_FIRST_MS
            var voicedRun = 0
            var voicedMs = 0'''),
    # discard + flush: reset voicedMs, chỉ gửi khi có giọng người
    ('''                seg.reset(); speaking = false; silentMs = 0; segMs = 0; speechMs = 0; loud = 0; voicedRun = 0
                nextPartial = PARTIAL_FIRST_MS
                seq++
            }
            fun flush() {''', '''                seg.reset(); speaking = false; silentMs = 0; segMs = 0; speechMs = 0; loud = 0; voicedRun = 0; voicedMs = 0
                nextPartial = PARTIAL_FIRST_MS
                seq++
            }
            fun flush() {'''),
    ('''                if (speechMs >= MIN_SPEECH_MS) {
                    val bytes = seg.toByteArray()
                    Log.i(TAG, "câu ${segMs}ms (nói ${speechMs}ms)")
                    onSegment(bytes, segMs.toLong())
                }
                seg.reset(); speaking = false; silentMs = 0; segMs = 0; speechMs = 0; loud = 0; voicedRun = 0''',
     '''                if (speechMs >= MIN_SPEECH_MS && voicedMs >= MIN_VOICED_MS) {
                    val bytes = seg.toByteArray()
                    Log.i(TAG, "câu ${segMs}ms (nói ${speechMs}ms, giọng người ${voicedMs}ms)")
                    onSegment(bytes, segMs.toLong(), speechMs.toLong())
                } else if (speechMs >= MIN_SPEECH_MS) {
                    Log.i(TAG, "bỏ ${segMs}ms tiếng động – không có giọng người (giọng ${voicedMs}ms)")
                }
                seg.reset(); speaking = false; silentMs = 0; segMs = 0; speechMs = 0; loud = 0; voicedRun = 0; voicedMs = 0'''),
    # đếm giọng người: lúc vừa bắt đầu (pre-roll) và trong câu
    ('''                            pre.forEach { append(it); segMs += FRAME_MS }''',
     '''                            pre.forEach { append(it); segMs += FRAME_MS; if (isVoiced(it, it.size)) voicedMs += FRAME_MS }'''),
    ('''                        if (db > floor + 7) {
                            speechMs += FRAME_MS''', '''                        if (db > floor + 7) {
                            speechMs += FRAME_MS
                            if (isVoiced(copy, n)) voicedMs += FRAME_MS'''),
    # chữ tạm: chỉ khi đã nghe ra giọng người (đỡ tốn lượt Groq cho tiếng động)
    ('''                        if (onPartial != null && segMs >= nextPartial && silentMs < END_SILENCE_MS) {''',
     '''                        if (onPartial != null && segMs >= nextPartial && silentMs < END_SILENCE_MS && voicedMs >= MIN_VOICED_MS) {'''),
])

# ======================= AiService =======================
A = J + 'ai/AiService.kt'
patch(A, [
    ('''    suspend fun transcribeAuto(wav: ByteArray, quick: Boolean = false): Heard {
        val s = settings.get()
        val groq = s.openaiKey.isNotBlank() && s.openaiBaseUrl.contains("groq.com")
        if (quick && !groq) return Heard("", "", "none")''',
     '''    suspend fun transcribeAuto(wav: ByteArray, quick: Boolean = false, speechMs: Long = 0): Heard {
        val s = settings.get()
        val groq = s.openaiKey.isNotBlank() && s.openaiBaseUrl.contains("groq.com")
        if (quick && !groq) return Heard("", "", "none")
        // Nói bao lâu thì được tối đa bấy nhiêu chữ (~9 âm tiết / giây + 2): tiếng động ngắn mà ra cả câu dài = Whisper bịa
        val maxUnits = if (speechMs > 0) 2 + 9 * speechMs / 1000.0 else Double.MAX_VALUE
        fun tooLong(t: String) = speechUnits(t) > maxUnits'''),
    ('''                // Im lặng thật = Whisper vừa thấy "không có lời" vừa đoán mò (cách Whisper tự lọc).
                // Nói nhỏ / từ ngắn có thể bị chấm "không có lời" cao nhưng chữ vẫn chắc → giữ lại.
                val silent = r.noSpeech > 0.6 && (r.logprob < -0.8 || isSilenceFiller(r.text))
                if (silent || r.text.isBlank() || isHallucination(r.text)) return Heard("", "", "whisper")
                if (r.logprob < -1.5 || r.compression > 2.4) {
                    Log.i("HuyFanAI", "whisper không chắc (tin cậy ${"%.2f".format(r.logprob)}, lặp ${"%.1f".format(r.compression)}): '${r.text.take(40)}' → bỏ")
                    return Heard("", "", "whisper")
                }
                val lang = whisperLang(r)
                if (lang != null) return Heard(lang, r.text, "whisper")''',
     '''                val tag = "tin cậy ${"%.2f".format(r.logprob)} · không-lời ${"%.2f".format(r.noSpeech)} · ${speechUnits(r.text)} chữ/${speechMs}ms"
                // Im lặng / tiếng động: Whisper chắc "không có lời", hoặc khá chắc mà chữ lại đoán mò / là câu hay bịa
                val silent = r.noSpeech > 0.8 || (r.noSpeech > 0.5 && (r.logprob < -0.7 || isSilenceFiller(r.text)))
                if (silent || r.text.isBlank() || isHallucination(r.text) || r.logprob < -1.5 || r.compression > 2.4 || tooLong(r.text)) {
                    if (r.text.isNotBlank()) Log.i("HuyFanAI", "bỏ ($tag): '${r.text.take(40)}'")
                    return Heard("", "", "whisper")
                }
                val lang = whisperLang(r)
                if (lang != null) {
                    Log.i("HuyFanAI", "nghe [$lang] ($tag): '${r.text.take(40)}'")
                    return Heard(lang, r.text, "whisper")
                }'''),
    ('''                        if (t == null || t.text.isBlank() || isHallucination(t.text) || t.compression > 2.4) return Double.NEGATIVE_INFINITY''',
     '''                        if (t == null || t.text.isBlank() || isHallucination(t.text) || t.compression > 2.4 || tooLong(t.text) || t.noSpeech > 0.9) return Double.NEGATIVE_INFINITY'''),
    # Gemini: câu lệnh ngắn, không để Gemini chép lại câu lệnh thành "lời nói"
    ('''        val (raw, _) = callGemini(s, system, listOf(Turn(Turn.USER, "Chép lại đoạn ghi âm này.", audioWav = wav)), schema)''',
     '''        val (raw, _) = callGemini(s, system, listOf(Turn(Turn.USER, "[ghi âm]", audioWav = wav)), schema)'''),
    ('''        if (text.isBlank() || lang == "none" || isHallucination(text)) return Heard("", "", "gemini")''',
     '''        if (text.isBlank() || lang == "none" || isHallucination(text) || tooLong(text)) {
            if (text.isNotBlank()) Log.i("HuyFanAI", "Gemini nghe ra câu bịa / quá dài → bỏ: '${text.take(40)}'")
            return Heard("", "", "gemini")
        }'''),
    ('''            "thank you for watching", "subtitles by",''',
     '''            "thank you for watching", "subtitles by",
            // câu kết video YouTube Whisper thuộc lòng
            "không bỏ lỡ những video", "video hấp dẫn", "những video tiếp theo", "video tiếp theo", "nhấn like", "bấm like",
            "nhấn chuông", "đăng ký cho kênh", "cảm ơn các bạn đã xem", "la la school",
            "시청해 주셔서", "시청해주셔서", "다음 영상에서", "구독 부탁", "좋아요와 구독", "알림 설정", "자막 제공", "kbs 뉴스", "sbs 뉴스",
            // Gemini chép lại câu lệnh thay vì lời nói
            "chép lại đoạn ghi âm", "đoạn ghi âm này", "ghi âm]", "녹음 내용을", "받아적어",'''),
    ('''    /** Âm tiết tiếng Việt: phụ âm đầu + nguyên âm (1–3 chữ) + phụ âm cuối. */''',
     '''    /** Số "chữ" để so với thời gian nói: âm tiết tiếng Hàn + từ chữ Latin (tiếng Việt mỗi từ = 1 âm tiết). */
    private fun speechUnits(t: String): Int {
        val hangul = t.count { it in '\\uAC00'..'\\uD7A3' }
        val words = t.split(Regex("\\\\s+")).count { w -> w.any { it.isLetter() && it !in '\\uAC00'..'\\uD7A3' } }
        return hangul + words
    }

    /** Âm tiết tiếng Việt: phụ âm đầu + nguyên âm (1–3 chữ) + phụ âm cuối. */'''),
])

# ======================= Màn Dịch tự động: truyền thời gian nói =======================
S = J + 'ui/screens/AutoInterpreterScreen.kt'
patch(S, [
    ('''    fun handle(pcm: ByteArray, ms: Long) {''', '''    fun handle(pcm: ByteArray, ms: Long, speechMs: Long) {'''),
    ('''c.ai.transcribeAuto(VadRecorder.wav(pcm))''', '''c.ai.transcribeAuto(VadRecorder.wav(pcm), speechMs = speechMs)'''),
    ('''            onSegment = { pcm, ms -> scope.launch { handle(pcm, ms) } },''',
     '''            onSegment = { pcm, ms, speechMs -> scope.launch { handle(pcm, ms, speechMs) } },'''),
])
print('ok')
