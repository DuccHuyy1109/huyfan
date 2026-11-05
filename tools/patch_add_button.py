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


# ---------- Repository: tìm bản mới nhất theo khoá ----------
patch('data/Repositories.kt', [
    ('''    suspend fun findSameOnDay(ko: String, day: String): VocabEntity? {''',
     '''    /** Bản mới nhất của từ này trong sổ (bất kỳ ngày nào). */
    suspend fun findLatest(ko: String): VocabEntity? = dao.findAllByKey(keyOf(ko)).firstOrNull()

    suspend fun findSameOnDay(ko: String, day: String): VocabEntity? {'''),
    ('''    suspend fun findSameOnDay(pattern: String, day: String): GrammarEntity? {''',
     '''    suspend fun findLatest(pattern: String): GrammarEntity? = dao.findAllByKey(grammarKeyOf(pattern)).firstOrNull()

    suspend fun findSameOnDay(pattern: String, day: String): GrammarEntity? {'''),
])

# ---------- Engine: thêm vào sổ; xoá tìm cả bản mới nhất ----------
patch('assistant/ConversationEngine.kt', [
    ('''c.vocab.findSameOnDay(item.ko, day)?.let''', '''(c.vocab.findSameOnDay(item.ko, day) ?: c.vocab.findLatest(item.ko))?.let'''),
    ('''c.grammar.findSameOnDay(item.pattern, day)?.let''', '''(c.grammar.findSameOnDay(item.pattern, day) ?: c.grammar.findLatest(item.pattern))?.let'''),
    ('''    /** Hỏi lại câu trước (khi lỗi mạng...) */''', '''    /** Người dùng bấm "+" trên bảng: lưu từ/ngữ pháp đó vào sổ hôm nay. */
    fun addLearned(m: ChatMessage, kind: String, index: Int) {
        scope.launch {
            val n = if (kind == LessonItem.VOCAB) {
                m.vocab.getOrNull(index)?.let { c.vocab.saveFromAi(listOf(it)).size } ?: 0
            } else {
                m.grammar.getOrNull(index)?.let { c.grammar.saveFromAi(listOf(it)).size } ?: 0
            }
            if (n > 0) {
                scope.launch { c.sheets.syncPending() }
                HuyFanWidget.refresh(app)
            }
        }
    }

    /** Hỏi lại câu trước (khi lỗi mạng...) */'''),
])

# ---------- Bảng: nút + / thùng rác theo trạng thái đã lưu ----------
patch('ui/components/Components.kt', [
    # VocabTable signature
    ('''fun VocabTable(
    items: List<VocabItem>,
    savedCount: Int,
    onSpeakKorean: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: ((Int) -> Unit)? = null,
) {''', '''fun VocabTable(
    items: List<VocabItem>,
    savedCount: Int,
    onSpeakKorean: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: ((Int) -> Unit)? = null,
    savedFlags: List<Boolean>? = null,
    onAdd: ((Int) -> Unit)? = null,
) {'''),
    ('''            Text("TỪ VỰNG", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            if (savedCount > 0) Pill("Đã lưu vào sổ", Icons.Rounded.CheckCircle, Brand.Green)''',
     '''            Text("TỪ VỰNG", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            SavedPill(savedFlags, savedCount)'''),
    ('''                        if (onDelete != null) DeleteChip { onDelete(i) }
                    }''', '''                        val saved = savedFlags?.getOrNull(i) ?: true
                        if (saved && onDelete != null) DeleteChip { onDelete(i) }
                        if (!saved && onAdd != null) AddChip { onAdd(i) }
                    }'''),
    # GrammarTable signature
    ('''fun GrammarTable(
    items: List<GrammarItem>,
    savedCount: Int,
    onSpeakKorean: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: ((Int) -> Unit)? = null,
) {''', '''fun GrammarTable(
    items: List<GrammarItem>,
    savedCount: Int,
    onSpeakKorean: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: ((Int) -> Unit)? = null,
    savedFlags: List<Boolean>? = null,
    onAdd: ((Int) -> Unit)? = null,
) {'''),
    ('''            Text("NGỮ PHÁP", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            if (savedCount > 0) Pill("Đã lưu vào sổ", Icons.Rounded.CheckCircle, Brand.Green)''',
     '''            Text("NGỮ PHÁP", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            SavedPill(savedFlags, savedCount)'''),
    ('''                    Text(g.pattern, color = Brand.Violet, fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    if (onDelete != null) DeleteChip { onDelete(i) }''',
     '''                    Text(g.pattern, color = Brand.Violet, fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    val saved = savedFlags?.getOrNull(i) ?: true
                    if (saved && onDelete != null) DeleteChip { onDelete(i) }
                    if (!saved && onAdd != null) AddChip { onAdd(i) }'''),
    # chips
    ('''/** Nút thùng rác nhỏ: xoá khỏi sổ ngay, không cần hỏi lại. */''', '''/** Nhãn trạng thái lưu: tất cả / một phần / chưa lưu. */
@Composable
private fun SavedPill(flags: List<Boolean>?, savedCount: Int) {
    if (flags == null) {
        if (savedCount > 0) Pill("Đã lưu vào sổ", Icons.Rounded.CheckCircle, Brand.Green)
        return
    }
    val n = flags.count { it }
    when {
        flags.isEmpty() -> Unit
        n == flags.size -> Pill("Đã lưu vào sổ", Icons.Rounded.CheckCircle, Brand.Green)
        n > 0 -> Pill("Đã lưu $n/${flags.size}", Icons.Rounded.CheckCircle, Brand.Amber)
        else -> Pill("Bấm + để lưu", Icons.Rounded.AddCircleOutline, MaterialTheme.colorScheme.primary)
    }
}

/** Nút "+" nhỏ: lưu mục này vào sổ ngay. */
@Composable
private fun AddChip(onClick: () -> Unit) {
    Box(
        Modifier
            .padding(top = 6.dp)
            .size(26.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, "Lưu vào sổ", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

/** Nút thùng rác nhỏ: xoá khỏi sổ ngay, không cần hỏi lại. */'''),
    # MessageItem
    ('''    onDeleteVocab: ((Int) -> Unit)? = null,
    onDeleteGrammar: ((Int) -> Unit)? = null,
) {''', '''    onDeleteVocab: ((Int) -> Unit)? = null,
    onDeleteGrammar: ((Int) -> Unit)? = null,
    vocabSaved: List<Boolean>? = null,
    grammarSaved: List<Boolean>? = null,
    onAddVocab: ((Int) -> Unit)? = null,
    onAddGrammar: ((Int) -> Unit)? = null,
) {'''),
    ('''            VocabTable(vocab, m.savedCount, onSpeakKorean, onDelete = onDeleteVocab)''',
     '''            VocabTable(vocab, m.savedCount, onSpeakKorean, onDelete = onDeleteVocab, savedFlags = vocabSaved, onAdd = onAddVocab)'''),
    ('''            GrammarTable(grammar, m.savedCount, onSpeakKorean, onDelete = onDeleteGrammar)''',
     '''            GrammarTable(grammar, m.savedCount, onSpeakKorean, onDelete = onDeleteGrammar, savedFlags = grammarSaved, onAdd = onAddGrammar)'''),
    ('import androidx.compose.material.icons.rounded.AccountTree\n',
     'import androidx.compose.material.icons.rounded.AccountTree\nimport androidx.compose.material.icons.rounded.Add\nimport androidx.compose.material.icons.rounded.AddCircleOutline\n'),
])

# ---------- Màn chat ----------
patch('ui/screens/ChatScreen.kt', [
    ('''    val standby by StandbyService.running.collectAsStateWithLifecycle()''',
     '''    val standby by StandbyService.running.collectAsStateWithLifecycle()
    // Khoá các mục đã có trong sổ → bảng biết hiện "+" hay thùng rác
    val allVocab by vm.vocab.collectAsStateWithLifecycle()
    val allGrammar by vm.grammar.collectAsStateWithLifecycle()
    val vocabKeys = remember(allVocab) { allVocab.map { keyOf(it.ko) }.toHashSet() }
    val grammarKeys = remember(allGrammar) { allGrammar.map { grammarKeyOf(it.pattern) }.toHashSet() }'''),
    ('''                            onDeleteGrammar = { i ->
                                m.grammar.getOrNull(i)?.let { context.toastShort("Đã xoá “${it.pattern}” khỏi sổ") }
                                vm.engine.removeLearned(m, LessonItem.GRAMMAR, i)
                            },''', '''                            onDeleteGrammar = { i ->
                                m.grammar.getOrNull(i)?.let { context.toastShort("Đã xoá “${it.pattern}” khỏi sổ") }
                                vm.engine.removeLearned(m, LessonItem.GRAMMAR, i)
                            },
                            vocabSaved = m.vocab.map { keyOf(it.ko) in vocabKeys },
                            grammarSaved = m.grammar.map { grammarKeyOf(it.pattern) in grammarKeys },
                            onAddVocab = { i ->
                                m.vocab.getOrNull(i)?.let { context.toastShort("Đã lưu “${it.ko}” vào sổ") }
                                vm.engine.addLearned(m, LessonItem.VOCAB, i)
                            },
                            onAddGrammar = { i ->
                                m.grammar.getOrNull(i)?.let { context.toastShort("Đã lưu “${it.pattern}” vào sổ") }
                                vm.engine.addLearned(m, LessonItem.GRAMMAR, i)
                            },'''),
    ('import com.hankukko.huyfan.data.LessonItem\n',
     'import com.hankukko.huyfan.data.LessonItem\nimport com.hankukko.huyfan.data.grammarKeyOf\nimport com.hankukko.huyfan.data.keyOf\n'),
])

# ---------- Bảng trợ lý nổi ----------
patch('ui/AssistActivity.kt', [
    ('''    var typing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }''', '''    var typing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val allVocab by c.vocab.all.collectAsStateWithLifecycle(emptyList())
    val allGrammar by c.grammar.all.collectAsStateWithLifecycle(emptyList())
    val vocabKeys = remember(allVocab) { allVocab.map { keyOf(it.ko) }.toHashSet() }
    val grammarKeys = remember(allGrammar) { allGrammar.map { grammarKeyOf(it.pattern) }.toHashSet() }'''),
    ('''                                onDelete = { i -> engine.removeLearned(m, LessonItem.VOCAB, i) },''',
     '''                                onDelete = { i -> engine.removeLearned(m, LessonItem.VOCAB, i) },
                                savedFlags = vocab.map { keyOf(it.ko) in vocabKeys },
                                onAdd = { i -> engine.addLearned(m, LessonItem.VOCAB, i) },'''),
    ('''                                onDelete = { i -> engine.removeLearned(m, LessonItem.GRAMMAR, i) },''',
     '''                                onDelete = { i -> engine.removeLearned(m, LessonItem.GRAMMAR, i) },
                                savedFlags = grammar.map { grammarKeyOf(it.pattern) in grammarKeys },
                                onAdd = { i -> engine.addLearned(m, LessonItem.GRAMMAR, i) },'''),
    ('import com.hankukko.huyfan.data.LessonItem\n',
     'import com.hankukko.huyfan.data.LessonItem\nimport com.hankukko.huyfan.data.grammarKeyOf\nimport com.hankukko.huyfan.data.keyOf\n'),
])
print('ok')
