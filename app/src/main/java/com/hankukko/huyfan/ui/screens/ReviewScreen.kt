package com.hankukko.huyfan.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hankukko.huyfan.ui.neonBackdrop
import com.hankukko.huyfan.data.GrammarEntity
import com.hankukko.huyfan.data.LessonSummary
import com.hankukko.huyfan.data.StudyConfig
import com.hankukko.huyfan.data.VocabEntity
import com.hankukko.huyfan.data.grammarKeyOf
import com.hankukko.huyfan.data.keyOf
import com.hankukko.huyfan.ui.MainViewModel
import com.hankukko.huyfan.ui.components.DrawPad
import com.hankukko.huyfan.ui.theme.Brand
import com.hankukko.huyfan.ui.theme.LocalExtra
import com.hankukko.huyfan.data.DueSummary
import com.hankukko.huyfan.data.Grade
import com.hankukko.huyfan.data.Srs
import com.hankukko.huyfan.data.SrsState
import com.hankukko.huyfan.data.due
import com.hankukko.huyfan.data.srs
import com.hankukko.huyfan.data.withSrs
import com.hankukko.huyfan.util.Dates
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 1 thẻ ôn: từ vựng hoặc ngữ pháp. */
data class StudyCard(val vocab: VocabEntity? = null, val grammar: GrammarEntity? = null, val repeats: Int = 1) {
    val key: String get() = vocab?.let { "v${it.id}" } ?: "g${grammar?.id}"
    val mastery: Int get() = vocab?.mastery ?: grammar?.mastery ?: 0
}

/** Phiên học đang mở (ôn nhanh hoặc theo bài ôn). */
data class StudySession(
    val title: String,
    val cards: List<StudyCard>,
    val lessonId: Long? = null,
    /** null = dùng cài đặt ôn nhanh mặc định */
    val config: StudyConfig? = null,
)

/** Yêu cầu mở cửa sổ tạo/sửa bài ôn. */
data class LessonEditorRequest(
    val lessonId: Long? = null,
    val name: String = "",
    val vocabIds: Set<Long> = emptySet(),
    val grammarIds: Set<Long> = emptySet(),
    val config: StudyConfig = StudyConfig(),
)

private enum class Quick(val label: String, val icon: ImageVector) {
    TODAY("Từ hôm nay", Icons.Rounded.Today),
    WEEK("Từ 7 ngày", Icons.Rounded.History),
    WEAK("Chưa thuộc", Icons.Rounded.School),
    DUP("Hay quên", Icons.Rounded.Repeat),
    GRAMMAR("Ngữ pháp", Icons.Rounded.AccountTree),
    ALL("Tất cả", Icons.Rounded.AutoAwesome),
}

@Composable
fun ReviewScreen(
    vm: MainViewModel,
    onStart: (StudySession) -> Unit,
    onEditLesson: (LessonEditorRequest) -> Unit,
    onVoiceQuiz: () -> Unit,
    autoStartDue: Boolean = false,
    onAutoStartConsumed: () -> Unit = {},
    onPractice: () -> Unit = {},
    onRolePlay: () -> Unit = {},
) {
    val context = LocalContext.current
    val x = LocalExtra.current
    val vocab by vm.vocab.collectAsStateWithLifecycle()
    val grammar by vm.grammar.collectAsStateWithLifecycle()
    val lessons by vm.lessons.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<LessonSummary?>(null) }
    var showStudySettings by remember { mutableStateOf(false) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val due by vm.due.collectAsStateWithLifecycle()

    fun dueSession(d: DueSummary): StudySession = StudySession(
        "Đến hạn hôm nay",
        collapseDuplicates(d.vocab.map { StudyCard(vocab = it) } + d.grammar.map { StudyCard(grammar = it) }, vocab, grammar)
            .sortedBy { it.vocab?.due ?: it.grammar?.due ?: 0L },
    )
    // Bấm "Ôn ngay" trên thông báo nhắc học → mở thẳng phiên ôn
    LaunchedEffect(autoStartDue, due) {
        val d = due ?: return@LaunchedEffect
        if (!autoStartDue) return@LaunchedEffect
        onAutoStartConsumed()
        if (d.count > 0) onStart(dueSession(d))
        else Toast.makeText(context, "Hết thẻ đến hạn rồi, giỏi quá ông ơi!", Toast.LENGTH_SHORT).show()
    }

    fun quickCards(q: Quick): List<StudyCard> = when (q) {
        Quick.TODAY -> vocab.filtered(VocabFilter.TODAY).map { StudyCard(vocab = it) }
        Quick.WEEK -> vocab.filtered(VocabFilter.WEEK).map { StudyCard(vocab = it) }
        Quick.WEAK -> vocab.filtered(VocabFilter.WEAK).map { StudyCard(vocab = it) } +
            grammar.filteredGrammar(VocabFilter.WEAK).map { StudyCard(grammar = it) }
        Quick.DUP -> vocab.filtered(VocabFilter.DUP).map { StudyCard(vocab = it) } +
            grammar.filteredGrammar(VocabFilter.DUP).map { StudyCard(grammar = it) }
        Quick.GRAMMAR -> grammar.map { StudyCard(grammar = it) }
        Quick.ALL -> vocab.map { StudyCard(vocab = it) } + grammar.map { StudyCard(grammar = it) }
    }.let { collapseDuplicates(it, vocab, grammar) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Ôn tập", style = MaterialTheme.typography.headlineMedium)
                }
                IconButton(onClick = { showStudySettings = true }) { Icon(Icons.Rounded.Tune, "Cài đặt bài học") }
                Button(onClick = { onEditLesson(LessonEditorRequest(config = settings.studyConfig)) }) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tạo bài ôn")
                }
            }
        }

        // ---------- Đến hạn hôm nay (ôn ngắt quãng) ----------
        item { DueHero(due) { due?.let { if (it.count > 0) onStart(dueSession(it)) } } }

        // ---------- Luyện nói ----------
        if (!com.hankukko.huyfan.Lang.isEn) item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PracticeTile(
                    "Phát âm", "Chấm điểm", Icons.Rounded.GraphicEq, Brand.Teal,
                    Modifier.weight(1f), onPractice,
                )
                PracticeTile(
                    "Hội thoại", "Nhập vai", Icons.Rounded.Forum, Brand.Violet,
                    Modifier.weight(1f), onRolePlay,
                )
            }
        }

        // ---------- Ôn nhanh ----------
        item { SectionTitle("Ôn nhanh") }
        item {
            val quick = Quick.entries
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                quick.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { q ->
                            val cards = remember(vocab, grammar, q) { quickCards(q) }
                            QuickTile(q.label, cards.size, q.icon, Modifier.weight(1f)) {
                                if (cards.isEmpty()) Toast.makeText(context, "Chưa có mục nào để ôn", Toast.LENGTH_SHORT).show()
                                else onStart(StudySession(q.label, cards.sortedBy { it.mastery }))
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.horizontalGradient(listOf(Brand.Cyan.copy(alpha = 0.12f), Brand.Violet.copy(alpha = 0.14f))))
                    .border(1.dp, Brush.horizontalGradient(listOf(Brand.Cyan.copy(alpha = 0.4f), Brand.Violet.copy(alpha = 0.2f))), RoundedCornerShape(18.dp))
                    .clickable(onClick = onVoiceQuiz)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.RecordVoiceOver, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Đố bằng giọng nói", fontWeight = FontWeight.SemiBold)
                    Text("Từ đã học hôm nay", style = MaterialTheme.typography.bodySmall, color = x.muted)
                }
            }
        }

        // ---------- Bài ôn ----------
        item { SectionTitle("Bài ôn của bạn (${lessons.size})") }
        if (lessons.isEmpty()) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(x.card)
                        .border(1.dp, x.cardBorder, RoundedCornerShape(20.dp))
                        .clickable { onEditLesson(LessonEditorRequest(config = settings.studyConfig)) }
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.Style, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Chưa có bài ôn nào", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Bấm “Tạo bài ôn” hoặc nhấn giữ từ trong Sổ học",
                        style = MaterialTheme.typography.bodySmall, color = x.muted, textAlign = TextAlign.Center,
                    )
                }
            }
        }
        items(lessons, key = { it.id }) { l ->
            LessonCard(
                l,
                onStudy = {
                    vm.loadLesson(l.id) { content ->
                        if (content == null || (content.vocab.isEmpty() && content.grammar.isEmpty())) {
                            Toast.makeText(context, "Bài ôn trống – thêm từ vào bài trước nha", Toast.LENGTH_SHORT).show()
                        } else {
                            onStart(
                                StudySession(
                                    l.name,
                                    collapseDuplicates(
                                        content.vocab.map { StudyCard(vocab = it) } + content.grammar.map { StudyCard(grammar = it) },
                                        vocab, grammar,
                                    ).shuffled(),
                                    lessonId = l.id,
                                    config = content.lesson.config(settings.studyAutoSpeak),
                                )
                            )
                        }
                    }
                },
                onEdit = {
                    vm.loadLesson(l.id) { content ->
                        content?.let {
                            onEditLesson(
                                LessonEditorRequest(
                                    l.id, l.name, it.vocab.map { v -> v.id }.toSet(), it.grammar.map { g -> g.id }.toSet(),
                                    config = it.lesson.config(settings.studyAutoSpeak),
                                )
                            )
                        }
                    }
                },
                onDelete = { confirmDelete = l },
            )
        }
    }

    if (showStudySettings) {
        StudyConfigDialog(
            "Cài đặt ôn nhanh", settings.studyConfig,
            onChange = { vm.saveStudyConfig(null, it) },
            onDismiss = { showStudySettings = false },
        )
    }
    confirmDelete?.let { l ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Xoá bài ôn “${l.name}”?") },
            text = { Text("Chỉ xoá bài ôn, các từ vẫn còn trong sổ.") },
            confirmButton = { TextButton(onClick = { vm.deleteLesson(l.id); confirmDelete = null }) { Text("Xoá", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Huỷ") } },
        )
    }
}

/**
 * Mỗi lượt ôn chỉ hiện 1 thẻ cho mỗi từ/ngữ pháp (giữ bản mới nhất), kèm số lần đã hỏi lại
 * trên toàn sổ để báo "từ hay quên".
 */
fun collapseDuplicates(cards: List<StudyCard>, allVocab: List<VocabEntity>, allGrammar: List<GrammarEntity>): List<StudyCard> {
    val vCount = allVocab.groupingBy { keyOf(it.ko) }.eachCount()
    val gCount = allGrammar.groupingBy { grammarKeyOf(it.pattern) }.eachCount()
    return cards
        .groupBy { c -> c.vocab?.let { "v" + keyOf(it.ko) } ?: ("g" + grammarKeyOf(c.grammar?.pattern.orEmpty())) }
        .values
        .map { group ->
            val newest = group.maxBy { it.vocab?.createdAt ?: it.grammar?.createdAt ?: 0L }
            val n = newest.vocab?.let { vCount[keyOf(it.ko)] } ?: newest.grammar?.let { gCount[grammarKeyOf(it.pattern)] } ?: 1
            newest.copy(repeats = n)
        }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        Modifier.padding(start = 4.dp, top = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = LocalExtra.current.muted,
    )
}

@Composable
private fun DueHero(due: DueSummary?, onStart: () -> Unit) {
    val count = due?.count ?: 0
    val forecast = due?.forecast ?: IntArray(7)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brand.gradient)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "ĐẾN HẠN HÔM NAY", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$count", color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(6.dp))
                    Text("thẻ", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
                }
                Text(
                    when {
                        due == null -> "Đang tính lịch ôn…"
                        due.total == 0 -> "Hỏi HUY FAN vài từ, mai sẽ có thẻ để ôn"
                        count > 0 -> "Mới ${due.newCount} · Ôn lại ${count - due.newCount} · Đã thuộc ${due.matured}/${due.total}"
                        else -> "Xong hết rồi 🎉 · Ngày mai: ${forecast.getOrElse(1) { 0 }} thẻ"
                    },
                    color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = onStart, enabled = count > 0, shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White, contentColor = Brand.Indigo,
                    disabledContainerColor = Color.White.copy(alpha = 0.22f), disabledContentColor = Color.White.copy(alpha = 0.8f),
                ),
            ) {
                Icon(if (count > 0) Icons.Rounded.PlayArrow else Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (count > 0) "Ôn ngay" else "Xong", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(14.dp))
        ForecastBars(forecast)
    }
}

/** Biểu đồ cột: số thẻ đến hạn 7 ngày tới. */
@Composable
private fun ForecastBars(f: IntArray) {
    val max = (f.maxOrNull() ?: 0).coerceAtLeast(1)
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        f.forEachIndexed { i, n ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (n > 0) "$n" else "", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .fillMaxWidth()
                        .height((6 + 34f * n / max).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = if (i == 0) 0.95f else 0.38f))
                )
                val d = today.plusDays(i.toLong())
                Text(
                    if (i == 0) "Nay" else if (d.dayOfWeek.value == 7) "CN" else "T${d.dayOfWeek.value + 1}",
                    color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun PracticeTile(title: String, subtitle: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val x = LocalExtra.current
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(x.card)
            .border(1.dp, x.cardBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = color, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = x.muted, maxLines = 2)
        }
    }
}

@Composable
private fun QuickTile(label: String, count: Int, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val x = LocalExtra.current
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(x.card)
            .border(1.dp, x.cardBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text("$count", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = x.muted, maxLines = 1)
    }
}

@Composable
private fun LessonCard(l: LessonSummary, onStudy: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val x = LocalExtra.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(x.card)
            .border(1.dp, x.cardBorder, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brand.gradient),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Style, null, tint = Color.White) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(l.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    "${l.vocabCount} từ · ${l.grammarCount} ngữ pháp",
                    style = MaterialTheme.typography.bodySmall, color = x.muted,
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Sửa", tint = x.muted) }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Xoá", tint = x.muted) }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (l.studyCount == 0) "Chưa học lần nào"
                else "Đã học ${l.studyCount} lần · gần nhất ${Dates.dayLabel(Dates.dayKeyOf(l.lastStudiedAt)).substringBefore(" ·")}" +
                    if (l.lastScore >= 0) " · nhớ ${l.lastScore}/${l.total}" else "",
                style = MaterialTheme.typography.labelMedium, color = x.muted, modifier = Modifier.weight(1f),
            )
            Button(onClick = onStudy, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Học")
            }
        }
    }
}

// =============================================================================================
// Cửa sổ học (flashcard) – dùng chung cho ôn nhanh và bài ôn
// =============================================================================================

@Composable
fun StudyDialog(vm: MainViewModel, session: StudySession, onDismiss: () -> Unit) {
    val x = LocalExtra.current
    var seed by remember { mutableIntStateOf(0) }
    val deck = remember(session, seed) {
        androidx.compose.runtime.mutableStateListOf<StudyCard>().apply { addAll(if (seed == 0) session.cards else session.cards.shuffled()) }
    }
    // Thẻ bấm "Quên" → đưa xuống cuối phiên để ôn lại (tối đa 2 lần mỗi thẻ)
    val again = remember(session, seed) { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
    var index by remember(session, seed) { mutableIntStateOf(0) }
    var flipped by remember(session, seed, index) { mutableStateOf(false) }
    var known by remember(session, seed) { mutableIntStateOf(0) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    // Cài đặt của bài ôn (hoặc mặc định cho ôn nhanh)
    var config by remember(session) { mutableStateOf(session.config ?: settings.studyConfig) }
    // true: mặt trước là tiếng Hàn / đuôi câu; false (chế độ ngược): nhìn nghĩa → nói/viết từ
    var front by remember { mutableStateOf(!config.reverse) }
    LaunchedEffect(config.reverse) { front = !config.reverse }
    var recorded by remember(session, seed) { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

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
                        Text(session.title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                        Text(
                            "${session.cards.size} thẻ" + if (deck.size > session.cards.size) " · ${deck.size - session.cards.size} lượt ôn lại" else "",
                            style = MaterialTheme.typography.labelMedium, color = x.muted,
                        )
                    }
                    IconButton(onClick = { front = !front }) { Icon(Icons.Rounded.SwapHoriz, "Đổi chiều") }
                    IconButton(onClick = { seed++ }) { Icon(Icons.Rounded.Shuffle, "Xáo trộn") }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.Tune, "Cài đặt bài học") }
                }
                if (index >= deck.size) {
                    LaunchedEffect(Unit) {
                        if (!recorded) {
                            recorded = true
                            session.lessonId?.let { vm.recordStudy(it, known) }
                        }
                    }
                    StudyResult(known, session.cards.size, onAgain = { seed++ }, onDone = onDismiss)
                } else {
                    StudyCardView(
                        vm, deck[index], index, deck.size, front, flipped,
                        matchPercent = config.matchPercent,
                        drawPercent = config.drawPercent,
                        autoSpeak = config.autoSpeak,
                        onFlip = { flipped = !flipped },
                        onReveal = { flipped = true },
                        onAnswer = { grade ->
                            val card = deck[index]
                            card.vocab?.let { vm.review(it, grade) }
                            card.grammar?.let { vm.reviewGrammar(it, grade) }
                            val times = again[card.key] ?: 0
                            if (grade == Grade.AGAIN) {
                                if (times < 2) {
                                    again[card.key] = times + 1
                                    deck.add(
                                        card.copy(
                                            vocab = card.vocab?.let { it.withSrs(Srs.next(it.srs, Grade.AGAIN)) },
                                            grammar = card.grammar?.let { it.withSrs(Srs.next(it.srs, Grade.AGAIN)) },
                                        )
                                    )
                                }
                            } else if (times == 0) {
                                known++
                            }
                            index++
                        },
                    )
                }
            }
        }
    }
    if (showSettings) {
        StudyConfigDialog(
            if (session.lessonId != null) "Cài đặt bài: ${session.title}" else "Cài đặt ôn nhanh",
            config,
            onChange = { c -> config = c; vm.saveStudyConfig(session.lessonId, c) },
            onDismiss = { showSettings = false },
        )
    }
}

/** Khối chỉnh cài đặt học: chế độ, % nói đúng, % viết đúng, tự đọc. Dùng trong hộp thoại và cửa sổ tạo bài ôn. */
@Composable
fun StudyConfigEditor(config: StudyConfig, onChange: (StudyConfig) -> Unit) {
    val x = LocalExtra.current
    var match by remember(config.matchPercent) { mutableStateOf(config.matchPercent.toFloat()) }
    var draw by remember(config.drawPercent) { mutableStateOf(config.drawPercent.toFloat()) }
    Column {
        Text("Chế độ học", style = MaterialTheme.typography.labelLarge, color = x.muted)
        Segmented(
            listOf(false, true), config.reverse,
            { if (it) "Ngược: nhìn nghĩa" else "Thường: nhìn từ" },
            { r -> onChange(config.copy(reverse = r)) },
            modifier = Modifier.padding(horizontal = 0.dp),
        )
        Text(
            if (config.reverse) "Thẻ chỉ hiện nghĩa + dạng → nói/viết ra từ ${com.hankukko.huyfan.Lang.current.nameVi}."
            else "Thẻ hiện từ ${com.hankukko.huyfan.Lang.current.nameVi} → nói/viết ra nghĩa tiếng Việt.",
            style = MaterialTheme.typography.bodySmall, color = x.muted,
        )
        Spacer(Modifier.height(10.dp))
        Text("🎤 Nói đúng từ ${match.toInt()}% thì lật thẻ", style = MaterialTheme.typography.bodyLarge)
        androidx.compose.material3.Slider(
            value = match, onValueChange = { match = it },
            onValueChangeFinished = { onChange(config.copy(matchPercent = match.toInt())) },
            valueRange = 40f..100f, steps = 11,
        )
        Text("✍️ Viết tay đúng từ ${draw.toInt()}% thì lật thẻ", style = MaterialTheme.typography.bodyLarge)
        androidx.compose.material3.Slider(
            value = draw, onValueChange = { draw = it },
            onValueChangeFinished = { onChange(config.copy(drawPercent = draw.toInt())) },
            valueRange = 40f..100f, steps = 11,
        )
        Text(
            "Mic và bút chỉ lật thẻ, không tự chấm nhớ/chưa nhớ.",
            style = MaterialTheme.typography.bodySmall, color = x.muted,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Tự đọc ${com.hankukko.huyfan.Lang.current.nameVi}", style = MaterialTheme.typography.bodyLarge)
                Text("Đọc khi thẻ hiện mặt ${com.hankukko.huyfan.Lang.current.nameVi}", style = MaterialTheme.typography.bodySmall, color = x.muted)
            }
            androidx.compose.material3.Switch(checked = config.autoSpeak, onCheckedChange = { onChange(config.copy(autoSpeak = it)) })
        }
    }
}

/** Gốc từ trên thẻ ôn: "HÁN HÀN 學生 · học sinh" hoặc "TỪ MƯỢN ice cream". */
@Composable
private fun OriginBadge(hanja: String, hanViet: String, loan: String, loanRead: String = "") {
    val isHan = hanja.isNotBlank()
    val color = if (isHan) Brand.Amber else Brand.Cyan
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (isHan) "HÁN HÀN" else if (com.hankukko.huyfan.Lang.isEn) "CÁCH ĐỌC" else "TỪ MƯỢN", color = color,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.width(10.dp))
        if (isHan) {
            Text(hanja, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            if (hanViet.isNotBlank()) {
                Text("  ·  $hanViet", color = color, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Text(loan, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            if (loanRead.isNotBlank()) {
                Text("  ·  $loanRead", color = color, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun StudyConfigDialog(title: String, config: StudyConfig, onChange: (StudyConfig) -> Unit, onDismiss: () -> Unit) {
    var local by remember(config) { mutableStateOf(config) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2) },
        text = { StudyConfigEditor(local) { local = it; onChange(it) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Xong") } },
    )
}

/** Nút chấm điểm: tên + lần ôn tới (vd "Nhớ · 3 ngày"). */
@Composable
private fun GradeButton(grade: Grade, next: String, color: Color, modifier: Modifier, filled: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (filled) color else color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = if (filled) 0f else 0.45f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(grade.label, fontWeight = FontWeight.Bold, color = if (filled) Color.White else color, maxLines = 1)
        Text(next, style = MaterialTheme.typography.labelSmall, color = if (filled) Color.White.copy(alpha = 0.85f) else color.copy(alpha = 0.8f), maxLines = 1)
    }
}

@Composable
private fun ColumnScope.StudyResult(known: Int, total: Int, onAgain: () -> Unit, onDone: () -> Unit) {
    val x = LocalExtra.current
    Column(
        Modifier.weight(1f).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.EmojiEvents, null, tint = Brand.Amber, modifier = Modifier.size(84.dp))
        Spacer(Modifier.height(12.dp))
        Text("Nhớ $known/$total thẻ!", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (known == total) "Đỉnh quá ông ơi! 최고!" else "Ôn lại mấy thẻ chưa nhớ nha, cố lên! 화이팅!",
            color = x.muted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onAgain) { Icon(Icons.Rounded.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Học lại") }
            Button(onClick = onDone) { Text("Xong") }
        }
    }
}

@Composable
private fun ColumnScope.StudyCardView(
    vm: MainViewModel,
    card: StudyCard,
    index: Int,
    total: Int,
    koFront: Boolean,
    flipped: Boolean,
    matchPercent: Int,
    drawPercent: Int,
    autoSpeak: Boolean,
    onFlip: () -> Unit,
    onReveal: () -> Unit,
    onAnswer: (Grade) -> Unit,
) {
    val x = LocalExtra.current
    val v = card.vocab
    val g = card.grammar
    val engineState by vm.engine.state.collectAsStateWithLifecycle()
    var listening by remember(card.key) { mutableStateOf(false) }
    var feedback by remember(card.key) { mutableStateOf<Pair<String, Boolean>?>(null) }
    var ruleOpen by remember(card.key) { mutableStateOf<String?>(null) }
    val ruleLinks = remember(card.key) {
        v?.let { com.hankukko.huyfan.data.RuleLinks.forVocab(it.ko) }
            ?: g?.let { com.hankukko.huyfan.data.RuleLinks.forGrammar(it.pattern, it.form) } ?: emptyList()
    }
    // Bảng viết tay
    var penOpen by remember(card.key) { mutableStateOf(false) }
    var inkBusy by remember(card.key) { mutableStateOf(false) }
    var inkBusyText by remember(card.key) { mutableStateOf("Đang nhận dạng chữ…") }
    var inkResult by remember(card.key) { mutableStateOf<Pair<String, Boolean>?>(null) }
    val inkStrokes = remember(card.key) { androidx.compose.runtime.mutableStateListOf<List<com.hankukko.huyfan.ink.InkPoint>>() }
    // Đáp án cần nói: mặt trước tiếng Hàn → nói nghĩa (tiếng Việt); chế độ ngược → nói từ tiếng Hàn
    val expected = when {
        v != null -> if (koFront) v.vi else v.ko
        g != null -> if (koFront) g.meaning else g.pattern
        else -> ""
    }
    val answerLang = if (koFront) com.hankukko.huyfan.assistant.LANG_VI else com.hankukko.huyfan.assistant.LANG_KO
    fun startMic() {
        if (listening) { vm.engine.stopListening(); return }
        listening = true
        feedback = null
        vm.engine.captureOnce(answerLang) { heard ->
            listening = false
            if (heard == null) {
                feedback = "Chưa nghe rõ, bấm mic nói lại nha" to false
            } else {
                val score = com.hankukko.huyfan.util.Similarity.score(heard, expected)
                if (score >= matchPercent) {
                    feedback = "Đúng rồi! “$heard” · $score%" to true
                    onReveal()
                } else {
                    feedback = "Chưa khớp ($score%) · nghe được: “$heard”" to false
                }
            }
        }
    }
    // Mặt đang hiện là tiếng Hàn → tự đọc
    LaunchedEffect(card.key, flipped, koFront) {
        if (autoSpeak && !listening && koFront != flipped) {
            if (v != null) vm.speakKorean(v.ko)
            else if (g != null && g.exampleKo.isNotBlank() && flipped) vm.speakKorean(g.exampleKo)
        }
    }

    LinearProgressIndicator(
        progress = { (index + 1f) / total },
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
        color = MaterialTheme.colorScheme.primary,
        trackColor = x.cardBorder,
    )
    Text("${index + 1} / $total", style = MaterialTheme.typography.labelMedium, color = x.muted, modifier = Modifier.padding(top = 6.dp))
    Spacer(Modifier.height(10.dp))

    if (penOpen) {
        val question = when {
            v != null -> if (koFront) v.ko else v.vi
            g != null -> if (koFront) g.pattern else g.meaning
            else -> ""
        }
        val inkLang = if (koFront) "vi" else "ko"
        DrawPad(
            prompt = question,
            hint = if (koFront) "Viết nghĩa tiếng Việt · đúng từ $drawPercent% thì lật thẻ"
            else "Viết từ ${com.hankukko.huyfan.Lang.current.nameVi} · đúng từ $drawPercent% thì lật thẻ",
            busy = inkBusy,
            busyText = inkBusyText,
            result = inkResult,
            onCheck = { strokes ->
                inkBusy = true
                inkResult = null
                inkBusyText = "Đang nhận dạng chữ…"
                vm.inkModelReady(inkLang) { ready ->
                    if (!ready) inkBusyText = "Lần đầu: đang tải bộ nhận dạng chữ viết (~20MB)…"
                }
                vm.recognizeInk(inkLang, strokes) { r ->
                    inkBusy = false
                    r.onSuccess { cands ->
                        if (cands.isEmpty()) {
                            inkResult = "Chưa đọc được chữ, viết rõ hơn nha" to false
                        } else {
                            val best = cands.take(5).maxBy { com.hankukko.huyfan.util.Similarity.score(it, expected) }
                            val score = com.hankukko.huyfan.util.Similarity.score(best, expected)
                            if (score >= drawPercent) {
                                feedback = "Viết đúng! “$best” · $score%" to true
                                inkStrokes.clear()
                                penOpen = false
                                onReveal()
                            } else {
                                inkResult = "Chưa khớp ($score%) · đọc được: “$best”" to false
                            }
                        }
                    }.onFailure { e -> inkResult = "Lỗi nhận dạng: ${e.message}" to false }
                }
            },
            onClose = { penOpen = false },
            modifier = Modifier.weight(1f),
            strokes = inkStrokes,
        )
    } else {
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(450), label = "flip")
    val showFront = rotation <= 90f
    val accent = if (g != null) listOf(Color(0xFF8A4DFF), Color(0xFFF2338F)) else listOf(Color(0xFF4F5BFF), Color(0xFF9B5CFF))
    Box(Modifier.fillMaxWidth().weight(1f)) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * density
            }
            .clip(RoundedCornerShape(28.dp))
            .background(if (showFront) Brush.linearGradient(accent) else SolidColor(x.card))
            .border(1.dp, x.cardBorder, RoundedCornerShape(28.dp))
            .clickable(onClick = onFlip),
        contentAlignment = Alignment.Center,
    ) {
        if (showFront) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                if (g != null) Text("NGỮ PHÁP", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp)
                if (card.repeats > 1) {
                    Row(
                        Modifier
                            .padding(bottom = 10.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Repeat, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Hay quên · đã hỏi lại ${card.repeats} lần", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
                val main = when {
                    v != null -> if (koFront) v.ko else v.vi
                    g != null -> if (koFront) g.pattern else g.meaning
                    else -> ""
                }
                Text(
                    main, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    fontSize = if (koFront) 42.sp else 28.sp, lineHeight = if (koFront) 50.sp else 34.sp,
                )
                if (v != null && koFront && v.romaja.isNotBlank()) Text(v.romaja, color = Color.White.copy(alpha = 0.75f), fontStyle = FontStyle.Italic)
                // Chế độ ngược: chỉ hiện nghĩa + dạng (từ loại / dạng ngữ pháp), giấu mọi gợi ý lộ đáp án
                if (!koFront) {
                    val form = v?.pos ?: g?.form ?: ""
                    if (form.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            form,
                            Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            color = Color.White, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    if (koFront) "Chạm để lật · hoặc bấm mic nói nghĩa" else "Nói từ ${com.hankukko.huyfan.Lang.current.nameVi} · hoặc chạm để lật",
                    color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium,
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { rotationY = 180f }.padding(24.dp),
            ) {
                if (v != null) {
                    Text(if (koFront) v.vi else v.ko, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, lineHeight = 36.sp,
                        color = if (koFront) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary)
                    if (!koFront && v.romaja.isNotBlank()) Text(v.romaja, color = x.muted, fontStyle = FontStyle.Italic)
                    if (v.hanja.isNotBlank() || v.loan.isNotBlank() || (com.hankukko.huyfan.Lang.isEn && v.loanRead.isNotBlank())) {
                        Spacer(Modifier.height(10.dp))
                        if (com.hankukko.huyfan.Lang.isEn) OriginBadge("", "", v.loanRead) else OriginBadge(v.hanja, v.hanViet, v.loan, v.loanRead)
                    }
                    if (v.breakdown.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("🧩 ${v.breakdown}", color = Brand.Teal, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (v.exampleKo.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Text(v.exampleKo, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                        Text(v.exampleVi, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = x.muted)
                    }
                    Spacer(Modifier.height(12.dp))
                    IconButton(onClick = { vm.speakKorean(v.exampleKo.ifBlank { v.ko }) }) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Nghe", tint = MaterialTheme.colorScheme.primary)
                    }
                    RuleChips(ruleLinks, { ruleOpen = it })
                } else if (g != null) {
                    Text(if (koFront) g.meaning else g.pattern, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, lineHeight = 32.sp,
                        color = if (koFront) MaterialTheme.colorScheme.onSurface else Brand.Violet)
                    Spacer(Modifier.height(10.dp))
                    if (g.form.isNotBlank()) Text("Dạng: ${g.form}", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    if (g.combine.isNotBlank()) Text("Kết hợp: ${g.combine}", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    if (g.breakdown.isNotBlank()) Text("🧩 ${g.breakdown}", color = Brand.Teal, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    if (g.exampleKo.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Text(g.exampleKo, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                        Text(g.exampleVi, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = x.muted)
                        IconButton(onClick = { vm.speakKorean(g.exampleKo) }) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Nghe", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    RuleChips(ruleLinks, { ruleOpen = it })
                }
            }
        }
    }
        // Nút bút: viết tay đáp án để lật thẻ
        IconButton(
            onClick = { penOpen = true; inkResult = null },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(46.dp)
                .clip(CircleShape)
                .background(if (showFront) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        ) {
            Icon(Icons.Rounded.Edit, "Viết tay", tint = if (showFront) Color.White else MaterialTheme.colorScheme.primary)
        }
    }

    ruleOpen?.let { id -> RuleSheet(id, onSpeak = { vm.speakKorean(it) }, onAsk = { ruleOpen = null }, onDismiss = { ruleOpen = null }) }

    // Kết quả so khớp giọng nói / chữ đang nghe
    val info = when {
        listening -> (engineState.partial.ifBlank { if (koFront) "Nói nghĩa tiếng Việt…" else "Nói từ ${com.hankukko.huyfan.Lang.current.nameVi}…" }) to null
        feedback != null -> feedback!!.first to feedback!!.second
        else -> null
    }
    Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
        if (info != null) {
            Text(
                info.first, textAlign = TextAlign.Center, maxLines = 2,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                color = when (info.second) {
                    true -> Brand.Green
                    false -> Brand.Red
                    null -> MaterialTheme.colorScheme.primary
                },
            )
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val srs = v?.srs ?: g?.srs ?: SrsState()
        GradeButton(Grade.AGAIN, Srs.intervalLabel(srs, Grade.AGAIN), Brand.Red, Modifier.weight(1f)) { onAnswer(Grade.AGAIN) }
        // Mic giữa: nói đáp án → đúng thì lật thẻ (không tự chấm điểm)
        com.hankukko.huyfan.ui.components.MicButton(listening = listening, onClick = { startMic() }, size = 58.dp)
        GradeButton(Grade.GOOD, Srs.intervalLabel(srs, Grade.GOOD), Brand.Green, Modifier.weight(1f), filled = true) { onAnswer(Grade.GOOD) }
        GradeButton(Grade.EASY, Srs.intervalLabel(srs, Grade.EASY), Brand.Blue, Modifier.weight(1f)) { onAnswer(Grade.EASY) }
    }
    }
}

// =============================================================================================
// Cửa sổ tạo / sửa bài ôn: tên + chọn từ, ngữ pháp
// =============================================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LessonEditorDialog(vm: MainViewModel, request: LessonEditorRequest, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val x = LocalExtra.current
    val vocab by vm.vocab.collectAsStateWithLifecycle()
    val grammar by vm.grammar.collectAsStateWithLifecycle()
    var name by remember {
        mutableStateOf(request.name.ifBlank { "Bài ôn " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM")) })
    }
    var selV by remember { mutableStateOf(request.vocabIds) }
    var config by remember { mutableStateOf(request.config) }
    var showConfig by remember { mutableStateOf(false) }
    var selG by remember { mutableStateOf(request.grammarIds) }
    var tab by remember { mutableStateOf(BookTab.VOCAB) }
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val vGroups = remember(vocab, q) {
        vocab.filter { q.isEmpty() || it.ko.contains(q) || it.vi.lowercase().contains(q) }
            .groupBy { it.day }.toSortedMap(compareByDescending { it })
    }
    val gGroups = remember(grammar, q) {
        grammar.filter { q.isEmpty() || it.pattern.lowercase().contains(q) || it.meaning.lowercase().contains(q) }
            .groupBy { it.day }.toSortedMap(compareByDescending { it })
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
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Đóng") }
                    Text(if (request.lessonId == null) "Tạo bài ôn" else "Sửa bài ôn", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Tên bài ôn") }, singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                // Cài đặt riêng của bài ôn: chế độ ngược, % nói đúng, % viết đúng
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(x.card)
                        .border(1.dp, x.cardBorder, RoundedCornerShape(14.dp))
                        .clickable { showConfig = !showConfig }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Cài đặt bài học", fontWeight = FontWeight.SemiBold)
                        Text(
                            (if (config.reverse) "Chế độ ngược" else "Chế độ thường") +
                                " · nói ${config.matchPercent}% · viết ${config.drawPercent}%",
                            style = MaterialTheme.typography.bodySmall, color = x.muted,
                        )
                    }
                    Icon(if (showConfig) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                }
                if (showConfig) {
                    Box(Modifier.padding(horizontal = 16.dp)) { StudyConfigEditor(config) { config = it } }
                }
                Segmented(listOf(BookTab.VOCAB, BookTab.GRAMMAR), tab, { t -> if (t == BookTab.VOCAB) "Từ vựng (${selV.size})" else "Ngữ pháp (${selG.size})" }, { tab = it })
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Tìm…") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        val today = Dates.todayKey()
                        if (tab == BookTab.VOCAB) selV = selV + vocab.filter { it.day == today }.map { it.id }
                        else selG = selG + grammar.filter { it.day == today }.map { it.id }
                    }) { Text("+ Chọn mục hôm nay") }
                    TextButton(onClick = { if (tab == BookTab.VOCAB) selV = emptySet() else selG = emptySet() }) { Text("Bỏ chọn hết") }
                }
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
                ) {
                    if (tab == BookTab.VOCAB) {
                        vGroups.forEach { (day, list) ->
                            stickyHeader(key = "h$day") {
                                val all = list.all { it.id in selV }
                                PickerDayHeader(day, list.size, all) { on ->
                                    selV = if (on) selV + list.map { it.id } else selV - list.map { it.id }.toSet()
                                }
                            }
                            items(list, key = { it.id }) { w ->
                                PickerRow(w.ko, w.vi, w.id in selV) { selV = if (w.id in selV) selV - w.id else selV + w.id }
                            }
                        }
                        if (vGroups.isEmpty()) item { Text("Sổ từ trống – hỏi HUY FAN vài từ trước nha.", color = x.muted, modifier = Modifier.padding(16.dp)) }
                    } else {
                        gGroups.forEach { (day, list) ->
                            stickyHeader(key = "g$day") {
                                val all = list.all { it.id in selG }
                                PickerDayHeader(day, list.size, all) { on ->
                                    selG = if (on) selG + list.map { it.id } else selG - list.map { it.id }.toSet()
                                }
                            }
                            items(list, key = { "g${it.id}" }) { g ->
                                PickerRow(g.pattern, g.meaning, g.id in selG) { selG = if (g.id in selG) selG - g.id else selG + g.id }
                            }
                        }
                        if (gGroups.isEmpty()) item { Text("Chưa có ngữ pháp – hỏi HUY FAN “-고 싶다 là gì?” nha.", color = x.muted, modifier = Modifier.padding(16.dp)) }
                    }
                }
                Button(
                    onClick = {
                        when {
                            name.isBlank() -> Toast.makeText(context, "Đặt tên bài ôn đã nha", Toast.LENGTH_SHORT).show()
                            selV.isEmpty() && selG.isEmpty() -> Toast.makeText(context, "Chọn ít nhất 1 từ hoặc ngữ pháp", Toast.LENGTH_SHORT).show()
                            else -> {
                                vm.saveLesson(request.lessonId, name, selV, selG, config)
                                Toast.makeText(context, "Đã lưu “${name.trim()}” – bấm Học để ôn", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(if (request.lessonId == null) "Tạo bài ôn (${selV.size + selG.size} mục)" else "Lưu (${selV.size + selG.size} mục)")
                }
            }
        }
    }
}

@Composable
private fun PickerDayHeader(day: String, count: Int, allSelected: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .clickable { onToggle(!allSelected) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectMark(allSelected) { onToggle(!allSelected) }
        Spacer(Modifier.width(10.dp))
        Text(Dates.dayLabel(day), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("$count mục", style = MaterialTheme.typography.labelMedium, color = LocalExtra.current.muted)
    }
}

@Composable
private fun PickerRow(title: String, subtitle: String, selected: Boolean, onToggle: () -> Unit) {
    val x = LocalExtra.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else x.card)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectMark(selected, onToggle)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = x.muted, maxLines = 1)
        }
    }
}
