package com.hankukko.huyfan.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hankukko.huyfan.ui.neonBackdrop
import com.hankukko.huyfan.assistant.Interpreter
import com.hankukko.huyfan.assistant.Interpreter.Side
import com.hankukko.huyfan.container
import com.hankukko.huyfan.ui.theme.Brand
import com.hankukko.huyfan.ui.theme.LocalExtra
import kotlinx.coroutines.launch

/** 1 lượt nói trong phiên dịch. */
private data class ITurn(
    val id: Long,
    val side: Side,
    val heard: String,
    val fixed: String = "",
    val changed: Boolean = false,
    val translation: String = "",
    val note: String = "",
    val working: Boolean = true,
    val error: String? = null,
)

private val VI_COLOR = Brand.Blue
private val KO_COLOR = Brand.Teal

/**
 * Phiên dịch hội thoại Việt ⇄ Hàn kiểu phiên dịch viên: nói tới đâu hiện chữ tới đó,
 * nói xong AI sửa câu nghe nhầm rồi dịch + đọc to; bật "Tự luân phiên" thì tự chuyển sang nghe người kia.
 * Dùng chung cho app và cửa sổ bong bóng.
 */
@Composable
fun InterpreterDialog(onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    val x = LocalExtra.current
    val engine = c.engine
    val state by engine.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val turns = remember { mutableStateListOf<ITurn>() }
    var listening by remember { mutableStateOf<Side?>(null) }
    var auto by rememberSaveable { mutableStateOf(true) }
    var speakOut by rememberSaveable { mutableStateOf(true) }
    var hint by remember { mutableStateOf<String?>(null) }
    var big by remember { mutableStateOf<ITurn?>(null) }
    val listState = rememberLazyListState()

    // Giữ micro cho phiên dịch: chế độ chờ không nghe "HUY FAN" chen vào
    DisposableEffect(Unit) {
        engine.setMicHold(true)
        com.hankukko.huyfan.ForegroundTracker.setInterpreting(true)
        onDispose {
            com.hankukko.huyfan.ForegroundTracker.setInterpreting(false)
            engine.setMicHold(false)
            engine.cancel()
            c.speaker.stop()
        }
    }
    LaunchedEffect(turns.size, listening) {
        val n = turns.size + (if (listening != null) 1 else 0)
        if (n > 0) listState.animateScrollToItem(n - 1)
    }

    fun listen(side: Side) {
        if (listening != null) { engine.stopListening(); return }
        c.speaker.stop()
        hint = null
        listening = side
        engine.captureOnce(side.lang) { heard ->
            listening = null
            if (heard.isNullOrBlank()) {
                hint = "Không nghe thấy gì – bấm mic ${side.flag} để nói tiếp"
                return@captureOnce
            }
            val id = System.nanoTime()
            turns += ITurn(id, side, heard)
            val history = turns.filter { !it.working && it.error == null }
                .map { Interpreter.Line(it.side, it.fixed, it.translation) }
            scope.launch {
                val r = runCatching {
                    val (raw, _) = c.ai.askJson(Interpreter.SYSTEM, Interpreter.request(history, side, heard), Interpreter.schema(), live = true)
                    Interpreter.parse(raw, heard)
                }
                val i = turns.indexOfFirst { it.id == id }
                if (i < 0) return@launch
                r.onSuccess { res ->
                    turns[i] = turns[i].copy(
                        fixed = res.fixed, changed = res.changed, translation = res.translation, note = res.note, working = false,
                    )
                    val next = { if (auto) listen(side.other) }
                    // Bản dịch sang tiếng Hàn đọc trọn bằng giọng Hàn (kể cả số, chữ Latin) và ngược lại
                    if (speakOut && res.translation.isNotBlank()) c.speaker.speakAs(res.translation, korean = side == Side.VI) { next() } else next()
                }.onFailure { e ->
                    turns[i] = turns[i].copy(
                        working = false,
                        error = (e as? com.hankukko.huyfan.ai.AiException)?.userMessage ?: "Lỗi dịch: ${e.message}",
                    )
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().neonBackdrop(), color = androidx.compose.ui.graphics.Color.Transparent) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // ---------- Thanh trên ----------
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Đóng") }
                    Column(Modifier.weight(1f)) {
                        Text("Phiên dịch hội thoại", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("🇻🇳 ⇄ 🇰🇷 · tự sửa câu nghe nhầm", style = MaterialTheme.typography.labelMedium, color = x.muted)
                    }
                    if (turns.isNotEmpty()) {
                        IconButton(onClick = { engine.cancel(); c.speaker.stop(); listening = null; turns.clear() }) {
                            Icon(Icons.Rounded.DeleteSweep, "Bắt đầu lại", tint = x.muted)
                        }
                    }
                }
                // Tuỳ chọn
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OptionChip("Luân phiên", Icons.Rounded.SyncAlt, auto, Modifier.weight(1f)) { auto = it }
                    OptionChip("Đọc to", Icons.AutoMirrored.Rounded.VolumeUp, speakOut, Modifier.weight(1f)) { speakOut = it }
                }

                // ---------- Hội thoại ----------
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (turns.isEmpty() && listening == null) {
                        item { IntroCard() }
                    }
                    items(turns, key = { it.id }) { t ->
                        TurnBubble(t, onSpeak = { c.speaker.speakAs(it, korean = t.side == Side.VI) }, onBig = { big = t })
                    }
                    listening?.let { side ->
                        item(key = "live") { LiveBubble(side, state.partial) }
                    }
                }
                hint?.let {
                    Text(it, color = x.muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(4.dp))
                }

                // ---------- 2 nút mic ----------
                Row(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SideMic(Side.KO, KO_COLOR, listening == Side.KO, Modifier.weight(1f)) { listen(Side.KO) }
                    SideMic(Side.VI, VI_COLOR, listening == Side.VI, Modifier.weight(1f)) { listen(Side.VI) }
                }
            }
        }
    }
    big?.let { t -> BigText(t, onSpeak = { c.speaker.speakAs(t.translation, korean = t.side == Side.VI) }, onDismiss = { big = null }) }
}

@Composable
private fun OptionChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, modifier: Modifier, onChange: (Boolean) -> Unit) {
    val x = LocalExtra.current
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(x.card)
            .border(1.dp, x.cardBorder, RoundedCornerShape(16.dp))
            .clickable { onChange(!on) }
            .padding(start = 12.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (on) MaterialTheme.colorScheme.primary else x.muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1)
        Switch(checked = on, onCheckedChange = onChange, modifier = Modifier.scale(0.8f))
    }
}

@Composable
private fun IntroCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(KO_COLOR, VI_COLOR)))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Translate, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Nói chuyện với người Hàn", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Bấm 🇻🇳 rồi nói · ⛶ để phóng to cho người Hàn đọc.",
            color = Color.White.copy(alpha = 0.93f), style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "한국어로 말씀하시려면 🇰🇷 버튼을 눌러 주세요.",
            color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TurnBubble(t: ITurn, onSpeak: (String) -> Unit, onBig: () -> Unit) {
    val x = LocalExtra.current
    val mine = t.side == Side.VI
    val color = if (mine) VI_COLOR else KO_COLOR
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Text(
            "${t.side.flag} ${t.side.who}", style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Column(
            Modifier
                .widthIn(max = 330.dp)
                .clip(RoundedCornerShape(if (mine) 22.dp else 6.dp, if (mine) 6.dp else 22.dp, 22.dp, 22.dp))
                .background(color.copy(alpha = 0.10f))
                .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(if (mine) 22.dp else 6.dp, if (mine) 6.dp else 22.dp, 22.dp, 22.dp))
                .padding(14.dp)
        ) {
            // Câu gốc (đã sửa nếu nghe nhầm)
            Text(t.fixed.ifBlank { t.heard }, style = MaterialTheme.typography.bodyLarge)
            if (t.changed) {
                Text(
                    "✏️ nghe được: “${t.heard}”", style = MaterialTheme.typography.labelSmall, color = x.muted,
                    textDecoration = TextDecoration.LineThrough, modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            when {
                t.working -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = color)
                    Spacer(Modifier.width(8.dp))
                    Text("Đang sửa câu & dịch…", color = x.muted, style = MaterialTheme.typography.bodySmall)
                }
                t.error != null -> Text(t.error, color = Brand.Red, style = MaterialTheme.typography.bodySmall)
                else -> {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            t.translation, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            color = color, modifier = Modifier.weight(1f, fill = false).clickable { onSpeak(t.translation) },
                        )
                    }
                    if (t.note.isNotBlank()) {
                        Text("💡 ${t.note}", style = MaterialTheme.typography.labelSmall, color = x.muted, modifier = Modifier.padding(top = 4.dp))
                    }
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallAction(Icons.AutoMirrored.Rounded.VolumeUp, "Nghe lại", color) { onSpeak(t.translation) }
                        SmallAction(Icons.Rounded.Fullscreen, if (mine) "Cho người Hàn xem" else "Phóng to", color, onBig)
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LiveBubble(side: Side, partial: String) {
    val x = LocalExtra.current
    val mine = side == Side.VI
    val color = if (mine) VI_COLOR else KO_COLOR
    val pulse by rememberInfiniteTransition(label = "live").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "a",
    )
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Brand.Red.copy(alpha = pulse)))
            Spacer(Modifier.width(6.dp))
            Text(
                if (mine) "Đang nghe tiếng Việt…" else "한국어를 듣고 있어요…",
                style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold,
            )
        }
        Box(
            Modifier
                .widthIn(max = 330.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(1.5.dp, color.copy(alpha = pulse * 0.8f), RoundedCornerShape(22.dp))
                .padding(14.dp)
        ) {
            Text(
                partial.ifBlank { if (mine) "Nói đi, tui đang nghe…" else "말씀하세요…" },
                style = MaterialTheme.typography.bodyLarge, fontStyle = if (partial.isBlank()) FontStyle.Italic else FontStyle.Normal,
                color = if (partial.isBlank()) x.muted else MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun SideMic(side: Side, color: Color, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "mic").animateFloat(
        1f, 1.04f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "s",
    )
    Row(
        modifier
            .height(64.dp)
            .scale(if (active) pulse else 1f)
            .clip(RoundedCornerShape(22.dp))
            .background(if (active) Brush.linearGradient(listOf(Brand.Red, Brand.Violet)) else Brush.linearGradient(listOf(color, color.copy(alpha = 0.75f))))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(if (active) Icons.Rounded.Stop else Icons.Rounded.Mic, null, tint = Color.White, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text("${side.flag} ${side.label}", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                if (active) (if (side == Side.VI) "Đang nghe…" else "듣는 중…") else (if (side == Side.VI) "Bạn nói" else "상대방 말하기"),
                color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall, maxLines = 1,
            )
        }
    }
}

/** Phóng to bản dịch cho người đối diện đọc; lật 180° khi ngồi đối diện. */
@Composable
private fun BigText(t: ITurn, onSpeak: () -> Unit, onDismiss: () -> Unit) =
    BigTranslation(t.translation, t.fixed, toKorean = t.side == Side.VI, onSpeak = onSpeak, onDismiss = onDismiss)

/** Phóng to bản dịch (dùng chung cho dịch hội thoại & dịch tự động). */
@Composable
fun BigTranslation(translation: String, original: String, toKorean: Boolean, onSpeak: () -> Unit, onDismiss: () -> Unit) {
    var flipped by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.White)
                .clickable(onClick = onDismiss)
        ) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(28.dp)
                    .rotate(if (flipped) 180f else 0f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    translation, color = Color(0xFF111827), fontSize = if (translation.length > 80) 30.sp else 40.sp,
                    lineHeight = if (translation.length > 80) 40.sp else 52.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Text(original, color = Color(0xFF6B7280), fontSize = 18.sp, textAlign = TextAlign.Center)
            }
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                BigAction(Icons.AutoMirrored.Rounded.VolumeUp, if (toKorean) "다시 듣기" else "Nghe lại", onSpeak)
                BigAction(Icons.Rounded.ScreenRotation, "Lật", { flipped = !flipped })
                BigAction(Icons.Rounded.Close, "Đóng", onDismiss)
            }
        }
    }
}

@Composable
private fun BigAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color(0xFF111827))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}
