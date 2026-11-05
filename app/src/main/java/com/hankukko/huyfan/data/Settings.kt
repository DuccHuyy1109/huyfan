package com.hankukko.huyfan.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class Provider(val label: String) { GEMINI("Gemini"), OPENAI("OpenAI-compatible") }

enum class TtsMode(val label: String) { DEVICE("Giọng máy"), AI("Giọng AI") }

enum class ThemeMode(val label: String) { DARK("Tối"), LIGHT("Sáng"), SYSTEM("Theo máy") }

enum class TalkStyle(val label: String, val desc: String) {
    BUDDY("Bạn thân", "Xưng tui – gọi ông: “Sao vậy ông?”"),
    BRO("Bro", "Năng lượng Gen Z: “Yo bro, chuẩn luôn!”"),
    POLITE("Gia sư", "Xưng mình – gọi bạn, nhẹ nhàng"),
}

const val DEFAULT_GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta"
const val DEFAULT_OPENAI_BASE = "https://api.groq.com/openai/v1"
const val DEFAULT_OPENAI_MODEL = "llama-3.3-70b-versatile"
const val DEFAULT_WAKE_PHRASES = "huy phan"
const val DEFAULT_WAKE_REPLIES = "HUY FAN đây!|HUY FAN đây, sao vậy ông?|HUY FAN nghe nè bro!"

data class AppSettings(
    // --- AI ---
    val provider: Provider = Provider.GEMINI,
    val geminiKey: String = "",
    /** "" = tự chọn model Flash mới nhất */
    val geminiModel: String = "",
    val geminiResolvedModel: String = "",
    val geminiBaseUrl: String = DEFAULT_GEMINI_BASE,
    /** Tắt/giảm "suy nghĩ" của model để trả lời nhanh */
    val fastMode: Boolean = true,
    val preferLite: Boolean = true,
    val openaiBaseUrl: String = DEFAULT_OPENAI_BASE,
    val openaiKey: String = "",
    val openaiModel: String = DEFAULT_OPENAI_MODEL,
    val fallback: Boolean = true,
    val temperature: Float = 0.6f,
    val contextTurns: Int = 8,
    // --- Prompt & cá nhân hoá ---
    /** "" = dùng prompt mặc định */
    val customPrompt: String = "",
    val userName: String = "",
    val style: TalkStyle = TalkStyle.BUDDY,
    val autoSaveVocab: Boolean = true,
    // --- Giọng nói ---
    val wakePhrases: String = DEFAULT_WAKE_PHRASES,
    val wakeReplies: String = DEFAULT_WAKE_REPLIES,
    /** 0 = ít nhầm, 1 = cân bằng, 2 = nhạy */
    val wakeSensitivity: Int = 1,
    val ttsEngine: String = "",
    /** Giọng máy (offline) hay giọng AI Gemini (tự nhiên) */
    val ttsMode: TtsMode = TtsMode.DEVICE,
    val viTtsEngine: String = "",
    val koTtsEngine: String = "",
    val viVoice: String = "",
    val koVoice: String = "",
    val aiVoice: String = "Kore",
    val speechRate: Float = 1.0f,
    val speakReplies: Boolean = true,
    val continuous: Boolean = true,
    val greetOnShortcut: Boolean = false,
    val preferGoogleStt: Boolean = true,
    /** Im lặng bao lâu (ms) sau khi nói thì tự gửi câu hỏi */
    val endSilenceMs: Int = 1500,
    // --- Chế độ chờ & pin ---
    val autoOffMinutes: Int = 30,
    val pauseOnScreenOff: Boolean = false,
    val standbyOnLaunch: Boolean = false,
    // --- Google Sheets ---
    val sheetsUrl: String = "",
    val sheetsToken: String = "",
    val sheetsAutoSync: Boolean = true,
    // --- Cài đặt bài học (ôn tập) ---
    /** Nói giống đáp án bao nhiêu % thì lật thẻ */
    val studyMatchPercent: Int = 70,
    /** Chế độ ngược: nhìn nghĩa → nói từ/ngữ pháp tiếng Hàn */
    val studyReverse: Boolean = false,
    /** Tự đọc tiếng Hàn khi thẻ hiện mặt tiếng Hàn */
    val studyAutoSpeak: Boolean = true,
    /** Viết tay giống đáp án bao nhiêu % thì lật thẻ */
    val studyDrawPercent: Int = 70,
    // --- Khác ---
    val themeMode: ThemeMode = ThemeMode.DARK,
    val onboarded: Boolean = false,
    /** Nhắc học mỗi ngày */
    val reminderEnabled: Boolean = true,
    /** Giờ nhắc, tính bằng phút trong ngày (20:30 = 1230) */
    val reminderMinutes: Int = 20 * 60 + 30,
    /** Hỏi lại từ đã có trong sổ → trả lời ngay từ sổ, không gọi AI */
    val instantFromNotebook: Boolean = true,
    /** Bong bóng chat nổi khi chế độ chờ bật */
    val bubbleEnabled: Boolean = true,
    /** 0 nhỏ · 1 vừa · 2 lớn */
    val bubbleSize: Int = 1,
    /** Chạm bong bóng: true = nghe ngay · false = mở bàn phím (nơi ồn) */
    val bubbleTapListen: Boolean = true,
    /** Nghe từ khoá "HUY FAN" khi chế độ chờ bật (tắt = chỉ dùng bong bóng, đỡ tốn pin) */
    val wakeListenEnabled: Boolean = true,
    /** Trả lời chi tiết, kỹ càng (bảng từ vựng/ngữ pháp vẫn như thường) */
    val detailedAnswers: Boolean = false,
) {
    val hasAiKey: Boolean
        get() = when (provider) {
            Provider.GEMINI -> geminiKey.isNotBlank()
            Provider.OPENAI -> openaiKey.isNotBlank() || openaiBaseUrl.contains("localhost") || openaiBaseUrl.contains("192.168.")
        }

    val wakePhraseList: List<String>
        get() = wakePhrases.split(',', '|', '\n').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            .ifEmpty { listOf(DEFAULT_WAKE_PHRASES) }

    /** Độ tin cậy tối thiểu từng từ của câu gọi theo mức độ nhạy. */
    val wakeMinConf: Double
        get() = when (wakeSensitivity) {
            0 -> 0.93
            2 -> 0.65
            else -> 0.8
        }

    /** Số nhịp (~0,2 s) câu gọi phải giữ ổn định ở cuối đoạn đang nghe. */
    val wakeStableHits: Int
        get() = when (wakeSensitivity) {
            0 -> 3
            2 -> 1
            else -> 2
        }

    /** Cài đặt học mặc định (dùng cho ôn nhanh). */
    val studyConfig: com.hankukko.huyfan.data.StudyConfig
        get() = com.hankukko.huyfan.data.StudyConfig(studyReverse, studyMatchPercent, studyDrawPercent, studyAutoSpeak)

    val wakeReplyList: List<String>
        get() = wakeReplies.split('|', '\n').map { it.trim() }.filter { it.isNotEmpty() }
            .ifEmpty { listOf("HUY FAN đây!") }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

private object K {
    val provider = stringPreferencesKey("provider")
    val geminiKey = stringPreferencesKey("gemini_key")
    val geminiModel = stringPreferencesKey("gemini_model")
    val geminiResolved = stringPreferencesKey("gemini_resolved")
    val geminiBase = stringPreferencesKey("gemini_base")
    val fastMode = booleanPreferencesKey("fast_mode")
    val preferLite = booleanPreferencesKey("prefer_lite")
    val openaiBase = stringPreferencesKey("openai_base")
    val openaiKey = stringPreferencesKey("openai_key")
    val openaiModel = stringPreferencesKey("openai_model")
    val fallback = booleanPreferencesKey("fallback")
    val temperature = floatPreferencesKey("temperature")
    val contextTurns = intPreferencesKey("context_turns")
    // Riêng theo ngôn ngữ học (tiếng Anh thêm hậu tố _en)
    val customPrompt get() = stringPreferencesKey(com.hankukko.huyfan.Lang.key("custom_prompt"))
    val userName = stringPreferencesKey("user_name")
    val style = stringPreferencesKey("style")
    val autoSaveVocab = booleanPreferencesKey("auto_save_vocab")
    val wakePhrases = stringPreferencesKey("wake_phrases")
    val wakeReplies = stringPreferencesKey("wake_replies")
    val wakeSensitivity = intPreferencesKey("wake_sensitivity")
    val ttsEngine = stringPreferencesKey("tts_engine")
    val ttsMode = stringPreferencesKey("tts_mode")
    val viTtsEngine = stringPreferencesKey("vi_tts_engine")
    val koTtsEngine get() = stringPreferencesKey(com.hankukko.huyfan.Lang.key("ko_tts_engine"))
    val viVoice = stringPreferencesKey("vi_voice")
    val koVoice get() = stringPreferencesKey(com.hankukko.huyfan.Lang.key("ko_voice"))
    val aiVoice = stringPreferencesKey("ai_voice")
    val speechRate = floatPreferencesKey("speech_rate")
    val speakReplies = booleanPreferencesKey("speak_replies")
    val continuous = booleanPreferencesKey("continuous")
    val greetOnShortcut = booleanPreferencesKey("greet_on_shortcut")
    val preferGoogleStt = booleanPreferencesKey("prefer_google_stt")
    val endSilence = intPreferencesKey("end_silence_ms")
    val autoOff = intPreferencesKey("auto_off_minutes")
    val pauseOnScreenOff = booleanPreferencesKey("pause_screen_off")
    val standbyOnLaunch = booleanPreferencesKey("standby_on_launch")
    val sheetsUrl get() = stringPreferencesKey(com.hankukko.huyfan.Lang.key("sheets_url"))
    val sheetsToken get() = stringPreferencesKey(com.hankukko.huyfan.Lang.key("sheets_token"))
    val sheetsAuto = booleanPreferencesKey("sheets_auto")
    val studyMatch = intPreferencesKey("study_match")
    val studyReverse = booleanPreferencesKey("study_reverse")
    val studyAutoSpeak = booleanPreferencesKey("study_auto_speak")
    val studyDraw = intPreferencesKey("study_draw")
    val theme = stringPreferencesKey("theme")
    val onboarded = booleanPreferencesKey("onboarded")
    val reminderOn = booleanPreferencesKey("reminder_on")
    val reminderMin = intPreferencesKey("reminder_min")
    val instantNotebook = booleanPreferencesKey("instant_notebook")
    val bubbleOn = booleanPreferencesKey("bubble_on")
    val bubbleSize = intPreferencesKey("bubble_size")
    val bubbleListen = booleanPreferencesKey("bubble_listen")
    val wakeListen = booleanPreferencesKey("wake_listen")
    val detailed = booleanPreferencesKey("detailed_answers")
}

private inline fun <reified E : Enum<E>> enumOr(value: String?, default: E): E =
    value?.let { v -> runCatching { enumValueOf<E>(v) }.getOrNull() } ?: default

private fun Preferences.toSettings(): AppSettings {
    val d = AppSettings()
    return AppSettings(
        provider = enumOr(this[K.provider], d.provider),
        geminiKey = this[K.geminiKey] ?: d.geminiKey,
        // Lỡ chọn model không chat được (vd …-tts) → về tự động
        geminiModel = (this[K.geminiModel] ?: d.geminiModel).let {
            if (it.isNotBlank() && !com.hankukko.huyfan.ai.GeminiClient.isChatModel(it)) "" else it
        },
        geminiResolvedModel = (this[K.geminiResolved] ?: d.geminiResolvedModel).let {
            if (it.isNotBlank() && !com.hankukko.huyfan.ai.GeminiClient.isChatModel(it)) "" else it
        },
        geminiBaseUrl = this[K.geminiBase] ?: d.geminiBaseUrl,
        fastMode = this[K.fastMode] ?: d.fastMode,
        preferLite = this[K.preferLite] ?: d.preferLite,
        openaiBaseUrl = this[K.openaiBase] ?: d.openaiBaseUrl,
        openaiKey = this[K.openaiKey] ?: d.openaiKey,
        openaiModel = this[K.openaiModel] ?: d.openaiModel,
        fallback = this[K.fallback] ?: d.fallback,
        temperature = this[K.temperature] ?: d.temperature,
        contextTurns = this[K.contextTurns] ?: d.contextTurns,
        customPrompt = this[K.customPrompt] ?: d.customPrompt,
        userName = this[K.userName] ?: d.userName,
        style = enumOr(this[K.style], d.style),
        autoSaveVocab = this[K.autoSaveVocab] ?: d.autoSaveVocab,
        wakePhrases = this[K.wakePhrases] ?: d.wakePhrases,
        wakeReplies = this[K.wakeReplies] ?: d.wakeReplies,
        wakeSensitivity = this[K.wakeSensitivity] ?: d.wakeSensitivity,
        ttsEngine = this[K.ttsEngine] ?: d.ttsEngine,
        ttsMode = enumOr(this[K.ttsMode], d.ttsMode),
        viTtsEngine = this[K.viTtsEngine] ?: d.viTtsEngine,
        koTtsEngine = this[K.koTtsEngine] ?: d.koTtsEngine,
        viVoice = this[K.viVoice] ?: d.viVoice,
        koVoice = this[K.koVoice] ?: d.koVoice,
        aiVoice = this[K.aiVoice] ?: d.aiVoice,
        speechRate = this[K.speechRate] ?: d.speechRate,
        speakReplies = this[K.speakReplies] ?: d.speakReplies,
        continuous = this[K.continuous] ?: d.continuous,
        greetOnShortcut = this[K.greetOnShortcut] ?: d.greetOnShortcut,
        preferGoogleStt = this[K.preferGoogleStt] ?: d.preferGoogleStt,
        endSilenceMs = this[K.endSilence] ?: d.endSilenceMs,
        autoOffMinutes = this[K.autoOff] ?: d.autoOffMinutes,
        pauseOnScreenOff = this[K.pauseOnScreenOff] ?: d.pauseOnScreenOff,
        standbyOnLaunch = this[K.standbyOnLaunch] ?: d.standbyOnLaunch,
        sheetsUrl = this[K.sheetsUrl] ?: d.sheetsUrl,
        sheetsToken = this[K.sheetsToken] ?: d.sheetsToken,
        sheetsAutoSync = this[K.sheetsAuto] ?: d.sheetsAutoSync,
        studyMatchPercent = this[K.studyMatch] ?: d.studyMatchPercent,
        studyReverse = this[K.studyReverse] ?: d.studyReverse,
        studyAutoSpeak = this[K.studyAutoSpeak] ?: d.studyAutoSpeak,
        studyDrawPercent = this[K.studyDraw] ?: d.studyDrawPercent,
        themeMode = enumOr(this[K.theme], d.themeMode),
        onboarded = this[K.onboarded] ?: d.onboarded,
        reminderEnabled = this[K.reminderOn] ?: d.reminderEnabled,
        reminderMinutes = this[K.reminderMin] ?: d.reminderMinutes,
        instantFromNotebook = this[K.instantNotebook] ?: d.instantFromNotebook,
        bubbleEnabled = this[K.bubbleOn] ?: d.bubbleEnabled,
        bubbleSize = this[K.bubbleSize] ?: d.bubbleSize,
        bubbleTapListen = this[K.bubbleListen] ?: d.bubbleTapListen,
        wakeListenEnabled = this[K.wakeListen] ?: d.wakeListenEnabled,
        detailedAnswers = this[K.detailed] ?: d.detailedAnswers,
    ).let { s ->
        // Ô "Groq key dự phòng" nhưng link còn của preset khác (vd Gemini OpenAI) → Groq bị từ chối; tự về link Groq
        if (s.provider == Provider.GEMINI && s.openaiKey.trim().startsWith("gsk_") && !s.openaiBaseUrl.contains("groq.com")) {
            s.copy(
                openaiBaseUrl = DEFAULT_OPENAI_BASE,
                openaiModel = if (s.openaiModel.isBlank() || s.openaiModel.startsWith("gemini")) "llama-3.3-70b-versatile" else s.openaiModel,
            )
        } else s
    }
}

private fun MutablePreferences.write(s: AppSettings) {
    this[K.provider] = s.provider.name
    this[K.geminiKey] = s.geminiKey.trim()
    this[K.geminiModel] = s.geminiModel.trim()
    this[K.geminiResolved] = s.geminiResolvedModel
    this[K.geminiBase] = s.geminiBaseUrl.trim()
    this[K.fastMode] = s.fastMode
    this[K.preferLite] = s.preferLite
    this[K.openaiBase] = s.openaiBaseUrl.trim()
    this[K.openaiKey] = s.openaiKey.trim()
    this[K.openaiModel] = s.openaiModel.trim()
    this[K.fallback] = s.fallback
    this[K.temperature] = s.temperature
    this[K.contextTurns] = s.contextTurns
    this[K.customPrompt] = s.customPrompt
    this[K.userName] = s.userName
    this[K.style] = s.style.name
    this[K.autoSaveVocab] = s.autoSaveVocab
    this[K.wakePhrases] = s.wakePhrases
    this[K.wakeReplies] = s.wakeReplies
    this[K.wakeSensitivity] = s.wakeSensitivity
    this[K.ttsEngine] = s.ttsEngine
    this[K.ttsMode] = s.ttsMode.name
    this[K.viTtsEngine] = s.viTtsEngine
    this[K.koTtsEngine] = s.koTtsEngine
    this[K.viVoice] = s.viVoice
    this[K.koVoice] = s.koVoice
    this[K.aiVoice] = s.aiVoice
    this[K.speechRate] = s.speechRate
    this[K.speakReplies] = s.speakReplies
    this[K.continuous] = s.continuous
    this[K.greetOnShortcut] = s.greetOnShortcut
    this[K.preferGoogleStt] = s.preferGoogleStt
    this[K.endSilence] = s.endSilenceMs
    this[K.autoOff] = s.autoOffMinutes
    this[K.pauseOnScreenOff] = s.pauseOnScreenOff
    this[K.standbyOnLaunch] = s.standbyOnLaunch
    this[K.sheetsUrl] = s.sheetsUrl.trim()
    this[K.sheetsToken] = s.sheetsToken
    this[K.sheetsAuto] = s.sheetsAutoSync
    this[K.studyMatch] = s.studyMatchPercent
    this[K.studyReverse] = s.studyReverse
    this[K.studyAutoSpeak] = s.studyAutoSpeak
    this[K.studyDraw] = s.studyDrawPercent
    this[K.theme] = s.themeMode.name
    this[K.onboarded] = s.onboarded
    this[K.reminderOn] = s.reminderEnabled
    this[K.reminderMin] = s.reminderMinutes
    this[K.instantNotebook] = s.instantFromNotebook
    this[K.bubbleOn] = s.bubbleEnabled
    this[K.bubbleSize] = s.bubbleSize
    this[K.bubbleListen] = s.bubbleTapListen
    this[K.wakeListen] = s.wakeListenEnabled
    this[K.detailed] = s.detailedAnswers
}

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    val flow: Flow<AppSettings> = store.data
        .catch { emit(emptyPreferences()) }
        .map { it.toSettings() }

    suspend fun get(): AppSettings = flow.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }
}
