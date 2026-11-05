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


# ---- AiService: giữ lại từ ngắn / nói nhỏ (네, 응, 아, ừ, dạ…) ----
patch(J + 'ai/AiService.kt', [
    ('''                if (r.noSpeech > 0.6 || r.text.isBlank() || isHallucination(r.text)) return Heard("", "", "whisper")
                if (r.logprob < -1.2 || r.compression > 2.4) {''',
     '''                // Im lặng thật = Whisper vừa thấy "không có lời" vừa đoán mò (cách Whisper tự lọc).
                // Nói nhỏ / từ ngắn có thể bị chấm "không có lời" cao nhưng chữ vẫn chắc → giữ lại.
                val silent = r.noSpeech > 0.6 && (r.logprob < -0.8 || isSilenceFiller(r.text))
                if (silent || r.text.isBlank() || isHallucination(r.text)) return Heard("", "", "whisper")
                if (r.logprob < -1.5 || r.compression > 2.4) {'''),
    ('''                    if (maxOf(sv, sk) < -1.0) return Heard("", "", "whisper")''',
     '''                    if (maxOf(sv, sk) < -1.3) return Heard("", "", "whisper")'''),
    ('''        if (x.length < 2) return true''',
     '''        if (x.isEmpty()) return true
        // Từ 1 chữ vẫn là lời nói thật: 네, 응, 아, 예 / ừ, ờ, à, ạ
        if (x.length == 1 && !(x[0] in '\\uAC00'..'\\uD7A3' || x[0] in "ừờàạơ")) return true'''),
    ('''    /** Câu cảm ơn ngắn bằng ngôn ngữ khác – Whisper hay bịa ra khi chỉ nghe thấy tiếng ồn. */''',
     '''    /** Câu Whisper hay bịa trên khoảng lặng ("감사합니다", "Cảm ơn"…) – chỉ bỏ khi Whisper cũng thấy "không có lời". */
    private fun isSilenceFiller(t: String): Boolean {
        val x = t.lowercase().trim().trim('.', '!', '?', '…', ' ')
        return isNoiseThanks(x) || x in setOf("감사합니다", "고맙습니다", "cảm ơn", "cảm ơn bạn", "cảm ơn các bạn", "xin cảm ơn")
    }

    /** Câu cảm ơn ngắn bằng ngôn ngữ khác – Whisper hay bịa ra khi chỉ nghe thấy tiếng ồn. */'''),
])

# ---- VAD: nhạy hơn với người nói nhỏ, nhận cả từ rất ngắn ----
patch(J + 'voice/VadRecorder.kt', [
    ('''        private const val MIN_SPEECH_MS = 350''',
     '''        private const val MIN_SPEECH_MS = 220 // đủ cho "네", "응", "ừ"'''),
    ('''                    val isLoud = db > maxOf(floor + 11, -50.0)''',
     '''                    // Người Hàn hay nói nhỏ → ngưỡng bắt đầu nói thấp hơn chút
                    val isLoud = db > maxOf(floor + 10, -52.0)'''),
])

# ---- Phiên dịch: từ ngắn / câu cụt vẫn dịch, đoán ý theo ngữ cảnh ----
patch(J + 'assistant/Interpreter.kt', [
    ('''changed = true nếu fixed khác câu nghe được về chữ''',
     '''NGƯỜI NÓI NHỎ / NGẮT TỪNG TỪ: người Hàn hay nói nhỏ, ngắt quãng, có khi chỉ 1–2 từ (네, 응, 아니요, 진짜요?, 그래요, 잠깐만요 / ừ, dạ, vâng, không…).
   Vẫn LUÔN dịch, không bỏ trống. Câu cụt hoặc nghe chưa trọn → đoán ý hợp lý nhất theo mấy câu trước trong cuộc nói chuyện
   (vd câu trước hỏi "Anh ăn chưa?" mà giờ nghe "네" → "Dạ, rồi ạ."). Nghe nhầm từ ngắn (vd "내"/"네") → sửa theo ngữ cảnh.

changed = true nếu fixed khác câu nghe được về chữ'''),
])
print('ok')
