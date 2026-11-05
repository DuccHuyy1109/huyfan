package com.hankukko.huyfan.ui.screens

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.verticalScroll
import com.hankukko.huyfan.data.RuleLink
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiPeople
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hankukko.huyfan.data.Rule
import com.hankukko.huyfan.data.RuleBook
import com.hankukko.huyfan.data.RuleCategory
import com.hankukko.huyfan.ui.MainViewModel
import com.hankukko.huyfan.ui.components.EmptyHint
import com.hankukko.huyfan.ui.components.GlassCard
import com.hankukko.huyfan.ui.components.Pill
import com.hankukko.huyfan.ui.theme.Brand
import com.hankukko.huyfan.ui.theme.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val HANGUL = Regex("[가-힣ㄱ-ㅎㅏ-ㅣ]+")

private val categoryColors = listOf(Brand.Blue, Brand.Violet, Brand.Teal, Brand.Indigo, Brand.Red, Brand.Amber, Brand.Green)

private fun iconOf(name: String): ImageVector = when (name) {
    "structure" -> Icons.Rounded.AccountTree
    "particle" -> Icons.Rounded.Extension
    "polite" -> Icons.Rounded.EmojiPeople
    "verb" -> Icons.Rounded.Transform
    "irregular" -> Icons.Rounded.Shuffle
    "sound" -> Icons.Rounded.RecordVoiceOver
    "number" -> Icons.Rounded.Tag
    "link" -> Icons.Rounded.Link
    "hanja" -> Icons.Rounded.Translate
    "alphabet" -> Icons.Rounded.SortByAlpha
    "mistake" -> Icons.Rounded.ErrorOutline
    else -> Icons.Rounded.AutoStories
}

/** Tô màu các đoạn chữ Hàn trong câu giải thích cho dễ nhìn. */
@Composable
private fun hangulStyled(text: String, color: Color = MaterialTheme.colorScheme.primary): AnnotatedString = remember(text, color) {
    buildAnnotatedString {
        var last = 0
        HANGUL.findAll(text).forEach { m ->
            append(text.substring(last, m.range.first))
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(m.value) }
            last = m.range.last + 1
        }
        append(text.substring(last))
    }
}

/** Màn "Nguyên tắc": cẩm nang ngữ pháp, bất quy tắc, biến âm… của tiếng Hàn. */
@Composable
fun RulesScreen(vm: MainViewModel, onAsk: (String) -> Unit) {
    val context = LocalContext.current
    val x = LocalExtra.current
    val book by produceState<List<RuleCategory>?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { RuleBook.load(context) }.getOrDefault(emptyList()) }
    }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val listState = rememberLazyListState()

    val all = book ?: emptyList()
    val open = all.firstOrNull { it.id == openId }
    val results = remember(all, query) { if (query.isBlank()) emptyList() else RuleBook.search(all, query) }
    val total = remember(all) { all.sumOf { it.rules.size } }

    BackHandler(enabled = open != null || searching) {
        when {
            searching -> { searching = false; query = "" }
            else -> openId = null
        }
    }
    LaunchedEffect(openId, query.isBlank()) { listState.scrollToItem(0) }

    Column(Modifier.fillMaxSize()) {
        // ---------- Thanh trên ----------
        Row(
            Modifier.fillMaxWidth().padding(start = if (open != null) 4.dp else 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (open != null) {
                IconButton(onClick = { openId = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Quay lại") }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    open?.title ?: "Nguyên tắc",
                    style = if (open != null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, maxLines = 1,
                )
                Text(
                    if (open != null) "${open.rules.size} quy tắc · ${open.subtitle}" else "$total quy tắc · ${all.size} nhóm · dùng offline",
                    style = MaterialTheme.typography.bodySmall, color = x.muted, maxLines = 1,
                )
            }
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, if (searching) "Đóng tìm kiếm" else "Tìm quy tắc")
            }
        }

        AnimatedVisibility(searching) {
            TextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Tìm: bất quy tắc ㅂ, nối âm, 에서, số đếm…") },
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

        // Chuyển nhanh giữa các nhóm khi đang xem 1 nhóm
        if (open != null && query.isBlank()) {
            val chipState = rememberLazyListState()
            LaunchedEffect(open.id) { chipState.animateScrollToItem(all.indexOf(open).coerceAtLeast(0)) }
            LazyRow(
                state = chipState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(all, key = { _, c -> c.id }) { i, c ->
                    val sel = c.id == open.id
                    val col = categoryColors[i % categoryColors.size]
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(if (sel) col.copy(alpha = 0.18f) else x.card)
                            .border(1.dp, if (sel) col.copy(alpha = 0.6f) else x.cardBorder, CircleShape)
                            .clickable { openId = c.id }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(iconOf(c.icon), null, tint = col, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(c.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }

        when {
            book == null -> Unit
            query.isNotBlank() -> {
                if (results.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyHint(Icons.Rounded.Search, "Không tìm thấy", "Thử từ khoá khác, hoặc hỏi thẳng HUY FAN nha.")
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item { Text("${results.size} kết quả", style = MaterialTheme.typography.labelMedium, color = x.muted) }
                        items(results, key = { (c, r) -> c.id + "/" + r.id }) { (c, r) ->
                            val i = all.indexOf(c)
                            RuleCard(
                                rule = r,
                                category = c.title,
                                color = categoryColors[i % categoryColors.size],
                                expanded = expanded[r.id] == true,
                                onToggle = { expanded[r.id] = expanded[r.id] != true },
                                onSpeak = { vm.speakKorean(it) },
                                onAsk = { onAsk(askText(r)) },
                            )
                        }
                    }
                }
            }
            open != null -> {
                val i = all.indexOf(open)
                val col = categoryColors[i % categoryColors.size]
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(open.rules, key = { it.id }) { r ->
                        RuleCard(
                            rule = r,
                            category = null,
                            color = col,
                            expanded = expanded[r.id] == true,
                            onToggle = { expanded[r.id] = expanded[r.id] != true },
                            onSpeak = { vm.speakKorean(it) },
                            onAsk = { onAsk(askText(r)) },
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { IntroCard(onAsk = { onAsk("Cho tôi lộ trình học ngữ pháp tiếng Hàn từ cơ bản, nên học nguyên tắc nào trước?") }) }
                    items(all, key = { it.id }) { c ->
                        val i = all.indexOf(c)
                        CategoryCard(c, categoryColors[i % categoryColors.size]) { openId = c.id }
                    }
                }
            }
        }
    }
}

/** Mở nhanh 1 quy tắc (từ thẻ từ vựng / ngữ pháp / thẻ ôn). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RuleSheet(ruleId: String, onSpeak: (String) -> Unit, onAsk: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val book = remember { runCatching { RuleBook.load(context) }.getOrDefault(emptyList()) }
    val found = remember(book, ruleId) {
        book.withIndex().firstNotNullOfOrNull { (i, c) -> c.rules.firstOrNull { it.id == ruleId }?.let { Triple(i, c, it) } }
    }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            if (found == null) {
                Text("Không tìm thấy quy tắc này", color = LocalExtra.current.muted)
            } else {
                val (i, c, r) = found
                RuleCard(
                    rule = r, category = c.title, color = categoryColors[i % categoryColors.size],
                    expanded = true, onToggle = {}, onSpeak = onSpeak,
                    onAsk = { onAsk(askText(r)); onDismiss() },
                )
            }
        }
    }
}

/** Chip quy tắc liên quan: chạm để mở giải thích. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RuleChips(links: List<RuleLink>, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    if (links.isEmpty()) return
    androidx.compose.foundation.layout.FlowRow(
        modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        links.forEach { l ->
            val (icon, color) = when (l.kind) {
                RuleLink.Kind.IRREGULAR -> Icons.Rounded.Shuffle to Brand.Red
                RuleLink.Kind.SOUND -> Icons.Rounded.RecordVoiceOver to Brand.Teal
                RuleLink.Kind.GRAMMAR -> Icons.Rounded.AutoStories to Brand.Violet
            }
            Row(
                Modifier
                    .clip(CircleShape)
                    .border(1.dp, color.copy(alpha = 0.45f), CircleShape)
                    .clickable { onOpen(l.ruleId) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(l.label, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(" ›", color = color, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun askText(r: Rule): String =
    "Giải thích kỹ hơn nguyên tắc tiếng Hàn: “${r.title}” (${r.summary}). Cho thêm vài ví dụ dễ hiểu nhé. " +
        "Đây là câu hỏi về nguyên tắc chung, chỉ cần giải thích, không cần thêm từ vựng hay ngữ pháp vào sổ."

@Composable
private fun IntroCard(onAsk: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brand.gradient)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoStories, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("Cẩm nang tiếng Hàn", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Cấu trúc, tiểu từ, bất quy tắc, biến âm – chạm ví dụ để nghe.",
            color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f))
                .clickable(onClick = onAsk)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Nên học cái nào trước?", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CategoryCard(c: RuleCategory, color: Color, onClick: () -> Unit) {
    val x = LocalExtra.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(x.card)
            .border(1.dp, x.cardBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.65f)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(iconOf(c.icon), null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(c.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(hangulStyled(c.subtitle, color), style = MaterialTheme.typography.bodySmall, color = x.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Pill("${c.rules.size}", color = color)
    }
}

@Composable
private fun RuleCard(
    rule: Rule,
    category: String?,
    color: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSpeak: (String) -> Unit,
    onAsk: () -> Unit,
) {
    val x = LocalExtra.current
    GlassCard(Modifier.fillMaxWidth().animateContentSize(), padding = 0.dp) {
        // Tiêu đề + tóm tắt (bấm để mở/đóng)
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(color)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (category != null) {
                    Text(category.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold)
                }
                Text(hangulStyled(rule.title, color), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (rule.summary.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(hangulStyled(rule.summary, color), style = MaterialTheme.typography.bodyMedium, color = x.muted, maxLines = if (expanded) 6 else 2)
                }
            }
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (expanded) "Thu gọn" else "Xem chi tiết", tint = x.muted)
        }

        if (expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                if (rule.body.isNotBlank()) {
                    Text(
                        hangulStyled(rule.body, color),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    )
                }
                if (rule.table.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    RuleTable(rule.table, color, onSpeak)
                }
                if (rule.examples.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("VÍ DỤ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = x.muted)
                    Spacer(Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rule.examples.forEach { ex ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(color.copy(alpha = 0.08f))
                                    .clickable { onSpeak(ex.ko) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(ex.ko, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    if (ex.read.isNotBlank()) {
                                        Text("Đọc: ${ex.read}", style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.SemiBold)
                                    }
                                    if (ex.vi.isNotBlank()) Text(ex.vi, style = MaterialTheme.typography.bodySmall, color = x.muted)
                                }
                                Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Nghe", tint = color, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .clickable(onClick = onAsk)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Hỏi HUY FAN thêm", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Bảng công thức: dòng đầu là tiêu đề; chạm dòng có chữ Hàn để nghe đọc. */
@Composable
private fun RuleTable(rows: List<List<String>>, color: Color, onSpeak: (String) -> Unit) {
    val x = LocalExtra.current
    val cols = rows.maxOf { it.size }
    val weights = if (cols == 2) listOf(1f, 1.35f) else List(cols) { 1f }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, x.cardBorder, RoundedCornerShape(14.dp))
    ) {
        rows.forEachIndexed { i, row ->
            val header = i == 0
            val speakText = if (header) null else row.firstNotNullOfOrNull { cell ->
                HANGUL.findAll(cell.substringBefore('(')).map { it.value }.joinToString(" ").ifBlank { null }
                    ?.takeIf { !cell.trim().startsWith("[") }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        when {
                            header -> color.copy(alpha = 0.16f)
                            i % 2 == 0 -> x.tableStripe
                            else -> Color.Transparent
                        }
                    )
                    .then(if (speakText != null) Modifier.clickable { onSpeak(speakText) } else Modifier)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (c in 0 until cols) {
                    val cell = row.getOrNull(c).orEmpty()
                    Text(
                        if (header) AnnotatedString(cell) else hangulStyled(cell, color),
                        modifier = Modifier.weight(weights[c]).padding(end = 6.dp),
                        style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
