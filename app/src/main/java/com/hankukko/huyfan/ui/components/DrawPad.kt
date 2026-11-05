package com.hankukko.huyfan.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hankukko.huyfan.ink.InkPoint
import com.hankukko.huyfan.ui.theme.Brand
import com.hankukko.huyfan.ui.theme.LocalExtra

/**
 * Bảng viết tay: người dùng vẽ chữ bằng ngón tay → bấm Kiểm tra để nhận dạng.
 * [result]: (thông báo, đúng?) – null khi chưa kiểm tra; [busy] khi đang nhận dạng/tải mô hình.
 */
@Composable
fun DrawPad(
    prompt: String,
    hint: String,
    busy: Boolean,
    busyText: String,
    result: Pair<String, Boolean>?,
    onCheck: (List<List<InkPoint>>) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    strokes: SnapshotStateList<List<InkPoint>> = remember { mutableStateListOf() },
) {
    val x = LocalExtra.current
    val current = remember { mutableStateListOf<InkPoint>() }
    val ink = MaterialTheme.colorScheme.onSurface

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(prompt, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = x.muted)
            }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Đóng bảng viết") }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(x.card)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
        ) {
            if (strokes.isEmpty() && current.isEmpty()) {
                Text(
                    "Viết vào đây ✍️", Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.titleMedium, color = x.muted,
                )
            }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            current.clear()
                            current += InkPoint(down.position.x, down.position.y, System.currentTimeMillis())
                            down.consume()
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull() ?: break
                                if (!ch.pressed) break
                                current += InkPoint(ch.position.x, ch.position.y, System.currentTimeMillis())
                                ch.consume()
                            }
                            strokes += current.toList()
                            current.clear()
                        }
                    }
            ) {
                val style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                (strokes + listOf(current.toList())).forEach { st ->
                    if (st.isEmpty()) return@forEach
                    if (st.size == 1) {
                        drawCircle(ink, radius = 3.5.dp.toPx(), center = Offset(st[0].x, st[0].y))
                    } else {
                        val path = Path().apply {
                            moveTo(st[0].x, st[0].y)
                            st.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(path, ink, style = style)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
            when {
                busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(busyText, style = MaterialTheme.typography.bodyMedium)
                }
                result != null -> Text(
                    result.first, textAlign = TextAlign.Center, maxLines = 2,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    color = if (result.second) Brand.Green else Brand.Red,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                modifier = Modifier.height(52.dp), shape = RoundedCornerShape(16.dp),
            ) { Icon(Icons.AutoMirrored.Rounded.Undo, "Hoàn tác") }
            OutlinedButton(
                onClick = { strokes.clear() },
                modifier = Modifier.height(52.dp), shape = RoundedCornerShape(16.dp),
            ) { Icon(Icons.Rounded.DeleteSweep, "Xoá hết") }
            Button(
                onClick = { if (strokes.isNotEmpty()) onCheck(strokes.toList()) },
                enabled = !busy && strokes.isNotEmpty(),
                modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Rounded.Check, null)
                Spacer(Modifier.width(6.dp))
                Text("Kiểm tra")
            }
        }
    }
}
