import io, sys, os

root = sys.argv[1]  # .../huyfan
p = os.path.join(root, 'ui/screens/AutoInterpreterScreen.kt')
s = io.open(p, encoding='utf-8').read()

# 1) Thay danh sách thẻ từng câu bằng các KHỐI cùng ngôn ngữ
a_start = s.index('                    items(lines, key = { it.id }) { l ->')
a_end = s.index('                }\n\n                // ---------- Trạng thái nghe ----------')
new_items = '''                    items(blocksOf(lines), key = { it.key }) { b ->
                        BlockCard(
                            b,
                            onSpeak = { c.speaker.speakAs(b.translation, korean = b.side == Side.VI) },
                            onCopy = {
                                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                                cm?.setPrimaryClip(android.content.ClipData.newPlainText("HUY FAN", b.translation))
                                android.widget.Toast.makeText(context, "Đã sao chép bản dịch", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onBig = { big = b },
                        )
                    }
'''
s = s[:a_start] + new_items + s[a_end:]

# 2) Phóng to cả khối
s = s.replace('''    var big by remember { mutableStateOf<AutoLine?>(null) }''', '''    var big by remember { mutableStateOf<Block?>(null) }''', 1)
old_big = '''    big?.let { l ->
        BigTranslation(
            l.translation, l.fixed.ifBlank { l.heard }, toKorean = l.side == Side.VI,
            onSpeak = { c.speaker.speakAs(l.translation, korean = l.side == Side.VI) },
            onDismiss = { big = null },
        )
    }'''
if old_big not in s:
    raise SystemExit('big not found')
s = s.replace(old_big, '''    big?.let { b ->
        BigTranslation(
            b.translation, b.original, toKorean = b.side == Side.VI,
            onSpeak = { c.speaker.speakAs(b.translation, korean = b.side == Side.VI) },
            onDismiss = { big = null },
        )
    }''', 1)

# 3) Thay AutoCard bằng khối song ngữ
i = s.index('@Composable\nprivate fun AutoCard(')
s = s[:i] + io.open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'auto_blocks.kt.txt'), encoding='utf-8').read()
io.open(p, 'w', encoding='utf-8').write(s)
print('ok')
