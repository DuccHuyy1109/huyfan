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


# ---------- DAO: tìm theo khoá chuẩn hoá (bỏ dấu cách, gạch) trên toàn sổ ----------
patch('data/Daos.kt', [
    ('''    @Query("SELECT * FROM vocab WHERE ko = :ko AND day = :day LIMIT 1")
    suspend fun find(ko: String, day: String): VocabEntity?''',
     '''    @Query("SELECT * FROM vocab WHERE ko = :ko AND day = :day LIMIT 1")
    suspend fun find(ko: String, day: String): VocabEntity?

    /** Tìm trên toàn sổ, bỏ qua dấu cách: "설거지 하다" = "설거지하다". */
    @Query("SELECT * FROM vocab WHERE REPLACE(ko, ' ', '') = :koKey ORDER BY createdAt DESC")
    suspend fun findAllByKey(koKey: String): List<VocabEntity>'''),
    ('''    @Query("SELECT * FROM grammar WHERE pattern = :pattern AND day = :day LIMIT 1")
    suspend fun find(pattern: String, day: String): GrammarEntity?''',
     '''    @Query("SELECT * FROM grammar WHERE pattern = :pattern AND day = :day LIMIT 1")
    suspend fun find(pattern: String, day: String): GrammarEntity?

    /** Tìm trên toàn sổ, bỏ qua dấu cách và gạch nối: "-고 싶다" = "고싶다". */
    @Query("SELECT * FROM grammar WHERE REPLACE(REPLACE(REPLACE(pattern, ' ', ''), '-', ''), '~', '') = :key ORDER BY createdAt DESC")
    suspend fun findAllByKey(key: String): List<GrammarEntity>'''),
])

# ---------- Repository: gộp trùng trên toàn sổ ----------
patch('data/Repositories.kt', [
    # vocab
    ('''        for (it in items) {
            if (it.ko.isBlank() || it.vi.isBlank()) continue
            val existing = dao.find(it.ko.trim(), day)
            if (existing != null) {
                val merged = existing.copy(''', '''        for (it in items) {
            if (it.ko.isBlank() || it.vi.isBlank()) continue
            val existing = findSame(it.ko)
            if (existing != null) {
                val forgot = existing.day != day
                val merged = existing.copy(
                    // Hỏi lại từ cũ → chuyển về danh sách hôm nay, hạ mức thuộc để ôn lại
                    day = day,
                    createdAt = now,
                    mastery = if (forgot) (existing.mastery - 1).coerceAtLeast(0) else existing.mastery,'''),
    ('''    suspend fun findOn(ko: String, day: String): VocabEntity? = dao.find(ko.trim(), day)''',
     '''    suspend fun findOn(ko: String, day: String): VocabEntity? = dao.find(ko.trim(), day)

    /**
     * Mục trùng trên toàn sổ (bỏ qua dấu cách). Nếu lỡ có nhiều bản trùng từ trước → gộp về 1 bản
     * (giữ bản mới nhất, cộng dồn số lần tra) và xoá các bản thừa.
     */
    suspend fun findSame(ko: String): VocabEntity? {
        val all = dao.findAllByKey(keyOf(ko))
        if (all.size <= 1) return all.firstOrNull()
        val keep = all.first().copy(lookupCount = all.sumOf { it.lookupCount }, starred = all.any { it.starred })
        dao.update(keep)
        val extra = all.drop(1).map { it.id }
        lessons.deleteItemRefs(LessonItem.VOCAB, extra)
        dao.deleteByIds(extra)
        return keep
    }'''),
    # grammar
    ('''        for (it in items) {
            if (it.pattern.isBlank() || it.meaning.isBlank()) continue
            val existing = dao.find(it.pattern.trim(), day)
            if (existing != null) {
                val merged = existing.copy(''', '''        for (it in items) {
            if (it.pattern.isBlank() || it.meaning.isBlank()) continue
            val existing = findSame(it.pattern)
            if (existing != null) {
                val forgot = existing.day != day
                val merged = existing.copy(
                    day = day,
                    createdAt = now,
                    mastery = if (forgot) (existing.mastery - 1).coerceAtLeast(0) else existing.mastery,'''),
    ('''    suspend fun findOn(pattern: String, day: String): GrammarEntity? = dao.find(pattern.trim(), day)''',
     '''    suspend fun findOn(pattern: String, day: String): GrammarEntity? = dao.find(pattern.trim(), day)

    suspend fun findSame(pattern: String): GrammarEntity? {
        val all = dao.findAllByKey(grammarKeyOf(pattern))
        if (all.size <= 1) return all.firstOrNull()
        val keep = all.first().copy(lookupCount = all.sumOf { it.lookupCount }, starred = all.any { it.starred })
        dao.update(keep)
        val extra = all.drop(1).map { it.id }
        lessons.deleteItemRefs(LessonItem.GRAMMAR, extra)
        dao.deleteByIds(extra)
        return keep
    }'''),
    ('''class VocabRepository(private val dao: VocabDao, private val lessons: LessonDao) {''',
     '''/** Khoá so trùng: bỏ mọi dấu cách. */
fun keyOf(ko: String): String = ko.replace(" ", "").trim()

/** Khoá so trùng ngữ pháp: bỏ dấu cách, gạch nối, dấu ~. */
fun grammarKeyOf(p: String): String = p.replace(" ", "").replace("-", "").replace("~", "").trim()

class VocabRepository(private val dao: VocabDao, private val lessons: LessonDao) {'''),
])

# ---------- Thùng rác trong chat: tìm theo khoá trên toàn sổ ----------
patch('assistant/ConversationEngine.kt', [
    ('''                    c.vocab.findOn(item.ko, day)?.let { e -> c.sheets.deleteRemote(c.vocab.deleteMany(listOf(e.id))) }''',
     '''                    c.vocab.findSame(item.ko)?.let { e -> c.sheets.deleteRemote(c.vocab.deleteMany(listOf(e.id))) }'''),
    ('''                    c.grammar.findOn(item.pattern, day)?.let { e -> c.sheets.deleteRemote(c.grammar.deleteMany(listOf(e.id))) }''',
     '''                    c.grammar.findSame(item.pattern)?.let { e -> c.sheets.deleteRemote(c.grammar.deleteMany(listOf(e.id))) }'''),
    ('''            val day = Dates.dayKeyOf(m.createdAt)
''', ''),
])
print('ok')
