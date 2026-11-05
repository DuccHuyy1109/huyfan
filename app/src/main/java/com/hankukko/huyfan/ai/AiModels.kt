package com.hankukko.huyfan.ai

import org.json.JSONArray
import org.json.JSONObject

data class VocabItem(
    val vi: String,
    val ko: String,
    val romaja: String = "",
    val hanja: String = "",
    val hanViet: String = "",
    val pos: String = "",
    val exampleKo: String = "",
    val exampleVi: String = "",
    val note: String = "",
    /** Cấu tạo từ ghép, vd "여자 (phụ nữ) + 친구 (bạn) → bạn gái" */
    val breakdown: String = "",
    /** Từ mượn nước ngoài: từ gốc, vd 컴퓨터 → "computer" */
    val loan: String = "",
    /** Cách đọc từ gốc kiểu Việt, vd computer → "cơm-piu-tơ" */
    val loanRead: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("vi", vi).put("ko", ko).put("romaja", romaja)
        .put("hanja", hanja).put("han_viet", hanViet).put("pos", pos)
        .put("example_ko", exampleKo).put("example_vi", exampleVi).put("note", note)
        .put("breakdown", breakdown).put("loan", loan).put("loan_read", loanRead)

    companion object {
        private fun JSONObject.str(vararg keys: String): String {
            for (k in keys) {
                val v = optString(k, "").trim()
                if (v.isNotEmpty() && v != "null" && v != "-" && v != "—") return v
            }
            return ""
        }

        fun fromJson(o: JSONObject) = VocabItem(
            vi = o.str("vi", "viet", "vietnamese", "meaning"),
            ko = o.str("ko", "korean", "word", "hangul"),
            romaja = o.str("romaja", "romanization", "pronunciation"),
            // Chỉ giữ chữ Hán (AI hay chèn "-", "·" hoặc chữ Latin như 補助battery)
            hanja = o.str("hanja", "han_han", "chinese").replace(Regex("[^\u3400-\u4DBF\u4E00-\u9FFF\uF900-\uFAFF,/ ]+"), "")
                .replace(Regex(" +"), " ").trim(' ', ',', '/'),
            hanViet = o.str("han_viet", "hanviet", "hanViet", "sino_vietnamese"),
            pos = o.str("pos", "type", "part_of_speech"),
            exampleKo = o.str("example_ko", "exampleKo", "example"),
            exampleVi = o.str("example_vi", "exampleVi", "example_translation"),
            note = o.str("note", "notes", "tip"),
            breakdown = o.str("breakdown", "composition", "cau_tao"),
            loan = o.str("loan", "loanword", "english", "origin"),
            loanRead = o.str("loan_read", "loanRead", "loan_reading"),
        )

        fun listFromJson(s: String): List<VocabItem> = runCatching {
            val arr = JSONArray(s)
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::fromJson) }
                .filter { it.ko.isNotBlank() }
        }.getOrDefault(emptyList())

        fun listToJson(list: List<VocabItem>): String =
            JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
    }
}

/** Điểm ngữ pháp AI trả về: Đuôi câu | Nghĩa | Dạng | Từ kết hợp | Câu mẫu */
data class GrammarItem(
    val pattern: String,
    val meaning: String,
    val form: String = "",
    val combine: String = "",
    val exampleKo: String = "",
    val exampleVi: String = "",
    val note: String = "",
    /** Cấu tạo, vd "-고 (nối) + 싶다 (muốn)" */
    val breakdown: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("pattern", pattern).put("meaning", meaning).put("form", form).put("combine", combine)
        .put("example_ko", exampleKo).put("example_vi", exampleVi).put("note", note)
        .put("breakdown", breakdown)

    companion object {
        private fun JSONObject.str(vararg keys: String): String {
            for (k in keys) {
                val v = optString(k, "").trim()
                if (v.isNotEmpty() && v != "null") return v
            }
            return ""
        }

        fun fromJson(o: JSONObject) = GrammarItem(
            pattern = o.str("pattern", "ending", "grammar", "structure"),
            meaning = o.str("meaning", "vi", "nghia"),
            form = o.str("form", "type", "dang"),
            combine = o.str("combine", "usage", "attach", "ket_hop"),
            exampleKo = o.str("example_ko", "exampleKo", "example"),
            exampleVi = o.str("example_vi", "exampleVi"),
            note = o.str("note", "tip"),
            breakdown = o.str("breakdown", "composition", "cau_tao"),
        )

        fun listFromJson(s: String): List<GrammarItem> = runCatching {
            val arr = JSONArray(s)
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::fromJson) }
                .filter { it.pattern.isNotBlank() }
        }.getOrDefault(emptyList())

        fun listToJson(list: List<GrammarItem>): String =
            JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
    }
}

data class AssistantReply(
    val reply: String,
    val intent: String = "chat",
    val vocab: List<VocabItem> = emptyList(),
    val grammar: List<GrammarItem> = emptyList(),
    val save: Boolean = false,
    val action: String = Actions.NONE,
    val model: String = "",
    val latencyMs: Long = 0,
    val isError: Boolean = false,
)

object Actions {
    const val NONE = "none"
    const val SHOW_VOCAB_TODAY = "show_vocab_today"
    const val SHOW_VOCAB_ALL = "show_vocab_all"
    const val START_REVIEW = "start_review"
    const val OPEN_HISTORY = "open_history"
    const val END = "end_conversation"
    const val DETAIL_ON = "detail_on"
    const val DETAIL_OFF = "detail_off"
    val ALL = listOf(NONE, SHOW_VOCAB_TODAY, SHOW_VOCAB_ALL, START_REVIEW, OPEN_HISTORY, END)
}

/** 1 lượt hội thoại gửi AI. [image] = đường dẫn ảnh đính kèm (chỉ gửi cho câu hỏi có ảnh gần nhất). */
data class Turn(val role: String, val text: String, val image: String? = null, val audioWav: ByteArray? = null) {
    companion object {
        const val USER = "user"
        const val ASSISTANT = "assistant"
    }
}

/** Lỗi có kèm câu báo thân thiện để hiển thị/đọc cho người dùng. */
class AiException(message: String, val userMessage: String, val code: Int = 0) : Exception(message)

object ReplyParser {

    fun parse(raw: String): AssistantReply {
        val text = raw.trim()
        val obj = extractJson(text)
        if (obj == null) {
            // Model không trả JSON → hiển thị nguyên văn
            return AssistantReply(reply = text.ifBlank { "…" }.take(4000))
        }
        val vocabArr = obj.optJSONArray("vocab") ?: obj.optJSONArray("words") ?: JSONArray()
        val vocab = (0 until vocabArr.length())
            .mapNotNull { vocabArr.optJSONObject(it)?.let(VocabItem::fromJson) }
            .filter { it.ko.isNotBlank() }
        val grammarArr = obj.optJSONArray("grammar") ?: JSONArray()
        val grammar = (0 until grammarArr.length())
            .mapNotNull { grammarArr.optJSONObject(it)?.let(GrammarItem::fromJson) }
            .filter { it.pattern.isNotBlank() }
        val reply = obj.optString("reply").ifBlank { obj.optString("answer") }.ifBlank { obj.optString("text") }
            .ifBlank { vocab.firstOrNull()?.let { "${it.vi} là ${it.ko}" } ?: "…" }
        val action = obj.optString("action", Actions.NONE).trim().lowercase().let { if (it in Actions.ALL) it else Actions.NONE }
        val intent = obj.optString("intent", "chat").ifBlank { "chat" }
        val save = if (obj.has("save")) obj.optBoolean("save", false) else intent == "vocab" || intent == "grammar"
        return AssistantReply(reply = reply.trim(), intent = intent, vocab = vocab, grammar = grammar, save = save, action = action)
    }

    fun extractJson(text: String): JSONObject? {
        runCatching { return JSONObject(text) }
        val unfenced = text.replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "").replace(Regex("```\\s*$"), "")
        runCatching { return JSONObject(unfenced) }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) runCatching { return JSONObject(text.substring(start, end + 1)) }
        return null
    }
}
