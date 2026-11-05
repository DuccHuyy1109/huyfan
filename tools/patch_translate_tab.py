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


M = 'ui/MainActivity.kt'
patch(M, [
    ('''    CHAT("Chat", Icons.Rounded.Forum),''', '''    CHAT("Chat", Icons.Rounded.Forum),
    TRANSLATE("Phiên dịch", Icons.Rounded.Translate),'''),
    ('import androidx.compose.material.icons.rounded.Forum\n', 'import androidx.compose.material.icons.rounded.Forum\nimport androidx.compose.material.icons.rounded.Translate\n'),
    ('''    var interpreterOpen by remember { mutableStateOf(false) }''', '''    var interpreterOpen by remember { mutableStateOf(false) }
    var autoOpen by remember { mutableStateOf(false) }'''),
    ('''                            onOpenInterpreter = { vm.engine.cancel(); interpreterOpen = true },''',
     '''                            onOpenInterpreter = { tab = Tab.TRANSLATE },'''),
    ('''                        Tab.RULES -> RulesScreen(''', '''                        Tab.TRANSLATE -> com.hankukko.huyfan.ui.screens.TranslateScreen(
                            onConversation = { vm.engine.cancel(); interpreterOpen = true },
                            onAuto = { vm.engine.cancel(); autoOpen = true },
                        )
                        Tab.RULES -> RulesScreen('''),
    ('''    if (interpreterOpen) com.hankukko.huyfan.ui.screens.InterpreterDialog(onDismiss = { interpreterOpen = false })''',
     '''    if (interpreterOpen) com.hankukko.huyfan.ui.screens.InterpreterDialog(onDismiss = { interpreterOpen = false })
    if (autoOpen) com.hankukko.huyfan.ui.screens.AutoInterpreterDialog(onDismiss = { autoOpen = false })'''),
])

A = 'ui/AssistActivity.kt'
patch(A, [
    ('''    var interpreter by remember { mutableStateOf(false) }''', '''    var interpreter by remember { mutableStateOf(false) }
    var autoInterpreter by remember { mutableStateOf(false) }'''),
    ('''        if (state.phase == Phase.IDLE && !typing && !interpreter && !com.hankukko.huyfan.ForegroundTracker.pickingImage) {''',
     '''        if (state.phase == Phase.IDLE && !typing && !interpreter && !autoInterpreter && !com.hankukko.huyfan.ForegroundTracker.pickingImage) {'''),
    ('''    LaunchedEffect(state.phase, typing, recent.size, lastTouch, interpreter) {''',
     '''    LaunchedEffect(state.phase, typing, recent.size, lastTouch, interpreter, autoInterpreter) {'''),
    ('''                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(com.hankukko.huyfan.ui.theme.Brand.Teal.copy(alpha = 0.16f))
                        .clickable { engine.cancel(); interpreter = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(androidx.compose.material.icons.Icons.Rounded.Translate, "Phiên dịch hội thoại", tint = com.hankukko.huyfan.ui.theme.Brand.Teal)
                }''', '''                com.hankukko.huyfan.ui.screens.TranslateModeButton(
                    onConversation = { engine.cancel(); interpreter = true },
                    onAuto = { engine.cancel(); autoInterpreter = true },
                )'''),
    ('''    if (interpreter) com.hankukko.huyfan.ui.screens.InterpreterDialog(onDismiss = { interpreter = false; lastTouch = System.currentTimeMillis() })''',
     '''    if (interpreter) com.hankukko.huyfan.ui.screens.InterpreterDialog(onDismiss = { interpreter = false; lastTouch = System.currentTimeMillis() })
    if (autoInterpreter) com.hankukko.huyfan.ui.screens.AutoInterpreterDialog(onDismiss = { autoInterpreter = false; lastTouch = System.currentTimeMillis() })'''),
])
print('ok')
