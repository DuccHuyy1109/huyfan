package com.hankukko.huyfan

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.hankukko.huyfan.service.StandbyService
import java.util.Locale

/** Ngôn ngữ đang học. Mỗi ngôn ngữ là 1 "app riêng": CSDL, lịch sử chat, sổ từ, link Sheets, prompt riêng. */
enum class StudyLang(
    val id: String,
    val flag: String,
    /** "tiếng Hàn" / "tiếng Anh" */
    val nameVi: String,
    /** Nhãn ngắn trên nút đổi mic: 한 / EN */
    val short: String,
    val sttLocale: String,
    val ttsLocale: Locale,
    val greeting: String,
    val dbName: String,
    /** Hậu tố khoá cài đặt riêng theo ngôn ngữ ("" = giữ nguyên khoá cũ của tiếng Hàn) */
    val keySuffix: String,
) {
    KO("ko", "🇰🇷", "tiếng Hàn", "한", "ko-KR", Locale.KOREA, "안녕!", "huyfan.db", ""),
    EN("en", "🇬🇧", "tiếng Anh", "EN", "en-US", Locale.US, "Hello!", "huyfan_en.db", "_en");

    /** "Tiếng Hàn" – viết hoa chữ đầu */
    val title: String get() = nameVi.replaceFirstChar { it.uppercase() }
    val other: StudyLang get() = if (this == KO) EN else KO
}

object Lang {
    private const val PREFS = "huyfan_lang"
    private const val KEY = "study_lang"
    const val EXTRA_SWITCHED = "lang_switched"
    private const val EXTRA_STANDBY = "lang_restart_standby"

    /** Cố định trong suốt 1 phiên chạy app – đổi ngôn ngữ = khởi động lại app. */
    @Volatile
    var current: StudyLang = StudyLang.KO
        private set

    val isEn: Boolean get() = current == StudyLang.EN

    fun init(context: Context) {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, StudyLang.KO.id)
        current = StudyLang.entries.firstOrNull { it.id == id } ?: StudyLang.KO
    }

    /** Khoá cài đặt theo ngôn ngữ đang học. */
    fun key(base: String): String = base + current.keySuffix

    /**
     * Chuyển sang ngôn ngữ kia như mở 1 app khác: lưu lựa chọn, đóng mọi thứ đang chạy,
     * mở lại [reopen] trong tiến trình mới (CSDL & cài đặt của ngôn ngữ mới), bắt đầu cuộc trò chuyện mới.
     */
    fun switchTo(activity: Activity, to: StudyLang, reopen: Class<out Activity>) {
        val app = activity.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, to.id).commit()
        val standby = StandbyService.running.value
        if (standby) StandbyService.stop(app)
        val intent = Intent(app, reopen)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(EXTRA_SWITCHED, true)
            .putExtra(EXTRA_STANDBY, standby)
        activity.finishAffinity()
        app.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    /** Gọi khi app mở lại sau khi đổi ngôn ngữ: bật lại chế độ chờ nếu trước đó đang bật. */
    fun afterSwitch(context: Context, intent: Intent?): Boolean {
        val switched = intent?.getBooleanExtra(EXTRA_SWITCHED, false) == true
        if (switched && intent?.getBooleanExtra(EXTRA_STANDBY, false) == true) StandbyService.start(context)
        intent?.removeExtra(EXTRA_SWITCHED)
        intent?.removeExtra(EXTRA_STANDBY)
        return switched
    }
}
