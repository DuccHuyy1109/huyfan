import sys

p = sys.argv[1]
s = open(p, encoding='utf-8').read()


def rep(old, new):
    global s
    if old not in s:
        raise SystemExit('NOT FOUND: ' + old[:80])
    s = s.replace(old, new, 1)


rep('''    savedCount: Int,
    onSpeakKorean: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val x = LocalExtra.current
    val shape = RoundedCornerShape(18.dp)''', '''    savedCount: Int,
    onSpeakKorean: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: ((Int) -> Unit)? = null,
) {
    val x = LocalExtra.current
    val shape = RoundedCornerShape(18.dp)''')

rep('''                    Text(
                        it.vi, Modifier.weight(W_VI).padding(end = 6.dp),
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                    )''', '''                    Column(Modifier.weight(W_VI).padding(end = 6.dp)) {
                        Text(it.vi, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        if (onDelete != null) DeleteChip { onDelete(i) }
                    }''')

rep('''@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {''', '''/** Nút thùng rác nhỏ: xoá khỏi sổ ngay, không cần hỏi lại. */
@Composable
private fun DeleteChip(onClick: () -> Unit) {
    Box(
        Modifier
            .padding(top = 6.dp)
            .size(26.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.DeleteOutline, "Xoá khỏi sổ", tint = LocalExtra.current.muted, modifier = Modifier.size(16.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Bảng ngữ pháp: Đuôi câu | Nghĩa | Dạng | Từ kết hợp | Câu mẫu
// Màn hình điện thoại hẹp → mỗi điểm ngữ pháp là 1 bảng dọc (cột tiêu đề | nội dung) cho dễ đọc.
// ---------------------------------------------------------------------------------------------

@Composable
fun GrammarTable(
    items: List<GrammarItem>,
    savedCount: Int,
    onSpeakKorean: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: ((Int) -> Unit)? = null,
) {
    val x = LocalExtra.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(x.card)
            .border(1.dp, x.cardBorder, shape)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Brand.Violet.copy(alpha = 0.24f), Brand.Red.copy(alpha = 0.14f))))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.AccountTree, null, tint = Brand.Violet, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("NGỮ PHÁP", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            if (savedCount > 0) Pill("Đã lưu vào sổ", Icons.Rounded.CheckCircle, Brand.Green)
        }
        items.forEachIndexed { i, g ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(6.dp).background(x.tableHeader))
            GrammarRow("Đuôi câu", striped = false) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(g.pattern, color = Brand.Violet, fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    if (onDelete != null) DeleteChip { onDelete(i) }
                }
            }
            GrammarRow("Nghĩa", striped = true) {
                Text(g.meaning, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            GrammarRow("Dạng", striped = false) {
                Text(g.form.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
            }
            GrammarRow("Từ kết hợp", striped = true) {
                Text(g.combine.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
            }
            GrammarRow("Câu mẫu", striped = false) {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = g.exampleKo.isNotBlank()) { onSpeakKorean(g.exampleKo) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.exampleKo.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
                        if (g.exampleKo.isNotBlank()) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Nghe", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        }
                    }
                    if (g.exampleVi.isNotBlank()) Text(g.exampleVi, style = MaterialTheme.typography.labelSmall, color = x.muted)
                }
            }
            if (g.note.isNotBlank()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Lightbulb, null, tint = Brand.Amber, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(g.note, style = MaterialTheme.typography.labelSmall, color = x.muted)
                }
            }
        }
    }
}

@Composable
private fun GrammarRow(label: String, striped: Boolean, content: @Composable () -> Unit) {
    val x = LocalExtra.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (striped) x.tableStripe else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label.uppercase(),
            Modifier.width(88.dp).padding(top = 2.dp),
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            color = x.muted, letterSpacing = 0.5.sp,
        )
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {''')

rep('''    onCopy: (String) -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    val x = LocalExtra.current
    if (m.isUser) {''', '''    onCopy: (String) -> Unit,
    onRetry: (() -> Unit)? = null,
    onDeleteVocab: ((Int) -> Unit)? = null,
    onDeleteGrammar: ((Int) -> Unit)? = null,
) {
    val x = LocalExtra.current
    if (m.isUser) {''')

rep('''        val vocab = m.vocab
        if (vocab.isNotEmpty()) {
            VocabTable(vocab, m.savedCount, onSpeakKorean)
            Spacer(Modifier.height(8.dp))
        }''', '''        val vocab = m.vocab
        if (vocab.isNotEmpty()) {
            VocabTable(vocab, m.savedCount, onSpeakKorean, onDelete = onDeleteVocab)
            Spacer(Modifier.height(8.dp))
        }
        val grammar = m.grammar
        if (grammar.isNotEmpty()) {
            GrammarTable(grammar, m.savedCount, onSpeakKorean, onDelete = onDeleteGrammar)
            Spacer(Modifier.height(8.dp))
        }''')

rep('''private fun copyText(m: ChatMessage): String = buildString {''', '''private fun copyText(m: ChatMessage): String = buildString {
    m.grammar.forEach { g ->
        append(g.pattern).append(" – ").append(g.meaning)
        if (g.form.isNotBlank()) append(" (").append(g.form).append(")")
        append('\\n')
        if (g.combine.isNotBlank()) append("  ").append(g.combine).append('\\n')
        if (g.exampleKo.isNotBlank()) append("  ").append(g.exampleKo).append(" – ").append(g.exampleVi).append('\\n')
    }''')

rep('import com.hankukko.huyfan.ai.VocabItem\n', 'import com.hankukko.huyfan.ai.GrammarItem\nimport com.hankukko.huyfan.ai.VocabItem\n')
rep('import androidx.compose.material.icons.rounded.CheckCircle\n',
    'import androidx.compose.material.icons.rounded.AccountTree\nimport androidx.compose.material.icons.rounded.CheckCircle\nimport androidx.compose.material.icons.rounded.DeleteOutline\n')

open(p, 'w', encoding='utf-8').write(s)
print('ok')
