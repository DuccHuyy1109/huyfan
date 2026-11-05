import io, sys, os

root = sys.argv[1]  # .../app/src/main
J = 'java/com/hankukko/huyfan/'
UI = J + 'ui/'
L = 'com.hankukko.huyfan.Lang'


def patch(rel, pairs):
    p = os.path.join(root, rel)
    s = io.open(p, encoding='utf-8').read()
    for a, b in pairs:
        if a not in s:
            raise SystemExit(rel + ' NOT FOUND: ' + a[:90])
        s = s.replace(a, b, 1)
    io.open(p, 'w', encoding='utf-8').write(s)


# ================= Thành phần dùng chung: nút cờ đổi ngôn ngữ + hộp xác nhận =================
p = os.path.join(root, UI + 'components/LangSwitch.kt')
io.open(p, 'w', encoding='utf-8').write('''package com.hankukko.huyfan.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hankukko.huyfan.Lang
import com.hankukko.huyfan.StudyLang
import com.hankukko.huyfan.ui.theme.Brand
import com.hankukko.huyfan.ui.theme.LocalExtra

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Nút cờ ngôn ngữ đang học (🇰🇷 / 🇬🇧). Chạm → hỏi lại rồi chuyển hẳn sang "app" ngôn ngữ kia
 * (sổ từ, lịch sử chat… riêng biệt), mở lại [reopen] với cuộc trò chuyện mới.
 */
@Composable
fun LangSwitchButton(reopen: Class<out Activity>, compact: Boolean = false, onBeforeSwitch: () -> Unit = {}) {
    val ctx = LocalContext.current
    val x = LocalExtra.current
    var ask by remember { mutableStateOf(false) }
    val cur = Lang.current
    Row(
        Modifier
            .semantics { contentDescription = "Đang học ${cur.nameVi} – chạm để đổi ngôn ngữ" }
            .clip(CircleShape)
            .background(x.card)
            .border(1.dp, x.neonBorder, CircleShape)
            .clickable { ask = true }
            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(cur.flag, fontSize = if (compact) 15.sp else 16.sp)
        if (!compact) {
            Spacer(Modifier.width(5.dp))
            Text(cur.id.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
    if (ask) {
        val to = cur.other
        AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text("${to.flag}  Học ${to.nameVi}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Chuyển sang không gian học ${to.nameVi} – như mở một app riêng.")
                    LangPoint("Sổ từ, ngữ pháp, lịch ôn, lịch sử chat tách biệt hoàn toàn")
                    LangPoint("Bắt đầu cuộc trò chuyện mới với HUY FAN")
                    LangPoint("Đổi lại bất cứ lúc nào, dữ liệu ${cur.nameVi} vẫn giữ nguyên")
                }
            },
            confirmButton = {
                Button(onClick = {
                    ask = false
                    onBeforeSwitch()
                    ctx.findActivity()?.let { Lang.switchTo(it, to, reopen) }
                }) { Text("Chuyển sang ${to.title}") }
            },
            dismissButton = { TextButton(onClick = { ask = false }) { Text("Ở lại") } },
        )
    }
}

@Composable
private fun LangPoint(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Brand.gradient))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
    }
}
''')

# ================= Màn Chat =================
C = UI + 'screens/ChatScreen.kt'
patch(C, [
    ('''    Phase.LISTENING -> if (ko) "Đang nghe tiếng Hàn…" else "Đang nghe…"''',
     '''    Phase.LISTENING -> if (ko) "Đang nghe ${''' + L + '''.current.nameVi}…" else "Đang nghe…"'''),
    ('''            BrandTitle(subtitle = "Trợ lý tiếng Hàn", modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))''',
     '''            BrandTitle(subtitle = "Trợ lý ${''' + L + '''.current.nameVi}", modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            com.hankukko.huyfan.ui.components.LangSwitchButton(
                com.hankukko.huyfan.ui.MainActivity::class.java, compact = true,
                onBeforeSwitch = { vm.engine.cancel() },
            )
            Spacer(Modifier.width(6.dp))'''),
    ('''state.partial.ifBlank { if (state.listenLang == LANG_KO) "말씀하세요…" else "Nói đi ông…" }''',
     '''state.partial.ifBlank { if (state.listenLang == LANG_KO) (if (''' + L + '''.isEn) "Speak English…" else "말씀하세요…") else "Nói đi ông…" }'''),
    ('''            if (ko) "한" else "VI",''', '''            if (ko) ''' + L + '''.current.short else "VI",'''),
    ('''if (name.isBlank()) "Hey bro! 안녕!" else "Hey ${name.trim()}! 안녕!"''',
     '''if (name.isBlank()) "Hey bro! ${''' + L + '''.current.greeting}" else "Hey ${name.trim()}! ${''' + L + '''.current.greeting}"'''),
    ('''        val suggestions = listOf(
            "Rửa bát tiếng Hàn là gì?",''',
     '''        val suggestions = if (''' + L + '''.isEn) listOf(
            "Rửa bát tiếng Anh là gì?",
            "Dịch: Hôm nay tôi mệt quá",
            "Giải thích thì hiện tại hoàn thành",
            "Kiểm tra tôi các từ hôm nay",
            "Xem danh sách từ hôm nay",
        ) else listOf(
            "Rửa bát tiếng Hàn là gì?",'''),
])

# ================= Bảng từ vựng (chat) =================
patch(UI + 'components/Components.kt', [
    ('''            HeaderCell("Tiếng Hàn", W_KO)''', '''            HeaderCell(''' + L + '''.current.title, W_KO)'''),
    ('''            HeaderCell("Gốc từ", W_HAN)''', '''            HeaderCell(if (''' + L + '''.isEn) "Cách đọc" else "Gốc từ", W_HAN)'''),
    ('''                        if (it.hanja.isNotBlank()) {
                            Text(it.hanja, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)''',
     '''                        if (''' + L + '''.isEn) {
                            // Tiếng Anh: cách đọc kiểu Việt (IPA đã hiện dưới từ)
                            Text(it.loanRead.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (it.loanRead.isBlank()) x.muted else Brand.Cyan)
                        } else if (it.hanja.isNotBlank()) {
                            Text(it.hanja, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)'''),
])

# ================= Sổ từ =================
V = UI + 'screens/VocabScreen.kt'
patch(V, [
    ('''            BookTab.entries, tab,''',
     '''            // Tiếng Anh không có tab Từ mượn (Hán Hàn / ngoại lai là của tiếng Hàn)
            if (''' + L + '''.isEn) listOf(BookTab.VOCAB, BookTab.GRAMMAR) else BookTab.entries, tab,'''),
    ('''BookTab.VOCAB -> "Tìm tiếng Việt, tiếng Hàn, Hán Việt…"''',
     '''BookTab.VOCAB -> if (''' + L + '''.isEn) "Tìm tiếng Việt, tiếng Anh, cách đọc…" else "Tìm tiếng Việt, tiếng Hàn, Hán Việt…"'''),
    ('''"Hỏi HUY FAN “… tiếng Hàn là gì?” – từ sẽ tự lưu vào đây theo ngày."''',
     '''"Hỏi HUY FAN “… ${''' + L + '''.current.nameVi} là gì?” – từ sẽ tự lưu vào đây theo ngày."'''),
    ('''            if (w.hanja.isBlank() && w.loan.isNotBlank()) Pill(''',
     '''            if (''' + L + '''.isEn && w.loanRead.isNotBlank()) Pill("đọc · ${w.loanRead}", color = Brand.Cyan)
            if (!''' + L + '''.isEn && w.hanja.isBlank() && w.loan.isNotBlank()) Pill('''),
    ('''it.loan.lowercase().contains(q)''', '''it.loan.lowercase().contains(q) || it.loanRead.lowercase().contains(q) || it.ko.lowercase().contains(q)'''),
])

# ================= Ôn tập =================
R = UI + 'screens/ReviewScreen.kt'
patch(R, [
    # Luyện phát âm / Hội thoại: bản tiếng Anh làm ở đợt sau
    ('''        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PracticeTile(''',
     '''        if (!''' + L + '''.isEn) item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PracticeTile('''),
    ('''"Thẻ chỉ hiện nghĩa + dạng → nói/viết ra từ tiếng Hàn."''', '''"Thẻ chỉ hiện nghĩa + dạng → nói/viết ra từ ${''' + L + '''.current.nameVi}."'''),
    ('''"Thẻ hiện từ tiếng Hàn → nói/viết ra nghĩa tiếng Việt."''', '''"Thẻ hiện từ ${''' + L + '''.current.nameVi} → nói/viết ra nghĩa tiếng Việt."'''),
    ('''Text("Tự đọc tiếng Hàn", style''', '''Text("Tự đọc ${''' + L + '''.current.nameVi}", style'''),
    ('''Text("Đọc khi thẻ hiện mặt tiếng Hàn", style''', '''Text("Đọc khi thẻ hiện mặt ${''' + L + '''.current.nameVi}", style'''),
    ('''"Viết từ tiếng Hàn · đúng từ $drawPercent% thì lật thẻ"''', '''"Viết từ ${''' + L + '''.current.nameVi} · đúng từ $drawPercent% thì lật thẻ"'''),
    ('''"Nói từ tiếng Hàn · hoặc chạm để lật"''', '''"Nói từ ${''' + L + '''.current.nameVi} · hoặc chạm để lật"'''),
    ('''"Nói từ tiếng Hàn…"''', '''"Nói từ ${''' + L + '''.current.nameVi}…"'''),
    ('''            if (isHan) "HÁN HÀN" else "TỪ MƯỢN", color = color,''',
     '''            if (isHan) "HÁN HÀN" else if (''' + L + '''.isEn) "CÁCH ĐỌC" else "TỪ MƯỢN", color = color,'''),
    ('''                    if (v.hanja.isNotBlank() || v.loan.isNotBlank()) {''',
     '''                    if (v.hanja.isNotBlank() || v.loan.isNotBlank() || (''' + L + '''.isEn && v.loanRead.isNotBlank())) {'''),
    ('''OriginBadge(v.hanja, v.hanViet, v.loan, v.loanRead)''',
     '''if (''' + L + '''.isEn) OriginBadge("", "", v.loanRead) else OriginBadge(v.hanja, v.hanViet, v.loan, v.loanRead)'''),
])

# ================= Cài đặt =================
patch(UI + 'screens/SettingsScreen.kt', [
    ('''Text("Học tiếng Hàn mỗi ngày – 화이팅!", style''',
     '''Text(if (''' + L + '''.isEn) "Học tiếng Anh mỗi ngày – Let's go!" else "Học tiếng Hàn mỗi ngày – 화이팅!", style'''),
    ('''                    "Giọng đọc Việt & Hàn",''', '''                    "Giọng đọc Việt & ${''' + L + '''.current.nameVi.removePrefix("tiếng ").replaceFirstChar { it.uppercase() }}",'''),
    ('''· Tiếng Hàn: ${if (ko) "có" else "thiếu"}''', '''· ${''' + L + '''.current.title}: ${if (ko) "có" else "thiếu"}'''),
    ('''"Giọng AI tự nhiên, đọc liền Việt + Hàn. Lỗi mạng thì dùng giọng máy."''',
     '''"Giọng AI tự nhiên, đọc liền Việt + ${''' + L + '''.current.nameVi.removePrefix("tiếng ")}. Lỗi mạng thì dùng giọng máy."'''),
    ('''                    "Bộ đọc tiếng Hàn", s.koTtsEngine,''', '''                    "Bộ đọc ${''' + L + '''.current.nameVi}", s.koTtsEngine,'''),
    ('''                    "Giọng tiếng Hàn", s.koVoice,''', '''                    "Giọng ${''' + L + '''.current.nameVi}", s.koVoice,'''),
])

# ================= Thanh tab + mở lại sau khi đổi ngôn ngữ =================
M = UI + 'MainActivity.kt'
patch(M, [
    ('''        Tab.entries.filter { it.inBar }.forEach { t ->''',
     '''        // Tiếng Anh: Phiên dịch & Nguyên tắc làm ở đợt sau → tạm ẩn
        Tab.entries.filter { it.inBar && !(''' + L + '''.isEn && (it == Tab.TRANSLATE || it == Tab.RULES)) }.forEach { t ->'''),
    ('''        if (savedInstanceState == null) {
            if (!hasMic()) requestPermissions()''',
     '''        if (savedInstanceState == null) {
            // Vừa đổi ngôn ngữ học → cuộc trò chuyện mới, bật lại chế độ chờ nếu trước đó đang bật
            if (''' + L + '''.afterSwitch(this, intent)) vm.newChat()
            if (!hasMic()) requestPermissions()'''),
])

# ================= Cửa sổ bong bóng =================
A = UI + 'AssistActivity.kt'
patch(A, [
    ('''        if (savedInstanceState == null) begin(intent)''',
     '''        if (savedInstanceState == null) {
            // Vừa đổi ngôn ngữ học từ bong bóng → mở lại với cuộc trò chuyện mới, chưa bật mic
            if (''' + L + '''.afterSwitch(this, intent)) container.engine.newSession() else begin(intent)
        }'''),
    ('''                // Chip trạng thái tự co lại → nút Chi tiết / Mở app / Đóng luôn đủ chỗ
                Box(Modifier.weight(1f).clipToBounds()) {
                    Pill(phaseLabel(state.phase, state.listenLang == LANG_KO))
                }
                val st by c.settingsState.collectAsStateWithLifecycle()
                com.hankukko.huyfan.ui.components.DetailToggle(
                    st.detailedAnswers, compact = true,
                    onToggle = {
                        lastTouch = System.currentTimeMillis()
                        kotlinx.coroutines.MainScope().launch { c.settings.update { it.copy(detailedAnswers = !it.detailedAnswers) } }
                    },
                )''',
     '''                // Trạng thái gọn: chấm màu + chữ viết tắt (SS = sẵn sàng, ĐN = đang nghe…)
                Box(Modifier.weight(1f).clipToBounds()) {
                    PhaseBadge(state.phase, state.listenLang == LANG_KO)
                }
                val st by c.settingsState.collectAsStateWithLifecycle()
                // Chi tiết: chỉ icon quyển sách (sáng = bật)
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .then(if (st.detailedAnswers) Modifier.background(Brand.gradient) else Modifier.background(Brand.Violet.copy(alpha = 0.12f)))
                        .clickable {
                            lastTouch = System.currentTimeMillis()
                            kotlinx.coroutines.MainScope().launch { c.settings.update { it.copy(detailedAnswers = !it.detailedAnswers) } }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.MenuBook,
                        if (st.detailedAnswers) "Trả lời chi tiết: bật" else "Trả lời chi tiết: tắt",
                        tint = if (st.detailedAnswers) androidx.compose.ui.graphics.Color.White else Brand.Violet,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                // Đổi ngôn ngữ học (🇰🇷 ⇄ 🇬🇧)
                com.hankukko.huyfan.ui.components.LangSwitchButton(AssistActivity::class.java, compact = true, onBeforeSwitch = { engine.cancel() })'''),
    ('''Phase.LISTENING -> if (state.listenLang == LANG_KO) "말씀하세요…" else "Nói đi ông, tui nghe nè…"''',
     '''Phase.LISTENING -> if (state.listenLang == LANG_KO) (if (''' + L + '''.isEn) "Speak English…" else "말씀하세요…") else "Nói đi ông, tui nghe nè…"'''),
    ('''                com.hankukko.huyfan.ui.screens.TranslateModeButton(
                    onConversation = { engine.cancel(); interpreter = true },
                    onAuto = { engine.cancel(); autoInterpreter = true },
                )
                Spacer(Modifier.width(8.dp))''',
     '''                if (!''' + L + '''.isEn) {
                    com.hankukko.huyfan.ui.screens.TranslateModeButton(
                        onConversation = { engine.cancel(); interpreter = true },
                        onAuto = { engine.cancel(); autoInterpreter = true },
                    )
                    Spacer(Modifier.width(8.dp))
                }'''),
])

# PhaseBadge cho cửa sổ bong bóng
p = os.path.join(root, A)
s = io.open(p, encoding='utf-8').read()
s += '''

/** Trạng thái viết tắt + chấm màu: SS sẵn sàng · ĐN đang nghe · XL đang xử lý · TL đang trả lời. */
@Composable
private fun PhaseBadge(phase: Phase, ko: Boolean) {
    val (short, color) = when (phase) {
        Phase.IDLE -> "SS" to Brand.Green
        Phase.GREETING -> "HF" to Brand.Violet
        Phase.LISTENING -> (if (ko) "ĐN·" + com.hankukko.huyfan.Lang.current.id.uppercase() else "ĐN") to Brand.Pink
        Phase.THINKING -> "XL" to Brand.Amber
        Phase.SPEAKING -> "TL" to Brand.Cyan
    }
    Row(
        Modifier
            .semantics { contentDescription = phaseLabel(phase, ko) }
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(short, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
'''
io.open(p, 'w', encoding='utf-8').write(s)
print('ok')
