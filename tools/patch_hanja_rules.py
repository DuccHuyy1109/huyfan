import io, sys, os

root = sys.argv[1]  # .../app/src/main/java/com/hankukko/huyfan


def patch(rel, pairs):
    p = os.path.join(root, rel)
    s = io.open(p, encoding='utf-8').read()
    for a, b in pairs:
        if a not in s:
            raise SystemExit(rel + ' NOT FOUND: ' + a[:90])
        s = s.replace(a, b, 1)
    io.open(p, 'w', encoding='utf-8').write(s)


def add_imports(rel, lines):
    p = os.path.join(root, rel)
    s = io.open(p, encoding='utf-8').read()
    for line in lines:
        if line + '\n' not in s:
            # chèn sau dòng package
            i = s.index('\n', s.index('package ')) + 1
            s = s[:i] + '\n' + line + s[i:] if False else s.replace('\nimport ', '\n' + line + '\nimport ', 1)
    io.open(p, 'w', encoding='utf-8').write(s)


# ---------- Sổ từ: tab Chữ Hán, chạm chữ Hán, chip quy tắc ----------
V = 'ui/screens/VocabScreen.kt'
patch(V, [
    ('''enum class BookTab(val label: String) { VOCAB("Từ vựng"), GRAMMAR("Ngữ pháp") }''',
     '''enum class BookTab(val label: String) { VOCAB("Từ vựng"), GRAMMAR("Ngữ pháp"), HANJA("Chữ Hán") }'''),
    ('''    onCreateLesson: (vocabIds: Set<Long>, grammarIds: Set<Long>) -> Unit,
) {
    val context = LocalContext.current''', '''    onCreateLesson: (vocabIds: Set<Long>, grammarIds: Set<Long>) -> Unit,
    onAsk: (String) -> Unit = {},
) {
    val context = LocalContext.current'''),
    ('''    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = selVocab.isNotEmpty() || selGrammar.isNotEmpty()''', '''    var confirmDelete by remember { mutableStateOf(false) }
    // Bảng chữ Hán (chữ / từ, âm Hán Việt) và bảng quy tắc đang mở
    var hanjaOpen by remember { mutableStateOf<Pair<String, String>?>(null) }
    var ruleOpen by remember { mutableStateOf<String?>(null) }
    val hanjaIndex = remember(all) { HanjaIndex.build(all) }
    val selecting = selVocab.isNotEmpty() || selGrammar.isNotEmpty()'''),
    ('''            { t -> if (t == BookTab.VOCAB) "${t.label} (${all.size})" else "${t.label} (${allGrammar.size})" },''',
     '''            { t ->
                when (t) {
                    BookTab.VOCAB -> "${t.label} (${all.size})"
                    BookTab.GRAMMAR -> "${t.label} (${allGrammar.size})"
                    BookTab.HANJA -> "${t.label} (${hanjaIndex.size})"
                }
            },'''),
    ('''        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VocabFilter.entries.forEach { f -> FilterPill(f.label, f == filter) { onFilter(f) } }''', '''        if (tab != BookTab.HANJA) Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VocabFilter.entries.forEach { f -> FilterPill(f.label, f == filter) { onFilter(f) } }'''),
    ('''        val empty = if (tab == BookTab.VOCAB) vocabList.isEmpty() else grammarList.isEmpty()
        if (empty) {''', '''        val empty = if (tab == BookTab.VOCAB) vocabList.isEmpty() else grammarList.isEmpty()
        if (tab == BookTab.HANJA) {
            HanjaTab(hanjaIndex, query) { ch -> hanjaOpen = ch to "" }
        } else if (empty) {'''),
    ('''                                onStar = { vm.toggleStar(w) },
                                onDelete = { selVocab = setOf(w.id); confirmDelete = true },
                            )''', '''                                onStar = { vm.toggleStar(w) },
                                onDelete = { selVocab = setOf(w.id); confirmDelete = true },
                                onHanja = { hanjaOpen = w.hanja to w.hanViet },
                                onRule = { ruleOpen = it },
                            )'''),
    ('''                                onStar = { vm.toggleStarGrammar(g) },
                                onDelete = { selGrammar = setOf(g.id); confirmDelete = true },
                            )''', '''                                onStar = { vm.toggleStarGrammar(g) },
                                onDelete = { selGrammar = setOf(g.id); confirmDelete = true },
                                onRule = { ruleOpen = it },
                            )'''),
    ('''    if (confirmDelete) {''', '''    hanjaOpen?.let { (h, hv) ->
        HanjaSheet(h, hv, hanjaIndex, onSpeak = { vm.speakKorean(it) }, onAsk = { q -> hanjaOpen = null; onAsk(q) }, onDismiss = { hanjaOpen = null })
    }
    ruleOpen?.let { id ->
        RuleSheet(id, onSpeak = { vm.speakKorean(it) }, onAsk = { q -> ruleOpen = null; onAsk(q) }, onDismiss = { ruleOpen = null })
    }
    if (confirmDelete) {'''),
    # WordCard
    ('''    onStar: () -> Unit,
    onDelete: () -> Unit,
) {
    val x = LocalExtra.current
    var expanded by rememberSaveable(w.id) { mutableStateOf(false) }''', '''    onStar: () -> Unit,
    onDelete: () -> Unit,
    onHanja: () -> Unit = {},
    onRule: (String) -> Unit = {},
) {
    val x = LocalExtra.current
    var expanded by rememberSaveable(w.id) { mutableStateOf(false) }
    val rules = remember(w.ko) { RuleLinks.forVocab(w.ko) }'''),
    ('''            if (w.hanja.isNotBlank()) Pill("${w.hanja} · ${w.hanViet}".trimEnd(' ', '·'), color = Brand.Amber)''',
     '''            if (w.hanja.isNotBlank()) {
                Pill(
                    "${w.hanja} · ${w.hanViet}".trimEnd(' ', '·') + " ›", color = Brand.Amber,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onHanja),
                )
            }'''),
    ('''        if (dupDays.isNotEmpty()) DupBadge(dupDays)
        if (w.breakdown.isNotBlank()) {''', '''        if (dupDays.isNotEmpty()) DupBadge(dupDays)
        RuleChips(rules, onRule, Modifier.padding(top = 6.dp))
        if (w.breakdown.isNotBlank()) {'''),
    # GrammarCard
    ('''    onStar: () -> Unit,
    onDelete: () -> Unit,
) {
    val x = LocalExtra.current
    var expanded by rememberSaveable(g.id) { mutableStateOf(false) }''', '''    onStar: () -> Unit,
    onDelete: () -> Unit,
    onRule: (String) -> Unit = {},
) {
    val x = LocalExtra.current
    var expanded by rememberSaveable(g.id) { mutableStateOf(false) }
    val rules = remember(g.pattern, g.form) { RuleLinks.forGrammar(g.pattern, g.form) }'''),
    ('''        if (dupDays.isNotEmpty()) DupBadge(dupDays)
        if (g.combine.isNotBlank()) {''', '''        if (dupDays.isNotEmpty()) DupBadge(dupDays)
        RuleChips(rules, onRule, Modifier.padding(top = 6.dp))
        if (g.combine.isNotBlank()) {'''),
])
add_imports(V, ['import com.hankukko.huyfan.data.RuleLinks', 'import androidx.compose.foundation.shape.CircleShape'])

# ---------- Chat: chạm chữ Hán trong bảng từ vựng ----------
C = 'ui/components/Components.kt'
patch(C, [
    ('''    savedFlags: List<Boolean>? = null,
    onAdd: ((Int) -> Unit)? = null,
) {
    val x = LocalExtra.current
    val shape = RoundedCornerShape(18.dp)''', '''    savedFlags: List<Boolean>? = null,
    onAdd: ((Int) -> Unit)? = null,
    onHanja: ((VocabItem) -> Unit)? = null,
) {
    val x = LocalExtra.current
    val shape = RoundedCornerShape(18.dp)'''),
    ('''                    Column(Modifier.weight(W_HAN).padding(end = 6.dp)) {
                        if (it.hanja.isNotBlank()) {''', '''                    Column(
                        Modifier
                            .weight(W_HAN)
                            .padding(end = 6.dp)
                            .then(if (onHanja != null && it.hanja.isNotBlank()) Modifier.clickable { onHanja(it) } else Modifier)
                    ) {
                        if (it.hanja.isNotBlank()) {'''),
    ('''    onAddVocab: ((Int) -> Unit)? = null,
    onAddGrammar: ((Int) -> Unit)? = null,
) {''', '''    onAddVocab: ((Int) -> Unit)? = null,
    onAddGrammar: ((Int) -> Unit)? = null,
    onHanja: ((VocabItem) -> Unit)? = null,
) {'''),
    ('''            VocabTable(vocab, m.savedCount, onSpeakKorean, onDelete = onDeleteVocab, savedFlags = vocabSaved, onAdd = onAddVocab)''',
     '''            VocabTable(vocab, m.savedCount, onSpeakKorean, onDelete = onDeleteVocab, savedFlags = vocabSaved, onAdd = onAddVocab, onHanja = onHanja)'''),
])

CH = 'ui/screens/ChatScreen.kt'
patch(CH, [
    ('''    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()''', '''    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    var hanjaOpen by remember { mutableStateOf<Pair<String, String>?>(null) }
    val hanjaIndex = remember(allVocab) { com.hankukko.huyfan.ui.screens.HanjaIndex.build(allVocab) }'''),
    ('''                            onAddGrammar = { i ->
                                m.grammar.getOrNull(i)?.let { context.toastShort("Đã lưu “${it.pattern}” vào sổ") }
                                vm.engine.addLearned(m, LessonItem.GRAMMAR, i)
                            },
                        )''', '''                            onAddGrammar = { i ->
                                m.grammar.getOrNull(i)?.let { context.toastShort("Đã lưu “${it.pattern}” vào sổ") }
                                vm.engine.addLearned(m, LessonItem.GRAMMAR, i)
                            },
                            onHanja = { v -> hanjaOpen = v.hanja to v.hanViet },
                        )'''),
])
p = os.path.join(root, CH)
s = io.open(p, encoding='utf-8').read()
# Hiện bảng chữ Hán ở cuối ChatScreen: chèn trước dấu đóng cuối cùng của hàm ChatScreen
anchor = '\n@Composable\nprivate fun '
i = s.index('fun ChatScreen(')
j = s.index(anchor, i)
k = s.rindex('}', i, j)
s = s[:k] + '''    hanjaOpen?.let { (h, hv) ->
        com.hankukko.huyfan.ui.screens.HanjaSheet(
            h, hv, hanjaIndex,
            onSpeak = { vm.speakKorean(it) },
            onAsk = { q -> hanjaOpen = null; vm.send(q) },
            onDismiss = { hanjaOpen = null },
        )
    }
''' + s[k:]
io.open(p, 'w', encoding='utf-8').write(s)

# ---------- MainActivity: Sổ từ hỏi HUY FAN ----------
patch('ui/MainActivity.kt', [
    ('''                            onCreateLesson = { v, g -> lessonEditor = LessonEditorRequest(vocabIds = v, grammarIds = g) },''',
     '''                            onCreateLesson = { v, g -> lessonEditor = LessonEditorRequest(vocabIds = v, grammarIds = g) },
                            onAsk = { q -> tab = Tab.CHAT; vm.send(q) },'''),
])
print('ok')
