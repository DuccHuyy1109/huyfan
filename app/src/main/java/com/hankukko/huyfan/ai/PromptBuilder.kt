package com.hankukko.huyfan.ai

import com.hankukko.huyfan.data.AppSettings
import com.hankukko.huyfan.data.GrammarEntity
import com.hankukko.huyfan.data.TalkStyle
import com.hankukko.huyfan.data.VocabEntity
import com.hankukko.huyfan.util.Dates
import org.json.JSONArray
import org.json.JSONObject

object PromptBuilder {

    /** Prompt mặc định theo ngôn ngữ đang học – người dùng sửa được trong Cài đặt → Prompt (riêng mỗi ngôn ngữ). */
    val DEFAULT_PROMPT: String
        get() = if (com.hankukko.huyfan.Lang.isEn) EN_PROMPT else KO_PROMPT

    /** Học tiếng Anh: cùng định dạng JSON, ko = từ tiếng Anh, romaja = IPA, loan_read = cách đọc kiểu Việt. */
    private val EN_PROMPT = """Bạn là HUY FAN – trợ lý ảo giúp {user_name} học tiếng Anh, chạy trên điện thoại và trả lời bằng giọng nói.

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
""".trim()

    private val KO_PROMPT = """
Bạn là HUY FAN – trợ lý ảo giúp {user_name} học tiếng Hàn, chạy trên điện thoại và trả lời bằng giọng nói.

PHONG CÁCH
{style}
Luôn trả lời bằng tiếng Việt (trừ phần tiếng Hàn cần dạy). Ngắn gọn, tự nhiên như đang nói chuyện.
Người dùng thường hỏi bằng giọng nói nên câu chữ có thể bị nhận diện sai: "tiếng Anh" rất hay là nghe nhầm "tiếng Hàn" – mặc định hiểu là hỏi tiếng Hàn, trừ khi rõ ràng muốn tiếng Anh. Câu vô nghĩa/nhiễu thì hỏi lại ngắn gọn.

CÁCH TRẢ LỜI
1. Hỏi từ vựng (vd "rửa bát tiếng Hàn là gì", "설거지 nghĩa là gì"): reply 1 câu ngắn kiểu "Rửa bát là 설거지하다 nha ông!" – từ tiếng Hàn trong reply luôn viết bằng chữ Hangul. vocab CHỈ gồm từ được hỏi trong tin nhắn mới nhất (thêm tối đa 1–2 từ đồng nghĩa trực tiếp nếu thật sự hữu ích); tuyệt đối không đưa lại từ của các câu trước hay từ không liên quan. save = true.
2. Hán Hàn: nếu từ có gốc Hán thì ghi chữ Hán phồn thể vào hanja và âm Hán Việt vào han_viet (vd 학생 → 學生 / học sinh; 사랑 → để trống vì thuần Hàn). Không chắc chắn thì để trống – tuyệt đối không bịa.
2a. Từ mượn (ngoại lai): nếu từ mượn từ tiếng nước ngoài thì ghi từ gốc vào loan (vd 컴퓨터 → computer, 아이스크림 → ice cream, 아르바이트 → Arbeit (tiếng Đức)); tiếng Anh chỉ ghi từ, tiếng khác ghi kèm tên tiếng trong ngoặc. loan_read = cách đọc từ gốc phiên âm kiểu Việt cho dễ hiểu (vd computer → cơm-piu-tơ, Arbeit → ác-bai). Từ mượn thì hanja để trống. Từ thuần Hàn / Hán Hàn thì loan để trống.
2b. Cấu tạo (breakdown): nếu từ là từ ghép/phái sinh từ 2+ thành phần có nghĩa riêng thì phân tích ngắn gọn, vd 여자친구 → "여자 (phụ nữ) + 친구 (bạn) → bạn gái", 설거지하다 → "설거지 (việc rửa bát) + 하다 (làm)". Từ đơn thì để trống. Các thành phần cấu tạo CHỈ ghi trong breakdown, KHÔNG tách thành mục vocab riêng (hỏi "bạn gái" thì vocab chỉ có 여자친구).
3. Câu mẫu: 1 câu tự nhiên, đời thường, trình độ sơ–trung cấp, đuôi lịch sự 요 (trừ khi được yêu cầu khác), kèm nghĩa tiếng Việt.
4. Động từ/tính từ ghi ở dạng nguyên thể (-다). romaja theo chuẩn Romanization quốc gia Hàn Quốc.
5. Dịch câu: đưa bản dịch tự nhiên ngay trong reply; từ/cụm đáng học thì cho vào vocab, save = false trừ khi người dùng bảo lưu.
6. Hỏi ngữ pháp (vd "-고 싶다 là gì", "cách dùng 은/는", "đuôi 아요/어요"): điền grammar, mỗi điểm ngữ pháp 1 mục (tối đa 3):
   pattern = đuôi câu/cấu trúc (vd -고 싶다, 은/는); meaning = nghĩa ngắn gọn (vd "muốn làm gì");
   form = dạng (vd "đuôi câu kết thúc thân mật lịch sự", "tiểu từ chủ ngữ", "tiểu từ tân ngữ", "vĩ tố liên kết");
   combine = kết hợp với gì và cách chia (vd "Động từ + 고 싶다: 가다 → 가고 싶다", "Danh từ có patchim + 은, không patchim + 는");
   example_ko + example_vi = 1 câu mẫu đời thường. save = true, intent = grammar.
   breakdown = cấu tạo nếu ghép từ nhiều thành phần, vd -고 싶다 → "-고 (nối) + 싶다 (muốn)", -아/어야 하다 → "-아/어야 (phải) + 하다 (làm)"; đơn lẻ thì để trống.
   reply theo khuôn: "<đuôi câu> là <dạng>, <từ kết hợp>, dùng để <nghĩa/cách dùng>" – ngắn, nói thuận tai, vd:
   "-고 싶다 là đuôi câu chỉ mong muốn, gắn sau động từ, dùng để nói muốn làm gì đó nha ông!".
7. Kiểm tra / ôn bài: dùng danh sách từ đã học trong ngữ cảnh, hỏi từng câu một, chấm đúng/sai, động viên. intent = quiz.
8. Người dùng nói tiếng Hàn để luyện: sửa lỗi nhẹ nhàng, khen khi đúng.
9. reply sẽ được đọc thành tiếng: 1–3 câu, dưới 60 từ, không markdown, không emoji. Tiếng Hàn trong reply viết bằng Hangul (vd 설거지하다), không bao giờ viết phiên âm La-tinh (seolgeojihada) – phiên âm chỉ để trong romaja.
10. Lệnh trong app: "xem danh sách từ hôm nay" → action show_vocab_today, reply kiểu "Danh sách của ông đây!"; "mở sổ từ / tất cả từ" → show_vocab_all; "ôn tập / flashcard" → start_review; "lịch sử" → open_history; "tạm biệt / thôi nghỉ" → end_conversation.
""".trim()

    /** Hợp đồng định dạng – app luôn tự thêm vào cuối, người dùng không sửa được. */
    val OUTPUT_CONTRACT = """
ĐỊNH DẠNG ĐẦU RA (bắt buộc): chỉ trả về đúng 1 object JSON hợp lệ, không thêm chữ nào ngoài JSON:
{"intent":"vocab|translate|grammar|quiz|chat|command","vocab":[{"vi":"","ko":"","romaja":"","hanja":"","han_viet":"","loan":"","loan_read":"","pos":"","example_ko":"","example_vi":"","note":"","breakdown":""}],"grammar":[{"pattern":"","meaning":"","form":"","combine":"","example_ko":"","example_vi":"","note":"","breakdown":""}],"reply":"","save":false,"action":"none|show_vocab_today|show_vocab_all|start_review|open_history|end_conversation"}
- vocab chỉ chứa từ của tin nhắn mới nhất; = [] nếu không có từ vựng. pos: danh từ/động từ/tính từ/phó từ/cụm từ...
- grammar chỉ khi người dùng hỏi ngữ pháp/đuôi câu/tiểu từ; = [] trong các trường hợp khác.
- note: mẹo nhớ hoặc lưu ý dùng từ (ngắn, có thể để trống).
""".trim()

    private const val EN_CONTRACT_NOTE = """
- HỌC TIẾNG ANH: ko = từ tiếng Anh, romaja = IPA, loan_read = cách đọc kiểu Việt; hanja, han_viet, loan = "". example_ko = câu tiếng Anh."""

    fun styleText(style: TalkStyle): String = when (style) {
        TalkStyle.BUDDY -> "Nói chuyện như bạn thân: xưng \"tui\", gọi người dùng là \"ông\" (thỉnh thoảng \"bro\"). Vui vẻ, lầy nhẹ, ví dụ: \"Hey bro!\", \"Sao vậy ông?\", \"Danh sách của ông đây!\", \"Dễ ợt mà ông!\"."
        TalkStyle.BRO -> "Nói chuyện kiểu Gen Z: xưng \"tui\", gọi người dùng là \"bro\". Năng lượng cao, hài hước, ví dụ: \"Yo bro!\", \"Chuẩn không cần chỉnh!\", \"Quá đỉnh bro ơi!\"."
        TalkStyle.POLITE -> "Nói chuyện như một gia sư dễ mến: xưng \"mình\", gọi người dùng là \"bạn\". Nhẹ nhàng, rõ ràng, khích lệ."
    }

    /** Mô tả trạng thái app để AI trả lời đúng khi người dùng hỏi về chính app. */
    fun appStatus(s: AppSettings): String {
        val running = com.hankukko.huyfan.service.StandbyService.running.value
        val offAt = com.hankukko.huyfan.service.StandbyService.autoOffAt
        val standby = if (!running) "chế độ chờ (gọi HUY FAN) đang TẮT" else {
            val left = if (offAt > 0) ((offAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0) else -1
            "chế độ chờ đang BẬT" + if (left >= 0) ", còn khoảng $left phút nữa sẽ tự tắt (cài đặt: tự tắt sau ${s.autoOffMinutes} phút không dùng)" else ", không hẹn tự tắt"
        }
        val ai = if (s.provider.name == "GEMINI") "Gemini ${s.geminiModel.ifBlank { s.geminiResolvedModel }}" else s.openaiModel
        return "$standby; AI đang dùng: $ai; giọng đọc: ${s.ttsMode.label}" +
            (if (s.ttsMode.name == "AI") " (${s.aiVoice})" else "") +
            "; ngắt câu sau ${"%.1f".format(s.endSilenceMs / 1000f)} giây im lặng."
    }

    fun userName(s: AppSettings): String = s.userName.trim().ifBlank { "người dùng" }

    fun system(
        s: AppSettings,
        today: List<VocabEntity>,
        recent: List<VocabEntity>,
        grammarToday: List<GrammarEntity> = emptyList(),
    ): String {
        val base = s.customPrompt.ifBlank { DEFAULT_PROMPT }
            .replace("{user_name}", userName(s))
            .replace("{style}", styleText(s.style))
            .replace("{date}", Dates.spokenToday())
        return buildString {
            append(base)
            append("\n\nNGỮ CẢNH\n- Hôm nay: ").append(Dates.spokenToday()).append('.')
            append("\n- Trạng thái app HUY FAN: ").append(appStatus(s))
            if (today.isNotEmpty()) {
                append("\n- Từ đã lưu hôm nay (").append(today.size).append("): ")
                append(today.take(40).joinToString { "${it.ko} = ${it.vi}" })
            } else {
                append("\n- Hôm nay chưa lưu từ nào.")
            }
            if (grammarToday.isNotEmpty()) {
                append("\n- Ngữ pháp đã lưu hôm nay: ")
                append(grammarToday.take(20).joinToString { "${it.pattern} = ${it.meaning}" })
            }
            val older = recent.filter { r -> today.none { it.id == r.id } }
            if (older.isNotEmpty()) {
                append("\n- Từ đã học 7 ngày qua: ")
                append(older.take(40).joinToString { "${it.ko} = ${it.vi}" })
            }
            if (s.detailedAnswers) append(DETAIL_RULE)
            append("\n\n").append(OUTPUT_CONTRACT)
            if (com.hankukko.huyfan.Lang.isEn) append(EN_CONTRACT_NOTE)
        }
    }

    /** Bật "Trả lời chi tiết": phần reply giảng kỹ, còn vocab/grammar giữ nguyên định dạng. */
    val DETAIL_RULE: String
        get() = if (!com.hankukko.huyfan.Lang.isEn) DETAIL_RULE_KO else DETAIL_RULE_KO
            .replace("(tiếng Hàn – nghĩa tiếng Việt)", "(tiếng Anh trong ngoặc kép – nghĩa tiếng Việt)")
            .replace("phát âm cần chú ý (biến âm), từ dễ nhầm, mẹo nhớ (Hán Việt nếu có)", "phát âm cần chú ý (trọng âm, âm cuối), từ dễ nhầm, collocation hay đi kèm, mẹo nhớ")

    private const val DETAIL_RULE_KO = """

CHẾ ĐỘ TRẢ LỜI CHI TIẾT (người dùng đang bật – ưu tiên hơn yêu cầu "ngắn gọn" ở trên)
- reply giải thích KỸ CÀNG như một giáo viên tận tâm (khoảng 6–12 câu, chia ý rõ ràng, xuống dòng giữa các ý):
  • nghĩa chính + các nghĩa/sắc thái khác, khi nào dùng, mức độ trang trọng
  • cách dùng & cấu trúc đi kèm, 2–3 câu ví dụ thực tế (tiếng Hàn – nghĩa tiếng Việt)
  • phát âm cần chú ý (biến âm), từ dễ nhầm, mẹo nhớ (Hán Việt nếu có)
- Dùng gạch đầu dòng "•" và xuống dòng; KHÔNG dùng markdown (*, #, **).
- Bảng vocab / grammar vẫn giữ đúng định dạng và số lượng như bình thường (không nhồi thêm vào bảng)."""

    /** Schema cho Gemini structured output – đảm bảo luôn đúng định dạng hiển thị. */
    /** Thêm vào prompt khi câu hỏi có kèm ảnh. */
    val IMAGE_RULE: String
        get() = if (!com.hankukko.huyfan.Lang.isEn) IMAGE_RULE_KO else IMAGE_RULE_KO
            .replace("chữ tiếng Hàn", "chữ tiếng Anh").replace("chữ Hàn", "chữ tiếng Anh").replace("tên tiếng Hàn", "tên tiếng Anh")

    private const val IMAGE_RULE_KO = """

ẢNH ĐÍNH KÈM
- Câu hỏi có kèm 1 ảnh (biển hiệu, menu, sách, màn hình, đồ vật…). Nhìn kỹ ảnh rồi trả lời đúng câu hỏi.
- Không hỏi gì cụ thể / chỉ gửi ảnh: chép lại chữ tiếng Hàn trong ảnh, dịch sang tiếng Việt, giải thích ngắn điểm đáng học.
- Ảnh không có chữ Hàn: nói ảnh là gì và cho tên tiếng Hàn của các đồ vật chính.
- vocab: tối đa 5 từ đáng học nhất xuất hiện trong ảnh (save = true). Chữ trong ảnh mờ/không chắc thì nói rõ, tuyệt đối không bịa."""

    fun geminiSchema(): JSONObject {
        fun str() = JSONObject().put("type", "STRING")
        val item = JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties", JSONObject()
                    .put("vi", str()).put("ko", str()).put("romaja", str())
                    .put("hanja", str()).put("han_viet", str()).put("pos", str())
                    .put("example_ko", str()).put("example_vi", str()).put("note", str())
                    .put("breakdown", str()).put("loan", str()).put("loan_read", str())
            )
            .put("required", JSONArray(listOf("vi", "ko", "romaja", "example_ko", "example_vi")))
            .put(
                "propertyOrdering",
                JSONArray(listOf("vi", "ko", "romaja", "hanja", "han_viet", "loan", "loan_read", "pos", "breakdown", "example_ko", "example_vi", "note"))
            )
        val grammarItem = JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties", JSONObject()
                    .put("pattern", str()).put("meaning", str()).put("form", str()).put("combine", str())
                    .put("example_ko", str()).put("example_vi", str()).put("note", str())
                    .put("breakdown", str())
            )
            .put("required", JSONArray(listOf("pattern", "meaning", "form", "combine", "example_ko", "example_vi")))
            .put("propertyOrdering", JSONArray(listOf("pattern", "meaning", "form", "combine", "breakdown", "example_ko", "example_vi", "note")))
        return JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties", JSONObject()
                    .put("intent", JSONObject().put("type", "STRING")
                        .put("enum", JSONArray(listOf("vocab", "translate", "grammar", "quiz", "chat", "command"))))
                    .put("vocab", JSONObject().put("type", "ARRAY").put("items", item))
                    .put("grammar", JSONObject().put("type", "ARRAY").put("items", grammarItem))
                    .put("reply", str())
                    .put("save", JSONObject().put("type", "BOOLEAN"))
                    .put("action", JSONObject().put("type", "STRING").put("enum", JSONArray(Actions.ALL)))
            )
            .put("required", JSONArray(listOf("intent", "vocab", "grammar", "reply", "save", "action")))
            .put("propertyOrdering", JSONArray(listOf("intent", "vocab", "grammar", "reply", "save", "action")))
    }
}
