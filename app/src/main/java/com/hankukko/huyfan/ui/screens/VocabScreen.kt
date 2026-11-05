@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.hankukko.huyfan.ui.screens

import com.hankukko.huyfan.data.RuleLinks
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hankukko.huyfan.data.GrammarEntity
import com.hankukko.huyfan.data.Srs
import com.hankukko.huyfan.data.VocabEntity
import com.hankukko.huyfan.data.grammarKeyOf
import com.hankukko.huyfan.data.keyOf
import com.hankukko.huyfan.ui.MainViewModel
import com.hankukko.huyfan.ui.components.EmptyHint
import com.hankukko.huyfan.ui.components.Pill
import com.hankukko.huyfan.ui.theme.Brand
import com.hankukko.huyfan.ui.theme.LocalExtra
import com.hankukko.huyfan.util.Dates

enum class VocabFilter(val label: String) {
    ALL("Tất cả"), TODAY("Hôm nay"), DUE("Đến hạn ôn"), WEEK("7 ngày"), DUP("Trùng lặp"), STARRED("Đánh dấu"), WEAK("Chưa thuộc")
}

/** Các ngày khác cũng có từ này (cùng khoá so trùng). */
fun dupDaysOf(list: List<VocabEntity>): Map<Long, List<String>> {
    val groups = list.groupBy { keyOf(it.ko) }.filter { it.value.size > 1 }
    return groups.values.flatMap { g -> g.map { e -> e.id to g.filter { it.id != e.id }.map { it.day }.distinct().sortedDescending() } }.toMap()
}

fun dupDaysOfGrammar(list: List<GrammarEntity>): Map<Long, List<String>> {
    val groups = list.groupBy { grammarKeyOf(it.pattern) }.filter { it.value.size > 1 }
    return groups.values.flatMap { g -> g.map { e -> e.id to g.filter { it.id != e.id }.map { it.day }.distinct().sortedDescending() } }.toMap()
}

fun shortDay(day: String): String = runCatching {
    java.time.LocalDate.parse(day).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))
}.getOrDefault(day)

enum class BookTab(val label: String) { VOCAB("Từ vựng"), GRAMMAR("Ngữ pháp"), HANJA("Chữ Hán") }

fun List<VocabEntity>.filtered(f: VocabFilter): List<VocabEntity> {
    val today = Dates.todayKey()
    val week = Dates.daysAgoKey(6)
    return when (f) {
        VocabFilter.ALL -> this
        VocabFilter.TODAY -> filter { it.day == today }
        VocabFilter.WEEK -> filter { it.day >= week }
        VocabFilter.STARRED -> filter { it.starred }
        VocabFilter.WEAK -> filter { it.mastery < 3 }
        VocabFilter.DUE -> filter { Srs.isDue(it.dueAt, it.createdAt) }
        VocabFilter.DUP -> { val d = dupDaysOf(this); filter { it.id in d } }
    }
}

fun List<GrammarEntity>.filteredGrammar(f: VocabFilter): List<GrammarEntity> {
    val today = Dates.todayKey()
    val week = Dates.daysAgoKey(6)
    return when (f) {
        VocabFilter.ALL -> this
        VocabFilter.TODAY -> filter { it.day == today }
        VocabFilter.WEEK -> filter { it.day >= week }
        VocabFilter.STARRED -> filter { it.starred }
        VocabFilter.WEAK -> filter { it.mastery < 3 }
        VocabFilter.DUE -> filter { Srs.isDue(it.dueAt, it.createdAt) }
        VocabFilter.DUP -> { val d = dupDaysOfGrammar(this); filter { it.id in d } }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VocabScreen(
    vm: MainViewModel,
    filter: VocabFilter,
    onFilter: (VocabFilter) -> Unit,
    onOpenReview: () -> Unit,
    onCreateLesson: (vocabIds: Set<Long>, grammarIds: Set<Long>) -> Unit,
    onAsk: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val x = LocalExtra.current
    val all by vm.vocab.collectAsStateWithLifecycle()
    val allGrammar by vm.grammar.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(BookTab.VOCAB) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    // Chế độ chọn nhiều
    var selVocab by remember { mutableStateOf(setOf<Long>()) }
    var selGrammar by remember { mutableStateOf(setOf<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Bảng chữ Hán (chữ / từ, âm Hán Việt) và bảng quy tắc đang mở
    var hanjaOpen by remember { mutableStateOf<Pair<String, String>?>(null) }
    var ruleOpen by remember { mutableStateOf<String?>(null) }
    val hanjaIndex = remember(all) { HanjaIndex.build(all) }
    val selecting = selVocab.isNotEmpty() || selGrammar.isNotEmpty()
    val selCount = selVocab.size + selGrammar.size

    LaunchedEffect(tab) { selVocab = emptySet(); selGrammar = emptySet() }

    val q = query.trim().lowercase()
    val vocabList = remember(all, filter, q) {
        all.filtered(filter).filter {
            q.isEmpty() || it.ko.contains(q) || it.vi.lowercase().contains(q) || it.romaja.lowercase().contains(q) ||
                it.hanViet.lowercase().contains(q) || it.hanja.contains(q) || it.loan.lowercase().contains(q)
        }
    }
    val grammarList = remember(allGrammar, filter, q) {
        allGrammar.filteredGrammar(filter).filter {
            q.isEmpty() || it.pattern.lowercase().contains(q) || it.meaning.lowercase().contains(q) ||
                it.form.lowercase().contains(q) || it.combine.lowercase().contains(q)
        }
    }
    val vocabDup = remember(all) { dupDaysOf(all) }
    val grammarDup = remember(allGrammar) { dupDaysOfGrammar(allGrammar) }
    val vocabGroups = remember(vocabList) { vocabList.groupBy { it.day }.toSortedMap(compareByDescending { it }) }
    val grammarGroups = remember(grammarList) { grammarList.groupBy { it.day }.toSortedMap(compareByDescending { it }) }

    Column(Modifier.fillMaxSize()) {
        // ---------- Thanh trên: bình thường / đang chọn ----------
        if (selecting) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { selVocab = emptySet(); selGrammar = emptySet() }) { Icon(Icons.Rounded.Close, "Bỏ chọn") }
                Text("Đã chọn $selCount", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    if (tab == BookTab.VOCAB) selVocab = vocabList.map { it.id }.toSet()
                    else selGrammar = grammarList.map { it.id }.toSet()
                }) { Icon(Icons.Rounded.SelectAll, "Chọn tất cả") }
                IconButton(onClick = {
                    onCreateLesson(selVocab, selGrammar)
                    selVocab = emptySet(); selGrammar = emptySet()
                }) { Icon(Icons.Rounded.LibraryAdd, "Tạo bài ôn", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Rounded.DeleteOutline, "Xoá", tint = MaterialTheme.colorScheme.error)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sổ học", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${stats.total} từ · ${stats.grammarTotal} ngữ pháp · chuỗi ${stats.streak} ngày",
                        style = MaterialTheme.typography.bodySmall, color = x.muted,
                    )
                }
                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                    Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, "Tìm")
                }
                if (settings.sheetsUrl.isNotBlank()) {
                    IconButton(onClick = { vm.syncNow { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }) {
                        Icon(
                            if (stats.unsynced == 0) Icons.Rounded.CloudDone else Icons.Rounded.CloudUpload,
                            "Đồng bộ Google Sheets",
                            tint = if (stats.unsynced == 0) Brand.Green else Brand.Amber,
                        )
                    }
                }
                IconButton(onClick = { vm.exportCsv(context) }) { Icon(Icons.Rounded.Share, "Xuất Excel/CSV") }
            }
        }

        Segmented(
            BookTab.entries, tab,
            { t ->
                when (t) {
                    BookTab.VOCAB -> "${t.label} (${all.size})"
                    BookTab.GRAMMAR -> "${t.label} (${allGrammar.size})"
                    BookTab.HANJA -> "${t.label} (${hanjaIndex.size})"
                }
            },
            { tab = it },
        )

        AnimatedVisibility(searching) {
            TextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text(if (tab == BookTab.VOCAB) "Tìm tiếng Việt, tiếng Hàn, Hán Việt…" else "Tìm đuôi câu, nghĩa, dạng…") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = x.card, unfocusedContainerColor = x.card,
                ),
            )
        }

        if (tab != BookTab.HANJA) Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VocabFilter.entries.forEach { f -> FilterPill(f.label, f == filter) { onFilter(f) } }
        }

        val empty = if (tab == BookTab.VOCAB) vocabList.isEmpty() else grammarList.isEmpty()
        if (tab == BookTab.HANJA) {
            HanjaTab(hanjaIndex, query) { ch -> hanjaOpen = ch to "" }
        } else if (empty) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyHint(
                    if (tab == BookTab.VOCAB) Icons.AutoMirrored.Rounded.MenuBook else Icons.Rounded.AccountTree,
                    if (query.isNotBlank()) "Không tìm thấy" else "Sổ còn trống",
                    when {
                        query.isNotBlank() -> "Thử từ khoá khác nha."
                        tab == BookTab.VOCAB -> "Hỏi HUY FAN “… tiếng Hàn là gì?” – từ sẽ tự lưu vào đây theo ngày."
                        else -> "Hỏi HUY FAN “-고 싶다 là gì?” – ngữ pháp sẽ tự lưu vào đây."
                    },
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (!selecting) {
                    item(key = "hint") {
                        Text(
                            "Nhấn giữ để chọn nhiều",
                            style = MaterialTheme.typography.labelSmall, color = x.muted,
                        )
                    }
                }
                if (tab == BookTab.VOCAB) {
                    vocabGroups.forEach { (day, words) ->
                        stickyHeader(key = "h_$day") {
                            DayHeader(day, words.size, showReview = day == Dates.todayKey() && !selecting, onReview = onOpenReview,
                                allSelected = selecting && words.all { it.id in selVocab },
                                onSelectDay = if (selecting) { { on ->
                                    selVocab = if (on) selVocab + words.map { it.id } else selVocab - words.map { it.id }.toSet()
                                } } else null)
                        }
                        items(words, key = { it.id }) { w ->
                            WordCard(
                                w,
                                dupDays = vocabDup[w.id].orEmpty(),
                                selecting = selecting,
                                selected = w.id in selVocab,
                                onToggleSelect = { selVocab = if (w.id in selVocab) selVocab - w.id else selVocab + w.id },
                                onSpeak = { vm.speakKorean(it) },
                                onStar = { vm.toggleStar(w) },
                                onDelete = { selVocab = setOf(w.id); confirmDelete = true },
                                onHanja = { hanjaOpen = w.hanja to w.hanViet },
                                onRule = { ruleOpen = it },
                            )
                        }
                    }
                } else {
                    grammarGroups.forEach { (day, list) ->
                        stickyHeader(key = "g_$day") {
                            DayHeader(day, list.size, showReview = false, onReview = onOpenReview,
                                allSelected = selecting && list.all { it.id in selGrammar },
                                onSelectDay = if (selecting) { { on ->
                                    selGrammar = if (on) selGrammar + list.map { it.id } else selGrammar - list.map { it.id }.toSet()
                                } } else null)
                        }
                        items(list, key = { "g${it.id}" }) { g ->
                            GrammarCard(
                                g,
                                dupDays = grammarDup[g.id].orEmpty(),
                                selecting = selecting,
                                selected = g.id in selGrammar,
                                onToggleSelect = { selGrammar = if (g.id in selGrammar) selGrammar - g.id else selGrammar + g.id },
                                onSpeak = { vm.speakKorean(it) },
                                onStar = { vm.toggleStarGrammar(g) },
                                onDelete = { selGrammar = setOf(g.id); confirmDelete = true },
                                onRule = { ruleOpen = it },
                            )
                        }
                    }
                }
            }
        }
    }

    hanjaOpen?.let { (h, hv) ->
        HanjaSheet(h, hv, hanjaIndex, onSpeak = { vm.speakKorean(it) }, onAsk = { q -> hanjaOpen = null; onAsk(q) }, onDismiss = { hanjaOpen = null })
    }
    ruleOpen?.let { id ->
        RuleSheet(id, onSpeak = { vm.speakKorean(it) }, onAsk = { q -> ruleOpen = null; onAsk(q) }, onDismiss = { ruleOpen = null })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Xoá $selCount mục?") },
            text = { Text("Các mục sẽ bị xoá hẳn khỏi sổ, khỏi các bài ôn và khỏi Google Sheets (nếu đang đồng bộ).") },
            confirmButton = {
                TextButton(onClick = {
                    if (selVocab.isNotEmpty()) vm.deleteWords(selVocab)
                    if (selGrammar.isNotEmpty()) vm.deleteGrammar(selGrammar)
                    Toast.makeText(context, "Đã xoá $selCount mục", Toast.LENGTH_SHORT).show()
                    selVocab = emptySet(); selGrammar = emptySet(); confirmDelete = false
                }) { Text("Xoá", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Huỷ") } },
        )
    }
}

@Composable
fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val x = LocalExtra.current
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) Brand.gradient else SolidColor(x.card))
            .border(1.dp, if (selected) Color.Transparent else x.cardBorder, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun DayHeader(
    day: String,
    count: Int,
    showReview: Boolean,
    onReview: () -> Unit,
    allSelected: Boolean,
    onSelectDay: ((Boolean) -> Unit)?,
) {
    val x = LocalExtra.current
    val bg = MaterialTheme.colorScheme.background
    Row(
        Modifier
            .fillMaxWidth()
            // Che nội dung cuộn bên dưới khi thanh ngày dính trên cùng, mờ dần ở mép dưới
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to bg.copy(alpha = 0f), 0.14f to bg, 0.78f to bg, 1f to bg.copy(alpha = 0f)))
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(x.navBg.copy(alpha = 0.97f))
            .border(1.dp, x.cardBorder, RoundedCornerShape(16.dp))
            .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 18.dp)
                .clip(CircleShape)
                .background(Brand.gradient)
        )
        Spacer(Modifier.width(10.dp))
        if (onSelectDay != null) {
            SelectMark(allSelected) { onSelectDay(!allSelected) }
            Spacer(Modifier.width(8.dp))
        }
        Text(Dates.dayLabel(day), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Pill("$count mục")
        Spacer(Modifier.weight(1f))
        if (showReview) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onReview)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Style, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Ôn ngay", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
fun SelectMark(selected: Boolean, onClick: () -> Unit) {
    Icon(
        if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
        if (selected) "Bỏ chọn" else "Chọn",
        tint = if (selected) MaterialTheme.colorScheme.primary else LocalExtra.current.muted,
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WordCard(
    w: VocabEntity,
    dupDays: List<String>,
    selecting: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onSpeak: (String) -> Unit,
    onStar: () -> Unit,
    onDelete: () -> Unit,
    onHanja: () -> Unit = {},
    onRule: (String) -> Unit = {},
) {
    val x = LocalExtra.current
    var expanded by rememberSaveable(w.id) { mutableStateOf(false) }
    val rules = remember(w.ko) { RuleLinks.forVocab(w.ko) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else x.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else x.cardBorder, RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = { if (selecting) onToggleSelect() else expanded = !expanded },
                onLongClick = onToggleSelect,
            )
            .animateContentSize()
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                SelectMark(selected, onToggleSelect)
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(w.ko, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (w.romaja.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(w.romaja, style = MaterialTheme.typography.bodySmall, color = x.muted, fontStyle = FontStyle.Italic, modifier = Modifier.padding(bottom = 3.dp))
                    }
                }
                Text(w.vi, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
            if (!selecting) {
                IconButton(onClick = { onSpeak(w.ko) }) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Nghe", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onStar) {
                    Icon(if (w.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Đánh dấu", tint = if (w.starred) Brand.Amber else x.muted)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            if (w.hanja.isNotBlank()) {
                Pill(
                    "${w.hanja} · ${w.hanViet}".trimEnd(' ', '·') + " ›", color = Brand.Amber,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onHanja),
                )
            }
            if (w.hanja.isBlank() && w.loan.isNotBlank()) Pill("từ mượn · ${w.loan}", color = Brand.Cyan)
            if (w.pos.isNotBlank()) Pill(w.pos, color = Brand.Violet)
            if (w.lookupCount > 1) Pill("tra ${w.lookupCount} lần", color = Brand.Red)
            DuePill(w.dueAt, w.createdAt)
            MasteryDots(w.mastery)
        }
        if (dupDays.isNotEmpty()) DupBadge(dupDays)
        RuleChips(rules, onRule, Modifier.padding(top = 6.dp))
        if (w.breakdown.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("🧩 ${w.breakdown}", style = MaterialTheme.typography.bodySmall, color = Brand.Teal, fontWeight = FontWeight.SemiBold)
        }
        if (w.exampleKo.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(x.tableStripe)
                    .clickable(enabled = !selecting) { onSpeak(w.exampleKo) }
                    .padding(10.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(w.exampleKo, style = MaterialTheme.typography.bodyMedium)
                    if (expanded || w.exampleVi.length < 60) {
                        Text(w.exampleVi, style = MaterialTheme.typography.bodySmall, color = x.muted)
                    }
                }
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = x.muted, modifier = Modifier.size(16.dp))
            }
        }
        if (expanded && !selecting) {
            if (w.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("💡 ${w.note}", style = MaterialTheme.typography.bodySmall, color = x.muted)
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Lưu lúc ${Dates.time(w.createdAt)}" + if (w.synced) " · đã lên Sheets" else "",
                    style = MaterialTheme.typography.labelSmall, color = x.muted, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Xoá", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GrammarCard(
    g: GrammarEntity,
    dupDays: List<String>,
    selecting: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onSpeak: (String) -> Unit,
    onStar: () -> Unit,
    onDelete: () -> Unit,
    onRule: (String) -> Unit = {},
) {
    val x = LocalExtra.current
    var expanded by rememberSaveable(g.id) { mutableStateOf(false) }
    val rules = remember(g.pattern, g.form) { RuleLinks.forGrammar(g.pattern, g.form) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Brand.Violet.copy(alpha = 0.10f) else x.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) Brand.Violet else x.cardBorder, RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = { if (selecting) onToggleSelect() else expanded = !expanded },
                onLongClick = onToggleSelect,
            )
            .animateContentSize()
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                SelectMark(selected, onToggleSelect)
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(g.pattern, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Brand.Violet)
                Text(g.meaning, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
            if (!selecting) {
                IconButton(onClick = onStar) {
                    Icon(if (g.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Đánh dấu", tint = if (g.starred) Brand.Amber else x.muted)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            if (g.form.isNotBlank()) Pill(g.form, color = Brand.Violet)
            DuePill(g.dueAt, g.createdAt)
            MasteryDots(g.mastery)
        }
        if (dupDays.isNotEmpty()) DupBadge(dupDays)
        RuleChips(rules, onRule, Modifier.padding(top = 6.dp))
        if (g.combine.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("Kết hợp: ${g.combine}", style = MaterialTheme.typography.bodySmall)
        }
        if (g.breakdown.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("🧩 ${g.breakdown}", style = MaterialTheme.typography.bodySmall, color = Brand.Teal, fontWeight = FontWeight.SemiBold)
        }
        if (g.exampleKo.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(x.tableStripe)
                    .clickable(enabled = !selecting) { onSpeak(g.exampleKo) }
                    .padding(10.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(g.exampleKo, style = MaterialTheme.typography.bodyMedium)
                    Text(g.exampleVi, style = MaterialTheme.typography.bodySmall, color = x.muted)
                }
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = x.muted, modifier = Modifier.size(16.dp))
            }
        }
        if (expanded && !selecting) {
            if (g.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("💡 ${g.note}", style = MaterialTheme.typography.bodySmall, color = x.muted)
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Lưu lúc ${Dates.time(g.createdAt)}", style = MaterialTheme.typography.labelSmall, color = x.muted, modifier = Modifier.weight(1f))
                TextButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Xoá", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** Nhãn cảnh báo trùng: từ này đã học ở ngày khác → dễ quên, nên ôn kỹ. */
@Composable
fun DuePill(dueAt: Long, createdAt: Long) {
    val due = Srs.isDue(dueAt, createdAt)
    Pill(Srs.dueLabel(dueAt, createdAt), icon = Icons.Rounded.Schedule, color = if (due) Brand.Amber else LocalExtra.current.muted)
}

@Composable
fun DupBadge(days: List<String>) {
    Row(
        Modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brand.Amber.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Repeat, null, tint = Brand.Amber, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "Trùng · đã học ngày " + days.take(3).joinToString(", ") { shortDay(it) } + if (days.size > 3) "…" else "",
            style = MaterialTheme.typography.labelMedium, color = Brand.Amber, fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun MasteryDots(level: Int) {
    Row(Modifier.padding(start = 2.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(5) { i ->
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (i < level) Brand.Green else LocalExtra.current.cardBorder)
            )
        }
    }
}
