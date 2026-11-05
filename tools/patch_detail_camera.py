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


# =====================================================================================
# 1) Cửa sổ bong bóng không tự đóng khi đang chụp / chọn ảnh
# =====================================================================================
A = 'ui/AssistActivity.kt'
patch(A, [
    ('''    private fun close() {
        if (!handedOff) container.engine.cancel()
        finish()
    }''', '''    private fun close() {
        // Đang chụp / chọn ảnh (dù lâu) → không đóng, kẻo camera trả ảnh về thì cửa sổ đã mất
        if (pickingImage || com.hankukko.huyfan.ForegroundTracker.pickingImage) return
        if (!handedOff) container.engine.cancel()
        finish()
    }'''),
    ('''        if (state.phase == Phase.IDLE && !typing && !interpreter) {''',
     '''        if (state.phase == Phase.IDLE && !typing && !interpreter && !com.hankukko.huyfan.ForegroundTracker.pickingImage) {'''),
])

# =====================================================================================
# 2) Xử lý ảnh nhanh: ImageDecoder giải mã thẳng ở kích thước nhỏ + tự xoay theo EXIF
# =====================================================================================
patch('data/ChatImages.kt', [
    ('''    fun saveFromUri(context: Context, uri: Uri): String? = runCatching {
        val cr = context.contentResolver''', '''    fun saveFromUri(context: Context, uri: Uri): String? {
        val t0 = System.currentTimeMillis()
        val fast = if (android.os.Build.VERSION.SDK_INT >= 28) runCatching { saveFast(context, uri) }
            .onFailure { Log.w(TAG, "fast decode failed, fallback", it) }.getOrNull() else null
        val out = fast ?: saveCompat(context, uri)
        Log.i(TAG, "processed in ${System.currentTimeMillis() - t0}ms (${if (fast != null) "ImageDecoder" else "compat"})")
        return out
    }

    /** Android 9+: giải mã thẳng ở kích thước đích (nhanh, ít RAM), tự xoay theo EXIF. */
    @androidx.annotation.RequiresApi(28)
    private fun saveFast(context: Context, uri: Uri): String {
        val src = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
        val bmp = android.graphics.ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(w, h))
            if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.memorySizePolicy = android.graphics.ImageDecoder.MEMORY_POLICY_LOW_RAM
        }
        val f = File(dir(context), "img_${UUID.randomUUID()}.jpg")
        f.outputStream().buffered().use { bmp.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        Log.i(TAG, "saved ${f.name} ${bmp.width}x${bmp.height} ${f.length() / 1024}KB")
        bmp.recycle()
        return f.absolutePath
    }

    private fun saveCompat(context: Context, uri: Uri): String? = runCatching {
        val cr = context.contentResolver'''),
])

# Hiện ngay "Đang xử lý ảnh…" trong lúc nén
patch('ui/components/ChatImage.kt', [
    ('''fun AttachImageButton(onPicked: (String) -> Unit, modifier: Modifier = Modifier, size: Dp = 40.dp, onLaunch: () -> Unit = {}) {''',
     '''fun AttachImageButton(
    onPicked: (String) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    onLaunch: () -> Unit = {},
    onBusy: (Boolean) -> Unit = {},
) {'''),
    ('''        if (uri == null) return
        busy = true
        scope.launch {
            val path = withContext(Dispatchers.IO) { ChatImages.saveFromUri(context, uri) }
            busy = false''', '''        if (uri == null) return
        busy = true
        onBusy(true)
        scope.launch {
            val path = withContext(Dispatchers.IO) { ChatImages.saveFromUri(context, uri) }
            busy = false
            onBusy(false)'''),
])

# =====================================================================================
# 3) Chế độ trả lời chi tiết
# =====================================================================================
patch('data/Settings.kt', [
    ('''    val wakeListenEnabled: Boolean = true,
''', '''    val wakeListenEnabled: Boolean = true,
    /** Trả lời chi tiết, kỹ càng (bảng từ vựng/ngữ pháp vẫn như thường) */
    val detailedAnswers: Boolean = false,
'''),
    ('''    val wakeListen = booleanPreferencesKey("wake_listen")''', '''    val wakeListen = booleanPreferencesKey("wake_listen")
    val detailed = booleanPreferencesKey("detailed_answers")'''),
    ('''        wakeListenEnabled = this[K.wakeListen] ?: d.wakeListenEnabled,
''', '''        wakeListenEnabled = this[K.wakeListen] ?: d.wakeListenEnabled,
        detailedAnswers = this[K.detailed] ?: d.detailedAnswers,
'''),
    ('''    this[K.wakeListen] = s.wakeListenEnabled
''', '''    this[K.wakeListen] = s.wakeListenEnabled
    this[K.detailed] = s.detailedAnswers
'''),
])

patch('ai/PromptBuilder.kt', [
    ('''            append("\\n\\n").append(OUTPUT_CONTRACT)
        }
    }''', '''            if (s.detailedAnswers) append(DETAIL_RULE)
            append("\\n\\n").append(OUTPUT_CONTRACT)
        }
    }

    /** Bật "Trả lời chi tiết": phần reply giảng kỹ, còn vocab/grammar giữ nguyên định dạng. */
    const val DETAIL_RULE = """

CHẾ ĐỘ TRẢ LỜI CHI TIẾT (người dùng đang bật – ưu tiên hơn yêu cầu "ngắn gọn" ở trên)
- reply giải thích KỸ CÀNG như một giáo viên tận tâm (khoảng 6–12 câu, chia ý rõ ràng, xuống dòng giữa các ý):
  • nghĩa chính + các nghĩa/sắc thái khác, khi nào dùng, mức độ trang trọng
  • cách dùng & cấu trúc đi kèm, 2–3 câu ví dụ thực tế (tiếng Hàn – nghĩa tiếng Việt)
  • phát âm cần chú ý (biến âm), từ dễ nhầm, mẹo nhớ (Hán Việt nếu có)
- Dùng gạch đầu dòng "•" và xuống dòng; KHÔNG dùng markdown (*, #, **).
- Bảng vocab / grammar vẫn giữ đúng định dạng và số lượng như bình thường (không nhồi thêm vào bảng)."""'''),
])

# Lệnh giọng nói: "trả lời chi tiết" / "trả lời ngắn gọn"
patch('ai/AiModels.kt', [
    ('''    const val END = "end_conversation"''', '''    const val END = "end_conversation"
    const val DETAIL_ON = "detail_on"
    const val DETAIL_OFF = "detail_off"'''),
])
patch('assistant/LocalCommands.kt', [
    ('''    private val historyRe = Regex("^(mo |xem )?(lich su)( chat| hoi| tro chuyen)?$")''', '''    private val historyRe = Regex("^(mo |xem )?(lich su)( chat| hoi| tro chuyen)?$")
    private val detailOnRe = Regex("^(bat |mo )?(che do )?(tra loi |giai thich )?(chi tiet|ki cang|ky cang|ki hon|ky hon)( hon)?( nhe| nha| di)?$")
    private val detailOffRe = Regex("^(tat che do chi tiet|(bat |mo )?(che do )?(tra loi )?(ngan gon|ngan thoi|ngan lai))( nhe| nha| di)?$")'''),
    ('''            historyRe.matches(q) -> AssistantReply(''', '''            detailOnRe.matches(q) -> AssistantReply(
                reply = "Ok $you, từ giờ tui giải thích chi tiết, kỹ càng nha!", intent = "command", action = Actions.DETAIL_ON,
            )
            detailOffRe.matches(q) -> AssistantReply(
                reply = "Ok $you, tui trả lời ngắn gọn lại nha!", intent = "command", action = Actions.DETAIL_OFF,
            )
            historyRe.matches(q) -> AssistantReply('''),
])
patch('assistant/ConversationEngine.kt', [
    ('''                Actions.OPEN_HISTORY -> _events.tryEmit(EngineEvent.OpenHistory)''', '''                Actions.OPEN_HISTORY -> _events.tryEmit(EngineEvent.OpenHistory)
                Actions.DETAIL_ON -> c.settings.update { it.copy(detailedAnswers = true) }
                Actions.DETAIL_OFF -> c.settings.update { it.copy(detailedAnswers = false) }'''),
])

# Công tắc dùng chung
patch('ui/components/Components.kt', [
    ('''@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {''', '''/** Công tắc nhanh "📖 Chi tiết": bật → AI giảng kỹ, bảng từ vựng/ngữ pháp vẫn như thường. */
@Composable
fun DetailToggle(on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val color = Brand.Violet
    Row(
        modifier
            .clip(CircleShape)
            .background(if (on) color else color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = if (on) 0f else 0.35f), CircleShape)
            .clickable(onClick = onToggle)
            .padding(horizontal = if (compact) 9.dp else 12.dp, vertical = if (compact) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("📖", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(4.dp))
        Text(
            if (compact) (if (on) "Chi tiết" else "Ngắn") else if (on) "Trả lời chi tiết: BẬT" else "Trả lời chi tiết: TẮT",
            color = if (on) Color.White else color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {'''),
])

# Chat: công tắc phía trên ô nhập + "Đang xử lý ảnh…"
C = 'ui/screens/ChatScreen.kt'
patch(C, [
    ('''    val pendingImage by vm.engine.pendingImage.collectAsStateWithLifecycle()''', '''    val pendingImage by vm.engine.pendingImage.collectAsStateWithLifecycle()
    var processingImage by remember { mutableStateOf(false) }'''),
    ('''        // ---------- Ảnh đính kèm ----------
        pendingImage?.let { img ->''', '''        // ---------- Trả lời chi tiết (bật/tắt nhanh) + trạng thái xử lý ảnh ----------
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (processingImage) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
                Text("Đang xử lý ảnh…", style = MaterialTheme.typography.labelMedium, color = x.muted)
            }
            Spacer(Modifier.weight(1f))
            com.hankukko.huyfan.ui.components.DetailToggle(
                settings.detailedAnswers,
                onToggle = { vm.updateSettings { it.copy(detailedAnswers = !it.detailedAnswers) } },
            )
        }

        // ---------- Ảnh đính kèm ----------
        pendingImage?.let { img ->'''),
    ('''                com.hankukko.huyfan.ui.components.AttachImageButton(
                    onPicked = { vm.engine.attachImage(it) },
                    modifier = Modifier.padding(start = 4.dp),
                )''', '''                com.hankukko.huyfan.ui.components.AttachImageButton(
                    onPicked = { vm.engine.attachImage(it) },
                    modifier = Modifier.padding(start = 4.dp),
                    onBusy = { processingImage = it },
                )'''),
])

# Cửa sổ bong bóng: công tắc nhỏ trên thanh tiêu đề
patch(A, [
    ('''                Pill(phaseLabel(state.phase, state.listenLang == LANG_KO))
                Spacer(Modifier.weight(1f))''', '''                Pill(phaseLabel(state.phase, state.listenLang == LANG_KO))
                Spacer(Modifier.weight(1f))
                val st by c.settingsState.collectAsStateWithLifecycle()
                com.hankukko.huyfan.ui.components.DetailToggle(
                    st.detailedAnswers, compact = true,
                    onToggle = {
                        lastTouch = System.currentTimeMillis()
                        kotlinx.coroutines.MainScope().launch { c.settings.update { it.copy(detailedAnswers = !it.detailedAnswers) } }
                    },
                )'''),
])
p = os.path.join(root, A)
s = io.open(p, encoding='utf-8').read()
for imp in ['import kotlinx.coroutines.launch']:
    if imp + '\n' not in s:
        s = s.replace('\nimport ', '\n' + imp + '\nimport ', 1)
io.open(p, 'w', encoding='utf-8').write(s)

# Cài đặt: công tắc trong "Prompt & phong cách"
patch('ui/screens/SettingsScreen.kt', [
    ('''                SwitchRow(
                    "Trả lời tức thì từ sổ",''', '''                SwitchRow(
                    "Trả lời chi tiết, kỹ càng",
                    "AI giảng kỹ: nghĩa, sắc thái, cách dùng, ví dụ, phát âm… Bảng từ vựng/ngữ pháp vẫn như thường. Bật/tắt nhanh ở màn Chat hoặc nói “trả lời chi tiết” / “trả lời ngắn gọn”",
                    s.detailedAnswers,
                ) { v -> vm.updateSettings { it.copy(detailedAnswers = v) } }
                SwitchRow(
                    "Trả lời tức thì từ sổ",'''),
])
print('ok')
