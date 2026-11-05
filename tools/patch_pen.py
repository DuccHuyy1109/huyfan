import sys, os

root = sys.argv[1]


def patch(rel, pairs):
    p = os.path.join(root, rel)
    s = open(p, encoding='utf-8').read()
    for old, new in pairs:
        if old not in s:
            raise SystemExit(rel + ' NOT FOUND: ' + old[:100])
        s = s.replace(old, new, 1)
    open(p, 'w', encoding='utf-8').write(s)


patch('AppContainer.kt', [
    ('''    val speaker = Speaker(app, settingsState, http, appScope)''',
     '''    val speaker = Speaker(app, settingsState, http, appScope)
    val ink = com.hankukko.huyfan.ink.InkRecognizer()'''),
])

patch('ui/MainViewModel.kt', [
    ('''    val ttsReady = c.speaker.readyState''', '''    val ttsReady = c.speaker.readyState

    // ---------- Viết tay ----------
    fun recognizeInk(lang: String, strokes: List<List<com.hankukko.huyfan.ink.InkPoint>>, cb: (Result<List<String>>) -> Unit) =
        viewModelScope.launch { cb(runCatching { c.ink.recognize(lang, strokes) }) }

    fun inkModelReady(lang: String, cb: (Boolean) -> Unit) = viewModelScope.launch { cb(c.ink.isDownloaded(lang)) }'''),
])

patch('ui/screens/ReviewScreen.kt', [
    # tham số
    ('''    matchPercent: Int,
    autoSpeak: Boolean,
    onFlip: () -> Unit,
    onReveal: () -> Unit,''', '''    matchPercent: Int,
    drawPercent: Int,
    autoSpeak: Boolean,
    onFlip: () -> Unit,
    onReveal: () -> Unit,'''),
    # trạng thái bảng viết
    ('''    var feedback by remember(card.key) { mutableStateOf<Pair<String, Boolean>?>(null) }''',
     '''    var feedback by remember(card.key) { mutableStateOf<Pair<String, Boolean>?>(null) }
    // Bảng viết tay
    var penOpen by remember(card.key) { mutableStateOf(false) }
    var inkBusy by remember(card.key) { mutableStateOf(false) }
    var inkBusyText by remember(card.key) { mutableStateOf("Đang nhận dạng chữ…") }
    var inkResult by remember(card.key) { mutableStateOf<Pair<String, Boolean>?>(null) }'''),
    # mở khối: nếu đang viết thì hiện bảng viết thay cho thẻ
    ('''    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(450), label = "flip")''',
     '''    if (penOpen) {
        val question = when {
            v != null -> if (koFront) v.ko else v.vi
            g != null -> if (koFront) g.pattern else g.meaning
            else -> ""
        }
        val inkLang = if (koFront) "vi" else "ko"
        DrawPad(
            prompt = question,
            hint = if (koFront) "Viết nghĩa tiếng Việt · đúng từ $drawPercent% thì lật thẻ"
            else "Viết từ tiếng Hàn · đúng từ $drawPercent% thì lật thẻ",
            busy = inkBusy,
            busyText = inkBusyText,
            result = inkResult,
            onCheck = { strokes ->
                inkBusy = true
                inkResult = null
                inkBusyText = "Đang nhận dạng chữ…"
                vm.inkModelReady(inkLang) { ready ->
                    if (!ready) inkBusyText = "Lần đầu: đang tải bộ nhận dạng chữ viết (~20MB)…"
                }
                vm.recognizeInk(inkLang, strokes) { r ->
                    inkBusy = false
                    r.onSuccess { cands ->
                        if (cands.isEmpty()) {
                            inkResult = "Chưa đọc được chữ, viết rõ hơn nha" to false
                        } else {
                            val best = cands.take(5).maxBy { com.hankukko.huyfan.util.Similarity.score(it, expected) }
                            val score = com.hankukko.huyfan.util.Similarity.score(best, expected)
                            if (score >= drawPercent) {
                                feedback = "Viết đúng! “$best” · $score%" to true
                                penOpen = false
                                onReveal()
                            } else {
                                inkResult = "Chưa khớp ($score%) · đọc được: “$best”" to false
                            }
                        }
                    }.onFailure { e -> inkResult = "Lỗi nhận dạng: ${e.message}" to false }
                }
            },
            onClose = { penOpen = false },
            modifier = Modifier.weight(1f),
        )
    } else {
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(450), label = "flip")'''),
    # bọc thẻ để đặt nút bút ở góc
    ('''    Box(
        Modifier
            .fillMaxWidth()
            .weight(1f)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * density
            }''', '''    Box(Modifier.fillMaxWidth().weight(1f)) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * density
            }'''),
    ('''    }

    // Kết quả so khớp giọng nói / chữ đang nghe''', '''    }
        // Nút bút: viết tay đáp án để lật thẻ
        IconButton(
            onClick = { penOpen = true; inkResult = null },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(46.dp)
                .clip(CircleShape)
                .background(if (showFront) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        ) {
            Icon(Icons.Rounded.Edit, "Viết tay", tint = if (showFront) Color.White else MaterialTheme.colorScheme.primary)
        }
    }

    // Kết quả so khớp giọng nói / chữ đang nghe'''),
    ('import com.hankukko.huyfan.ui.MainViewModel\n', 'import com.hankukko.huyfan.ui.MainViewModel\nimport com.hankukko.huyfan.ui.components.DrawPad\n'),
])

# đóng khối else ở cuối StudyCardView
p = os.path.join(root, 'ui/screens/ReviewScreen.kt')
s = open(p, encoding='utf-8').read()
anchor = '''            Icon(Icons.Rounded.Check, null, tint = Color.White); Spacer(Modifier.width(4.dp)); Text("Nhớ rồi", color = Color.White, maxLines = 1)
        }
    }
}'''
if anchor not in s:
    raise SystemExit('end anchor not found')
s = s.replace(anchor, '''            Icon(Icons.Rounded.Check, null, tint = Color.White); Spacer(Modifier.width(4.dp)); Text("Nhớ rồi", color = Color.White, maxLines = 1)
        }
    }
    }
}''', 1)
open(p, 'w', encoding='utf-8').write(s)
print('ok')
