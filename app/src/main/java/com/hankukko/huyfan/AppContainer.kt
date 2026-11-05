package com.hankukko.huyfan

import android.app.Application
import com.hankukko.huyfan.ai.AiService
import com.hankukko.huyfan.assistant.ConversationEngine
import com.hankukko.huyfan.data.AppDatabase
import com.hankukko.huyfan.data.AppSettings
import com.hankukko.huyfan.data.ChatRepository
import com.hankukko.huyfan.data.GrammarRepository
import com.hankukko.huyfan.data.LessonRepository
import com.hankukko.huyfan.data.SettingsRepository
import com.hankukko.huyfan.data.SheetsSync
import com.hankukko.huyfan.data.VocabRepository
import com.hankukko.huyfan.util.SmartDns
import com.hankukko.huyfan.voice.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** "DI" thủ công: mọi thành phần dùng chung trong app. */
class AppContainer(val app: Application) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val dns = SmartDns()

    val http: OkHttpClient = OkHttpClient.Builder()
        .dns(dns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .eventListenerFactory { NetLog() }
        .build()

    val db: AppDatabase = AppDatabase.build(app)
    val settings = SettingsRepository(app)

    /** Bản sao "nóng" của cài đặt để đọc đồng bộ (TTS, service...). */
    val settingsState: StateFlow<AppSettings> =
        settings.flow.stateIn(appScope, SharingStarted.Eagerly, AppSettings())

    val vocab = VocabRepository(db.vocabDao(), db.lessonDao())
    val grammar = GrammarRepository(db.grammarDao(), db.lessonDao())
    val lessons = LessonRepository(db.lessonDao(), db.vocabDao(), db.grammarDao())
    val chat = ChatRepository(db.chatDao())
    val ai = AiService(http, settings)
    val sheets = SheetsSync(http, settings, db.vocabDao(), db.grammarDao())
    val speaker = Speaker(app, settingsState, http, appScope)
    val ink = com.hankukko.huyfan.ink.InkRecognizer()
    val engine = ConversationEngine(app, this)

    fun start() {
        dns.warmUp("generativelanguage.googleapis.com", "api.groq.com")
        speaker.init()
        appScope.launch {
            // Đổi engine đọc trong Cài đặt → khởi tạo lại TTS
            settingsState.map { listOf(it.ttsEngine, it.viTtsEngine, it.koTtsEngine, it.viVoice, it.koVoice) }
                .distinctUntilChanged().collect { speaker.init() }
        }
        appScope.launch {
            runCatching { com.hankukko.huyfan.data.ChatImages.cleanOrphans(app, chat.allImagePaths().toSet(), engine.pendingImage.value) }
        }
        appScope.launch {
            vocab.cleanHanja()
            sheets.syncPending()
        }
        appScope.launch {
            delay(8000) // để app mở xong, không tranh lượt AI với câu hỏi đầu tiên
            runCatching { com.hankukko.huyfan.ai.LoanBackfill.run(this@AppContainer) }
                .onFailure { android.util.Log.w("HuyFanAI", "bổ sung từ mượn lỗi – để lần sau: ${it.message}") }
        }
        appScope.launch {
            settingsState.map { it.reminderEnabled to it.reminderMinutes }.distinctUntilChanged().collect {
                com.hankukko.huyfan.service.Reminder.schedule(app, settingsState.value)
            }
        }
        appScope.launch {
            // Đổi sang link Sheet khác (Sheet mới trống) → đánh dấu chưa đồng bộ để gửi lại toàn bộ sổ từ
            settingsState.map { it.sheetsUrl.trim() }.distinctUntilChanged().drop(1).collectLatest { url ->
                delay(2000) // đang gõ/dán link → chờ ổn định
                if (url.isNotBlank()) {
                    db.vocabDao().markAllUnsynced()
                    db.grammarDao().markAllUnsynced()
                    sheets.syncPending()
                }
            }
        }
        engine.restoreRecentSession()
    }
}
