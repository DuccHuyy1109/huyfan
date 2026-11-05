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


patch('ui/screens/InterpreterScreen.kt', [
    ('''    var flipped by remember { mutableStateOf(t.side == Side.VI) }''', '''    var flipped by remember { mutableStateOf(false) }'''),
])

# ---------- Engine: giữ micro cho phiên dịch ----------
patch('assistant/ConversationEngine.kt', [
    ('''    /** Ảnh đã đính kèm, chờ câu hỏi (gõ hoặc nói) để gửi cùng. */''', '''    /** Đang phiên dịch → chế độ chờ không nghe "HUY FAN" chen vào micro. */
    private val _micHold = kotlinx.coroutines.flow.MutableStateFlow(false)
    val micHold: kotlinx.coroutines.flow.StateFlow<Boolean> = _micHold

    fun setMicHold(on: Boolean) {
        _micHold.value = on
        if (on) onMicRequest?.invoke()
    }

    /** Ảnh đã đính kèm, chờ câu hỏi (gõ hoặc nói) để gửi cùng. */'''),
])
patch('service/StandbyService.kt', [
    ('''        scope.launch {
            engine.state.map { it.phase }.distinctUntilChanged().collect { updateBubbleState() }
        }''', '''        scope.launch {
            engine.state.map { it.phase }.distinctUntilChanged().collect { updateBubbleState() }
        }
        scope.launch {
            engine.micHold.collect { hold ->
                if (hold) pauseListening("Đang phiên dịch hội thoại…") else scheduleResume(700)
            }
        }'''),
    ('''        if (conversationBusy) return
        if (screenOff && container.settingsState.value.pauseOnScreenOff) {''', '''        if (conversationBusy) return
        if (container.engine.micHold.value) return
        if (screenOff && container.settingsState.value.pauseOnScreenOff) {'''),
])

# ---------- Chat: nút mở phiên dịch ----------
patch('ui/screens/ChatScreen.kt', [
    ('''    onOpenHistory: () -> Unit,
) {''', '''    onOpenHistory: () -> Unit,
    onOpenInterpreter: () -> Unit = {},
) {'''),
    ('''                com.hankukko.huyfan.ui.components.AttachImageButton(
                    onPicked = { vm.engine.attachImage(it) },
                    modifier = Modifier.padding(start = 4.dp),
                )''', '''                com.hankukko.huyfan.ui.components.AttachImageButton(
                    onPicked = { vm.engine.attachImage(it) },
                    modifier = Modifier.padding(start = 4.dp),
                )
                // Chuyển sang phiên dịch hội thoại song ngữ
                Box(
                    Modifier
                        .padding(start = 4.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Brand.Teal.copy(alpha = 0.16f))
                        .clickable(onClick = onOpenInterpreter),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Translate, "Phiên dịch hội thoại", tint = Brand.Teal, modifier = Modifier.size(22.dp))
                }'''),
])
p = os.path.join(root, 'ui/screens/ChatScreen.kt')
s = io.open(p, encoding='utf-8').read()
for imp in ['import androidx.compose.material.icons.rounded.Translate']:
    if imp + '\n' not in s:
        s = s.replace('\nimport ', '\n' + imp + '\nimport ', 1)
io.open(p, 'w', encoding='utf-8').write(s)

patch('ui/MainActivity.kt', [
    ('''    var practiceOpen by remember { mutableStateOf(false) }''', '''    var practiceOpen by remember { mutableStateOf(false) }
    var interpreterOpen by remember { mutableStateOf(false) }'''),
    ('''                            onOpenHistory = { tab = Tab.HISTORY },''', '''                            onOpenHistory = { tab = Tab.HISTORY },
                            onOpenInterpreter = { vm.engine.cancel(); interpreterOpen = true },'''),
    ('''    if (practiceOpen) com.hankukko.huyfan.ui.screens.PracticeDialog(vm, onDismiss = { practiceOpen = false })''',
     '''    if (practiceOpen) com.hankukko.huyfan.ui.screens.PracticeDialog(vm, onDismiss = { practiceOpen = false })
    if (interpreterOpen) com.hankukko.huyfan.ui.screens.InterpreterDialog(onDismiss = { interpreterOpen = false })'''),
])

# ---------- Cửa sổ bong bóng: nút phiên dịch ----------
A = 'ui/AssistActivity.kt'
patch(A, [
    ('''    var lastTouch by remember { mutableLongStateOf(0L) }''', '''    var lastTouch by remember { mutableLongStateOf(0L) }
    var interpreter by remember { mutableStateOf(false) }'''),
    ('''    LaunchedEffect(state.phase, typing, recent.size, lastTouch) {
        if (state.phase == Phase.IDLE && !typing) {''', '''    LaunchedEffect(state.phase, typing, recent.size, lastTouch, interpreter) {
        if (state.phase == Phase.IDLE && !typing && !interpreter) {'''),
    ('''                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (typing) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { typing = !typing },''', '''                Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(com.hankukko.huyfan.ui.theme.Brand.Teal.copy(alpha = 0.16f))
                        .clickable { engine.cancel(); interpreter = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(androidx.compose.material.icons.Icons.Rounded.Translate, "Phiên dịch hội thoại", tint = com.hankukko.huyfan.ui.theme.Brand.Teal)
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (typing) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { typing = !typing },'''),
    ('''                    Icon(Icons.Rounded.Keyboard, "Gõ phím", tint = if (typing) MaterialTheme.colorScheme.primary else x.muted)
                }''', '''                    Icon(Icons.Rounded.Keyboard, "Gõ phím", tint = if (typing) MaterialTheme.colorScheme.primary else x.muted)
                }
                }'''),
])
p = os.path.join(root, A)
s = io.open(p, encoding='utf-8').read()
# Hiện phiên dịch trong cửa sổ bong bóng: chèn cuối hàm AssistSheet
i = s.index('private fun AssistSheet(')
j = s.find('\n@Composable', i)
if j < 0:
    j = len(s)
k = s.rindex('\n}', i, j)
s = s[:k] + '''
    if (interpreter) com.hankukko.huyfan.ui.screens.InterpreterDialog(onDismiss = { interpreter = false; lastTouch = System.currentTimeMillis() })''' + s[k:]
for imp in ['import androidx.compose.material.icons.rounded.Translate']:
    if imp + '\n' not in s:
        s = s.replace('\nimport ', '\n' + imp + '\nimport ', 1)
io.open(p, 'w', encoding='utf-8').write(s)
print('ok')
