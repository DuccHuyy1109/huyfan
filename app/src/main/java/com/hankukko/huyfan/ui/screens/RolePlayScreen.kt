package com.hankukko.huyfan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hankukko.huyfan.ui.components.hideKeyboardOnTap
import com.hankukko.huyfan.ui.neonBackdrop
import com.hankukko.huyfan.ai.Turn
import com.hankukko.huyfan.assistant.LANG_KO
import com.hankukko.huyfan.assistant.RolePlay
import com.hankukko.huyfan.ui.MainViewModel
import com.hankukko.huyfan.ui.components.MicButton
import com.hankukko.huyfan.ui.theme.Brand
import com.hankukko.huyfan.ui.theme.LocalExtra

/** 1 lượt trong hội thoại nhập vai. */
private data class RpMsg(
    val isUser: Boolean,
    val ko: String,
    val vi: String = "",
    /** Câu sửa cho lượt của người học: (câu đúng, giải thích) */
    val fix: Pair<String, String>? = null,
    val vocab: List<RolePlay.Phrase> = emptyList(),
)

@Composable
fun RolePlayDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val x = LocalExtra.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val engine by vm.engine.state.collectAsStateWithLifecycle()
    var scenario by remember { mutableStateOf<RolePlay.Scenario?>(null) }
    var level by remember { mutableStateOf(RolePlay.Level.BEGINNER) }
    var custom by remember { mutableStateOf("") }
    val msgs = remember { mutableStateListOf<RpMsg>() }
    var suggestions by remember { mutableStateOf<List<RolePlay.Phrase>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showVi by remember { mutableStateOf(true) }
    var finished by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var savedMsg by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    DisposableEffect(Unit) { onDispose { vm.engine.cancel(); vm.stopSpeaking() } }
    LaunchedEffect(msgs.size, busy, finished) {
        val n = msgs.size + (if (busy) 1 else 0) + (if (finished) 1 else 0)
        if (n > 0) listState.animateScrollToItem(n - 1)
    }

    fun askAi() {
        val sc = scenario ?: return
        busy = true
        error = null
        val turns = buildList {
            add(Turn(Turn.USER, "(Bắt đầu: bạn nói câu mở đầu theo vai.)"))
            msgs.forEach { add(Turn(if (it.isUser) Turn.USER else Turn.ASSISTANT, it.ko)) }
        }
        vm.rolePlayTurn(RolePlay.system(sc, level, settings.userName), turns) { r ->
            busy = false
            r.onSuccess { t ->
                // Gắn câu sửa vào lượt vừa nói của người học
                val lastUser = msgs.indexOfLast { it.isUser }
                if (t.hasError && lastUser >= 0 && lastUser == msgs.lastIndex) {
                    msgs[lastUser] = msgs[lastUser].copy(fix = t.fixedKo to t.explainVi)
                }
                msgs += RpMsg(false, t.ko, t.vi, vocab = t.vocab)
                suggestions = t.suggestions
                if (t.ko.isNotBlank()) vm.speakKorean(t.ko)
                if (t.done) finished = true
            }.onFailure { e -> error = (e as? com.hankukko.huyfan.ai.AiException)?.userMessage ?: "Lỗi: ${e.message}" }
        }
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy || finished) return
        msgs += RpMsg(true, t)
        input = ""
        suggestions = emptyList()
        askAi()
    }

    fun mic() {
        if (listening) { vm.engine.stopListening(); return }
        vm.stopSpeaking()
        listening = true
        vm.engine.captureOnce(LANG_KO) { heard ->
            listening = false
            if (heard != null) send(heard) else error = "Chưa nghe rõ – bấm mic nói lại, hoặc gõ phím nha"
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        com.hankukko.huyfan.ui.components.DialogImeFix()
        Surface(Modifier.fillMaxSize().neonBackdrop(), color = androidx.compose.ui.graphics.Color.Transparent) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // ---------- Thanh trên ----------
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Đóng") }
                    Column(Modifier.weight(1f)) {
                        val sc = scenario
                        Text(
                            if (sc == null) "Hội thoại nhập vai" else "${sc.emoji} ${sc.title}",
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1,
                        )
                        Text(
                            if (sc == null) "Chọn tình huống để luyện nói tiếng Hàn" else "HUY FAN đóng vai ${sc.aiRole} · ${level.label}",
                            style = MaterialTheme.typography.labelMedium, color = x.muted, maxLines = 1,
                        )
                    }
                    if (scenario != null) {
                        IconButton(onClick = { showVi = !showVi }) {
                            Icon(Icons.Rounded.Translate, if (showVi) "Ẩn dịch" else "Hiện dịch", tint = if (showVi) MaterialTheme.colorScheme.primary else x.muted)
                        }
                        if (!finished && msgs.isNotEmpty()) {
                            IconButton(onClick = { finished = true; vm.engine.cancel() }) { Icon(Icons.Rounded.Flag, "Kết thúc") }
                        }
                    }
                }

                val sc = scenario
                if (sc == null) {
                    ScenarioPicker(
                        level, { level = it }, custom, { custom = it },
                        onPick = { picked -> scenario = picked; msgs.clear(); finished = false; askAi() },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth().hideKeyboardOnTap(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(msgs) { _, m -> RpBubble(m, showVi, onSpeak = { vm.speakKorean(it) }) }
                        if (busy) item { Text("HUY FAN đang nghĩ câu tiếp…", color = x.muted, style = MaterialTheme.typography.bodySmall) }
                        if (finished) item {
                            RpSummary(
                                msgs, savedMsg,
                                onSave = { list -> vm.saveRolePlayVocab(list) { n -> savedMsg = "Đã lưu $n từ vào sổ" } },
                                onAgain = { msgs.clear(); finished = false; savedMsg = null; askAi() },
                                onOther = { scenario = null; msgs.clear(); finished = false; savedMsg = null },
                            )
                        }
                    }
                    error?.let {
                        Text(it, color = Brand.Red, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    if (!finished) {
                        // Gợi ý câu nói tiếp
                        if (suggestions.isNotEmpty()) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(suggestions) { p ->
                                    Column(
                                        Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Brand.Violet.copy(alpha = 0.10f))
                                            .border(1.dp, Brand.Violet.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                            .clickable { input = p.ko; vm.speakKorean(p.ko) }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.Lightbulb, null, tint = Brand.Violet, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(p.ko, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        if (showVi) Text(p.vi, style = MaterialTheme.typography.labelSmall, color = x.muted)
                                    }
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextField(
                                value = if (listening) engine.partial else input,
                                onValueChange = { input = it },
                                placeholder = { Text(if (listening) "Đang nghe tiếng Hàn…" else "Nói hoặc gõ câu tiếng Hàn…") },
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(20.dp)),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                    focusedContainerColor = x.card, unfocusedContainerColor = x.card,
                                ),
                                trailingIcon = {
                                    if (input.isNotBlank() && !listening) {
                                        IconButton(onClick = { send(input) }, enabled = !busy) {
                                            Icon(Icons.AutoMirrored.Rounded.Send, "Gửi", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                },
                                maxLines = 3,
                            )
                            Spacer(Modifier.width(10.dp))
                            if (busy) {
                                Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
                            } else {
                                MicButton(listening = listening, onClick = { mic() }, size = 60.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScenarioPicker(
    level: RolePlay.Level,
    onLevel: (RolePlay.Level) -> Unit,
    custom: String,
    onCustom: (String) -> Unit,
    onPick: (RolePlay.Scenario) -> Unit,
    modifier: Modifier,
) {
    val x = LocalExtra.current
    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brand.gradient)
                    .padding(16.dp)
            ) {
                Text("Luyện nói như thật 🎭", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "HUY FAN đóng vai nói tiếng Hàn – nói sai được sửa ngay.",
                    color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Text("Trình độ", style = MaterialTheme.typography.labelLarge, color = x.muted)
            Segmented(RolePlay.Level.entries, level, { it.label }, onLevel, modifier = Modifier.padding(horizontal = 0.dp))
        }
        item { Text("Chọn tình huống", style = MaterialTheme.typography.labelLarge, color = x.muted) }
        items(RolePlay.SCENARIOS, key = { it.id }) { sc ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(x.card)
                    .border(1.dp, x.cardBorder, RoundedCornerShape(18.dp))
                    .clickable { onPick(sc) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(sc.emoji, fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(sc.title, fontWeight = FontWeight.Bold)
                    Text(sc.setting, style = MaterialTheme.typography.bodySmall, color = x.muted, maxLines = 2)
                }
            }
        }
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(x.card)
                    .border(1.dp, x.cardBorder, RoundedCornerShape(18.dp))
                    .padding(12.dp)
            ) {
                Text("✨ Tự chọn tình huống", fontWeight = FontWeight.Bold)
                TextField(
                    value = custom, onValueChange = onCustom,
                    placeholder = { Text("vd: Mua vé xem phim, gọi điện đặt bàn…") },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(14.dp)),
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    singleLine = true,
                )
                Button(
                    onClick = { onPick(RolePlay.Scenario("custom", "✨", custom.trim(), "nhân vật phù hợp nhất với tình huống", custom.trim())) },
                    enabled = custom.isNotBlank(), modifier = Modifier.padding(top = 8.dp),
                ) { Text("Bắt đầu") }
            }
        }
    }
}

@Composable
private fun RpBubble(m: RpMsg, showVi: Boolean, onSpeak: (String) -> Unit) {
    val x = LocalExtra.current
    if (m.isUser) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Box(
                Modifier
                    .padding(start = 48.dp)
                    .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                    .background(x.bubbleUser)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) { Text(m.ko, color = Color.White, style = MaterialTheme.typography.bodyLarge) }
            m.fix?.let { (fixed, why) ->
                Column(
                    Modifier
                        .padding(start = 48.dp, top = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brand.Amber.copy(alpha = 0.14f))
                        .border(1.dp, Brand.Amber.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .clickable { onSpeak(fixed) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✏️ Nên nói: ", style = MaterialTheme.typography.labelMedium, color = Brand.Amber, fontWeight = FontWeight.Bold)
                        Text(fixed, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Nghe", tint = Brand.Amber, modifier = Modifier.size(16.dp))
                    }
                    if (why.isNotBlank()) Text(why, style = MaterialTheme.typography.bodySmall, color = x.muted)
                }
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(end = 40.dp)) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                    .background(x.bubbleAi)
                    .clickable { onSpeak(m.ko) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f, fill = false)) {
                    Text(m.ko, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (showVi && m.vi.isNotBlank()) Text(m.vi, style = MaterialTheme.typography.bodySmall, color = x.muted)
                }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Nghe", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            if (m.vocab.isNotEmpty()) {
                Text(
                    "📌 " + m.vocab.joinToString(" · ") { "${it.ko} = ${it.vi}" },
                    style = MaterialTheme.typography.labelSmall, color = Brand.Teal, modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun RpSummary(
    msgs: List<RpMsg>,
    saved: String?,
    onSave: (List<RolePlay.Phrase>) -> Unit,
    onAgain: () -> Unit,
    onOther: () -> Unit,
) {
    val x = LocalExtra.current
    val userTurns = msgs.count { it.isUser }
    val fixes = msgs.filter { it.isUser && it.fix != null }
    val vocab = msgs.flatMap { it.vocab }.distinctBy { it.ko }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(x.card)
            .border(1.dp, x.cardBorder, RoundedCornerShape(22.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.EmojiEvents, null, tint = Brand.Amber, modifier = Modifier.size(56.dp))
        Text("Xong buổi luyện nói!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Ông nói $userTurns câu · ${userTurns - fixes.size} câu chuẩn · ${fixes.size} câu được sửa",
            color = x.muted, textAlign = TextAlign.Center,
        )
        if (fixes.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Ghi nhớ cách nói đúng", style = MaterialTheme.typography.labelLarge, color = x.muted, modifier = Modifier.fillMaxWidth())
            fixes.forEach { f ->
                Text("• ${f.fix?.first}", fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
            }
        }
        if (vocab.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Từ mới trong buổi (${vocab.size})", style = MaterialTheme.typography.labelLarge, color = x.muted, modifier = Modifier.fillMaxWidth())
            vocab.forEach { v -> Text("• ${v.ko} – ${v.vi}", modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) }
            Spacer(Modifier.height(8.dp))
            if (saved == null) {
                Button(onClick = { onSave(vocab) }) { Text("Lưu ${vocab.size} từ vào sổ") }
            } else {
                Text("✅ $saved", color = Brand.Green, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onAgain) { Text("Chơi lại") }
            OutlinedButton(onClick = onOther) { Text("Tình huống khác") }
        }
    }
}
