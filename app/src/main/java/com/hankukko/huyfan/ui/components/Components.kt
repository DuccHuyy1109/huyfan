package com.hankukko.huyfan.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.res.painterResource
import com.hankukko.huyfan.R
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hankukko.huyfan.ai.GrammarItem
import com.hankukko.huyfan.ai.VocabItem
import com.hankukko.huyfan.data.ChatMessage
import com.hankukko.huyfan.ui.theme.Brand
import com.hankukko.huyfan.ui.theme.BrandTextStyle
import com.hankukko.huyfan.ui.theme.LocalExtra
import com.hankukko.huyfan.util.Dates

@Composable
fun BrandMark(size: Dp = 36.dp) {
    Box(
        Modifier
            .size(size)
            .shadow(size * 0.35f, RoundedCornerShape(size * 0.32f), ambientColor = Brand.Pink, spotColor = Brand.Violet)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(Brand.gradient),
        contentAlignment = Alignment.Center,
    ) {
        // Dùng đúng hình chữ ㅎ của icon app (vector 108dp có lề an toàn → phóng to để lấp khung)
        Image(
            painterResource(R.drawable.ic_launcher_foreground), null,
            modifier = Modifier.requiredSize(size * 1.55f),
        )
    }
}

@Composable
fun BrandTitle(subtitle: String? = null, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandMark()
        Spacer(Modifier.width(10.dp))
        Column {
            Text("HUY FAN", style = MaterialTheme.typography.titleLarge.merge(BrandTextStyle))
            if (subtitle != null) {
                Text(
                    subtitle.uppercase(), color = LocalExtra.current.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val x = LocalExtra.current
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(x.card)
            .border(1.dp, x.neonBorder, RoundedCornerShape(22.dp))
            .padding(padding),
        content = content,
    )
}

@Composable
fun Pill(text: String, icon: ImageVector? = null, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Nút mic tròn gradient – nhấp nháy khi đang nghe. */
@Composable
fun MicButton(listening: Boolean, onClick: () -> Unit, size: Dp = 60.dp, enabled: Boolean = true) {
    val inf = rememberInfiniteTransition(label = "mic")
    val pulse by inf.animateFloat(1f, 1.2f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    val spin by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(if (listening) 1300 else 4800, easing = androidx.compose.animation.core.LinearEasing)),
        label = "spin",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size + 8.dp)) {
        if (listening) {
            Box(
                Modifier
                    .size(size)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(Brand.Pink.copy(alpha = 0.28f))
            )
        }
        // Vòng neon hồng → tím → cyan xoay quanh nút
        androidx.compose.foundation.Canvas(Modifier.size(size + 7.dp).alpha(if (enabled) 1f else 0.4f)) {
            val ring = 2.2.dp.toPx()
            rotate(spin) {
                drawCircle(
                    Brush.sweepGradient(listOf(Brand.Pink, Brand.Violet, Brand.Cyan, Color.Transparent, Brand.Pink)),
                    radius = this.size.minDimension / 2f - ring / 2f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(ring),
                )
            }
        }
        Box(
            Modifier
                .size(size)
                .shadow(18.dp, CircleShape, ambientColor = Brand.Pink, spotColor = Brand.Violet)
                .clip(CircleShape)
                .background(if (listening) Brand.warm else Brand.gradient)
                .alpha(if (enabled) 1f else 0.5f)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (listening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                contentDescription = if (listening) "Dừng" else "Nói",
                tint = Color.White,
                modifier = Modifier.size(size * 0.45f),
            )
        }
    }
}

@Composable
fun TypingDots(color: Color = MaterialTheme.colorScheme.primary) {
    val inf = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val a by inf.animateFloat(
                0.25f, 1f,
                infiniteRepeatable(tween(520, delayMillis = i * 160), RepeatMode.Reverse),
                label = "d$i",
            )
            Box(
                Modifier
                    .size(8.dp)
                    .alpha(a)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Bảng từ vựng: Tiếng Việt | Tiếng Hàn | Gốc từ (Hán Hàn / từ mượn) | Câu mẫu
// ---------------------------------------------------------------------------------------------

private const val W_VI = 0.95f
private const val W_KO = 1.15f
private const val W_HAN = 0.8f
private const val W_EX = 1.55f

@Composable
fun VocabTable(
    items: List<VocabItem>,
    savedCount: Int,
    onSpeakKorean: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: ((Int) -> Unit)? = null,
    savedFlags: List<Boolean>? = null,
    onAdd: ((Int) -> Unit)? = null,
    onHanja: ((VocabItem) -> Unit)? = null,
) {
    val x = LocalExtra.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(x.card)
            .border(1.dp, x.cardBorder, shape)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Brand.Cyan.copy(alpha = 0.16f), Brand.Violet.copy(alpha = 0.16f), Color.Transparent)))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("TỪ VỰNG", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            SavedPill(savedFlags, savedCount)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(x.tableHeader)
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            HeaderCell("Tiếng Việt", W_VI)
            HeaderCell("Tiếng Hàn", W_KO)
            HeaderCell("Gốc từ", W_HAN)
            HeaderCell("Câu mẫu", W_EX)
        }
        items.forEachIndexed { i, it ->
            Column(Modifier.background(if (i % 2 == 1) x.tableStripe else Color.Transparent)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(W_VI).padding(end = 6.dp)) {
                        Text(it.vi, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        val saved = savedFlags?.getOrNull(i) ?: true
                        if (saved && onDelete != null) DeleteChip { onDelete(i) }
                        if (!saved && onAdd != null) AddChip { onAdd(i) }
                    }
                    Column(
                        Modifier
                            .weight(W_KO)
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSpeakKorean(it.ko) }
                    ) {
                        Text(it.ko, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp)
                        if (it.romaja.isNotBlank()) {
                            Text(it.romaja, style = MaterialTheme.typography.labelSmall, color = x.muted, fontStyle = FontStyle.Italic)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Nghe", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            if (it.pos.isNotBlank()) {
                                Spacer(Modifier.width(4.dp))
                                Text(it.pos, style = MaterialTheme.typography.labelSmall, color = x.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Column(
                        Modifier
                            .weight(W_HAN)
                            .padding(end = 6.dp)
                            .then(if (onHanja != null && it.hanja.isNotBlank()) Modifier.clickable { onHanja(it) } else Modifier)
                    ) {
                        if (it.hanja.isNotBlank()) {
                            Text(it.hanja, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            if (it.hanViet.isNotBlank()) {
                                Text(it.hanViet, style = MaterialTheme.typography.labelSmall, color = Brand.Amber, fontStyle = FontStyle.Italic)
                            }
                        } else if (it.loan.isNotBlank()) {
                            // Từ mượn: từ gốc + nhãn nhỏ
                            Text(it.loan, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("từ mượn", style = MaterialTheme.typography.labelSmall, color = Brand.Cyan, fontStyle = FontStyle.Italic)
                        } else {
                            Text("—", color = x.muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Column(
                        Modifier
                            .weight(W_EX)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = it.exampleKo.isNotBlank()) { onSpeakKorean(it.exampleKo) }
                    ) {
                        Text(it.exampleKo, style = MaterialTheme.typography.bodySmall, lineHeight = 17.sp)
                        if (it.exampleVi.isNotBlank()) {
                            Text(it.exampleVi, style = MaterialTheme.typography.labelSmall, color = x.muted, lineHeight = 15.sp)
                        }
                    }
                }
                if (it.breakdown.isNotBlank()) BreakdownRow(it.breakdown)
                if (it.note.isNotBlank()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 10.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(Icons.Rounded.Lightbulb, null, tint = Brand.Amber, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(it.note, style = MaterialTheme.typography.labelSmall, color = x.muted)
                    }
                }
            }
        }
    }
}

/** Hàng "Cấu tạo" dưới mỗi từ: từ ghép = thành phần + thành phần. */
@Composable
fun BreakdownRow(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, bottom = 9.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brand.Teal.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("CẤU TẠO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Brand.Teal, letterSpacing = 0.5.sp)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

/** Nhãn trạng thái lưu: tất cả / một phần / chưa lưu. */
@Composable
private fun SavedPill(flags: List<Boolean>?, savedCount: Int) {
    if (flags == null) {
        if (savedCount > 0) Pill("Đã lưu vào sổ", Icons.Rounded.CheckCircle, Brand.Green)
        return
    }
    val n = flags.count { it }
    when {
        flags.isEmpty() -> Unit
        n == flags.size -> Pill("Đã lưu vào sổ", Icons.Rounded.CheckCircle, Brand.Green)
        n > 0 -> Pill("Đã lưu $n/${flags.size}", Icons.Rounded.CheckCircle, Brand.Amber)
        else -> Pill("Bấm + để lưu", Icons.Rounded.AddCircleOutline, MaterialTheme.colorScheme.primary)
    }
}

/** Nút "+" nhỏ: lưu mục này vào sổ ngay. */
@Composable
private fun AddChip(onClick: () -> Unit) {
    Box(
        Modifier
            .padding(top = 6.dp)
            .size(26.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, "Lưu vào sổ", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

/** Nút thùng rác nhỏ: xoá khỏi sổ ngay, không cần hỏi lại. */
@Composable
private fun DeleteChip(onClick: () -> Unit) {
    Box(
        Modifier
            .padding(top = 6.dp)
            .size(26.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.DeleteOutline, "Xoá khỏi sổ", tint = LocalExtra.current.muted, modifier = Modifier.size(16.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Bảng ngữ pháp: Đuôi câu | Nghĩa | Dạng | Từ kết hợp | Câu mẫu
// Màn hình điện thoại hẹp → mỗi điểm ngữ pháp là 1 bảng dọc (cột tiêu đề | nội dung) cho dễ đọc.
// ---------------------------------------------------------------------------------------------

@Composable
fun GrammarTable(
    items: List<GrammarItem>,
    savedCount: Int,
    onSpeakKorean: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: ((Int) -> Unit)? = null,
    savedFlags: List<Boolean>? = null,
    onAdd: ((Int) -> Unit)? = null,
) {
    val x = LocalExtra.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(x.card)
            .border(1.dp, x.cardBorder, shape)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Brand.Violet.copy(alpha = 0.22f), Brand.Pink.copy(alpha = 0.14f), Color.Transparent)))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.AccountTree, null, tint = Brand.Violet, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("NGỮ PHÁP", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            SavedPill(savedFlags, savedCount)
        }
        items.forEachIndexed { i, g ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(6.dp).background(x.tableHeader))
            GrammarRow("Đuôi câu", striped = false) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(g.pattern, color = Brand.Violet, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    val saved = savedFlags?.getOrNull(i) ?: true
                    if (saved && onDelete != null) DeleteChip { onDelete(i) }
                    if (!saved && onAdd != null) AddChip { onAdd(i) }
                }
            }
            GrammarRow("Nghĩa", striped = true) {
                Text(g.meaning, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            GrammarRow("Dạng", striped = false) {
                Text(g.form.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
            }
            GrammarRow("Từ kết hợp", striped = true) {
                Text(g.combine.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
            }
            if (g.breakdown.isNotBlank()) {
                GrammarRow("Cấu tạo", striped = false) {
                    Text(g.breakdown, style = MaterialTheme.typography.bodySmall, color = Brand.Teal, fontWeight = FontWeight.SemiBold)
                }
            }
            GrammarRow("Câu mẫu", striped = g.breakdown.isNotBlank()) {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = g.exampleKo.isNotBlank()) { onSpeakKorean(g.exampleKo) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.exampleKo.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
                        if (g.exampleKo.isNotBlank()) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Nghe", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        }
                    }
                    if (g.exampleVi.isNotBlank()) Text(g.exampleVi, style = MaterialTheme.typography.labelSmall, color = x.muted)
                }
            }
            if (g.note.isNotBlank()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Lightbulb, null, tint = Brand.Amber, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(g.note, style = MaterialTheme.typography.labelSmall, color = x.muted)
                }
            }
        }
    }
}

@Composable
private fun GrammarRow(label: String, striped: Boolean, content: @Composable () -> Unit) {
    val x = LocalExtra.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (striped) x.tableStripe else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label.uppercase(),
            Modifier.width(88.dp).padding(top = 2.dp),
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            color = x.muted, letterSpacing = 0.5.sp,
        )
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text.uppercase(),
        Modifier.weight(weight),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = LocalExtra.current.muted,
        letterSpacing = 0.5.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// ---------------------------------------------------------------------------------------------
// Tin nhắn
// ---------------------------------------------------------------------------------------------

@Composable
fun MessageItem(
    m: ChatMessage,
    onSpeak: (String) -> Unit,
    onSpeakKorean: (String) -> Unit,
    onCopy: (String) -> Unit,
    onRetry: (() -> Unit)? = null,
    onDeleteVocab: ((Int) -> Unit)? = null,
    onDeleteGrammar: ((Int) -> Unit)? = null,
    vocabSaved: List<Boolean>? = null,
    grammarSaved: List<Boolean>? = null,
    onAddVocab: ((Int) -> Unit)? = null,
    onAddGrammar: ((Int) -> Unit)? = null,
    onHanja: ((VocabItem) -> Unit)? = null,
) {
    val x = LocalExtra.current
    if (m.isUser) {
        if (m.hasImage) {
            Row(Modifier.fillMaxWidth().padding(start = 48.dp, bottom = if (m.text.isBlank()) 0.dp else 4.dp), horizontalArrangement = Arrangement.End) {
                ChatImageThumb(m.imagePath)
            }
        }
        if (m.text.isNotBlank()) Row(Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                    .background(x.bubbleUser)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(m.text, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(22.dp)
            Spacer(Modifier.width(8.dp))
            Text("HUY FAN", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                Dates.time(m.createdAt) + if (m.latencyMs > 0) " · ${"%.1f".format(m.latencyMs / 1000f)}s" else "",
                style = MaterialTheme.typography.labelSmall, color = x.muted,
            )
            if (m.model == com.hankukko.huyfan.assistant.NotebookAnswer.MODEL) {
                Spacer(Modifier.width(6.dp))
                Pill("📒 Từ sổ · tức thì", color = Brand.Green)
            }
        }
        Spacer(Modifier.height(8.dp))
        val vocab = m.vocab
        if (vocab.isNotEmpty()) {
            VocabTable(vocab, m.savedCount, onSpeakKorean, onDelete = onDeleteVocab, savedFlags = vocabSaved, onAdd = onAddVocab, onHanja = onHanja)
            Spacer(Modifier.height(8.dp))
        }
        val grammar = m.grammar
        if (grammar.isNotEmpty()) {
            GrammarTable(grammar, m.savedCount, onSpeakKorean, onDelete = onDeleteGrammar, savedFlags = grammarSaved, onAdd = onAddGrammar)
            Spacer(Modifier.height(8.dp))
        }
        Box(
            Modifier
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp))
                .background(if (m.isError) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else x.bubbleAi)
                .border(1.dp, if (m.isError) MaterialTheme.colorScheme.error.copy(alpha = 0.4f) else x.cardBorder, RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                if (m.isError) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(m.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSpeak(m.text) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Đọc lại", tint = x.muted, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { onCopy(copyText(m)) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.ContentCopy, "Sao chép", tint = x.muted, modifier = Modifier.size(16.dp))
            }
            if (m.isError && onRetry != null) {
                IconButton(onClick = onRetry, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Refresh, "Thử lại", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun copyText(m: ChatMessage): String = buildString {
    m.grammar.forEach { g ->
        append(g.pattern).append(" – ").append(g.meaning)
        if (g.form.isNotBlank()) append(" (").append(g.form).append(")")
        append('\n')
        if (g.combine.isNotBlank()) append("  ").append(g.combine).append('\n')
        if (g.exampleKo.isNotBlank()) append("  ").append(g.exampleKo).append(" – ").append(g.exampleVi).append('\n')
    }
    m.vocab.forEach { v ->
        append(v.vi).append(" – ").append(v.ko)
        if (v.romaja.isNotBlank()) append(" (").append(v.romaja).append(")")
        if (v.breakdown.isNotBlank()) append(" [").append(v.breakdown).append("]")
        if (v.hanja.isNotBlank()) append(" · ").append(v.hanja).append(" ").append(v.hanViet)
        else if (v.loan.isNotBlank()) append(" · ").append(v.loan)
        append('\n')
        if (v.exampleKo.isNotBlank()) append("  ").append(v.exampleKo).append(" – ").append(v.exampleVi).append('\n')
    }
    append(m.text)
}

/** Công tắc nhanh "📖 Chi tiết": bật → AI giảng kỹ, bảng từ vựng/ngữ pháp vẫn như thường. */
@Composable
fun DetailToggle(on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val color = Brand.Violet
    Row(
        modifier
            .clip(CircleShape)
            .background(if (on) color else color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = if (on) 0f else 0.35f), CircleShape)
            .clickable(onClick = onToggle)
            .padding(horizontal = if (compact) 9.dp else 12.dp, vertical = if (compact) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = if (on) Color.White else color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            if (compact) (if (on) "Chi tiết" else "Ngắn") else "Chi tiết",
            color = if (on) Color.White else color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier.padding(start = 4.dp, bottom = 8.dp, top = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = LocalExtra.current.muted,
    )
}

@Composable
fun EmptyHint(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/**
 * Cửa sổ Dialog toàn màn hình đã tự đệm bàn phím bằng imePadding() → không cho cửa sổ tự co lại nữa,
 * nếu không bàn phím bị chừa chỗ 2 lần (khoảng trắng lớn phía trên bàn phím).
 */
@Composable
fun DialogImeFix() {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            ?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
    }
}

/** Chạm vào vùng này (danh sách tin nhắn…) hoặc cuộn → ẩn bàn phím, bỏ chọn ô nhập. */
@Composable
fun Modifier.hideKeyboardOnTap(): Modifier {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val kb = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    return this.then(
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(
                    requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial,
                )
                val up = waitForUpOrCancellation(
                    pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial,
                )
                if (up != null) { focus.clearFocus(); kb?.hide() }
            }
        }
    )
}
