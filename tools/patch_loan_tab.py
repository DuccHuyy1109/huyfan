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


# ================= Trường mới: loanRead (cách đọc từ gốc kiểu Việt) =================
patch(J + 'ai/AiModels.kt', [
    ('''    val loan: String = "",
) {''', '''    val loan: String = "",
    /** Cách đọc từ gốc kiểu Việt, vd computer → "cơm-piu-tơ" */
    val loanRead: String = "",
) {'''),
    ('''.put("loan", loan)''', '''.put("loan", loan).put("loan_read", loanRead)'''),
    ('''            loan = o.str("loan", "loanword", "english", "origin"),''',
     '''            loan = o.str("loan", "loanword", "english", "origin"),
            loanRead = o.str("loan_read", "loanRead", "loan_reading"),'''),
])
patch(J + 'data/Entities.kt', [
    ('''    @ColumnInfo(defaultValue = "") val loan: String = "",
) {''', '''    @ColumnInfo(defaultValue = "") val loan: String = "",
    /** Cách đọc từ gốc kiểu Việt (vd "cơm-piu-tơ") */
    @ColumnInfo(defaultValue = "") val loanRead: String = "",
) {'''),
    ('''note, breakdown, loan)''', '''note, breakdown, loan, loanRead)'''),
])
patch(J + 'data/Daos.kt', [
    ('''    version = 7,''', '''    version = 8,'''),
    ('''AutoMigration(from = 6, to = 7),''', '''AutoMigration(from = 6, to = 7), AutoMigration(from = 7, to = 8),'''),
])
patch(J + 'data/Repositories.kt', [
    ('''                    loan = existing.loan.ifBlank { it.loan },''',
     '''                    loan = existing.loan.ifBlank { it.loan },
                    loanRead = existing.loanRead.ifBlank { it.loanRead },'''),
    ('''loan = it.loan.trim(), day = day,''', '''loan = it.loan.trim(), loanRead = it.loanRead.trim(), day = day,'''),
    ('''    suspend fun setLoan(ko: String, loan: String): Int {
        val list = dao.findAllByKey(keyOf(ko)).filter { it.hanja.isBlank() && it.loan.isBlank() }
        list.forEach { dao.update(it.copy(loan = loan)) }
        return list.size
    }''',
     '''    suspend fun setLoan(ko: String, loan: String, read: String = ""): Int {
        val list = dao.findAllByKey(keyOf(ko)).filter { it.hanja.isBlank() && (it.loan.isBlank() || it.loanRead.isBlank()) }
        list.forEach { dao.update(it.copy(loan = it.loan.ifBlank { loan }, loanRead = it.loanRead.ifBlank { read })) }
        return list.size
    }'''),
])
patch(J + 'ai/PromptBuilder.kt', [
    ('''tiếng Anh chỉ ghi từ, tiếng khác ghi kèm tên tiếng trong ngoặc. Từ mượn thì hanja để trống.''',
     '''tiếng Anh chỉ ghi từ, tiếng khác ghi kèm tên tiếng trong ngoặc. loan_read = cách đọc từ gốc phiên âm kiểu Việt cho dễ hiểu (vd computer → cơm-piu-tơ, Arbeit → ác-bai). Từ mượn thì hanja để trống.'''),
    ('''"loan":"",''', '''"loan":"","loan_read":"",'''),
    ('''.put("loan", str())''', '''.put("loan", str()).put("loan_read", str())'''),
    ('''"han_viet", "loan", "pos",''', '''"han_viet", "loan", "loan_read", "pos",'''),
])

# ================= Hiển thị cách đọc =================
patch(J + 'ui/components/Components.kt', [
    ('''                            Text("từ mượn", style = MaterialTheme.typography.labelSmall, color = Brand.Cyan, fontStyle = FontStyle.Italic)''',
     '''                            Text(it.loanRead.ifBlank { "từ mượn" }, style = MaterialTheme.typography.labelSmall, color = Brand.Cyan, fontStyle = FontStyle.Italic)'''),
    ('''        else if (v.loan.isNotBlank()) append(" · ").append(v.loan)''',
     '''        else if (v.loan.isNotBlank()) {
            append(" · ").append(v.loan)
            if (v.loanRead.isNotBlank()) append(" (đọc ").append(v.loanRead).append(")")
        }'''),
])
patch(J + 'ui/screens/VocabScreen.kt', [
    ('''Pill("từ mượn · ${w.loan}", color = Brand.Cyan)''',
     '''Pill("từ mượn · ${w.loan}" + (if (w.loanRead.isNotBlank()) " · ${w.loanRead}" else ""), color = Brand.Cyan)'''),
])
patch(J + 'ui/screens/ReviewScreen.kt', [
    ('''private fun OriginBadge(hanja: String, hanViet: String, loan: String) {''',
     '''private fun OriginBadge(hanja: String, hanViet: String, loan: String, loanRead: String = "") {'''),
    ('''            Text(loan, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)''',
     '''            Text(loan, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            if (loanRead.isNotBlank()) {
                Text("  ·  $loanRead", color = color, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyMedium)
            }'''),
    ('''OriginBadge(v.hanja, v.hanViet, v.loan)''', '''OriginBadge(v.hanja, v.hanViet, v.loan, v.loanRead)'''),
])
patch(J + 'ui/MainViewModel.kt', [
    ('''Hán Việt,Từ mượn,''', '''Hán Việt,Từ mượn,Cách đọc từ mượn,'''),
    ('''it.hanViet, it.loan, it.pos,''', '''it.hanViet, it.loan, it.loanRead, it.pos,'''),
])

# ================= Tab "Chữ Hán" → "Từ mượn" (Hán Hàn + ngoại lai) =================
V = J + 'ui/screens/VocabScreen.kt'
patch(V, [
    ('''HANJA("Chữ Hán") }''', '''HANJA("Từ mượn") }'''),
    ('''                    BookTab.HANJA -> "${t.label} (${hanjaIndex.size})"''',
     '''                    BookTab.HANJA -> "${t.label} (${hanjaWordCount + loanWords.size})"'''),
    ('''            HanjaTab(hanjaIndex, query) { ch -> hanjaOpen = ch to "" }''',
     '''            HanjaTab(hanjaIndex, loanWords, query, onSpeak = { vm.speakKorean(it) }) { ch -> hanjaOpen = ch to "" }'''),
    ('''    val hanjaIndex = remember(all) { HanjaIndex.build(all) }''',
     '''    val hanjaIndex = remember(all) { HanjaIndex.build(all) }
    val hanjaWordCount = remember(hanjaIndex) { hanjaIndex.flatMap { it.words }.distinctBy { it.id }.size }
    // Từ mượn ngoại lai (tiếng Anh, Đức, Pháp…): mỗi từ lấy bản mới nhất
    val loanWords = remember(all) { all.latestPerWord().filter { it.hanja.isBlank() && it.loan.isNotBlank() } }'''),
    ('''placeholder = { Text(if (tab == BookTab.VOCAB) "Tìm tiếng Việt, tiếng Hàn, Hán Việt…" else "Tìm đuôi câu, nghĩa, dạng…") },''',
     '''placeholder = {
                    Text(
                        when (tab) {
                            BookTab.VOCAB -> "Tìm tiếng Việt, tiếng Hàn, Hán Việt…"
                            BookTab.GRAMMAR -> "Tìm đuôi câu, nghĩa, dạng…"
                            BookTab.HANJA -> "Tìm chữ Hán, Hán Việt, từ gốc…"
                        }
                    )
                },'''),
])

H = J + 'ui/screens/HanjaScreen.kt'
patch(H, [
    ('''/** Tab "Chữ Hán" trong Sổ từ: lưới chữ Hán, chạm để xem các từ cùng chữ. */
@Composable
fun HanjaTab(index: List<HanjaChar>, query: String, onOpen: (String) -> Unit) {''',
     '''/** Từ gốc + tên tiếng: "Arbeit (tiếng Đức)" → ("Arbeit", "Tiếng Đức"); không ghi tiếng → tiếng Anh. */
fun loanParts(loan: String): Pair<String, String> {
    val m = Regex("^(.*?)\\\\s*\\\\((?:tiếng\\\\s+)?([^)]+)\\\\)\\\\s*$", RegexOption.IGNORE_CASE).find(loan.trim())
    return if (m != null) m.groupValues[1].trim() to "Tiếng " + m.groupValues[2].trim() else loan.trim() to "Tiếng Anh"
}

/**
 * Tab "Từ mượn" trong Sổ từ: 2 phần
 * - Hán Hàn: lưới chữ Hán + âm Hán Việt, chạm để xem các từ cùng chữ
 * - Ngoại lai: từ mượn tiếng Anh, Đức, Pháp… kèm từ gốc và cách đọc kiểu Việt
 */
@Composable
fun HanjaTab(index: List<HanjaChar>, loans: List<VocabEntity>, query: String, onSpeak: (String) -> Unit, onOpen: (String) -> Unit) {
    var mode by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableIntStateOf(if (index.isEmpty() && loans.isNotEmpty()) 1 else 0)
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LoanModeChip("Hán Hàn · ${index.size} chữ", mode == 0, Brand.Amber, Modifier.weight(1f)) { mode = 0 }
            LoanModeChip("Ngoại lai · ${loans.size} từ", mode == 1, Brand.Cyan, Modifier.weight(1f)) { mode = 1 }
        }
        if (mode == 0) HanjaGrid(index, query, onOpen) else LoanList(loans, query, onSpeak)
    }
}

@Composable
private fun LoanModeChip(label: String, on: Boolean, color: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) color.copy(alpha = 0.16f) else LocalExtra.current.card)
            .border(1.dp, if (on) color.copy(alpha = 0.6f) else LocalExtra.current.cardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, color = if (on) color else LocalExtra.current.muted, maxLines = 1,
            style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/** Danh sách từ mượn ngoại lai, nhóm theo tiếng gốc. */
@Composable
private fun LoanList(loans: List<VocabEntity>, query: String, onSpeak: (String) -> Unit) {
    val x = LocalExtra.current
    val q = HanjaIndex.fold(query.trim())
    val groups = remember(loans, q) {
        loans.filter { w ->
            q.isEmpty() || w.ko.contains(query.trim()) || HanjaIndex.fold(w.loan).contains(q) ||
                HanjaIndex.fold(w.loanRead).contains(q) || HanjaIndex.fold(w.vi).contains(q)
        }.groupBy { loanParts(it.loan).second }.toList().sortedByDescending { it.second.size }
    }
    if (loans.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyHint(Icons.Rounded.Translate, "Chưa có từ mượn ngoại lai", "Từ mượn tiếng Anh… (vd 컴퓨터 – computer) sẽ hiện ở đây.")
        }
        return
    }
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brand.Cyan.copy(alpha = 0.10f))
                    .padding(14.dp)
            ) {
                Text("${loans.size} từ mượn ngoại lai", fontWeight = FontWeight.Bold)
                Text("Đọc theo âm gốc là đoán ra nghĩa (컴퓨터 → computer)", style = MaterialTheme.typography.bodySmall, color = x.muted)
            }
        }
        groups.forEach { (lang, words) ->
            item(key = "lang_$lang") {
                Text(
                    "${lang.uppercase()} · ${words.size}", color = Brand.Cyan,
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }
            items(words.size, key = { "loan_${words[it].id}" }) { i ->
                val w = words[i]
                val (origin, _) = loanParts(w.loan)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(x.card)
                        .border(1.dp, x.cardBorder, RoundedCornerShape(16.dp))
                        .clickable { onSpeak(w.ko) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(w.ko, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(w.vi, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(origin, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Brand.Cyan)
                            if (w.loanRead.isNotBlank()) {
                                Text("  ·  đọc ${w.loanRead}", style = MaterialTheme.typography.bodySmall, color = x.muted)
                            }
                        }
                    }
                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Nghe", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** Lưới chữ Hán: chạm để xem các từ cùng chữ. */
@Composable
private fun HanjaGrid(index: List<HanjaChar>, query: String, onOpen: (String) -> Unit) {'''),
])
print('ok')
