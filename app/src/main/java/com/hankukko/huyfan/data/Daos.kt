package com.hankukko.huyfan.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabDao {
    @Query("SELECT * FROM vocab ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VocabEntity>>

    @Query("SELECT * FROM vocab ORDER BY createdAt DESC")
    suspend fun all(): List<VocabEntity>

    @Query("SELECT * FROM vocab WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<VocabEntity>

    @Query("SELECT * FROM vocab WHERE day = :day ORDER BY createdAt DESC")
    suspend fun byDay(day: String): List<VocabEntity>

    @Query("SELECT * FROM vocab WHERE day >= :fromDay ORDER BY createdAt DESC LIMIT :limit")
    suspend fun since(fromDay: String, limit: Int): List<VocabEntity>

    @Query("SELECT * FROM vocab WHERE ko = :ko AND day = :day LIMIT 1")
    suspend fun find(ko: String, day: String): VocabEntity?

    /** Tìm trên toàn sổ, bỏ qua dấu cách: "설거지 하다" = "설거지하다". */
    @Query("SELECT * FROM vocab WHERE REPLACE(ko, ' ', '') = :koKey ORDER BY createdAt DESC")
    suspend fun findAllByKey(koKey: String): List<VocabEntity>

    @Query("SELECT * FROM vocab WHERE uid = :uid LIMIT 1")
    suspend fun byUid(uid: String): VocabEntity?

    @Query("SELECT * FROM vocab WHERE synced = 0 ORDER BY createdAt ASC")
    suspend fun unsynced(): List<VocabEntity>

    @Query("UPDATE vocab SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("UPDATE vocab SET synced = 0")
    suspend fun markAllUnsynced()

    @Query("SELECT COUNT(*) FROM vocab WHERE day = :day")
    suspend fun countByDay(day: String): Int

    @Insert
    suspend fun insert(v: VocabEntity): Long

    @Update
    suspend fun update(v: VocabEntity)

    @Delete
    suspend fun delete(v: VocabEntity)

    @Query("DELETE FROM vocab WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

@Dao
interface GrammarDao {
    @Query("SELECT * FROM grammar ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GrammarEntity>>

    @Query("SELECT * FROM grammar ORDER BY createdAt DESC")
    suspend fun all(): List<GrammarEntity>

    @Query("SELECT * FROM grammar WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<GrammarEntity>

    @Query("SELECT * FROM grammar WHERE day = :day ORDER BY createdAt DESC")
    suspend fun byDay(day: String): List<GrammarEntity>

    @Query("SELECT * FROM grammar WHERE day >= :fromDay ORDER BY createdAt DESC LIMIT :limit")
    suspend fun since(fromDay: String, limit: Int): List<GrammarEntity>

    @Query("SELECT * FROM grammar WHERE pattern = :pattern AND day = :day LIMIT 1")
    suspend fun find(pattern: String, day: String): GrammarEntity?

    /** Tìm trên toàn sổ, bỏ qua dấu cách và gạch nối: "-고 싶다" = "고싶다". */
    @Query("SELECT * FROM grammar WHERE REPLACE(REPLACE(REPLACE(pattern, ' ', ''), '-', ''), '~', '') = :key ORDER BY createdAt DESC")
    suspend fun findAllByKey(key: String): List<GrammarEntity>

    @Query("SELECT * FROM grammar WHERE uid = :uid LIMIT 1")
    suspend fun byUid(uid: String): GrammarEntity?

    @Query("SELECT * FROM grammar WHERE synced = 0 ORDER BY createdAt ASC")
    suspend fun unsynced(): List<GrammarEntity>

    @Query("UPDATE grammar SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("UPDATE grammar SET synced = 0")
    suspend fun markAllUnsynced()

    @Insert
    suspend fun insert(g: GrammarEntity): Long

    @Update
    suspend fun update(g: GrammarEntity)

    @Query("DELETE FROM grammar WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

@Dao
interface LessonDao {
    @Query(
        """
        SELECT l.id, l.name, l.createdAt, l.lastStudiedAt, l.studyCount, l.lastScore,
            (SELECT COUNT(*) FROM lesson_items i WHERE i.lessonId = l.id AND i.kind = 'vocab') AS vocabCount,
            (SELECT COUNT(*) FROM lesson_items i WHERE i.lessonId = l.id AND i.kind = 'grammar') AS grammarCount
        FROM lessons l ORDER BY CASE WHEN l.lastStudiedAt > l.createdAt THEN l.lastStudiedAt ELSE l.createdAt END DESC
        """
    )
    fun observeLessons(): Flow<List<LessonSummary>>

    @Query("SELECT * FROM lessons WHERE id = :id")
    suspend fun lesson(id: Long): LessonEntity?

    @Insert
    suspend fun insertLesson(l: LessonEntity): Long

    @Update
    suspend fun updateLesson(l: LessonEntity)

    @Query("DELETE FROM lessons WHERE id = :id")
    suspend fun deleteLessonRow(id: Long)

    @Query("SELECT * FROM lesson_items WHERE lessonId = :lessonId")
    suspend fun itemsOf(lessonId: Long): List<LessonItem>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<LessonItem>)

    @Query("DELETE FROM lesson_items WHERE lessonId = :lessonId")
    suspend fun deleteItemsOf(lessonId: Long)

    /** Từ/ngữ pháp bị xoá khỏi sổ → gỡ khỏi mọi bài ôn. */
    @Query("DELETE FROM lesson_items WHERE kind = :kind AND itemId IN (:ids)")
    suspend fun deleteItemRefs(kind: String, ids: List<Long>)
}

@Dao
interface ChatDao {
    @Insert
    suspend fun insertSession(s: ChatSession): Long

    @Query("UPDATE sessions SET updatedAt = :t WHERE id = :id")
    suspend fun touch(id: Long, t: Long)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun session(id: Long): ChatSession?

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestSession(): ChatSession?

    @Insert
    suspend fun insertMessage(m: ChatMessage): Long

    @Update
    suspend fun updateMessage(m: ChatMessage)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun message(id: Long): ChatMessage?

    /** Câu hỏi của người dùng ngay trước một câu trả lời. */
    @Query("SELECT * FROM messages WHERE sessionId = :sid AND id < :beforeId AND role = 'user' ORDER BY id DESC LIMIT 1")
    suspend fun userMessageBefore(sid: Long, beforeId: Long): ChatMessage?

    @Query("UPDATE messages SET excluded = 1 WHERE id IN (:ids)")
    suspend fun exclude(ids: List<Long>)

    @Query("SELECT * FROM messages WHERE sessionId = :sid ORDER BY id ASC")
    fun observeMessages(sid: Long): Flow<List<ChatMessage>>

    @Query("SELECT * FROM messages WHERE sessionId = :sid ORDER BY id DESC LIMIT :n")
    suspend fun recent(sid: Long, n: Int): List<ChatMessage>

    @Query(
        """
        SELECT s.id, s.title, s.createdAt, s.updatedAt,
            (SELECT COUNT(*) FROM messages m WHERE m.sessionId = s.id) AS count,
            (SELECT m2.text FROM messages m2 WHERE m2.sessionId = s.id AND m2.role = 'assistant'
                ORDER BY m2.id DESC LIMIT 1) AS preview
        FROM sessions s ORDER BY s.updatedAt DESC
        """
    )
    fun observeSessions(): Flow<List<SessionSummary>>

    @Query("DELETE FROM messages WHERE sessionId = :id")
    suspend fun deleteMessagesOf(id: Long)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Query("SELECT imagePath FROM messages WHERE sessionId = :id AND imagePath != ''")
    suspend fun imagePathsOf(id: Long): List<String>

    @Query("SELECT imagePath FROM messages WHERE imagePath != ''")
    suspend fun allImagePaths(): List<String>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSessionRow(id: Long)

    @Query("DELETE FROM messages")
    suspend fun clearMessages()

    @Query("DELETE FROM sessions")
    suspend fun clearSessions()
}

@Database(
    entities = [
        VocabEntity::class, ChatSession::class, ChatMessage::class,
        GrammarEntity::class, LessonEntity::class, LessonItem::class,
    ],
    version = 7,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vocabDao(): VocabDao
    abstract fun grammarDao(): GrammarDao
    abstract fun lessonDao(): LessonDao
    abstract fun chatDao(): ChatDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "huyfan.db")
                .build()
    }
}
