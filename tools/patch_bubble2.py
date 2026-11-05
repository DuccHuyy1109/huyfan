import io, sys, os

root = sys.argv[1]  # .../huyfan


def patch(rel, pairs):
    p = os.path.join(root, rel)
    s = io.open(p, encoding='utf-8').read()
    for a, b in pairs:
        if a not in s:
            raise SystemExit(rel + ' NOT FOUND: ' + a[:90])
        s = s.replace(a, b, 1)
    io.open(p, 'w', encoding='utf-8').write(s)


# Nút ảnh báo trước khi mở camera / thư viện (để cửa sổ bong bóng không tự đóng)
patch('ui/components/ChatImage.kt', [
    ('''fun AttachImageButton(onPicked: (String) -> Unit, modifier: Modifier = Modifier, size: Dp = 40.dp) {''',
     '''fun AttachImageButton(onPicked: (String) -> Unit, modifier: Modifier = Modifier, size: Dp = 40.dp, onLaunch: () -> Unit = {}) {'''),
    ('''                    cameraFile = f
                    cameraUri = u
                    runCatching { shoot.launch(u) }''', '''                    cameraFile = f
                    cameraUri = u
                    onLaunch()
                    runCatching { shoot.launch(u) }'''),
    ('''                    menu = false
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))''', '''                    menu = false
                    onLaunch()
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))'''),
])

A = 'ui/AssistActivity.kt'
patch(A, [
    ('''    private var handedOff = false''', '''    private var handedOff = false
    /** Đang mở camera / thư viện ảnh → không đóng cửa sổ khi bị che */
    private var pickingImage = false'''),
    ('''                    startTyping = startTyping.value,
                )''', '''                    startTyping = startTyping.value,
                    onPickStart = { pickingImage = true },
                )'''),
    ('''    startTyping: Boolean = false,
) {
    val c = androidx.compose.ui.platform.LocalContext.current.container''', '''    startTyping: Boolean = false,
    onPickStart: () -> Unit = {},
) {
    val c = androidx.compose.ui.platform.LocalContext.current.container'''),
    ('''com.hankukko.huyfan.ui.components.AttachImageButton(onPicked = { engine.cancel(); engine.attachImage(it); typing = true })''',
     '''com.hankukko.huyfan.ui.components.AttachImageButton(
                        onPicked = { engine.cancel(); engine.attachImage(it); typing = true },
                        onLaunch = { engine.cancel(); onPickStart() },
                    )'''),
    ('''    override fun onStop() {
        super.onStop()''', '''    override fun onResume() {
        super.onResume()
        pickingImage = false
    }

    override fun onStop() {
        super.onStop()
        if (pickingImage) return'''),
])

# Bong bóng tự ẩn khi đang ở tab Chat (đã có mic + bàn phím), vẫn hiện ở Ôn tập / tab khác / app khác
patch('HuyFanApp.kt', [
    ('''    /** Gọi mỗi khi màn hình của app đổi (vd ẩn bong bóng khi cửa sổ hỏi đang mở). */''', '''    /** MainActivity đang mở tab Chat (bong bóng tự ẩn vì màn Chat đã có mic + bàn phím). */
    @Volatile
    var mainOnChat: Boolean = false
        private set

    fun setMainOnChat(v: Boolean) {
        if (mainOnChat == v) return
        mainOnChat = v
        onChange?.invoke()
    }

    /** Bong bóng nên tạm giấu: cửa sổ hỏi đang mở, hoặc đang ở tab Chat của app. */
    val hideBubble: Boolean get() = assistVisible || (mainVisible && mainOnChat)

    /** Gọi mỗi khi màn hình của app đổi (vd ẩn bong bóng khi cửa sổ hỏi đang mở). */'''),
])
patch('service/StandbyService.kt', [
    ('''        ForegroundTracker.onChange = { scope.launch { bubble?.setSuppressed(ForegroundTracker.assistVisible) } }''',
     '''        ForegroundTracker.onChange = { scope.launch { bubble?.setSuppressed(ForegroundTracker.hideBubble) } }'''),
    ('''            b.setSuppressed(ForegroundTracker.assistVisible)''', '''            b.setSuppressed(ForegroundTracker.hideBubble)'''),
])
patch('ui/MainActivity.kt', [
    ('''    // Rời tab Chat → dừng cuộc trò chuyện (vẫn giữ nội dung) để không nghe/trả lời lung tung
    LaunchedEffect(tab) {''', '''    // Rời tab Chat → dừng cuộc trò chuyện (vẫn giữ nội dung) để không nghe/trả lời lung tung
    LaunchedEffect(tab) {
        com.hankukko.huyfan.ForegroundTracker.setMainOnChat(tab == Tab.CHAT)'''),
])

# Ghi log khi xoá ảnh (kiểm chứng "xoá chat → mất ảnh")
patch('data/ChatImages.kt', [
    ('''    fun delete(paths: Collection<String>) {
        paths.filter { it.isNotBlank() }.forEach { p -> runCatching { File(p).delete() } }
    }''', '''    fun delete(paths: Collection<String>) {
        val n = paths.filter { it.isNotBlank() }.count { p -> runCatching { File(p).delete() }.getOrDefault(false) }
        if (paths.isNotEmpty()) Log.i(TAG, "deleted $n/${paths.size} images")
    }'''),
])
print('ok')
