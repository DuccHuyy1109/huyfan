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


patch('res/values/strings.xml', [
    ('</resources>', '''    <string name="a11y_label">HUY FAN – Bong bóng trên màn hình khoá</string>
    <string name="a11y_summary">Hiện bong bóng HUY FAN cả khi máy đang khoá</string>
    <string name="a11y_desc">Android chỉ cho dịch vụ Trợ năng hiện lên trên màn hình khoá. HUY FAN dùng quyền này DUY NHẤT để vẽ bong bóng chat nổi, chạm vào là hỏi ngay mà không cần mở khoá. Không đọc nội dung màn hình, không thao tác thay bạn.</string>
</resources>'''),
])

patch('AndroidManifest.xml', [
    ('''        <receiver
            android:name=".service.ReminderReceiver"''', '''        <!-- Bong bóng hiện cả trên màn hình khoá (lớp nổi Trợ năng) -->
        <service
            android:name=".service.BubbleAccessibilityService"
            android:exported="true"
            android:label="@string/a11y_label"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/bubble_accessibility" />
        </service>

        <receiver
            android:name=".service.ReminderReceiver"'''),
])

# Bong bóng: chủ là dịch vụ Trợ năng → dùng lớp nổi Trợ năng (hiện trên màn hình khoá)
patch(J + 'service/Bubble.kt', [
    ('''    private val overlayType =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE''', '''    /** Dịch vụ Trợ năng → lớp nổi Trợ năng (hiện cả trên màn hình khoá); còn lại → lớp nổi thường. */
    val onLockScreen: Boolean = ctx is android.accessibilityservice.AccessibilityService

    private val overlayType =
        if (onLockScreen) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE'''),
    ('''    fun show(size: Int) {
        if (!Settings.canDrawOverlays(ctx)) return''', '''    fun show(size: Int) {
        if (!onLockScreen && !Settings.canDrawOverlays(ctx)) return'''),
])

S = J + 'service/StandbyService.kt'
patch(S, [
    ('''        // ---- Bong bóng chat nổi ----
        bubble = BubbleController(
            this,
            onTap = { openFromBubble() },
            onDismiss = {
                bubbleHidden = true
                android.widget.Toast.makeText(this, "Đã ẩn bong bóng · hiện lại bằng nút trên thông báo HUY FAN", android.widget.Toast.LENGTH_LONG).show()
                setStatus(_status.value)
            },
        )''', '''        // ---- Bong bóng chat nổi ----
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
        }'''),
    ('''    private fun updateBubble() {
        val b = bubble ?: return
        val s = container.settingsState.value
        if (_running.value && s.bubbleEnabled && !bubbleHidden && Settings.canDrawOverlays(this)) {''', '''    private fun createBubble(): BubbleController = BubbleController(
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
        if (_running.value && s.bubbleEnabled && !bubbleHidden && canDraw) {'''),
])

# Cài đặt: bật bong bóng trên màn hình khoá
patch(J + 'ui/screens/SettingsScreen.kt', [
    ('''                    Text("Kích thước", style = MaterialTheme.typography.labelLarge, color = x.muted, modifier = Modifier.padding(start = 16.dp, top = 6.dp))
                    Segmented(listOf(0, 1, 2), s.bubbleSize''', '''                    val lockOk = remember(tick) { com.hankukko.huyfan.service.BubbleAccessibilityService.isEnabled(context) }
                    StatusRow(
                        "Hiện cả trên màn hình khoá",
                        if (lockOk) "Đã bật – bong bóng hiện cả khi máy khoá, chạm là hỏi không cần mở khoá"
                        else "Cài đặt → Hỗ trợ → Ứng dụng đã cài → “HUY FAN – Bong bóng trên màn hình khoá” → Bật. Chỉ dùng để vẽ bong bóng, không đọc màn hình",
                        lockOk, fixLabel = "Mở cài đặt",
                    ) {
                        context.tryStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    Text("Kích thước", style = MaterialTheme.typography.labelLarge, color = x.muted, modifier = Modifier.padding(start = 16.dp, top = 6.dp))
                    Segmented(listOf(0, 1, 2), s.bubbleSize'''),
])
print('ok')
