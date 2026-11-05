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


patch('ui/screens/RulesScreen.kt', [
    ('import androidx.compose.runtime.mutableStateMapOf\n',
     'import androidx.compose.runtime.mutableStateMapOf\nimport androidx.compose.runtime.mutableStateOf\n'),
])

# ---------- Tab: Nguyên tắc thay chỗ Lịch sử trên thanh dưới ----------
patch('ui/MainActivity.kt', [
    ('''enum class Tab(val label: String, val icon: ImageVector) {
    CHAT("Chat", Icons.Rounded.Forum),
    VOCAB("Sổ từ", Icons.AutoMirrored.Rounded.MenuBook),
    REVIEW("Ôn tập", Icons.Rounded.Style),
    HISTORY("Lịch sử", Icons.Rounded.History),
    SETTINGS("Cài đặt", Icons.Rounded.Settings),
}''', '''enum class Tab(val label: String, val icon: ImageVector, val inBar: Boolean = true) {
    CHAT("Chat", Icons.Rounded.Forum),
    VOCAB("Sổ từ", Icons.AutoMirrored.Rounded.MenuBook),
    REVIEW("Ôn tập", Icons.Rounded.Style),
    RULES("Nguyên tắc", Icons.Rounded.AutoStories),
    SETTINGS("Cài đặt", Icons.Rounded.Settings),
    // Mở từ nút đồng hồ ở màn Chat
    HISTORY("Lịch sử", Icons.Rounded.History, inBar = false),
}'''),
    ('import androidx.compose.material.icons.rounded.Forum\n',
     'import androidx.compose.material.icons.rounded.AutoStories\nimport androidx.compose.material.icons.rounded.Forum\n'),
    ('''                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,''', '''                    Tab.entries.filter { it.inBar }.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t || (t == Tab.CHAT && tab == Tab.HISTORY),'''),
    ('''                                    fontWeight = if (tab == t) FontWeight.Bold else FontWeight.Normal,''',
     '''                                    fontWeight = if (tab == t) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.labelSmall,'''),
    ('''                        Tab.CHAT -> ChatScreen(vm, onMic = onMic, onToggleStandby = onToggleStandby, onOpenSettings = { tab = Tab.SETTINGS })''',
     '''                        Tab.CHAT -> ChatScreen(
                            vm, onMic = onMic, onToggleStandby = onToggleStandby,
                            onOpenSettings = { tab = Tab.SETTINGS },
                            onOpenHistory = { tab = Tab.HISTORY },
                        )'''),
    ('''                        Tab.HISTORY -> HistoryScreen(vm, onOpen = { id -> vm.openSession(id); tab = Tab.CHAT })''',
     '''                        Tab.HISTORY -> HistoryScreen(vm, onOpen = { id -> vm.openSession(id); tab = Tab.CHAT }, onBack = { tab = Tab.CHAT })
                        Tab.RULES -> RulesScreen(vm, onAsk = { q -> tab = Tab.CHAT; vm.send(q) })'''),
    ('import com.hankukko.huyfan.ui.screens.ReviewScreen\n',
     'import com.hankukko.huyfan.ui.screens.ReviewScreen\nimport com.hankukko.huyfan.ui.screens.RulesScreen\n'),
])

# ---------- Chat: nút Lịch sử ở thanh trên ----------
patch('ui/screens/ChatScreen.kt', [
    ('''    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current''', '''    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val context = LocalContext.current'''),
    ('''            StandbyChip(on = standby, onClick = onToggleStandby)
            IconButton(onClick = { vm.newChat() }) {''', '''            StandbyChip(on = standby, onClick = onToggleStandby)
            IconButton(onClick = onOpenHistory) {
                Icon(Icons.Rounded.History, "Lịch sử trò chuyện")
            }
            IconButton(onClick = { vm.newChat() }) {'''),
])
p = os.path.join(root, 'ui/screens/ChatScreen.kt')
s = open(p, encoding='utf-8').read()
if 'import androidx.compose.material.icons.rounded.History\n' not in s:
    s = s.replace('import androidx.compose.material.icons.Icons\n',
                  'import androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.rounded.History\n', 1)
    open(p, 'w', encoding='utf-8').write(s)

# ---------- Lịch sử: nút quay lại Chat ----------
patch('ui/screens/HistoryScreen.kt', [
    ('''fun HistoryScreen(vm: MainViewModel, onOpen: (Long) -> Unit) {''',
     '''fun HistoryScreen(vm: MainViewModel, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)'''),
    ('''        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {''', '''        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack, "Về Chat")
            }
            Column(Modifier.weight(1f)) {'''),
])
p = os.path.join(root, 'ui/screens/HistoryScreen.kt')
s = open(p, encoding='utf-8').read()
s = s.replace('import androidx.compose.material.icons.Icons\n',
              'import androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.automirrored.rounded.ArrowBack\n', 1)
open(p, 'w', encoding='utf-8').write(s)
print('ok')
