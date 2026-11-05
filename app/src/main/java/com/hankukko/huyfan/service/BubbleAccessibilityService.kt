package com.hankukko.huyfan.service

import android.Manifest
import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Dịch vụ Trợ năng CHỈ để vẽ bong bóng HUY FAN lên cả màn hình khoá
 * (Android chỉ cho lớp nổi của dịch vụ Trợ năng hiện trên màn hình khoá).
 * Không đọc nội dung màn hình, không thao tác thay người dùng.
 */
class BubbleAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: BubbleAccessibilityService? = null
            private set

        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected

        /** Người dùng đã bật dịch vụ trong Cài đặt → Hỗ trợ chưa (kể cả khi chưa kết nối xong). */
        fun isEnabled(context: Context): Boolean {
            val list = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return list.split(':').any { it.startsWith(context.packageName + "/") && it.contains("BubbleAccessibilityService") }
        }
    }

    private var buttonCallback: AccessibilityButtonController.AccessibilityButtonCallback? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _connected.value = true
        // Nút Trợ năng (hình người trên thanh điều hướng) = công tắc bật/tắt nghe "HUY FAN"
        val cb = object : AccessibilityButtonController.AccessibilityButtonCallback() {
            override fun onClicked(controller: AccessibilityButtonController) = toggleListening()
        }
        accessibilityButtonController.registerAccessibilityButtonCallback(cb)
        buttonCallback = cb
    }

    /** Bật/tắt chế độ chờ nghe "HUY FAN" – giống mở/tắt app, vẫn tự tắt khi hết giờ như thường. */
    private fun toggleListening() {
        val msg = when {
            StandbyService.running.value -> {
                StandbyService.stop(this)
                "HUY FAN: đã tắt nghe"
            }
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                StandbyService.start(this)
                "HUY FAN: đang nghe – gọi “HUY FAN” để hỏi"
            }
            else -> "HUY FAN cần quyền micro – mở app để cấp quyền"
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        buttonCallback?.let { runCatching { accessibilityButtonController.unregisterAccessibilityButtonCallback(it) } }
        buttonCallback = null
        instance = null
        _connected.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        _connected.value = false
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
