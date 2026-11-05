import sys

p = sys.argv[1]
s = open(p, encoding='utf-8').read()


def rep(old, new):
    global s
    if old not in s:
        raise SystemExit('NOT FOUND: ' + old[:90])
    s = s.replace(old, new, 1)


rep('''    var reviewSource by rememberSaveable { mutableStateOf(VocabFilter.TODAY) }''',
    '''    var study by remember { mutableStateOf<StudySession?>(null) }
    var lessonEditor by remember { mutableStateOf<LessonEditorRequest?>(null) }''')

rep('''                EngineEvent.OpenReview -> { tab = Tab.REVIEW; reviewSource = VocabFilter.TODAY }''',
    '''                EngineEvent.OpenReview -> tab = Tab.REVIEW''')

rep('''                        Tab.VOCAB -> VocabScreen(
                            vm, vocabFilter, onFilter = { vocabFilter = it },
                            onReviewDay = { reviewSource = it; tab = Tab.REVIEW },
                        )
                        Tab.REVIEW -> ReviewScreen(
                            vm, reviewSource, onSource = { reviewSource = it },
                            onVoiceQuiz = {
                                tab = Tab.CHAT
                                vm.engine.submit("Kiểm tra tôi các từ đã học hôm nay, hỏi từng từ một nhé", handsFree = true)
                            },
                        )''', '''                        Tab.VOCAB -> VocabScreen(
                            vm, vocabFilter, onFilter = { vocabFilter = it },
                            onOpenReview = { tab = Tab.REVIEW },
                            onCreateLesson = { v, g -> lessonEditor = LessonEditorRequest(vocabIds = v, grammarIds = g) },
                        )
                        Tab.REVIEW -> ReviewScreen(
                            vm,
                            onStart = { study = it },
                            onEditLesson = { lessonEditor = it },
                            onVoiceQuiz = {
                                tab = Tab.CHAT
                                vm.engine.submit("Kiểm tra tôi các từ đã học hôm nay, hỏi từng từ một nhé", handsFree = true)
                            },
                        )''')

rep('''    if (showSheets) SheetsGuideDialog(onDismiss = { showSheets = false })''',
    '''    if (showSheets) SheetsGuideDialog(onDismiss = { showSheets = false })
    study?.let { StudyDialog(vm, it, onDismiss = { study = null }) }
    lessonEditor?.let { LessonEditorDialog(vm, it, onDismiss = { lessonEditor = null }) }''')

rep('import com.hankukko.huyfan.ui.screens.ReviewScreen\n', '''import com.hankukko.huyfan.ui.screens.LessonEditorDialog
import com.hankukko.huyfan.ui.screens.LessonEditorRequest
import com.hankukko.huyfan.ui.screens.ReviewScreen
import com.hankukko.huyfan.ui.screens.StudyDialog
import com.hankukko.huyfan.ui.screens.StudySession
''')

open(p, 'w', encoding='utf-8').write(s)
print('ok')
