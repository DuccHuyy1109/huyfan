package com.hankukko.huyfan.util

import java.text.Normalizer

/**
 * So khớp câu người dùng nói với đáp án của thẻ (0..100%).
 * Bỏ qua hoa/thường, dấu câu, dấu cách (tiếng Hàn), và có so cả bản bỏ dấu tiếng Việt.
 */
object Similarity {

    private fun norm(s: String): String =
        s.lowercase()
            .replace(Regex("\\(.*?\\)"), " ")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun noMarks(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace('đ', 'd')

    private fun ratio(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty()) return 0
        if (a == b) return 100
        val d = levenshtein(a, b)
        return ((1.0 - d.toDouble() / maxOf(a.length, b.length)) * 100).toInt().coerceIn(0, 100)
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** Điểm cao nhất giữa câu nói và các phương án đáp án (tách bởi , ; / hoặc "hoặc"). */
    fun score(heard: String, expected: String): Int {
        val h = norm(heard)
        if (h.isEmpty()) return 0
        val alts = expected.split(Regex("[,;/]| hoặc | or ")).map { norm(it) }.filter { it.isNotEmpty() }
            .ifEmpty { listOf(norm(expected)) }
        return alts.maxOf { alt ->
            val compact = { s: String -> s.replace(" ", "") }
            maxOf(
                ratio(h, alt),
                ratio(compact(h), compact(alt)),
                ratio(noMarks(h), noMarks(alt)),
                // Nói dài hơn nhưng có chứa đúng đáp án → coi là đúng
                if (alt.length >= 2 && (compact(h).contains(compact(alt)) || noMarks(h).contains(noMarks(alt)))) 100 else 0,
            )
        }
    }
}
