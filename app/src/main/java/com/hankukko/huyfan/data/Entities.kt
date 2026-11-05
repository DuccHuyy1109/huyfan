package com.hankukko.huyfan.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hankukko.huyfan.ai.GrammarItem
import com.hankukko.huyfan.ai.VocabItem
import com.hankukko.huyfan.util.Dates
import org.json.JSONObject
import java.util.UUID

@Entity(tableName = "vocab", indices = [Index("day"), Index("ko")])
data class VocabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val vi: String,
    val ko: String,
    val romaja: String = "",
    val hanja: String = "",
    val hanViet: String = "",
    val pos: String = "",
    val exampleKo: String = "",
    val exampleVi: String = "",
    val note: String = "",
    /** yyyy-MM-dd – dùng để nhóm danh sách theo ngày */
    val day: String,
    val createdAt: Long,
    val starred: Boolean = false,
    val lookupCount: Int = 1,
    /** 0..5 – mức thuộc bài khi ôn flashcard */
    val mastery: Int = 0,
    val lastReviewedAt: Long = 0,
    val synced: Boolean = false,
    @ColumnInfo(defaultValue = "") val breakdown: String = "",
    // ---- Lịch ôn ngắt quãng (xem Srs.kt) ----
    @ColumnInfo(defaultValue = "0") val dueAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val intervalDays: Int = 0,
    @ColumnInfo(defaultValue = "250") val ease: Int = 250,
    @ColumnInfo(defaultValue = "0") val reps: Int = 0,
    @ColumnInfo(defaultValue = "0") val lapses: Int = 0,
    /** Từ mượn nước ngoài: từ gốc (vd "computer") */
    @ColumnInfo(defaultValue = "") val loan: String = "",
    /** Cách đọc từ gốc kiểu Việt (vd "cơm-piu-tơ") */
    @ColumnInfo(defaultValue = "") val loanRead: String = "",
) {
    fun toItem() = VocabItem(vi, ko, romaja, hanja, hanViet, pos, exampleKo, exampleVi, note, breakdown, loan, loanRead)

    fun toSheetJson(): JSONObject = JSONObject()
        .put("uid", uid)
        .put("date", day)
        .put("time", Dates.time(createdAt))
        .put("vi", vi)
        .put("ko", ko)
        .put("romaja", romaja)
        .put("hanja", hanja)
        .put("han_viet", hanViet)
        .put("pos", pos)
        .put("example_ko", exampleKo)
        .put("example_vi", exampleVi)
        .put("note", note)
        .put("breakdown", breakdown)
        .put("next_review", Dates.dayKeyOf(Srs.effectiveDue(dueAt, createdAt)))
        .put("interval", intervalDays)
        .put("srs", "$dueAt,$intervalDays,$ease,$reps,$lapses")
}

@Entity(tableName = "sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "messages", indices = [Index("sessionId")])
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    /** "user" | "assistant" */
    val role: String,
    val text: String,
    val vocabJson: String = "",
    val intent: String = "",
    val isError: Boolean = false,
    val savedCount: Int = 0,
    val model: String = "",
    val latencyMs: Long = 0,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "") val grammarJson: String = "",
    /** Người dùng đã xoá nội dung học của lượt này → không đưa vào ngữ cảnh AI nữa */
    @ColumnInfo(defaultValue = "0") val excluded: Boolean = false,
    /** Ảnh đính kèm câu hỏi (file trong thư mục riêng của app) – xoá đoạn chat sẽ xoá luôn file */
    @ColumnInfo(defaultValue = "") val imagePath: String = "",
) {
    val hasImage: Boolean get() = imagePath.isNotBlank()
    val isUser: Boolean get() = role == ROLE_USER
    val vocab: List<VocabItem> get() = if (vocabJson.isBlank()) emptyList() else VocabItem.listFromJson(vocabJson)
    val grammar: List<GrammarItem> get() = if (grammarJson.isBlank()) emptyList() else GrammarItem.listFromJson(grammarJson)

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

data class SessionSummary(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val count: Int,
    val preview: String?,
)

/** Một điểm ngữ pháp đã học (đuôi câu, tiểu từ, cấu trúc...). */
@Entity(tableName = "grammar", indices = [Index("day"), Index("pattern")])
data class GrammarEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    /** Đuôi câu / cấu trúc, vd "-고 싶다" */
    val pattern: String,
    /** Nghĩa, vd "muốn (làm gì)" */
    val meaning: String,
    /** Dạng, vd "đuôi câu diễn tả mong muốn", "tiểu từ chủ ngữ" */
    val form: String = "",
    /** Từ kết hợp, vd "Động từ + 고 싶다" */
    val combine: String = "",
    val exampleKo: String = "",
    val exampleVi: String = "",
    val note: String = "",
    val day: String,
    val createdAt: Long,
    val starred: Boolean = false,
    val lookupCount: Int = 1,
    val mastery: Int = 0,
    val lastReviewedAt: Long = 0,
    val synced: Boolean = false,
    @ColumnInfo(defaultValue = "") val breakdown: String = "",
    // ---- Lịch ôn ngắt quãng (xem Srs.kt) ----
    @ColumnInfo(defaultValue = "0") val dueAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val intervalDays: Int = 0,
    @ColumnInfo(defaultValue = "250") val ease: Int = 250,
    @ColumnInfo(defaultValue = "0") val reps: Int = 0,
    @ColumnInfo(defaultValue = "0") val lapses: Int = 0,
) {
    fun toItem() = GrammarItem(pattern, meaning, form, combine, exampleKo, exampleVi, note, breakdown)

    fun toSheetJson(): JSONObject = JSONObject()
        .put("uid", uid)
        .put("date", day)
        .put("time", Dates.time(createdAt))
        .put("pattern", pattern)
        .put("meaning", meaning)
        .put("form", form)
        .put("combine", combine)
        .put("example_ko", exampleKo)
        .put("example_vi", exampleVi)
        .put("note", note)
        .put("breakdown", breakdown)
        .put("next_review", Dates.dayKeyOf(Srs.effectiveDue(dueAt, createdAt)))
        .put("interval", intervalDays)
        .put("srs", "$dueAt,$intervalDays,$ease,$reps,$lapses")
}

/** Bài ôn do người dùng tự tạo. */
@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val lastStudiedAt: Long = 0,
    val studyCount: Int = 0,
    /** Số thẻ nhớ được ở lần học gần nhất (-1 = chưa học) */
    val lastScore: Int = -1,
    /** Cài đặt riêng của bài: chế độ ngược (nhìn nghĩa → nói/viết từ) */
    @ColumnInfo(defaultValue = "0") val reverse: Boolean = false,
    /** Nói giống đáp án bao nhiêu % thì lật thẻ */
    @ColumnInfo(defaultValue = "70") val matchPercent: Int = 70,
    /** Viết tay giống đáp án bao nhiêu % thì lật thẻ */
    @ColumnInfo(defaultValue = "70") val drawPercent: Int = 70,
) {
    fun config(autoSpeak: Boolean) = StudyConfig(reverse, matchPercent, drawPercent, autoSpeak)
}

/** Cài đặt khi học thẻ (của 1 bài ôn, hoặc mặc định cho ôn nhanh). */
data class StudyConfig(
    val reverse: Boolean = false,
    val matchPercent: Int = 70,
    val drawPercent: Int = 70,
    val autoSpeak: Boolean = true,
)

@Entity(
    tableName = "lesson_items",
    primaryKeys = ["lessonId", "kind", "itemId"],
    indices = [Index("lessonId")],
)
data class LessonItem(
    val lessonId: Long,
    /** "vocab" | "grammar" */
    val kind: String,
    val itemId: Long,
) {
    companion object {
        const val VOCAB = "vocab"
        const val GRAMMAR = "grammar"
    }
}

data class LessonSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val lastStudiedAt: Long,
    val studyCount: Int,
    val lastScore: Int,
    val vocabCount: Int,
    val grammarCount: Int,
) {
    val total: Int get() = vocabCount + grammarCount
}
