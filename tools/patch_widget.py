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
    ('''    <string name="widget_desc">Hỏi nhanh và bật/tắt chế độ chờ</string>''',
     '''    <string name="widget_desc">Hỏi nhanh và bật/tắt chế độ chờ</string>
    <string name="widget_word_label">Từ của ngày</string>
    <string name="widget_word_desc">Mỗi ngày 1 từ trong sổ – nghe, lật xem nghĩa, ôn thẻ đến hạn</string>'''),
])

patch('AndroidManifest.xml', [
    ('''        <receiver
            android:name=".service.HuyFanWidget"''', '''        <receiver
            android:name=".service.WordWidget"
            android:exported="true"
            android:label="@string/widget_word_label">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_word_info" />
        </receiver>

        <receiver
            android:name=".service.HuyFanWidget"'''),
])

patch(J + 'service/Shortcuts.kt', [
    ('''        fun refresh(context: Context) {
            val app = context.applicationContext
            val mgr = AppWidgetManager.getInstance(app)
            val ids = mgr.getAppWidgetIds(ComponentName(app, HuyFanWidget::class.java))''', '''        fun refresh(context: Context) {
            val app = context.applicationContext
            WordWidget.refresh(app)
            val mgr = AppWidgetManager.getInstance(app)
            val ids = mgr.getAppWidgetIds(ComponentName(app, HuyFanWidget::class.java))'''),
])

# Bấm loa trên widget khi app vừa khởi động: chờ bộ đọc sẵn sàng rồi mới đọc
patch(J + 'voice/Speaker.kt', [
    ('''            val token = ++chainToken
            _speaking.value = true
            playChain(listOf(text.trim() to true), 0, token, slowKorean = true, onDone = null)''', '''            val token = ++chainToken
            _speaking.value = true
            val segs = listOf(text.trim() to true)
            if (!koE.ready) {
                init()
                waitReady(token, 0) { playChain(segs, 0, token, slowKorean = true, onDone = null) }
            } else {
                playChain(segs, 0, token, slowKorean = true, onDone = null)
            }'''),
])

patch(J + 'ui/screens/SettingsScreen.kt', [
    ('''                ActionRow("Thêm widget ra màn hình chính", "Nút mic + bật/tắt chờ + số từ hôm nay", Icons.Rounded.Widgets) {''',
     '''                ActionRow("Thêm widget “Từ của ngày”", "Mỗi ngày 1 từ: nghe, lật xem nghĩa, ôn thẻ đến hạn", Icons.Rounded.Widgets) {
                    val awm = AppWidgetManager.getInstance(context)
                    if (awm.isRequestPinAppWidgetSupported) awm.requestPinAppWidget(ComponentName(context, com.hankukko.huyfan.service.WordWidget::class.java), null, null)
                    else context.toast("Giữ màn hình chính → Widget → HUY FAN – Từ của ngày")
                }
                ActionRow("Thêm widget ra màn hình chính", "Nút mic + bật/tắt chờ + số từ hôm nay", Icons.Rounded.Widgets) {'''),
])
print('ok')
