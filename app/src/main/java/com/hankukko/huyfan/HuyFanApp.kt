package com.hankukko.huyfan

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.hankukko.huyfan.service.Notifications

class HuyFanApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        registerActivityLifecycleCallbacks(ForegroundTracker)
        container.start()
    }
}

val Context.container: AppContainer
    get() = (applicationContext as HuyFanApp).container

/** Biết activity nào của app đang hiển thị để quyết định có bật bảng trợ lý hay không. */
object ForegroundTracker : Application.ActivityLifecycleCallbacks {
    @Volatile
    var resumed: String? = null
        private set

    val mainVisible: Boolean get() = resumed?.endsWith(".MainActivity") == true
    val assistVisible: Boolean get() = resumed?.endsWith(".AssistActivity") == true

    /** MainActivity đang mở tab Chat (bong bóng tự ẩn vì màn Chat đã có mic + bàn phím). */
    @Volatile
    var mainOnChat: Boolean = false
        private set

    fun setMainOnChat(v: Boolean) {
        if (mainOnChat == v) return
        mainOnChat = v
        onChange?.invoke()
    }

    /** Đang mở camera / trình chọn ảnh để hỏi bằng ảnh (bong bóng không được che). */
    @Volatile
    var pickingImage: Boolean = false
        private set

    fun setPickingImage(v: Boolean) {
        if (pickingImage == v) return
        pickingImage = v
        onChange?.invoke()
    }

    /** Đang mở màn phiên dịch (Dịch hội thoại / Dịch tự động) – bong bóng không được che chữ dịch. */
    @Volatile
    var interpreting: Boolean = false
        private set

    fun setInterpreting(v: Boolean) {
        if (interpreting == v) return
        interpreting = v
        onChange?.invoke()
    }

    /** Bong bóng nên tạm giấu: cửa sổ hỏi đang mở, đang ở tab Chat / màn phiên dịch, hoặc đang chọn/chụp ảnh. */
    val hideBubble: Boolean get() = assistVisible || pickingImage || (mainVisible && (mainOnChat || interpreting))

    /** Gọi mỗi khi màn hình của app đổi (vd ẩn bong bóng khi cửa sổ hỏi đang mở). */
    @Volatile
    var onChange: (() -> Unit)? = null

    override fun onActivityResumed(activity: Activity) {
        resumed = activity.javaClass.name
        onChange?.invoke()
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed == activity.javaClass.name) resumed = null
        onChange?.invoke()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
