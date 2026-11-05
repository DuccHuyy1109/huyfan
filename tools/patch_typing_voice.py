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


patch(J + 'data/Daos.kt', [
    ('''    @Query("DELETE FROM messages WHERE sessionId = :id")
    suspend fun deleteMessagesOf(id: Long)''',
     '''    @Query("DELETE FROM messages WHERE sessionId = :id")
    suspend fun deleteMessagesOf(id: Long)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)'''),
])

patch(J + 'data/Repositories.kt', [
    ('''    suspend fun addAssistant(sessionId: Long, reply: AssistantReply, savedCount: Int): Long {''',
     '''    suspend fun deleteMessage(id: Long) = dao.deleteMessage(id)

    suspend fun addAssistant(sessionId: Long, reply: AssistantReply, savedCount: Int): Long {'''),
])

E = J + 'assistant/ConversationEngine.kt'
patch(E, [
    # câu nghe từ mic → đánh dấu để còn huỷ được khi người dùng chuyển sang gõ
    ('''                if (text.isBlank()) onSilence() else submit(text, handsFree = _state.value.handsFree)''',
     '''                if (text.isBlank()) onSilence() else submit(text, handsFree = _state.value.handsFree, fromVoice = true)'''),
    ('''    fun submit(text: String, handsFree: Boolean = false) {
        val image = _pendingImage.value''',
     '''    fun submit(text: String, handsFree: Boolean = false, fromVoice: Boolean = false) {
        val image = _pendingImage.value'''),
    ('''        askJob?.cancel()
        _state.update { it.copy(phase = Phase.THINKING, partial = q, level = 0f, handsFree = handsFree, notice = null) }
        askJob = scope.launch {
            val settings = c.settings.get()
            val sid = ensureSession(q.ifBlank { "Hỏi bằng ảnh" })
            c.chat.addUser(sid, q, image.orEmpty())
''',
     '''        askJob?.cancel()
        voiceMsgId = null
        voiceAsking = fromVoice
        _state.update { it.copy(phase = Phase.THINKING, partial = q, level = 0f, handsFree = handsFree, notice = null) }
        askJob = scope.launch {
            val settings = c.settings.get()
            val sid = ensureSession(q.ifBlank { "Hỏi bằng ảnh" })
            val msgId = c.chat.addUser(sid, q, image.orEmpty())
            if (fromVoice) voiceMsgId = msgId
'''),
    ('''            c.chat.addAssistant(sid, reply, saved)
            lastActivity = System.currentTimeMillis()''',
     '''            c.chat.addAssistant(sid, reply, saved)
            voiceAsking = false
            voiceMsgId = null
            lastActivity = System.currentTimeMillis()'''),
    ('''    fun switchToTyping() {
        when (_state.value.phase) {
            Phase.LISTENING, Phase.GREETING, Phase.SPEAKING -> cancel()
            else -> _state.update { it.copy(handsFree = false) }
        }
    }''',
     '''    fun switchToTyping() {
        when (_state.value.phase) {
            Phase.LISTENING, Phase.GREETING, Phase.SPEAKING -> cancel()
            // Câu vừa nghe từ mic đang chờ AI → huỷ luôn và xoá câu đó khỏi khung chat
            Phase.THINKING -> if (voiceAsking) {
                val id = voiceMsgId
                cancel()
                voiceAsking = false
                voiceMsgId = null
                if (id != null) scope.launch { c.chat.deleteMessage(id) }
            } else _state.update { it.copy(handsFree = false) }
            else -> _state.update { it.copy(handsFree = false) }
        }
    }

    /** Câu hỏi đang chờ AI có phải nghe từ mic không (+ id tin nhắn để xoá khi huỷ). */
    @Volatile private var voiceAsking = false
    @Volatile private var voiceMsgId: Long? = null'''),
])
print('ok')
