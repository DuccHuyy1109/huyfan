import io, sys, os

root = sys.argv[1]  # .../huyfan


def patch(rel, pairs):
    p = os.path.join(root, rel)
    s = io.open(p, encoding='utf-8').read()
    for a, b in pairs:
        if a not in s:
            raise SystemExit(rel + ' NOT FOUND: ' + a[:90])
        s = s.replace(a, b, 1)
    io.open(p, 'w', encoding='utf-8').write(s)


# ---------- ViewModel ----------
patch('ui/MainViewModel.kt', [
    ('''    fun speakKorean(text: String) = c.speaker.speakKorean(text)''', '''    fun speakKorean(text: String) = c.speaker.speakKorean(text)
    fun stopSpeaking() = c.speaker.stop()

    // ---------- Hội thoại nhập vai ----------
    fun rolePlayTurn(
        system: String,
        turns: List<com.hankukko.huyfan.ai.Turn>,
        onResult: (Result<com.hankukko.huyfan.assistant.RolePlay.AiTurn>) -> Unit,
    ) = viewModelScope.launch {
        onResult(runCatching {
            val (raw, _) = c.ai.askJson(system, turns, com.hankukko.huyfan.assistant.RolePlay.schema())
            com.hankukko.huyfan.assistant.RolePlay.parse(raw)
        })
    }

    fun saveRolePlayVocab(items: List<com.hankukko.huyfan.assistant.RolePlay.Phrase>, onDone: (Int) -> Unit) = viewModelScope.launch {
        val n = c.vocab.saveFromAi(items.map { VocabItem(vi = it.vi, ko = it.ko) }).size
        launch { c.sheets.syncPending() }
        HuyFanWidget.refresh(getApplication())
        onDone(n)
    }'''),
])

# ---------- MainActivity: mở Luyện phát âm / Hội thoại ----------
patch('ui/MainActivity.kt', [
    ('''    var autoStartDue by remember { mutableStateOf(false) }''', '''    var autoStartDue by remember { mutableStateOf(false) }
    var practiceOpen by remember { mutableStateOf(false) }
    var rolePlayOpen by remember { mutableStateOf(false) }'''),
    ('''                            autoStartDue = autoStartDue,
                            onAutoStartConsumed = { autoStartDue = false },''', '''                            autoStartDue = autoStartDue,
                            onAutoStartConsumed = { autoStartDue = false },
                            onPractice = { vm.engine.cancel(); practiceOpen = true },
                            onRolePlay = { vm.engine.cancel(); rolePlayOpen = true },'''),
    ('''    lessonEditor?.let { LessonEditorDialog(vm, it, onDismiss = { lessonEditor = null }) }''', '''    lessonEditor?.let { LessonEditorDialog(vm, it, onDismiss = { lessonEditor = null }) }
    if (practiceOpen) com.hankukko.huyfan.ui.screens.PracticeDialog(vm, onDismiss = { practiceOpen = false })
    if (rolePlayOpen) com.hankukko.huyfan.ui.screens.RolePlayDialog(vm, onDismiss = { rolePlayOpen = false })'''),
])

# ---------- Thẻ ôn: chip quy tắc ở mặt sau ----------
patch('ui/screens/ReviewScreen.kt', [
    ('''    var feedback by remember(card.key) { mutableStateOf<Pair<String, Boolean>?>(null) }
    // Bảng viết tay''', '''    var feedback by remember(card.key) { mutableStateOf<Pair<String, Boolean>?>(null) }
    var ruleOpen by remember(card.key) { mutableStateOf<String?>(null) }
    val ruleLinks = remember(card.key) {
        v?.let { com.hankukko.huyfan.data.RuleLinks.forVocab(it.ko) }
            ?: g?.let { com.hankukko.huyfan.data.RuleLinks.forGrammar(it.pattern, it.form) } ?: emptyList()
    }
    // Bảng viết tay'''),
    ('''                    Spacer(Modifier.height(12.dp))
                    IconButton(onClick = { vm.speakKorean(v.exampleKo.ifBlank { v.ko }) }) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Nghe", tint = MaterialTheme.colorScheme.primary)
                    }''', '''                    Spacer(Modifier.height(12.dp))
                    IconButton(onClick = { vm.speakKorean(v.exampleKo.ifBlank { v.ko }) }) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Nghe", tint = MaterialTheme.colorScheme.primary)
                    }
                    RuleChips(ruleLinks, { ruleOpen = it })'''),
    ('''                        IconButton(onClick = { vm.speakKorean(g.exampleKo) }) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Nghe", tint = MaterialTheme.colorScheme.primary)
                        }
                    }''', '''                        IconButton(onClick = { vm.speakKorean(g.exampleKo) }) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Nghe", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    RuleChips(ruleLinks, { ruleOpen = it })'''),
    ('''    // Kết quả so khớp giọng nói / chữ đang nghe''', '''    ruleOpen?.let { id -> RuleSheet(id, onSpeak = { vm.speakKorean(it) }, onAsk = { ruleOpen = null }, onDismiss = { ruleOpen = null }) }

    // Kết quả so khớp giọng nói / chữ đang nghe'''),
])

# ---------- Dọn Hán tự cũ (lẫn chữ Latin / gạch nối) 1 lần khi mở app ----------
patch('data/Repositories.kt', [
    ('''    suspend fun toggleStar(v: VocabEntity) = dao.update(v.copy(starred = !v.starred))''', '''    suspend fun toggleStar(v: VocabEntity) = dao.update(v.copy(starred = !v.starred))

    /** Cột Hán tự chỉ giữ chữ Hán (dữ liệu cũ có thể lẫn "-", chữ Latin như 補助battery). */
    suspend fun cleanHanja(): Int {
        val bad = Regex("[^\\\\u3400-\\\\u4DBF\\\\u4E00-\\\\uF900-\\\\uFAFF\\\\u9FFF,/ ]")
        var n = 0
        dao.all().filter { it.hanja.isNotBlank() && bad.containsMatchIn(it.hanja) }.forEach {
            val clean = it.hanja.replace(Regex("[^\\\\u3400-\\\\u4DBF\\\\u4E00-\\\\u9FFF\\\\uF900-\\\\uFAFF,/ ]+"), "").replace(Regex(" +"), " ").trim(' ', ',', '/')
            dao.update(it.copy(hanja = clean, synced = false))
            n++
        }
        return n
    }'''),
])
patch('AppContainer.kt', [
    ('''        appScope.launch { sheets.syncPending() }''', '''        appScope.launch {
            vocab.cleanHanja()
            sheets.syncPending()
        }'''),
])
print('ok')
