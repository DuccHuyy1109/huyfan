package com.hankukko.huyfan.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.hankukko.huyfan.AppContainer
import com.hankukko.huyfan.ai.Actions
import com.hankukko.huyfan.ai.AiException
import com.hankukko.huyfan.ai.AssistantReply
import com.hankukko.huyfan.ai.PromptBuilder
import com.hankukko.huyfan.data.ChatMessage
import com.hankukko.huyfan.data.LessonItem
import com.hankukko.huyfan.util.Dates
import com.hankukko.huyfan.service.HuyFanWidget
import com.hankukko.huyfan.voice.SpeechInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Phase { IDLE, GREETING, LISTENING, THINKING, SPEAKING }

enum class Source { WAKE, SHORTCUT, MANUAL }

data class EngineState(
    val phase: Phase = Phase.IDLE,
    val partial: String = "",
    /** 0..1 – độ lớn giọng nói để vẽ hiệu ứng */
    val level: Float = 0f,
    val listenLang: String = LANG_VI,
    /** Đang ở chế độ rảnh tay: đọc xong tự nghe tiếp */
    val handsFree: Boolean = false,
    val notice: String? = null,
) {
    val busy: Boolean get() = phase != Phase.IDLE
}

const val LANG_VI = "vi-VN"
/** Mã nhận giọng của ngôn ngữ đang học (ko-KR hoặc en-US). Tên giữ nguyên cho code cũ. */
val LANG_KO: String get() = com.hankukko.huyfan.Lang.current.sttLocale

sealed interface EngineEvent {
    data class OpenVocab(val todayOnly: Boolean) : EngineEvent
    data object OpenReview : EngineEvent
    data object OpenHistory : EngineEvent
    data object Ended : EngineEvent
    /** Vừa được gọi (HUY FAN / phím tắt) → màn hình nên chuyển về tab Chat */
    data object Woke : EngineEvent
}

/**
 * Bộ não hội thoại dùng chung cho màn hình chat, bảng trợ lý bật lên và dịch vụ chạy nền.
 * Luồng: (chào) → nghe → hỏi AI → lưu từ → hiển thị → đọc → (nghe tiếp nếu rảnh tay).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationEngine(private val app: Context, private val c: AppContainer) {

    private val scope = c.appScope
    private val speech = SpeechInput(app)

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state

    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<EngineEvent> = _events

    private val _sessionId = MutableStateFlow<Long?>(null)
    val sessionId: StateFlow<Long?> = _sessionId

    val messages: Flow<List<ChatMessage>> = _sessionId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else c.chat.messages(id)
    }

    /** Dịch vụ chờ đăng ký để nhả micro ngay trước khi nhận diện giọng nói chạy. */
    var onMicRequest: (() -> Unit)? = null

    private var askJob: Job? = null
    private var lastActivity = 0L
    private var busyRetry = 0

    fun restoreRecentSession() {
        scope.launch {
            val s = c.chat.latestSession() ?: return@launch
            if (System.currentTimeMillis() - s.updatedAt < SESSION_GAP_MS) {
                _sessionId.value = s.id
                lastActivity = s.updatedAt
            }
        }
    }

    // ---------------- Điều khiển ----------------

    /** Được gọi khi nghe "HUY FAN", bấm phím tắt, tile, widget... */
    fun wake(source: Source) {
        cancelInternal()
        _events.tryEmit(EngineEvent.Woke)
        val s = c.settingsState.value
        _state.update { it.copy(handsFree = true, notice = null, partial = "", listenLang = LANG_VI) }
        val greet = source == Source.WAKE || (source == Source.SHORTCUT && s.greetOnShortcut)
        if (greet) {
            _state.update { it.copy(phase = Phase.GREETING) }
            onMicRequest?.invoke()
            c.speaker.speak(s.wakeReplyList.random()) {
                if (_state.value.phase == Phase.GREETING) startListening(handsFree = true)
            }
        } else {
            startListening(handsFree = true)
        }
    }

    fun startListening(handsFree: Boolean = true, lang: String? = null) {
        askJob?.cancel()
        c.speaker.stop()
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _state.update { it.copy(phase = Phase.IDLE, notice = "Cần cấp quyền micro để HUY FAN nghe được.") }
            return
        }
        onMicRequest?.invoke()
        val language = lang ?: _state.value.listenLang
        _state.update { it.copy(phase = Phase.LISTENING, partial = "", level = 0f, listenLang = language, handsFree = handsFree, notice = null) }
        val st = c.settingsState.value
        speech.start(language, st.preferGoogleStt, endSilenceMs = st.endSilenceMs.toLong(), listener = object : SpeechInput.Listener {
            override fun onReady() {
                busyRetry = 0
            }

            override fun onRms(db: Float) {
                val lv = ((db + 2f) / 12f).coerceIn(0f, 1f)
                _state.update { it.copy(level = lv) }
            }

            override fun onPartial(text: String) {
                _state.update { it.copy(partial = text) }
            }

            override fun onResult(text: String) {
                if (text.isBlank()) onSilence() else submit(text, handsFree = _state.value.handsFree, fromVoice = true)
            }

            override fun onError(code: Int) {
                when {
                    SpeechInput.isSilence(code) -> onSilence()
                    (code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || code == SpeechRecognizer.ERROR_CLIENT) && busyRetry < 2 -> {
                        busyRetry++
                        scope.launch {
                            delay(350)
                            if (_state.value.phase == Phase.LISTENING) startListening(_state.value.handsFree, language)
                        }
                    }
                    else -> {
                        busyRetry = 0
                        _state.update { it.copy(phase = Phase.IDLE, level = 0f, handsFree = false, notice = SpeechInput.describe(code)) }
                    }
                }
            }
        })
    }

    private fun onSilence() {
        _state.update { it.copy(phase = Phase.IDLE, partial = "", level = 0f, handsFree = false) }
    }

    /**
     * Nghe 1 câu rồi trả chữ về [onText] (null = không nghe được), KHÔNG gửi cho AI.
     * Dùng cho mic trên thẻ ôn tập. Trạng thái LISTENING giúp chế độ chờ tự nhả micro.
     */
    fun captureOnce(lang: String, onText: (String?) -> Unit) {
        askJob?.cancel()
        c.speaker.stop()
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onText(null); return
        }
        onMicRequest?.invoke()
        _state.update { it.copy(phase = Phase.LISTENING, partial = "", level = 0f, listenLang = lang, handsFree = false, notice = null) }
        val st = c.settingsState.value
        speech.start(lang, st.preferGoogleStt, endSilenceMs = st.endSilenceMs.toLong(), listener = object : SpeechInput.Listener {
            override fun onRms(db: Float) {
                _state.update { it.copy(level = ((db + 2f) / 12f).coerceIn(0f, 1f)) }
            }

            override fun onPartial(text: String) {
                _state.update { it.copy(partial = text) }
            }

            override fun onResult(text: String) {
                _state.update { it.copy(phase = Phase.IDLE, level = 0f, partial = "", listenLang = LANG_VI) }
                onText(text.ifBlank { null })
            }

            override fun onError(code: Int) {
                _state.update { it.copy(phase = Phase.IDLE, level = 0f, partial = "", listenLang = LANG_VI) }
                onText(null)
            }
        })
    }

    /** Người dùng bấm mic lần nữa khi đang nghe → chốt câu ngay. */
    fun stopListening() = speech.stop()

    fun toggleLanguage() {
        val next = if (_state.value.listenLang == LANG_VI) LANG_KO else LANG_VI
        _state.update { it.copy(listenLang = next) }
        if (_state.value.phase == Phase.LISTENING) startListening(_state.value.handsFree, next)
    }

    /** Dừng mọi thứ (đóng bảng trợ lý, bấm dừng). */
    fun cancel() {
        cancelInternal()
        _state.update { it.copy(handsFree = false) }
    }

    private fun cancelInternal() {
        askJob?.cancel()
        speech.cancel()
        c.speaker.stop()
        _state.update { it.copy(phase = Phase.IDLE, partial = "", level = 0f) }
    }

    /**
     * Người dùng chuyển sang gõ phím: dừng mic (bỏ luôn phần vừa nghe, không gửi) và dừng đọc,
     * thôi chế độ rảnh tay. Đang chờ AI trả lời thì để trả lời xong nhưng không tự nghe tiếp.
     */
    fun switchToTyping() {
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
    @Volatile private var voiceMsgId: Long? = null

    /** Thôi chế độ rảnh tay: đọc nốt câu đang đọc nhưng không nghe tiếp. */
    fun endHandsFree() {
        if (_state.value.phase == Phase.LISTENING || _state.value.phase == Phase.GREETING) {
            cancel()
        } else {
            _state.update { it.copy(handsFree = false) }
        }
    }

    fun stopSpeaking() {
        c.speaker.stop()
        _state.update { it.copy(phase = Phase.IDLE, handsFree = false) }
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    fun newSession() {
        cancel()
        _sessionId.value = null
    }

    fun openSession(id: Long) {
        cancel()
        _sessionId.value = id
        lastActivity = System.currentTimeMillis()
    }

    fun onSessionDeleted(id: Long) {
        if (_sessionId.value == id) _sessionId.value = null
    }

    // ---------------- Hỏi đáp ----------------

    /** Đang phiên dịch → chế độ chờ không nghe "HUY FAN" chen vào micro. */
    private val _micHold = kotlinx.coroutines.flow.MutableStateFlow(false)
    val micHold: kotlinx.coroutines.flow.StateFlow<Boolean> = _micHold

    fun setMicHold(on: Boolean) {
        _micHold.value = on
        if (on) onMicRequest?.invoke()
    }

    /** Ảnh đã đính kèm, chờ câu hỏi (gõ hoặc nói) để gửi cùng. */
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

    fun submit(text: String, handsFree: Boolean = false, fromVoice: Boolean = false) {
        val image = _pendingImage.value
        val q = text.trim()
        if (q.isEmpty() && image == null) return
        _pendingImage.value = null
        speech.cancel()
        c.speaker.stop()
        askJob?.cancel()
        voiceMsgId = null
        voiceAsking = fromVoice
        _state.update { it.copy(phase = Phase.THINKING, partial = q, level = 0f, handsFree = handsFree, notice = null) }
        askJob = scope.launch {
            val settings = c.settings.get()
            val sid = ensureSession(q.ifBlank { "Hỏi bằng ảnh" })
            val msgId = c.chat.addUser(sid, q, image.orEmpty())
            if (fromVoice) voiceMsgId = msgId

            val t0 = System.currentTimeMillis()
            val reply: AssistantReply = (if (image == null) LocalCommands.match(q, settings.style) else null)
                // Hỏi lại từ đã có trong sổ → trả lời ngay, không chờ AI
                ?: (if (image == null && settings.instantFromNotebook) NotebookAnswer.match(q, c.vocab.allNow(), settings.style)
                    ?.copy(latencyMs = (System.currentTimeMillis() - t0).coerceAtLeast(1)) else null)
                ?: try {
                val history = c.chat.recentTurns(sid, settings.contextTurns)
                val system = PromptBuilder.system(settings, c.vocab.today(), c.vocab.recent(7), c.grammar.today()) +
                    if (history.any { it.image != null }) PromptBuilder.IMAGE_RULE else ""
                c.ai.ask(system, history)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                AssistantReply(reply = e.userMessage, intent = "error", isError = true)
            } catch (e: Exception) {
                AssistantReply(reply = "Toang rồi ông ơi: ${e.message}", intent = "error", isError = true)
            }

            var saved = 0
            if (reply.save && settings.autoSaveVocab && (reply.vocab.isNotEmpty() || reply.grammar.isNotEmpty())) {
                saved = c.vocab.saveFromAi(reply.vocab).size + c.grammar.saveFromAi(reply.grammar).size
                if (saved > 0) {
                    scope.launch { c.sheets.syncPending() }
                    HuyFanWidget.refresh(app)
                }
            }
            c.chat.addAssistant(sid, reply, saved)
            voiceAsking = false
            voiceMsgId = null
            lastActivity = System.currentTimeMillis()

            when (reply.action) {
                Actions.SHOW_VOCAB_TODAY -> _events.tryEmit(EngineEvent.OpenVocab(todayOnly = true))
                Actions.SHOW_VOCAB_ALL -> _events.tryEmit(EngineEvent.OpenVocab(todayOnly = false))
                Actions.START_REVIEW -> _events.tryEmit(EngineEvent.OpenReview)
                Actions.OPEN_HISTORY -> _events.tryEmit(EngineEvent.OpenHistory)
                Actions.DETAIL_ON -> c.settings.update { it.copy(detailedAnswers = true) }
                Actions.DETAIL_OFF -> c.settings.update { it.copy(detailedAnswers = false) }
            }
            speakReply(reply)
        }
    }

    private fun speakReply(reply: AssistantReply) {
        val s = c.settingsState.value
        val end = reply.action == Actions.END
        val after: () -> Unit = {
            if (!end && _state.value.handsFree && s.continuous) {
                startListening(handsFree = true)
            } else {
                _state.update { it.copy(phase = Phase.IDLE, level = 0f, handsFree = if (end) false else it.handsFree && s.continuous) }
                if (end) _events.tryEmit(EngineEvent.Ended)
            }
        }
        if (s.speakReplies || end) {
            _state.update { it.copy(phase = Phase.SPEAKING) }
            c.speaker.speak(reply.reply) {
                if (_state.value.phase == Phase.SPEAKING) after()
            }
        } else {
            after()
        }
    }

    private suspend fun ensureSession(firstText: String): Long {
        val current = _sessionId.value
        val stale = System.currentTimeMillis() - lastActivity > SESSION_GAP_MS
        if (current != null && !stale) return current
        val id = c.chat.newSession(firstText)
        _sessionId.value = id
        lastActivity = System.currentTimeMillis()
        return id
    }

    /**
     * Người dùng bấm thùng rác trên bảng từ/ngữ pháp: xoá hẳn khỏi sổ, Google Sheets, bài ôn
     * và khỏi ngữ cảnh AI (để AI không "nhớ" từ đó nữa).
     */
    fun removeLearned(m: ChatMessage, kind: String, index: Int) {
        scope.launch {
            val day = Dates.dayKeyOf(m.createdAt)
            if (kind == LessonItem.VOCAB) {
                m.vocab.getOrNull(index)?.let { item ->
                    (c.vocab.findSameOnDay(item.ko, day) ?: c.vocab.findLatest(item.ko))?.let { e -> c.sheets.deleteRemote(c.vocab.deleteMany(listOf(e.id))) }
                }
            } else {
                m.grammar.getOrNull(index)?.let { item ->
                    (c.grammar.findSameOnDay(item.pattern, day) ?: c.grammar.findLatest(item.pattern))?.let { e -> c.sheets.deleteRemote(c.grammar.deleteMany(listOf(e.id))) }
                }
            }
            c.chat.removeItem(m, kind, index)
            HuyFanWidget.refresh(app)
        }
    }

    /** Người dùng bấm "+" trên bảng: lưu từ/ngữ pháp đó vào sổ hôm nay. */
    fun addLearned(m: ChatMessage, kind: String, index: Int) {
        scope.launch {
            val n = if (kind == LessonItem.VOCAB) {
                m.vocab.getOrNull(index)?.let { c.vocab.saveFromAi(listOf(it)).size } ?: 0
            } else {
                m.grammar.getOrNull(index)?.let { c.grammar.saveFromAi(listOf(it)).size } ?: 0
            }
            if (n > 0) {
                scope.launch { c.sheets.syncPending() }
                HuyFanWidget.refresh(app)
            }
        }
    }

    /** Hỏi lại câu trước (khi lỗi mạng...) */
    fun retry(text: String) = submit(text, handsFree = false)

    companion object {
        /** Quá 30 phút không nói gì → câu hỏi mới mở cuộc trò chuyện mới */
        private const val SESSION_GAP_MS = 30 * 60 * 1000L
    }
}
