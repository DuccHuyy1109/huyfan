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


# ---------- Ghi âm: báo đoạn đang nói dở (để hiện chữ ngay) ----------
patch('voice/VadRecorder.kt', [
    ('''    private val onSegment: (pcm: ByteArray, ms: Long) -> Unit,
    private val onLevel: (Float, Boolean) -> Unit = { _, _ -> },
) {''', '''    private val onSegment: (pcm: ByteArray, ms: Long) -> Unit,
    private val onLevel: (Float, Boolean) -> Unit = { _, _ -> },
    /** Đang nói dở: gửi phần âm thanh đã có (mỗi ~1,5 s) để hiện chữ tạm thời. [seq] = số thứ tự câu. */
    private val onPartial: ((seq: Int, pcm: ByteArray) -> Unit)? = null,
) {
    /** Số thứ tự câu hiện tại (tăng mỗi khi 1 câu kết thúc). */
    @Volatile
    var seq = 0
        private set'''),
    ('''        private const val MAX_SEGMENT_MS = 12_000''', '''        private const val MAX_SEGMENT_MS = 12_000
        private const val PARTIAL_FIRST_MS = 1_200
        private const val PARTIAL_EVERY_MS = 1_500'''),
    ('''            var speechMs = 0''', '''            var speechMs = 0
            var nextPartial = PARTIAL_FIRST_MS'''),
    ('''                seg.reset(); speaking = false; silentMs = 0; segMs = 0; speechMs = 0; loud = 0''',
     '''                seg.reset(); speaking = false; silentMs = 0; segMs = 0; speechMs = 0; loud = 0
                nextPartial = PARTIAL_FIRST_MS
                seq++'''),
    ('''                        append(copy); segMs += FRAME_MS
                        if (db > floor + 7) { silentMs = 0; speechMs += FRAME_MS } else silentMs += FRAME_MS''',
     '''                        append(copy); segMs += FRAME_MS
                        if (db > floor + 7) { silentMs = 0; speechMs += FRAME_MS } else silentMs += FRAME_MS
                        if (onPartial != null && segMs >= nextPartial && silentMs < END_SILENCE_MS) {
                            nextPartial = segMs + PARTIAL_EVERY_MS
                            onPartial.invoke(seq, seg.toByteArray())
                        }'''),
])

# ---------- AI: nhận dạng nhanh (chỉ Whisper/Groq) cho chữ tạm ----------
patch('ai/AiService.kt', [
    ('''    suspend fun transcribeAuto(wav: ByteArray): Heard {
        val s = settings.get()
        val groq = s.openaiKey.isNotBlank() && s.openaiBaseUrl.contains("groq.com")''', '''    /** Có nhận dạng nhanh (Whisper trên Groq) để hiện chữ ngay khi đang nói không. */
    suspend fun hasFastTranscribe(): Boolean {
        val s = settings.get()
        return s.openaiKey.isNotBlank() && s.openaiBaseUrl.contains("groq.com")
    }

    /** [quick] = chữ tạm khi đang nói: chỉ dùng Whisper (nhanh), lỗi thì thôi, không gọi Gemini. */
    suspend fun transcribeAuto(wav: ByteArray, quick: Boolean = false): Heard {
        val s = settings.get()
        val groq = s.openaiKey.isNotBlank() && s.openaiBaseUrl.contains("groq.com")
        if (quick && !groq) return Heard("", "", "none")'''),
    ('''                return Heard(lang, text, "whisper")
            }
        }''', '''                return Heard(lang, text, "whisper")
            }
            if (quick) return Heard("", "", "whisper")
        }'''),
])

# ---------- Màn dịch tự động: chữ tạm khi đang nói + tự cuộn ----------
A = 'ui/screens/AutoInterpreterScreen.kt'
patch(A, [
    ('''    enum class Stage { HEARING, TRANSLATING, DONE }''', '''    /** LIVE = đang nói dở (chữ tạm, tự cập nhật) · HEARING = vừa nói xong, đang nhận dạng chính xác */
    enum class Stage { LIVE, HEARING, TRANSLATING, DONE }'''),
    ('''    val listState = rememberLazyListState()''', '''    val listState = rememberLazyListState()
    // Câu đang nói dở: chữ hiện ngay, tự sửa khi nghe thêm
    var live by remember { mutableStateOf<AutoLine?>(null) }
    var liveBusy by remember { mutableStateOf(false) }
    var fastTranscribe by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { fastTranscribe = c.ai.hasFastTranscribe() }'''),
    # Câu chốt: nhận luôn chữ tạm làm chỗ chờ để không bị nhấp nháy
    ('''    fun handle(pcm: ByteArray) {
        val id = System.nanoTime()
        scope.launch {
            lines += AutoLine(id)''', '''    fun handle(pcm: ByteArray) {
        val id = System.nanoTime()
        val seed = live
        live = null
        scope.launch {
            lines += AutoLine(id, side = seed?.side, heard = seed?.heard.orEmpty())'''),
    ('''    val recorder = remember {
        VadRecorder(
            scope,
            onSegment = { pcm, _ -> scope.launch { handle(pcm) } },
            onLevel = { l, s -> level = l; speaking = s },
        )
    }''', '''    lateinit var recorder: VadRecorder

    /** Chữ tạm khi đang nói: tối đa 1 yêu cầu cùng lúc (tiết kiệm hạn mức Groq). */
    fun partial(seq: Int, pcm: ByteArray) {
        if (!fastTranscribe || liveBusy) return
        liveBusy = true
        scope.launch {
            val heard = runCatching { c.ai.transcribeAuto(VadRecorder.wav(pcm), quick = true) }.getOrNull()
            liveBusy = false
            // Câu đã chốt trong lúc chờ → bỏ kết quả cũ
            if (heard == null || heard.text.isBlank() || recorder.seq != seq) return@launch
            live = AutoLine(-1L - seq, side = if (heard.lang == "ko") Side.KO else Side.VI, heard = heard.text, stage = AutoLine.Stage.LIVE)
        }
    }

    recorder = remember {
        VadRecorder(
            scope,
            onSegment = { pcm, _ -> scope.launch { handle(pcm) } },
            onLevel = { l, s -> level = l; speaking = s },
            onPartial = { seq, pcm -> scope.launch { partial(seq, pcm) } },
        )
    }'''),
    # Tự cuộn xuống cuối khi nội dung đổi (nếu đang ở cuối)
    ('''    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex) }''',
     '''    // Theo dõi câu mới nhất: đang ở cuối thì tự trôi xuống; đang kéo lên đọc thì hiện nút "↓ Câu mới"
    var follow by remember { mutableStateOf(true) }
    val atBottom by remember { androidx.compose.runtime.derivedStateOf { !listState.canScrollForward } }
    LaunchedEffect(listState.isScrollInProgress, atBottom) {
        if (listState.isScrollInProgress) follow = atBottom
        else if (atBottom) follow = true
    }
    val contentKey = lines.sumOf { it.translation.length + it.heard.length + it.stage.ordinal } + lines.size * 1000 + (live?.heard?.length ?: -1)
    LaunchedEffect(contentKey) {
        if (follow && (lines.isNotEmpty() || live != null)) {
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) listState.animateScrollToItem(total - 1, scrollOffset = 100_000)
        }
    }'''),
    ('''                    items(blocksOf(lines), key = { it.key }) { b ->''', '''                    items(blocksOf(lines + listOfNotNull(live)), key = { it.key }) { b ->'''),
])

# Khung danh sách: thêm nút "↓ Câu mới" nổi
p = os.path.join(root, A)
s = io.open(p, encoding='utf-8').read()
a = '''                // ---------- Các câu đã dịch ----------
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),'''
b = '''                // ---------- Các câu đã dịch ----------
                Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),'''
if a not in s:
    raise SystemExit('list start not found')
s = s.replace(a, b, 1)
a2 = '''                }

                // ---------- Trạng thái nghe ----------'''
b2 = '''                }
                if (!follow && (lines.isNotEmpty() || live != null)) {
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable {
                                follow = true
                                scope.launch {
                                    val total = listState.layoutInfo.totalItemsCount
                                    if (total > 0) listState.animateScrollToItem(total - 1, scrollOffset = 100_000)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("↓  Câu mới", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                }
                }

                // ---------- Trạng thái nghe ----------'''
if a2 not in s:
    raise SystemExit('list end not found')
s = s.replace(a2, b2, 1)

# Hiển thị câu LIVE: chữ tạm, nghiêng, có con trỏ nhấp nháy
a3 = '''            l.stage == AutoLine.Stage.HEARING -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = color)
                Spacer(Modifier.width(8.dp))
                Text("…", color = x.muted, style = MaterialTheme.typography.titleLarge)
            }'''
b3 = '''            l.stage == AutoLine.Stage.LIVE -> {
                val blink by androidx.compose.animation.core.rememberInfiniteTransition(label = "caret").animateFloat(
                    0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(500), androidx.compose.animation.core.RepeatMode.Reverse), label = "c",
                )
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        append(l.heard)
                        withStyle(androidx.compose.ui.text.SpanStyle(color = color.copy(alpha = blink))) { append(" ▍") }
                    },
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.titleMedium,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                )
                Text("đang nghe…", color = color, style = MaterialTheme.typography.labelSmall)
            }
            l.stage == AutoLine.Stage.HEARING && l.heard.isNotBlank() -> {
                // Vừa nói xong: giữ chữ tạm, đang nghe lại chính xác + sửa câu
                Text(sentence(l.heard), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp, color = color)
                    Spacer(Modifier.width(6.dp))
                    Text("đang sửa câu…", color = color, style = MaterialTheme.typography.labelMedium)
                }
            }
            l.stage == AutoLine.Stage.HEARING -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = color)
                Spacer(Modifier.width(8.dp))
                Text("…", color = x.muted, style = MaterialTheme.typography.titleLarge)
            }'''
if a3 not in s:
    raise SystemExit('hearing row not found')
s = s.replace(a3, b3, 1)
s = s.replace('import androidx.compose.ui.text.withStyle\n', '')
if 'import androidx.compose.ui.text.withStyle\n' not in s:
    s = s.replace('\nimport ', '\nimport androidx.compose.ui.text.withStyle\nimport ', 1)
io.open(p, 'w', encoding='utf-8').write(s)
print('ok')
