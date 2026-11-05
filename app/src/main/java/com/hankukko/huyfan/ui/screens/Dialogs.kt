package com.hankukko.huyfan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hankukko.huyfan.ui.neonBackdrop
import com.hankukko.huyfan.R
import com.hankukko.huyfan.ai.PromptBuilder
import com.hankukko.huyfan.ui.theme.LocalExtra

@Composable
private fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        com.hankukko.huyfan.ui.components.DialogImeFix()
        Surface(Modifier.fillMaxSize().neonBackdrop(), color = androidx.compose.ui.graphics.Color.Transparent) {
            content()
        }
    }
}

@Composable
fun PromptEditorDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val x = LocalExtra.current
    var text by remember { mutableStateOf(current.ifBlank { PromptBuilder.DEFAULT_PROMPT }) }
    var showContract by remember { mutableStateOf(false) }
    FullScreenDialog(onDismiss) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Đóng") }
                Text("Prompt gửi cho AI", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = {
                    // Giống mặc định → lưu rỗng để sau này cập nhật app vẫn nhận prompt mới
                    onSave(if (text.trim() == PromptBuilder.DEFAULT_PROMPT.trim()) "" else text)
                    onDismiss()
                }) { Text("Lưu") }
                Spacer(Modifier.width(8.dp))
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    "Biến tự điền: {user_name} = tên của bạn · {style} = phong cách nói chuyện · {date} = ngày hôm nay. " +
                        "App tự thêm danh sách từ đã học và định dạng JSON ở cuối – đừng xoá yêu cầu trả JSON nhé.",
                    style = MaterialTheme.typography.bodySmall, color = x.muted,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    shape = RoundedCornerShape(16.dp),
                )
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { text = PromptBuilder.DEFAULT_PROMPT }) {
                        Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Khôi phục mặc định")
                    }
                }
                TextButton(onClick = { showContract = !showContract }) {
                    Text("Định dạng đầu ra cố định (app tự thêm)")
                    Icon(if (showContract) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                }
                if (showContract) {
                    CodeBlock(PromptBuilder.OUTPUT_CONTRACT)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String, maxHeight: Int = 2000) {
    val x = LocalExtra.current
    Text(
        code,
        Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(x.tableStripe)
            .border(1.dp, x.cardBorder, RoundedCornerShape(14.dp))
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
            .padding(12.dp),
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    )
}

@Composable
fun SheetsGuideDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val x = LocalExtra.current
    val code = remember {
        context.resources.openRawResource(R.raw.apps_script).bufferedReader().use { it.readText() }
    }
    val steps = listOf(
        "Bấm “Tạo Google Sheet” bên dưới (mở sheets.new), đặt tên tuỳ ý, vd “HUY FAN – Từ vựng”.",
        "Trong Sheet: Tiện ích mở rộng → Apps Script. Xoá code mẫu, dán mã đã sao chép → Lưu.",
        "Triển khai → Tùy chọn triển khai mới → loại “Ứng dụng web”. Thực thi: Tôi · Quyền truy cập: Bất kỳ ai → Triển khai.",
        "Cấp quyền cho script (Google cảnh báo thì chọn Nâng cao → Đi tới dự án). Đây là script của chính bạn.",
        "Sao chép “URL ứng dụng web” (đuôi /exec) → dán vào ô Link Apps Script → bấm Gửi thử.",
        "Đã cài bản cũ trước đây? Dán đè mã mới → Lưu → Triển khai → Quản lý triển khai → ✏️ → Phiên bản: Phiên bản mới → Triển khai. Link /exec giữ nguyên.",
    )
    FullScreenDialog(onDismiss) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Đóng") }
                Text("Đồng bộ Google Sheets", style = MaterialTheme.typography.titleLarge)
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    "Mỗi từ ông hỏi sẽ thành 1 dòng trong Sheet (có cột Ngày để lọc theo ngày). Miễn phí, dữ liệu nằm trong Google Drive của ông.",
                    style = MaterialTheme.typography.bodyMedium, color = x.muted,
                )
                Spacer(Modifier.height(14.dp))
                steps.forEachIndexed { i, s ->
                    Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.Top) {
                        Text(
                            "${i + 1}",
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(top = 3.dp),
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(s, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { context.copyToClipboard(code) }) {
                        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sao chép mã")
                    }
                    FilledTonalButton(onClick = { context.openUrl("https://sheets.new") }) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tạo Google Sheet")
                    }
                }
                Spacer(Modifier.height(14.dp))
                CodeBlock(code, maxHeight = 420)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
