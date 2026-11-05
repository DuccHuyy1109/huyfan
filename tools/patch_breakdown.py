import sys, os

root = sys.argv[1]


def patch(rel, pairs):
    p = os.path.join(root, rel)
    s = open(p, encoding='utf-8').read()
    for old, new in pairs:
        if old not in s:
            raise SystemExit(rel + ' NOT FOUND: ' + old[:100])
        s = s.replace(old, new, 1)
    open(p, 'w', encoding='utf-8').write(s)


# ---------------- AI models ----------------
patch('ai/AiModels.kt', [
    ('''    val exampleVi: String = "",
    val note: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("vi", vi).put("ko", ko).put("romaja", romaja)
        .put("hanja", hanja).put("han_viet", hanViet).put("pos", pos)
        .put("example_ko", exampleKo).put("example_vi", exampleVi).put("note", note)''',
     '''    val exampleVi: String = "",
    val note: String = "",
    /** Cấu tạo từ ghép, vd "여자 (phụ nữ) + 친구 (bạn) → bạn gái" */
    val breakdown: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("vi", vi).put("ko", ko).put("romaja", romaja)
        .put("hanja", hanja).put("han_viet", hanViet).put("pos", pos)
        .put("example_ko", exampleKo).put("example_vi", exampleVi).put("note", note)
        .put("breakdown", breakdown)'''),
    ('''            note = o.str("note", "notes", "tip"),
        )

        fun listFromJson(s: String): List<VocabItem>''',
     '''            note = o.str("note", "notes", "tip"),
            breakdown = o.str("breakdown", "composition", "cau_tao"),
        )

        fun listFromJson(s: String): List<VocabItem>'''),
    ('''    val exampleVi: String = "",
    val note: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("pattern", pattern).put("meaning", meaning).put("form", form).put("combine", combine)
        .put("example_ko", exampleKo).put("example_vi", exampleVi).put("note", note)''',
     '''    val exampleVi: String = "",
    val note: String = "",
    /** Cấu tạo, vd "-고 (nối) + 싶다 (muốn)" */
    val breakdown: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("pattern", pattern).put("meaning", meaning).put("form", form).put("combine", combine)
        .put("example_ko", exampleKo).put("example_vi", exampleVi).put("note", note)
        .put("breakdown", breakdown)'''),
    ('''            note = o.str("note", "tip"),
        )''', '''            note = o.str("note", "tip"),
            breakdown = o.str("breakdown", "composition", "cau_tao"),
        )'''),
])

# ---------------- Prompt + schema ----------------
patch('ai/PromptBuilder.kt', [
    ('''3. Câu mẫu:''', '''2b. Cấu tạo (breakdown): nếu từ là từ ghép/phái sinh từ 2+ thành phần có nghĩa riêng thì phân tích ngắn gọn, vd 여자친구 → "여자 (phụ nữ) + 친구 (bạn) → bạn gái", 설거지하다 → "설거지 (việc rửa bát) + 하다 (làm)". Từ đơn thì để trống.
3. Câu mẫu:'''),
    ('''   example_ko + example_vi = 1 câu mẫu đời thường. save = true, intent = grammar.''',
     '''   example_ko + example_vi = 1 câu mẫu đời thường. save = true, intent = grammar.
   breakdown = cấu tạo nếu ghép từ nhiều thành phần, vd -고 싶다 → "-고 (nối) + 싶다 (muốn)", -아/어야 하다 → "-아/어야 (phải) + 하다 (làm)"; đơn lẻ thì để trống.'''),
    ('''"example_ko":"","example_vi":"","note":""}],"grammar":[{"pattern":"","meaning":"","form":"","combine":"","example_ko":"","example_vi":"","note":""}]''',
     '''"example_ko":"","example_vi":"","note":"","breakdown":""}],"grammar":[{"pattern":"","meaning":"","form":"","combine":"","example_ko":"","example_vi":"","note":"","breakdown":""}]'''),
    ('''                    .put("example_ko", str()).put("example_vi", str()).put("note", str())
            )
            .put("required", JSONArray(listOf("vi", "ko", "romaja", "example_ko", "example_vi")))''',
     '''                    .put("example_ko", str()).put("example_vi", str()).put("note", str())
                    .put("breakdown", str())
            )
            .put("required", JSONArray(listOf("vi", "ko", "romaja", "example_ko", "example_vi")))'''),
    ('''JSONArray(listOf("vi", "ko", "romaja", "hanja", "han_viet", "pos", "example_ko", "example_vi", "note"))''',
     '''JSONArray(listOf("vi", "ko", "romaja", "hanja", "han_viet", "pos", "breakdown", "example_ko", "example_vi", "note"))'''),
    ('''                    .put("example_ko", str()).put("example_vi", str()).put("note", str())
            )
            .put("required", JSONArray(listOf("pattern", "meaning", "form", "combine", "example_ko", "example_vi")))
            .put("propertyOrdering", JSONArray(listOf("pattern", "meaning", "form", "combine", "example_ko", "example_vi", "note")))''',
     '''                    .put("example_ko", str()).put("example_vi", str()).put("note", str())
                    .put("breakdown", str())
            )
            .put("required", JSONArray(listOf("pattern", "meaning", "form", "combine", "example_ko", "example_vi")))
            .put("propertyOrdering", JSONArray(listOf("pattern", "meaning", "form", "combine", "breakdown", "example_ko", "example_vi", "note")))'''),
])

# ---------------- Entities + DB v3 ----------------
patch('data/Entities.kt', [
    ('''    val lastReviewedAt: Long = 0,
    val synced: Boolean = false,
) {
    fun toItem() = VocabItem(vi, ko, romaja, hanja, hanViet, pos, exampleKo, exampleVi, note)''',
     '''    val lastReviewedAt: Long = 0,
    val synced: Boolean = false,
    @ColumnInfo(defaultValue = "") val breakdown: String = "",
) {
    fun toItem() = VocabItem(vi, ko, romaja, hanja, hanViet, pos, exampleKo, exampleVi, note, breakdown)'''),
    ('''        .put("note", note)
}

@Entity(tableName = "sessions")''', '''        .put("note", note)
        .put("breakdown", breakdown)
}

@Entity(tableName = "sessions")'''),
    ('''    val lastReviewedAt: Long = 0,
    val synced: Boolean = false,
) {
    fun toItem() = GrammarItem(pattern, meaning, form, combine, exampleKo, exampleVi, note)''',
     '''    val lastReviewedAt: Long = 0,
    val synced: Boolean = false,
    @ColumnInfo(defaultValue = "") val breakdown: String = "",
) {
    fun toItem() = GrammarItem(pattern, meaning, form, combine, exampleKo, exampleVi, note, breakdown)'''),
    ('''        .put("note", note)
}

/** Bài ôn do người dùng tự tạo. */''', '''        .put("note", note)
        .put("breakdown", breakdown)
}

/** Bài ôn do người dùng tự tạo. */'''),
])

patch('data/Daos.kt', [
    ('''    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],''',
     '''    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],'''),
])

# ---------------- Repositories: lưu breakdown ----------------
patch('data/Repositories.kt', [
    ('''                    note = existing.note.ifBlank { it.note },
                    lookupCount = existing.lookupCount + 1,
                    synced = false,
                )
                dao.update(merged)
                saved += merged
            } else {
                val e = VocabEntity(''', '''                    note = existing.note.ifBlank { it.note },
                    breakdown = existing.breakdown.ifBlank { it.breakdown },
                    lookupCount = existing.lookupCount + 1,
                    synced = false,
                )
                dao.update(merged)
                saved += merged
            } else {
                val e = VocabEntity('''),
    ('''                    exampleKo = it.exampleKo.trim(), exampleVi = it.exampleVi.trim(), note = it.note.trim(),
                    day = day, createdAt = now,
                )''', '''                    exampleKo = it.exampleKo.trim(), exampleVi = it.exampleVi.trim(), note = it.note.trim(),
                    breakdown = it.breakdown.trim(), day = day, createdAt = now,
                )'''),
    ('''                    note = existing.note.ifBlank { it.note },
                    lookupCount = existing.lookupCount + 1,
                    synced = false,
                )
                dao.update(merged)
                saved += merged
            } else {
                val e = GrammarEntity(''', '''                    note = existing.note.ifBlank { it.note },
                    breakdown = existing.breakdown.ifBlank { it.breakdown },
                    lookupCount = existing.lookupCount + 1,
                    synced = false,
                )
                dao.update(merged)
                saved += merged
            } else {
                val e = GrammarEntity('''),
    ('''                    note = it.note.trim(), day = day, createdAt = now,''',
     '''                    note = it.note.trim(), breakdown = it.breakdown.trim(), day = day, createdAt = now,'''),
])

# ---------------- UI: bảng trong chat ----------------
patch('ui/components/Components.kt', [
    ('''                if (it.note.isNotBlank()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 10.dp, bottom = 9.dp),''',
     '''                if (it.breakdown.isNotBlank()) BreakdownRow(it.breakdown)
                if (it.note.isNotBlank()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 10.dp, bottom = 9.dp),'''),
    ('''            GrammarRow("Câu mẫu", striped = false) {''', '''            if (g.breakdown.isNotBlank()) {
                GrammarRow("Cấu tạo", striped = false) {
                    Text(g.breakdown, style = MaterialTheme.typography.bodySmall, color = Brand.Teal, fontWeight = FontWeight.SemiBold)
                }
            }
            GrammarRow("Câu mẫu", striped = g.breakdown.isNotBlank()) {'''),
    ('''/** Nút thùng rác nhỏ: xoá khỏi sổ ngay, không cần hỏi lại. */''', '''/** Hàng "Cấu tạo" dưới mỗi từ: từ ghép = thành phần + thành phần. */
@Composable
fun BreakdownRow(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, bottom = 9.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brand.Teal.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("CẤU TẠO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Brand.Teal, letterSpacing = 0.5.sp)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

/** Nút thùng rác nhỏ: xoá khỏi sổ ngay, không cần hỏi lại. */'''),
    ('''        if (v.romaja.isNotBlank()) append(" (").append(v.romaja).append(")")''',
     '''        if (v.romaja.isNotBlank()) append(" (").append(v.romaja).append(")")
        if (v.breakdown.isNotBlank()) append(" [").append(v.breakdown).append("]")'''),
])

# ---------------- UI: sổ học ----------------
patch('ui/screens/VocabScreen.kt', [
    ('''            if (w.lookupCount > 1) Pill("tra ${w.lookupCount} lần", color = Brand.Red)
            MasteryDots(w.mastery)
        }''', '''            if (w.lookupCount > 1) Pill("tra ${w.lookupCount} lần", color = Brand.Red)
            MasteryDots(w.mastery)
        }
        if (w.breakdown.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("🧩 ${w.breakdown}", style = MaterialTheme.typography.bodySmall, color = Brand.Teal, fontWeight = FontWeight.SemiBold)
        }'''),
    ('''        if (g.combine.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("Kết hợp: ${g.combine}", style = MaterialTheme.typography.bodySmall)
        }''', '''        if (g.combine.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("Kết hợp: ${g.combine}", style = MaterialTheme.typography.bodySmall)
        }
        if (g.breakdown.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("🧩 ${g.breakdown}", style = MaterialTheme.typography.bodySmall, color = Brand.Teal, fontWeight = FontWeight.SemiBold)
        }'''),
])

# ---------------- UI: thẻ ôn ----------------
patch('ui/screens/ReviewScreen.kt', [
    ('''                    if (v.hanja.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("${v.hanja}  ·  ${v.hanViet}", color = Brand.Amber, fontWeight = FontWeight.SemiBold)
                    }''', '''                    if (v.hanja.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("${v.hanja}  ·  ${v.hanViet}", color = Brand.Amber, fontWeight = FontWeight.SemiBold)
                    }
                    if (v.breakdown.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("🧩 ${v.breakdown}", color = Brand.Teal, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    }'''),
    ('''                    if (g.combine.isNotBlank()) Text("Kết hợp: ${g.combine}", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)''',
     '''                    if (g.combine.isNotBlank()) Text("Kết hợp: ${g.combine}", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    if (g.breakdown.isNotBlank()) Text("🧩 ${g.breakdown}", color = Brand.Teal, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)'''),
])

# ---------------- CSV ----------------
patch('ui/MainViewModel.kt', [
    ('''                append("Ngày,Đuôi câu,Nghĩa,Dạng,Từ kết hợp,Câu mẫu,Nghĩa câu mẫu,Ghi chú\\n")''',
     '''                append("Ngày,Đuôi câu,Nghĩa,Dạng,Từ kết hợp,Cấu tạo,Câu mẫu,Nghĩa câu mẫu,Ghi chú\\n")'''),
    ('''                    append(listOf(it.day, it.pattern, it.meaning, it.form, it.combine, it.exampleKo, it.exampleVi, it.note)''',
     '''                    append(listOf(it.day, it.pattern, it.meaning, it.form, it.combine, it.breakdown, it.exampleKo, it.exampleVi, it.note)'''),
    ('''                append("Ngày,Tiếng Việt,Tiếng Hàn,Phiên âm,Hán Hàn,Hán Việt,Từ loại,Câu mẫu,Nghĩa câu mẫu,Ghi chú\\n")''',
     '''                append("Ngày,Tiếng Việt,Tiếng Hàn,Phiên âm,Hán Hàn,Hán Việt,Từ loại,Cấu tạo,Câu mẫu,Nghĩa câu mẫu,Ghi chú\\n")'''),
    ('''                    append(listOf(it.day, it.vi, it.ko, it.romaja, it.hanja, it.hanViet, it.pos, it.exampleKo, it.exampleVi, it.note)''',
     '''                    append(listOf(it.day, it.vi, it.ko, it.romaja, it.hanja, it.hanViet, it.pos, it.breakdown, it.exampleKo, it.exampleVi, it.note)'''),
])
print('ok')
