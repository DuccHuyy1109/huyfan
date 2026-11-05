import io, sys, os

root = sys.argv[1]  # .../app/src/main/java/com/hankukko/huyfan


def patch(rel, pairs):
    p = os.path.join(root, rel)
    s = io.open(p, encoding='utf-8').read()
    for a, b in pairs:
        if a not in s:
            raise SystemExit(rel + ' NOT FOUND: ' + a[:90])
        s = s.replace(a, b, 1)
    io.open(p, 'w', encoding='utf-8').write(s)


def add_imports(rel, lines):
    p = os.path.join(root, rel)
    s = io.open(p, encoding='utf-8').read()
    for line in lines:
        if line + '\n' not in s:
            s = s.replace('\nimport ', '\n' + line + '\nimport ', 1)
    io.open(p, 'w', encoding='utf-8').write(s)


patch('ui/components/ChatImage.kt', [
    ('    fun import(uri: Uri?) {', '    fun importUri(uri: Uri?) {'),
    ('{ uri -> import(uri) }', '{ uri -> importUri(uri) }'),
    ('if (ok) import(cameraUri) else', 'if (ok) importUri(cameraUri) else'),
])

# ---------- Dữ liệu ----------
patch('data/Entities.kt', [
    ('''    /** Người dùng đã xoá nội dung học của lượt này → không đưa vào ngữ cảnh AI nữa */
    @ColumnInfo(defaultValue = "0") val excluded: Boolean = false,
) {''', '''    /** Người dùng đã xoá nội dung học của lượt này → không đưa vào ngữ cảnh AI nữa */
    @ColumnInfo(defaultValue = "0") val excluded: Boolean = false,
    /** Ảnh đính kèm câu hỏi (file trong thư mục riêng của app) – xoá đoạn chat sẽ xoá luôn file */
    @ColumnInfo(defaultValue = "") val imagePath: String = "",
) {
    val hasImage: Boolean get() = imagePath.isNotBlank()'''),
])
patch('data/Daos.kt', [
    ('''    @Query("DELETE FROM messages WHERE sessionId = :id")
    suspend fun deleteMessagesOf(id: Long)''', '''    @Query("DELETE FROM messages WHERE sessionId = :id")
    suspend fun deleteMessagesOf(id: Long)

    @Query("SELECT imagePath FROM messages WHERE sessionId = :id AND imagePath != ''")
    suspend fun imagePathsOf(id: Long): List<String>

    @Query("SELECT imagePath FROM messages WHERE imagePath != ''")
    suspend fun allImagePaths(): List<String>'''),
    ('''    version = 5,''', '''    version = 6,'''),
    ('''        AutoMigration(from = 4, to = 5),''', '''        AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6),'''),
])
patch('ai/AiModels.kt', [
    ('''data class Turn(val role: String, val text: String) {''',
     '''/** 1 lượt hội thoại gửi AI. [image] = đường dẫn ảnh đính kèm (chỉ gửi cho câu hỏi có ảnh gần nhất). */
data class Turn(val role: String, val text: String, val image: String? = null) {'''),
])
patch('data/Repositories.kt', [
    ('''    suspend fun addUser(sessionId: Long, text: String): Long {
        val now = System.currentTimeMillis()
        dao.touch(sessionId, now)
        return dao.insertMessage(
            ChatMessage(sessionId = sessionId, role = ChatMessage.ROLE_USER, text = text, createdAt = now)
        )
    }''', '''    suspend fun addUser(sessionId: Long, text: String, imagePath: String = ""): Long {
        val now = System.currentTimeMillis()
        dao.touch(sessionId, now)
        return dao.insertMessage(
            ChatMessage(sessionId = sessionId, role = ChatMessage.ROLE_USER, text = text, createdAt = now, imagePath = imagePath)
        )
    }'''),
    ('''    suspend fun recentTurns(sessionId: Long, n: Int): List<Turn> =
        dao.recent(sessionId, n.coerceAtLeast(1))
            .asReversed()
            .filterNot { it.isError || it.excluded }
            .map { m ->
                val text = if (!m.isUser) buildString {
                    append(m.text)
                    if (m.vocabJson.isNotBlank()) append("\\n[Từ vựng đã đưa: ").append(m.vocab.joinToString { "${it.ko} (${it.vi})" }).append(']')
                    if (m.grammarJson.isNotBlank()) append("\\n[Ngữ pháp đã đưa: ").append(m.grammar.joinToString { it.pattern }).append(']')
                } else m.text
                Turn(if (m.isUser) Turn.USER else Turn.ASSISTANT, text)
            }''', '''    suspend fun recentTurns(sessionId: Long, n: Int): List<Turn> {
        val list = dao.recent(sessionId, n.coerceAtLeast(1))
            .asReversed()
            .filterNot { it.isError || it.excluded }
        // Chỉ gửi lại ẢNH GẦN NHẤT (để hỏi tiếp về ảnh đó), các ảnh cũ thay bằng chữ cho nhẹ
        val lastImageId = list.lastOrNull { it.isUser && it.hasImage }?.id
        return list.map { m ->
            val text = if (!m.isUser) buildString {
                append(m.text)
                if (m.vocabJson.isNotBlank()) append("\\n[Từ vựng đã đưa: ").append(m.vocab.joinToString { "${it.ko} (${it.vi})" }).append(']')
                if (m.grammarJson.isNotBlank()) append("\\n[Ngữ pháp đã đưa: ").append(m.grammar.joinToString { it.pattern }).append(']')
            } else when {
                m.hasImage && m.id != lastImageId -> "[Ảnh đã gửi trước đó] ${m.text}".trim()
                m.hasImage && m.text.isBlank() -> "(Xem ảnh đính kèm)"
                else -> m.text
            }
            Turn(if (m.isUser) Turn.USER else Turn.ASSISTANT, text, image = if (m.id == lastImageId) m.imagePath else null)
        }
    }'''),
    ('''    suspend fun deleteSession(id: Long) {
        dao.deleteMessagesOf(id)''', '''    suspend fun deleteSession(id: Long) {
        // Xoá đoạn chat → xoá luôn ảnh của đoạn đó
        ChatImages.delete(dao.imagePathsOf(id))
        dao.deleteMessagesOf(id)'''),
    ('''    suspend fun clearAll() {
        dao.clearMessages()''', '''    suspend fun allImagePaths(): List<String> = dao.allImagePaths()

    suspend fun clearAll() {
        ChatImages.delete(dao.allImagePaths())
        dao.clearMessages()'''),
])

# ---------- Gemini: gửi ảnh dạng inline_data ----------
patch('ai/Clients.kt', [
    ('''        mergeTurns(turns).forEach { t ->
            contents.put(
                JSONObject()
                    .put("role", if (t.role == Turn.USER) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", t.text)))
            )
        }''', '''        mergeTurns(turns).forEach { t ->
            val parts = JSONArray()
            t.image?.let { com.hankukko.huyfan.data.ChatImages.base64(it) }?.let { b64 ->
                parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", b64)))
            }
            parts.put(JSONObject().put("text", t.text.ifBlank { "(ảnh)" }))
            contents.put(
                JSONObject()
                    .put("role", if (t.role == Turn.USER) "user" else "model")
                    .put("parts", parts)
            )
        }'''),
    ('''                if (last != null && last.role == t.role) out[out.lastIndex] = Turn(t.role, last.text + "\\n" + t.text)''',
     '''                if (last != null && last.role == t.role) out[out.lastIndex] = Turn(t.role, last.text + "\\n" + t.text, t.image ?: last.image)'''),
    # OpenAI-compatible: nội dung dạng mảng có image_url
    ('''        turns.forEach {
            messages.put(JSONObject().put("role", if (it.role == Turn.USER) "user" else "assistant").put("content", it.text))
        }''', '''        turns.forEach {
            val b64 = it.image?.let { p -> com.hankukko.huyfan.data.ChatImages.base64(p) }
            val content: Any = if (b64 == null) it.text else JSONArray()
                .put(JSONObject().put("type", "text").put("text", it.text.ifBlank { "(ảnh)" }))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64")))
            messages.put(JSONObject().put("role", if (it.role == Turn.USER) "user" else "assistant").put("content", content))
        }'''),
])

# ---------- Dự phòng (Groq) gặp ảnh → dùng model đọc được ảnh ----------
patch('ai/AiService.kt', [
    ('''                try {
                    openai.generate(s.openaiBaseUrl, s.openaiKey, s.openaiModel, system, turns, s.temperature) to s.openaiModel''',
     '''                if (turns.any { it.image != null }) {
                    val vm = pickVisionModel(s)
                        ?: throw AiException("no vision", "API dự phòng không có model đọc được ảnh – đợi Gemini rảnh rồi hỏi lại nha.")
                    return openai.generate(s.openaiBaseUrl, s.openaiKey, vm, system, turns, s.temperature) to vm
                }
                try {
                    openai.generate(s.openaiBaseUrl, s.openaiKey, s.openaiModel, system, turns, s.temperature) to s.openaiModel'''),
    ('''    /** Chọn model chat tốt nhất từ /models của API chuẩn OpenAI (Groq, OpenRouter...). */''', '''    @Volatile
    private var visionModel: String? = null

    /** Model đọc được ảnh trên API dự phòng (Groq: Llama 4 Scout/Maverick…). */
    private suspend fun pickVisionModel(s: AppSettings): String? {
        visionModel?.let { return it }
        val all = runCatching { openai.listModels(s.openaiBaseUrl, s.openaiKey) }.getOrNull() ?: return null
        val prefs = listOf("llama-4-scout", "llama-4-maverick", "vision", "-vl", "gpt-4o", "gemini")
        for (p in prefs) all.firstOrNull { it.contains(p, ignoreCase = true) }?.let { visionModel = it; return it }
        return null
    }

    /** Chọn model chat tốt nhất từ /models của API chuẩn OpenAI (Groq, OpenRouter...). */'''),
])

# ---------- Engine: ảnh chờ gửi (gõ hoặc nói đều gửi kèm) ----------
E = 'assistant/ConversationEngine.kt'
patch(E, [
    ('''    fun submit(text: String, handsFree: Boolean = false) {
        val q = text.trim()
        if (q.isEmpty()) return''', '''    /** Ảnh đã đính kèm, chờ câu hỏi (gõ hoặc nói) để gửi cùng. */
    private val _pendingImage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val pendingImage: kotlinx.coroutines.flow.StateFlow<String?> = _pendingImage

    fun attachImage(path: String) {
        _pendingImage.value?.let { old -> if (old != path) com.hankukko.huyfan.data.ChatImages.delete(listOf(old)) }
        _pendingImage.value = path
    }

    /** Bỏ ảnh chưa gửi (xoá luôn file). */
    fun discardImage() {
        _pendingImage.value?.let { com.hankukko.huyfan.data.ChatImages.delete(listOf(it)) }
        _pendingImage.value = null
    }

    fun submit(text: String, handsFree: Boolean = false) {
        val image = _pendingImage.value
        val q = text.trim()
        if (q.isEmpty() && image == null) return
        _pendingImage.value = null'''),
    ('''            val sid = ensureSession(q)
            c.chat.addUser(sid, q)
''', '''            val sid = ensureSession(q.ifBlank { "Hỏi bằng ảnh" })
            c.chat.addUser(sid, q, image.orEmpty())
'''),
    ('''            val reply: AssistantReply = LocalCommands.match(q, settings.style)
                // Hỏi lại từ đã có trong sổ → trả lời ngay, không chờ AI
                ?: (if (settings.instantFromNotebook) NotebookAnswer.match(q, c.vocab.allNow(), settings.style)
                    ?.copy(latencyMs = (System.currentTimeMillis() - t0).coerceAtLeast(1)) else null)
                ?: try {
                val history = c.chat.recentTurns(sid, settings.contextTurns)
                val system = PromptBuilder.system(settings, c.vocab.today(), c.vocab.recent(7), c.grammar.today())''', '''            val reply: AssistantReply = (if (image == null) LocalCommands.match(q, settings.style) else null)
                // Hỏi lại từ đã có trong sổ → trả lời ngay, không chờ AI
                ?: (if (image == null && settings.instantFromNotebook) NotebookAnswer.match(q, c.vocab.allNow(), settings.style)
                    ?.copy(latencyMs = (System.currentTimeMillis() - t0).coerceAtLeast(1)) else null)
                ?: try {
                val history = c.chat.recentTurns(sid, settings.contextTurns)
                val system = PromptBuilder.system(settings, c.vocab.today(), c.vocab.recent(7), c.grammar.today()) +
                    if (history.any { it.image != null }) PromptBuilder.IMAGE_RULE else ""'''),
])

patch('ai/PromptBuilder.kt', [
    ('''    fun geminiSchema(): JSONObject {''', '''    /** Thêm vào prompt khi câu hỏi có kèm ảnh. */
    const val IMAGE_RULE = """

ẢNH ĐÍNH KÈM
- Câu hỏi có kèm 1 ảnh (biển hiệu, menu, sách, màn hình, đồ vật…). Nhìn kỹ ảnh rồi trả lời đúng câu hỏi.
- Không hỏi gì cụ thể / chỉ gửi ảnh: chép lại chữ tiếng Hàn trong ảnh, dịch sang tiếng Việt, giải thích ngắn điểm đáng học.
- Ảnh không có chữ Hàn: nói ảnh là gì và cho tên tiếng Hàn của các đồ vật chính.
- vocab: tối đa 5 từ đáng học nhất xuất hiện trong ảnh (save = true). Chữ trong ảnh mờ/không chắc thì nói rõ, tuyệt đối không bịa."""

    fun geminiSchema(): JSONObject {'''),
])

# ---------- Dọn ảnh mồ côi khi mở app ----------
patch('AppContainer.kt', [
    ('''        appScope.launch {
            vocab.cleanHanja()''', '''        appScope.launch {
            runCatching { com.hankukko.huyfan.data.ChatImages.cleanOrphans(app, chat.allImagePaths().toSet(), engine.pendingImage.value) }
        }
        appScope.launch {
            vocab.cleanHanja()'''),
])

# ---------- Chat: nút ảnh, thanh đính kèm, gửi kèm ảnh ----------
C = 'ui/screens/ChatScreen.kt'
patch(C, [
    ('''    var hanjaOpen by remember { mutableStateOf<Pair<String, String>?>(null) }''', '''    var hanjaOpen by remember { mutableStateOf<Pair<String, String>?>(null) }
    val pendingImage by vm.engine.pendingImage.collectAsStateWithLifecycle()'''),
    ('''        // ---------- Ô nhập ----------
        Row(''', '''        // ---------- Ảnh đính kèm ----------
        pendingImage?.let { img ->
            com.hankukko.huyfan.ui.components.AttachmentBar(
                img,
                onRemove = { vm.engine.discardImage() },
                onQuickAsk = { q -> vm.send(q); input = "" },
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp),
            )
        }

        // ---------- Ô nhập ----------
        Row('''),
    ('''                LangToggle(ko = state.listenLang == LANG_KO, onClick = { vm.engine.toggleLanguage() })
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Hỏi từ vựng, dịch, ngữ pháp…", color = x.muted) },''', '''                LangToggle(ko = state.listenLang == LANG_KO, onClick = { vm.engine.toggleLanguage() })
                com.hankukko.huyfan.ui.components.AttachImageButton(
                    onPicked = { vm.engine.attachImage(it) },
                    modifier = Modifier.padding(start = 4.dp),
                )
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(if (pendingImage != null) "Hỏi về ảnh này…" else "Hỏi từ vựng, dịch, ngữ pháp…", color = x.muted) },'''),
    ('''                    keyboardActions = KeyboardActions(onSend = {
                        if (input.isNotBlank()) { vm.send(input); input = "" }
                    }),
                )
                AnimatedVisibility(visible = input.isNotBlank()) {''', '''                    keyboardActions = KeyboardActions(onSend = {
                        if (input.isNotBlank() || pendingImage != null) { vm.send(input); input = "" }
                    }),
                )
                AnimatedVisibility(visible = input.isNotBlank() || pendingImage != null) {'''),
])

# ---------- Tin nhắn: hiện ảnh trong bong bóng ----------
patch('ui/components/Components.kt', [
    ('''    if (m.isUser) {
        Row(Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                    .background(x.bubbleUser)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(m.text, color = Color.White, style = MaterialTheme.typography.bodyLarge)''', '''    if (m.isUser) {
        if (m.hasImage) {
            Row(Modifier.fillMaxWidth().padding(start = 48.dp, bottom = if (m.text.isBlank()) 0.dp else 4.dp), horizontalArrangement = Arrangement.End) {
                ChatImageThumb(m.imagePath)
            }
        }
        if (m.text.isNotBlank()) Row(Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                    .background(x.bubbleUser)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(m.text, color = Color.White, style = MaterialTheme.typography.bodyLarge)'''),
])

# ---------- Cửa sổ bong bóng: cũng hỏi bằng ảnh ----------
A = 'ui/AssistActivity.kt'
patch(A, [
    ('''                    lastUser?.let {
                        Text("“${it.text}”", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = x.muted)
                        Spacer(Modifier.height(10.dp))
                    }''', '''                    lastUser?.let {
                        if (it.hasImage) {
                            com.hankukko.huyfan.ui.components.ChatImageThumb(it.imagePath, maxWidth = 160.dp)
                            Spacer(Modifier.height(6.dp))
                        }
                        if (it.text.isNotBlank()) Text("“${it.text}”", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = x.muted)
                        Spacer(Modifier.height(10.dp))
                    }'''),
    ('''            if (typing) {
                OutlinedTextField(''', '''            val pending by engine.pendingImage.collectAsStateWithLifecycle()
            pending?.let { img ->
                com.hankukko.huyfan.ui.components.AttachmentBar(
                    img, onRemove = { engine.discardImage() },
                    onQuickAsk = { q -> engine.submit(q, handsFree = false) },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (typing) {
                OutlinedTextField('''),
    ('''                    keyboardActions = KeyboardActions(onSend = {
                        if (input.isNotBlank()) { engine.submit(input, handsFree = false); input = "" }
                    }),
                    trailingIcon = {
                        IconButton(onClick = { if (input.isNotBlank()) { engine.submit(input, handsFree = false); input = "" } }) {''', '''                    keyboardActions = KeyboardActions(onSend = {
                        if (input.isNotBlank() || pending != null) { engine.submit(input, handsFree = false); input = "" }
                    }),
                    trailingIcon = {
                        IconButton(onClick = { if (input.isNotBlank() || pending != null) { engine.submit(input, handsFree = false); input = "" } }) {'''),
    ('''                LangToggle(ko = state.listenLang == LANG_KO, onClick = { engine.toggleLanguage() })
                MicButton(''', '''                Row(verticalAlignment = Alignment.CenterVertically) {
                    LangToggle(ko = state.listenLang == LANG_KO, onClick = { engine.toggleLanguage() })
                    Spacer(Modifier.width(8.dp))
                    com.hankukko.huyfan.ui.components.AttachImageButton(onPicked = { engine.cancel(); engine.attachImage(it); typing = true })
                }
                MicButton('''),
])

# ---------- Màn hình khoá: thông báo hiện đầy đủ + mục cài phím tắt ----------
patch('service/Notifications.kt', [
    ('''            .setContentTitle("HUY FAN đang chờ")''', '''            .setContentTitle("HUY FAN đang chờ")
            // Hiện cả trên màn hình khoá (nút "Hỏi ngay" mở cửa sổ hỏi không cần mở khoá)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)'''),
])
patch('ui/screens/SettingsScreen.kt', [
    ('''                ActionRow(
                    "Đặt làm trợ lý kỹ thuật số",''', '''                val lockSet = remember(tick) {
                    runCatching {
                        Settings.System.getString(context.contentResolver, "lock_application_shortcut")
                            ?.contains("${context.packageName}/") == true
                    }.getOrDefault(false)
                }
                ActionRow(
                    "Biểu tượng trên màn hình khoá",
                    if (lockSet) "✓ Đã đặt – vuốt biểu tượng mic tím ở góc màn hình khoá để hỏi, không cần mở khoá"
                    else "Màn hình khoá → Phím tắt → chọn “Hỏi HUY FAN” (góc trái hoặc phải). Vuốt là hỏi, không cần mở khoá",
                    Icons.Rounded.Smartphone,
                ) {
                    if (!isQuickAskEnabled(context)) setQuickAskEnabled(context, true)
                    context.tryStart(Intent("com.android.settings.LOCKSCREEN_SETTINGS"), Intent(Settings.ACTION_SECURITY_SETTINGS))
                }
                ActionRow(
                    "Đặt làm trợ lý kỹ thuật số",'''),
])
print('ok')
