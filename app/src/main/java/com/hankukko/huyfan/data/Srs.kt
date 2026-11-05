package com.hankukko.huyfan.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Lịch ôn của 1 thẻ (từ vựng hoặc ngữ pháp). */
data class SrsState(
    /** Thời điểm đến hạn ôn (ms). 0 = chưa ôn lần nào → đến hạn từ ngày hôm sau ngày học. */
    val dueAt: Long = 0,
    /** Khoảng cách ôn hiện tại (ngày) */
    val intervalDays: Int = 0,
    /** Độ dễ ×100 (SM-2: 130..300, mặc định 250) */
    val ease: Int = Srs.DEFAULT_EASE,
    /** Số lần nhớ liên tiếp */
    val reps: Int = 0,
    /** Số lần quên */
    val lapses: Int = 0,
)

enum class Grade(val label: String) { AGAIN("Quên"), GOOD("Nhớ"), EASY("Dễ") }

/**
 * Ôn ngắt quãng kiểu SM-2 (thuật toán Anki dùng), rút gọn còn 3 nút Quên / Nhớ / Dễ:
 * nhớ thì giãn dần 1 → 3 → 7 → 15 → 30… ngày, quên thì ôn lại ngay trong phiên và bắt đầu lại.
 */
object Srs {
    const val DEFAULT_EASE = 250
    private const val MIN_EASE = 130
    private const val MAX_EASE = 320
    private const val MAX_INTERVAL = 365
    /** Quên → hỏi lại sau 10 phút (trong phiên học: đưa thẻ xuống cuối). */
    const val RELEARN_MS = 10 * 60_000L

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun startOfDay(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** Hạn ôn thực tế: thẻ chưa ôn lần nào đến hạn từ đầu ngày hôm sau ngày học. */
    fun effectiveDue(dueAt: Long, createdAt: Long): Long =
        if (dueAt > 0) dueAt else startOfDay(dateOf(createdAt).plusDays(1))

    fun isDue(dueAt: Long, createdAt: Long, now: Long = System.currentTimeMillis()): Boolean =
        effectiveDue(dueAt, createdAt) <= now

    fun next(s: SrsState, g: Grade, now: Long = System.currentTimeMillis()): SrsState {
        val today = dateOf(now)
        return when (g) {
            Grade.AGAIN -> s.copy(
                dueAt = now + RELEARN_MS, intervalDays = 0, reps = 0,
                lapses = s.lapses + 1, ease = (s.ease - 20).coerceAtLeast(MIN_EASE),
            )
            Grade.GOOD -> {
                val iv = when (s.reps) {
                    0 -> 1
                    1 -> 3
                    else -> (s.intervalDays.coerceAtLeast(1) * s.ease / 100.0).roundToInt().coerceAtLeast(s.intervalDays + 1)
                }.coerceAtMost(MAX_INTERVAL)
                s.copy(dueAt = startOfDay(today.plusDays(iv.toLong())), intervalDays = iv, reps = s.reps + 1)
            }
            Grade.EASY -> {
                val ease = (s.ease + 15).coerceAtMost(MAX_EASE)
                val iv = when (s.reps) {
                    0 -> 3
                    1 -> 7
                    else -> (s.intervalDays.coerceAtLeast(1) * ease / 100.0 * 1.3).roundToInt().coerceAtLeast(s.intervalDays + 2)
                }.coerceAtMost(MAX_INTERVAL)
                s.copy(dueAt = startOfDay(today.plusDays(iv.toLong())), intervalDays = iv, reps = s.reps + 1, ease = ease)
            }
        }
    }

    /** Mức thuộc 0..5 suy từ khoảng cách ôn (dùng cho bộ lọc "Chưa thuộc"). */
    fun mastery(s: SrsState): Int = when {
        s.intervalDays <= 0 -> 0
        s.intervalDays <= 1 -> 1
        s.intervalDays <= 3 -> 2
        s.intervalDays <= 7 -> 3
        s.intervalDays <= 21 -> 4
        else -> 5
    }

    /** "10 phút", "1 ngày", "2 tuần", "3 tháng" – dùng dưới nút chấm điểm. */
    fun intervalLabel(s: SrsState, g: Grade): String {
        val n = next(s, g)
        if (g == Grade.AGAIN) return "10 phút"
        return daysLabel(n.intervalDays)
    }

    fun daysLabel(days: Int): String = when {
        days <= 0 -> "hôm nay"
        days < 14 -> "$days ngày"
        days < 60 -> "${(days / 7.0).roundToInt()} tuần"
        days < 365 -> "${(days / 30.0).roundToInt()} tháng"
        else -> "1 năm"
    }

    /** "Ôn hôm nay", "Ôn ngày mai", "Ôn sau 5 ngày" – hiện trên thẻ trong sổ. */
    fun dueLabel(dueAt: Long, createdAt: Long, now: Long = System.currentTimeMillis()): String {
        val due = effectiveDue(dueAt, createdAt)
        if (due <= now) return "Đến hạn ôn"
        val days = (dateOf(due).toEpochDay() - dateOf(now).toEpochDay()).toInt()
        return when {
            days <= 0 -> "Ôn lại lúc ${Instant.ofEpochMilli(due).atZone(zone).toLocalTime().toString().take(5)}"
            days == 1 -> "Ôn ngày mai"
            else -> "Ôn sau ${daysLabel(days)}"
        }
    }

    /** Số thẻ đến hạn trong từng ngày của [days] ngày tới (phần tử 0 = hôm nay, gồm cả thẻ quá hạn). */
    fun forecast(dues: List<Long>, days: Int = 7, now: Long = System.currentTimeMillis()): IntArray {
        val out = IntArray(days)
        val today = dateOf(now).toEpochDay()
        dues.forEach { d ->
            val off = (dateOf(d).toEpochDay() - today).toInt().coerceAtLeast(0)
            if (off < days) out[off]++
        }
        return out
    }
}

// ---------------------------------------------------------------------------------------------
// Gắn lịch ôn vào thực thể

val VocabEntity.srs: SrsState get() = SrsState(dueAt, intervalDays, ease, reps, lapses)
fun VocabEntity.withSrs(s: SrsState, now: Long = System.currentTimeMillis()) = copy(
    dueAt = s.dueAt, intervalDays = s.intervalDays, ease = s.ease, reps = s.reps, lapses = s.lapses,
    mastery = Srs.mastery(s), lastReviewedAt = now,
)
val VocabEntity.isDue: Boolean get() = Srs.isDue(dueAt, createdAt)
val VocabEntity.due: Long get() = Srs.effectiveDue(dueAt, createdAt)

val GrammarEntity.srs: SrsState get() = SrsState(dueAt, intervalDays, ease, reps, lapses)
fun GrammarEntity.withSrs(s: SrsState, now: Long = System.currentTimeMillis()) = copy(
    dueAt = s.dueAt, intervalDays = s.intervalDays, ease = s.ease, reps = s.reps, lapses = s.lapses,
    mastery = Srs.mastery(s), lastReviewedAt = now,
)
val GrammarEntity.isDue: Boolean get() = Srs.isDue(dueAt, createdAt)
val GrammarEntity.due: Long get() = Srs.effectiveDue(dueAt, createdAt)

/** Mỗi từ (bỏ qua bản trùng ở các ngày khác) chỉ tính 1 lần – lấy bản mới nhất. */
fun List<VocabEntity>.latestPerWord(): List<VocabEntity> =
    groupBy { keyOf(it.ko) }.values.map { g -> g.maxBy { it.createdAt } }

fun List<GrammarEntity>.latestPerPattern(): List<GrammarEntity> =
    groupBy { grammarKeyOf(it.pattern) }.values.map { g -> g.maxBy { it.createdAt } }

/** Tổng quan lịch ôn cho màn Ôn tập, thông báo nhắc học, widget. */
data class DueSummary(
    val vocab: List<VocabEntity>,
    val grammar: List<GrammarEntity>,
    /** Thẻ đến hạn lần đầu (chưa ôn lần nào) */
    val newCount: Int,
    /** Số thẻ đến hạn theo ngày, 7 ngày tới */
    val forecast: IntArray,
    /** Đã "thuộc" (khoảng cách ôn ≥ 21 ngày) */
    val matured: Int,
    val total: Int,
) {
    val count: Int get() = vocab.size + grammar.size

    companion object {
        fun of(allVocab: List<VocabEntity>, allGrammar: List<GrammarEntity>, now: Long = System.currentTimeMillis()): DueSummary {
            val v = allVocab.latestPerWord()
            val g = allGrammar.latestPerPattern()
            val dv = v.filter { Srs.isDue(it.dueAt, it.createdAt, now) }.sortedBy { it.due }
            val dg = g.filter { Srs.isDue(it.dueAt, it.createdAt, now) }.sortedBy { it.due }
            return DueSummary(
                vocab = dv,
                grammar = dg,
                newCount = dv.count { it.reps == 0 && it.lapses == 0 } + dg.count { it.reps == 0 && it.lapses == 0 },
                forecast = Srs.forecast(v.map { it.due } + g.map { it.due }, 7, now),
                matured = v.count { it.intervalDays >= 21 } + g.count { it.intervalDays >= 21 },
                total = v.size + g.size,
            )
        }
    }
}
