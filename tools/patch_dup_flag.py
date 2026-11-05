import sys, os

root = sys.argv[1]


def patch(rel, pairs):
    p = os.path.join(root, rel)
    s = open(p, encoding='utf-8').read()
    for old, new in pairs:
        if old not in s:
            raise SystemExit(rel + ' NOT FOUND: ' + old[:100])
        s = s.replace(old, new, 1)
    open(p, 'w', encoding='utf-8').write(s)


# ---------- Repository: chỉ gộp trong cùng ngày; khác ngày → thêm mới ----------
patch('data/Repositories.kt', [
    ('''            val existing = findSame(it.ko)
            if (existing != null) {
                val forgot = existing.day != day
                val merged = existing.copy(
                    // Hỏi lại từ cũ → chuyển về danh sách hôm nay, hạ mức thuộc để ôn lại
                    day = day,
                    createdAt = now,
                    mastery = if (forgot) (existing.mastery - 1).coerceAtLeast(0) else existing.mastery,''',
     '''            // Cùng ngày → gộp (tăng số lần tra). Khác ngày → thêm dòng mới, sổ sẽ đánh dấu "Trùng".
            val existing = findSameOnDay(it.ko, day)
            if (existing != null) {
                val merged = existing.copy('''),
    ('''    suspend fun findSame(ko: String): VocabEntity? {
        val all = dao.findAllByKey(keyOf(ko))
        if (all.size <= 1) return all.firstOrNull()''',
     '''    suspend fun findSameOnDay(ko: String, day: String): VocabEntity? {
        val all = dao.findAllByKey(keyOf(ko)).filter { it.day == day }
        if (all.size <= 1) return all.firstOrNull()'''),
    ('''            val existing = findSame(it.pattern)
            if (existing != null) {
                val forgot = existing.day != day
                val merged = existing.copy(
                    day = day,
                    createdAt = now,
                    mastery = if (forgot) (existing.mastery - 1).coerceAtLeast(0) else existing.mastery,''',
     '''            val existing = findSameOnDay(it.pattern, day)
            if (existing != null) {
                val merged = existing.copy('''),
    ('''    suspend fun findSame(pattern: String): GrammarEntity? {
        val all = dao.findAllByKey(grammarKeyOf(pattern))
        if (all.size <= 1) return all.firstOrNull()''',
     '''    suspend fun findSameOnDay(pattern: String, day: String): GrammarEntity? {
        val all = dao.findAllByKey(grammarKeyOf(pattern)).filter { it.day == day }
        if (all.size <= 1) return all.firstOrNull()'''),
])

patch('assistant/ConversationEngine.kt', [
    ('''        scope.launch {
            if (kind == LessonItem.VOCAB) {''', '''        scope.launch {
            val day = Dates.dayKeyOf(m.createdAt)
            if (kind == LessonItem.VOCAB) {'''),
    ('''c.vocab.findSame(item.ko)?.let''', '''c.vocab.findSameOnDay(item.ko, day)?.let'''),
    ('''c.grammar.findSame(item.pattern)?.let''', '''c.grammar.findSameOnDay(item.pattern, day)?.let'''),
])

# ---------- Sổ học: nhãn "Trùng" + bộ lọc "Trùng lặp" ----------
patch('ui/screens/VocabScreen.kt', [
    ('''enum class VocabFilter(val label: String) { ALL("Tất cả"), TODAY("Hôm nay"), WEEK("7 ngày"), STARRED("Đánh dấu"), WEAK("Chưa thuộc") }''',
     '''enum class VocabFilter(val label: String) {
    ALL("Tất cả"), TODAY("Hôm nay"), WEEK("7 ngày"), DUP("Trùng lặp"), STARRED("Đánh dấu"), WEAK("Chưa thuộc")
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
}.getOrDefault(day)'''),
    ('''        VocabFilter.STARRED -> filter { it.starred }
        VocabFilter.WEAK -> filter { it.mastery < 3 }
    }
}

fun List<GrammarEntity>.filteredGrammar''', '''        VocabFilter.STARRED -> filter { it.starred }
        VocabFilter.WEAK -> filter { it.mastery < 3 }
        VocabFilter.DUP -> { val d = dupDaysOf(this); filter { it.id in d } }
    }
}

fun List<GrammarEntity>.filteredGrammar'''),
    ('''        VocabFilter.STARRED -> filter { it.starred }
        VocabFilter.WEAK -> filter { it.mastery < 3 }
    }
}

@OptIn''', '''        VocabFilter.STARRED -> filter { it.starred }
        VocabFilter.WEAK -> filter { it.mastery < 3 }
        VocabFilter.DUP -> { val d = dupDaysOfGrammar(this); filter { it.id in d } }
    }
}

@OptIn'''),
    ('''    val vocabGroups = remember(vocabList) {''', '''    val vocabDup = remember(all) { dupDaysOf(all) }
    val grammarDup = remember(allGrammar) { dupDaysOfGrammar(allGrammar) }
    val vocabGroups = remember(vocabList) {'''),
    ('''                            WordCard(
                                w,
                                selecting = selecting,''', '''                            WordCard(
                                w,
                                dupDays = vocabDup[w.id].orEmpty(),
                                selecting = selecting,'''),
    ('''                            GrammarCard(
                                g,
                                selecting = selecting,''', '''                            GrammarCard(
                                g,
                                dupDays = grammarDup[g.id].orEmpty(),
                                selecting = selecting,'''),
    ('''private fun WordCard(
    w: VocabEntity,
    selecting: Boolean,''', '''private fun WordCard(
    w: VocabEntity,
    dupDays: List<String>,
    selecting: Boolean,'''),
    ('''private fun GrammarCard(
    g: GrammarEntity,
    selecting: Boolean,''', '''private fun GrammarCard(
    g: GrammarEntity,
    dupDays: List<String>,
    selecting: Boolean,'''),
    ('''            if (w.lookupCount > 1) Pill("tra ${w.lookupCount} lần", color = Brand.Red)
            MasteryDots(w.mastery)
        }''', '''            if (w.lookupCount > 1) Pill("tra ${w.lookupCount} lần", color = Brand.Red)
            MasteryDots(w.mastery)
        }
        if (dupDays.isNotEmpty()) DupBadge(dupDays)'''),
    ('''            if (g.form.isNotBlank()) Pill(g.form, color = Brand.Violet)
            MasteryDots(g.mastery)
        }''', '''            if (g.form.isNotBlank()) Pill(g.form, color = Brand.Violet)
            MasteryDots(g.mastery)
        }
        if (dupDays.isNotEmpty()) DupBadge(dupDays)'''),
    ('''@Composable
fun MasteryDots(level: Int) {''', '''/** Nhãn cảnh báo trùng: từ này đã học ở ngày khác → dễ quên, nên ôn kỹ. */
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
fun MasteryDots(level: Int) {'''),
    ('''import androidx.compose.material.icons.rounded.RadioButtonUnchecked''',
     '''import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Repeat'''),
    ('''import com.hankukko.huyfan.data.VocabEntity''', '''import com.hankukko.huyfan.data.VocabEntity
import com.hankukko.huyfan.data.grammarKeyOf
import com.hankukko.huyfan.data.keyOf'''),
])

# ---------- Ôn tập: ô "Hay quên", thẻ không lặp, nhãn "Đã hỏi lại N lần" ----------
patch('ui/screens/ReviewScreen.kt', [
    ('''data class StudyCard(val vocab: VocabEntity? = null, val grammar: GrammarEntity? = null) {''',
     '''data class StudyCard(val vocab: VocabEntity? = null, val grammar: GrammarEntity? = null, val repeats: Int = 1) {'''),
    ('''    TODAY("Từ hôm nay", Icons.Rounded.Today),
    WEEK("Từ 7 ngày", Icons.Rounded.History),
    WEAK("Chưa thuộc", Icons.Rounded.School),
    GRAMMAR("Ngữ pháp", Icons.Rounded.AccountTree),
    STARRED("Có sao", Icons.Rounded.Star),
    ALL("Tất cả", Icons.Rounded.AutoAwesome),''', '''    TODAY("Từ hôm nay", Icons.Rounded.Today),
    WEEK("Từ 7 ngày", Icons.Rounded.History),
    WEAK("Chưa thuộc", Icons.Rounded.School),
    DUP("Hay quên", Icons.Rounded.Repeat),
    GRAMMAR("Ngữ pháp", Icons.Rounded.AccountTree),
    ALL("Tất cả", Icons.Rounded.AutoAwesome),'''),
    ('''    fun quickCards(q: Quick): List<StudyCard> = when (q) {
        Quick.TODAY -> vocab.filtered(VocabFilter.TODAY).map { StudyCard(vocab = it) }
        Quick.WEEK -> vocab.filtered(VocabFilter.WEEK).map { StudyCard(vocab = it) }
        Quick.WEAK -> vocab.filtered(VocabFilter.WEAK).map { StudyCard(vocab = it) } +
            grammar.filteredGrammar(VocabFilter.WEAK).map { StudyCard(grammar = it) }
        Quick.GRAMMAR -> grammar.map { StudyCard(grammar = it) }
        Quick.STARRED -> vocab.filtered(VocabFilter.STARRED).map { StudyCard(vocab = it) } +
            grammar.filteredGrammar(VocabFilter.STARRED).map { StudyCard(grammar = it) }
        Quick.ALL -> vocab.map { StudyCard(vocab = it) } + grammar.map { StudyCard(grammar = it) }
    }''', '''    fun quickCards(q: Quick): List<StudyCard> = when (q) {
        Quick.TODAY -> vocab.filtered(VocabFilter.TODAY).map { StudyCard(vocab = it) }
        Quick.WEEK -> vocab.filtered(VocabFilter.WEEK).map { StudyCard(vocab = it) }
        Quick.WEAK -> vocab.filtered(VocabFilter.WEAK).map { StudyCard(vocab = it) } +
            grammar.filteredGrammar(VocabFilter.WEAK).map { StudyCard(grammar = it) }
        Quick.DUP -> vocab.filtered(VocabFilter.DUP).map { StudyCard(vocab = it) } +
            grammar.filteredGrammar(VocabFilter.DUP).map { StudyCard(grammar = it) }
        Quick.GRAMMAR -> grammar.map { StudyCard(grammar = it) }
        Quick.ALL -> vocab.map { StudyCard(vocab = it) } + grammar.map { StudyCard(grammar = it) }
    }.let { collapseDuplicates(it, vocab, grammar) }'''),
    ('''@Composable
private fun SectionTitle(text: String) {''', '''/**
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
private fun SectionTitle(text: String) {'''),
    # bài ôn: cũng gộp trùng
    ('''                                    (content.vocab.map { StudyCard(vocab = it) } + content.grammar.map { StudyCard(grammar = it) }).shuffled(),''',
     '''                                    collapseDuplicates(
                                        content.vocab.map { StudyCard(vocab = it) } + content.grammar.map { StudyCard(grammar = it) },
                                        vocab, grammar,
                                    ).shuffled(),'''),
    # nhãn trên thẻ
    ('''                if (g != null) Text("NGỮ PHÁP", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp)''',
     '''                if (g != null) Text("NGỮ PHÁP", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp)
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
                }'''),
    ('''import androidx.compose.material.icons.rounded.RecordVoiceOver''', '''import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Repeat'''),
    ('''import com.hankukko.huyfan.data.VocabEntity''', '''import com.hankukko.huyfan.data.VocabEntity
import com.hankukko.huyfan.data.grammarKeyOf
import com.hankukko.huyfan.data.keyOf'''),
])
print('ok')
