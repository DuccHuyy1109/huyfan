import io, sys, os

root = sys.argv[1]
p = os.path.join(root, 'ui/screens/ReviewScreen.kt')
s = io.open(p, encoding='utf-8').read()

pairs = [
# --- tham số tự mở phiên ôn đến hạn (từ thông báo) + luyện nói ---
('''fun ReviewScreen(
    vm: MainViewModel,
    onStart: (StudySession) -> Unit,
    onEditLesson: (LessonEditorRequest) -> Unit,
    onVoiceQuiz: () -> Unit,
) {''', '''fun ReviewScreen(
    vm: MainViewModel,
    onStart: (StudySession) -> Unit,
    onEditLesson: (LessonEditorRequest) -> Unit,
    onVoiceQuiz: () -> Unit,
    autoStartDue: Boolean = false,
    onAutoStartConsumed: () -> Unit = {},
    onPractice: () -> Unit = {},
    onRolePlay: () -> Unit = {},
) {'''),
('''    var showStudySettings by remember { mutableStateOf(false) }
    val settings by vm.settings.collectAsStateWithLifecycle()
''', '''    var showStudySettings by remember { mutableStateOf(false) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val due by vm.due.collectAsStateWithLifecycle()

    fun dueSession(d: DueSummary): StudySession = StudySession(
        "Đến hạn hôm nay",
        collapseDuplicates(d.vocab.map { StudyCard(vocab = it) } + d.grammar.map { StudyCard(grammar = it) }, vocab, grammar)
            .sortedBy { it.vocab?.due ?: it.grammar?.due ?: 0L },
    )
    // Bấm "Ôn ngay" trên thông báo nhắc học → mở thẳng phiên ôn
    LaunchedEffect(autoStartDue, due) {
        val d = due ?: return@LaunchedEffect
        if (!autoStartDue) return@LaunchedEffect
        onAutoStartConsumed()
        if (d.count > 0) onStart(dueSession(d))
        else Toast.makeText(context, "Hết thẻ đến hạn rồi, giỏi quá ông ơi!", Toast.LENGTH_SHORT).show()
    }
'''),
('''        // ---------- Ôn nhanh ----------
        item { SectionTitle("Ôn nhanh") }''', '''        // ---------- Đến hạn hôm nay (ôn ngắt quãng) ----------
        item { DueHero(due) { due?.let { if (it.count > 0) onStart(dueSession(it)) } } }

        // ---------- Luyện nói ----------
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PracticeTile(
                    "Luyện phát âm", "Đọc từ, câu mẫu → chấm điểm", Icons.Rounded.GraphicEq, Brand.Teal,
                    Modifier.weight(1f), onPractice,
                )
                PracticeTile(
                    "Hội thoại", "Nhập vai nói tiếng Hàn", Icons.Rounded.Forum, Brand.Violet,
                    Modifier.weight(1f), onRolePlay,
                )
            }
        }

        // ---------- Ôn nhanh ----------
        item { SectionTitle("Ôn nhanh") }'''),
('''@Composable
private fun QuickTile(label: String, count: Int, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {''', '''@Composable
private fun DueHero(due: DueSummary?, onStart: () -> Unit) {
    val count = due?.count ?: 0
    val forecast = due?.forecast ?: IntArray(7)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brand.gradient)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "ĐẾN HẠN HÔM NAY", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$count", color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(6.dp))
                    Text("thẻ", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
                }
                Text(
                    when {
                        due == null -> "Đang tính lịch ôn…"
                        due.total == 0 -> "Hỏi HUY FAN vài từ, mai sẽ có thẻ để ôn"
                        count > 0 -> "Mới ${due.newCount} · Ôn lại ${count - due.newCount} · Đã thuộc ${due.matured}/${due.total}"
                        else -> "Xong hết rồi 🎉 · Ngày mai: ${forecast.getOrElse(1) { 0 }} thẻ"
                    },
                    color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = onStart, enabled = count > 0, shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White, contentColor = Brand.Indigo,
                    disabledContainerColor = Color.White.copy(alpha = 0.22f), disabledContentColor = Color.White.copy(alpha = 0.8f),
                ),
            ) {
                Icon(if (count > 0) Icons.Rounded.PlayArrow else Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (count > 0) "Ôn ngay" else "Xong", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(14.dp))
        ForecastBars(forecast)
    }
}

/** Biểu đồ cột: số thẻ đến hạn 7 ngày tới. */
@Composable
private fun ForecastBars(f: IntArray) {
    val max = (f.maxOrNull() ?: 0).coerceAtLeast(1)
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        f.forEachIndexed { i, n ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (n > 0) "$n" else "", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .fillMaxWidth()
                        .height((6 + 34f * n / max).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = if (i == 0) 0.95f else 0.38f))
                )
                val d = today.plusDays(i.toLong())
                Text(
                    if (i == 0) "Nay" else if (d.dayOfWeek.value == 7) "CN" else "T${d.dayOfWeek.value + 1}",
                    color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun PracticeTile(title: String, subtitle: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val x = LocalExtra.current
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(x.card)
            .border(1.dp, x.cardBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = color, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = x.muted, maxLines = 2)
        }
    }
}

@Composable
private fun QuickTile(label: String, count: Int, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {'''),
# --- Phiên học: thẻ quên được hỏi lại cuối phiên ---
('''    val deck = remember(session, seed) { if (seed == 0) session.cards else session.cards.shuffled() }
    var index by remember(session, seed) { mutableIntStateOf(0) }
    var flipped by remember(session, seed, index) { mutableStateOf(false) }
    var known by remember(session, seed) { mutableIntStateOf(0) }''', '''    val deck = remember(session, seed) {
        androidx.compose.runtime.mutableStateListOf<StudyCard>().apply { addAll(if (seed == 0) session.cards else session.cards.shuffled()) }
    }
    // Thẻ bấm "Quên" → đưa xuống cuối phiên để ôn lại (tối đa 2 lần mỗi thẻ)
    val again = remember(session, seed) { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
    var index by remember(session, seed) { mutableIntStateOf(0) }
    var flipped by remember(session, seed, index) { mutableStateOf(false) }
    var known by remember(session, seed) { mutableIntStateOf(0) }'''),
('''                        Text("${deck.size} thẻ", style = MaterialTheme.typography.labelMedium, color = x.muted)''',
 '''                        Text(
                            "${session.cards.size} thẻ" + if (deck.size > session.cards.size) " · ${deck.size - session.cards.size} lượt ôn lại" else "",
                            style = MaterialTheme.typography.labelMedium, color = x.muted,
                        )'''),
('''                    StudyResult(known, deck.size, onAgain = { seed++ }, onDone = onDismiss)''',
 '''                    StudyResult(known, session.cards.size, onAgain = { seed++ }, onDone = onDismiss)'''),
('''                        onAnswer = { ok ->
                            val card = deck[index]
                            card.vocab?.let { vm.review(it, ok) }
                            card.grammar?.let { vm.reviewGrammar(it, ok) }
                            if (ok) known++
                            index++
                        },''', '''                        onAnswer = { grade ->
                            val card = deck[index]
                            card.vocab?.let { vm.review(it, grade) }
                            card.grammar?.let { vm.reviewGrammar(it, grade) }
                            val times = again[card.key] ?: 0
                            if (grade == Grade.AGAIN) {
                                if (times < 2) {
                                    again[card.key] = times + 1
                                    deck.add(
                                        card.copy(
                                            vocab = card.vocab?.let { it.withSrs(Srs.next(it.srs, Grade.AGAIN)) },
                                            grammar = card.grammar?.let { it.withSrs(Srs.next(it.srs, Grade.AGAIN)) },
                                        )
                                    )
                                }
                            } else if (times == 0) {
                                known++
                            }
                            index++
                        },'''),
# --- 3 nút chấm điểm ---
('''    onAnswer: (Boolean) -> Unit,
) {
    val x = LocalExtra.current
    val v = card.vocab''', '''    onAnswer: (Grade) -> Unit,
) {
    val x = LocalExtra.current
    val v = card.vocab'''),
('''        OutlinedButton(onClick = { onAnswer(false) }, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(18.dp)) {
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
    }''', '''        val srs = v?.srs ?: g?.srs ?: SrsState()
        GradeButton(Grade.AGAIN, Srs.intervalLabel(srs, Grade.AGAIN), Brand.Red, Modifier.weight(1f)) { onAnswer(Grade.AGAIN) }
        // Mic giữa: nói đáp án → đúng thì lật thẻ (không tự chấm điểm)
        com.hankukko.huyfan.ui.components.MicButton(listening = listening, onClick = { startMic() }, size = 58.dp)
        GradeButton(Grade.GOOD, Srs.intervalLabel(srs, Grade.GOOD), Brand.Green, Modifier.weight(1f), filled = true) { onAnswer(Grade.GOOD) }
        GradeButton(Grade.EASY, Srs.intervalLabel(srs, Grade.EASY), Brand.Blue, Modifier.weight(1f)) { onAnswer(Grade.EASY) }
    }'''),
('''@Composable
private fun ColumnScope.StudyResult(''', '''/** Nút chấm điểm: tên + lần ôn tới (vd "Nhớ · 3 ngày"). */
@Composable
private fun GradeButton(grade: Grade, next: String, color: Color, modifier: Modifier, filled: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (filled) color else color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = if (filled) 0f else 0.45f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(grade.label, fontWeight = FontWeight.Bold, color = if (filled) Color.White else color, maxLines = 1)
        Text(next, style = MaterialTheme.typography.labelSmall, color = if (filled) Color.White.copy(alpha = 0.85f) else color.copy(alpha = 0.8f), maxLines = 1)
    }
}

@Composable
private fun ColumnScope.StudyResult('''),
]
for a, b in pairs:
    if a not in s:
        raise SystemExit('NOT FOUND: ' + a[:90])
    s = s.replace(a, b, 1)

imports = '''import com.hankukko.huyfan.data.DueSummary
import com.hankukko.huyfan.data.Grade
import com.hankukko.huyfan.data.Srs
import com.hankukko.huyfan.data.SrsState
import com.hankukko.huyfan.data.due
import com.hankukko.huyfan.data.srs
import com.hankukko.huyfan.data.withSrs
'''
s = s.replace('import com.hankukko.huyfan.util.Dates\n', imports + 'import com.hankukko.huyfan.util.Dates\n', 1)
for ic in ['Schedule', 'GraphicEq', 'Forum', 'Check', 'PlayArrow']:
    line = 'import androidx.compose.material.icons.rounded.%s\n' % ic
    if line not in s:
        s = s.replace('import androidx.compose.material.icons.Icons\n', 'import androidx.compose.material.icons.Icons\n' + line, 1)
io.open(p, 'w', encoding='utf-8').write(s)
print('ok')
