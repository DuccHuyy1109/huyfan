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


# ---- VAD: "bịt tai" khi app đang tự đọc to (bỏ hẳn phần âm thanh, không gửi đi dịch) ----
patch(J + 'voice/VadRecorder.kt', [
    ('''    private var job: Job? = null
    @Volatile
    var paused = false''', '''    private var job: Job? = null
    @Volatile
    var paused = false

    /** App đang tự đọc to → không nghe (tránh nghe lại chính tiếng loa của mình rồi dịch tiếp). */
    @Volatile
    private var muted = false

    @Volatile
    private var muteUntil = 0L

    /** Bật/tắt "bịt tai". Tắt xong vẫn bỏ thêm [tailMs] để tiếng vang của loa tắt hẳn. */
    fun mute(on: Boolean, tailMs: Long = 700) {
        if (on) muted = true
        else { muteUntil = System.currentTimeMillis() + tailMs; muted = false }
    }'''),
    ('''            fun flush() {''', '''            /** Bỏ câu đang thu dở (tiếng loa của app) – không gửi đi nhận dạng. */
            fun discard() {
                if (speaking) Log.i(TAG, "bỏ ${segMs}ms âm thanh lúc app đang đọc to")
                seg.reset(); speaking = false; silentMs = 0; segMs = 0; speechMs = 0; loud = 0; voicedRun = 0
                nextPartial = PARTIAL_FIRST_MS
                seq++
            }
            fun flush() {'''),
    ('''                    if (paused) {
                        if (speaking) flush()
                        pre.clear(); onLevel(0f, false); continue
                    }''', '''                    if (paused) {
                        if (speaking) flush()
                        pre.clear(); onLevel(0f, false); continue
                    }
                    if (muted || System.currentTimeMillis() < muteUntil) {
                        if (speaking) discard()
                        pre.clear(); loud = 0; onLevel(0f, false); continue
                    }'''),
])

# ---- AiService: chặn câu "cảm ơn" Whisper bịa ra khi chỉ có tiếng ồn ----
patch(J + 'ai/AiService.kt', [
    ('''                Log.i("HuyFanAI", "whisper đoán '${r.language}' (${r.text.take(40)}) → không phải Việt/Hàn")''',
     '''                Log.i("HuyFanAI", "whisper đoán '${r.language}' (${r.text.take(40)}) → không phải Việt/Hàn")
                // "Thank you." / "Спасибо." / "ありがとう" trên tiếng ồn = Whisper bịa, không phải lời nói
                if (isNoiseThanks(r.text)) return Heard("", "", "whisper")'''),
    ('''                    // Cả 2 bản đều đoán mò → coi như nghe không rõ, không dịch bừa
                    if (maxOf(sv, sk) < -1.0) return Heard("", "", "whisper")''',
     '''                    // Cả 2 bản đều đoán mò → coi như nghe không rõ, không dịch bừa
                    if (maxOf(sv, sk) < -1.0) return Heard("", "", "whisper")
                    // 1 bản là câu bịa quen thuộc ("Cảm ơn các bạn đã theo dõi"…) → đoạn này chỉ là tiếng ồn
                    if (vi?.text?.let(::isHallucination) == true || ko?.text?.let(::isHallucination) == true) {
                        Log.i("HuyFanAI", "nghe lại ra câu bịa → bỏ (chỉ là tiếng ồn)")
                        return Heard("", "", "whisper")
                    }'''),
    ('''    /** Câu Whisper hay "bịa" ra khi chỉ có tiếng ồn / im lặng. */''', '''    /** Câu cảm ơn ngắn bằng ngôn ngữ khác – Whisper hay bịa ra khi chỉ nghe thấy tiếng ồn. */
    private fun isNoiseThanks(t: String): Boolean {
        val x = t.lowercase().trim().trim('.', '!', '?', '…', ' ', '。', '！')
        return x in setOf(
            "thank you", "thanks", "thank you very much", "thank you so much", "thanks for watching",
            "спасибо", "спасибо за просмотр", "merci", "gracias", "danke", "grazie", "obrigado", "obrigada",
            "ありがとうございました", "ありがとうございます", "ありがとう", "谢谢", "謝謝", "谢谢观看", "dziękuję",
        )
    }

    /** Câu Whisper hay "bịa" ra khi chỉ có tiếng ồn / im lặng. */'''),
])

# ---- Màn Dịch tự động: bịt tai khi đọc to + chặn câu trùng tiếng loa vừa đọc ----
A = J + 'ui/screens/AutoInterpreterScreen.kt'
patch(A, [
    ('''    // Nhận dạng + dịch tối đa 3 câu song song để theo kịp người nói nhanh
    val gate = remember { Semaphore(3) }''', '''    // Nhận dạng + dịch tối đa 3 câu song song để theo kịp người nói nhanh
    val gate = remember { Semaphore(3) }
    // App đang đọc to bản dịch → micro "bịt tai" để không nghe lại tiếng loa của chính mình
    val ttsOn by c.speaker.speaking.collectAsState()
    // Những câu app vừa đọc to (lớp chặn thứ 2: lỡ còn lọt tiếng vang thì cũng không dịch lại)
    val spoken = remember { mutableListOf<Pair<String, Long>>() }
    fun speak(text: String, korean: Boolean) {
        spoken += text to System.currentTimeMillis()
        c.speaker.speakAs(text, korean)
    }'''),
    ('''                if (heard.lang.isBlank() || heard.text.isBlank()) {
                    lines.removeAll { it.id == id } // chỉ là tiếng ồn
                    return@withPermit
                }''', '''                if (heard.lang.isBlank() || heard.text.isBlank()) {
                    lines.removeAll { it.id == id } // chỉ là tiếng ồn
                    return@withPermit
                }
                val now = System.currentTimeMillis()
                spoken.removeAll { now - it.second > 30_000 }
                if (spoken.any { echoOf(heard.text, it.first) }) {
                    android.util.Log.i("HuyFanAI", "bỏ tiếng vang của loa: '${heard.text.take(40)}'")
                    lines.removeAll { it.id == id }
                    return@withPermit
                }'''),
    ('''                            onSpeak = { c.speaker.speakAs(b.translation, korean = b.side == Side.VI) },''',
     '''                            onSpeak = { speak(b.translation, korean = b.side == Side.VI) },'''),
    ('''            onSpeak = { c.speaker.speakAs(b.translation, korean = b.side == Side.VI) },''',
     '''            onSpeak = { speak(b.translation, korean = b.side == Side.VI) },'''),
    ('''                            !listening -> "Đã tạm dừng"
                            speaking -> "Đang nghe…"''', '''                            !listening -> "Đã tạm dừng"
                            ttsOn -> "Đang đọc to · tạm không nghe"
                            speaking -> "Đang nghe…"'''),
    ('''private fun blocksOf(lines: List<AutoLine>): List<Block> {''', '''/** Câu vừa nghe có phải chính tiếng loa của app (câu dịch vừa đọc to) không – so khớp gần đúng. */
private fun echoOf(heard: String, said: String): Boolean {
    fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
    val a = norm(heard)
    val b = norm(said)
    if (a.length < 2 || b.isEmpty()) return false
    if (b.contains(a) || a.contains(b)) return true
    fun grams(s: String) = s.windowed(2).toHashSet()
    val ga = grams(a)
    val gb = grams(b)
    if (ga.isEmpty() || gb.isEmpty()) return false
    // Phần lớn câu nghe được nằm trong câu đã đọc → là tiếng vang
    return ga.count { it in gb }.toDouble() / ga.size >= 0.6
}

private fun blocksOf(lines: List<AutoLine>): List<Block> {'''),
])

# Mute theo trạng thái loa
p = os.path.join(root, A)
s = io.open(p, encoding='utf-8').read()
anchor = '''    // Mở là nghe ngay; giữ micro (chế độ chờ không chen vào); ẩn app → tạm dừng'''
if anchor not in s:
    raise SystemExit('anchor missing')
s = s.replace(anchor, '''    LaunchedEffect(ttsOn) {
        recorder.mute(ttsOn)
        if (ttsOn) live = null
    }

''' + anchor, 1)
if 'import androidx.compose.runtime.collectAsState' not in s:
    s = s.replace('import androidx.compose.runtime.getValue', 'import androidx.compose.runtime.collectAsState\nimport androidx.compose.runtime.getValue', 1)
io.open(p, 'w', encoding='utf-8').write(s)
print('ok')
