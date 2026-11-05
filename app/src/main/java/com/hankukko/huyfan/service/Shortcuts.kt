package com.hankukko.huyfan.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import com.hankukko.huyfan.R
import com.hankukko.huyfan.container
import com.hankukko.huyfan.ui.ActionActivity
import com.hankukko.huyfan.ui.AssistActivity
import kotlinx.coroutines.launch

/** Mở activity từ tile (API 34+ bắt buộc dùng PendingIntent). Khoá màn hình → mở khoá trước. */
@SuppressLint("StartActivityAndCollapseDeprecated")
private fun TileService.launch(intent: Intent, needsUnlock: Boolean) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val go = {
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, intent.action.hashCode(), intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
    if (needsUnlock && isLocked) unlockAndRun { go() } else go()
}

/** Tile "HUY FAN chờ" trong bảng cài đặt nhanh: bật/tắt nghe từ khoá. */
class StandbyTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        update(StandbyService.running.value)
    }

    override fun onClick() {
        super.onClick()
        if (StandbyService.running.value) {
            StandbyService.stop(this)
            update(false)
        } else {
            // Bật micro chạy nền phải xuất phát từ màn hình đang hiển thị → đi qua ActionActivity
            launch(Intent(this, ActionActivity::class.java).setAction(ActionActivity.ACTION_START), needsUnlock = true)
        }
    }

    private fun update(on: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "HUY FAN"
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (on) "Đang chờ gọi" else "Tắt"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_huyfan)
        tile.updateTile()
    }

    companion object {
        fun refresh(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, StandbyTileService::class.java)) }
        }
    }
}

/** Tile "Hỏi HUY FAN": bật bảng trợ lý và nghe ngay. */
class AskTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Hỏi HUY FAN"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        launch(
            Intent(this, AssistActivity::class.java)
                .setAction("com.hankukko.huyfan.ASK")
                .putExtra(AssistActivity.EXTRA_FROM, AssistActivity.FROM_SHORTCUT),
            needsUnlock = false,
        )
    }
}

/** Widget màn hình chính: mic hỏi nhanh + nút bật/tắt chế độ chờ + số từ hôm nay. */
class HuyFanWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        context.container.appScope.launch {
            try {
                val count = runCatching { context.container.vocab.countToday() }.getOrDefault(0)
                ids.forEach { manager.updateAppWidget(it, build(context, count)) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun refresh(context: Context) {
            val app = context.applicationContext
            WordWidget.refresh(app)
            val mgr = AppWidgetManager.getInstance(app)
            val ids = mgr.getAppWidgetIds(ComponentName(app, HuyFanWidget::class.java))
            if (ids.isEmpty()) return
            app.container.appScope.launch {
                val count = runCatching { app.container.vocab.countToday() }.getOrDefault(0)
                ids.forEach { mgr.updateAppWidget(it, build(app, count)) }
            }
        }

        private fun build(context: Context, todayCount: Int): RemoteViews {
            val on = StandbyService.running.value
            val views = RemoteViews(context.packageName, R.layout.widget_huyfan)
            views.setTextViewText(
                R.id.widget_status,
                (if (on) "Đang chờ gọi “HUY FAN”" else "Chạm mic để hỏi") + " · Hôm nay $todayCount từ"
            )
            views.setInt(R.id.widget_standby, "setBackgroundResource",
                if (on) R.drawable.widget_btn_on_bg else R.drawable.widget_btn_bg)

            val ask = PendingIntent.getActivity(
                context, 11,
                Intent(context, AssistActivity::class.java)
                    .setAction("com.hankukko.huyfan.ASK")
                    .putExtra(AssistActivity.EXTRA_FROM, AssistActivity.FROM_SHORTCUT)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val toggle = PendingIntent.getActivity(
                context, 12,
                Intent(context, ActionActivity::class.java)
                    .setAction(ActionActivity.ACTION_TOGGLE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val open = PendingIntent.getActivity(
                context, 13,
                context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_ask, ask)
            views.setOnClickPendingIntent(R.id.widget_standby, toggle)
            views.setOnClickPendingIntent(R.id.widget_root, open)
            return views
        }
    }
}
