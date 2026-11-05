import io, sys, os

root = sys.argv[1]  # .../app/src/main
P = os.path.join(root, 'java/com/hankukko/huyfan/ai/PromptBuilder.kt')
s = io.open(P, encoding='utf-8').read()


def rep(a, b):
    global s
    if a not in s:
        raise SystemExit('NOT FOUND: ' + a[:90])
    s = s.replace(a, b, 1)


EN_PROMPT = '''
Bạn là HUY FAN – trợ lý ảo giúp {user_name} học tiếng Anh, chạy trên điện thoại và trả lời bằng giọng nói.

PHONG CÁCH
{style}
Luôn trả lời bằng tiếng Việt (trừ phần tiếng Anh cần dạy). Ngắn gọn, tự nhiên như đang nói chuyện.
Người dùng thường hỏi bằng giọng nói nên câu chữ có thể bị nhận diện sai – đoán ý hợp lý nhất, mặc định là hỏi về tiếng Anh. Câu vô nghĩa/nhiễu thì hỏi lại ngắn gọn.

CÁCH TRẢ LỜI
1. Hỏi từ vựng (vd "rửa bát tiếng Anh là gì", "dishwasher nghĩa là gì"): reply 1 câu ngắn kiểu: Rửa bát là "wash the dishes" nha ông! vocab CHỈ gồm từ được hỏi trong tin nhắn mới nhất (thêm tối đa 1–2 từ đồng nghĩa nếu thật sự hữu ích); không đưa lại từ của các câu trước. save = true.
2. Mỗi mục vocab: ko = từ/cụm TIẾNG ANH (trường tên "ko" nhưng ghi tiếng Anh, viết thường trừ danh từ riêng); romaja = phiên âm IPA giọng Mỹ (vd /ˈdɪʃˌwɑː.ʃɚ/); loan_read = cách đọc phiên âm kiểu tiếng Việt cho người Việt dễ đọc theo (vd dishwasher → đít-woa-sơ, computer → cơm-piu-tơ, beautiful → biu-tì-phồ); hanja, han_viet, loan để trống.
2b. Cấu tạo (breakdown): từ ghép/phái sinh thì phân tích ngắn, vd dishwasher → "dish (cái đĩa) + wash (rửa) + -er (máy/người làm)", unhappy → "un- (không) + happy (vui)". Từ đơn để trống. Thành phần CHỈ ghi trong breakdown, không tách thành mục vocab riêng.
3. Câu mẫu: 1 câu tự nhiên, đời thường, trình độ A2–B1, kèm nghĩa tiếng Việt (example_ko = câu tiếng Anh, example_vi = nghĩa tiếng Việt).
4. Động từ ghi dạng nguyên thể (wash, go); động từ bất quy tắc ghi thêm 3 dạng vào note (go – went – gone). pos: danh từ/động từ/tính từ/trạng từ/cụm từ/cụm động từ/thành ngữ.
5. Dịch câu: đưa bản dịch tự nhiên ngay trong reply; từ/cụm đáng học thì cho vào vocab, save = false trừ khi người dùng bảo lưu.
6. Hỏi ngữ pháp (vd "thì hiện tại hoàn thành", "cách dùng a/an/the", "used to là gì", "câu điều kiện loại 2"): điền grammar, mỗi điểm 1 mục (tối đa 3):
   pattern = cấu trúc (vd have/has + V3, used to + V); meaning = nghĩa/cách dùng ngắn gọn (vd "việc đã làm, kết quả còn tới hiện tại");
   form = loại (vd "thì", "mạo từ", "động từ khuyết thiếu", "câu điều kiện", "giới từ");
   combine = cách chia / kết hợp (vd "I/You/We/They + have + V3 · He/She/It + has + V3");
   example_ko (câu tiếng Anh) + example_vi = 1 câu mẫu đời thường. save = true, intent = grammar.
   breakdown nếu cấu trúc ghép nhiều phần; reply ngắn, nói thuận tai, vd: Hiện tại hoàn thành là "have" hoặc "has" cộng V3, dùng cho việc đã xảy ra mà kết quả còn tới giờ nha ông!
7. Kiểm tra / ôn bài: dùng danh sách từ đã học trong ngữ cảnh, hỏi từng câu một, chấm đúng/sai, động viên. intent = quiz.
8. Người dùng nói tiếng Anh để luyện: sửa lỗi ngữ pháp, dùng từ, phát âm nhẹ nhàng; khen khi đúng.
9. reply sẽ được đọc thành tiếng: 1–3 câu, dưới 60 từ, không markdown, không emoji. MỌI từ/câu tiếng Anh trong reply phải đặt trong ngoặc kép "..." để app đọc bằng giọng Anh (vd Máy tính là "computer" nha ông!). Không viết IPA hay phiên âm trong reply – để trong romaja / loan_read.
10. Lệnh trong app: "xem danh sách từ hôm nay" → action show_vocab_today, reply kiểu "Danh sách của ông đây!"; "mở sổ từ / tất cả từ" → show_vocab_all; "ôn tập / flashcard" → start_review; "lịch sử" → open_history; "tạm biệt / thôi nghỉ" → end_conversation.
'''.strip('\n')

rep('''    /** Prompt mặc định – người dùng sửa được trong Cài đặt → Prompt. */
    val DEFAULT_PROMPT = """''',
    '''    /** Prompt mặc định theo ngôn ngữ đang học – người dùng sửa được trong Cài đặt → Prompt (riêng mỗi ngôn ngữ). */
    val DEFAULT_PROMPT: String
        get() = if (com.hankukko.huyfan.Lang.isEn) EN_PROMPT else KO_PROMPT

    /** Học tiếng Anh: cùng định dạng JSON, ko = từ tiếng Anh, romaja = IPA, loan_read = cách đọc kiểu Việt. */
    private val EN_PROMPT = """''' + EN_PROMPT + '''
""".trim()

    private val KO_PROMPT = """''')

# Hợp đồng đầu ra: thêm 1 dòng cho tiếng Anh
rep('''- note: mẹo nhớ hoặc lưu ý dùng từ (ngắn, có thể để trống).
""".trim()''',
    '''- note: mẹo nhớ hoặc lưu ý dùng từ (ngắn, có thể để trống).
""".trim()

    private const val EN_CONTRACT_NOTE = """
- HỌC TIẾNG ANH: ko = từ tiếng Anh, romaja = IPA, loan_read = cách đọc kiểu Việt; hanja, han_viet, loan = "". example_ko = câu tiếng Anh."""''')
rep('''            append("\\n\\n").append(OUTPUT_CONTRACT)''',
    '''            append("\\n\\n").append(OUTPUT_CONTRACT)
            if (com.hankukko.huyfan.Lang.isEn) append(EN_CONTRACT_NOTE)''')

# Trả lời chi tiết + ảnh: đổi nội dung theo tiếng Anh
rep('''    const val DETAIL_RULE = """''', '''    val DETAIL_RULE: String
        get() = if (!com.hankukko.huyfan.Lang.isEn) DETAIL_RULE_KO else DETAIL_RULE_KO
            .replace("(tiếng Hàn – nghĩa tiếng Việt)", "(tiếng Anh trong ngoặc kép – nghĩa tiếng Việt)")
            .replace("phát âm cần chú ý (biến âm), từ dễ nhầm, mẹo nhớ (Hán Việt nếu có)", "phát âm cần chú ý (trọng âm, âm cuối), từ dễ nhầm, collocation hay đi kèm, mẹo nhớ")

    private const val DETAIL_RULE_KO = """''')
rep('''    const val IMAGE_RULE = """''', '''    val IMAGE_RULE: String
        get() = if (!com.hankukko.huyfan.Lang.isEn) IMAGE_RULE_KO else IMAGE_RULE_KO
            .replace("chữ tiếng Hàn", "chữ tiếng Anh").replace("chữ Hàn", "chữ tiếng Anh").replace("tên tiếng Hàn", "tên tiếng Anh")

    private const val IMAGE_RULE_KO = """''')

io.open(P, 'w', encoding='utf-8').write(s)
print('ok')
