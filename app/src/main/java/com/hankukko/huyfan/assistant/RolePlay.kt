package com.hankukko.huyfan.assistant

import com.hankukko.huyfan.ai.ReplyParser
import org.json.JSONArray
import org.json.JSONObject

/** Hội thoại nhập vai bằng tiếng Hàn: AI đóng 1 vai, người học trả lời, AI sửa lỗi + gợi ý câu tiếp. */
object RolePlay {

    data class Scenario(val id: String, val emoji: String, val title: String, val aiRole: String, val setting: String)

    val SCENARIOS = listOf(
        Scenario("restaurant", "🍜", "Gọi món ở quán ăn", "nhân viên phục vụ quán ăn Hàn Quốc", "Khách vào quán, hỏi món, gọi món, gọi thêm, thanh toán."),
        Scenario("cafe", "☕", "Quán cà phê", "nhân viên quán cà phê", "Khách gọi đồ uống, chọn nóng/đá, size, uống tại chỗ hay mang đi, thanh toán."),
        Scenario("shopping", "🛍️", "Mua sắm quần áo", "nhân viên cửa hàng quần áo", "Khách hỏi giá, hỏi size/màu, thử đồ, xin giảm giá, thanh toán."),
        Scenario("direction", "🧭", "Hỏi đường", "người đi đường ở Seoul", "Khách du lịch hỏi đường tới ga tàu điện ngầm và địa điểm gần đó."),
        Scenario("taxi", "🚕", "Đi taxi", "tài xế taxi", "Khách nói điểm đến, hỏi mất bao lâu, xin dừng ở đâu, trả tiền."),
        Scenario("friend", "🙋", "Làm quen bạn mới", "một người bạn Hàn mới quen, cùng tuổi, thân thiện", "Hai người làm quen: tên, quê, công việc/học, sở thích, hẹn đi chơi."),
        Scenario("hospital", "🏥", "Đi khám bệnh", "bác sĩ phòng khám", "Bệnh nhân mô tả triệu chứng, bác sĩ hỏi thêm, dặn dò, kê thuốc."),
        Scenario("interview", "💼", "Phỏng vấn làm thêm", "quản lý cửa hàng tiện lợi", "Phỏng vấn xin việc làm thêm: giới thiệu, thời gian làm được, kinh nghiệm, lương."),
        Scenario("hotel", "🏨", "Nhận phòng khách sạn", "lễ tân khách sạn", "Nhận phòng, hỏi giờ ăn sáng, mật khẩu wifi, gửi hành lý, trả phòng."),
    )

    enum class Level(val label: String, val desc: String) {
        BEGINNER("Sơ cấp", "sơ cấp (TOPIK 1–2): câu rất ngắn, từ thông dụng, ngữ pháp cơ bản"),
        INTERMEDIATE("Trung cấp", "trung cấp (TOPIK 3–4): câu tự nhiên như người bản xứ, ngữ pháp trung cấp"),
    }

    data class Phrase(val ko: String, val vi: String)

    data class AiTurn(
        val ko: String,
        val vi: String,
        val hasError: Boolean,
        val fixedKo: String,
        val explainVi: String,
        val suggestions: List<Phrase>,
        val vocab: List<Phrase>,
        val done: Boolean,
    )

    fun system(sc: Scenario, level: Level, userName: String): String = """
Bạn là bạn diễn giúp ${userName.ifBlank { "người học" }} (người Việt) luyện NÓI tiếng Hàn qua đóng vai.
Tình huống: ${sc.title}. ${sc.setting}
Vai của bạn: ${sc.aiRole}. Người học đóng vai còn lại (khách / người đối diện).
Trình độ: ${level.desc}. Dùng đuôi lịch sự 해요체 (trừ khi vai là bạn bè đã thân).

QUY TẮC TRẢ LỜI (chỉ trả JSON):
- reply_ko: lời thoại tiếp theo của bạn bằng tiếng Hàn, 1–2 câu ngắn, tự nhiên, đúng vai. Thường kết thúc bằng 1 câu hỏi để người học nói tiếp.
- reply_vi: dịch reply_ko sang tiếng Việt tự nhiên.
- Câu của người học có thể do máy nghe giọng nói (có thể sai chính tả nhẹ, thiếu dấu câu) – chỉ sửa lỗi ngữ pháp/từ vựng/cách nói chưa tự nhiên thật sự.
- Nếu người học nói/viết bằng tiếng Việt: hiểu ý, vẫn đáp lại theo vai, và đưa câu tiếng Hàn tương ứng vào fixed_ko (has_error = true, explain_vi: "Câu này nói bằng tiếng Hàn là…").
- has_error: true nếu câu tiếng Hàn vừa rồi của người học sai hoặc không tự nhiên. fixed_ko: cách nói đúng và tự nhiên. explain_vi: giải thích 1 câu ngắn bằng tiếng Việt. Lượt mở đầu thì has_error = false.
- suggestions: đúng 2 câu mẫu ngắn người học có thể nói tiếp (ko + vi), đúng trình độ.
- vocab: 0–2 từ/cụm từ đáng học trong lượt này (ko + vi).
- done: true khi cuộc hội thoại đã xong một cách tự nhiên (thường sau 8–12 lượt) – khi đó reply_ko là lời chào kết thúc, suggestions để trống.
- Tuyệt đối không dùng romaja, không giải thích dài.

ĐỊNH DẠNG JSON (đúng các khoá sau):
{"has_error":false,"fixed_ko":"","explain_vi":"","reply_ko":"","reply_vi":"","suggestions":[{"ko":"","vi":""},{"ko":"","vi":""}],"vocab":[{"ko":"","vi":""}],"done":false}
""".trim()

    fun schema(): JSONObject {
        fun str() = JSONObject().put("type", "STRING")
        val phrase = JSONObject().put("type", "OBJECT")
            .put("properties", JSONObject().put("ko", str()).put("vi", str()))
            .put("required", JSONArray(listOf("ko", "vi")))
        return JSONObject().put("type", "OBJECT")
            .put(
                "properties", JSONObject()
                    .put("reply_ko", str()).put("reply_vi", str())
                    .put("has_error", JSONObject().put("type", "BOOLEAN"))
                    .put("fixed_ko", str()).put("explain_vi", str())
                    .put("suggestions", JSONObject().put("type", "ARRAY").put("items", phrase))
                    .put("vocab", JSONObject().put("type", "ARRAY").put("items", phrase))
                    .put("done", JSONObject().put("type", "BOOLEAN"))
            )
            .put("required", JSONArray(listOf("reply_ko", "reply_vi", "has_error", "fixed_ko", "explain_vi", "suggestions", "vocab", "done")))
            .put("propertyOrdering", JSONArray(listOf("has_error", "fixed_ko", "explain_vi", "reply_ko", "reply_vi", "suggestions", "vocab", "done")))
    }

    fun parse(raw: String): AiTurn {
        val o = ReplyParser.extractJson(raw.trim()) ?: return AiTurn(raw.trim().take(400), "", false, "", "", emptyList(), emptyList(), false)
        fun phrases(key: String): List<Phrase> {
            val a = o.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
                .map { Phrase(it.optString("ko").trim(), it.optString("vi").trim()) }
                .filter { it.ko.isNotBlank() }
        }
        val fixed = o.optString("fixed_ko").trim()
        return AiTurn(
            ko = o.optString("reply_ko").trim(),
            vi = o.optString("reply_vi").trim(),
            hasError = o.optBoolean("has_error", false) && fixed.isNotBlank(),
            fixedKo = fixed,
            explainVi = o.optString("explain_vi").trim(),
            suggestions = phrases("suggestions").take(3),
            vocab = phrases("vocab").take(3),
            done = o.optBoolean("done", false),
        )
    }
}
