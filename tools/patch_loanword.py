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


# ---------- 1. Phiên dịch: bỏ mẹo bóng đèn ----------
patch(J + 'ui/screens/TranslateScreen.kt', [
    ('''        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(x.card)
                    .border(1.dp, Brush.horizontalGradient(listOf(Brand.Amber.copy(alpha = 0.5f), x.cardBorder)), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Rounded.Lightbulb, null, tint = Brand.Amber, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Đặt máy giữa hai người, nói rõ từng câu. Nơi ồn nên dùng chế độ 01.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
''', ''),
])

# ---------- 2. Chat: bỏ nút phiên dịch trong ô nhập (đã có tab riêng) ----------
patch(J + 'ui/screens/ChatScreen.kt', [
    ('''                // Chuyển sang phiên dịch hội thoại song ngữ
                Box(
                    Modifier
                        .padding(start = 4.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Brand.Teal.copy(alpha = 0.16f))
                        .clickable(onClick = onOpenInterpreter),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Translate, "Phiên dịch hội thoại", tint = Brand.Teal, modifier = Modifier.size(22.dp))
                }
''', ''),
])

# ---------- 3. Từ mượn tiếng Anh (loan) ----------
patch(J + 'ai/AiModels.kt', [
    ('''    val breakdown: String = "",
) {
    fun toJson(): JSONObject = JSONObject()''',
     '''    val breakdown: String = "",
    /** Từ mượn nước ngoài: từ gốc, vd 컴퓨터 → "computer" */
    val loan: String = "",
) {
    fun toJson(): JSONObject = JSONObject()'''),
    ('''        .put("breakdown", breakdown)

    companion object {''',
     '''        .put("breakdown", breakdown).put("loan", loan)

    companion object {'''),
    ('''            breakdown = o.str("breakdown", "composition", "cau_tao"),
        )''',
     '''            breakdown = o.str("breakdown", "composition", "cau_tao"),
            loan = o.str("loan", "loanword", "english", "origin"),
        )'''),
])

patch(J + 'data/Entities.kt', [
    ('''    @ColumnInfo(defaultValue = "0") val lapses: Int = 0,
) {
    fun toItem() = VocabItem(vi, ko, romaja, hanja, hanViet, pos, exampleKo, exampleVi, note, breakdown)''',
     '''    @ColumnInfo(defaultValue = "0") val lapses: Int = 0,
    /** Từ mượn nước ngoài: từ gốc (vd "computer") */
    @ColumnInfo(defaultValue = "") val loan: String = "",
) {
    fun toItem() = VocabItem(vi, ko, romaja, hanja, hanViet, pos, exampleKo, exampleVi, note, breakdown, loan)'''),
])

patch(J + 'data/Daos.kt', [
    ('''    version = 6,''', '''    version = 7,'''),
    ('''AutoMigration(from = 5, to = 6),''', '''AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7),'''),
])

patch(J + 'data/Repositories.kt', [
    ('''                    breakdown = existing.breakdown.ifBlank { it.breakdown },
                    lookupCount''',
     '''                    breakdown = existing.breakdown.ifBlank { it.breakdown },
                    loan = existing.loan.ifBlank { it.loan },
                    lookupCount'''),
    ('''                    breakdown = it.breakdown.trim(), day = day, createdAt = now,''',
     '''                    breakdown = it.breakdown.trim(), loan = it.loan.trim(), day = day, createdAt = now,'''),
])

P = J + 'ai/PromptBuilder.kt'
patch(P, [
    ('''Không chắc chắn thì để trống – tuyệt đối không bịa.
2b.''',
     '''Không chắc chắn thì để trống – tuyệt đối không bịa.
2a. Từ mượn (ngoại lai): nếu từ mượn từ tiếng nước ngoài thì ghi từ gốc vào loan (vd 컴퓨터 → computer, 아이스크림 → ice cream, 아르바이트 → Arbeit (tiếng Đức)); tiếng Anh chỉ ghi từ, tiếng khác ghi kèm tên tiếng trong ngoặc. Từ mượn thì hanja để trống. Từ thuần Hàn / Hán Hàn thì loan để trống.
2b.'''),
    ('''"hanja":"","han_viet":"",''', '''"hanja":"","han_viet":"","loan":"",'''),
    ('''                    .put("breakdown", str())
            )
            .put("required", JSONArray(listOf("vi", "ko", "romaja", "example_ko", "example_vi")))''',
     '''                    .put("breakdown", str()).put("loan", str())
            )
            .put("required", JSONArray(listOf("vi", "ko", "romaja", "example_ko", "example_vi")))'''),
    ('''JSONArray(listOf("vi", "ko", "romaja", "hanja", "han_viet", "pos", "breakdown", "example_ko", "example_vi", "note"))''',
     '''JSONArray(listOf("vi", "ko", "romaja", "hanja", "han_viet", "loan", "pos", "breakdown", "example_ko", "example_vi", "note"))'''),
])

C = J + 'ui/components/Components.kt'
patch(C, [
    ('''// Bảng từ vựng: Tiếng Việt | Tiếng Hàn | Hán Hàn | Câu mẫu''',
     '''// Bảng từ vựng: Tiếng Việt | Tiếng Hàn | Gốc từ (Hán Hàn / từ mượn) | Câu mẫu'''),
    ('''            HeaderCell("Hán Hàn", W_HAN)''', '''            HeaderCell("Gốc từ", W_HAN)'''),
    ('''                            if (it.hanViet.isNotBlank()) {
                                Text(it.hanViet, style = MaterialTheme.typography.labelSmall, color = Brand.Amber, fontStyle = FontStyle.Italic)
                            }
                        } else {''',
     '''                            if (it.hanViet.isNotBlank()) {
                                Text(it.hanViet, style = MaterialTheme.typography.labelSmall, color = Brand.Amber, fontStyle = FontStyle.Italic)
                            }
                        } else if (it.loan.isNotBlank()) {
                            // Từ mượn: từ gốc + nhãn nhỏ
                            Text(it.loan, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("từ mượn", style = MaterialTheme.typography.labelSmall, color = Brand.Cyan, fontStyle = FontStyle.Italic)
                        } else {'''),
    ('''        if (v.hanja.isNotBlank()) append(" · ").append(v.hanja).append(" ").append(v.hanViet)''',
     '''        if (v.hanja.isNotBlank()) append(" · ").append(v.hanja).append(" ").append(v.hanViet)
        else if (v.loan.isNotBlank()) append(" · ").append(v.loan)'''),
])

patch(J + 'ui/screens/VocabScreen.kt', [
    ('''            if (w.pos.isNotBlank()) Pill(w.pos, color = Brand.Violet)
            if (w.lookupCount > 1)''',
     '''            if (w.hanja.isBlank() && w.loan.isNotBlank()) Pill("từ mượn · ${w.loan}", color = Brand.Cyan)
            if (w.pos.isNotBlank()) Pill(w.pos, color = Brand.Violet)
            if (w.lookupCount > 1)'''),
])
print('ok')
