package com.hankukko.huyfan.ui.screens

import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.text.withStyle
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hankukko.huyfan.ui.neonBackdrop
import com.hankukko.huyfan.assistant.Interpreter
import com.hankukko.huyfan.assistant.Interpreter.Side
import com.hankukko.huyfan.container
import com.hankukko.huyfan.ui.theme.Brand
import com.hankukko.huyfan.ui.theme.LocalExtra
import com.hankukko.huyfan.voice.VadRecorder
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** 1 câu trong dịch tự động. */
private data class AutoLine(
    val id: Long,
    val side: Side? = null,
    val heard: String = "",
    val fixed: String = "",
    val changed: Boolean = false,
    val translation: String = "",
    val stage: Stage = Stage.HEARING,
    val error: String? = null,
) {
    /** LIVE = đang nói dở (chữ tạm, tự cập nhật) · HEARING = vừa nói xong, đang nhận dạng chính xác */
    enum class Stage { LIVE, HEARING, TRANSLATING, DONE }
}

/**
 * Dịch tự động kiểu "dịch trực tiếp" của Google Dịch: luôn nghe, tự nhận ra tiếng Việt hay tiếng Hàn,
 * nghe xong câu nào dịch câu đó (không đọc to, không phải bấm chọn ngôn ngữ).
 */
@Composable
fun AutoInterpreterDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val x = LocalExtra.current
    val scope = rememberCoroutineScope()
    val lines = remember { mutableStateListOf<AutoLine>() }
    var listening by remember { mutableStateOf(true) }
    var level by remember { mutableFloatStateOf(0f) }
    var speaking by remember { mutableStateOf(false) }
    var big by remember { mutableStateOf<Block?>(null) }
    var micError by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    // Câu đang nói dở: chữ hiện ngay, tự sửa khi nghe thêm
    var live by remember { mutableStateOf<AutoLine?>(null) }
    var liveBusy by remember { mutableStateOf(false) }
    var fastTranscribe by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { fastTranscribe = c.ai.hasFastTranscribe() }
    // Nhận dạng + dịch tối đa 3 câu song song để theo kịp người nói nhanh
    val gate = remember { Semaphore(3) }
    // App đang đọc to bản dịch → micro "bịt tai" để không nghe lại tiếng loa của chính mình
    val ttsOn by c.speaker.speaking.collectAsState()
    // Những câu app vừa đọc to (lớp chặn thứ 2: lỡ còn lọt tiếng vang thì cũng không dịch lại)
    val spoken = remember { mutableListOf<Pair<String, Long>>() }
    // Lúc loa vừa tắt: chỉ câu bắt đầu ngay sau đó mới có thể là tiếng vang
    val ttsEndAt = remember { longArrayOf(0L) }
    fun speak(text: String, korean: Boolean) {
        spoken += text to System.currentTimeMillis()
        c.speaker.speakAs(text, korean)
    }

    fun update(id: Long, f: (AutoLine) -> AutoLine) {
        val i = lines.indexOfFirst { it.id == id }
        if (i >= 0) lines[i] = f(lines[i])
    }

    fun handle(pcm: ByteArray, ms: Long, speechMs: Long) {
        // Thời điểm người nói bắt đầu câu này
        val startedAt = System.currentTimeMillis() - ms
        val id = System.nanoTime()
        val seed = live
        live = null
        scope.launch {
            lines += AutoLine(id, side = seed?.side, heard = seed?.heard.orEmpty())
            gate.withPermit {
                val heard = runCatching { c.ai.transcribeAuto(VadRecorder.wav(pcm), speechMs = speechMs) }
                    .getOrElse { e ->
                        update(id) { it.copy(stage = AutoLine.Stage.DONE, error = (e as? com.hankukko.huyfan.ai.AiException)?.userMessage ?: "Lỗi nhận dạng: ${e.message}") }
                        return@withPermit
                    }
                if (heard.lang.isBlank() || heard.text.isBlank()) {
                    lines.removeAll { it.id == id } // chỉ là tiếng ồn
                    return@withPermit
                }
                val now = System.currentTimeMillis()
                spoken.removeAll { now - it.second > 30_000 }
                // Chỉ câu bắt đầu trong ~2 s sau khi loa tắt mới bị nghi là tiếng vang –
                // người thật nói lại đúng câu đó sau này vẫn được dịch bình thường
                val nearTts = ttsOn || startedAt < ttsEndAt[0] + 2_000
                if (nearTts && spoken.any { echoOf(heard.text, it.first) }) {
                    android.util.Log.i("HuyFanAI", "bỏ tiếng vang của loa: '${heard.text.take(40)}'")
                    lines.removeAll { it.id == id }
                    return@withPermit
                }
                val side = if (heard.lang == "ko") Side.KO else Side.VI
                update(id) { it.copy(side = side, heard = heard.text, stage = AutoLine.Stage.TRANSLATING) }
                val history = lines.filter { it.stage == AutoLine.Stage.DONE && it.error == null && it.side != null }
                    .map { Interpreter.Line(it.side!!, it.fixed, it.translation) }
                runCatching {
                    val (raw, _) = c.ai.askJson(Interpreter.SYSTEM, Interpreter.request(history, side, heard.text), Interpreter.schema(), live = true)
                    Interpreter.parse(raw, heard.text)
                }.onSuccess { r ->
                    update(id) { it.copy(fixed = r.fixed, changed = r.changed, translation = r.translation, stage = AutoLine.Stage.DONE) }
                }.onFailure { e ->
                    update(id) { it.copy(stage = AutoLine.Stage.DONE, error = (e as? com.hankukko.huyfan.ai.AiException)?.userMessage ?: "Lỗi dịch: ${e.message}") }
                }
            }
        }
    }

    lateinit var recorder: VadRecorder

    /** Chữ tạm khi đang nói: tối đa 1 yêu cầu cùng lúc (tiết kiệm hạn mức Groq). */
    fun partial(seq: Int, pcm: ByteArray) {
        if (!fastTranscribe || liveBusy) return
        liveBusy = true
        scope.launch {
            val heard = runCatching { c.ai.transcribeAuto(VadRecorder.wav(pcm), quick = true) }.getOrNull()
            liveBusy = false
            // Câu đã chốt trong lúc chờ → bỏ kết quả cũ
            if (heard == null || heard.text.isBlank() || recorder.seq != seq) return@launch
            live = AutoLine(-1L - seq, side = if (heard.lang == "ko") Side.KO else Side.VI, heard = heard.text, stage = AutoLine.Stage.LIVE)
        }
    }

    recorder = remember {
        VadRecorder(
            scope,
            onSegment = { pcm, ms, speechMs -> scope.launch { handle(pcm, ms, speechMs) } },
            onLevel = { l, s -> level = l; speaking = s },
            onPartial = { seq, pcm -> scope.launch { partial(seq, pcm) } },
        )
    }

    LaunchedEffect(ttsOn) {
        recorder.mute(ttsOn)
        if (ttsOn) live = null else ttsEndAt[0] = System.currentTimeMillis()
    }

    // Mở là nghe ngay; giữ micro (chế độ chờ không chen vào); ẩn app → tạm dừng
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(Unit) {
        c.engine.cancel()
        c.engine.setMicHold(true)
        com.hankukko.huyfan.ForegroundTracker.setInterpreting(true)
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> recorder.paused = true
                Lifecycle.Event.ON_START -> recorder.paused = !listening
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            recorder.stop()
            c.speaker.stop()
            c.engine.setMicHold(false)
            com.hankukko.huyfan.ForegroundTracker.setInterpreting(false)
        }
    }
    LaunchedEffect(Unit) {
        // Chờ chế độ chờ nhả micro rồi mới bắt đầu ghi
        kotlinx.coroutines.delay(250)
        if (!recorder.start()) micError = "Không mở được micro – thử đóng app khác đang dùng micro rồi mở lại"
    }
    LaunchedEffect(listening) { recorder.paused = !listening }
    // Theo dõi câu mới nhất: đang ở cuối thì tự trôi xuống; đang kéo lên đọc thì hiện nút "↓ Câu mới"
    var follow by remember { mutableStateOf(true) }
    val atBottom by remember { androidx.compose.runtime.derivedStateOf { !listState.canScrollForward } }
    LaunchedEffect(listState.isScrollInProgress, atBottom) {
        if (listState.isScrollInProgress) follow = atBottom
        else if (atBottom) follow = true
    }
    val contentKey = lines.sumOf { it.translation.length + it.heard.length + it.stage.ordinal } + lines.size * 1000 + (live?.heard?.length ?: -1)
    LaunchedEffect(contentKey) {
        if (follow && (lines.isNotEmpty() || live != null)) {
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) listState.animateScrollToItem(total - 1, scrollOffset = 100_000)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().neonBackdrop(), color = androidx.compose.ui.graphics.Color.Transparent) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // ---------- Thanh trên ----------
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Quay lại") }
                    Column(Modifier.weight(1f)) {
                        Text("Dịch tự động", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("🇻🇳 ⇄ ${com.hankukko.huyfan.Lang.current.flag} · tự nhận ngôn ngữ", style = MaterialTheme.typography.labelMedium, color = x.muted)
                    }
                    if (lines.isNotEmpty()) {
                        IconButton(onClick = { lines.clear() }) { Icon(Icons.Rounded.DeleteSweep, "Xoá màn hình", tint = x.muted) }
                    }
                }

                // ---------- Các câu đã dịch ----------
                Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (lines.isEmpty()) {
                        item {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Brand.gradient)
                                    .padding(18.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.GraphicEq, null, tint = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Cứ nói, HUY FAN tự dịch", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Nói xong một câu, ngừng chút là có bản dịch.",
                                    color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodySmall,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(if (com.hankukko.huyfan.Lang.isEn) "Just speak naturally – I’ll translate for you." else "편하게 말씀하세요. 자동으로 번역해 드려요.", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    items(blocksOf(lines + listOfNotNull(live)), key = { it.key }) { b ->
                        BlockCard(
                            b,
                            onSpeak = { speak(b.translation, korean = b.side == Side.VI) },
                            onCopy = {
                                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                                cm?.setPrimaryClip(android.content.ClipData.newPlainText("HUY FAN", b.translation))
                                android.widget.Toast.makeText(context, "Đã sao chép bản dịch", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onBig = { big = b },
                        )
                    }
                }
                if (!follow && (lines.isNotEmpty() || live != null)) {
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable {
                                follow = true
                                scope.launch {
                                    val total = listState.layoutInfo.totalItemsCount
                                    if (total > 0) listState.animateScrollToItem(total - 1, scrollOffset = 100_000)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("↓  Câu mới", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                }
                }

                // ---------- Trạng thái nghe ----------
                Column(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val glow by animateFloatAsState(if (listening) 0.6f + level * 0.8f else 0.4f, tween(120), label = "glow")
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.height(56.dp).fillMaxWidth()) {
                        Box(
                            Modifier
                                .size(width = 220.dp, height = 18.dp)
                                .scale(scaleX = 0.6f + glow * 0.5f, scaleY = 1f)
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Transparent, Brand.Teal.copy(alpha = if (listening) 0.7f else 0.2f), Brand.Blue.copy(alpha = if (listening) 0.8f else 0.2f), Color.Transparent)
                                    )
                                )
                        )
                    }
                    Text(
                        micError ?: when {
                            !listening -> "Đã tạm dừng"
                            ttsOn -> "Đang đọc to · tạm không nghe"
                            speaking -> "Đang nghe…"
                            lines.any { it.stage != AutoLine.Stage.DONE } -> "Đang dịch…"
                            else -> "Đang chờ người nói…"
                        },
                        color = if (micError != null) Brand.Red else MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(26.dp))
                            .background(if (listening) Brand.Red else Brand.Blue)
                            .clickable { listening = !listening }
                            .padding(horizontal = 34.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (listening) Icons.Rounded.Pause else Icons.Rounded.Mic, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(if (listening) "Tạm dừng" else "Nghe tiếp", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    big?.let { b ->
        BigTranslation(
            b.translation, b.original, toKorean = b.side == Side.VI,
            onSpeak = { speak(b.translation, korean = b.side == Side.VI) },
            onDismiss = { big = null },
        )
    }
}

/** Các câu liền nhau cùng 1 ngôn ngữ gom thành 1 khối (giống dịch trực tiếp của Google Dịch). */
private data class Block(val key: Long, val side: Side?, val lines: List<AutoLine>) {
    val done: List<AutoLine> get() = lines.filter { it.stage == AutoLine.Stage.DONE && it.error == null }
    val translation: String get() = done.joinToString("\n") { sentence(it.translation) }
    val original: String get() = done.joinToString("\n") { sentence(it.fixed.ifBlank { it.heard }) }
}

/** Ngừng nói = hết câu → thêm dấu chấm nếu thiếu. */
private fun sentence(t: String): String {
    val s = t.trim()
    return if (s.isEmpty() || s.last() in ".!?…。~") s else "$s."
}

/** Câu vừa nghe có phải chính tiếng loa của app (câu dịch vừa đọc to) không – so khớp gần đúng. */
private fun echoOf(heard: String, said: String): Boolean {
    fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
    val a = norm(heard)
    val b = norm(said)
    if (a.length < 2 || b.isEmpty()) return false
    if (b.contains(a) || a.contains(b)) return true
    fun grams(s: String) = s.windowed(2).toHashSet()
    val ga = grams(a)
    val gb = grams(b)
    if (ga.isEmpty() || gb.isEmpty()) return false
    // Phần lớn câu nghe được nằm trong câu đã đọc → là tiếng vang
    return ga.count { it in gb }.toDouble() / ga.size >= 0.6
}

private fun blocksOf(lines: List<AutoLine>): List<Block> {
    val out = mutableListOf<Block>()
    for (l in lines) {
        val last = out.lastOrNull()
        // Câu chưa rõ ngôn ngữ (đang nghe) tạm nối vào khối hiện tại
        if (last != null && (l.side == null || last.side == null || l.side == last.side)) {
            out[out.lastIndex] = last.copy(side = last.side ?: l.side, lines = last.lines + l)
        } else {
            out += Block(l.id, l.side, listOf(l))
        }
    }
    return out
}

@Composable
private fun BlockCard(b: Block, onSpeak: () -> Unit, onCopy: () -> Unit, onBig: () -> Unit) {
    val x = LocalExtra.current
    val color = when (b.side) {
        Side.VI -> Brand.Blue
        Side.KO -> Brand.Teal
        null -> x.muted
    }
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(24.dp))
            .background(x.card)
            .border(1.dp, color.copy(alpha = 0.32f), RoundedCornerShape(24.dp))
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)
    ) {
        // Tiêu đề: chiều dịch + thao tác
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (b.side) { Side.VI -> "Việt"; Side.KO -> if (com.hankukko.huyfan.Lang.isEn) "Anh" else "Hàn"; null -> "…" },
                    style = MaterialTheme.typography.labelLarge, color = x.muted,
                )
                Text("  →  ", style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold)
                Text(
                    when (b.side) { Side.VI -> if (com.hankukko.huyfan.Lang.isEn) "Anh" else "Hàn"; Side.KO -> "Việt"; null -> "…" },
                    style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold,
                )
            }
            if (b.done.isNotEmpty()) {
                RoundAction(Icons.Rounded.ContentCopy, "Sao chép", x.muted, onCopy)
                RoundAction(Icons.AutoMirrored.Rounded.VolumeUp, "Nghe", color, onSpeak)
                RoundAction(Icons.Rounded.Fullscreen, "Phóng to", x.muted, onBig)
            }
        }
        Spacer(Modifier.height(6.dp))
        // Mỗi câu 1 dòng: xong → chỉ bản dịch; đang dịch → song ngữ
        b.lines.forEach { l -> SentenceRow(l, color) }
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 6.dp)
            .size(38.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = tint, modifier = Modifier.size(19.dp)) }
}

@Composable
private fun SentenceRow(l: AutoLine, color: Color) {
    val x = LocalExtra.current
    var showOriginal by remember(l.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(end = 10.dp, top = 4.dp, bottom = 4.dp)
            .animateContentSize()
    ) {
        when {
            l.error != null -> Text(l.error, color = Brand.Red, style = MaterialTheme.typography.bodySmall)
            l.stage == AutoLine.Stage.LIVE -> {
                val blink by androidx.compose.animation.core.rememberInfiniteTransition(label = "caret").animateFloat(
                    0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(500), androidx.compose.animation.core.RepeatMode.Reverse), label = "c",
                )
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        append(l.heard)
                        withStyle(androidx.compose.ui.text.SpanStyle(color = color.copy(alpha = blink))) { append(" ▍") }
                    },
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.titleMedium,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                )
                Text("đang nghe…", color = color, style = MaterialTheme.typography.labelSmall)
            }
            l.stage == AutoLine.Stage.HEARING && l.heard.isNotBlank() -> {
                // Vừa nói xong: giữ chữ tạm, đang nghe lại chính xác + sửa câu
                Text(sentence(l.heard), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp, color = color)
                    Spacer(Modifier.width(6.dp))
                    Text("đang sửa câu…", color = color, style = MaterialTheme.typography.labelMedium)
                }
            }
            l.stage == AutoLine.Stage.HEARING -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = color)
                Spacer(Modifier.width(8.dp))
                Text("…", color = x.muted, style = MaterialTheme.typography.titleLarge)
            }
            l.stage == AutoLine.Stage.TRANSLATING -> {
                // Đang dịch: song ngữ – câu gốc + chỗ chờ bản dịch
                Text(
                    sentence(l.heard), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Box(Modifier.width(3.dp).height(18.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp, color = color)
                    Spacer(Modifier.width(6.dp))
                    Text("đang dịch…", color = color, style = MaterialTheme.typography.labelMedium)
                }
            }
            else -> {
                // Xong: chỉ hiện bản dịch (chạm để xem lại câu gốc)
                Text(
                    sentence(l.translation),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.clickable { showOriginal = !showOriginal },
                )
                if (showOriginal) {
                    Text(sentence(l.fixed.ifBlank { l.heard }), color = x.muted, style = MaterialTheme.typography.bodyMedium)
                    if (l.changed) {
                        Text(
                            "✏️ nghe được: “${l.heard}”", color = x.muted, style = MaterialTheme.typography.labelSmall,
                            textDecoration = TextDecoration.LineThrough,
                        )
                    }
                }
            }
        }
    }
}
