package com.hankukko.huyfan.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.hankukko.huyfan.service.StandbyService

/**
 * Activity vô hình: bật/tắt chế độ chờ từ tile, widget, lối tắt.
 * Android chỉ cho bật micro chạy nền khi app đang hiển thị → đi vòng qua đây rồi đóng ngay.
 */
class ActionActivity : ComponentActivity() {

    companion object {
        const val ACTION_START = "com.hankukko.huyfan.START_STANDBY"
        const val ACTION_STOP = "com.hankukko.huyfan.STOP_STANDBY"
        const val ACTION_TOGGLE = "com.hankukko.huyfan.TOGGLE_STANDBY"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wantOn = when (intent?.action) {
            ACTION_START -> true
            ACTION_STOP -> false
            else -> !StandbyService.running.value
        }
        if (!wantOn) {
            StandbyService.stop(this)
            Toast.makeText(this, "Đã tắt chế độ chờ HUY FAN", Toast.LENGTH_SHORT).show()
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_REQUEST_PERMS, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } else {
            StandbyService.start(this)
            Toast.makeText(this, "HUY FAN đang chờ – gọi “HUY FAN” nha!", Toast.LENGTH_SHORT).show()
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
