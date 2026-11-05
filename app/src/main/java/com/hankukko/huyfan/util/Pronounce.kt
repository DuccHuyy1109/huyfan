package com.hankukko.huyfan.util

/**
 * Chấm phát âm tiếng Hàn: so câu máy nghe được với câu mẫu ở mức phụ âm/nguyên âm (jamo),
 * và dóng từng âm tiết để tô xanh (đúng) / vàng (gần đúng) / đỏ (sai hoặc thiếu).
 */
object Pronounce {

    enum class Mark { OK, CLOSE, MISS, NEUTRAL }

    data class Result(
        val score: Int,
        /** Mỗi ký tự của câu mẫu kèm đánh giá (ký tự không phải Hangul = NEUTRAL) */
        val marks: List<Pair<Char, Mark>>,
        val heard: String,
    ) {
        val stars: Int get() = when {
            score >= 90 -> 3
            score >= 75 -> 2
            score >= 55 -> 1
            else -> 0
        }
    }

    private const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    private const val JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
    private const val JONG = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"

    private fun isSyl(c: Char) = c in '가'..'힣'

    private fun jamo(c: Char): String {
        if (!isSyl(c)) return ""
        val i = c - '가'
        val f = i % 28
        return buildString {
            append(CHO[i / 588]); append(JUNG[(i % 588) / 28])
            if (f != 0) append(JONG[f])
        }
    }

    private fun jamoOf(s: String): String = s.filter(::isSyl).map(::jamo).joinToString("")

    private fun lev(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    private fun ratio(a: String, b: String): Int {
        val m = maxOf(a.length, b.length)
        if (m == 0) return 0
        return ((1.0 - lev(a, b).toDouble() / m) * 100).toInt().coerceIn(0, 100)
    }

    /** Tiếng Anh (câu mẫu không có Hangul): chấm theo từng từ, tô màu cả từ. */
    private fun scoreWords(target: String, heard: String): Result {
        fun norm(s: String) = s.lowercase().replace('’', '\'').filter { it.isLetterOrDigit() || it == '\'' }
        val tWords = Regex("[A-Za-z0-9’']+").findAll(target).toList()
        val hWords = heard.split(Regex("[^A-Za-z0-9’']+")).map(::norm).filter { it.isNotBlank() }
        if (tWords.isEmpty() || hWords.isEmpty()) return Result(0, target.map { it to Mark.NEUTRAL }, heard)
        val marks = Array(target.length) { Mark.NEUTRAL }
        var total = 0
        var used = 0
        tWords.forEach { m ->
            val w = norm(m.value)
            // Tìm từ khớp nhất trong câu nghe được (ưu tiên theo thứ tự)
            var best = 0
            var bestAt = -1
            for (k in used until hWords.size) {
                val r = ratio(w, hWords[k])
                if (r > best) { best = r; bestAt = k }
                if (r == 100) break
            }
            if (bestAt >= 0 && best >= 60) used = bestAt + 1
            total += best
            val mark = when {
                best == 100 -> Mark.OK
                best >= 60 -> Mark.CLOSE
                else -> Mark.MISS
            }
            for (i in m.range) marks[i] = mark
        }
        val score = (total / tWords.size).coerceIn(0, 100)
        return Result(score, target.mapIndexed { i, c -> c to marks[i] }, heard)
    }

    fun score(target: String, heard: String): Result {
        if (target.none(::isSyl)) return scoreWords(target, heard)
        val tSyl = target.filter(::isSyl)
        val hSyl = heard.filter(::isSyl)
        val score = when {
            tSyl.isEmpty() || hSyl.isEmpty() -> 0
            hSyl.contains(tSyl) -> 100 // đọc đúng, có nói thêm vài chữ
            else -> ratio(jamoOf(tSyl), jamoOf(hSyl))
        }
        return Result(score, align(target, tSyl, hSyl), heard)
    }

    /** Dóng âm tiết câu mẫu với câu nghe được (Levenshtein + truy vết). */
    private fun align(target: String, t: String, h: String): List<Pair<Char, Mark>> {
        val n = t.length
        val m = h.length
        val d = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) d[i][0] = i
        for (j in 0..m) d[0][j] = j
        for (i in 1..n) for (j in 1..m) {
            val sub = if (t[i - 1] == h[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + sub)
        }
        val marks = Array(n) { Mark.MISS }
        var i = n
        var j = m
        while (i > 0 && j > 0) {
            val sub = if (t[i - 1] == h[j - 1]) 0 else 1
            when {
                d[i][j] == d[i - 1][j - 1] + sub -> {
                    marks[i - 1] = when {
                        sub == 0 -> Mark.OK
                        ratio(jamo(t[i - 1]), jamo(h[j - 1])) >= 60 -> Mark.CLOSE
                        else -> Mark.MISS
                    }
                    i--; j--
                }
                d[i][j] == d[i - 1][j] + 1 -> { marks[i - 1] = Mark.MISS; i-- }
                else -> j--
            }
        }
        var k = 0
        return target.map { c -> if (isSyl(c)) c to marks.getOrElse(k++) { Mark.MISS } else c to Mark.NEUTRAL }
    }
}
