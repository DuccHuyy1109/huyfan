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


# ---------- Khởi tạo ngôn ngữ trước mọi thứ ----------
patch(J + 'HuyFanApp.kt', [
    ('''        super.onCreate()
        container = AppContainer(this)''',
     '''        super.onCreate()
        // Ngôn ngữ đang học quyết định CSDL + cài đặt riêng → phải đọc trước khi tạo container
        Lang.init(this)
        container = AppContainer(this)'''),
])

# ---------- CSDL riêng cho mỗi ngôn ngữ ----------
patch(J + 'data/Daos.kt', [
    ('''Room.databaseBuilder(context, AppDatabase::class.java, "huyfan.db")''',
     '''Room.databaseBuilder(context, AppDatabase::class.java, com.hankukko.huyfan.Lang.current.dbName)'''),
])

# ---------- Cài đặt riêng theo ngôn ngữ: prompt, link Sheets, bộ đọc + giọng của ngôn ngữ học ----------
patch(J + 'data/Settings.kt', [
    ('''    val customPrompt = stringPreferencesKey("custom_prompt")''',
     '''    // Riêng theo ngôn ngữ học (tiếng Anh thêm hậu tố _en)
    val customPrompt get() = stringPreferencesKey(com.hankukko.huyfan.Lang.key("custom_prompt"))'''),
    ('''    val koTtsEngine = stringPreferencesKey("ko_tts_engine")''',
     '''    val koTtsEngine get() = stringPreferencesKey(com.hankukko.huyfan.Lang.key("ko_tts_engine"))'''),
    ('''    val koVoice = stringPreferencesKey("ko_voice")''',
     '''    val koVoice get() = stringPreferencesKey(com.hankukko.huyfan.Lang.key("ko_voice"))'''),
    ('''    val sheetsUrl = stringPreferencesKey("sheets_url")
    val sheetsToken = stringPreferencesKey("sheets_token")''',
     '''    val sheetsUrl get() = stringPreferencesKey(com.hankukko.huyfan.Lang.key("sheets_url"))
    val sheetsToken get() = stringPreferencesKey(com.hankukko.huyfan.Lang.key("sheets_token"))'''),
])

# ---------- Mic: "ngôn ngữ học" = Hàn hoặc Anh ----------
patch(J + 'assistant/ConversationEngine.kt', [
    ('''const val LANG_KO = "ko-KR"''',
     '''/** Mã nhận giọng của ngôn ngữ đang học (ko-KR hoặc en-US). Tên giữ nguyên cho code cũ. */
val LANG_KO: String get() = com.hankukko.huyfan.Lang.current.sttLocale'''),
])

# ---------- Giọng đọc ----------
S = J + 'voice/Speaker.kt'
patch(S, [
    ('''    private val ko = Locale.KOREA''', '''    /** Giọng của ngôn ngữ đang học (Hàn hoặc Anh) */
    private val ko = com.hankukko.huyfan.Lang.current.ttsLocale'''),
    ('''    private fun locale(korean: Boolean) = if (korean) "ko" else "vi"''',
     '''    private fun locale(korean: Boolean) = if (korean) ko.language else "vi"'''),
    ('''t.speak(if (korean) "안녕하세요! 만나서 반가워요." else''',
     '''t.speak(if (korean) (if (com.hankukko.huyfan.Lang.isEn) "Hello! Nice to meet you." else "안녕하세요! 만나서 반가워요.") else'''),
    ('''        /** Tách văn bản thành các đoạn (nội dung, là tiếng Hàn?). */
        fun segment(text: String): List<Pair<String, Boolean>> {''',
     '''        private val quoted = Regex("[\\"“”]([^\\"“”]{1,200})[\\"“”]")
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
            if (com.hankukko.huyfan.Lang.isEn) return segmentEnglish(text)'''),
])

# ---------- Trả lời tức thì từ sổ ----------
N = J + 'assistant/NotebookAnswer.kt'
patch(N, [
    ('''    private val viAsk = Regex(
        "^(?:(?:cho (?:tôi|tui|mình|em) hỏi|hỏi|ê|ơi|huy fan|huy phan)\\\\s+)?(.+?)\\\\s+(?:trong |bằng |sang )?tiếng hàn(?: quốc)?" +''',
     '''    private val en = com.hankukko.huyfan.Lang.isEn
    private val langWord = if (en) "tiếng anh(?: mỹ| anh)?" else "tiếng hàn(?: quốc)?"
    private val viAsk = Regex(
        "^(?:(?:cho (?:tôi|tui|mình|em) hỏi|hỏi|ê|ơi|huy fan|huy phan)\\\\s+)?(.+?)\\\\s+(?:trong |bằng |sang )?" + langWord +'''),
    ('''    private val viAskOf = Regex("^tiếng hàn (?:của )?(.+?)''',
     '''    private val viAskOf = Regex("^" + (if (en) "tiếng anh" else "tiếng hàn") + " (?:của )?(.+?)'''),
    ('''    private val koAsk = Regex(
        "^([가-힣][가-힣 ]{0,20})\\\\s*''',
     '''    private val koAsk = Regex(
        (if (en) "^([a-z][a-z' -]{0,30}?)\\\\s*" else "^([가-힣][가-힣 ]{0,20})\\\\s*") +
            "'''),
    ('''                val key = keyOf(m.groupValues[1])
                return@run vocab.filter { keyOf(it.ko) == key }''',
     '''                val key = keyOf(m.groupValues[1]).lowercase()
                return@run vocab.filter { keyOf(it.ko).lowercase() == key }'''),
    ('''append(" là ").append(v.ko).append(" nha ")''',
     '''append(" là ").append(if (en) "\\"${v.ko}\\"" else v.ko).append(" nha ")'''),
])

# ---------- Bổ sung từ mượn: chỉ cho tiếng Hàn ----------
patch(J + 'ai/LoanBackfill.kt', [
    ('''        if (prefs.getBoolean(KEY, false)) return''',
     '''        if (com.hankukko.huyfan.Lang.isEn || prefs.getBoolean(KEY, false)) return'''),
])

# ---------- Gợi ý hỏi về ảnh ----------
patch(J + 'ui/components/ChatImage.kt', [
    ('''val IMAGE_QUICK_ASKS = listOf(
    "Dịch chữ tiếng Hàn trong ảnh",
    "Cái này tiếng Hàn là gì?",
    "Giải thích ngữ pháp trong ảnh",
    "Đọc to chữ Hàn trong ảnh giúp tôi",
)''',
     '''val IMAGE_QUICK_ASKS: List<String>
    get() = com.hankukko.huyfan.Lang.current.nameVi.let { l ->
        listOf("Dịch chữ $l trong ảnh", "Cái này $l là gì?", "Giải thích ngữ pháp trong ảnh", "Đọc to chữ $l trong ảnh giúp tôi")
    }'''),
])
print('ok')
