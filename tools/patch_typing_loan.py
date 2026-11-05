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


# ---------- Engine: chuyển sang gõ phím ----------
patch(J + 'assistant/ConversationEngine.kt', [
    ('''    /** Thôi chế độ rảnh tay: đọc nốt câu đang đọc nhưng không nghe tiếp. */''',
     '''    /**
     * Người dùng chuyển sang gõ phím: dừng mic (bỏ luôn phần vừa nghe, không gửi) và dừng đọc,
     * thôi chế độ rảnh tay. Đang chờ AI trả lời thì để trả lời xong nhưng không tự nghe tiếp.
     */
    fun switchToTyping() {
        when (_state.value.phase) {
            Phase.LISTENING, Phase.GREETING, Phase.SPEAKING -> cancel()
            else -> _state.update { it.copy(handsFree = false) }
        }
    }

    /** Thôi chế độ rảnh tay: đọc nốt câu đang đọc nhưng không nghe tiếp. */'''),
])

# ---------- Chat: chạm / gõ vào ô nhập ----------
patch(J + 'ui/screens/ChatScreen.kt', [
    ('''                    onValueChange = { input = it },
                    placeholder = { Text(if (pendingImage != null)''',
     '''                    onValueChange = {
                        val ph = vm.engine.state.value.phase
                        if (ph == Phase.LISTENING || ph == Phase.GREETING || ph == Phase.SPEAKING) vm.engine.switchToTyping()
                        input = it
                    },
                    placeholder = { Text(if (pendingImage != null)'''),
    ('''                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp, max = 140.dp),''',
     '''                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp, max = 140.dp)
                        // Chạm vào ô để gõ → dừng mic, bỏ phần đã nghe
                        .onFocusChanged { if (it.isFocused) vm.engine.switchToTyping() },'''),
])

# ---------- Cửa sổ chat nổi (bong bóng) ----------
patch(J + 'ui/AssistActivity.kt', [
    ('''                    value = input, onValueChange = { input = it },
                    placeholder = { Text("Gõ câu hỏi…") },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).focusRequester(inputFocus),''',
     '''                    value = input,
                    onValueChange = {
                        val ph = engine.state.value.phase
                        if (ph == Phase.LISTENING || ph == Phase.GREETING || ph == Phase.SPEAKING) engine.switchToTyping()
                        input = it
                    },
                    placeholder = { Text("Gõ câu hỏi…") },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).focusRequester(inputFocus)
                        .onFocusChanged { if (it.isFocused) engine.switchToTyping() },'''),
    ('''                        .clickable { typing = !typing },''',
     '''                        .clickable { typing = !typing; if (typing) engine.switchToTyping() },'''),
    ('''    LaunchedEffect(startTyping, startedAt) { if (startTyping) typing = true }''',
     '''    LaunchedEffect(startTyping, startedAt) { if (startTyping) { typing = true; engine.switchToTyping() } }'''),
])

# ---------- Sổ từ: tìm theo từ mượn; bổ sung từ mượn cho từ cũ ----------
patch(J + 'ui/screens/VocabScreen.kt', [
    ('''                it.hanViet.lowercase().contains(q) || it.hanja.contains(q)
        }''',
     '''                it.hanViet.lowercase().contains(q) || it.hanja.contains(q) || it.loan.lowercase().contains(q)
        }'''),
])
patch(J + 'data/Repositories.kt', [
    ('''    suspend fun toggleStar(v: VocabEntity) = dao.update(v.copy(starred = !v.starred))''',
     '''    suspend fun toggleStar(v: VocabEntity) = dao.update(v.copy(starred = !v.starred))

    /** Điền gốc từ mượn cho mọi mục cùng từ chưa có gốc. Trả về số mục đã điền. */
    suspend fun setLoan(ko: String, loan: String): Int {
        val list = dao.findAllByKey(keyOf(ko)).filter { it.hanja.isBlank() && it.loan.isBlank() }
        list.forEach { dao.update(it.copy(loan = loan)) }
        return list.size
    }'''),
])
patch(J + 'AppContainer.kt', [
    ('''        appScope.launch {
            vocab.cleanHanja()
            sheets.syncPending()
        }''',
     '''        appScope.launch {
            vocab.cleanHanja()
            sheets.syncPending()
        }
        appScope.launch {
            delay(8000) // để app mở xong, không tranh lượt AI với câu hỏi đầu tiên
            runCatching { com.hankukko.huyfan.ai.LoanBackfill.run(this@AppContainer) }
                .onFailure { android.util.Log.w("HuyFanAI", "bổ sung từ mượn lỗi – để lần sau: ${it.message}") }
        }'''),
])
print('ok')
