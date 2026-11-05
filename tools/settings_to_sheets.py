"""Chuyển SettingsScreen: danh sách mục gọn + mỗi mục mở cửa sổ (bottom sheet) để chỉnh."""
import re
import sys

p = sys.argv[1]
s = open(p, encoding='utf-8').read()


def rep(old, new):
    global s
    if old not in s:
        raise SystemExit('NOT FOUND: ' + old[:90])
    s = s.replace(old, new, 1)


# --- 0. Bổ sung vài điều khiển mới trước khi chuyển ---
rep('''                SwitchRow("Ưu tiên nhận diện của Google", "Nghe tiếng Việt chuẩn nhất trên Samsung", s.preferGoogleStt) { v -> vm.updateSettings { it.copy(preferGoogleStt = v) } }''',
    '''                SwitchRow("Ưu tiên nhận diện của Google", "Nghe tiếng Việt chuẩn nhất trên Samsung", s.preferGoogleStt) { v -> vm.updateSettings { it.copy(preferGoogleStt = v) } }
                SliderRow(
                    "Ngắt câu sau khi im lặng", "%.1f giây".format(s.endSilenceMs / 1000f), s.endSilenceMs.toFloat(), 800f..3000f, steps = 10,
                    subtitle = "Nói xong, im lặng chừng này thì HUY FAN tự gửi câu hỏi",
                ) { v -> vm.updateSettings { it.copy(endSilenceMs = v.toInt()) } }''')
rep('''SwitchRow("Tạm dừng khi tắt màn hình", "Tiết kiệm pin hơn, nhưng phải bật màn hình mới gọi được", s.pauseOnScreenOff)''',
    '''SwitchRow("Tạm dừng khi tắt màn hình", "Tiết kiệm pin hơn. TẮT mục này nếu muốn gọi HUY FAN cả khi máy đang khoá/tắt màn hình", s.pauseOnScreenOff)''')

# --- 1. Tìm LazyColumn của SettingsScreen ---
fn = s.index('fun SettingsScreen(')
lc = s.index('    LazyColumn(', fn)
brace = s.index(') {', lc) + 2  # vị trí '{'


def match(i):
    """i trỏ vào '{' → trả vị trí '}' tương ứng (bỏ qua chuỗi)."""
    depth = 0
    j = i
    in_str = False
    while j < len(s):
        c = s[j]
        if in_str:
            if c == '\\':
                j += 2
                continue
            if c == '"':
                in_str = False
        else:
            if c == '"':
                in_str = True
            elif c == '{':
                depth += 1
            elif c == '}':
                depth -= 1
                if depth == 0:
                    return j
        j += 1
    raise SystemExit('unbalanced')


lc_end = match(brace)
body = s[brace + 1:lc_end]

# --- 2. Tách các SettingsSection ---
sections = []  # (title, icon, pre, content)
pos = 0
pre_all = []
while True:
    k = body.find('SettingsSection(', pos)
    if k < 0:
        break
    # đoạn "pre" = từ 'item {' gần nhất tới SettingsSection
    item_start = body.rfind('item {', 0, k)
    pre = body[item_start + len('item {'):k]
    m = re.match(r'SettingsSection\("([^"]+)",\s*([A-Za-z0-9_.]+)\)\s*\{', body[k:])
    if not m:
        raise SystemExit('bad section at ' + body[k:k + 80])
    title, icon = m.group(1), m.group(2)
    ob = k + m.end() - 1
    # match trong body
    depth = 0
    j = ob
    in_str = False
    while True:
        c = body[j]
        if in_str:
            if c == '\\':
                j += 2
                continue
            if c == '"':
                in_str = False
        else:
            if c == '"':
                in_str = True
            elif c == '{':
                depth += 1
            elif c == '}':
                depth -= 1
                if depth == 0:
                    break
        j += 1
    content = body[ob + 1:j]
    sections.append((title, icon, pre.strip('\n'), content))
    pos = j

print('sections:', [t for t, _, _, _ in sections])

summaries = {
    'Quyền & trạng thái': '''run {
                val missing = listOf(mic, notif, overlay, battery, vi && ko).count { !it }
                if (missing == 0) "Đã đủ quyền" else "Còn $missing mục nên bật"
            }''',
    'AI & API (miễn phí)': '''if (!s.hasAiKey) "${s.provider.label} · chưa có API key" else if (s.provider == Provider.GEMINI)
                "Gemini · ${s.geminiModel.ifBlank { s.geminiResolvedModel.ifBlank { "tự chọn model" } }}" else "${s.openaiModel}"''',
    'Prompt & phong cách': '''"${s.style.label} · " + if (s.customPrompt.isBlank()) "prompt mặc định" else "prompt tuỳ chỉnh"''',
    'Giọng nói & HUY FAN': '''"Độ nhạy ${listOf("Ít nhầm", "Cân bằng", "Nhạy")[s.wakeSensitivity.coerceIn(0, 2)]} · ngắt câu ${"%.1f".format(s.endSilenceMs / 1000f)}s"''',
    'Chế độ chờ & pin': '''if (standbyOn) "Đang chờ gọi · " + (if (s.autoOffMinutes == 0) "không tự tắt" else "tự tắt sau ${s.autoOffMinutes} phút") else "Đang tắt"''',
    'Google Sheets (sổ từ online)': '''if (s.sheetsUrl.isBlank()) "Chưa kết nối" else if (s.sheetsAutoSync) "Đã kết nối · tự đồng bộ" else "Đã kết nối · đồng bộ thủ công"''',
    'Phím tắt khởi động nhanh': '''"Phím sườn, ô cài đặt nhanh, widget, tai nghe"''',
    'Giao diện': '''s.themeMode.label''',
    'Dữ liệu': '''"Xuất Excel, xoá lịch sử"''',
}

# --- 3. Sinh mã mới ---
pre_code = '\n'.join(sec[2] for sec in sections if sec[2].strip())
pre_code = '\n'.join(line[4:] if line.startswith('            ') else line for line in pre_code.split('\n'))

rows = []
whens = []
for idx, (title, icon, pre, content) in enumerate(sections):
    summ = summaries.get(title, '""')
    rows.append(f'''        item {{
            CategoryRow("{title}", {icon}, {summ}) {{ openPage = {idx} }}
        }}''')
    whens.append(f'''                    {idx} -> SheetPage("{title}", {icon}) {{{content}}}''')

footer_start = body.rfind('item {')
footer = body[footer_start:]
if 'BuildConfig.VERSION_NAME' not in footer:
    raise SystemExit('footer not found')

new_body = '''
        item {
            Text("Cài đặt", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 4.dp))
            Text("Chạm vào mục để chỉnh", style = MaterialTheme.typography.bodySmall, color = x.muted, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
        }
''' + '\n'.join(rows) + '\n        ' + footer.strip() + '\n    '

new_lazy = s[lc:brace + 1] + new_body + '}'

sheet = '''

    openPage?.let { page ->
        ModalBottomSheet(
            onDismissRequest = { openPage = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                when (page) {
''' + '\n'.join(whens) + '''
                }
            }
        }
    }'''

state_decl = '''    var openPage by remember { mutableStateOf<Int?>(null) }
'''

s = s[:lc] + state_decl + pre_code + '\n\n' + new_lazy + sheet + s[lc_end + 1:]

# --- 4. Thành phần dùng chung ---
s += '''

/** 1 dòng trong trang Cài đặt chính: biểu tượng · tên · tóm tắt giá trị hiện tại. */
@Composable
private fun CategoryRow(title: String, icon: ImageVector, summary: String, onClick: () -> Unit) {
    val x = LocalExtra.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(x.card)
            .border(1.dp, x.cardBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brand.gradient),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = x.muted, maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = x.muted)
    }
}

/** Nội dung 1 cửa sổ chỉnh: tiêu đề + thẻ chứa các điều khiển. */
@Composable
private fun SheetPage(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    val x = LocalExtra.current
    Row(Modifier.padding(start = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brand.gradient),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(x.card)
            .border(1.dp, x.cardBorder, RoundedCornerShape(22.dp))
            .padding(vertical = 6.dp),
        content = content,
    )
}
'''

# --- 5. imports + opt-in ---
imports = '''import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
'''
s = s.replace('import androidx.compose.foundation.layout.Arrangement\n', imports + 'import androidx.compose.foundation.layout.Arrangement\n', 1)
s = s.replace('@Composable\nfun SettingsScreen(', '@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun SettingsScreen(', 1)

open(p, 'w', encoding='utf-8').write(s)
print('ok')
