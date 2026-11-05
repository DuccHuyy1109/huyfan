import sys, os

root = sys.argv[1]


def patch(rel, pairs):
    p = os.path.join(root, rel)
    s = open(p, encoding='utf-8').read()
    for old, new in pairs:
        if old not in s:
            raise SystemExit(rel + ' NOT FOUND: ' + old[:90])
        s = s.replace(old, new, 1)
    open(p, 'w', encoding='utf-8').write(s)


patch('assistant/ConversationEngine.kt', [
    ('''    /** Hỏi lại câu trước (khi lỗi mạng...) */''', '''    /**
     * Người dùng bấm thùng rác trên bảng từ/ngữ pháp: xoá hẳn khỏi sổ, Google Sheets, bài ôn
     * và khỏi ngữ cảnh AI (để AI không "nhớ" từ đó nữa).
     */
    fun removeLearned(m: ChatMessage, kind: String, index: Int) {
        scope.launch {
            val day = Dates.dayKeyOf(m.createdAt)
            if (kind == LessonItem.VOCAB) {
                m.vocab.getOrNull(index)?.let { item ->
                    c.vocab.findOn(item.ko, day)?.let { e -> c.sheets.deleteRemote(c.vocab.deleteMany(listOf(e.id))) }
                }
            } else {
                m.grammar.getOrNull(index)?.let { item ->
                    c.grammar.findOn(item.pattern, day)?.let { e -> c.sheets.deleteRemote(c.grammar.deleteMany(listOf(e.id))) }
                }
            }
            c.chat.removeItem(m, kind, index)
            HuyFanWidget.refresh(app)
        }
    }

    /** Hỏi lại câu trước (khi lỗi mạng...) */'''),
    ('import com.hankukko.huyfan.data.ChatMessage\n',
     'import com.hankukko.huyfan.data.ChatMessage\nimport com.hankukko.huyfan.data.LessonItem\nimport com.hankukko.huyfan.util.Dates\n'),
])

patch('ui/screens/ChatScreen.kt', [
    ('''                            onRetry = prevUser?.let { q -> { vm.send(q) } },
                        )''', '''                            onRetry = prevUser?.let { q -> { vm.send(q) } },
                            onDeleteVocab = { i ->
                                m.vocab.getOrNull(i)?.let { context.toastShort("Đã xoá “${it.ko}” khỏi sổ") }
                                vm.engine.removeLearned(m, LessonItem.VOCAB, i)
                            },
                            onDeleteGrammar = { i ->
                                m.grammar.getOrNull(i)?.let { context.toastShort("Đã xoá “${it.pattern}” khỏi sổ") }
                                vm.engine.removeLearned(m, LessonItem.GRAMMAR, i)
                            },
                        )'''),
    ('''fun Context.copyToClipboard(text: String) {''', '''fun Context.toastShort(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun Context.copyToClipboard(text: String) {'''),
    ('import com.hankukko.huyfan.assistant.Phase\n', 'import com.hankukko.huyfan.assistant.Phase\nimport com.hankukko.huyfan.data.LessonItem\n'),
])

patch('ui/AssistActivity.kt', [
    ('''                        if (vocab.isNotEmpty()) {
                            VocabTable(vocab, m.savedCount, onSpeakKorean = { c.speaker.speakKorean(it) })
                            Spacer(Modifier.height(10.dp))
                        }''', '''                        if (vocab.isNotEmpty()) {
                            VocabTable(
                                vocab, m.savedCount, onSpeakKorean = { c.speaker.speakKorean(it) },
                                onDelete = { i -> engine.removeLearned(m, LessonItem.VOCAB, i) },
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        val grammar = m.grammar
                        if (grammar.isNotEmpty()) {
                            GrammarTable(
                                grammar, m.savedCount, onSpeakKorean = { c.speaker.speakKorean(it) },
                                onDelete = { i -> engine.removeLearned(m, LessonItem.GRAMMAR, i) },
                            )
                            Spacer(Modifier.height(10.dp))
                        }'''),
    ('import com.hankukko.huyfan.ui.components.VocabTable\n',
     'import com.hankukko.huyfan.ui.components.VocabTable\nimport com.hankukko.huyfan.ui.components.GrammarTable\nimport com.hankukko.huyfan.data.LessonItem\n'),
])
print('ok')
