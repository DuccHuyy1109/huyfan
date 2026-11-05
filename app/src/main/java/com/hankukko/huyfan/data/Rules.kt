package com.hankukko.huyfan.data

import android.content.Context
import org.json.JSONArray
import java.text.Normalizer

/** Cẩm nang nguyên tắc tiếng Hàn – nội dung soạn sẵn trong assets/rules_vi.json, dùng offline. */
data class RuleExample(val ko: String, val vi: String, val read: String = "")

data class Rule(
    val id: String,
    val title: String,
    val summary: String,
    val body: String,
    val table: List<List<String>>,
    val examples: List<RuleExample>,
) {
    /** Chuỗi gộp đã chuẩn hoá để tìm kiếm. */
    val searchText: String by lazy {
        RuleBook.fold(
            buildString {
                append(title).append(' ').append(summary).append(' ').append(body)
                table.forEach { r -> r.forEach { append(' ').append(it) } }
                examples.forEach { append(' ').append(it.ko).append(' ').append(it.vi).append(' ').append(it.read) }
            }
        )
    }
}

data class RuleCategory(
    val id: String,
    val icon: String,
    val title: String,
    val subtitle: String,
    val rules: List<Rule>,
)

object RuleBook {
    @Volatile
    private var cache: List<RuleCategory>? = null

    fun load(context: Context): List<RuleCategory> = cache ?: synchronized(this) {
        cache ?: parse(context.assets.open("rules_vi.json").bufferedReader().use { it.readText() }).also { cache = it }
    }

    private fun parse(json: String): List<RuleCategory> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val c = arr.getJSONObject(i)
            val rules = c.getJSONArray("rules")
            RuleCategory(
                id = c.getString("id"),
                icon = c.optString("icon"),
                title = c.getString("title"),
                subtitle = c.optString("subtitle"),
                rules = (0 until rules.length()).map { j ->
                    val r = rules.getJSONObject(j)
                    val table = r.optJSONArray("table")
                    val ex = r.optJSONArray("examples")
                    Rule(
                        id = r.getString("id"),
                        title = r.getString("title"),
                        summary = r.optString("summary"),
                        body = r.optString("body"),
                        table = if (table == null) emptyList() else (0 until table.length()).map { k ->
                            val row = table.getJSONArray(k)
                            (0 until row.length()).map { row.getString(it) }
                        },
                        examples = if (ex == null) emptyList() else (0 until ex.length()).map { k ->
                            val e = ex.getJSONObject(k)
                            RuleExample(e.getString("ko"), e.optString("vi"), e.optString("read"))
                        },
                    )
                },
            )
        }
    }

    /** Chữ thường, bỏ dấu tiếng Việt (giữ nguyên chữ Hàn). */
    fun fold(s: String): String =
        Normalizer.normalize(
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), ""),
            Normalizer.Form.NFC,
        ).replace('đ', 'd')

    /** Tìm theo tất cả từ khoá (khớp mọi từ). */
    fun search(all: List<RuleCategory>, query: String): List<Pair<RuleCategory, Rule>> {
        val words = fold(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val phrase = words.joinToString(" ")
        val hits = all.flatMap { c -> c.rules.map { c to it } }
            .filter { (_, r) -> words.all { w -> r.searchText.contains(w) } }
            .map { (c, r) ->
                val title = fold(r.title)
                val score = when {
                    title.contains(phrase) -> 3
                    r.searchText.contains(phrase) -> 2
                    words.all { title.contains(it) } -> 1
                    else -> 0
                }
                Triple(c, r, score)
            }
        // Có kết quả khớp nguyên cụm → chỉ giữ những kết quả đó cho gọn
        val best = hits.maxOfOrNull { it.third } ?: 0
        return hits.filter { best < 2 || it.third >= 2 }
            .sortedByDescending { it.third }
            .map { it.first to it.second }
    }
}
