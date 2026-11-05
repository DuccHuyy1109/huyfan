package com.hankukko.huyfan.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hankukko.huyfan.ai.VocabItem
import com.hankukko.huyfan.assistant.Phase
import com.hankukko.huyfan.container
import com.hankukko.huyfan.data.AppSettings
import com.hankukko.huyfan.data.ChatMessage
import com.hankukko.huyfan.data.GrammarEntity
import com.hankukko.huyfan.data.LessonContent
import com.hankukko.huyfan.data.LessonSummary
import com.hankukko.huyfan.data.Provider
import com.hankukko.huyfan.data.SessionSummary
import com.hankukko.huyfan.data.VocabEntity
import com.hankukko.huyfan.service.HuyFanWidget
import com.hankukko.huyfan.util.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class Stats(
    val today: Int = 0,
    val total: Int = 0,
    val streak: Int = 0,
    val unsynced: Int = 0,
    val grammarTotal: Int = 0,
    val grammarToday: Int = 0,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val c = app.container
    val engine = c.engine

    val settings: StateFlow<AppSettings> = c.settingsState
    val engineState = engine.state
    val sheetsStatus = c.sheets.status

    val messages: StateFlow<List<ChatMessage>> =
        engine.messages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vocab: StateFlow<List<VocabEntity>> =
        c.vocab.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val grammar: StateFlow<List<GrammarEntity>> =
        c.grammar.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lessons: StateFlow<List<LessonSummary>> =
        c.lessons.lessons.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions: StateFlow<List<SessionSummary>> =
        c.chat.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stats: StateFlow<Stats> = combine(c.vocab.all, c.grammar.all) { list, gram ->
        val today = Dates.todayKey()
        Stats(
            today = list.count { it.day == today },
            total = list.size,
            streak = Dates.streak(list.map { it.day } + gram.map { it.day }),
            unsynced = list.count { !it.synced } + gram.count { !it.synced },
            grammarTotal = gram.size,
            grammarToday = gram.count { it.day == today },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Stats())

    // ---------- Chat ----------
    fun send(text: String) = engine.submit(text, handsFree = false)

    fun micTap() {
        when (engineState.value.phase) {
            Phase.LISTENING -> engine.stopListening()
            Phase.SPEAKING, Phase.THINKING, Phase.GREETING -> engine.cancel()
            Phase.IDLE -> engine.startListening(handsFree = true)
        }
    }

    fun speak(text: String) = c.speaker.speak(text)
    fun speakKorean(text: String) = c.speaker.speakKorean(text)
    fun stopSpeaking() = c.speaker.stop()

    // ---------- Hội thoại nhập vai ----------
    fun rolePlayTurn(
        system: String,
        turns: List<com.hankukko.huyfan.ai.Turn>,
        onResult: (Result<com.hankukko.huyfan.assistant.RolePlay.AiTurn>) -> Unit,
    ) = viewModelScope.launch {
        onResult(runCatching {
            val (raw, _) = c.ai.askJson(system, turns, com.hankukko.huyfan.assistant.RolePlay.schema())
            com.hankukko.huyfan.assistant.RolePlay.parse(raw)
        })
    }

    fun saveRolePlayVocab(items: List<com.hankukko.huyfan.assistant.RolePlay.Phrase>, onDone: (Int) -> Unit) = viewModelScope.launch {
        val n = c.vocab.saveFromAi(items.map { VocabItem(vi = it.vi, ko = it.ko) }).size
        launch { c.sheets.syncPending() }
        HuyFanWidget.refresh(getApplication())
        onDone(n)
    }
    fun newChat() = engine.newSession()
    fun openSession(id: Long) = engine.openSession(id)

    fun deleteSession(id: Long) = viewModelScope.launch {
        c.chat.deleteSession(id)
        engine.onSessionDeleted(id)
    }

    fun clearHistory() = viewModelScope.launch {
        c.chat.clearAll()
        engine.newSession()
    }

    // ---------- Vocab ----------
    fun toggleStar(v: VocabEntity) = viewModelScope.launch { c.vocab.toggleStar(v) }

    /** Xoá hẳn nhiều từ (sổ + Google Sheets + bài ôn). */
    fun deleteWords(ids: Collection<Long>) = viewModelScope.launch {
        c.sheets.deleteRemote(c.vocab.deleteMany(ids.toList()))
        HuyFanWidget.refresh(getApplication())
    }

    fun review(v: VocabEntity, grade: com.hankukko.huyfan.data.Grade) = viewModelScope.launch {
        c.vocab.review(v, grade)
        HuyFanWidget.refresh(getApplication())
    }

    // ---------- Ngữ pháp ----------
    fun toggleStarGrammar(g: GrammarEntity) = viewModelScope.launch { c.grammar.toggleStar(g) }

    fun deleteGrammar(ids: Collection<Long>) = viewModelScope.launch {
        c.sheets.deleteRemote(c.grammar.deleteMany(ids.toList()))
    }

    fun reviewGrammar(g: GrammarEntity, grade: com.hankukko.huyfan.data.Grade) = viewModelScope.launch { c.grammar.review(g, grade) }

    /** Lịch ôn: thẻ đến hạn hôm nay + dự báo 7 ngày. */
    val due: StateFlow<com.hankukko.huyfan.data.DueSummary?> = combine(c.vocab.all, c.grammar.all) { v, g ->
        com.hankukko.huyfan.data.DueSummary.of(v, g)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ---------- Bài ôn ----------
    fun saveLesson(
        id: Long?, name: String, vocabIds: Collection<Long>, grammarIds: Collection<Long>,
        config: com.hankukko.huyfan.data.StudyConfig, onDone: (Long) -> Unit = {},
    ) = viewModelScope.launch { onDone(c.lessons.save(id, name, vocabIds, grammarIds, config)) }

    /** Lưu cài đặt học: của bài ôn (nếu có) hoặc mặc định cho ôn nhanh. */
    fun saveStudyConfig(lessonId: Long?, config: com.hankukko.huyfan.data.StudyConfig) = viewModelScope.launch {
        if (lessonId != null) c.lessons.updateConfig(lessonId, config)
        else c.settings.update {
            it.copy(
                studyReverse = config.reverse, studyMatchPercent = config.matchPercent,
                studyDrawPercent = config.drawPercent, studyAutoSpeak = config.autoSpeak,
            )
        }
        if (lessonId != null) c.settings.update { it.copy(studyAutoSpeak = config.autoSpeak) }
    }

    fun deleteLesson(id: Long) = viewModelScope.launch { c.lessons.delete(id) }

    fun loadLesson(id: Long, onLoaded: (LessonContent?) -> Unit) = viewModelScope.launch { onLoaded(c.lessons.load(id)) }

    fun recordStudy(id: Long, score: Int) = viewModelScope.launch { c.lessons.recordStudy(id, score) }

    fun syncNow(onDone: (String) -> Unit) = viewModelScope.launch {
        val r = c.sheets.syncPending(force = true)
        onDone(r.fold({ if (it == 0) "Không có từ mới cần đồng bộ" else "Đã đồng bộ $it từ" }, { "Lỗi: ${it.message}" }))
    }

    fun restoreFromSheets(onDone: (String) -> Unit) = viewModelScope.launch {
        val r = c.sheets.restore()
        HuyFanWidget.refresh(getApplication())
        onDone(
            r.fold(
                { if (it.vocabAdded + it.grammarAdded == 0) "Máy đã có đủ dữ liệu – không có mục nào cần khôi phục (${it.skipped} mục đã có)"
                  else "Đã khôi phục ${it.vocabAdded} từ vựng, ${it.grammarAdded} ngữ pháp (bỏ qua ${it.skipped} mục đã có)" },
                { "Lỗi: ${it.message}" },
            )
        )
    }

    /** Ôn xong 1 phiên → đẩy lịch ôn mới lên Sheet (nếu bật tự đồng bộ). */
    fun syncAfterStudy() = viewModelScope.launch { c.sheets.syncPending() }

    fun resyncAll(onDone: (String) -> Unit) = viewModelScope.launch {
        val r = c.sheets.resyncAll()
        onDone(r.fold({ "Đã gửi lại toàn bộ $it từ" }, { "Lỗi: ${it.message}" }))
    }

    fun testReminder(onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { runCatching { com.hankukko.huyfan.service.Reminder.fire(getApplication(), force = true) }.getOrDefault(false) }
        onDone(ok)
    }

    fun testSheets(url: String, onDone: (String) -> Unit) = viewModelScope.launch {
        onDone(c.sheets.test(url).fold({ it }, { "Lỗi: ${it.message}" }))
    }

    /** Xuất CSV (UTF-8 BOM để Excel đọc đúng tiếng Hàn) rồi mở bảng chia sẻ. */
    fun exportCsv(context: Context) = viewModelScope.launch {
        val grammarFile = withContext(Dispatchers.IO) {
            val list = c.grammar.allNow()
            if (list.isEmpty()) return@withContext null
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val f = File(dir, "huyfan_ngu_phap_${Dates.todayKey()}.csv")
            fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
            f.writeText(buildString {
                append(0xFEFF.toChar())
                append("Ngày,Đuôi câu,Nghĩa,Dạng,Từ kết hợp,Cấu tạo,Câu mẫu,Nghĩa câu mẫu,Ghi chú\n")
                list.sortedWith(compareBy({ it.day }, { it.createdAt })).forEach {
                    append(listOf(it.day, it.pattern, it.meaning, it.form, it.combine, it.breakdown, it.exampleKo, it.exampleVi, it.note)
                        .joinToString(",") { v -> esc(v) }).append('\n')
                }
            })
            f
        }
        val file = withContext(Dispatchers.IO) {
            val list = c.vocab.allNow()
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val f = File(dir, "huyfan_tu_vung_${Dates.todayKey()}.csv")
            fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
            f.writeText(buildString {
                // BOM để Excel nhận đúng UTF-8 (tiếng Hàn)
                append(0xFEFF.toChar())
                append("Ngày,Tiếng Việt,Tiếng Hàn,Phiên âm,Hán Hàn,Hán Việt,Từ mượn,Từ loại,Cấu tạo,Câu mẫu,Nghĩa câu mẫu,Ghi chú\n")
                list.sortedWith(compareBy({ it.day }, { it.createdAt })).forEach {
                    append(listOf(it.day, it.vi, it.ko, it.romaja, it.hanja, it.hanViet, it.loan, it.pos, it.breakdown, it.exampleKo, it.exampleVi, it.note)
                        .joinToString(",") { s -> esc(s) }).append('\n')
                }
            })
            f
        }
        val uris = arrayListOf(FileProvider.getUriForFile(context, context.packageName + ".files", file))
        grammarFile?.let { uris += FileProvider.getUriForFile(context, context.packageName + ".files", it) }
        val send = Intent(if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND)
            .setType("text/csv")
            .apply { if (uris.size > 1) putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) else putExtra(Intent.EXTRA_STREAM, uris[0]) }
            .putExtra(Intent.EXTRA_SUBJECT, "Sổ từ vựng HUY FAN")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Xuất sổ từ vựng").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ---------- Settings ----------
    fun updateSettings(block: (AppSettings) -> AppSettings) = viewModelScope.launch { c.settings.update(block) }

    fun testAi(p: Provider, onDone: (String) -> Unit) = viewModelScope.launch {
        onDone(c.ai.test(p).fold({ it }, { (it as? com.hankukko.huyfan.ai.AiException)?.userMessage ?: "Lỗi: ${it.message}" }))
    }

    fun benchmark(onDone: (Result<List<com.hankukko.huyfan.ai.AiService.Bench>>) -> Unit) = viewModelScope.launch {
        onDone(c.ai.benchmark())
    }

    fun listModels(p: Provider, onDone: (Result<List<String>>) -> Unit) = viewModelScope.launch {
        onDone(c.ai.listModels(p))
    }

    val ttsReady = c.speaker.readyState

    // ---------- Viết tay ----------
    fun recognizeInk(lang: String, strokes: List<List<com.hankukko.huyfan.ink.InkPoint>>, cb: (Result<List<String>>) -> Unit) =
        viewModelScope.launch { cb(runCatching { c.ink.recognize(lang, strokes) }) }

    fun inkModelReady(lang: String, cb: (Boolean) -> Unit) = viewModelScope.launch { cb(c.ink.isDownloaded(lang)) }
    fun voiceOptions(korean: Boolean) = c.speaker.voiceOptions(korean)
    fun previewDeviceVoice(korean: Boolean, voice: String) = c.speaker.previewDevice(korean, voice)
    fun previewAiVoice(voice: String, onResult: (String?) -> Unit) = c.speaker.previewAi(voice, onResult)
    fun ttsEngines(): List<Pair<String, String>> = c.speaker.engines()
    fun ttsLanguages(): Pair<Boolean, Boolean> = c.speaker.languageSupport()
    fun ttsInstallIntent() = c.speaker.installVoiceDataIntent()
    fun testVoice() = c.speaker.speak("Xin chào, HUY FAN đây. 안녕하세요! 만나서 반가워요.")

    fun vocabItemsFor(list: List<VocabEntity>): List<VocabItem> = list.map { it.toItem() }
}
