package com.hankukko.huyfan.assistant

import com.hankukko.huyfan.ai.AssistantReply
import com.hankukko.huyfan.data.TalkStyle
import com.hankukko.huyfan.data.VocabEntity
import com.hankukko.huyfan.data.keyOf
import com.hankukko.huyfan.util.Dates
import java.text.Normalizer

/**
 * Hỏi lại từ đã có trong sổ ("rửa bát tiếng Hàn là gì", "설거지하다 nghĩa là gì")
 * → trả lời ngay từ sổ (< 0,1 giây, không cần mạng), vẫn hiện bảng và vẫn tính 1 lần tra.
 * Câu hỏi phức tạp hơn, hoặc từ có nhiều nghĩa khác nhau trong sổ → để AI trả lời.
 */
object NotebookAnswer {

    const val MODEL = "Sổ từ"

    private fun clean(s: String): String =
        s.lowercase().replace(Regex("[?!.,;:…\"“”'‘’]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun fold(s: String): String =
        Normalizer.normalize(Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), ""), Normalizer.Form.NFC)
            .replace('đ', 'd')

    // "rửa bát tiếng hàn là gì", "cái thìa trong tiếng hàn nói thế nào", "tiếng hàn của con mèo là gì"
    private val en = com.hankukko.huyfan.Lang.isEn
    private val langWord = if (en) "tiếng anh(?: mỹ| anh)?" else "tiếng hàn(?: quốc)?"
    private val viAsk = Regex(
        "^(?:(?:cho (?:tôi|tui|mình|em) hỏi|hỏi|ê|ơi|huy fan|huy phan)\\s+)?(.+?)\\s+(?:trong |bằng |sang )?" + langWord +
            "(?:\\s+(?:là gì|là gì vậy|là gì thế|là gì nhỉ|là chi|nói (?:thế nào|như thế nào|sao|là gì)|gọi là gì|viết (?:thế nào|sao)|là sao))?(?:\\s+(?:vậy|nhỉ|nhé|nha|ông|bro|ạ))*$"
    )
    private val viAskOf = Regex("^" + (if (en) "tiếng anh" else "tiếng hàn") + " (?:của )?(.+?) (?:là gì|nói thế nào|là sao)(?:\\s+(?:vậy|nhỉ|nha|ạ))*$")
    private val viAskFolded = Regex(fold(viAsk.pattern))
    private val viAskOfFolded = Regex(fold(viAskOf.pattern))
    private val koAsk = Regex(
        (if (en) "^([a-z][a-z' -]{0,30}?)\\s*" else "^([가-힣][가-힣 ]{0,20})\\s*") +
            "(?:là gì|nghĩa là gì|có nghĩa là gì|nghĩa là sao|nghĩa gì|là sao|tiếng việt là gì|dịch là gì)(?:\\s+(?:vậy|nhỉ|nha|ạ|ông|bro))*$"
    )

    fun match(text: String, vocab: List<VocabEntity>, style: TalkStyle): AssistantReply? {
        if (vocab.isEmpty()) return null
        val q = clean(text)
        if (q.length > 60) return null
        val hit: List<VocabEntity> = run {
            koAsk.matchEntire(q)?.let { m ->
                val key = keyOf(m.groupValues[1]).lowercase()
                return@run vocab.filter { keyOf(it.ko).lowercase() == key }
            }
            // So khớp nghĩa: đúng nguyên văn trước
            val exactTerm = (viAsk.matchEntire(q) ?: viAskOf.matchEntire(q))?.groupValues?.get(1)?.trim()
            if (exactTerm != null) {
                if (exactTerm.isBlank() || exactTerm.length > 40) return null
                val exact = vocab.filter { v -> meanings(v.vi).any { it == exactTerm } }
                if (exact.isNotEmpty()) return@run exact
            }
            // Gõ không dấu ("rua bat tieng han la gi") → so khớp bản bỏ dấu
            val fq = fold(q)
            val ft = exactTerm?.let { fold(it) }
                ?: (viAskFolded.matchEntire(fq) ?: viAskOfFolded.matchEntire(fq))?.groupValues?.get(1)?.trim()
                ?: return null
            if (ft.isBlank() || ft.length > 40) return null
            vocab.filter { v -> meanings(v.vi).any { fold(it) == ft } }
        }
        if (hit.isEmpty()) return null
        // Nhiều từ tiếng Hàn khác nhau cùng nghĩa → để AI giải thích sự khác nhau
        if (hit.map { keyOf(it.ko) }.distinct().size > 1) return null
        val v = hit.maxBy { it.createdAt }
        val lookups = hit.sumOf { it.lookupCount } + 1
        val you = when (style) {
            TalkStyle.BUDDY -> "ông"
            TalkStyle.BRO -> "bro"
            TalkStyle.POLITE -> "bạn"
        }
        val learned = Dates.dayLabel(v.day).substringBefore(" ·").lowercase()
        val reply = buildString {
            append(v.vi.replaceFirstChar { it.uppercase() }).append(" là ").append(if (en) "\"${v.ko}\"" else v.ko).append(" nha ").append(you).append("! ")
            append(
                when {
                    lookups >= 4 -> "Từ này $you tra $lookups lần rồi đó, ôn kỹ nha."
                    lookups >= 2 -> "Từ này có trong sổ rồi, $you học $learned."
                    else -> "Có sẵn trong sổ của $you."
                }
            )
        }
        return AssistantReply(
            reply = reply, intent = "vocab", vocab = listOf(v.toItem()), save = true, model = MODEL,
        )
    }

    /** "bạn / bạn bè, người bạn" → ["bạn", "bạn bè", "người bạn"] */
    private fun meanings(vi: String): List<String> {
        val base = vi.replace(Regex("\\(.*?\\)"), " ")
        return (base.split('/', ',', ';') + base + vi).map { clean(it) }.filter { it.isNotBlank() }
    }
}
