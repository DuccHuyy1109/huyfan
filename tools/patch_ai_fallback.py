import io, sys, os

root = sys.argv[1]  # .../huyfan


def patch(rel, pairs):
    p = os.path.join(root, rel)
    s = io.open(p, encoding='utf-8').read()
    for a, b in pairs:
        if a not in s:
            raise SystemExit(rel + ' NOT FOUND: ' + a[:90])
        s = s.replace(a, b, 1)
    io.open(p, 'w', encoding='utf-8').write(s)


# Trả lời chi tiết dài hơn → nới giới hạn độ dài
patch('ai/Clients.kt', [
    ('''            .put("maxOutputTokens", 2048)''', '''            .put("maxOutputTokens", 4096)'''),
])

patch('ai/AiService.kt', [
    # Ghi log lỗi từng bên + ưu tiên lỗi của API chính khi báo cho người dùng
    ('''                val e = r.second.exceptionOrNull()!!
                if (e is CancellationException) throw e
                if (r.first == primary) primaryTroubleUntil = System.currentTimeMillis() + TROUBLE_MS
                if (firstError == null) firstError = e''', '''                val e = r.second.exceptionOrNull()!!
                if (e is CancellationException) throw e
                Log.w("HuyFanAI", "${r.first} lỗi: ${e.javaClass.simpleName}: ${e.message?.take(200)}")
                if (r.first == primary) primaryTroubleUntil = System.currentTimeMillis() + TROUBLE_MS
                // Báo lỗi của API chính (thường đúng bản chất hơn lỗi của API dự phòng)
                if (firstError == null || r.first == primary) firstError = e'''),
    # Dự phòng không đọc được ảnh: câu hiện tại không kèm ảnh → bỏ ảnh cũ, vẫn trả lời bằng chữ
    ('''                if (turns.any { it.image != null }) {
                    val vm = pickVisionModel(s)
                        ?: throw AiException("no vision", "API dự phòng không có model đọc được ảnh – đợi Gemini rảnh rồi hỏi lại nha.")
                    return openai.generate(s.openaiBaseUrl, s.openaiKey, vm, system, turns, s.temperature) to vm
                }''', '''                if (turns.any { it.image != null }) {
                    val vm = pickVisionModel(s)
                    if (vm != null) return openai.generate(s.openaiBaseUrl, s.openaiKey, vm, system, turns, s.temperature) to vm
                    // Ảnh nằm ở câu hiện tại thì bắt buộc phải đọc ảnh; ảnh cũ thì bỏ, trả lời theo chữ
                    if (turns.lastOrNull { it.role == Turn.USER }?.image != null) {
                        throw AiException("no vision", "API dự phòng không có model đọc được ảnh – đợi Gemini rảnh rồi hỏi lại nha.")
                    }
                    val textOnly = turns.map { if (it.image != null) it.copy(image = null, text = "[Ảnh đã gửi trước đó] ${it.text}") else it }
                    return openai.generate(s.openaiBaseUrl, s.openaiKey, s.openaiModel, system, textOnly, s.temperature) to s.openaiModel
                }'''),
])

# Chỉ gửi lại ảnh khi câu hỏi ngay sau câu có ảnh (hỏi tiếp về ảnh đó)
patch('data/Repositories.kt', [
    ('''        val lastImageId = list.lastOrNull { it.isUser && it.hasImage }?.id''', '''        val users = list.filter { it.isUser }
        // Ảnh ở câu hiện tại hoặc câu ngay trước (đang hỏi tiếp về ảnh) mới gửi lại
        val lastImageId = users.takeLast(2).lastOrNull { it.hasImage }?.id'''),
])
print('ok')
