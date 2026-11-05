package com.hankukko.huyfan.service

import android.Manifest
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.hankukko.huyfan.ForegroundTracker
import com.hankukko.huyfan.assistant.Source
import com.hankukko.huyfan.container
import com.hankukko.huyfan.ui.AssistActivity
import com.hankukko.huyfan.voice.WakeWordEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Chế độ chờ: dịch vụ tiền cảnh (micro) nghe từ khoá "HUY FAN" offline.
 * Chỉ chạy khi người dùng bật (tile / widget / phím tắt / trong app) và tự tắt sau X phút để tiết kiệm pin.
 */
class StandbyService : Service() {

    companion object {
        private const val SELF_ECHO_MS = 1500L
        const val ACTION_START = "com.hankukko.huyfan.STANDBY_START"
        const val ACTION_STOP = "com.hankukko.huyfan.STANDBY_STOP"
        const val ACTION_BUBBLE = "com.hankukko.huyfan.STANDBY_BUBBLE"
        private val BUBBLE_SIZES = intArrayOf(50, 58, 66)
        private const val TAG = "Standby"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        private val _status = MutableStateFlow("")
        val status: StateFlow<String> = _status

        /** Thời điểm (ms) chế độ chờ sẽ tự tắt; 0 = không hẹn giờ. */
        @Volatile
        var autoOffAt: Long = 0L
            private set

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, StandbyService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StandbyService::class.java))
        }

        fun toggle(context: Context) = if (_running.value) stop(context) else start(context)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var wake: WakeWordEngine
    private var initialized = false
    private var conversationBusy = false
    private var screenOff = false
    private var autoOffJob: Job? = null
    private var resumeJob: Job? = null
    private var errorStreak = 0
    private var bubble: BubbleController? = null
    /** Người dùng kéo bong bóng vào ✕ → ẩn tới lần bật chờ sau (hoặc bấm "Hiện bong bóng" trên thông báo) */
    private var bubbleHidden = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_BUBBLE && initialized) {
            toggleBubble()
            return START_NOT_STICKY
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Notifications.alert(this, "HUY FAN cần quyền micro", "Mở app HUY FAN để cấp quyền micro rồi bật lại chế độ chờ.")
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(
                this, Notifications.ID_STANDBY,
                Notifications.standby(this, "Đang chuẩn bị…"),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
            )
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed", e)
            Notifications.alert(this, "Không bật được chế độ chờ", "Android chặn bật micro chạy nền lúc này. Mở app HUY FAN rồi bật lại nha.")
            stopSelf()
            return START_NOT_STICKY
        }
        _running.value = true
        refreshShortcuts()
        if (!initialized) {
            initialized = true
            setup()
        }
        updateBubble()
        resetAutoOff()
        return START_NOT_STICKY
    }

    private fun setup() {
        wake = WakeWordEngine(this)
        wake.onWake = { onWakeDetected() }
        scope.launch {
            var was = false
            container.speaker.speaking.collect { now ->
                if (was && !now) lastSpokeAt = System.currentTimeMillis()
                was = now
            }
        }
        wake.onError = { msg ->
            Log.w(TAG, "wake error: $msg")
            errorStreak++
            wake.stop()
            if (errorStreak <= 5) scheduleResume(1500L * errorStreak)
            else setStatus("Micro đang bận – chạm “Hỏi ngay” để nói")
        }

        val engine = container.engine
        engine.onMicRequest = { pauseListening() }

        // ---- Bong bóng chat nổi ----
        bubble = createBubble()
        // Bật/tắt dịch vụ Trợ năng → tạo lại bong bóng (lớp nổi Trợ năng hiện được trên màn hình khoá)
        scope.launch {
            BubbleAccessibilityService.connected.collect {
                val old = bubble ?: return@collect
                if (old.onLockScreen == (BubbleAccessibilityService.instance != null)) return@collect
                old.hide(animate = false)
                bubble = createBubble()
                updateBubble()
            }
        }
        ForegroundTracker.onChange = { scope.launch { bubble?.setSuppressed(ForegroundTracker.hideBubble) } }
        scope.launch {
            container.settingsState.map { Triple(it.bubbleEnabled, it.bubbleSize, it.wakeListenEnabled) }.distinctUntilChanged().collect { (_, _, listen) ->
                updateBubble()
                if (listen) scheduleResume(200) else pauseListening("Chỉ dùng bong bóng – chạm bong bóng để hỏi")
                setStatus(_status.value)
            }
        }
        scope.launch {
            engine.state.map { it.phase }.distinctUntilChanged().collect { updateBubbleState() }
        }
        scope.launch {
            engine.micHold.collect { hold ->
                if (hold) pauseListening("Đang phiên dịch hội thoại…") else scheduleResume(700)
            }
        }

        setStatus("Đang tải bộ nhận diện “HUY FAN”…")
        wake.load(
            onLoaded = { resumeListening() },
            onFail = { msg -> setStatus("Lỗi mô hình: $msg") },
        )

        scope.launch {
            engine.state.map { it.busy }.distinctUntilChanged().collect { busy ->
                conversationBusy = busy
                if (busy) {
                    pauseListening()
                    resetAutoOff()
                } else {
                    scheduleResume(700)
                }
            }
        }

        val pm = getSystemService(PowerManager::class.java)
        screenOff = !pm.isInteractive
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOff = intent.action == Intent.ACTION_SCREEN_OFF
            if (!container.settingsState.value.pauseOnScreenOff) return
            if (screenOff) pauseListening("Tạm dừng khi tắt màn hình") else scheduleResume(300)
        }
    }

    /** Lúc app vừa đọc xong (ms) – micro chờ có thể nghe lại chính loa của app ("HUY FAN đây…"). */
    private var lastSpokeAt = 0L

    private fun onWakeDetected() {
        val sp = container.speaker.speaking.value
        if (sp || System.currentTimeMillis() - lastSpokeAt < SELF_ECHO_MS) {
            Log.i(TAG, "bỏ qua tiếng gọi: app đang/vừa tự đọc")
            return
        }
        pauseListening()
        vibrate()
        resetAutoOff()
        container.engine.wake(Source.WAKE)
        showAssistant()
    }

    private fun createBubble(): BubbleController = BubbleController(
        BubbleAccessibilityService.instance ?: this,
        onTap = { openFromBubble() },
        onDismiss = {
            bubbleHidden = true
            android.widget.Toast.makeText(this, "Đã ẩn bong bóng · hiện lại bằng nút trên thông báo HUY FAN", android.widget.Toast.LENGTH_LONG).show()
            setStatus(_status.value)
        },
    )

    private fun updateBubble() {
        val b = bubble ?: return
        val s = container.settingsState.value
        val canDraw = b.onLockScreen || Settings.canDrawOverlays(this)
        if (_running.value && s.bubbleEnabled && !bubbleHidden && canDraw) {
            b.show(BUBBLE_SIZES[s.bubbleSize.coerceIn(0, 2)])
            b.setSuppressed(ForegroundTracker.hideBubble)
            updateBubbleState()
        } else {
            b.hide()
        }
    }

    private fun updateBubbleState() {
        val b = bubble ?: return
        b.setState(
            when (container.engine.state.value.phase) {
                com.hankukko.huyfan.assistant.Phase.LISTENING -> BubbleState.LISTENING
                com.hankukko.huyfan.assistant.Phase.THINKING -> BubbleState.THINKING
                com.hankukko.huyfan.assistant.Phase.SPEAKING, com.hankukko.huyfan.assistant.Phase.GREETING -> BubbleState.SPEAKING
                else -> if (::wake.isInitialized && wake.isListening) BubbleState.WAITING else BubbleState.PAUSED
            }
        )
    }

    /** Nút trên thông báo: ẩn ↔ hiện bong bóng. */
    private fun toggleBubble() {
        val s = container.settingsState.value
        if (!s.bubbleEnabled) {
            bubbleHidden = false
            scope.launch { container.settings.update { it.copy(bubbleEnabled = true) } }
        } else {
            bubbleHidden = !bubbleHidden
            updateBubble()
            setStatus(_status.value)
        }
        if (!Settings.canDrawOverlays(this)) {
            Notifications.alert(this, "Bong bóng cần quyền", "Mở HUY FAN → Cài đặt → Quyền → bật “Hiển thị trên ứng dụng khác”.")
        }
    }

    /** Chạm bong bóng → mở cửa sổ hỏi (đè lên mọi app, kể cả khi đang ôn tập trong HUY FAN). */
    private fun openFromBubble() {
        resetAutoOff()
        val intent = Intent(this, AssistActivity::class.java)
            .setAction("com.hankukko.huyfan.BUBBLE")
            .putExtra(AssistActivity.EXTRA_FROM, AssistActivity.FROM_BUBBLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }.onFailure { Log.w(TAG, "open from bubble failed", it) }
    }

    private fun showAssistant() {
        if (ForegroundTracker.mainVisible || ForegroundTracker.assistVisible) return
        val intent = Intent(this, AssistActivity::class.java)
            .setAction("com.hankukko.huyfan.WAKE")
            .putExtra(AssistActivity.EXTRA_FROM, AssistActivity.FROM_WAKE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val canPop = Settings.canDrawOverlays(this)
        val ok = canPop && runCatching { startActivity(intent) }.isSuccess
        if (!ok) {
            // Không được phép bật màn hình trợ lý → vẫn nói chuyện bằng giọng, kèm thông báo để chạm xem
            Notifications.alert(this, "HUY FAN đang nghe…", "Chạm để xem câu trả lời trên màn hình.", toAssist = true)
        }
    }

    private fun scheduleResume(delayMs: Long) {
        resumeJob?.cancel()
        resumeJob = scope.launch {
            delay(delayMs)
            resumeListening()
        }
    }

    private fun resumeListening() {
        if (!_running.value || !::wake.isInitialized || !wake.isLoaded) return
        if (conversationBusy) return
        if (container.engine.micHold.value) return
        if (screenOff && container.settingsState.value.pauseOnScreenOff) {
            setStatus("Tạm dừng khi tắt màn hình"); return
        }
        if (wake.isListening) return
        val st = container.settingsState.value
        if (!st.wakeListenEnabled) {
            setStatus("Chỉ dùng bong bóng – chạm bong bóng để hỏi"); return
        }
        if (wake.start(st.wakePhraseList, st.wakeMinConf, st.wakeStableHits)) {
            errorStreak = 0
            setStatus("Gọi “HUY FAN” để bắt đầu")
        }
    }

    private fun pauseListening(reason: String = "Đang trò chuyện…") {
        resumeJob?.cancel()
        if (::wake.isInitialized && wake.isListening) wake.stop()
        if (_running.value) setStatus(reason)
    }

    private fun resetAutoOff() {
        autoOffJob?.cancel()
        val minutes = container.settingsState.value.autoOffMinutes
        if (minutes <= 0) return
        autoOffAt = System.currentTimeMillis() + minutes * 60_000L
        autoOffJob = scope.launch {
            delay(minutes * 60_000L)
            Notifications.alert(
                this@StandbyService, "Đã tắt chế độ chờ",
                "Sau $minutes phút không dùng, HUY FAN tự tắt để tiết kiệm pin. Bật lại bằng tile/widget nha."
            )
            stopSelf()
        }
    }

    private fun setStatus(text: String) {
        Log.i(TAG, "status: $text")
        _status.value = text
        if (!_running.value) return
        updateBubbleState()
        val s = container.settingsState.value
        val shown = if (s.bubbleEnabled || bubbleHidden) (bubble?.isShowing == true) else null
        runCatching {
            NotificationManagerCompat.from(this).notify(Notifications.ID_STANDBY, Notifications.standby(this, text, shown))
        }
    }

    private fun vibrate() {
        runCatching {
            val v = getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            else v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun refreshShortcuts() {
        HuyFanWidget.refresh(this)
        StandbyTileService.refresh(this)
    }

    override fun onDestroy() {
        // Hết giờ chờ / tắt chờ → bong bóng tắt theo
        bubble?.hide(animate = false)
        bubble = null
        ForegroundTracker.onChange = null
        _running.value = false
        autoOffAt = 0L
        _status.value = ""
        if (initialized) {
            runCatching { unregisterReceiver(screenReceiver) }
            if (::wake.isInitialized) wake.release()
            container.engine.onMicRequest = null
        }
        scope.cancel()
        refreshShortcuts()
        super.onDestroy()
    }
}
