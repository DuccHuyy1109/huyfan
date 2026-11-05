package com.hankukko.huyfan.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hankukko.huyfan.ui.components.hideKeyboardOnTap
import com.hankukko.huyfan.assistant.LANG_KO
import com.hankukko.huyfan.assistant.Phase
import com.hankukko.huyfan.data.LessonItem
import com.hankukko.huyfan.data.grammarKeyOf
import com.hankukko.huyfan.data.keyOf
import com.hankukko.huyfan.service.StandbyService
import com.hankukko.huyfan.ui.MainViewModel
import com.hankukko.huyfan.ui.components.BrandTitle
import com.hankukko.huyfan.ui.components.MessageItem
import com.hankukko.huyfan.ui.components.MicButton
import com.hankukko.huyfan.ui.components.Pill
import com.hankukko.huyfan.ui.components.TypingDots
import com.hankukko.huyfan.ui.components.VoiceOrb
import com.hankukko.huyfan.ui.theme.Brand
import com.hankukko.huyfan.ui.theme.LocalExtra

fun Context.toastShort(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun Context.copyToClipboard(text: String) {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("HUY FAN", text))
    Toast.makeText(this, "Đã sao chép", Toast.LENGTH_SHORT).show()
}

fun phaseLabel(phase: Phase, ko: Boolean): String = when (phase) {
    Phase.IDLE -> "Sẵn sàng"
    Phase.GREETING -> "HUY FAN đây!"
    Phase.LISTENING -> if (ko) "Đang nghe tiếng Hàn…" else "Đang nghe…"
    Phase.THINKING -> "Đang nghĩ…"
    Phase.SPEAKING -> "Đang trả lời…"
}

@Composable
fun ChatScreen(
    vm: MainViewModel,
    onMic: () -> Unit,
    onToggleStandby: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenInterpreter: () -> Unit = {},
) {
    val context = LocalContext.current
    val x = LocalExtra.current
    val messages by vm.messages.collectAsStateWithLifecycle()
    val state by vm.engineState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val standby by StandbyService.running.collectAsStateWithLifecycle()
    // Khoá các mục đã có trong sổ → bảng biết hiện "+" hay thùng rác
    val allVocab by vm.vocab.collectAsStateWithLifecycle()
    val allGrammar by vm.grammar.collectAsStateWithLifecycle()
    val vocabKeys = remember(allVocab) { allVocab.map { keyOf(it.ko) }.toHashSet() }
    val grammarKeys = remember(allGrammar) { allGrammar.map { grammarKeyOf(it.pattern) }.toHashSet() }
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    var hanjaOpen by remember { mutableStateOf<Pair<String, String>?>(null) }
    val pendingImage by vm.engine.pendingImage.collectAsStateWithLifecycle()
    var processingImage by remember { mutableStateOf(false) }
    val hanjaIndex = remember(allVocab) { com.hankukko.huyfan.ui.screens.HanjaIndex.build(allVocab) }

    LaunchedEffect(messages.size, state.phase) {
        val extra = if (state.phase == Phase.THINKING) 1 else 0
        val count = messages.size + extra
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(Modifier.fillMaxSize()) {
        // ---------- Thanh trên ----------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandTitle(subtitle = "Trợ lý tiếng Hàn", modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            StandbyChip(on = standby, onClick = onToggleStandby)
            IconButton(onClick = onOpenHistory) {
                Icon(Icons.Rounded.History, "Lịch sử trò chuyện")
            }
            IconButton(onClick = { vm.newChat() }) {
                Icon(Icons.Rounded.AddComment, "Cuộc trò chuyện mới")
            }
        }

        Box(Modifier.weight(1f).hideKeyboardOnTap()) {
            if (messages.isEmpty() && state.phase != Phase.THINKING) {
                Welcome(
                    hasKey = settings.hasAiKey,
                    name = settings.userName,
                    statsToday = stats.today, statsTotal = stats.total, streak = stats.streak,
                    phase = state.phase, level = state.level,
                    onSuggestion = { vm.send(it) },
                    onSetup = onOpenSettings,
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(messages, key = { it.id }) { m ->
                        val prevUser = remember(messages, m.id) {
                            messages.lastOrNull { it.id < m.id && it.isUser }?.text
                        }
                        MessageItem(
                            m,
                            onSpeak = { vm.speak(it) },
                            onSpeakKorean = { vm.speakKorean(it) },
                            onCopy = { context.copyToClipboard(it) },
                            onRetry = prevUser?.let { q -> { vm.send(q) } },
                            onDeleteVocab = { i ->
                                m.vocab.getOrNull(i)?.let { context.toastShort("Đã xoá “${it.ko}” khỏi sổ") }
                                vm.engine.removeLearned(m, LessonItem.VOCAB, i)
                            },
                            onDeleteGrammar = { i ->
                                m.grammar.getOrNull(i)?.let { context.toastShort("Đã xoá “${it.pattern}” khỏi sổ") }
                                vm.engine.removeLearned(m, LessonItem.GRAMMAR, i)
                            },
                            vocabSaved = m.vocab.map { keyOf(it.ko) in vocabKeys },
                            grammarSaved = m.grammar.map { grammarKeyOf(it.pattern) in grammarKeys },
                            onAddVocab = { i ->
                                m.vocab.getOrNull(i)?.let { context.toastShort("Đã lưu “${it.ko}” vào sổ") }
                                vm.engine.addLearned(m, LessonItem.VOCAB, i)
                            },
                            onAddGrammar = { i ->
                                m.grammar.getOrNull(i)?.let { context.toastShort("Đã lưu “${it.pattern}” vào sổ") }
                                vm.engine.addLearned(m, LessonItem.GRAMMAR, i)
                            },
                            onHanja = { v -> hanjaOpen = v.hanja to v.hanViet },
                        )
                    }
                    if (state.phase == Phase.THINKING) {
                        item(key = "typing") {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(x.bubbleAi)
                                    .border(1.dp, x.cardBorder, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) { TypingDots() }
                        }
                    }
                }
            }
        }

        // ---------- Thông báo lỗi giọng nói ----------
        AnimatedVisibility(visible = state.notice != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(state.notice.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                IconButton(onClick = { vm.engine.clearNotice() }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, "Đóng", modifier = Modifier.size(16.dp))
                }
            }
        }

        // ---------- Đang nghe ----------
        AnimatedVisibility(
            visible = state.phase == Phase.LISTENING || state.phase == Phase.GREETING,
            enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(x.card)
                    .border(1.dp, x.cardBorder, RoundedCornerShape(20.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VoiceOrb(state.phase, state.level, size = 52.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(phaseLabel(state.phase, state.listenLang == LANG_KO), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        state.partial.ifBlank { if (state.listenLang == LANG_KO) "말씀하세요…" else "Nói đi ông…" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (state.partial.isBlank()) x.muted else MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                    )
                }
            }
        }

        // ---------- Trả lời chi tiết (bật/tắt nhanh) + trạng thái xử lý ảnh ----------
        if (processingImage) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
                Text("Đang xử lý ảnh…", style = MaterialTheme.typography.labelMedium, color = x.muted)
            }
        }

        // ---------- Ảnh đính kèm ----------
        pendingImage?.let { img ->
            com.hankukko.huyfan.ui.components.AttachmentBar(
                img,
                onRemove = { vm.engine.discardImage() },
                onQuickAsk = { q -> vm.send(q); input = "" },
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp),
            )
        }

        // ---------- Ô nhập ----------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(x.card)
                    .border(1.dp, x.neonBorder, RoundedCornerShape(28.dp))
                    .padding(start = 6.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LangToggle(ko = state.listenLang == LANG_KO, onClick = { vm.engine.toggleLanguage() })
                com.hankukko.huyfan.ui.components.AttachImageButton(
                    onPicked = { vm.engine.attachImage(it) },
                    modifier = Modifier.padding(start = 4.dp),
                    onBusy = { processingImage = it },
                )
                DetailButton(
                    on = settings.detailedAnswers,
                    onClick = { vm.updateSettings { it.copy(detailedAnswers = !it.detailedAnswers) } },
                )
                TextField(
                    value = input,
                    onValueChange = {
                        val ph = vm.engine.state.value.phase
                        if (ph != Phase.IDLE) vm.engine.switchToTyping()
                        input = it
                    },
                    placeholder = { Text(if (pendingImage != null) "Hỏi về ảnh này…" else "Hỏi HUY FAN…", color = x.muted) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp, max = 140.dp)
                        // Chạm vào ô để gõ → dừng mic, bỏ phần đã nghe
                        .onFocusChanged { if (it.isFocused) vm.engine.switchToTyping() },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (input.isNotBlank() || pendingImage != null) { vm.send(input); input = "" }
                    }),
                )
                AnimatedVisibility(visible = input.isNotBlank() || pendingImage != null) {
                    IconButton(onClick = { vm.send(input); input = "" }) {
                        Icon(Icons.AutoMirrored.Rounded.Send, "Gửi", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            MicButton(listening = state.phase == Phase.LISTENING || state.phase == Phase.SPEAKING, onClick = onMic)
        }
    }
    hanjaOpen?.let { (h, hv) ->
        com.hankukko.huyfan.ui.screens.HanjaSheet(
            h, hv, hanjaIndex,
            onSpeak = { vm.speakKorean(it) },
            onAsk = { q -> hanjaOpen = null; vm.send(q) },
            onDismiss = { hanjaOpen = null },
        )
    }
}

/** Nút tròn "Chi tiết" trong ô nhập: sáng gradient = trả lời chi tiết, mờ = ngắn gọn. */
@Composable
private fun DetailButton(on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 4.dp)
            .size(40.dp)
            .clip(CircleShape)
            .then(if (on) Modifier.background(Brand.gradient) else Modifier.background(Brand.Violet.copy(alpha = 0.14f)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.MenuBook,
            if (on) "Trả lời chi tiết: bật" else "Trả lời chi tiết: tắt",
            tint = if (on) Color.White else Brand.Violet, modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun LangToggle(ko: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (ko) Brand.Red.copy(alpha = 0.18f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (ko) "한" else "VI",
            fontWeight = FontWeight.Black,
            fontSize = if (ko) 16.sp else 12.sp,
            color = if (ko) Brand.Red else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun StandbyChip(on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (on) Brand.Green.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, if (on) Brand.Green.copy(alpha = 0.45f) else LocalExtra.current.cardBorder, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .then(if (on) Modifier.shadow(6.dp, CircleShape, ambientColor = Brand.Green, spotColor = Brand.Green) else Modifier)
                .clip(CircleShape)
                .background(if (on) Brand.Green else LocalExtra.current.muted)
        )
        Spacer(Modifier.width(6.dp))
        Text("Chờ", style = MaterialTheme.typography.labelMedium, color = if (on) Brand.Green else LocalExtra.current.muted)
    }
}

@Composable
private fun Welcome(
    hasKey: Boolean,
    name: String,
    statsToday: Int,
    statsTotal: Int,
    streak: Int,
    phase: Phase,
    level: Float,
    onSuggestion: (String) -> Unit,
    onSetup: () -> Unit,
) {
    val x = LocalExtra.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        VoiceOrb(phase, level, size = 170.dp)
        Text(
            if (name.isBlank()) "Hey bro! 안녕!" else "Hey ${name.trim()}! 안녕!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Bấm mic hoặc gọi “HUY FAN”",
            style = MaterialTheme.typography.bodyMedium,
            color = x.muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))

        if (!hasKey) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brand.warm)
                    .clickable(onClick = onSetup)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.VpnKey, null, tint = Color.White)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Thiết lập API key miễn phí", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Lấy key Gemini 1 phút ở aistudio.google.com rồi dán vào Cài đặt", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Hôm nay", "$statsToday từ", Icons.Rounded.AutoAwesome, Brand.Cyan, Modifier.weight(1f))
            StatTile("Tổng sổ", "$statsTotal từ", Icons.Rounded.School, Brand.Violet, Modifier.weight(1f))
            StatTile("Chuỗi", "$streak ngày", Icons.Rounded.LocalFireDepartment, Brand.Pink, Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        Text("Thử hỏi", style = MaterialTheme.typography.labelLarge, color = x.muted, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        val suggestions = listOf(
            "Rửa bát tiếng Hàn là gì?",
            "Dịch: Hôm nay tôi mệt quá",
            "Giải thích ngữ pháp -고 싶다",
            "Kiểm tra tôi các từ hôm nay",
            "Xem danh sách từ hôm nay",
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { s ->
                Row(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(x.card)
                        .border(1.dp, x.cardBorder, RoundedCornerShape(16.dp))
                        .clickable { onSuggestion(s) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Translate, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(s, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatTile(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier) {
    val x = LocalExtra.current
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(x.card)
            .border(1.dp, Brush.verticalGradient(listOf(color.copy(alpha = 0.55f), x.cardBorder)), RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = color, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = x.muted)
    }
}
