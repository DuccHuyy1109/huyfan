import io, sys, os

root = sys.argv[1]  # .../app/src/main
J = 'java/com/hankukko/huyfan/'
S = J + 'ui/screens/'


def patch(rel, pairs):
    p = os.path.join(root, rel)
    s = io.open(p, encoding='utf-8').read()
    for a, b in pairs:
        if a not in s:
            raise SystemExit(rel + ' NOT FOUND: ' + a[:90])
        s = s.replace(a, b, 1)
    io.open(p, 'w', encoding='utf-8').write(s)


# ======================= Chat =======================
patch(S + 'ChatScreen.kt', [
    ('''"Bấm mic hoặc gọi “HUY FAN” rồi hỏi tui bất cứ từ nào.\\nTừ ông hỏi sẽ tự lưu vào sổ theo ngày."''',
     '''"Bấm mic hoặc gọi “HUY FAN”"'''),
    ('''"Hỏi từ vựng, dịch, ngữ pháp…"''', '''"Hỏi HUY FAN…"'''),
    # Chip chế độ chờ: chỉ chữ "Chờ" – đèn xanh sáng = đang bật, xám = tắt
    ('''Text(if (on) "Chờ: BẬT" else "Chờ: TẮT", style = MaterialTheme.typography.labelMedium''',
     '''Text("Chờ", style = MaterialTheme.typography.labelMedium'''),
])

# Nút "Trả lời chi tiết: TẮT" → "Chi tiết" (sáng = bật, viền = tắt)
patch(J + 'ui/components/Components.kt', [
    ('''        Text("📖", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(4.dp))
        Text(
            if (compact) (if (on) "Chi tiết" else "Ngắn") else if (on) "Trả lời chi tiết: BẬT" else "Trả lời chi tiết: TẮT",''',
     '''        Icon(Icons.AutoMirrored.Rounded.MenuBook, null, tint = if (on) Color.White else color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            if (compact) (if (on) "Chi tiết" else "Ngắn") else "Chi tiết",'''),
])

# ======================= Phiên dịch =======================
patch(S + 'TranslateScreen.kt', [
    ('''Text("🇻🇳 Việt ⇄ Hàn 🇰🇷 · tự sửa câu nghe nhầm", style = MaterialTheme.typography.bodySmall, color = x.muted)''',
     '''Text("🇻🇳 Việt ⇄ Hàn 🇰🇷", style = MaterialTheme.typography.bodySmall, color = x.muted)'''),
    ('''desc = "Bấm 🇻🇳 hoặc 🇰🇷 rồi nói – dịch xong đọc to bằng đúng giọng, tự luân phiên lượt hai người. Phóng to cho người Hàn đọc.",''',
     '''desc = "Bấm nút rồi nói – dịch xong đọc to.",'''),
    ('''desc = "Luôn nghe, tự nhận ra tiếng Việt hay tiếng Hàn – ai nói câu nào dịch câu đó, liên tục như dịch trực tiếp của Google Dịch.",''',
     '''desc = "Luôn nghe – ai nói gì dịch nấy.",'''),
    ('''                    "Đặt điện thoại giữa hai người, nói rõ từng câu rồi ngừng một chút. Nơi quá ồn nên dùng “Dịch hội thoại” " +
                        "(bấm nút rồi mới nói) cho chính xác hơn. Cũng mở được từ bong bóng chat, kể cả khi đang dùng app khác.",''',
     '''                    "Đặt máy giữa hai người, nói rõ từng câu. Nơi ồn nên dùng chế độ 01.",'''),
])

patch(S + 'AutoInterpreterScreen.kt', [
    ('''Text("Tự nhận 🇻🇳 tiếng Việt / 🇰🇷 한국어 · nghe liên tục", style = MaterialTheme.typography.labelMedium, color = x.muted)''',
     '''Text("🇻🇳 ⇄ 🇰🇷 · tự nhận ngôn ngữ", style = MaterialTheme.typography.labelMedium, color = x.muted)'''),
    ('''                                    .background(Brush.linearGradient(listOf(Brand.Violet, Brand.Blue)))
                                    .padding(18.dp)''',
     '''                                    .background(Brand.gradient)
                                    .padding(18.dp)'''),
    ('''                                Text(
                                    "Đặt điện thoại giữa hai người. Ai nói tiếng Việt hay tiếng Hàn đều được – nói xong 1 câu, ngừng chút là có bản dịch. " +
                                        "Câu nghe nhầm sẽ được sửa lại cho đúng.",
                                    color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodySmall,
                                )''',
     '''                                Text(
                                    "Nói xong một câu, ngừng chút là có bản dịch.",
                                    color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodySmall,
                                )'''),
])

patch(S + 'InterpreterScreen.kt', [
    ('''Text("🇻🇳 Việt ⇄ Hàn 🇰🇷 · tự sửa câu nghe nhầm", style = MaterialTheme.typography.labelMedium, color = x.muted)''',
     '''Text("🇻🇳 ⇄ 🇰🇷 · tự sửa câu nghe nhầm", style = MaterialTheme.typography.labelMedium, color = x.muted)'''),
    ('''            "1. Bấm 🇻🇳 rồi nói tiếng Việt → HUY FAN sửa câu nghe nhầm, dịch và đọc to tiếng Hàn.\\n" +
                "2. Bật “Luân phiên”: đọc xong sẽ tự nghe người Hàn trả lời, rồi lại tới lượt bạn.\\n" +
                "3. Bấm ⛶ trên bản dịch để phóng to cho người Hàn đọc (lật ngược được nếu ngồi đối diện).",''',
     '''            "Bấm 🇻🇳 rồi nói · ⛶ để phóng to cho người Hàn đọc.",'''),
])

patch(S + 'RolePlayScreen.kt', [
    ('''                    .background(Brush.linearGradient(listOf(Brand.Violet, Brand.Blue)))
                    .padding(16.dp)''',
     '''                    .background(Brand.gradient)
                    .padding(16.dp)'''),
    ('''"HUY FAN đóng 1 vai và nói tiếng Hàn. Ông trả lời bằng mic (hoặc gõ). Nói sai sẽ được sửa ngay, kèm 2 câu gợi ý để nói tiếp."''',
     '''"HUY FAN đóng vai nói tiếng Hàn – nói sai được sửa ngay."'''),
])

# ======================= Ôn tập =======================
patch(S + 'ReviewScreen.kt', [
    ('''                    Text("Ôn tập", style = MaterialTheme.typography.headlineMedium)
                    Text("Ôn nhanh hoặc tự tạo bài ôn riêng", style = MaterialTheme.typography.bodySmall, color = x.muted)''',
     '''                    Text("Ôn tập", style = MaterialTheme.typography.headlineMedium)'''),
    ('''"Luyện phát âm", "Đọc từ, câu mẫu → chấm điểm",''', '''"Luyện phát âm", "Chấm điểm phát âm",'''),
    ('''"Hội thoại", "Nhập vai nói tiếng Hàn",''', '''"Hội thoại", "Nhập vai",'''),
    ('''                    .background(Brush.horizontalGradient(listOf(Brand.Blue.copy(alpha = 0.16f), Brand.Violet.copy(alpha = 0.16f))))
                    .clickable(onClick = onVoiceQuiz)''',
     '''                    .background(Brush.horizontalGradient(listOf(Brand.Cyan.copy(alpha = 0.12f), Brand.Violet.copy(alpha = 0.14f))))
                    .border(1.dp, Brush.horizontalGradient(listOf(Brand.Cyan.copy(alpha = 0.4f), Brand.Violet.copy(alpha = 0.2f))), RoundedCornerShape(18.dp))
                    .clickable(onClick = onVoiceQuiz)'''),
    ('''                    Text("Để HUY FAN đố bằng giọng nói", fontWeight = FontWeight.SemiBold)
                    Text("Hỏi từng từ đã học hôm nay, chấm đúng/sai", style = MaterialTheme.typography.bodySmall, color = x.muted)''',
     '''                    Text("Đố bằng giọng nói", fontWeight = FontWeight.SemiBold)
                    Text("Từ đã học hôm nay", style = MaterialTheme.typography.bodySmall, color = x.muted)'''),
    ('''"Bấm “Tạo bài ôn” → đặt tên → chọn từ/ngữ pháp → Tạo → Học.\\nHoặc trong Sổ học: nhấn giữ để chọn nhiều rồi tạo bài ôn."''',
     '''"Bấm “Tạo bài ôn” hoặc nhấn giữ từ trong Sổ học"'''),
])

# ======================= Sổ từ / Nguyên tắc / Chữ Hán / Cài đặt =======================
patch(S + 'VocabScreen.kt', [
    ('''"Nhấn giữ 1 mục để chọn nhiều (xoá / tạo bài ôn)"''', '''"Nhấn giữ để chọn nhiều"'''),
])
patch(S + 'RulesScreen.kt', [
    ('''"Cấu trúc câu, cách dịch, tiểu từ, bất quy tắc, biến âm khi đọc… Chạm vào ví dụ để nghe đọc, bấm “Hỏi HUY FAN” để được giải thích thêm."''',
     '''"Cấu trúc, tiểu từ, bất quy tắc, biến âm – chạm ví dụ để nghe."'''),
])
patch(S + 'HanjaScreen.kt', [
    ('''"Từ Hán Hàn (vd 학생 學生 – học sinh) sẽ hiện ở đây. Người Việt đoán nghĩa từ Hán Hàn qua âm Hán Việt rất nhanh!"''',
     '''"Từ Hán Hàn (vd 학생 學生 – học sinh) sẽ hiện ở đây."'''),
    ('''"Mẹo: âm Hán Việt ≈ âm Hán Hàn (學 học → 학). Thuộc 1 chữ là đoán được cả chục từ. Chạm vào chữ để xem các từ cùng chữ."''',
     '''"Âm Hán Việt ≈ âm Hán Hàn (學 học → 학)"'''),
])
patch(S + 'SettingsScreen.kt', [
    ('''            Text("Chạm vào mục để chỉnh", style = MaterialTheme.typography.bodySmall, color = x.muted, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))''',
     '''            Spacer(Modifier.height(12.dp))'''),
    ('''"Gemini miễn phí hay nghẽn giờ cao điểm. Dán Groq key (miễn phí, rất nhanh) – Gemini lỗi là HUY FAN tự chuyển sang Groq."''',
     '''"Dự phòng khi Gemini nghẽn – Groq miễn phí, rất nhanh."'''),
    ('''"AI giảng kỹ: nghĩa, sắc thái, cách dùng, ví dụ, phát âm… Bảng từ vựng/ngữ pháp vẫn như thường. Bật/tắt nhanh ở màn Chat hoặc nói “trả lời chi tiết” / “trả lời ngắn gọn”"''',
     '''"Giảng kỹ nghĩa, sắc thái, ví dụ, phát âm"'''),
    ('''"Hay tự bật nhầm → chọn Ít nhầm. Gọi mãi không nghe → chọn Nhạy. Đổi xong tắt/bật lại chế độ chờ."''',
     '''"Bật nhầm → Ít nhầm · Gọi không nghe → Nhạy"'''),
    ('''                        "Giọng AI Gemini tự nhiên như người thật, 1 giọng đọc liền cả tiếng Việt lẫn tiếng Hàn. " +
                            "Dùng chung Gemini key; mạng chậm hoặc lỗi thì tự đọc bằng giọng máy.",''',
     '''                        "Giọng AI tự nhiên, đọc liền Việt + Hàn. Lỗi mạng thì dùng giọng máy.",'''),
    ('''                    "Đọc tức thì, dùng khi bấm loa ở từ vựng và làm dự phòng. Mỗi ngôn ngữ chọn bộ đọc + giọng riêng " +
                        "(vd tiếng Hàn dùng Samsung, tiếng Việt dùng Google) – bấm loa để nghe thử.",''',
     '''                    "Đọc tức thì khi bấm loa – chọn giọng riêng cho mỗi ngôn ngữ.",'''),
    ('''"Tiết kiệm pin hơn. TẮT mục này nếu muốn gọi HUY FAN cả khi máy đang khoá/tắt màn hình"''',
     '''"Tiết kiệm pin – tắt nếu muốn gọi cả khi khoá màn hình"'''),
    ('''                    "Nơi ồn gọi không được? Bong bóng nhỏ nổi trên mọi màn hình (kể cả lúc đang ôn tập) – chạm để hỏi ngay. " +
                        "Kéo thả tự do, thả ra tự hít vào mép; kéo xuống ✕ để ẩn. Tự tắt cùng chế độ chờ.",''',
     '''                    "Nổi trên mọi màn hình – chạm để hỏi, kéo xuống ✕ để ẩn.",'''),
    ('''"Chạy ngầm liên tục tốn pin, nên HUY FAN chỉ nghe khi ông bật. Chọn cách bật tiện nhất:"''',
     '''"Chọn cách bật HUY FAN tiện nhất:"'''),
])
print('ok')
