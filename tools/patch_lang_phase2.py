import io, sys, os

root = sys.argv[1]  # .../app/src/main
J = 'java/com/hankukko/huyfan/'
UI = J + 'ui/screens/'
L = 'com.hankukko.huyfan.Lang'
EN = L + '.isEn'


def patch(rel, pairs):
    p = os.path.join(root, rel)
    s = io.open(p, encoding='utf-8').read()
    for a, b in pairs:
        if a not in s:
            raise SystemExit(rel + ' NOT FOUND: ' + a[:90])
        s = s.replace(a, b, 1)
    io.open(p, 'w', encoding='utf-8').write(s)


# ================= Bỏ ẩn các mục ở bản tiếng Anh =================
patch(J + 'ui/MainActivity.kt', [
    ('''        // Tiếng Anh: Phiên dịch & Nguyên tắc làm ở đợt sau → tạm ẩn
        Tab.entries.filter { it.inBar && !(''' + EN + ''' && (it == Tab.TRANSLATE || it == Tab.RULES)) }.forEach { t ->''',
     '''        Tab.entries.filter { it.inBar }.forEach { t ->'''),
])
patch(UI + 'ReviewScreen.kt', [
    ('''        if (!''' + EN + ''') item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PracticeTile(''', '''        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PracticeTile('''),
])
patch(J + 'ui/AssistActivity.kt', [
    ('''                if (!''' + EN + ''') {
                    com.hankukko.huyfan.ui.screens.TranslateModeButton(''',
     '''                run {
                    com.hankukko.huyfan.ui.screens.TranslateModeButton('''),
])

# ================= Cẩm nang Nguyên tắc =================
patch(J + 'data/Rules.kt', [
    ('''context.assets.open("rules_vi.json")''', '''context.assets.open(if (''' + EN + ''') "rules_en.json" else "rules_vi.json")'''),
])
RL = J + 'data/RuleLinks.kt'
p = os.path.join(root, RL)
s = io.open(p, encoding='utf-8').read()
for sig in ['    fun soundRules(text: String): List<RuleLink> {',
            '    fun forGrammar(pattern: String, form: String = ""): List<RuleLink> {']:
    if sig not in s:
        raise SystemExit('RuleLinks NOT FOUND ' + sig)
    s = s.replace(sig, sig + '\n        if (' + EN + ') return emptyList() // liên kết quy tắc chỉ có cho tiếng Hàn', 1)
s = s.replace('''    fun forVocab(ko: String): List<RuleLink> = listOfNotNull(irregular(ko)) + soundRules(ko)''',
              '''    fun forVocab(ko: String): List<RuleLink> = if (''' + EN + ''') emptyList() else listOfNotNull(irregular(ko)) + soundRules(ko)''', 1)
io.open(p, 'w', encoding='utf-8').write(s)

patch(UI + 'RulesScreen.kt', [
    ('''onAsk("Cho tôi lộ trình học ngữ pháp tiếng Hàn từ cơ bản, nên học nguyên tắc nào trước?")''',
     '''onAsk("Cho tôi lộ trình học ngữ pháp ${''' + L + '''.current.nameVi} từ cơ bản, nên học nguyên tắc nào trước?")'''),
    ('''"Giải thích kỹ hơn nguyên tắc tiếng Hàn: “''', '''"Giải thích kỹ hơn nguyên tắc ${''' + L + '''.current.nameVi}: “'''),
    ('''Text("Cẩm nang tiếng Hàn", color''', '''Text("Cẩm nang ${''' + L + '''.current.nameVi}", color'''),
    ('''"Cấu trúc, tiểu từ, bất quy tắc, biến âm – chạm ví dụ để nghe."''',
     '''if (''' + EN + ''') "Thì, mạo từ, giới từ, phát âm – chạm ví dụ để nghe." else "Cấu trúc, tiểu từ, bất quy tắc, biến âm – chạm ví dụ để nghe."'''),
])

# ================= Phiên dịch hội thoại =================
I = J + 'assistant/Interpreter.kt'
patch(I, [
    ('''        KO(LANG_KO, "한국어", "🇰🇷", "상대방");''',
     '''        KO(LANG_KO, if (''' + EN + ''') "English" else "한국어", ''' + L + '''.current.flag, if (''' + EN + ''') "Partner" else "상대방");'''),
    ('''    const val SYSTEM = """Bạn là phiên dịch viên Việt ⇄ Hàn chuyên nghiệp''',
     '''    val SYSTEM: String get() = if (''' + EN + ''') SYSTEM_EN else SYSTEM_KO

    private const val SYSTEM_EN = """Bạn là phiên dịch viên Việt ⇄ Anh chuyên nghiệp, đứng giữa 2 người đang nói chuyện trực tiếp.
Mỗi lượt bạn nhận 1 câu do máy nhận dạng giọng nói ghi lại – có thể sai chính tả, thiếu dấu tiếng Việt, nghe nhầm từ đồng âm, thiếu dấu câu.

LÀM 2 VIỆC:
1. fixed – sửa câu gốc cho đúng (GIỮ NGUYÊN ngôn ngữ gốc): thêm dấu, sửa từ nghe nhầm dựa vào ngữ cảnh, viết hoa tên riêng, thêm dấu câu. Giữ nguyên ý, không thêm thắt. Câu đã đúng thì giữ nguyên.
   Ví dụ: "huy ăn cóm vè rau" → "Huy ăn cơm và rau." ; "where is the bat room" → "Where is the bathroom?"
2. translation – dịch câu đã sửa sang ngôn ngữ còn lại, tự nhiên như người bản xứ nói, ngắn gọn, giữ đúng sắc thái.
   Sang tiếng Anh: tự nhiên, lịch sự vừa phải (dùng please / could khi nhờ vả).
   Sang tiếng Việt: tự nhiên, xưng hô hợp ngữ cảnh.

NGƯỜI NÓI NGẮN / NGẮT TỪNG TỪ: có khi chỉ 1–2 từ (yes, yeah, okay, no, sorry? / ừ, dạ, vâng, không…).
   Vẫn LUÔN dịch, không bỏ trống. Câu cụt → đoán ý hợp lý nhất theo mấy câu trước trong cuộc nói chuyện.

changed = true nếu fixed khác câu nghe được về chữ (không tính viết hoa / dấu câu).
note = ghi chú ngắn bằng tiếng Việt (≤ 12 từ) chỉ khi có từ đa nghĩa / thành ngữ cần lưu ý; thường để trống.
Chỉ trả JSON đúng định dạng:
{"fixed":"","changed":false,"translation":"","note":""}"""

    private const val SYSTEM_KO = """Bạn là phiên dịch viên Việt ⇄ Hàn chuyên nghiệp'''),
    ('''            append(if (side == Side.VI) "tiếng Việt → tiếng Hàn" else "tiếng Hàn → tiếng Việt")''',
     '''            val tg = ''' + L + '''.current.nameVi
            append(if (side == Side.VI) "tiếng Việt → $tg" else "$tg → tiếng Việt")'''),
])

IS = UI + 'InterpreterScreen.kt'
patch(IS, [
    ('''Text("🇻🇳 ⇄ 🇰🇷 · tự sửa câu nghe nhầm"''', '''Text("🇻🇳 ⇄ ${''' + L + '''.current.flag} · tự sửa câu nghe nhầm"'''),
    ('''Text("Nói chuyện với người Hàn", color''', '''Text(if (''' + EN + ''') "Nói chuyện với người nước ngoài" else "Nói chuyện với người Hàn", color'''),
    ('''"Bấm 🇻🇳 rồi nói · ⛶ để phóng to cho người Hàn đọc."''',
     '''if (''' + EN + ''') "Bấm 🇻🇳 rồi nói · ⛶ để phóng to cho người đối diện đọc." else "Bấm 🇻🇳 rồi nói · ⛶ để phóng to cho người Hàn đọc."'''),
    ('''            "한국어로 말씀하시려면 🇰🇷 버튼을 눌러 주세요.",''',
     '''            if (''' + EN + ''') "To speak English, tap the ${''' + L + '''.current.flag} button." else "한국어로 말씀하시려면 🇰🇷 버튼을 눌러 주세요.",'''),
    ('''if (mine) "Cho người Hàn xem" else "Phóng to"''', '''if (mine) (if (''' + EN + ''') "Cho người kia xem" else "Cho người Hàn xem") else "Phóng to"'''),
    ('''if (mine) "Đang nghe tiếng Việt…" else "한국어를 듣고 있어요…"''', '''if (mine) "Đang nghe tiếng Việt…" else (if (''' + EN + ''') "Listening to English…" else "한국어를 듣고 있어요…")'''),
    ('''partial.ifBlank { if (mine) "Nói đi, tui đang nghe…" else "말씀하세요…" }''',
     '''partial.ifBlank { if (mine) "Nói đi, tui đang nghe…" else (if (''' + EN + ''') "Please speak…" else "말씀하세요…") }'''),
    ('''if (active) (if (side == Side.VI) "Đang nghe…" else "듣는 중…") else (if (side == Side.VI) "Bạn nói" else "상대방 말하기")''',
     '''if (active) (if (side == Side.VI) "Đang nghe…" else (if (''' + EN + ''') "Listening…" else "듣는 중…")) else (if (side == Side.VI) "Bạn nói" else (if (''' + EN + ''') "Partner speaks" else "상대방 말하기"))'''),
    ('''if (toKorean) "다시 듣기" else "Nghe lại"''', '''if (toKorean) (if (''' + EN + ''') "Play again" else "다시 듣기") else "Nghe lại"'''),
])

# ================= Dịch tự động =================
AS = UI + 'AutoInterpreterScreen.kt'
patch(AS, [
    ('''Text("🇻🇳 ⇄ 🇰🇷 · tự nhận ngôn ngữ"''', '''Text("🇻🇳 ⇄ ${''' + L + '''.current.flag} · tự nhận ngôn ngữ"'''),
    ('''Text("편하게 말씀하세요. 자동으로 번역해 드려요.", color''',
     '''Text(if (''' + EN + ''') "Just speak naturally – I’ll translate for you." else "편하게 말씀하세요. 자동으로 번역해 드려요.", color'''),
    ('''when (b.side) { Side.VI -> "Việt"; Side.KO -> "Hàn"; null -> "…" },''',
     '''when (b.side) { Side.VI -> "Việt"; Side.KO -> if (''' + EN + ''') "Anh" else "Hàn"; null -> "…" },'''),
    ('''when (b.side) { Side.VI -> "Hàn"; Side.KO -> "Việt"; null -> "…" },''',
     '''when (b.side) { Side.VI -> if (''' + EN + ''') "Anh" else "Hàn"; Side.KO -> "Việt"; null -> "…" },'''),
])
patch(UI + 'TranslateScreen.kt', [
    ('''Text("🇻🇳 Việt ⇄ Hàn 🇰🇷", style''', '''Text(if (''' + EN + ''') "🇻🇳 Việt ⇄ Anh 🇬🇧" else "🇻🇳 Việt ⇄ Hàn 🇰🇷", style'''),
])

# Nhận dạng tự động: "ko" trong app = ngôn ngữ đang học (Hàn hoặc Anh)
A = J + 'ai/AiService.kt'
patch(A, [
    ('''    private fun whisperLang(r: OpenAiCompatClient.Transcript): String? {
        val l = r.language.lowercase()
        val h = hangulRatio(r.text)
        return when {
            (l.contains("korean") || l == "ko") && h >= 0.5 -> "ko"''',
     '''    private val vietMark = Regex("[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]", RegexOption.IGNORE_CASE)

    /** Mã Whisper của ngôn ngữ đang học. Kết quả trả về app vẫn ghi "ko" = ngôn ngữ đang học. */
    private val target: String get() = if (''' + EN + ''') "en" else "ko"

    private fun whisperLang(r: OpenAiCompatClient.Transcript): String? {
        val l = r.language.lowercase()
        val h = hangulRatio(r.text)
        return when {
            ''' + EN + ''' && (l.contains("english") || l == "en") && h == 0.0 && !vietMark.containsMatchIn(r.text) -> "ko"
            !''' + EN + ''' && (l.contains("korean") || l == "ko") && h >= 0.5 -> "ko"'''),
    ('''                val lang = whisperLang(r)
                if (lang != null) {''',
     '''                // Tiếng Anh: "Thank you." trên nền ồn mà Whisper không chắc = câu bịa
                if (''' + EN + ''' && isNoiseThanks(r.text) && r.logprob < -0.6) {
                    Log.i("HuyFanAI", "bỏ ($tag): '${r.text.take(40)}' – câu cảm ơn nghi bịa")
                    return Heard("", "", "whisper")
                }
                val lang = whisperLang(r)
                if (lang != null) {'''),
    ('''                if (isNoiseThanks(r.text)) return Heard("", "", "whisper")''',
     '''                if (!''' + EN + ''' && isNoiseThanks(r.text)) return Heard("", "", "whisper")'''),
    ('''                        val b = async { whisper(s, wav, "ko") }''', '''                        val b = async { whisper(s, wav, target) }'''),
    ('''                        val penalty = if (korean) (if (h < 0.5) 2.0 else 0.0) else (if (h > 0.2 || !looksVietnamese(t.text)) 2.0 else 0.0)''',
     '''                        val words = t.text.split(Regex("\\\\s+")).count { w -> w.any { it.isLetter() } }
                        val penalty = when {
                            ''' + EN + ''' && korean -> if (h > 0.0 || vietMark.containsMatchIn(t.text)) 2.0 else 0.0
                            ''' + EN + ''' -> if (h > 0.2 || !looksVietnamese(t.text) || (words >= 2 && !vietMark.containsMatchIn(t.text))) 2.0 else 0.0
                            korean -> if (h < 0.5) 2.0 else 0.0
                            else -> if (h > 0.2 || !looksVietnamese(t.text)) 2.0 else 0.0
                        }'''),
    ('''        val system = """Bạn nhận 1 đoạn ghi âm ngắn trong cuộc nói chuyện giữa người Việt và người Hàn.''',
     '''        val system = if (''' + EN + ''') """Bạn nhận 1 đoạn ghi âm ngắn trong cuộc nói chuyện giữa người Việt và người nói tiếng Anh.
Chép lại CHÍNH XÁC lời nói (đúng ngôn ngữ gốc, có dấu, có dấu câu) và cho biết ngôn ngữ: "vi" (tiếng Việt) hoặc "en" (tiếng Anh).
Người nói CHỈ dùng tiếng Việt hoặc tiếng Anh: nghe giống ngôn ngữ khác thì chọn ngôn ngữ gần nhất trong 2 ngôn ngữ này và chép bằng ngôn ngữ đó, KHÔNG dịch.
Không có lời nói rõ ràng (chỉ tiếng ồn, nhạc, im lặng) → lang = "none", text = "". Tuyệt đối không bịa.
Chỉ trả JSON: {"lang":"vi","text":""}""" else """Bạn nhận 1 đoạn ghi âm ngắn trong cuộc nói chuyện giữa người Việt và người Hàn.'''),
    ('''        return Heard(if (lang.startsWith("ko") || hangul * 2 >= text.count { it.isLetter() }) "ko" else "vi", text, "gemini")''',
     '''        if (''' + EN + ''') {
            val en = lang.startsWith("en") || (!lang.startsWith("vi") && !vietMark.containsMatchIn(text))
            return Heard(if (en) "ko" else "vi", text, "gemini")
        }
        return Heard(if (lang.startsWith("ko") || hangul * 2 >= text.count { it.isLetter() }) "ko" else "vi", text, "gemini")'''),
])

# ================= Hội thoại tình huống =================
RP = J + 'assistant/RolePlay.kt'
patch(RP, [
    ('''    val SCENARIOS = listOf(''', '''    val SCENARIOS: List<Scenario> get() = if (''' + EN + ''') SCENARIOS_EN else SCENARIOS_KO

    private val SCENARIOS_EN = listOf(
        Scenario("restaurant", "🍔", "Gọi món ở nhà hàng", "nhân viên phục vụ nhà hàng ở Mỹ", "Khách vào nhà hàng, hỏi món, gọi món, gọi thêm, xin hoá đơn, tip."),
        Scenario("cafe", "☕", "Quán cà phê", "barista quán cà phê", "Khách gọi đồ uống, chọn size, nóng/đá, uống tại chỗ hay mang đi, thanh toán."),
        Scenario("shopping", "🛍️", "Mua sắm quần áo", "nhân viên cửa hàng quần áo", "Khách hỏi giá, size/màu, thử đồ, hỏi giảm giá, thanh toán."),
        Scenario("airport", "✈️", "Làm thủ tục sân bay", "nhân viên quầy check-in", "Hành khách check-in, gửi hành lý, chọn chỗ ngồi, hỏi cổng ra máy bay."),
        Scenario("direction", "🧭", "Hỏi đường", "người đi đường ở London", "Khách du lịch hỏi đường tới ga tàu điện ngầm và địa điểm gần đó."),
        Scenario("friend", "🙋", "Làm quen bạn mới", "một người bạn nước ngoài mới quen, cùng tuổi, thân thiện", "Hai người làm quen: tên, quê, công việc/học, sở thích, hẹn đi chơi."),
        Scenario("hospital", "🏥", "Đi khám bệnh", "bác sĩ phòng khám", "Bệnh nhân mô tả triệu chứng, bác sĩ hỏi thêm, dặn dò, kê thuốc."),
        Scenario("interview", "💼", "Phỏng vấn xin việc", "người phỏng vấn của công ty", "Phỏng vấn: giới thiệu bản thân, kinh nghiệm, điểm mạnh, câu hỏi cho công ty."),
        Scenario("hotel", "🏨", "Nhận phòng khách sạn", "lễ tân khách sạn", "Nhận phòng, hỏi giờ ăn sáng, wifi, gửi hành lý, trả phòng."),
    )

    private val SCENARIOS_KO = listOf('''),
    ('''    enum class Level(val label: String, val desc: String) {
        BEGINNER("Sơ cấp", "sơ cấp (TOPIK 1–2): câu rất ngắn, từ thông dụng, ngữ pháp cơ bản"),
        INTERMEDIATE("Trung cấp", "trung cấp (TOPIK 3–4): câu tự nhiên như người bản xứ, ngữ pháp trung cấp"),
    }''',
     '''    enum class Level(val label: String, private val descKo: String, private val descEn: String) {
        BEGINNER("Sơ cấp", "sơ cấp (TOPIK 1–2): câu rất ngắn, từ thông dụng, ngữ pháp cơ bản", "sơ cấp (A1–A2): câu rất ngắn, từ thông dụng, thì đơn giản"),
        INTERMEDIATE("Trung cấp", "trung cấp (TOPIK 3–4): câu tự nhiên như người bản xứ, ngữ pháp trung cấp", "trung cấp (B1–B2): câu tự nhiên như người bản xứ, có thành ngữ thông dụng");

        val desc: String get() = if (''' + EN + ''') descEn else descKo
    }'''),
    ('''    fun system(sc: Scenario, level: Level, userName: String): String = """''',
     '''    fun system(sc: Scenario, level: Level, userName: String): String = if (''' + EN + ''') systemEn(sc, level, userName) else systemKo(sc, level, userName)

    /** Tiếng Anh: giữ nguyên khoá JSON (reply_ko = câu tiếng Anh) để dùng chung màn hình. */
    private fun systemEn(sc: Scenario, level: Level, userName: String): String = """
Bạn là bạn diễn giúp ${userName.ifBlank { "người học" }} (người Việt) luyện NÓI tiếng Anh qua đóng vai.
Tình huống: ${sc.title}. ${sc.setting}
Vai của bạn: ${sc.aiRole}. Người học đóng vai còn lại (khách / người đối diện).
Trình độ: ${level.desc}.

QUY TẮC TRẢ LỜI (chỉ trả JSON):
- reply_ko: lời thoại tiếp theo của bạn bằng TIẾNG ANH (khoá tên "ko" nhưng ghi tiếng Anh), 1–2 câu ngắn, tự nhiên, đúng vai. Thường kết thúc bằng 1 câu hỏi để người học nói tiếp.
- reply_vi: dịch reply_ko sang tiếng Việt tự nhiên.
- Câu của người học có thể do máy nghe giọng nói (có thể sai chính tả nhẹ, thiếu dấu câu) – chỉ sửa lỗi ngữ pháp/từ vựng/cách nói chưa tự nhiên thật sự.
- Nếu người học nói/viết bằng tiếng Việt: hiểu ý, vẫn đáp lại theo vai, và đưa câu tiếng Anh tương ứng vào fixed_ko (has_error = true, explain_vi: "Câu này nói bằng tiếng Anh là…").
- has_error: true nếu câu tiếng Anh vừa rồi của người học sai hoặc không tự nhiên. fixed_ko: cách nói đúng và tự nhiên (tiếng Anh). explain_vi: giải thích 1 câu ngắn bằng tiếng Việt. Lượt mở đầu thì has_error = false.
- suggestions: đúng 2 câu mẫu ngắn người học có thể nói tiếp (ko = tiếng Anh, vi = nghĩa), đúng trình độ.
- vocab: 0–2 từ/cụm từ đáng học trong lượt này (ko = tiếng Anh, vi = nghĩa).
- done: true khi cuộc hội thoại đã xong một cách tự nhiên (thường sau 8–12 lượt) – khi đó reply_ko là lời chào kết thúc, suggestions để trống.
- Không viết IPA, không giải thích dài.

ĐỊNH DẠNG JSON (đúng các khoá sau):
{"has_error":false,"fixed_ko":"","explain_vi":"","reply_ko":"","reply_vi":"","suggestions":[{"ko":"","vi":""},{"ko":"","vi":""}],"vocab":[{"ko":"","vi":""}],"done":false}
""".trim()

    private fun systemKo(sc: Scenario, level: Level, userName: String): String = """'''),
])
RS = UI + 'RolePlayScreen.kt'
patch(RS, [
    ('''"Chọn tình huống để luyện nói tiếng Hàn"''', '''"Chọn tình huống để luyện nói ${''' + L + '''.current.nameVi}"'''),
    ('''Text(if (listening) "Đang nghe tiếng Hàn…" else "Nói hoặc gõ câu tiếng Hàn…")''',
     '''Text(if (listening) "Đang nghe ${''' + L + '''.current.nameVi}…" else "Nói hoặc gõ câu ${''' + L + '''.current.nameVi}…")'''),
    ('''"HUY FAN đóng vai nói tiếng Hàn – nói sai được sửa ngay."''', '''"HUY FAN đóng vai nói ${''' + L + '''.current.nameVi} – nói sai được sửa ngay."'''),
])

# ================= Chấm phát âm tiếng Anh =================
PR = J + 'util/Pronounce.kt'
patch(PR, [
    ('''    fun score(target: String, heard: String): Result {
        val tSyl = target.filter(::isSyl)''',
     '''    /** Tiếng Anh (câu mẫu không có Hangul): chấm theo từng từ, tô màu cả từ. */
    private fun scoreWords(target: String, heard: String): Result {
        fun norm(s: String) = s.lowercase().replace('’', '\\'').filter { it.isLetterOrDigit() || it == '\\'' }
        val tWords = Regex("[A-Za-z0-9’']+").findAll(target).toList()
        val hWords = heard.split(Regex("[^A-Za-z0-9’']+")).map(::norm).filter { it.isNotBlank() }
        if (tWords.isEmpty() || hWords.isEmpty()) return Result(0, target.map { it to Mark.NEUTRAL }, heard)
        val marks = Array(target.length) { Mark.NEUTRAL }
        var total = 0
        var used = 0
        tWords.forEach { m ->
            val w = norm(m.value)
            // Tìm từ khớp nhất trong câu nghe được (ưu tiên theo thứ tự)
            var best = 0
            var bestAt = -1
            for (k in used until hWords.size) {
                val r = ratio(w, hWords[k])
                if (r > best) { best = r; bestAt = k }
                if (r == 100) break
            }
            if (bestAt >= 0 && best >= 60) used = bestAt + 1
            total += best
            val mark = when {
                best == 100 -> Mark.OK
                best >= 60 -> Mark.CLOSE
                else -> Mark.MISS
            }
            for (i in m.range) marks[i] = mark
        }
        val score = (total / tWords.size).coerceIn(0, 100)
        return Result(score, target.mapIndexed { i, c -> c to marks[i] }, heard)
    }

    fun score(target: String, heard: String): Result {
        if (target.none(::isSyl)) return scoreWords(target, heard)
        val tSyl = target.filter(::isSyl)'''),
])
print('ok')
