import re
import sys

p = sys.argv[1]
s = open(p, encoding='utf-8').read()


def rep(old, new, count=1):
    global s
    if old not in s:
        raise SystemExit('NOT FOUND: ' + old[:100])
    s = s.replace(old, new, count)


# ---------- Kiểu dữ liệu ----------
rep('''data class StudySession(val title: String, val cards: List<StudyCard>, val lessonId: Long? = null)''',
    '''data class StudySession(
    val title: String,
    val cards: List<StudyCard>,
    val lessonId: Long? = null,
    /** null = dùng cài đặt ôn nhanh mặc định */
    val config: StudyConfig? = null,
)''')
rep('''    val grammarIds: Set<Long> = emptySet(),
)''', '''    val grammarIds: Set<Long> = emptySet(),
    val config: StudyConfig = StudyConfig(),
)''')

# ---------- Màn Ôn tập: mở bài ôn kèm cài đặt riêng ----------
rep('''    var showStudySettings by remember { mutableStateOf(false) }''', '''    var showStudySettings by remember { mutableStateOf(false) }
    val settings by vm.settings.collectAsStateWithLifecycle()''')
rep('''                                    lessonId = l.id,
                                )''', '''                                    lessonId = l.id,
                                    config = content.lesson.config(settings.studyAutoSpeak),
                                )''')
rep('''                            onEditLesson(LessonEditorRequest(l.id, l.name, it.vocab.map { v -> v.id }.toSet(), it.grammar.map { g -> g.id }.toSet()))''',
    '''                            onEditLesson(
                                LessonEditorRequest(
                                    l.id, l.name, it.vocab.map { v -> v.id }.toSet(), it.grammar.map { g -> g.id }.toSet(),
                                    config = it.lesson.config(settings.studyAutoSpeak),
                                )
                            )''')
rep('''Button(onClick = { onEditLesson(LessonEditorRequest()) }) {''',
    '''Button(onClick = { onEditLesson(LessonEditorRequest(config = settings.studyConfig)) }) {''')
rep('''                        .clickable { onEditLesson(LessonEditorRequest()) }''',
    '''                        .clickable { onEditLesson(LessonEditorRequest(config = settings.studyConfig)) }''')
rep('''    if (showStudySettings) StudySettingsDialog(vm, onDismiss = { showStudySettings = false })''',
    '''    if (showStudySettings) {
        StudyConfigDialog(
            "Cài đặt ôn nhanh", settings.studyConfig,
            onChange = { vm.saveStudyConfig(null, it) },
            onDismiss = { showStudySettings = false },
        )
    }''')

# ---------- Cửa sổ học: dùng cài đặt của phiên ----------
rep('''    val settings by vm.settings.collectAsStateWithLifecycle()
    // true: mặt trước là tiếng Hàn / đuôi câu; false (chế độ ngược): nhìn nghĩa → nói từ
    var front by remember { mutableStateOf(!settings.studyReverse) }
    LaunchedEffect(settings.studyReverse) { front = !settings.studyReverse }''',
    '''    val settings by vm.settings.collectAsStateWithLifecycle()
    // Cài đặt của bài ôn (hoặc mặc định cho ôn nhanh)
    var config by remember(session) { mutableStateOf(session.config ?: settings.studyConfig) }
    // true: mặt trước là tiếng Hàn / đuôi câu; false (chế độ ngược): nhìn nghĩa → nói/viết từ
    var front by remember { mutableStateOf(!config.reverse) }
    LaunchedEffect(config.reverse) { front = !config.reverse }''')
rep('''                        matchPercent = settings.studyMatchPercent,
                        autoSpeak = settings.studyAutoSpeak,''', '''                        matchPercent = config.matchPercent,
                        drawPercent = config.drawPercent,
                        autoSpeak = config.autoSpeak,''')
rep('''    if (showSettings) StudySettingsDialog(vm, onDismiss = { showSettings = false })''',
    '''    if (showSettings) {
        StudyConfigDialog(
            if (session.lessonId != null) "Cài đặt bài: ${session.title}" else "Cài đặt ôn nhanh",
            config,
            onChange = { c -> config = c; vm.saveStudyConfig(session.lessonId, c) },
            onDismiss = { showSettings = false },
        )
    }''')

# ---------- Thay hộp cài đặt cũ bằng khối chỉnh dùng chung ----------
start = s.index('/** Cài đặt bài học: % nói đúng để lật thẻ, chế độ ngược, tự đọc. */')
end = s.index('@Composable\nprivate fun ColumnScope.StudyResult(')
s = s[:start] + '''/** Khối chỉnh cài đặt học: chế độ, % nói đúng, % viết đúng, tự đọc. Dùng trong hộp thoại và cửa sổ tạo bài ôn. */
@Composable
fun StudyConfigEditor(config: StudyConfig, onChange: (StudyConfig) -> Unit) {
    val x = LocalExtra.current
    var match by remember(config.matchPercent) { mutableStateOf(config.matchPercent.toFloat()) }
    var draw by remember(config.drawPercent) { mutableStateOf(config.drawPercent.toFloat()) }
    Column {
        Text("Chế độ học", style = MaterialTheme.typography.labelLarge, color = x.muted)
        Segmented(
            listOf(false, true), config.reverse,
            { if (it) "Ngược: nhìn nghĩa" else "Thường: nhìn từ" },
            { r -> onChange(config.copy(reverse = r)) },
            modifier = Modifier.padding(horizontal = 0.dp),
        )
        Text(
            if (config.reverse) "Thẻ chỉ hiện nghĩa + dạng → nói/viết ra từ tiếng Hàn."
            else "Thẻ hiện từ tiếng Hàn → nói/viết ra nghĩa tiếng Việt.",
            style = MaterialTheme.typography.bodySmall, color = x.muted,
        )
        Spacer(Modifier.height(10.dp))
        Text("🎤 Nói đúng từ ${match.toInt()}% thì lật thẻ", style = MaterialTheme.typography.bodyLarge)
        androidx.compose.material3.Slider(
            value = match, onValueChange = { match = it },
            onValueChangeFinished = { onChange(config.copy(matchPercent = match.toInt())) },
            valueRange = 40f..100f, steps = 11,
        )
        Text("✍️ Viết tay đúng từ ${draw.toInt()}% thì lật thẻ", style = MaterialTheme.typography.bodyLarge)
        androidx.compose.material3.Slider(
            value = draw, onValueChange = { draw = it },
            onValueChangeFinished = { onChange(config.copy(drawPercent = draw.toInt())) },
            valueRange = 40f..100f, steps = 11,
        )
        Text(
            "Mic và bút chỉ lật thẻ, không tự chấm nhớ/chưa nhớ.",
            style = MaterialTheme.typography.bodySmall, color = x.muted,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Tự đọc tiếng Hàn", style = MaterialTheme.typography.bodyLarge)
                Text("Đọc khi thẻ hiện mặt tiếng Hàn", style = MaterialTheme.typography.bodySmall, color = x.muted)
            }
            androidx.compose.material3.Switch(checked = config.autoSpeak, onCheckedChange = { onChange(config.copy(autoSpeak = it)) })
        }
    }
}

@Composable
fun StudyConfigDialog(title: String, config: StudyConfig, onChange: (StudyConfig) -> Unit, onDismiss: () -> Unit) {
    var local by remember(config) { mutableStateOf(config) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2) },
        text = { StudyConfigEditor(local) { local = it; onChange(it) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Xong") } },
    )
}

''' + s[end:]

# ---------- Cửa sổ tạo/sửa bài ôn: thêm phần Cài đặt bài học ----------
rep('''    var selV by remember { mutableStateOf(request.vocabIds) }''', '''    var selV by remember { mutableStateOf(request.vocabIds) }
    var config by remember { mutableStateOf(request.config) }
    var showConfig by remember { mutableStateOf(false) }''')
rep('''                Segmented(BookTab.entries, tab, { t -> if (t == BookTab.VOCAB) "Từ vựng (${selV.size})" else "Ngữ pháp (${selG.size})" }, { tab = it })''',
    '''                // Cài đặt riêng của bài ôn: chế độ ngược, % nói đúng, % viết đúng
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(x.card)
                        .border(1.dp, x.cardBorder, RoundedCornerShape(14.dp))
                        .clickable { showConfig = !showConfig }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Cài đặt bài học", fontWeight = FontWeight.SemiBold)
                        Text(
                            (if (config.reverse) "Chế độ ngược" else "Chế độ thường") +
                                " · nói ${config.matchPercent}% · viết ${config.drawPercent}%",
                            style = MaterialTheme.typography.bodySmall, color = x.muted,
                        )
                    }
                    Icon(if (showConfig) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                }
                if (showConfig) {
                    Box(Modifier.padding(horizontal = 16.dp)) { StudyConfigEditor(config) { config = it } }
                }
                Segmented(BookTab.entries, tab, { t -> if (t == BookTab.VOCAB) "Từ vựng (${selV.size})" else "Ngữ pháp (${selG.size})" }, { tab = it })''')
rep('''                                vm.saveLesson(request.lessonId, name, selV, selG)''',
    '''                                vm.saveLesson(request.lessonId, name, selV, selG, config)''')

rep('import com.hankukko.huyfan.data.LessonSummary\n', 'import com.hankukko.huyfan.data.LessonSummary\nimport com.hankukko.huyfan.data.StudyConfig\n')
rep('import androidx.compose.material.icons.rounded.EmojiEvents\n',
    'import androidx.compose.material.icons.rounded.EmojiEvents\nimport androidx.compose.material.icons.rounded.ExpandLess\nimport androidx.compose.material.icons.rounded.ExpandMore\n')

open(p, 'w', encoding='utf-8').write(s)
print('ok')
