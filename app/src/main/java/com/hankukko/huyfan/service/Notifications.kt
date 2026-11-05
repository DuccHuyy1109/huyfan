package com.hankukko.huyfan.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hankukko.huyfan.R
import com.hankukko.huyfan.ui.AssistActivity
import com.hankukko.huyfan.ui.MainActivity

object Notifications {
    const val CH_STANDBY = "standby"
    const val CH_ALERTS = "alerts"
    const val CH_REMIND = "remind"
    const val ID_REMIND = 1003
    const val ACTION_OPEN_DUE = "com.hankukko.huyfan.OPEN_DUE"
    const val ID_STANDBY = 1001
    const val ID_ALERT = 1002

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_STANDBY, "Chế độ chờ", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Hiện khi HUY FAN đang chờ bạn gọi"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "Thông báo", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "HUY FAN đang nghe, tự tắt chế độ chờ..."
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REMIND, "Nhắc học mỗi ngày", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Nhắc ôn thẻ đến hạn và giữ chuỗi ngày học"
            }
        )
    }

    fun assistIntent(context: Context, from: String): PendingIntent = PendingIntent.getActivity(
        context, from.hashCode(),
        Intent(context, AssistActivity::class.java)
            .setAction("com.hankukko.huyfan.ASK")
            .putExtra(AssistActivity.EXTRA_FROM, from)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Mở app và vào thẳng phiên ôn thẻ đến hạn. */
    fun openDueIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 9,
        Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_DUE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun reminder(context: Context, title: String, text: String, hasDue: Boolean) {
        if (!canPost(context)) return
        val b = NotificationCompat.Builder(context, CH_REMIND)
            .setSmallIcon(R.drawable.ic_stat_huyfan)
            .setColor(0xFFFF3D9A.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(if (hasDue) openDueIntent(context) else assistIntent(context, "remind"))
        if (hasDue) b.addAction(R.drawable.ic_mic_white, "Ôn ngay", openDueIntent(context))
        else b.addAction(R.drawable.ic_mic_white, "Hỏi HUY FAN", assistIntent(context, "remind"))
        runCatching { NotificationManagerCompat.from(context).notify(ID_REMIND, b.build()) }
    }

    fun mainIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 7,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun standby(context: Context, status: String, bubbleShown: Boolean? = null): android.app.Notification {
        val stop = PendingIntent.getService(
            context, 2,
            Intent(context, StandbyService::class.java).setAction(StandbyService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CH_STANDBY)
            .setSmallIcon(R.drawable.ic_stat_huyfan)
            .setColor(0xFFFF3D9A.toInt())
            .setContentTitle("HUY FAN đang chờ")
            // Hiện cả trên màn hình khoá (nút "Hỏi ngay" mở cửa sổ hỏi không cần mở khoá)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentText(status)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(mainIntent(context))
            .addAction(R.drawable.ic_mic_white, "Hỏi ngay", assistIntent(context, "notification"))
            .apply {
                if (bubbleShown != null) {
                    val toggle = PendingIntent.getService(
                        context, 3,
                        Intent(context, StandbyService::class.java).setAction(StandbyService.ACTION_BUBBLE),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    addAction(R.drawable.ic_mic_white, if (bubbleShown) "Ẩn bong bóng" else "Hiện bong bóng", toggle)
                }
            }
            .addAction(R.drawable.ic_power_white, "Tắt", stop)
            .build()
    }

    private fun canPost(context: Context) =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun alert(context: Context, title: String, text: String, toAssist: Boolean = false) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_huyfan)
            .setColor(0xFFFF3D9A.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(if (toAssist) assistIntent(context, "alert") else mainIntent(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_ALERT, n) }
    }

    fun cancelAlert(context: Context) = NotificationManagerCompat.from(context).cancel(ID_ALERT)
}
