package com.hankukko.huyfan.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hankukko.huyfan.ui.neonBackdrop
import com.hankukko.huyfan.assistant.LANG_KO
import com.hankukko.huyfan.assistant.Phase
import com.hankukko.huyfan.data.RuleLinks
import com.hankukko.huyfan.data.VocabEntity
import com.hankukko.huyfan.data.isDue
import com.hankukko.huyfan.data.latestPerWord
import com.hankukko.huyfan.ui.MainViewModel
import com.hankukko.huyfan.ui.components.MicButton
import com.hankukko.huyfan.ui.theme.Brand
import com.hankukko.huyfan.ui.theme.LocalExtra
import com.hankukko.huyfan.util.Pronounce

private data class PracticeItem(val ko: String, val vi: String, val romaja: String, val word: VocabEntity)

/** Chọn bài luyện: từ đến hạn ôn + từ chưa thuộc trước, mỗi phiên tối đa [limit] mục. */
private fun buildItems(all: List<VocabEntity>, sentences: Boolean, limit: Int = 12): List<PracticeItem> {
    val words = all.latestPerWord()
        .sortedWith(compareByDescending<VocabEntity> { it.isDue }.thenBy { it.mastery }.thenByDescending { it.createdAt })
    val items = if (sentences) {
        words.filter { it.exampleKo.isNotBlank() }.map { PracticeItem(it.exampleKo, it.exampleVi, "", it) }
    } else {
        words.map { PracticeItem(it.ko, it.vi, it.romaja, it) }
    }
    // Lấy nhóm cần ôn trước rồi xáo nhẹ cho đỡ nhàm
    return items.take(limit * 2).shuffled().sortedByDescending { it.word.isDue }.take(limit)
}

@Composable
fun PracticeDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val x = LocalExtra.current
    val all by vm.vocab.collectAsStateWithLifecycle()
    val engine by vm.engine.state.collectAsStateWithLifecycle()
    var sentences by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    var retryList by remember { mutableStateOf<List<PracticeItem>?>(null) }
    val items = remember(sentences, round, all.isNotEmpty()) { retryList ?: buildItems(all, sentences) }
    var index by remember(items) { mutableIntStateOf(0) }
    val best = remember(items) { mutableStateMapOf<Int, Int>() }
    var result by remember(items, index) { mutableStateOf<Pronounce.Result?>(null) }
    var listening by remember(items, index) { mutableStateOf(false) }
    var noHear by remember(items, index) { mutableStateOf(false) }
    var ruleOpen by remember { mutableStateOf<String?>(null) }

    // Tự đọc mẫu khi sang thẻ mới
    LaunchedEffect(items, index) { items.getOrNull(index)?.let { vm.speakKorean(it.ko) } }
    // Rời màn → dừng nghe
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.engine.cancel() } }

    fun listen() {
        val it = items.getOrNull(index) ?: return
        if (listening) { vm.engine.stopListening(); return }
        listening = true
        noHear = false
        vm.engine.captureOnce(LANG_KO) { heard ->
            listening = false
            if (heard == null) { noHear = true; return@captureOnce }
            val r = Pronounce.score(it.ko, heard)
            result = r
            best[index] = maxOf(best[index] ?: 0, r.score)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().neonBackdrop(), color = androidx.compose.ui.graphics.Color.Transparent) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Đóng") }
                    Column(Modifier.weight(1f)) {
                        Text("Luyện phát âm", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Nghe mẫu → bấm mic đọc → xem điểm", style = MaterialTheme.typography.labelMedium, color = x.muted)
                    }
                }
                Segmented(
                    listOf(false, true), sentences, { if (it) "Câu mẫu" else "Từ vựng" },
                    { s -> retryList = null; sentences = s; round++ },
                    modifier = Modifier.padding(horizontal = 0.dp),
                )
                Spacer(Modifier.height(8.dp))
                when {
                    items.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            if (sentences) "Sổ chưa có câu mẫu nào. Hỏi HUY FAN vài từ trước nha!" else "Sổ chưa có từ nào. Hỏi HUY FAN vài từ trước nha!",
                            color = x.muted, textAlign = TextAlign.Center,
                        )
                    }
                    index >= items.size -> PracticeSummary(
                        items, best,
                        onRetryWeak = {
                            val weak = items.filterIndexed { i, _ -> (best[i] ?: 0) < 75 }
                            retryList = weak.ifEmpty { null }
                            round++
                        },
                        onNew = { retryList = null; round++ },
                        onDone = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    else -> {
                        val item = items[index]
                        val rules = remember(item.ko) { RuleLinks.soundRules(item.ko) }
                        LinearProgressIndicator(
                            progress = { (index + 1f) / items.size },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            trackColor = x.cardBorder,
                        )
                        Text("${index + 1} / ${items.size}", style = MaterialTheme.typography.labelMedium, color = x.muted, modifier = Modifier.padding(top = 6.dp))
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Thẻ chữ mẫu
                            Column(
                                Modifier
                                    .padding(top = 10.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Brand.Teal, Brand.Blue)))
                                    .padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                val r = result
                                Text(
                                    if (r == null) buildAnnotatedString { append(item.ko) } else buildAnnotatedString {
                                        r.marks.forEach { (c, m) ->
                                            when (m) {
                                                Pronounce.Mark.OK -> withStyle(SpanStyle(color = Color(0xFFB9FBC0))) { append(c) }
                                                Pronounce.Mark.CLOSE -> withStyle(SpanStyle(color = Color(0xFFFFE08A))) { append(c) }
                                                Pronounce.Mark.MISS -> withStyle(SpanStyle(color = Color(0xFFFFB3C1), textDecoration = TextDecoration.Underline)) { append(c) }
                                                Pronounce.Mark.NEUTRAL -> withStyle(SpanStyle(color = Color.White)) { append(c) }
                                            }
                                        }
                                    },
                                    color = Color.White, fontSize = if (sentences) 26.sp else 42.sp, fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center, lineHeight = if (sentences) 36.sp else 50.sp,
                                )
                                if (item.romaja.isNotBlank()) {
                                    Text(item.romaja, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(item.vi, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    Modifier
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.22f))
                                        .clickable { vm.speakKorean(item.ko) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Nghe mẫu", color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (rules.isNotEmpty()) {
                                Text(
                                    "Lưu ý khi đọc", style = MaterialTheme.typography.labelMedium, color = x.muted,
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                                )
                                RuleChips(rules, { ruleOpen = it }, Modifier.fillMaxWidth())
                            }
                            // Kết quả
                            val r = result
                            AnimatedVisibility(r != null) {
                                if (r != null) ScorePanel(r)
                            }
                            when {
                                listening -> Text(
                                    engine.partial.ifBlank { "Đang nghe… đọc to, rõ nha" },
                                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 16.dp), textAlign = TextAlign.Center,
                                )
                                noHear -> Text("Chưa nghe rõ, bấm mic đọc lại nha", color = Brand.Red, modifier = Modifier.padding(top = 16.dp))
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { result = null; noHear = false },
                                modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(18.dp),
                                enabled = result != null,
                            ) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(4.dp)); Text("Thử lại") }
                            MicButton(listening = listening, onClick = { listen() }, size = 68.dp)
                            Button(
                                onClick = { vm.engine.cancel(); index++ },
                                modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(18.dp),
                            ) {
                                Text(if (result == null) "Bỏ qua" else "Tiếp")
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Rounded.SkipNext, null)
                            }
                        }
                    }
                }
            }
        }
    }
    ruleOpen?.let { id -> RuleSheet(id, onSpeak = { vm.speakKorean(it) }, onAsk = { ruleOpen = null }, onDismiss = { ruleOpen = null }) }
}

@Composable
private fun ScorePanel(r: Pronounce.Result) {
    val x = LocalExtra.current
    val color = when {
        r.score >= 90 -> Brand.Green
        r.score >= 75 -> Brand.Teal
        r.score >= 55 -> Brand.Amber
        else -> Brand.Red
    }
    Column(
        Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${r.score}", fontSize = 44.sp, fontWeight = FontWeight.Black, color = color)
            Text("%", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(start = 2.dp, top = 12.dp))
            Spacer(Modifier.width(12.dp))
            repeat(3) { i ->
                Icon(
                    if (i < r.stars) Icons.Rounded.Star else Icons.Rounded.StarBorder, null,
                    tint = if (i < r.stars) Brand.Amber else x.muted, modifier = Modifier.size(30.dp),
                )
            }
        }
        Text(
            when {
                r.score >= 90 -> "Chuẩn như người Hàn! 완벽해요 👏"
                r.score >= 75 -> "Tốt lắm! Chỉnh chút xíu chỗ màu vàng/đỏ là đẹp"
                r.score >= 55 -> "Gần đúng rồi – nghe mẫu lại và chú ý âm màu đỏ"
                else -> "Chưa khớp lắm – nghe mẫu chậm rồi đọc từng âm tiết nha"
            },
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text("Máy nghe được: “${r.heard}”", style = MaterialTheme.typography.bodySmall, color = x.muted, textAlign = TextAlign.Center)
        Text("Xanh: đúng · Vàng: gần đúng · Đỏ gạch chân: sai/thiếu", style = MaterialTheme.typography.labelSmall, color = x.muted)
    }
}

@Composable
private fun PracticeSummary(
    items: List<PracticeItem>,
    best: Map<Int, Int>,
    onRetryWeak: () -> Unit,
    onNew: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier,
) {
    val x = LocalExtra.current
    val done = best.size
    val avg = if (done == 0) 0 else best.values.sum() / done
    val weak = items.withIndex().filter { (best[it.index] ?: 0) < 75 }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(Icons.Rounded.EmojiEvents, null, tint = Brand.Amber, modifier = Modifier.size(84.dp))
        Text(if (done == 0) "Chưa đọc thẻ nào" else "Điểm trung bình $avg%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Đã đọc $done/${items.size} thẻ", color = x.muted)
        Spacer(Modifier.height(16.dp))
        if (weak.isNotEmpty()) {
            Text("Cần luyện thêm", style = MaterialTheme.typography.labelLarge, color = x.muted, modifier = Modifier.fillMaxWidth())
            weak.forEach { (i, it) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(x.card)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(it.ko, fontWeight = FontWeight.Bold)
                        Text(it.vi, style = MaterialTheme.typography.bodySmall, color = x.muted, maxLines = 1)
                    }
                    Text(best[i]?.let { s -> "$s%" } ?: "chưa đọc", color = Brand.Red, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (weak.isNotEmpty()) {
                OutlinedButton(onClick = onRetryWeak) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Luyện lại ${weak.size} thẻ") }
            } else {
                OutlinedButton(onClick = onNew) { Icon(Icons.Rounded.GraphicEq, null); Spacer(Modifier.width(6.dp)); Text("Lượt mới") }
            }
            Button(onClick = onDone) { Text("Xong") }
        }
    }
}
