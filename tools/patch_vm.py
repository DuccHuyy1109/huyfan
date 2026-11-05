import sys

p = sys.argv[1]
s = open(p, encoding='utf-8').read()


def rep(old, new):
    global s
    if old not in s:
        raise SystemExit('NOT FOUND: ' + old[:90])
    s = s.replace(old, new, 1)


rep('''    val sessions: StateFlow<List<SessionSummary>> =''', '''    val grammar: StateFlow<List<GrammarEntity>> =
        c.grammar.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lessons: StateFlow<List<LessonSummary>> =
        c.lessons.lessons.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions: StateFlow<List<SessionSummary>> =''')

rep('''    val stats: StateFlow<Stats> = c.vocab.all.map { list ->
        val today = Dates.todayKey()
        Stats(
            today = list.count { it.day == today },
            total = list.size,
            streak = Dates.streak(list.map { it.day }),
            unsynced = list.count { !it.synced },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Stats())''', '''    val stats: StateFlow<Stats> = combine(c.vocab.all, c.grammar.all) { list, gram ->
        val today = Dates.todayKey()
        Stats(
            today = list.count { it.day == today },
            total = list.size,
            streak = Dates.streak(list.map { it.day } + gram.map { it.day }),
            unsynced = list.count { !it.synced } + gram.count { !it.synced },
            grammarTotal = gram.size,
            grammarToday = gram.count { it.day == today },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Stats())''')

rep('''    fun deleteWord(v: VocabEntity) = viewModelScope.launch {
        c.vocab.delete(v)
        c.sheets.deleteRemote(listOf(v.uid))
        HuyFanWidget.refresh(getApplication())
    }

    fun review(v: VocabEntity, remembered: Boolean) = viewModelScope.launch { c.vocab.review(v, remembered) }''', '''    /** Xoá hẳn nhiều từ (sổ + Google Sheets + bài ôn). */
    fun deleteWords(ids: Collection<Long>) = viewModelScope.launch {
        c.sheets.deleteRemote(c.vocab.deleteMany(ids.toList()))
        HuyFanWidget.refresh(getApplication())
    }

    fun review(v: VocabEntity, remembered: Boolean) = viewModelScope.launch { c.vocab.review(v, remembered) }

    // ---------- Ngữ pháp ----------
    fun toggleStarGrammar(g: GrammarEntity) = viewModelScope.launch { c.grammar.toggleStar(g) }

    fun deleteGrammar(ids: Collection<Long>) = viewModelScope.launch {
        c.sheets.deleteRemote(c.grammar.deleteMany(ids.toList()))
    }

    fun reviewGrammar(g: GrammarEntity, remembered: Boolean) = viewModelScope.launch { c.grammar.review(g, remembered) }

    // ---------- Bài ôn ----------
    fun saveLesson(id: Long?, name: String, vocabIds: Collection<Long>, grammarIds: Collection<Long>, onDone: (Long) -> Unit = {}) =
        viewModelScope.launch { onDone(c.lessons.save(id, name, vocabIds, grammarIds)) }

    fun deleteLesson(id: Long) = viewModelScope.launch { c.lessons.delete(id) }

    fun loadLesson(id: Long, onLoaded: (LessonContent?) -> Unit) = viewModelScope.launch { onLoaded(c.lessons.load(id)) }

    fun recordStudy(id: Long, score: Int) = viewModelScope.launch { c.lessons.recordStudy(id, score) }''')

rep('''data class Stats(val today: Int = 0, val total: Int = 0, val streak: Int = 0, val unsynced: Int = 0)''',
    '''data class Stats(
    val today: Int = 0,
    val total: Int = 0,
    val streak: Int = 0,
    val unsynced: Int = 0,
    val grammarTotal: Int = 0,
    val grammarToday: Int = 0,
)''')

# CSV: thêm file ngữ pháp
rep('''    fun exportCsv(context: Context) = viewModelScope.launch {
        val file = withContext(Dispatchers.IO) {''', '''    fun exportCsv(context: Context) = viewModelScope.launch {
        val grammarFile = withContext(Dispatchers.IO) {
            val list = c.grammar.allNow()
            if (list.isEmpty()) return@withContext null
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val f = File(dir, "huyfan_ngu_phap_${Dates.todayKey()}.csv")
            fun esc(s: String) = "\\"" + s.replace("\\"", "\\"\\"") + "\\""
            f.writeText(buildString {
                append(0xFEFF.toChar())
                append("Ngày,Đuôi câu,Nghĩa,Dạng,Từ kết hợp,Câu mẫu,Nghĩa câu mẫu,Ghi chú\\n")
                list.sortedWith(compareBy({ it.day }, { it.createdAt })).forEach {
                    append(listOf(it.day, it.pattern, it.meaning, it.form, it.combine, it.exampleKo, it.exampleVi, it.note)
                        .joinToString(",") { v -> esc(v) }).append('\\n')
                }
            })
            f
        }
        val file = withContext(Dispatchers.IO) {''')

rep('''        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)''', '''        val uris = arrayListOf(FileProvider.getUriForFile(context, context.packageName + ".files", file))
        grammarFile?.let { uris += FileProvider.getUriForFile(context, context.packageName + ".files", it) }
        val send = Intent(if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND)
            .setType("text/csv")
            .apply { if (uris.size > 1) putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) else putExtra(Intent.EXTRA_STREAM, uris[0]) }''')

rep('import com.hankukko.huyfan.data.ChatMessage\n', '''import com.hankukko.huyfan.data.ChatMessage
import com.hankukko.huyfan.data.GrammarEntity
import com.hankukko.huyfan.data.LessonContent
import com.hankukko.huyfan.data.LessonSummary
''')
rep('import kotlinx.coroutines.flow.SharingStarted\n', 'import kotlinx.coroutines.flow.SharingStarted\nimport kotlinx.coroutines.flow.combine\n')

open(p, 'w', encoding='utf-8').write(s)
print('ok')
