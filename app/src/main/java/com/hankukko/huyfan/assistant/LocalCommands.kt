package com.hankukko.huyfan.assistant

import com.hankukko.huyfan.ai.Actions
import com.hankukko.huyfan.ai.AssistantReply
import com.hankukko.huyfan.data.TalkStyle
import java.text.Normalizer

/** Lệnh hay dùng xử lý ngay trên máy – không cần chờ AI, phản hồi tức thì. */
object LocalCommands {

    private fun norm(s: String): String {
        val noMarks = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('đ', 'd')
        return noMarks.replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
    }

    private val endRe = Regex("^(ok |thoi |vay )?(tam biet|bye|bai bai|bye bye|ket thuc|dung lai|thoi nghi|thoi khong hoi nua|nghi thoi|het roi|thoi duoc roi|cam on nhe thoi)( nhe| nha| huy fan| huy phan)?$")
    private val listWords = Regex("(danh sach|so tu|list)")
    private val todayWords = Regex("(hom nay)")
    private val allWords = Regex("(tat ca|toan bo|mo so tu|so tu vung)")
    private val reviewRe = Regex("(flash ?card|mo on tap|lat the|on tap the)")
    private val historyRe = Regex("^(mo |xem )?(lich su)( chat| hoi| tro chuyen)?$")
    // Chỉ lệnh bật/tắt chế độ; "giải thích chi tiết hơn" vẫn để AI giảng kỹ câu vừa hỏi
    private val detailOnRe = Regex("^((bat |mo )?(che do )?tra loi (chi tiet|ki cang|ky cang)( hon)?|(bat |mo )che do (tra loi )?chi tiet)( nhe| nha| di)?$")
    private val detailOffRe = Regex("^(tat che do chi tiet|(bat |mo )?(che do )?(tra loi )?(ngan gon|ngan thoi|ngan lai))( nhe| nha| di)?$")

    fun match(text: String, style: TalkStyle): AssistantReply? {
        val q = norm(text)
        if (q.isEmpty()) return null
        val you = when (style) {
            TalkStyle.BUDDY -> "ông"
            TalkStyle.BRO -> "bro"
            TalkStyle.POLITE -> "bạn"
        }
        return when {
            endRe.matches(q) -> AssistantReply(
                reply = listOf(
                    "Ok $you, cần gì cứ gọi HUY FAN nha!",
                    "Bye $you, nhớ ôn bài đó nha!",
                    "Ok, nghỉ thôi. 화이팅!",
                ).random(),
                intent = "command", action = Actions.END,
            )
            listWords.containsMatchIn(q) && todayWords.containsMatchIn(q) && q.length < 60 -> AssistantReply(
                reply = "Danh sách của $you đây!", intent = "command", action = Actions.SHOW_VOCAB_TODAY,
            )
            listWords.containsMatchIn(q) && allWords.containsMatchIn(q) && q.length < 60 -> AssistantReply(
                reply = "Sổ từ vựng của $you đây!", intent = "command", action = Actions.SHOW_VOCAB_ALL,
            )
            reviewRe.containsMatchIn(q) && q.length < 50 -> AssistantReply(
                reply = "Lật thẻ ôn bài thôi $you!", intent = "command", action = Actions.START_REVIEW,
            )
            detailOnRe.matches(q) -> AssistantReply(
                reply = "Ok $you, từ giờ tui giải thích chi tiết, kỹ càng nha!", intent = "command", action = Actions.DETAIL_ON,
            )
            detailOffRe.matches(q) -> AssistantReply(
                reply = "Ok $you, tui trả lời ngắn gọn lại nha!", intent = "command", action = Actions.DETAIL_OFF,
            )
            historyRe.matches(q) -> AssistantReply(
                reply = "Lịch sử hỏi đáp của $you đây!", intent = "command", action = Actions.OPEN_HISTORY,
            )
            else -> null
        }
    }
}
