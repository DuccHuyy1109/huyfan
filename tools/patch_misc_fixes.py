import sys, os

root = sys.argv[1]


def patch(rel, pairs):
    p = os.path.join(root, rel)
    s = open(p, encoding='utf-8').read()
    for old, new in pairs:
        if old not in s:
            raise SystemExit(rel + ' NOT FOUND: ' + old[:100])
        s = s.replace(old, new, 1)
    open(p, 'w', encoding='utf-8').write(s)


# ---------- 1. Bảng viết: giữ nét vẽ ở cấp thẻ ----------
patch('ui/components/DrawPad.kt', [
    ('''    onCheck: (List<List<InkPoint>>) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val x = LocalExtra.current
    val strokes = remember { mutableStateListOf<List<InkPoint>>() }''', '''    onCheck: (List<List<InkPoint>>) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    strokes: SnapshotStateList<List<InkPoint>> = remember { mutableStateListOf() },
) {
    val x = LocalExtra.current'''),
    ('import androidx.compose.runtime.mutableStateListOf\n', 'import androidx.compose.runtime.mutableStateListOf\nimport androidx.compose.runtime.snapshots.SnapshotStateList\n'),
])
patch('ui/screens/ReviewScreen.kt', [
    ('''    var inkResult by remember(card.key) { mutableStateOf<Pair<String, Boolean>?>(null) }''',
     '''    var inkResult by remember(card.key) { mutableStateOf<Pair<String, Boolean>?>(null) }
    val inkStrokes = remember(card.key) { androidx.compose.runtime.mutableStateListOf<List<com.hankukko.huyfan.ink.InkPoint>>() }'''),
    ('''            onClose = { penOpen = false },
            modifier = Modifier.weight(1f),
        )''', '''            onClose = { penOpen = false },
            modifier = Modifier.weight(1f),
            strokes = inkStrokes,
        )'''),
    ('''                                feedback = "Viết đúng! “$best” · $score%" to true
                                penOpen = false''', '''                                feedback = "Viết đúng! “$best” · $score%" to true
                                inkStrokes.clear()
                                penOpen = false'''),
])

# ---------- 2. Thanh hệ thống theo giao diện của app ----------
patch('ui/MainActivity.kt', [
    ('''            val settings by vm.settings.collectAsStateWithLifecycle()
            HuyFanTheme(settings.themeMode) {''', '''            val settings by vm.settings.collectAsStateWithLifecycle()
            val dark = com.hankukko.huyfan.ui.theme.isDarkTheme(settings.themeMode)
            LaunchedEffect(dark) { applySystemBars(dark) }
            HuyFanTheme(settings.themeMode) {'''),
    ('''    override fun onStop() {''', '''    /** Biểu tượng thanh trạng thái/điều hướng sáng hay tối theo giao diện của app (không theo hệ thống). */
    private fun applySystemBars(dark: Boolean) {
        val t = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = if (dark) androidx.activity.SystemBarStyle.dark(t) else androidx.activity.SystemBarStyle.light(t, t),
            navigationBarStyle = if (dark) androidx.activity.SystemBarStyle.dark(t) else androidx.activity.SystemBarStyle.light(t, t),
        )
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    }

    override fun onStop() {'''),
])

# ---------- 3. Hán tự: bỏ ký tự thừa ----------
patch('ai/AiModels.kt', [
    ('''            hanja = o.str("hanja", "han_han", "chinese"),''',
     '''            hanja = o.str("hanja", "han_han", "chinese").replace(Regex("[-–—_·.]+"), "").trim(),'''),
])

# ---------- 4. AI biết trạng thái app (chế độ chờ, tự tắt sau bao lâu) ----------
patch('service/StandbyService.kt', [
    ('''        private val _status = MutableStateFlow("")
        val status: StateFlow<String> = _status''', '''        private val _status = MutableStateFlow("")
        val status: StateFlow<String> = _status

        /** Thời điểm (ms) chế độ chờ sẽ tự tắt; 0 = không hẹn giờ. */
        @Volatile
        var autoOffAt: Long = 0L
            private set'''),
    ('''        autoOffJob = scope.launch {
            delay(minutes * 60_000L)''', '''        autoOffAt = System.currentTimeMillis() + minutes * 60_000L
        autoOffJob = scope.launch {
            delay(minutes * 60_000L)'''),
    ('''    override fun onDestroy() {
        _running.value = false''', '''    override fun onDestroy() {
        _running.value = false
        autoOffAt = 0L'''),
])
patch('ai/PromptBuilder.kt', [
    ('''            append("\\n\\nNGỮ CẢNH\\n- Hôm nay: ").append(Dates.spokenToday()).append('.')''',
     '''            append("\\n\\nNGỮ CẢNH\\n- Hôm nay: ").append(Dates.spokenToday()).append('.')
            append("\\n- Trạng thái app HUY FAN: ").append(appStatus(s))'''),
    ('''    fun userName(s: AppSettings): String''', '''    /** Mô tả trạng thái app để AI trả lời đúng khi người dùng hỏi về chính app. */
    fun appStatus(s: AppSettings): String {
        val running = com.hankukko.huyfan.service.StandbyService.running.value
        val offAt = com.hankukko.huyfan.service.StandbyService.autoOffAt
        val standby = if (!running) "chế độ chờ (gọi HUY FAN) đang TẮT" else {
            val left = if (offAt > 0) ((offAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0) else -1
            "chế độ chờ đang BẬT" + if (left >= 0) ", còn khoảng $left phút nữa sẽ tự tắt (cài đặt: tự tắt sau ${s.autoOffMinutes} phút không dùng)" else ", không hẹn tự tắt"
        }
        val ai = if (s.provider.name == "GEMINI") "Gemini ${s.geminiModel.ifBlank { s.geminiResolvedModel }}" else s.openaiModel
        return "$standby; AI đang dùng: $ai; giọng đọc: ${s.ttsMode.label}" +
            (if (s.ttsMode.name == "AI") " (${s.aiVoice})" else "") +
            "; ngắt câu sau ${"%.1f".format(s.endSilenceMs / 1000f)} giây im lặng."
    }

    fun userName(s: AppSettings): String'''),
])
print('ok')
