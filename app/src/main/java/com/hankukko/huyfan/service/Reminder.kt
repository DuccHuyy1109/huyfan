package com.hankukko.huyfan.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hankukko.huyfan.container
import com.hankukko.huyfan.data.AppSettings
import com.hankukko.huyfan.data.DueSummary
import com.hankukko.huyfan.util.Dates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Nhắc học mỗi ngày: hẹn giờ bằng AlarmManager (không cần quyền báo thức chính xác, lệch tối đa ~10 phút
 * để Android gom việc tiết kiệm pin). Tới giờ: còn thẻ đến hạn → nhắc ôn; chưa học gì hôm nay → nhắc giữ chuỗi.
 */
object Reminder {
    private const val TAG = "Reminder"
    const val ACTION_REMIND = "com.hankukko.huyfan.REMIND"
    private const val WINDOW_MS = 10 * 60_000L

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 31,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Lần nhắc kế tiếp (ms) theo giờ đã chọn. */
    fun nextTrigger(minutesOfDay: Int, now: LocalDateTime = LocalDateTime.now()): Long {
        val t = LocalTime.of((minutesOfDay / 60).coerceIn(0, 23), (minutesOfDay % 60).coerceIn(0, 59))
        var at = LocalDateTime.of(now.toLocalDate(), t)
        if (!at.isAfter(now.plusMinutes(1))) at = at.plusDays(1)
        return at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun schedule(context: Context, s: AppSettings) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context)
        am.cancel(pi)
        if (!s.reminderEnabled) {
            Log.i(TAG, "tắt nhắc học")
            return
        }
        val at = nextTrigger(s.reminderMinutes)
        am.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, pi)
        Log.i(TAG, "hẹn nhắc học lúc ${java.time.Instant.ofEpochMilli(at)}")
    }

    /** Soạn và hiện thông báo nhắc học. [force] = gửi thử (luôn hiện kể cả khi không có gì để ôn). */
    suspend fun fire(context: Context, force: Boolean = false): Boolean {
        val c = context.container
        val vocab = c.vocab.allNow()
        val grammar = c.grammar.allNow()
        val due = DueSummary.of(vocab, grammar)
        val today = Dates.todayKey()
        val learnedToday = vocab.count { it.day == today } + grammar.count { it.day == today }
        val reviewedToday = (vocab.map { it.lastReviewedAt } + grammar.map { it.lastReviewedAt })
            .any { it > 0 && Dates.dayKeyOf(it) == today }
        val streak = Dates.streak(vocab.map { it.day } + grammar.map { it.day })
        val name = c.settings.get().userName.ifBlank { "ông" }

        val (title, text) = when {
            due.count > 0 -> {
                val minutes = (due.count * 8 / 60).coerceAtLeast(1)
                "📚 $name ơi, tới giờ ôn bài rồi!" to
                    "Hôm nay có ${due.count} thẻ đến hạn (${due.newCount} mới · ${due.count - due.newCount} ôn lại). Chừng $minutes phút là xong, ôn đúng hạn là nhớ lâu lắm đó!"
            }
            learnedToday == 0 && !reviewedToday ->
                "🔥 Giữ chuỗi học nha $name!" to
                    (if (streak > 0) "Chuỗi đang là $streak ngày. " else "") + "Hôm nay chưa học từ mới nào – hỏi HUY FAN 1 từ thôi cũng được!"
            force -> "✅ Hết bài để ôn rồi!" to "Không còn thẻ nào đến hạn. Ngày mai có ${due.forecast.getOrElse(1) { 0 }} thẻ – giỏi quá $name ơi!"
            else -> return false
        }
        Notifications.reminder(context, title, text, hasDue = due.count > 0)
        Log.i(TAG, "đã nhắc: $title | $text")
        return true
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val s = app.container.settings.get()
                if (intent.action == Reminder.ACTION_REMIND && s.reminderEnabled) {
                    runCatching { Reminder.fire(app) }.onFailure { Log.w("Reminder", "lỗi nhắc học", it) }
                }
                // Mọi trường hợp (tới giờ, khởi động lại máy, đổi giờ, cập nhật app) → hẹn lần kế tiếp
                Reminder.schedule(app, s)
            } finally {
                result.finish()
            }
        }
    }
}
