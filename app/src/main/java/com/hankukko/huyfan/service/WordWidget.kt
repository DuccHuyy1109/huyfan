package com.hankukko.huyfan.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.hankukko.huyfan.R
import com.hankukko.huyfan.container
import com.hankukko.huyfan.data.DueSummary
import com.hankukko.huyfan.data.VocabEntity
import com.hankukko.huyfan.data.due
import com.hankukko.huyfan.data.isDue
import com.hankukko.huyfan.data.latestPerWord
import com.hankukko.huyfan.util.Dates
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Widget "Từ của ngày": ưu tiên từ đến hạn ôn, không có thì mỗi ngày 1 từ khác trong sổ.
 * 🔊 nghe · 👁 lật xem nghĩa · ⏭ từ khác · "Ôn N thẻ" mở thẳng phiên ôn.
 */
class WordWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        context.container.appScope.launch {
            try {
                render(context.applicationContext, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            ACTION_FLIP, ACTION_NEXT, ACTION_SPEAK -> {
                val pending = goAsync()
                app.container.appScope.launch {
                    try {
                        val p = prefs(app)
                        when (intent.action) {
                            ACTION_FLIP -> p.edit().putBoolean(K_REVEAL, !p.getBoolean(K_REVEAL, false)).apply()
                            ACTION_NEXT -> p.edit().putInt(K_OFFSET, p.getInt(K_OFFSET, 0) + 1).putBoolean(K_REVEAL, false).apply()
                            ACTION_SPEAK -> pick(app)?.let { app.container.speaker.speakKorean(it.ko) }
                        }
                        val mgr = AppWidgetManager.getInstance(app)
                        render(app, mgr, mgr.getAppWidgetIds(ComponentName(app, WordWidget::class.java)))
                    } finally {
                        pending.finish()
                    }
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        private const val ACTION_FLIP = "com.hankukko.huyfan.WORD_FLIP"
        private const val ACTION_NEXT = "com.hankukko.huyfan.WORD_NEXT"
        private const val ACTION_SPEAK = "com.hankukko.huyfan.WORD_SPEAK"
        private const val K_DAY = "day"
        private const val K_OFFSET = "offset"
        private const val K_REVEAL = "reveal"

        private fun prefs(c: Context) = c.getSharedPreferences("word_widget", Context.MODE_PRIVATE)

        fun refresh(context: Context) {
            val app = context.applicationContext
            val mgr = AppWidgetManager.getInstance(app)
            val ids = mgr.getAppWidgetIds(ComponentName(app, WordWidget::class.java))
            if (ids.isEmpty()) return
            app.container.appScope.launch { render(app, mgr, ids) }
        }

        /** Thứ tự từ: đến hạn (hạn sớm nhất trước) rồi các từ còn lại xáo theo ngày. */
        private suspend fun ordered(app: Context): List<VocabEntity> {
            val all = app.container.vocab.allNow().latestPerWord()
            val today = Dates.todayKey()
            val (due, rest) = all.partition { it.isDue }
            return due.sortedBy { it.due } + rest.shuffled(Random(today.hashCode()))
        }

        private suspend fun pick(app: Context): VocabEntity? {
            val list = ordered(app)
            if (list.isEmpty()) return null
            val p = prefs(app)
            val today = Dates.todayKey()
            if (p.getString(K_DAY, "") != today) {
                p.edit().putString(K_DAY, today).putInt(K_OFFSET, 0).putBoolean(K_REVEAL, false).apply()
            }
            return list[p.getInt(K_OFFSET, 0).mod(list.size)]
        }

        private suspend fun render(app: Context, mgr: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val w = pick(app)
            val c = app.container
            val due = DueSummary.of(c.vocab.allNow(), c.grammar.allNow()).count
            val reveal = prefs(app).getBoolean(K_REVEAL, false)
            val v = RemoteViews(app.packageName, R.layout.widget_word)
            if (w == null) {
                v.setTextViewText(R.id.ww_ko, "안녕하세요!")
                v.setTextViewText(R.id.ww_romaja, "annyeonghaseyo")
                v.setTextViewText(R.id.ww_vi, "Hỏi HUY FAN vài từ – từ sẽ hiện ở đây mỗi ngày")
                v.setViewVisibility(R.id.ww_badge, View.GONE)
            } else {
                v.setTextViewText(R.id.ww_ko, w.ko)
                v.setTextViewText(R.id.ww_romaja, w.romaja)
                v.setViewVisibility(R.id.ww_romaja, if (w.romaja.isBlank()) View.GONE else View.VISIBLE)
                v.setTextViewText(
                    R.id.ww_vi,
                    if (reveal) w.vi + (if (w.hanja.isNotBlank()) "  ·  ${w.hanja} ${w.hanViet}".trimEnd() else if (w.loan.isNotBlank()) "  ·  ${w.loan}" else "") else "Chạm 👁 để xem nghĩa",
                )
                v.setViewVisibility(R.id.ww_badge, if (w.isDue) View.VISIBLE else View.GONE)
            }
            v.setTextViewText(R.id.ww_review, if (due > 0) "Ôn $due thẻ" else "Mở sổ từ")

            fun bc(action: String, code: Int) = PendingIntent.getBroadcast(
                app, code, Intent(app, WordWidget::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.ww_speak, bc(ACTION_SPEAK, 41))
            v.setOnClickPendingIntent(R.id.ww_flip, bc(ACTION_FLIP, 42))
            v.setOnClickPendingIntent(R.id.ww_card, bc(ACTION_FLIP, 43))
            v.setOnClickPendingIntent(R.id.ww_next, bc(ACTION_NEXT, 44))
            v.setOnClickPendingIntent(
                R.id.ww_review,
                if (due > 0) Notifications.openDueIntent(app)
                else PendingIntent.getActivity(
                    app, 45,
                    Intent(app, com.hankukko.huyfan.ui.MainActivity::class.java)
                        .putExtra(com.hankukko.huyfan.ui.MainActivity.EXTRA_TAB, "VOCAB")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            ids.forEach { mgr.updateAppWidget(it, v) }
        }
    }
}
