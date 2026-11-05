import sys

p = sys.argv[1]
s = open(p, encoding='utf-8').read()


def rep(old, new):
    global s
    if old not in s:
        raise SystemExit('NOT FOUND: ' + old[:90])
    s = s.replace(old, new, 1)


# ---------- StudyDialog: chế độ ngược theo cài đặt + nút Cài đặt bài học ----------
rep('''    var front by remember { mutableStateOf(true) } // true: mặt trước là tiếng Hàn / đuôi câu
    var recorded by remember(session, seed) { mutableStateOf(false) }''',
    '''    val settings by vm.settings.collectAsStateWithLifecycle()
    // true: mặt trước là tiếng Hàn / đuôi câu; false (chế độ ngược): nhìn nghĩa → nói từ
    var front by remember { mutableStateOf(!settings.studyReverse) }
    LaunchedEffect(settings.studyReverse) { front = !settings.studyReverse }
    var recorded by remember(session, seed) { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }''')

rep('''                    IconButton(onClick = { front = !front }) { Icon(Icons.Rounded.SwapHoriz, "Đổi chiều") }
                    IconButton(onClick = { seed++ }) { Icon(Icons.Rounded.Shuffle, "Xáo trộn") }''',
    '''                    IconButton(onClick = { front = !front }) { Icon(Icons.Rounded.SwapHoriz, "Đổi chiều") }
                    IconButton(onClick = { seed++ }) { Icon(Icons.Rounded.Shuffle, "Xáo trộn") }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.Tune, "Cài đặt bài học") }''')

rep('''                    StudyCardView(
                        vm, deck[index], index, deck.size, front, flipped,
                        onFlip = { flipped = !flipped },''',
    '''                    StudyCardView(
                        vm, deck[index], index, deck.size, front, flipped,
                        matchPercent = settings.studyMatchPercent,
                        autoSpeak = settings.studyAutoSpeak,
                        onFlip = { flipped = !flipped },
                        onReveal = { flipped = true },''')

rep('''                    StudyResult(known, deck.size, onAgain = { seed++ }, onDone = onDismiss)
                } else {''', '''                    StudyResult(known, deck.size, onAgain = { seed++ }, onDone = onDismiss)
                } else {''')

rep('''                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.StudyResult(''', '''                        },
                    )
                }
            }
        }
    }
    if (showSettings) StudySettingsDialog(vm, onDismiss = { showSettings = false })
}

/** Cài đặt bài học: % nói đúng để lật thẻ, chế độ ngược, tự đọc. */
@Composable
fun StudySettingsDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val x = LocalExtra.current
    var pct by remember(s.studyMatchPercent) { mutableStateOf(s.studyMatchPercent.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cài đặt bài học") },
        text = {
            Column {
                Text("Chế độ học", style = MaterialTheme.typography.labelLarge, color = x.muted)
                Segmented(
                    listOf(false, true), s.studyReverse,
                    { if (it) "Nhìn nghĩa → nói từ" else "Nhìn từ → nói nghĩa" },
                    { r -> vm.updateSettings { it.copy(studyReverse = r) } },
                    modifier = Modifier.padding(horizontal = 0.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text("Nói đúng từ ${pct.toInt()}% thì lật thẻ", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Bấm mic dưới thẻ rồi nói nghĩa (hoặc từ tiếng Hàn ở chế độ ngược). Chỉ lật thẻ, không tự chấm nhớ.",
                    style = MaterialTheme.typography.bodySmall, color = x.muted,
                )
                androidx.compose.material3.Slider(
                    value = pct, onValueChange = { pct = it },
                    onValueChangeFinished = { vm.updateSettings { it.copy(studyMatchPercent = pct.toInt()) } },
                    valueRange = 40f..100f, steps = 11,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tự đọc tiếng Hàn", style = MaterialTheme.typography.bodyLarge)
                        Text("Đọc khi thẻ hiện mặt tiếng Hàn", style = MaterialTheme.typography.bodySmall, color = x.muted)
                    }
                    androidx.compose.material3.Switch(
                        checked = s.studyAutoSpeak,
                        onCheckedChange = { v -> vm.updateSettings { it.copy(studyAutoSpeak = v) } },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Xong") } },
    )
}

@Composable
private fun ColumnScope.StudyResult(''')

# ---------- StudyCardView: mic + so khớp ----------
rep('''    koFront: Boolean,
    flipped: Boolean,
    onFlip: () -> Unit,
    onAnswer: (Boolean) -> Unit,
) {
    val x = LocalExtra.current
    val v = card.vocab
    val g = card.grammar
    // Mặt đang hiện là tiếng Hàn → tự đọc
    LaunchedEffect(card.key, flipped, koFront) {
        if (koFront != flipped) {''', '''    koFront: Boolean,
    flipped: Boolean,
    matchPercent: Int,
    autoSpeak: Boolean,
    onFlip: () -> Unit,
    onReveal: () -> Unit,
    onAnswer: (Boolean) -> Unit,
) {
    val x = LocalExtra.current
    val v = card.vocab
    val g = card.grammar
    val engineState by vm.engine.state.collectAsStateWithLifecycle()
    var listening by remember(card.key) { mutableStateOf(false) }
    var feedback by remember(card.key) { mutableStateOf<Pair<String, Boolean>?>(null) }
    // Đáp án cần nói: mặt trước tiếng Hàn → nói nghĩa (tiếng Việt); chế độ ngược → nói từ tiếng Hàn
    val expected = when {
        v != null -> if (koFront) v.vi else v.ko
        g != null -> if (koFront) g.meaning else g.pattern
        else -> ""
    }
    val answerLang = if (koFront) com.hankukko.huyfan.assistant.LANG_VI else com.hankukko.huyfan.assistant.LANG_KO
    fun startMic() {
        if (listening) { vm.engine.stopListening(); return }
        listening = true
        feedback = null
        vm.engine.captureOnce(answerLang) { heard ->
            listening = false
            if (heard == null) {
                feedback = "Chưa nghe rõ, bấm mic nói lại nha" to false
            } else {
                val score = com.hankukko.huyfan.util.Similarity.score(heard, expected)
                if (score >= matchPercent) {
                    feedback = "Đúng rồi! “$heard” · $score%" to true
                    onReveal()
                } else {
                    feedback = "Chưa khớp ($score%) · nghe được: “$heard”" to false
                }
            }
        }
    }
    // Mặt đang hiện là tiếng Hàn → tự đọc
    LaunchedEffect(card.key, flipped, koFront) {
        if (autoSpeak && !listening && koFront != flipped) {'''),

rep('''    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { onAnswer(false) }, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(18.dp)) {
            Icon(Icons.Rounded.Close, null, tint = Brand.Red); Spacer(Modifier.width(6.dp)); Text("Chưa nhớ")
        }
        Button(
            onClick = { onAnswer(true) }, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Brand.Green),
        ) {
            Icon(Icons.Rounded.Check, null, tint = Color.White); Spacer(Modifier.width(6.dp)); Text("Nhớ rồi", color = Color.White)
        }
    }
}''', '''    // Kết quả so khớp giọng nói / chữ đang nghe
    val info = when {
        listening -> (engineState.partial.ifBlank { if (koFront) "Nói nghĩa tiếng Việt…" else "Nói từ tiếng Hàn…" }) to null
        feedback != null -> feedback!!.first to feedback!!.second
        else -> null
    }
    Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
        if (info != null) {
            Text(
                info.first, textAlign = TextAlign.Center, maxLines = 2,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                color = when (info.second) {
                    true -> Brand.Green
                    false -> Brand.Red
                    null -> MaterialTheme.colorScheme.primary
                },
            )
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = { onAnswer(false) }, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(18.dp)) {
            Icon(Icons.Rounded.Close, null, tint = Brand.Red); Spacer(Modifier.width(4.dp)); Text("Chưa nhớ", maxLines = 1)
        }
        // Mic giữa: nói đáp án → đúng thì lật thẻ (không tự chấm nhớ/chưa nhớ)
        com.hankukko.huyfan.ui.components.MicButton(listening = listening, onClick = { startMic() }, size = 60.dp)
        Button(
            onClick = { onAnswer(true) }, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Brand.Green),
        ) {
            Icon(Icons.Rounded.Check, null, tint = Color.White); Spacer(Modifier.width(4.dp)); Text("Nhớ rồi", color = Color.White, maxLines = 1)
        }
    }
}''')

# ---------- Màn Ôn tập: nút bánh răng mở Cài đặt bài học ----------
rep('''                Button(onClick = { onEditLesson(LessonEditorRequest()) }) {''',
    '''                IconButton(onClick = { showStudySettings = true }) { Icon(Icons.Rounded.Tune, "Cài đặt bài học") }
                Button(onClick = { onEditLesson(LessonEditorRequest()) }) {''')
rep('''    var confirmDelete by remember { mutableStateOf<LessonSummary?>(null) }

    fun quickCards''', '''    var confirmDelete by remember { mutableStateOf<LessonSummary?>(null) }
    var showStudySettings by remember { mutableStateOf(false) }

    fun quickCards''')
rep('''    confirmDelete?.let { l ->''', '''    if (showStudySettings) StudySettingsDialog(vm, onDismiss = { showStudySettings = false })
    confirmDelete?.let { l ->''')

rep('import androidx.compose.material.icons.rounded.Today\n', 'import androidx.compose.material.icons.rounded.Today\nimport androidx.compose.material.icons.rounded.Tune\n')

open(p, 'w', encoding='utf-8').write(s)
print('ok')
