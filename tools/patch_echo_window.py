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


A = J + 'ui/screens/AutoInterpreterScreen.kt'
patch(A, [
    ('''    val spoken = remember { mutableListOf<Pair<String, Long>>() }''',
     '''    val spoken = remember { mutableListOf<Pair<String, Long>>() }
    // Lúc loa vừa tắt: chỉ câu bắt đầu ngay sau đó mới có thể là tiếng vang
    val ttsEndAt = remember { longArrayOf(0L) }'''),
    ('''    fun handle(pcm: ByteArray) {''', '''    fun handle(pcm: ByteArray, ms: Long) {
        // Thời điểm người nói bắt đầu câu này
        val startedAt = System.currentTimeMillis() - ms'''),
    ('''                if (spoken.any { echoOf(heard.text, it.first) }) {''',
     '''                // Chỉ câu bắt đầu trong ~2 s sau khi loa tắt mới bị nghi là tiếng vang –
                // người thật nói lại đúng câu đó sau này vẫn được dịch bình thường
                val nearTts = ttsOn || startedAt < ttsEndAt[0] + 2_000
                if (nearTts && spoken.any { echoOf(heard.text, it.first) }) {'''),
    ('''            onSegment = { pcm, _ -> scope.launch { handle(pcm) } },''',
     '''            onSegment = { pcm, ms -> scope.launch { handle(pcm, ms) } },'''),
    ('''        recorder.mute(ttsOn)
        if (ttsOn) live = null''', '''        recorder.mute(ttsOn)
        if (ttsOn) live = null else ttsEndAt[0] = System.currentTimeMillis()'''),
])

# Nghe lại ép tiếng Việt mà chữ ra không phải tiếng Việt (vd "C'est là") → trừ điểm
patch(J + 'ai/AiService.kt', [
    ('''                        val penalty = if (korean) (if (h < 0.5) 2.0 else 0.0) else (if (h > 0.2) 2.0 else 0.0)''',
     '''                        val penalty = if (korean) (if (h < 0.5) 2.0 else 0.0) else (if (h > 0.2 || !looksVietnamese(t.text)) 2.0 else 0.0)'''),
    ('''    /** Câu cảm ơn ngắn bằng ngôn ngữ khác – Whisper hay bịa ra khi chỉ nghe thấy tiếng ồn. */''',
     '''    /** Âm tiết tiếng Việt: phụ âm đầu + nguyên âm (1–3 chữ) + phụ âm cuối. */
    private val viSyllable = Regex(
        "^(ngh|ng|nh|ch|gh|gi|kh|ph|qu|th|tr|[bcdđghklmnprstvx])?" +
            "[aăâeêioôơuưyáàảãạắằẳẵặấầẩẫậéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ]{1,3}" +
            "(ng|nh|ch|[cmnpt])?$"
    )

    /** Chữ có thật là tiếng Việt không (≥ 60% từ là âm tiết tiếng Việt hợp lệ). */
    private fun looksVietnamese(t: String): Boolean {
        val words = t.lowercase().split(Regex("[\\\\s,.!?…;:\\"“”()\\\\-]+"))
            .filter { it.isNotBlank() && !it.all(Char::isDigit) }
        if (words.isEmpty()) return false
        return words.count { viSyllable.matches(it) }.toDouble() / words.size >= 0.6
    }

    /** Câu cảm ơn ngắn bằng ngôn ngữ khác – Whisper hay bịa ra khi chỉ nghe thấy tiếng ồn. */'''),
])
print('ok')
