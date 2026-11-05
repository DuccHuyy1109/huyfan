import io, sys, os

root = sys.argv[1]  # .../app/src/main


def patch(rel, pairs):
    p = os.path.join(root, rel)
    s = io.open(p, encoding='utf-8').read()
    for a, b in pairs:
        if a not in s:
            raise SystemExit(rel + ' NOT FOUND: ' + a[:90])
        s = s.replace(a, b, 1)
    io.open(p, 'w', encoding='utf-8').write(s)


J = 'java/com/hankukko/huyfan/'

patch(J + 'service/Reminder.kt', [
    ('''

/** Ngày hôm nay theo giờ máy – tiện cho test. */
internal fun todayDate(): LocalDate = LocalDate.now()
''', '\n'),
    ('import java.time.LocalDate\n', ''),
])

# ---------- Cài đặt: bật nhắc + giờ nhắc ----------
patch(J + 'data/Settings.kt', [
    ('''    val onboarded: Boolean = false,
''', '''    val onboarded: Boolean = false,
    /** Nhắc học mỗi ngày */
    val reminderEnabled: Boolean = true,
    /** Giờ nhắc, tính bằng phút trong ngày (20:30 = 1230) */
    val reminderMinutes: Int = 20 * 60 + 30,
    /** Hỏi lại từ đã có trong sổ → trả lời ngay từ sổ, không gọi AI */
    val instantFromNotebook: Boolean = true,
'''),
    ('''    val onboarded = booleanPreferencesKey("onboarded")''', '''    val onboarded = booleanPreferencesKey("onboarded")
    val reminderOn = booleanPreferencesKey("reminder_on")
    val reminderMin = intPreferencesKey("reminder_min")
    val instantNotebook = booleanPreferencesKey("instant_notebook")'''),
    ('''        onboarded = this[K.onboarded] ?: d.onboarded,
''', '''        onboarded = this[K.onboarded] ?: d.onboarded,
        reminderEnabled = this[K.reminderOn] ?: d.reminderEnabled,
        reminderMinutes = this[K.reminderMin] ?: d.reminderMinutes,
        instantFromNotebook = this[K.instantNotebook] ?: d.instantFromNotebook,
'''),
    ('''    this[K.onboarded] = s.onboarded
''', '''    this[K.onboarded] = s.onboarded
    this[K.reminderOn] = s.reminderEnabled
    this[K.reminderMin] = s.reminderMinutes
    this[K.instantNotebook] = s.instantFromNotebook
'''),
])

# ---------- Thông báo nhắc học ----------
patch(J + 'service/Notifications.kt', [
    ('''    const val CH_ALERTS = "alerts"''', '''    const val CH_ALERTS = "alerts"
    const val CH_REMIND = "remind"
    const val ID_REMIND = 1003
    const val ACTION_OPEN_DUE = "com.hankukko.huyfan.OPEN_DUE"'''),
    ('''                description = "HUY FAN đang nghe, tự tắt chế độ chờ..."
            }
        )''', '''                description = "HUY FAN đang nghe, tự tắt chế độ chờ..."
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REMIND, "Nhắc học mỗi ngày", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Nhắc ôn thẻ đến hạn và giữ chuỗi ngày học"
            }
        )'''),
    ('''    fun mainIntent(context: Context): PendingIntent = PendingIntent.getActivity(''', '''    /** Mở app và vào thẳng phiên ôn thẻ đến hạn. */
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
            .setColor(0xFF4F7BFF.toInt())
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

    fun mainIntent(context: Context): PendingIntent = PendingIntent.getActivity('''),
])

# ---------- Manifest ----------
patch('AndroidManifest.xml', [
    ('''    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />''',
     '''    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />'''),
    ('''        <receiver
            android:name=".service.HuyFanWidget"''', '''        <receiver
            android:name=".service.ReminderReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="com.hankukko.huyfan.REMIND" />
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.TIME_SET" />
                <action android:name="android.intent.action.TIMEZONE_CHANGED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>

        <receiver
            android:name=".service.HuyFanWidget"'''),
])

# ---------- Hẹn giờ khi mở app và khi đổi cài đặt ----------
patch(J + 'AppContainer.kt', [
    ('''        appScope.launch { sheets.syncPending() }''', '''        appScope.launch { sheets.syncPending() }
        appScope.launch {
            settingsState.map { it.reminderEnabled to it.reminderMinutes }.distinctUntilChanged().collect {
                com.hankukko.huyfan.service.Reminder.schedule(app, settingsState.value)
            }
        }'''),
])

# ---------- Thông báo "Ôn ngay" → vào thẳng phiên ôn ----------
patch(J + 'ui/MainActivity.kt', [
    ('''data class NavRequest(val tab: Tab, val vocabToday: Boolean? = null, val nonce: Long = System.nanoTime())''',
     '''data class NavRequest(
    val tab: Tab,
    val vocabToday: Boolean? = null,
    /** Mở luôn phiên ôn thẻ đến hạn (bấm thông báo nhắc học) */
    val startDue: Boolean = false,
    val nonce: Long = System.nanoTime(),
)'''),
    ('''            "com.hankukko.huyfan.OPEN_REVIEW" -> NavRequest(Tab.REVIEW)''', '''            "com.hankukko.huyfan.OPEN_REVIEW" -> NavRequest(Tab.REVIEW)
            com.hankukko.huyfan.service.Notifications.ACTION_OPEN_DUE -> NavRequest(Tab.REVIEW, startDue = true)'''),
    ('''    var lessonEditor by remember { mutableStateOf<LessonEditorRequest?>(null) }''', '''    var lessonEditor by remember { mutableStateOf<LessonEditorRequest?>(null) }
    var autoStartDue by remember { mutableStateOf(false) }'''),
    ('''        tab = r.tab
        if (r.vocabToday != null)''', '''        tab = r.tab
        if (r.startDue) autoStartDue = true
        if (r.vocabToday != null)'''),
    ('''                                vm.engine.submit("Kiểm tra tôi các từ đã học hôm nay, hỏi từng từ một nhé", handsFree = true)
                            },''', '''                                vm.engine.submit("Kiểm tra tôi các từ đã học hôm nay, hỏi từng từ một nhé", handsFree = true)
                            },
                            autoStartDue = autoStartDue,
                            onAutoStartConsumed = { autoStartDue = false },'''),
])

# ---------- Trang cài đặt "Nhắc học" ----------
patch(J + 'ui/screens/SettingsScreen.kt', [
    ('''        item {
            CategoryRow("Dữ liệu", Icons.Rounded.Storage, "Xuất Excel, xoá lịch sử") { openPage = 8 }
        }''', '''        item {
            CategoryRow(
                "Nhắc học mỗi ngày", Icons.Rounded.NotificationsActive,
                if (s.reminderEnabled) "Nhắc lúc %02d:%02d · ôn thẻ đến hạn, giữ chuỗi".format(s.reminderMinutes / 60, s.reminderMinutes % 60) else "Đang tắt",
            ) { openPage = 9 }
        }
        item {
            CategoryRow("Dữ liệu", Icons.Rounded.Storage, "Xuất Excel, khôi phục, xoá lịch sử") { openPage = 8 }
        }'''),
    ('''                    8 -> SheetPage("Dữ liệu", Icons.Rounded.Storage) {''', '''                    9 -> SheetPage("Nhắc học mỗi ngày", Icons.Rounded.NotificationsActive) {
                var pickTime by remember { mutableStateOf(false) }
                var testMsg by remember { mutableStateOf<String?>(null) }
                SwitchRow(
                    "Nhắc học", "Tới giờ: còn thẻ đến hạn thì nhắc ôn; chưa học gì hôm nay thì nhắc giữ chuỗi",
                    s.reminderEnabled,
                ) { v -> vm.updateSettings { it.copy(reminderEnabled = v) } }
                ActionRow(
                    "Giờ nhắc", "%02d:%02d mỗi ngày · chạm để đổi".format(s.reminderMinutes / 60, s.reminderMinutes % 60),
                    Icons.Rounded.Schedule,
                ) { pickTime = true }
                ActionRow("Gửi thử thông báo", testMsg ?: "Xem thông báo nhắc học trông thế nào", Icons.Rounded.NotificationsActive) {
                    vm.testReminder { ok -> testMsg = if (ok) "Đã gửi – kéo thanh thông báo xuống để xem" else "Không gửi được – kiểm tra quyền Thông báo" }
                }
                if (pickTime) {
                    TimePickDialog(
                        s.reminderMinutes,
                        onPick = { m -> vm.updateSettings { it.copy(reminderMinutes = m) }; pickTime = false },
                        onDismiss = { pickTime = false },
                    )
                }
            }
                    8 -> SheetPage("Dữ liệu", Icons.Rounded.Storage) {'''),
    ('''@Composable
private fun CategoryRow(''', '''@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(minutes: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = androidx.compose.material3.rememberTimePickerState(minutes / 60, minutes % 60, is24Hour = true)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Giờ nhắc học") },
        text = { androidx.compose.material3.TimePicker(state) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("Lưu") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Huỷ") } },
    )
}

@Composable
private fun CategoryRow('''),
])
p = os.path.join(root, J + 'ui/screens/SettingsScreen.kt')
s = io.open(p, encoding='utf-8').read()
for ic in ['NotificationsActive', 'Schedule']:
    line = 'import androidx.compose.material.icons.rounded.%s\n' % ic
    if line not in s:
        s = s.replace('import androidx.compose.material.icons.Icons\n', 'import androidx.compose.material.icons.Icons\n' + line, 1)
io.open(p, 'w', encoding='utf-8').write(s)

patch(J + 'ui/MainViewModel.kt', [
    ('''    fun testSheets(url: String, onDone: (String) -> Unit) = viewModelScope.launch {''', '''    fun testReminder(onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { runCatching { com.hankukko.huyfan.service.Reminder.fire(getApplication(), force = true) }.getOrDefault(false) }
        onDone(ok)
    }

    fun testSheets(url: String, onDone: (String) -> Unit) = viewModelScope.launch {'''),
])
print('ok')
