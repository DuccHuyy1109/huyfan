package com.hankukko.huyfan.assistant

import com.hankukko.huyfan.ai.ReplyParser
import com.hankukko.huyfan.ai.Turn
import org.json.JSONArray
import org.json.JSONObject

/**
 * Phiên dịch hội thoại Việt ⇄ Hàn: mỗi lượt nhận câu máy nghe được (có thể sai),
 * AI sửa lại cho đúng theo ngữ cảnh rồi dịch sang ngôn ngữ còn lại.
 */
object Interpreter {

    enum class Side(val lang: String, val label: String, val flag: String, val who: String) {
        VI(LANG_VI, "Tiếng Việt", "🇻🇳", "Bạn"),
        KO(LANG_KO, "한국어", "🇰🇷", "상대방");

        val other: Side get() = if (this == VI) KO else VI
    }

    data class Result(val fixed: String, val changed: Boolean, val translation: String, val note: String)

    /** 1 lượt đã dịch (dùng làm ngữ cảnh cho lượt sau). */
    data class Line(val side: Side, val text: String, val translation: String)

    const val SYSTEM = """Bạn là phiên dịch viên Việt ⇄ Hàn chuyên nghiệp, đứng giữa 2 người đang nói chuyện trực tiếp.
Mỗi lượt bạn nhận 1 câu do máy nhận dạng giọng nói ghi lại – có thể sai chính tả, thiếu dấu tiếng Việt, nghe nhầm từ đồng âm, thiếu dấu câu.

LÀM 2 VIỆC:
1. fixed – sửa câu gốc cho đúng (GIỮ NGUYÊN ngôn ngữ gốc): thêm dấu, sửa từ nghe nhầm dựa vào ngữ cảnh cuộc nói chuyện, viết hoa tên riêng, thêm dấu câu. Giữ nguyên ý, không thêm thắt. Câu đã đúng thì giữ nguyên.
   Ví dụ: "huy ăn cóm vè rau" → "Huy ăn cơm và rau." ; "저는 베트남에서 왔어오" → "저는 베트남에서 왔어요."
2. translation – dịch câu đã sửa sang ngôn ngữ còn lại, tự nhiên như người bản xứ nói, ngắn gọn, giữ đúng sắc thái.
   Sang tiếng Hàn: dùng đuôi lịch sự 해요체 (trừ khi câu gốc rõ ràng thân mật giữa bạn bè).
   Sang tiếng Việt: tự nhiên, xưng hô hợp ngữ cảnh.

NGƯỜI NÓI NHỎ / NGẮT TỪNG TỪ: người Hàn hay nói nhỏ, ngắt quãng, có khi chỉ 1–2 từ (네, 응, 아니요, 진짜요?, 그래요, 잠깐만요 / ừ, dạ, vâng, không…).
   Vẫn LUÔN dịch, không bỏ trống. Câu cụt hoặc nghe chưa trọn → đoán ý hợp lý nhất theo mấy câu trước trong cuộc nói chuyện
   (vd câu trước hỏi "Anh ăn chưa?" mà giờ nghe "네" → "Dạ, rồi ạ."). Nghe nhầm từ ngắn (vd "내"/"네") → sửa theo ngữ cảnh.

changed = true nếu fixed khác câu nghe được về chữ (không tính viết hoa / dấu câu).
note = ghi chú ngắn bằng tiếng Việt (≤ 12 từ) chỉ khi có từ đa nghĩa / thành ngữ cần lưu ý; thường để trống.
Không dùng romaja. Chỉ trả JSON đúng định dạng:
{"fixed":"","changed":false,"translation":"","note":""}"""

    fun schema(): JSONObject {
        fun str() = JSONObject().put("type", "STRING")
        return JSONObject().put("type", "OBJECT")
            .put(
                "properties", JSONObject()
                    .put("fixed", str()).put("changed", JSONObject().put("type", "BOOLEAN"))
                    .put("translation", str()).put("note", str())
            )
            .put("required", JSONArray(listOf("fixed", "changed", "translation", "note")))
            .put("propertyOrdering", JSONArray(listOf("fixed", "changed", "translation", "note")))
    }

    /** Gói ngữ cảnh gần đây + câu mới thành 1 lượt hỏi (không phụ thuộc lịch sử chat). */
    fun request(history: List<Line>, side: Side, heard: String): List<Turn> {
        val text = buildString {
            val recent = history.takeLast(6)
            if (recent.isNotEmpty()) {
                append("[Ngữ cảnh gần đây]\n")
                recent.forEach { append(it.side.flag).append(' ').append(it.text).append("  →  ").append(it.translation).append('\n') }
                append('\n')
            }
            append("[Câu mới · ")
            append(if (side == Side.VI) "tiếng Việt → tiếng Hàn" else "tiếng Hàn → tiếng Việt")
            append("]\n").append(heard.trim())
        }
        return listOf(Turn(Turn.USER, text))
    }

    fun parse(raw: String, heard: String): Result {
        val o = ReplyParser.extractJson(raw.trim()) ?: return Result(heard, false, raw.trim().take(300), "")
        val fixed = o.optString("fixed").trim().ifBlank { heard.trim() }
        fun norm(s: String) = s.lowercase().replace(Regex("[\\s.,!?~·…]"), "")
        return Result(
            fixed = fixed,
            changed = o.optBoolean("changed", false) && norm(fixed) != norm(heard),
            translation = o.optString("translation").trim(),
            note = o.optString("note").trim(),
        )
    }
}
