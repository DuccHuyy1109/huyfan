package com.hankukko.huyfan.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hankukko.huyfan.data.SessionSummary
import com.hankukko.huyfan.ui.MainViewModel
import com.hankukko.huyfan.ui.components.EmptyHint
import com.hankukko.huyfan.ui.components.Pill
import com.hankukko.huyfan.ui.theme.LocalExtra
import com.hankukko.huyfan.util.Dates

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(vm: MainViewModel, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val x = LocalExtra.current
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val current by vm.engine.sessionId.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<SessionSummary?>(null) }
    val grouped = remember(sessions) { sessions.groupBy { Dates.dayKeyOf(it.updatedAt) } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack, "Về Chat")
            }
            Column(Modifier.weight(1f)) {
                Text("Lịch sử", style = MaterialTheme.typography.headlineMedium)
                Text("${sessions.size} cuộc trò chuyện", style = MaterialTheme.typography.bodySmall, color = x.muted)
            }
            if (sessions.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.DeleteSweep, "Xoá tất cả") }
            }
        }
        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyHint(Icons.Rounded.History, "Chưa có lịch sử", "Các câu hỏi của ông sẽ được lưu ở đây để xem lại.")
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                grouped.forEach { (day, list) ->
                    stickyHeader(key = "h_$day") {
                        Text(
                            Dates.dayLabel(day),
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(vertical = 8.dp),
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        )
                    }
                    items(list, key = { it.id }) { s ->
                        val active = s.id == current
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(x.card)
                                .border(1.dp, if (active) MaterialTheme.colorScheme.primary else x.cardBorder, RoundedCornerShape(20.dp))
                                .clickable { onOpen(s.id) }
                                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.ChatBubbleOutline, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!s.preview.isNullOrBlank()) {
                                    Text(s.preview, style = MaterialTheme.typography.bodySmall, color = x.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Pill(Dates.time(s.updatedAt))
                                    Pill("${s.count} tin")
                                    if (active) Pill("Đang mở")
                                }
                            }
                            IconButton(onClick = { confirmDelete = s }) {
                                Icon(Icons.Rounded.DeleteOutline, "Xoá", tint = x.muted)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Xoá toàn bộ lịch sử?") },
            text = { Text("Sổ từ vựng vẫn được giữ nguyên, chỉ xoá các cuộc trò chuyện.") },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); confirmClear = false }) { Text("Xoá hết", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Huỷ") } },
        )
    }
    confirmDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Xoá cuộc trò chuyện?") },
            text = { Text("“${s.title}”") },
            confirmButton = { TextButton(onClick = { vm.deleteSession(s.id); confirmDelete = null }) { Text("Xoá", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Huỷ") } },
        )
    }
}
