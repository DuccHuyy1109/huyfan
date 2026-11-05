import io, sys, os

root = sys.argv[1]  # .../app/src/main
J = 'java/com/hankukko/huyfan/'


def patch(rel, pairs):
    p = os.path.join(root, rel)
    s = io.open(p, encoding='utf-8').read()
    for a, b in pairs:
        if a not in s:
            raise SystemExit(rel + ' NOT FOUND: ' + a[:90])
        s = s.replace(a, b, 1)
    io.open(p, 'w', encoding='utf-8').write(s)


# ---------- Cài đặt ----------
patch(J + 'data/Settings.kt', [
    ('''    val instantFromNotebook: Boolean = true,
''', '''    val instantFromNotebook: Boolean = true,
    /** Bong bóng chat nổi khi chế độ chờ bật */
    val bubbleEnabled: Boolean = true,
    /** 0 nhỏ · 1 vừa · 2 lớn */
    val bubbleSize: Int = 1,
    /** Chạm bong bóng: true = nghe ngay · false = mở bàn phím (nơi ồn) */
    val bubbleTapListen: Boolean = true,
    /** Nghe từ khoá "HUY FAN" khi chế độ chờ bật (tắt = chỉ dùng bong bóng, đỡ tốn pin) */
    val wakeListenEnabled: Boolean = true,
'''),
    ('''    val instantNotebook = booleanPreferencesKey("instant_notebook")''', '''    val instantNotebook = booleanPreferencesKey("instant_notebook")
    val bubbleOn = booleanPreferencesKey("bubble_on")
    val bubbleSize = intPreferencesKey("bubble_size")
    val bubbleListen = booleanPreferencesKey("bubble_listen")
    val wakeListen = booleanPreferencesKey("wake_listen")'''),
    ('''        instantFromNotebook = this[K.instantNotebook] ?: d.instantFromNotebook,
''', '''        instantFromNotebook = this[K.instantNotebook] ?: d.instantFromNotebook,
        bubbleEnabled = this[K.bubbleOn] ?: d.bubbleEnabled,
        bubbleSize = this[K.bubbleSize] ?: d.bubbleSize,
        bubbleTapListen = this[K.bubbleListen] ?: d.bubbleTapListen,
        wakeListenEnabled = this[K.wakeListen] ?: d.wakeListenEnabled,
'''),
    ('''    this[K.instantNotebook] = s.instantFromNotebook
''', '''    this[K.instantNotebook] = s.instantFromNotebook
    this[K.bubbleOn] = s.bubbleEnabled
    this[K.bubbleSize] = s.bubbleSize
    this[K.bubbleListen] = s.bubbleTapListen
    this[K.wakeListen] = s.wakeListenEnabled
'''),
])

# ---------- Báo cho bong bóng biết cửa sổ hỏi đang mở ----------
patch(J + 'HuyFanApp.kt', [
    ('''    val assistVisible: Boolean get() = resumed?.endsWith(".AssistActivity") == true

    override fun onActivityResumed(activity: Activity) {
        resumed = activity.javaClass.name
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed == activity.javaClass.name) resumed = null
    }''', '''    val assistVisible: Boolean get() = resumed?.endsWith(".AssistActivity") == true

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
    }'''),
])

# ---------- Thông báo: nút ẩn/hiện bong bóng ----------
patch(J + 'service/Notifications.kt', [
    ('''    fun standby(context: Context, status: String): android.app.Notification {''',
     '''    fun standby(context: Context, status: String, bubbleShown: Boolean? = null): android.app.Notification {'''),
    ('''            .addAction(R.drawable.ic_mic_white, "Hỏi ngay", assistIntent(context, "notification"))
            .addAction(R.drawable.ic_power_white, "Tắt", stop)
            .build()''', '''            .addAction(R.drawable.ic_mic_white, "Hỏi ngay", assistIntent(context, "notification"))
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
            .build()'''),
])

# ---------- Chế độ chờ: quản lý bong bóng ----------
S = J + 'service/StandbyService.kt'
patch(S, [
    ('''        const val ACTION_STOP = "com.hankukko.huyfan.STANDBY_STOP"''', '''        const val ACTION_STOP = "com.hankukko.huyfan.STANDBY_STOP"
        const val ACTION_BUBBLE = "com.hankukko.huyfan.STANDBY_BUBBLE"
        private val BUBBLE_SIZES = intArrayOf(50, 58, 66)'''),
    ('''    private var errorStreak = 0
''', '''    private var errorStreak = 0
    private var bubble: BubbleController? = null
    /** Người dùng kéo bong bóng vào ✕ → ẩn tới lần bật chờ sau (hoặc bấm "Hiện bong bóng" trên thông báo) */
    private var bubbleHidden = false
'''),
    ('''        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }''', '''        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_BUBBLE && initialized) {
            toggleBubble()
            return START_NOT_STICKY
        }'''),
    ('''        val engine = container.engine
        engine.onMicRequest = { pauseListening() }
''', '''        val engine = container.engine
        engine.onMicRequest = { pauseListening() }

        // ---- Bong bóng chat nổi ----
        bubble = BubbleController(
            this,
            onTap = { openFromBubble() },
            onDismiss = {
                bubbleHidden = true
                android.widget.Toast.makeText(this, "Đã ẩn bong bóng · hiện lại bằng nút trên thông báo HUY FAN", android.widget.Toast.LENGTH_LONG).show()
                setStatus(_status.value)
            },
        )
        ForegroundTracker.onChange = { scope.launch { bubble?.setSuppressed(ForegroundTracker.assistVisible) } }
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
'''),
    ('''    private fun showAssistant() {''', '''    private fun updateBubble() {
        val b = bubble ?: return
        val s = container.settingsState.value
        if (_running.value && s.bubbleEnabled && !bubbleHidden && Settings.canDrawOverlays(this)) {
            b.show(BUBBLE_SIZES[s.bubbleSize.coerceIn(0, 2)])
            b.setSuppressed(ForegroundTracker.assistVisible)
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

    private fun showAssistant() {'''),
    ('''        if (wake.isListening) return
        val st = container.settingsState.value''', '''        if (wake.isListening) return
        val st = container.settingsState.value
        if (!st.wakeListenEnabled) {
            setStatus("Chỉ dùng bong bóng – chạm bong bóng để hỏi"); return
        }'''),
    ('''        _status.value = text
        if (!_running.value) return
        runCatching {
            NotificationManagerCompat.from(this).notify(Notifications.ID_STANDBY, Notifications.standby(this, text))
        }''', '''        _status.value = text
        if (!_running.value) return
        updateBubbleState()
        val s = container.settingsState.value
        val shown = if (s.bubbleEnabled || bubbleHidden) (bubble?.isShowing == true) else null
        runCatching {
            NotificationManagerCompat.from(this).notify(Notifications.ID_STANDBY, Notifications.standby(this, text, shown))
        }'''),
    ('''        _running.value = true
        refreshShortcuts()
        if (!initialized) {
            initialized = true
            setup()
        }''', '''        _running.value = true
        refreshShortcuts()
        if (!initialized) {
            initialized = true
            setup()
        }
        updateBubble()'''),
    ('''    override fun onDestroy() {
        _running.value = false''', '''    override fun onDestroy() {
        // Hết giờ chờ / tắt chờ → bong bóng tắt theo
        bubble?.hide(animate = false)
        bubble = null
        ForegroundTracker.onChange = null
        _running.value = false'''),
])

# ---------- Cửa sổ hỏi: mở từ bong bóng ----------
A = J + 'ui/AssistActivity.kt'
patch(A, [
    ('''        const val FROM_SHORTCUT = "shortcut"''', '''        const val FROM_SHORTCUT = "shortcut"
        const val FROM_BUBBLE = "bubble"'''),
    ('''    private val startedAt = mutableLongStateOf(0L)''', '''    private val startedAt = mutableLongStateOf(0L)
    /** Mở từ bong bóng ở chế độ "Gõ phím" → bật sẵn ô nhập và bàn phím */
    private val startTyping = mutableStateOf(false)'''),
    ('''                    onMic = { mic() },
                )''', '''                    onMic = { mic() },
                    startTyping = startTyping.value,
                )'''),
    ('''        if (intent?.getStringExtra(EXTRA_FROM) == FROM_WAKE) return''', '''        if (intent?.getStringExtra(EXTRA_FROM) == FROM_WAKE) return
        startTyping.value = false
        if (intent?.getStringExtra(EXTRA_FROM) == FROM_BUBBLE && !container.settingsState.value.bubbleTapListen) {
            // Nơi ồn: không bật mic, mở bàn phím để gõ
            container.engine.cancel()
            startTyping.value = true
            return
        }'''),
    ('''    onMic: () -> Unit,
) {
    val c = androidx.compose.ui.platform.LocalContext.current.container''', '''    onMic: () -> Unit,
    startTyping: Boolean = false,
) {
    val c = androidx.compose.ui.platform.LocalContext.current.container'''),
    ('''    var typing by remember { mutableStateOf(false) }''', '''    var typing by remember(startedAt) { mutableStateOf(startTyping) }
    LaunchedEffect(startTyping, startedAt) { if (startTyping) typing = true }
    val inputFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(typing, startedAt) {
        if (typing && startTyping) {
            kotlinx.coroutines.delay(250)
            runCatching { inputFocus.requestFocus(); keyboard?.show() }
        }
    }'''),
    ('''                    placeholder = { Text("Gõ câu hỏi…") },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),''', '''                    placeholder = { Text("Gõ câu hỏi…") },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).focusRequester(inputFocus),'''),
])
p = os.path.join(root, A)
s = io.open(p, encoding='utf-8').read()
for imp in ['import androidx.compose.ui.focus.focusRequester', 'import androidx.compose.runtime.LaunchedEffect']:
    if imp + '\n' not in s:
        s = s.replace('\nimport ', '\n' + imp + '\nimport ', 1)
io.open(p, 'w', encoding='utf-8').write(s)

# ---------- Trang cài đặt ----------
patch(J + 'ui/screens/SettingsScreen.kt', [
    ('''                SwitchRow("Bật chờ khi mở app", "Mở HUY FAN là tự bật nghe từ khoá", s.standbyOnLaunch) { v ->
                    vm.updateSettings { it.copy(standbyOnLaunch = v) }
                }
            }''', '''                SwitchRow("Bật chờ khi mở app", "Mở HUY FAN là tự bật nghe từ khoá", s.standbyOnLaunch) { v ->
                    vm.updateSettings { it.copy(standbyOnLaunch = v) }
                }
                SwitchRow(
                    "Nghe từ khoá “HUY FAN”", "Tắt nếu chỉ muốn dùng bong bóng chat (đỡ tốn pin hơn)", s.wakeListenEnabled,
                ) { v -> vm.updateSettings { it.copy(wakeListenEnabled = v) } }
                RowDivider()
                Text(
                    "Bong bóng chat nổi", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp),
                )
                Text(
                    "Nơi ồn gọi không được? Bong bóng nhỏ nổi trên mọi màn hình (kể cả lúc đang ôn tập) – chạm để hỏi ngay. " +
                        "Kéo thả tự do, thả ra tự hít vào mép; kéo xuống ✕ để ẩn. Tự tắt cùng chế độ chờ.",
                    style = MaterialTheme.typography.bodySmall, color = x.muted,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                SwitchRow("Hiện bong bóng chat", if (standbyOn) "Hiện khi chế độ chờ đang bật" else "Sẽ hiện khi bật chế độ chờ", s.bubbleEnabled) { v ->
                    vm.updateSettings { it.copy(bubbleEnabled = v) }
                }
                if (s.bubbleEnabled) {
                    val overlayOk = remember(tick) { Settings.canDrawOverlays(context) }
                    if (!overlayOk) {
                        StatusRow(
                            "Cần quyền hiển thị trên ứng dụng khác", "Để bong bóng nổi lên trên app khác", false,
                            fixLabel = "Cấp quyền",
                        ) {
                            context.tryStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                        }
                    }
                    Text("Kích thước", style = MaterialTheme.typography.labelLarge, color = x.muted, modifier = Modifier.padding(start = 16.dp, top = 6.dp))
                    Segmented(listOf(0, 1, 2), s.bubbleSize, { listOf("Nhỏ", "Vừa", "Lớn")[it] }, { v -> vm.updateSettings { it.copy(bubbleSize = v) } })
                    Text("Chạm bong bóng thì", style = MaterialTheme.typography.labelLarge, color = x.muted, modifier = Modifier.padding(start = 16.dp, top = 6.dp))
                    Segmented(
                        listOf(true, false), s.bubbleTapListen, { if (it) "🎤 Nghe ngay" else "⌨️ Gõ phím" },
                        { v -> vm.updateSettings { it.copy(bubbleTapListen = v) } },
                    )
                    Text(
                        if (s.bubbleTapListen) "Mở cửa sổ và bật mic luôn." else "Mở cửa sổ kèm bàn phím – hợp nơi ồn ào.",
                        style = MaterialTheme.typography.bodySmall, color = x.muted, modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }'''),
    ('''if (standbyOn) "Đang chờ gọi · " + (if (s.autoOffMinutes == 0) "không tự tắt" else "tự tắt sau ${s.autoOffMinutes} phút") else "Đang tắt"''',
     '''if (standbyOn) (if (s.wakeListenEnabled) "Đang chờ gọi" else "Chỉ bong bóng") + (if (s.bubbleEnabled) " · bong bóng" else "") + " · " + (if (s.autoOffMinutes == 0) "không tự tắt" else "tự tắt sau ${s.autoOffMinutes} phút") else "Đang tắt" + if (s.bubbleEnabled) " · bong bóng hiện khi bật chờ" else ""'''),
])
print('ok')
