package com.hankukko.huyfan.data

import com.hankukko.huyfan.ai.AssistantReply
import com.hankukko.huyfan.ai.GrammarItem
import com.hankukko.huyfan.ai.Turn
import com.hankukko.huyfan.ai.VocabItem
import com.hankukko.huyfan.util.Dates
import kotlinx.coroutines.flow.Flow

/** Khoá so trùng: bỏ mọi dấu cách. */
fun keyOf(ko: String): String = ko.replace(" ", "").trim()

/** Khoá so trùng ngữ pháp: bỏ dấu cách, gạch nối, dấu ~. */
fun grammarKeyOf(p: String): String = p.replace(" ", "").replace("-", "").replace("~", "").trim()

class VocabRepository(private val dao: VocabDao, private val lessons: LessonDao) {

    val all: Flow<List<VocabEntity>> = dao.observeAll()

    suspend fun today(): List<VocabEntity> = dao.byDay(Dates.todayKey())

    suspend fun recent(days: Long, limit: Int = 60): List<VocabEntity> = dao.since(Dates.daysAgoKey(days), limit)

    suspend fun allNow(): List<VocabEntity> = dao.all()

    suspend fun countToday(): Int = dao.countByDay(Dates.todayKey())

    /**
     * Lưu các từ AI trả về vào sổ hôm nay. Từ đã có trong hôm nay → tăng số lần tra,
     * bổ sung trường còn trống thay vì tạo bản trùng.
     */
    suspend fun saveFromAi(items: List<VocabItem>): List<VocabEntity> {
        val day = Dates.todayKey()
        val now = System.currentTimeMillis()
        val saved = mutableListOf<VocabEntity>()
        for (it in items) {
            if (it.ko.isBlank() || it.vi.isBlank()) continue
            // Cùng ngày → gộp (tăng số lần tra). Khác ngày → thêm dòng mới, sổ sẽ đánh dấu "Trùng".
            val existing = findSameOnDay(it.ko, day)
            if (existing != null) {
                val merged = existing.copy(
                    romaja = existing.romaja.ifBlank { it.romaja },
                    hanja = existing.hanja.ifBlank { it.hanja },
                    hanViet = existing.hanViet.ifBlank { it.hanViet },
                    pos = existing.pos.ifBlank { it.pos },
                    exampleKo = existing.exampleKo.ifBlank { it.exampleKo },
                    exampleVi = existing.exampleVi.ifBlank { it.exampleVi },
                    note = existing.note.ifBlank { it.note },
                    breakdown = existing.breakdown.ifBlank { it.breakdown },
                    loan = existing.loan.ifBlank { it.loan },
                    loanRead = existing.loanRead.ifBlank { it.loanRead },
                    lookupCount = existing.lookupCount + 1,
                    synced = false,
                )
                dao.update(merged)
                saved += merged
            } else {
                val e = VocabEntity(
                    vi = it.vi.trim(), ko = it.ko.trim(), romaja = it.romaja.trim(),
                    hanja = it.hanja.trim(), hanViet = it.hanViet.trim(), pos = it.pos.trim(),
                    exampleKo = it.exampleKo.trim(), exampleVi = it.exampleVi.trim(), note = it.note.trim(),
                    breakdown = it.breakdown.trim(), loan = it.loan.trim(), loanRead = it.loanRead.trim(), day = day, createdAt = now,
                )
                saved += e.copy(id = dao.insert(e))
            }
        }
        return saved
    }

    suspend fun toggleStar(v: VocabEntity) = dao.update(v.copy(starred = !v.starred))

    /** Điền gốc từ mượn cho mọi mục cùng từ chưa có gốc. Trả về số mục đã điền. */
    suspend fun setLoan(ko: String, loan: String, read: String = ""): Int {
        val list = dao.findAllByKey(keyOf(ko)).filter { it.hanja.isBlank() && (it.loan.isBlank() || it.loanRead.isBlank()) }
        list.forEach { dao.update(it.copy(loan = it.loan.ifBlank { loan }, loanRead = it.loanRead.ifBlank { read })) }
        return list.size
    }

    /** Cột Hán tự chỉ giữ chữ Hán (dữ liệu cũ có thể lẫn "-", chữ Latin như 補助battery). */
    suspend fun cleanHanja(): Int {
        val keep = Regex("[^\u3400-\u4DBF\u4E00-\u9FFF\uF900-\uFAFF,/ ]+")
        var n = 0
        dao.all().filter { it.hanja.isNotBlank() }.forEach {
            val clean = it.hanja.replace(keep, "").replace(Regex(" +"), " ").trim(' ', ',', '/')
            if (clean != it.hanja) {
                dao.update(it.copy(hanja = clean, synced = false))
                n++
            }
        }
        return n
    }

    /** Xoá hẳn khỏi sổ (và khỏi mọi bài ôn). Trả về uid để xoá trên Google Sheets. */
    suspend fun deleteMany(ids: List<Long>): List<String> {
        if (ids.isEmpty()) return emptyList()
        val uids = dao.byIds(ids).map { it.uid }
        lessons.deleteItemRefs(LessonItem.VOCAB, ids)
        dao.deleteByIds(ids)
        return uids
    }

    suspend fun findOn(ko: String, day: String): VocabEntity? = dao.find(ko.trim(), day)

    /**
     * Mục trùng trên toàn sổ (bỏ qua dấu cách). Nếu lỡ có nhiều bản trùng từ trước → gộp về 1 bản
     * (giữ bản mới nhất, cộng dồn số lần tra) và xoá các bản thừa.
     */
    /** Bản mới nhất của từ này trong sổ (bất kỳ ngày nào). */
    suspend fun findLatest(ko: String): VocabEntity? = dao.findAllByKey(keyOf(ko)).firstOrNull()

    suspend fun findSameOnDay(ko: String, day: String): VocabEntity? {
        val all = dao.findAllByKey(keyOf(ko)).filter { it.day == day }
        if (all.size <= 1) return all.firstOrNull()
        val keep = all.first().copy(lookupCount = all.sumOf { it.lookupCount }, starred = all.any { it.starred })
        dao.update(keep)
        val extra = all.drop(1).map { it.id }
        lessons.deleteItemRefs(LessonItem.VOCAB, extra)
        dao.deleteByIds(extra)
        return keep
    }

    /** Chấm 1 lần ôn → tính lịch ôn mới, áp cho mọi bản của từ này (kể cả bản trùng ở ngày khác). */
    suspend fun review(v: VocabEntity, grade: Grade): SrsState {
        val next = Srs.next(v.srs, grade)
        val rows = dao.findAllByKey(keyOf(v.ko)).ifEmpty { listOf(v) }
        rows.forEach { dao.update(it.withSrs(next).copy(synced = false)) }
        return next
    }
}

class GrammarRepository(private val dao: GrammarDao, private val lessons: LessonDao) {

    val all: Flow<List<GrammarEntity>> = dao.observeAll()

    suspend fun today(): List<GrammarEntity> = dao.byDay(Dates.todayKey())

    suspend fun recent(days: Long, limit: Int = 30): List<GrammarEntity> = dao.since(Dates.daysAgoKey(days), limit)

    suspend fun allNow(): List<GrammarEntity> = dao.all()

    suspend fun saveFromAi(items: List<GrammarItem>): List<GrammarEntity> {
        val day = Dates.todayKey()
        val now = System.currentTimeMillis()
        val saved = mutableListOf<GrammarEntity>()
        for (it in items) {
            if (it.pattern.isBlank() || it.meaning.isBlank()) continue
            val existing = findSameOnDay(it.pattern, day)
            if (existing != null) {
                val merged = existing.copy(
                    form = existing.form.ifBlank { it.form },
                    combine = existing.combine.ifBlank { it.combine },
                    exampleKo = existing.exampleKo.ifBlank { it.exampleKo },
                    exampleVi = existing.exampleVi.ifBlank { it.exampleVi },
                    note = existing.note.ifBlank { it.note },
                    breakdown = existing.breakdown.ifBlank { it.breakdown },
                    lookupCount = existing.lookupCount + 1,
                    synced = false,
                )
                dao.update(merged)
                saved += merged
            } else {
                val e = GrammarEntity(
                    pattern = it.pattern.trim(), meaning = it.meaning.trim(), form = it.form.trim(),
                    combine = it.combine.trim(), exampleKo = it.exampleKo.trim(), exampleVi = it.exampleVi.trim(),
                    note = it.note.trim(), breakdown = it.breakdown.trim(), day = day, createdAt = now,
                )
                saved += e.copy(id = dao.insert(e))
            }
        }
        return saved
    }

    suspend fun toggleStar(g: GrammarEntity) = dao.update(g.copy(starred = !g.starred))

    suspend fun deleteMany(ids: List<Long>): List<String> {
        if (ids.isEmpty()) return emptyList()
        val uids = dao.byIds(ids).map { it.uid }
        lessons.deleteItemRefs(LessonItem.GRAMMAR, ids)
        dao.deleteByIds(ids)
        return uids
    }

    suspend fun findOn(pattern: String, day: String): GrammarEntity? = dao.find(pattern.trim(), day)

    suspend fun findLatest(pattern: String): GrammarEntity? = dao.findAllByKey(grammarKeyOf(pattern)).firstOrNull()

    suspend fun findSameOnDay(pattern: String, day: String): GrammarEntity? {
        val all = dao.findAllByKey(grammarKeyOf(pattern)).filter { it.day == day }
        if (all.size <= 1) return all.firstOrNull()
        val keep = all.first().copy(lookupCount = all.sumOf { it.lookupCount }, starred = all.any { it.starred })
        dao.update(keep)
        val extra = all.drop(1).map { it.id }
        lessons.deleteItemRefs(LessonItem.GRAMMAR, extra)
        dao.deleteByIds(extra)
        return keep
    }

    suspend fun review(g: GrammarEntity, grade: Grade): SrsState {
        val next = Srs.next(g.srs, grade)
        val rows = dao.findAllByKey(grammarKeyOf(g.pattern)).ifEmpty { listOf(g) }
        rows.forEach { dao.update(it.withSrs(next).copy(synced = false)) }
        return next
    }
}

/** Nội dung 1 bài ôn đã nạp đủ. */
data class LessonContent(val lesson: LessonEntity, val vocab: List<VocabEntity>, val grammar: List<GrammarEntity>)

class LessonRepository(
    private val dao: LessonDao,
    private val vocab: VocabDao,
    private val grammar: GrammarDao,
) {
    val lessons: Flow<List<LessonSummary>> = dao.observeLessons()

    suspend fun save(
        id: Long?, name: String, vocabIds: Collection<Long>, grammarIds: Collection<Long>, config: StudyConfig,
    ): Long {
        val lessonId = if (id == null) {
            dao.insertLesson(
                LessonEntity(
                    name = name.trim(), createdAt = System.currentTimeMillis(),
                    reverse = config.reverse, matchPercent = config.matchPercent, drawPercent = config.drawPercent,
                )
            )
        } else {
            dao.lesson(id)?.let {
                dao.updateLesson(
                    it.copy(name = name.trim(), reverse = config.reverse, matchPercent = config.matchPercent, drawPercent = config.drawPercent)
                )
            }
            dao.deleteItemsOf(id)
            id
        }
        dao.insertItems(
            vocabIds.map { LessonItem(lessonId, LessonItem.VOCAB, it) } +
                grammarIds.map { LessonItem(lessonId, LessonItem.GRAMMAR, it) }
        )
        return lessonId
    }

    suspend fun load(id: Long): LessonContent? {
        val l = dao.lesson(id) ?: return null
        val items = dao.itemsOf(id)
        val v = vocab.byIds(items.filter { it.kind == LessonItem.VOCAB }.map { it.itemId })
        val g = grammar.byIds(items.filter { it.kind == LessonItem.GRAMMAR }.map { it.itemId })
        return LessonContent(l, v, g)
    }

    suspend fun delete(id: Long) {
        dao.deleteItemsOf(id)
        dao.deleteLessonRow(id)
    }

    suspend fun updateConfig(id: Long, config: StudyConfig) {
        val l = dao.lesson(id) ?: return
        dao.updateLesson(l.copy(reverse = config.reverse, matchPercent = config.matchPercent, drawPercent = config.drawPercent))
    }

    suspend fun recordStudy(id: Long, score: Int) {
        val l = dao.lesson(id) ?: return
        dao.updateLesson(l.copy(lastStudiedAt = System.currentTimeMillis(), studyCount = l.studyCount + 1, lastScore = score))
    }
}

class ChatRepository(private val dao: ChatDao) {

    val sessions: Flow<List<SessionSummary>> = dao.observeSessions()

    fun messages(sessionId: Long): Flow<List<ChatMessage>> = dao.observeMessages(sessionId)

    suspend fun latestSession(): ChatSession? = dao.latestSession()

    suspend fun message(id: Long): ChatMessage? = dao.message(id)

    suspend fun newSession(title: String): Long {
        val now = System.currentTimeMillis()
        return dao.insertSession(ChatSession(title = title.take(60), createdAt = now, updatedAt = now))
    }

    suspend fun addUser(sessionId: Long, text: String, imagePath: String = ""): Long {
        val now = System.currentTimeMillis()
        dao.touch(sessionId, now)
        return dao.insertMessage(
            ChatMessage(sessionId = sessionId, role = ChatMessage.ROLE_USER, text = text, createdAt = now, imagePath = imagePath)
        )
    }

    suspend fun deleteMessage(id: Long) = dao.deleteMessage(id)

    suspend fun addAssistant(sessionId: Long, reply: AssistantReply, savedCount: Int): Long {
        val now = System.currentTimeMillis()
        dao.touch(sessionId, now)
        return dao.insertMessage(
            ChatMessage(
                sessionId = sessionId,
                role = ChatMessage.ROLE_ASSISTANT,
                text = reply.reply,
                vocabJson = if (reply.vocab.isEmpty()) "" else VocabItem.listToJson(reply.vocab),
                grammarJson = if (reply.grammar.isEmpty()) "" else GrammarItem.listToJson(reply.grammar),
                intent = reply.intent,
                isError = reply.isError,
                savedCount = savedCount,
                model = reply.model,
                latencyMs = reply.latencyMs,
                createdAt = now,
            )
        )
    }

    /**
     * Gỡ 1 từ/ngữ pháp khỏi tin trả lời. Nếu tin không còn nội dung học nào → loại cả câu hỏi
     * lẫn câu trả lời khỏi ngữ cảnh AI, để AI "quên" hẳn.
     */
    suspend fun removeItem(m: ChatMessage, kind: String, index: Int) {
        val updated = if (kind == LessonItem.VOCAB) {
            val list = m.vocab.toMutableList().also { if (index in it.indices) it.removeAt(index) }
            m.copy(vocabJson = if (list.isEmpty()) "" else VocabItem.listToJson(list))
        } else {
            val list = m.grammar.toMutableList().also { if (index in it.indices) it.removeAt(index) }
            m.copy(grammarJson = if (list.isEmpty()) "" else GrammarItem.listToJson(list))
        }
        val empty = updated.vocabJson.isBlank() && updated.grammarJson.isBlank()
        dao.updateMessage(updated.copy(savedCount = if (empty) 0 else updated.savedCount, excluded = updated.excluded || empty))
        if (empty) dao.userMessageBefore(m.sessionId, m.id)?.let { dao.exclude(listOf(it.id)) }
    }

    /** Lấy n lượt gần nhất (cũ → mới) để làm ngữ cảnh cho AI. Bỏ qua tin lỗi và tin đã bị xoá nội dung. */
    suspend fun recentTurns(sessionId: Long, n: Int): List<Turn> {
        val list = dao.recent(sessionId, n.coerceAtLeast(1))
            .asReversed()
            .filterNot { it.isError || it.excluded }
        // Chỉ gửi lại ẢNH GẦN NHẤT (để hỏi tiếp về ảnh đó), các ảnh cũ thay bằng chữ cho nhẹ
        val users = list.filter { it.isUser }
        // Ảnh ở câu hiện tại hoặc câu ngay trước (đang hỏi tiếp về ảnh) mới gửi lại
        val lastImageId = users.takeLast(2).lastOrNull { it.hasImage }?.id
        return list.map { m ->
            val text = if (!m.isUser) buildString {
                append(m.text)
                if (m.vocabJson.isNotBlank()) append("\n[Từ vựng đã đưa: ").append(m.vocab.joinToString { "${it.ko} (${it.vi})" }).append(']')
                if (m.grammarJson.isNotBlank()) append("\n[Ngữ pháp đã đưa: ").append(m.grammar.joinToString { it.pattern }).append(']')
            } else when {
                m.hasImage && m.id != lastImageId -> "[Ảnh đã gửi trước đó] ${m.text}".trim()
                m.hasImage && m.text.isBlank() -> "(Xem ảnh đính kèm)"
                else -> m.text
            }
            Turn(if (m.isUser) Turn.USER else Turn.ASSISTANT, text, image = if (m.id == lastImageId) m.imagePath else null)
        }
    }

    suspend fun deleteSession(id: Long) {
        // Xoá đoạn chat → xoá luôn ảnh của đoạn đó
        ChatImages.delete(dao.imagePathsOf(id))
        dao.deleteMessagesOf(id)
        dao.deleteSessionRow(id)
    }

    suspend fun allImagePaths(): List<String> = dao.allImagePaths()

    suspend fun clearAll() {
        ChatImages.delete(dao.allImagePaths())
        dao.clearMessages()
        dao.clearSessions()
    }
}
