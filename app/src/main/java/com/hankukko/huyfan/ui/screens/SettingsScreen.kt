package com.hankukko.huyfan.ui.screens

import android.Manifest
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hankukko.huyfan.BuildConfig
import com.hankukko.huyfan.R
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.ui.text.font.FontWeight
import com.hankukko.huyfan.ai.AiException
import com.hankukko.huyfan.ai.AiService
import com.hankukko.huyfan.ai.PromptBuilder
import com.hankukko.huyfan.data.DEFAULT_GEMINI_BASE
import com.hankukko.huyfan.data.DEFAULT_OPENAI_BASE
import com.hankukko.huyfan.data.Provider
import com.hankukko.huyfan.data.TalkStyle
import com.hankukko.huyfan.data.ThemeMode
import com.hankukko.huyfan.data.TtsMode
import com.hankukko.huyfan.voice.AI_VOICES
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.RadioButton
import com.hankukko.huyfan.service.HuyFanWidget
import com.hankukko.huyfan.service.StandbyService
import com.hankukko.huyfan.service.StandbyTileService
import com.hankukko.huyfan.service.AskTileService
import com.hankukko.huyfan.ui.AssistActivity
import com.hankukko.huyfan.ui.MainViewModel
import com.hankukko.huyfan.ui.components.Pill
import com.hankukko.huyfan.ui.theme.Brand
import com.hankukko.huyfan.ui.theme.LocalExtra

private val AUTO_OFF_STEPS = listOf(0, 5, 10, 15, 30, 45, 60, 120)

private data class Preset(val name: String, val base: String, val model: String, val keyUrl: String)

private val PRESETS = listOf(
    Preset("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "https://console.groq.com/keys"),
    Preset("OpenRouter", "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct:free", "https://openrouter.ai/settings/keys"),
    Preset("Gemini (OpenAI)", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-flash-latest", "https://aistudio.google.com/apikey"),
    Preset("Ollama (LAN)", "http://192.168.1.10:11434/v1", "qwen2.5:7b", ""),
)

fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, url, Toast.LENGTH_LONG).show()
    }
}

private fun Context.tryStart(vararg intents: Intent) {
    for (i in intents) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
        } catch (_: Exception) {
        }
    }
    Toast.makeText(this, "Không mở được trang cài đặt này trên máy", Toast.LENGTH_SHORT).show()
}

private fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onRequestPermissions: () -> Unit,
    onEditPrompt: () -> Unit,
    onSheetsGuide: () -> Unit,
) {
    val context = LocalContext.current
    val x = LocalExtra.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val standbyOn by StandbyService.running.collectAsStateWithLifecycle()
    val standbyStatus by StandbyService.status.collectAsStateWithLifecycle()
    val sheetsStatus by vm.sheetsStatus.collectAsStateWithLifecycle()
    val ttsReady by vm.ttsReady.collectAsStateWithLifecycle()

    // Làm mới trạng thái quyền mỗi khi quay lại app
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    var aiResult by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var aiBusy by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var oaModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var sheetsResult by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var benchBusy by remember { mutableStateOf(false) }
    var bench by remember { mutableStateOf<List<AiService.Bench>>(emptyList()) }

    var openPage by remember { mutableStateOf<Int?>(null) }
        val mic = remember(tick) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        }
        val notif = remember(tick) {
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
        val overlay = remember(tick) { Settings.canDrawOverlays(context) }
        val battery = remember(tick) {
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        }
        val (vi, ko) = remember(tick, ttsReady) { vm.ttsLanguages() }
        

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 32.dp),
    ) {
        item {
            Text("Cài đặt", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 4.dp))
            Spacer(Modifier.height(12.dp))
        }
        item {
            CategoryRow("Quyền & trạng thái", Icons.Rounded.Security, run {
                // Bộ đọc chưa khởi động xong thì chưa tính là thiếu giọng
                val missing = listOf(mic, notif, overlay, battery, vi && ko || !ttsReady).count { !it }
                if (missing == 0) "Đã đủ quyền" else "Còn $missing mục nên bật"
            }) { openPage = 0 }
        }
        item {
            CategoryRow("AI & API (miễn phí)", Icons.Rounded.Psychology, if (!s.hasAiKey) "${s.provider.label} · chưa có API key" else if (s.provider == Provider.GEMINI)
                "Gemini · ${s.geminiModel.ifBlank { s.geminiResolvedModel.ifBlank { "tự chọn model" } }}" else "${s.openaiModel}") { openPage = 1 }
        }
        item {
            CategoryRow("Prompt & phong cách", Icons.Rounded.AutoAwesome, "${s.style.label} · " + if (s.customPrompt.isBlank()) "prompt mặc định" else "prompt tuỳ chỉnh") { openPage = 2 }
        }
        item {
            CategoryRow("Giọng nói & HUY FAN", Icons.Rounded.GraphicEq, "Độ nhạy ${listOf("Ít nhầm", "Cân bằng", "Nhạy")[s.wakeSensitivity.coerceIn(0, 2)]} · ngắt câu ${"%.1f".format(s.endSilenceMs / 1000f)}s") { openPage = 3 }
        }
        item {
            CategoryRow("Chế độ chờ & pin", Icons.Rounded.BatteryChargingFull, if (standbyOn) (if (s.wakeListenEnabled) "Đang chờ gọi" else "Chỉ bong bóng") + (if (s.bubbleEnabled) " · bong bóng" else "") + " · " + (if (s.autoOffMinutes == 0) "không tự tắt" else "tự tắt sau ${s.autoOffMinutes} phút") else "Đang tắt" + if (s.bubbleEnabled) " · bong bóng hiện khi bật chờ" else "") { openPage = 4 }
        }
        item {
            CategoryRow("Google Sheets (sổ từ online)", Icons.Rounded.CloudUpload, if (s.sheetsUrl.isBlank()) "Chưa kết nối" else if (com.hankukko.huyfan.data.SheetsSync.urlProblem(s.sheetsUrl.trim()) != null) "Link chưa đúng – cần link Apps Script /exec" else if (s.sheetsAutoSync) "Đã kết nối · tự đồng bộ" else "Đã kết nối · đồng bộ thủ công") { openPage = 5 }
        }
        item {
            CategoryRow("Phím tắt khởi động nhanh", Icons.Rounded.TouchApp, "Phím sườn, ô cài đặt nhanh, widget, tai nghe") { openPage = 6 }
        }
        item {
            CategoryRow("Giao diện", Icons.Rounded.Palette, s.themeMode.label) { openPage = 7 }
        }
        item {
            CategoryRow(
                "Nhắc học mỗi ngày", Icons.Rounded.NotificationsActive,
                if (s.reminderEnabled) "Nhắc lúc %02d:%02d · ôn thẻ đến hạn, giữ chuỗi".format(s.reminderMinutes / 60, s.reminderMinutes % 60) else "Đang tắt",
            ) { openPage = 9 }
        }
        item {
            CategoryRow("Dữ liệu", Icons.Rounded.Storage, "Xuất Excel, khôi phục, xoá lịch sử") { openPage = 8 }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Pill("HUY FAN v${BuildConfig.VERSION_NAME}", color = Brand.Violet)
                Spacer(Modifier.height(6.dp))
                Text("Học tiếng Hàn mỗi ngày – 화이팅!", style = MaterialTheme.typography.bodySmall, color = x.muted)
            }
        }
    }

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
                    0 -> SheetPage("Quyền & trạng thái", Icons.Rounded.Security) {
                StatusRow("Micro", "Để nghe câu hỏi và từ khoá HUY FAN", mic, onFix = onRequestPermissions)
                StatusRow("Thông báo", "Hiện trạng thái chế độ chờ", notif, onFix = onRequestPermissions)
                StatusRow(
                    "Hiển thị trên ứng dụng khác", "Để bảng HUY FAN bật lên khi bạn gọi (kể cả màn hình khoá)", overlay,
                    fixLabel = "Mở cài đặt",
                ) {
                    context.tryStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                }
                StatusRow(
                    "Không tối ưu pin", "Samsung hay tắt app chạy nền – nên bật khi dùng chế độ chờ", battery,
                    fixLabel = "Mở cài đặt",
                ) {
                    // Hộp hỏi thẳng cho HUY FAN; máy không hỗ trợ thì mở danh sách
                    context.tryStart(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                    )
                }
                StatusRow(
                    "Giọng đọc Việt & Hàn",
                    "Tiếng Việt: ${if (vi) "có" else "thiếu"} · Tiếng Hàn: ${if (ko) "có" else "thiếu"}" +
                        if (vi && ko) "" else " – bấm Tải giọng (nên dùng Google TTS)",
                    vi && ko, fixLabel = "Tải giọng",
                ) { context.tryStart(vm.ttsInstallIntent()) }
            }
                    1 -> SheetPage("AI & API (miễn phí)", Icons.Rounded.Psychology) {
                Segmented(Provider.entries, s.provider, { it.label }, { p -> vm.updateSettings { it.copy(provider = p) } })
                if (s.provider == Provider.GEMINI) {
                    SettingTextField(
                        "Gemini API key", s.geminiKey, { v -> vm.updateSettings { it.copy(geminiKey = v, geminiResolvedModel = "") } },
                        placeholder = "AIza…", secret = true,
                        supporting = "Miễn phí tại Google AI Studio → Get API key",
                    )
                    Row(Modifier.padding(horizontal = 12.dp)) {
                        FilledTonalButton(onClick = { context.openUrl("https://aistudio.google.com/apikey") }) {
                            Icon(Icons.Rounded.VpnKey, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Lấy key miễn phí")
                        }
                    }
                    val autoLabel = "Tự động" + if (s.geminiResolvedModel.isNotBlank()) " (${s.geminiResolvedModel})" else ""
                    DropdownField(
                        "Model", s.geminiModel,
                        listOf("" to autoLabel) + models.map { it to it },
                        onSelect = { m -> vm.updateSettings { it.copy(geminiModel = m) } },
                        trailingAction = {
                            IconButton(onClick = {
                                vm.listModels(Provider.GEMINI) { r ->
                                    r.onSuccess { models = it; context.toast("Tải ${it.size} model") }
                                        .onFailure { context.toast(it.message ?: "Lỗi") }
                                }
                            }) { Icon(Icons.Rounded.Refresh, "Tải danh sách model") }
                        },
                    )
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            enabled = !benchBusy,
                            onClick = {
                                benchBusy = true
                                vm.benchmark { r ->
                                    benchBusy = false
                                    r.onSuccess { bench = it }
                                        .onFailure { e -> context.toast((e as? AiException)?.userMessage ?: e.message ?: "Lỗi") }
                                }
                            },
                        ) {
                            Icon(Icons.Rounded.Speed, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Đo tốc độ model")
                        }
                        if (benchBusy) {
                            Spacer(Modifier.width(12.dp))
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("tối đa 25 giây…", style = MaterialTheme.typography.bodySmall, color = x.muted)
                        }
                    }
                    if (bench.isNotEmpty()) {
                        Text(
                            "Chạm model để dùng (xanh = trả lời đúng định dạng):",
                            style = MaterialTheme.typography.bodySmall, color = x.muted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        bench.forEach { b ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = b.ok) {
                                        vm.updateSettings { it.copy(geminiModel = b.model) }
                                        context.toast("Đã chọn ${b.model}")
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Dot(if (b.ok) Brand.Green else Brand.Red)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        b.model, style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (b.model == s.geminiModel) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    Text(b.note, style = MaterialTheme.typography.labelSmall, color = x.muted, maxLines = 1)
                                }
                                Text(
                                    "%.1fs".format(b.ms / 1000f),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (b.ok) Brand.Green else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    SwitchRow("Ưu tiên tốc độ", "Tắt chế độ “suy nghĩ” của model → trả lời nhanh hơn nhiều", s.fastMode) { v ->
                        vm.updateSettings { it.copy(fastMode = v) }
                    }
                    SwitchRow("Dùng bản Flash-Lite", "Nhanh nhất (~2 giây), ít bị quá tải, hạn mức free cao", s.preferLite) { v ->
                        vm.updateSettings { it.copy(preferLite = v, geminiResolvedModel = "") }
                    }
                    SettingTextField("Link API Gemini", s.geminiBaseUrl, { v -> vm.updateSettings { it.copy(geminiBaseUrl = v.ifBlank { DEFAULT_GEMINI_BASE }) } })
                    RowDivider()
                    Text(
                        "API dự phòng khi Gemini quá tải (khuyên dùng)",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 10.dp),
                    )
                    Text(
                        "Dự phòng khi Gemini nghẽn – Groq miễn phí, rất nhanh.",
                        style = MaterialTheme.typography.bodySmall, color = x.muted,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    SettingTextField(
                        "Groq API key (dự phòng)", s.openaiKey,
                        { v ->
                            vm.updateSettings {
                                // Ô này dành cho Groq → key gsk_ luôn dùng link Groq (tránh link cũ của preset khác)
                                val groq = v.trim().startsWith("gsk_") && !it.openaiBaseUrl.contains("groq.com")
                                it.copy(
                                    openaiKey = v,
                                    openaiBaseUrl = if (groq) DEFAULT_OPENAI_BASE else it.openaiBaseUrl.ifBlank { DEFAULT_OPENAI_BASE },
                                    openaiModel = if (groq) "llama-3.3-70b-versatile" else it.openaiModel,
                                    fallback = true,
                                )
                            }
                        },
                        placeholder = "gsk_…", secret = true,
                        supporting = if (s.openaiKey.isBlank()) "Chưa có dự phòng" else "Dự phòng: ${s.openaiModel} @ ${s.openaiBaseUrl.removePrefix("https://")}",
                    )
                    Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = { context.openUrl("https://console.groq.com/keys") }) {
                            Icon(Icons.Rounded.VpnKey, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Lấy Groq key")
                        }
                        if (s.openaiKey.isNotBlank()) {
                            OutlinedButton(onClick = {
                                aiBusy = true; aiResult = null
                                vm.testAi(Provider.OPENAI) { r -> aiBusy = false; aiResult = "Dự phòng: $r" to r.startsWith("OK") }
                            }, enabled = !aiBusy) {
                                Icon(Icons.Rounded.Bolt, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Thử dự phòng")
                            }
                        }
                    }
                } else {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PRESETS.take(2).forEach { p ->
                            FilledTonalButton(onClick = { vm.updateSettings { it.copy(openaiBaseUrl = p.base, openaiModel = p.model) } }) { Text(p.name) }
                        }
                    }
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PRESETS.drop(2).forEach { p ->
                            FilledTonalButton(onClick = { vm.updateSettings { it.copy(openaiBaseUrl = p.base, openaiModel = p.model) } }) { Text(p.name) }
                        }
                    }
                    SettingTextField("Link API (base URL)", s.openaiBaseUrl, { v -> vm.updateSettings { it.copy(openaiBaseUrl = v) } },
                        placeholder = "https://…/v1", supporting = "Chuẩn OpenAI: app gọi {link}/chat/completions")
                    SettingTextField("API key", s.openaiKey, { v -> vm.updateSettings { it.copy(openaiKey = v) } }, secret = true)
                    PRESETS.firstOrNull { it.base == s.openaiBaseUrl && it.keyUrl.isNotBlank() }?.let { p ->
                        Row(Modifier.padding(horizontal = 12.dp)) {
                            FilledTonalButton(onClick = { context.openUrl(p.keyUrl) }) {
                                Icon(Icons.Rounded.VpnKey, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Lấy key ${p.name}")
                            }
                        }
                    }
                    SettingTextField("Model", s.openaiModel, { v -> vm.updateSettings { it.copy(openaiModel = v) } })
                    if (oaModels.isNotEmpty()) {
                        DropdownField("Chọn nhanh model", s.openaiModel, oaModels.map { it to it }, { m -> vm.updateSettings { it.copy(openaiModel = m) } })
                    }
                    Row(Modifier.padding(horizontal = 12.dp)) {
                        FilledTonalButton(onClick = {
                            vm.listModels(Provider.OPENAI) { r ->
                                r.onSuccess { oaModels = it; context.toast("Tải ${it.size} model") }
                                    .onFailure { context.toast(it.message ?: "Lỗi") }
                            }
                        }) { Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Tải danh sách model") }
                    }
                }
                RowDivider()
                SwitchRow("API dự phòng", "Lỗi / hết hạn mức thì tự chuyển sang API còn lại (nếu đã nhập key)", s.fallback) { v ->
                    vm.updateSettings { it.copy(fallback = v) }
                }
                SliderRow("Độ sáng tạo", "%.1f".format(s.temperature), s.temperature, 0f..1.2f, steps = 11,
                    subtitle = "Thấp = chính xác, ổn định · Cao = trả lời phong phú hơn") { v -> vm.updateSettings { it.copy(temperature = v) } }
                SliderRow("Nhớ ngữ cảnh", "${s.contextTurns} tin", s.contextTurns.toFloat(), 2f..20f, steps = 17,
                    subtitle = "Số tin nhắn gần nhất gửi kèm để AI hiểu mạch trò chuyện") { v -> vm.updateSettings { it.copy(contextTurns = v.toInt()) } }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        aiBusy = true; aiResult = null
                        vm.testAi(s.provider) { r -> aiBusy = false; aiResult = r to r.startsWith("OK") }
                    }, enabled = !aiBusy) {
                        Icon(Icons.Rounded.Bolt, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Kiểm tra kết nối")
                    }
                    if (aiBusy) {
                        Spacer(Modifier.width(12.dp)); CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
                ResultBox(aiResult?.first, aiResult?.second == true)
            }
                    2 -> SheetPage("Prompt & phong cách", Icons.Rounded.AutoAwesome) {
                SwitchRow(
                    "Trả lời chi tiết, kỹ càng",
                    "Giảng kỹ nghĩa, sắc thái, ví dụ, phát âm",
                    s.detailedAnswers,
                ) { v -> vm.updateSettings { it.copy(detailedAnswers = v) } }
                SwitchRow(
                    "Trả lời tức thì từ sổ", "Hỏi lại từ đã có trong sổ → trả lời ngay (không cần mạng, không chờ AI)",
                    s.instantFromNotebook,
                ) { v -> vm.updateSettings { it.copy(instantFromNotebook = v) } }
                SettingTextField("Tên của bạn", s.userName, { v -> vm.updateSettings { it.copy(userName = v) } }, placeholder = "vd: Huy")
                Text("Phong cách nói chuyện", style = MaterialTheme.typography.labelLarge, color = x.muted, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                Segmented(TalkStyle.entries, s.style, { it.label }, { st -> vm.updateSettings { it.copy(style = st) } })
                Text(s.style.desc, style = MaterialTheme.typography.bodySmall, color = x.muted, modifier = Modifier.padding(horizontal = 16.dp))
                SmallGap()
                ActionRow(
                    "Chỉnh sửa prompt gửi cho AI",
                    if (s.customPrompt.isBlank()) "Đang dùng prompt mặc định" else "Đang dùng prompt tuỳ chỉnh (${s.customPrompt.length} ký tự)",
                    Icons.Rounded.Edit, onClick = onEditPrompt,
                )
                SwitchRow("Tự lưu từ vựng", "Từ hỏi xong sẽ vào sổ theo ngày", s.autoSaveVocab) { v -> vm.updateSettings { it.copy(autoSaveVocab = v) } }
            }
                    3 -> SheetPage("Giọng nói & HUY FAN", Icons.Rounded.GraphicEq) {
                SettingTextField(
                    "Từ khoá đánh thức", s.wakePhrases, { v -> vm.updateSettings { it.copy(wakePhrases = v) } },
                    supporting = "Viết theo tiếng Việt, cách nhau bằng dấu phẩy. “huy phan” = HUY FAN",
                )
                Text("Độ nhạy khi gọi HUY FAN", style = MaterialTheme.typography.labelLarge, color = x.muted, modifier = Modifier.padding(start = 16.dp, top = 6.dp))
                Segmented(listOf(0, 1, 2), s.wakeSensitivity, { listOf("Ít nhầm", "Cân bằng", "Nhạy")[it] }, { v -> vm.updateSettings { it.copy(wakeSensitivity = v) } })
                Text(
                    "Bật nhầm → Ít nhầm · Gọi không nghe → Nhạy",
                    style = MaterialTheme.typography.bodySmall, color = x.muted, modifier = Modifier.padding(horizontal = 16.dp),
                )
                SettingTextField(
                    "Câu đáp khi được gọi", s.wakeReplies, { v -> vm.updateSettings { it.copy(wakeReplies = v) } },
                    singleLine = false, minLines = 2, supporting = "Nhiều câu cách nhau bằng dấu |, app chọn ngẫu nhiên",
                )
                SwitchRow("Đọc câu trả lời", "HUY FAN nói câu trả lời thành tiếng", s.speakReplies) { v -> vm.updateSettings { it.copy(speakReplies = v) } }
                SwitchRow("Trò chuyện liên tục", "Đọc xong tự nghe tiếp, im lặng thì dừng", s.continuous) { v -> vm.updateSettings { it.copy(continuous = v) } }
                SwitchRow("Chào khi mở bằng phím tắt", "Nói “HUY FAN đây!” cả khi mở bằng nút/tile", s.greetOnShortcut) { v -> vm.updateSettings { it.copy(greetOnShortcut = v) } }
                SwitchRow("Ưu tiên nhận diện của Google", "Nghe tiếng Việt chuẩn nhất trên Samsung", s.preferGoogleStt) { v -> vm.updateSettings { it.copy(preferGoogleStt = v) } }
                SliderRow(
                    "Ngắt câu sau khi im lặng", "%.1f giây".format(s.endSilenceMs / 1000f), s.endSilenceMs.toFloat(), 800f..3000f, steps = 10,
                    subtitle = "Nói xong, im lặng chừng này thì HUY FAN tự gửi câu hỏi",
                ) { v -> vm.updateSettings { it.copy(endSilenceMs = v.toInt()) } }
                RowDivider()
                Text("Kiểu giọng đọc", style = MaterialTheme.typography.labelLarge, color = x.muted, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                Segmented(TtsMode.entries, s.ttsMode, { it.label }, { m -> vm.updateSettings { it.copy(ttsMode = m) } })
                if (s.ttsMode == TtsMode.AI) {
                    Text(
                        "Giọng AI tự nhiên, đọc liền Việt + Hàn. Lỗi mạng thì dùng giọng máy.",
                        style = MaterialTheme.typography.bodySmall, color = x.muted,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    listOf(true to "GIỌNG NỮ", false to "GIỌNG NAM").forEach { (female, title) ->
                        Text(
                            title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = x.muted,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp),
                        )
                        AI_VOICES.filter { it.female == female }.forEach { v ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.updateSettings { it.copy(aiVoice = v.id) } }
                                    .padding(start = 8.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = s.aiVoice == v.id, onClick = { vm.updateSettings { it.copy(aiVoice = v.id) } })
                                Column(Modifier.weight(1f)) {
                                    Text(v.id, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    Text(v.desc, style = MaterialTheme.typography.bodySmall, color = x.muted)
                                }
                                IconButton(onClick = {
                                    vm.previewAiVoice(v.id) { err -> if (err != null) context.toast(err) }
                                }) { Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Nghe thử ${v.id}", tint = MaterialTheme.colorScheme.primary) }
                            }
                        }
                    }
                }
                RowDivider()
                Text(
                    "Giọng máy (offline)", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp),
                )
                Text(
                    "Đọc tức thì khi bấm loa – chọn giọng riêng cho mỗi ngôn ngữ.",
                    style = MaterialTheme.typography.bodySmall, color = x.muted,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                val engines = remember(tick, ttsReady) { vm.ttsEngines() }
                val viVoices = remember(tick, ttsReady, s.viTtsEngine) { vm.voiceOptions(false) }
                val koVoices = remember(tick, ttsReady, s.koTtsEngine) { vm.voiceOptions(true) }
                DropdownField(
                    "Bộ đọc tiếng Việt", s.viTtsEngine,
                    listOf("" to "Tự động (ưu tiên Google)") + engines,
                    onSelect = { e -> vm.updateSettings { it.copy(viTtsEngine = e, viVoice = "") } },
                )
                DropdownField(
                    "Giọng tiếng Việt", s.viVoice,
                    listOf("" to "Mặc định của bộ đọc") + viVoices.map { it.name to it.label },
                    onSelect = { v -> vm.updateSettings { it.copy(viVoice = v) } },
                    trailingAction = {
                        IconButton(onClick = { vm.previewDeviceVoice(false, s.viVoice) }) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Nghe thử", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
                DropdownField(
                    "Bộ đọc tiếng Hàn", s.koTtsEngine,
                    listOf("" to "Tự động (ưu tiên Google)") + engines,
                    onSelect = { e -> vm.updateSettings { it.copy(koTtsEngine = e, koVoice = "") } },
                )
                DropdownField(
                    "Giọng tiếng Hàn", s.koVoice,
                    listOf("" to "Mặc định của bộ đọc") + koVoices.map { it.name to it.label },
                    onSelect = { v -> vm.updateSettings { it.copy(koVoice = v) } },
                    trailingAction = {
                        IconButton(onClick = { vm.previewDeviceVoice(true, s.koVoice) }) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Nghe thử", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
                SliderRow("Tốc độ đọc", "%.1fx".format(s.speechRate), s.speechRate, 0.6f..1.6f, steps = 9) { v ->
                    vm.updateSettings { it.copy(speechRate = v) }
                }
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    FilledTonalButton(onClick = { vm.testVoice() }) { Text("Nghe thử câu song ngữ") }
                }
            }
                    4 -> SheetPage("Chế độ chờ & pin", Icons.Rounded.BatteryChargingFull) {
                SwitchRow(
                    "Chế độ chờ “HUY FAN”",
                    if (standbyOn) standbyStatus.ifBlank { "Đang chạy" } else "Đang tắt – bật bằng tile, widget hoặc nút này",
                    standbyOn,
                ) { on -> if (on) StandbyService.start(context) else StandbyService.stop(context) }
                val idx = AUTO_OFF_STEPS.indexOf(s.autoOffMinutes).let { if (it < 0) 4 else it }
                SliderRow(
                    "Tự tắt sau", if (s.autoOffMinutes == 0) "Không tự tắt" else "${s.autoOffMinutes} phút",
                    idx.toFloat(), 0f..(AUTO_OFF_STEPS.size - 1).toFloat(), steps = AUTO_OFF_STEPS.size - 2,
                    subtitle = "Không ai gọi trong khoảng này thì tắt để tiết kiệm pin",
                ) { v -> vm.updateSettings { it.copy(autoOffMinutes = AUTO_OFF_STEPS[v.toInt().coerceIn(0, AUTO_OFF_STEPS.lastIndex)]) } }
                SwitchRow("Tạm dừng khi tắt màn hình", "Tiết kiệm pin – tắt nếu muốn gọi cả khi khoá màn hình", s.pauseOnScreenOff) { v ->
                    vm.updateSettings { it.copy(pauseOnScreenOff = v) }
                }
                SwitchRow("Bật chờ khi mở app", "Mở HUY FAN là tự bật nghe từ khoá", s.standbyOnLaunch) { v ->
                    vm.updateSettings { it.copy(standbyOnLaunch = v) }
                }
                SwitchRow(
                    "Nghe từ khoá “HUY FAN”", "Tắt nếu chỉ muốn dùng bong bóng chat (đỡ tốn pin hơn)", s.wakeListenEnabled,
                ) { v -> vm.updateSettings { it.copy(wakeListenEnabled = v) } }
                RowDivider()
                Text(
                    "Bong bóng chat nổi", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp),
                )
                Text(
                    "Nổi trên mọi màn hình – chạm để hỏi, kéo xuống ✕ để ẩn.",
                    style = MaterialTheme.typography.bodySmall, color = x.muted,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                SwitchRow("Hiện bong bóng chat", if (standbyOn) "Hiện khi chế độ chờ đang bật" else "Sẽ hiện khi bật chế độ chờ", s.bubbleEnabled) { v ->
                    vm.updateSettings { it.copy(bubbleEnabled = v) }
                }
                if (s.bubbleEnabled) {
                    val overlayOk = remember(tick) { Settings.canDrawOverlays(context) }
                    if (!overlayOk) {
                        StatusRow(
                            "Cần quyền hiển thị trên ứng dụng khác", "Để bong bóng nổi lên trên app khác", false,
                            fixLabel = "Cấp quyền",
                        ) {
                            context.tryStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                        }
                    }
                    val lockOk = remember(tick) { com.hankukko.huyfan.service.BubbleAccessibilityService.isEnabled(context) }
                    StatusRow(
                        "Hiện cả trên màn hình khoá",
                        if (lockOk) "Đã bật – bong bóng hiện cả khi máy khoá, chạm là hỏi không cần mở khoá"
                        else "Cài đặt → Hỗ trợ → Ứng dụng đã cài → “HUY FAN – Bong bóng trên màn hình khoá” → Bật. Chỉ dùng để vẽ bong bóng, không đọc màn hình",
                        lockOk, fixLabel = "Mở cài đặt",
                    ) {
                        context.tryStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    Text("Kích thước", style = MaterialTheme.typography.labelLarge, color = x.muted, modifier = Modifier.padding(start = 16.dp, top = 6.dp))
                    Segmented(listOf(0, 1, 2), s.bubbleSize, { listOf("Nhỏ", "Vừa", "Lớn")[it] }, { v -> vm.updateSettings { it.copy(bubbleSize = v) } })
                    Text("Chạm bong bóng thì", style = MaterialTheme.typography.labelLarge, color = x.muted, modifier = Modifier.padding(start = 16.dp, top = 6.dp))
                    Segmented(
                        listOf(true, false), s.bubbleTapListen, { if (it) "🎤 Nghe ngay" else "⌨️ Gõ phím" },
                        { v -> vm.updateSettings { it.copy(bubbleTapListen = v) } },
                    )
                    Text(
                        if (s.bubbleTapListen) "Mở cửa sổ và bật mic luôn." else "Mở cửa sổ kèm bàn phím – hợp nơi ồn ào.",
                        style = MaterialTheme.typography.bodySmall, color = x.muted, modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
                    5 -> SheetPage("Google Sheets (sổ từ online)", Icons.Rounded.CloudUpload) {
                ActionRow("Hướng dẫn & mã Apps Script", "Làm 1 lần ~2 phút, không cần đăng nhập trong app", Icons.Rounded.Code, onClick = onSheetsGuide)
                SettingTextField(
                    "Link Apps Script (Web App)", s.sheetsUrl, { v -> vm.updateSettings { it.copy(sheetsUrl = v) } },
                    placeholder = "https://script.google.com/macros/s/…/exec",
                )
                SettingTextField("Mã bí mật (tuỳ chọn)", s.sheetsToken, { v -> vm.updateSettings { it.copy(sheetsToken = v) } }, secret = true)
                SwitchRow("Tự đồng bộ", "Mỗi từ / ngữ pháp mới lưu sẽ được đẩy lên Sheet", s.sheetsAutoSync) { v -> vm.updateSettings { it.copy(sheetsAutoSync = v) } }
                val sheetStats by vm.stats.collectAsStateWithLifecycle()
                Text(
                    "Sheet gồm 2 trang: “Từ vựng” và “Ngữ pháp”. " +
                        if (s.sheetsUrl.isBlank()) "Chưa kết nối." else if (sheetStats.unsynced == 0) "Đã đồng bộ hết." else "Còn ${sheetStats.unsynced} mục chưa lên Sheet.",
                    style = MaterialTheme.typography.bodySmall, color = x.muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { sheetsResult = "Đang kiểm tra…" to true; vm.testSheets(s.sheetsUrl) { sheetsResult = it to !it.startsWith("Lỗi") } }) { Text("Gửi thử") }
                    FilledTonalButton(onClick = { vm.resyncAll { sheetsResult = it to !it.startsWith("Lỗi") } }) { Text("Đồng bộ lại tất cả") }
                }
                ActionRow(
                    "Khôi phục từ Google Sheets", "Kéo sổ từ trên Sheet về máy (máy mới, lỡ xoá…) – chỉ thêm mục còn thiếu",
                    Icons.Rounded.CloudDownload,
                ) {
                    sheetsResult = "Đang tải dữ liệu từ Sheet…" to true
                    vm.restoreFromSheets { sheetsResult = it to !it.startsWith("Lỗi") }
                }
                ResultBox(sheetsResult?.first ?: sheetsStatus, sheetsResult?.second ?: (sheetsStatus?.startsWith("Lỗi") == false))
            }
                    6 -> SheetPage("Phím tắt khởi động nhanh", Icons.Rounded.TouchApp) {
                Text(
                    "Chọn cách bật HUY FAN tiện nhất:",
                    style = MaterialTheme.typography.bodySmall, color = x.muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                ActionRow(
                    "Phím sườn Samsung (nhấn 2 lần)",
                    "Mở trang Phím Cạnh → Nhấn hai lần → Mở ứng dụng → chọn “Hỏi HUY FAN”",
                    Icons.Rounded.Smartphone,
                ) {
                    context.tryStart(
                        Intent("com.samsung.android.intent.action.SIDE_KEY_SETTINGS"), // mở thẳng trang "Phím Cạnh"
                        Intent("android.settings.ADVANCED_FEATURES_SETTINGS"),
                        Intent(Settings.ACTION_SETTINGS),
                    )
                }
                val lockSet = remember(tick) {
                    runCatching {
                        Settings.System.getString(context.contentResolver, "lock_application_shortcut")
                            ?.contains("${context.packageName}/") == true
                    }.getOrDefault(false)
                }
                ActionRow(
                    "Biểu tượng trên màn hình khoá",
                    if (lockSet) "✓ Đã đặt – vuốt biểu tượng mic tím ở góc màn hình khoá để hỏi, không cần mở khoá"
                    else "Màn hình khoá → Phím tắt → chọn “Hỏi HUY FAN” (góc trái hoặc phải). Vuốt là hỏi, không cần mở khoá",
                    Icons.Rounded.Smartphone,
                ) {
                    if (!isQuickAskEnabled(context)) setQuickAskEnabled(context, true)
                    context.tryStart(Intent("com.android.settings.LOCKSCREEN_SETTINGS"), Intent(Settings.ACTION_SECURITY_SETTINGS))
                }
                ActionRow(
                    "Đặt làm trợ lý kỹ thuật số",
                    "Ứng dụng mặc định → Ứng dụng trợ lý kỹ thuật số → HUY FAN. Sau đó giữ phím sườn / vuốt góc dưới để gọi",
                    Icons.Rounded.AutoAwesome,
                ) { context.tryStart(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS), Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
                if (Build.VERSION.SDK_INT >= 33) {
                    ActionRow("Thêm ô vào bảng cài đặt nhanh", "Vuốt xuống → chạm “HUY FAN chờ” để bật/tắt", Icons.Rounded.Dashboard) {
                        requestTile(context, ComponentName(context, StandbyTileService::class.java), context.getString(R.string.tile_standby))
                    }
                    ActionRow("Thêm ô “Hỏi HUY FAN”", "Một chạm là hỏi ngay, không cần chế độ chờ", Icons.Rounded.Dashboard) {
                        requestTile(context, ComponentName(context, AskTileService::class.java), context.getString(R.string.tile_ask))
                    }
                }
                ActionRow("Thêm widget “Từ của ngày”", "Mỗi ngày 1 từ: nghe, lật xem nghĩa, ôn thẻ đến hạn", Icons.Rounded.Widgets) {
                    val awm = AppWidgetManager.getInstance(context)
                    if (awm.isRequestPinAppWidgetSupported) awm.requestPinAppWidget(ComponentName(context, com.hankukko.huyfan.service.WordWidget::class.java), null, null)
                    else context.toast("Giữ màn hình chính → Widget → HUY FAN – Từ của ngày")
                }
                ActionRow("Thêm widget ra màn hình chính", "Nút mic + bật/tắt chờ + số từ hôm nay", Icons.Rounded.Widgets) {
                    val awm = AppWidgetManager.getInstance(context)
                    if (awm.isRequestPinAppWidgetSupported) awm.requestPinAppWidget(ComponentName(context, HuyFanWidget::class.java), null, null)
                    else context.toast("Giữ màn hình chính → Widget → HUY FAN")
                }
                ActionRow("Ghim lối tắt “Hỏi HUY FAN”", "Biểu tượng mic trên màn hình chính", Icons.Rounded.AddToHomeScreen) {
                    val info = ShortcutInfoCompat.Builder(context, "pin_ask")
                        .setShortLabel("Hỏi HUY FAN")
                        .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_quick_ask))
                        .setIntent(
                            Intent(context, AssistActivity::class.java)
                                .setAction("com.hankukko.huyfan.ASK")
                                .putExtra(AssistActivity.EXTRA_FROM, AssistActivity.FROM_SHORTCUT)
                        ).build()
                    if (!ShortcutManagerCompat.requestPinShortcut(context, info, null)) context.toast("Launcher không hỗ trợ ghim lối tắt")
                }
                var aliasOn by remember { mutableStateOf(isQuickAskEnabled(context)) }
                SwitchRow("Hiện biểu tượng “Hỏi HUY FAN”", "Biểu tượng riêng trong ngăn ứng dụng – dùng cho phím sườn", aliasOn) { on ->
                    setQuickAskEnabled(context, on); aliasOn = on
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Headset, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Tai nghe Bluetooth: giữ nút trên tai nghe để gọi HUY FAN (khi đặt làm trợ lý).", style = MaterialTheme.typography.bodySmall, color = x.muted)
                }
            }
                    7 -> SheetPage("Giao diện", Icons.Rounded.Palette) {
                Segmented(ThemeMode.entries, s.themeMode, { it.label }, { m -> vm.updateSettings { it.copy(themeMode = m) } })
            }
                    9 -> SheetPage("Nhắc học mỗi ngày", Icons.Rounded.NotificationsActive) {
                var pickTime by remember { mutableStateOf(false) }
                var testMsg by remember { mutableStateOf<String?>(null) }
                SwitchRow(
                    "Nhắc học", "Tới giờ: còn thẻ đến hạn thì nhắc ôn; chưa học gì hôm nay thì nhắc giữ chuỗi",
                    s.reminderEnabled,
                ) { v -> vm.updateSettings { it.copy(reminderEnabled = v) } }
                ActionRow(
                    "Giờ nhắc", "%02d:%02d mỗi ngày · chạm để đổi".format(s.reminderMinutes / 60, s.reminderMinutes % 60),
                    Icons.Rounded.Schedule,
                ) { pickTime = true }
                ActionRow("Gửi thử thông báo", testMsg ?: "Xem thông báo nhắc học trông thế nào", Icons.Rounded.NotificationsActive) {
                    vm.testReminder { ok -> testMsg = if (ok) "Đã gửi – kéo thanh thông báo xuống để xem" else "Không gửi được – kiểm tra quyền Thông báo" }
                }
                if (pickTime) {
                    TimePickDialog(
                        s.reminderMinutes,
                        onPick = { m -> vm.updateSettings { it.copy(reminderMinutes = m) }; pickTime = false },
                        onDismiss = { pickTime = false },
                    )
                }
            }
                    8 -> SheetPage("Dữ liệu", Icons.Rounded.Storage) {
                ActionRow("Xuất sổ từ vựng (Excel/CSV)", "Mở được bằng Excel, Google Sheets", Icons.AutoMirrored.Rounded.OpenInNew) { vm.exportCsv(context) }
                var restoreMsg by remember { mutableStateOf<String?>(null) }
                ActionRow(
                    "Khôi phục sổ từ Google Sheets",
                    restoreMsg ?: if (s.sheetsUrl.isBlank()) "Cần kết nối Google Sheets trước" else "Kéo từ vựng, ngữ pháp và lịch ôn từ Sheet về máy",
                    Icons.Rounded.CloudDownload,
                ) {
                    restoreMsg = "Đang tải dữ liệu từ Sheet…"
                    vm.restoreFromSheets { restoreMsg = it }
                }
                var confirmClearChat by remember { mutableStateOf(false) }
                ActionRow("Xoá toàn bộ lịch sử trò chuyện", "Sổ từ vựng vẫn giữ nguyên", Icons.Rounded.Refresh, tint = MaterialTheme.colorScheme.error) {
                    confirmClearChat = true
                }
                if (confirmClearChat) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { confirmClearChat = false },
                        title = { Text("Xoá toàn bộ lịch sử?") },
                        text = { Text("Mọi cuộc trò chuyện sẽ bị xoá vĩnh viễn, không khôi phục được. Sổ từ vựng và ngữ pháp vẫn giữ nguyên.") },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                confirmClearChat = false
                                vm.clearHistory(); context.toast("Đã xoá lịch sử")
                            }) { Text("Xoá hết", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmClearChat = false }) { Text("Huỷ") } },
                    )
                }
            }
                }
            }
        }
    }
}

private fun requestTile(context: Context, cn: ComponentName, label: String) {
    if (Build.VERSION.SDK_INT < 33) return
    val sbm = context.getSystemService(StatusBarManager::class.java)
    sbm.requestAddTileService(cn, label, Icon.createWithResource(context, R.drawable.ic_stat_huyfan), context.mainExecutor) { result ->
        val msg = when (result) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Đã thêm ô “$label”"
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "Ô “$label” đã có sẵn"
            else -> "Chưa thêm ô"
        }
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}

private fun quickAskComponent(context: Context) = ComponentName(context.packageName, "com.hankukko.huyfan.QuickAsk")

private fun isQuickAskEnabled(context: Context): Boolean =
    context.packageManager.getComponentEnabledSetting(quickAskComponent(context)) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED

private fun setQuickAskEnabled(context: Context, on: Boolean) {
    context.packageManager.setComponentEnabledSetting(
        quickAskComponent(context),
        if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP,
    )
}

/** Dùng trong PromptEditor: phần định dạng cố định để người dùng xem. */
val FIXED_CONTRACT: String get() = PromptBuilder.OUTPUT_CONTRACT


/** 1 dòng trong trang Cài đặt chính: biểu tượng · tên · tóm tắt giá trị hiện tại. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(minutes: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = androidx.compose.material3.rememberTimePickerState(minutes / 60, minutes % 60, is24Hour = true)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Giờ nhắc học") },
        text = { androidx.compose.material3.TimePicker(state) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("Lưu") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Huỷ") } },
    )
}

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
