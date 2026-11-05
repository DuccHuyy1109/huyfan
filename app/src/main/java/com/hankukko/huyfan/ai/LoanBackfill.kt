package com.hankukko.huyfan.ai

import android.content.Context
import android.util.Log
import com.hankukko.huyfan.AppContainer
import org.json.JSONArray
import org.json.JSONObject

/**
 * Từ lưu trước khi app biết "từ mượn" (vd 컴퓨터) chưa có gốc từ → hỏi AI 1 lần theo nhóm rồi điền vào sổ.
 * Chạy ngầm khi mở app; lỗi mạng / hết hạn mức thì để lần mở sau chạy lại.
 */
object LoanBackfill {
    private const val KEY = "loan_backfill_v3"

    private const val SYSTEM = """Bạn là chuyên gia từ vựng tiếng Hàn. Với mỗi từ tiếng Hàn được cho (mỗi dòng 1 từ), xác định có phải từ mượn tiếng nước ngoài (외래어) không.
- Từ mượn → loan = từ gốc. Tiếng Anh chỉ ghi từ (vd 컴퓨터 → computer, 아이스크림 → ice cream); tiếng khác ghi kèm tên tiếng (vd 아르바이트 → Arbeit (tiếng Đức), 빵 → pão (tiếng Bồ Đào Nha)).
- Từ thuần Hàn hoặc Hán Hàn → loan = "".
Không chắc chắn thì để trống, tuyệt đối không bịa. Trả về đủ mọi từ, giữ nguyên chữ ko như đầu vào.
CHỈ trả về JSON đúng dạng: {"items":[{"ko":"컴퓨터","loan":"computer"},{"ko":"깜짝","loan":""}]}"""

    private fun schema(): JSONObject {
        fun str() = JSONObject().put("type", "STRING")
        val item = JSONObject().put("type", "OBJECT")
            .put("properties", JSONObject().put("ko", str()).put("loan", str()))
            .put("required", JSONArray(listOf("ko", "loan")))
        return JSONObject().put("type", "OBJECT")
            .put("properties", JSONObject().put("items", JSONObject().put("type", "ARRAY").put("items", item)))
            .put("required", JSONArray(listOf("items")))
    }

    suspend fun run(c: AppContainer) {
        val prefs = c.app.getSharedPreferences("huyfan_maint", Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY, false)) return
        if (!c.settings.get().hasAiKey) return
        val words = c.vocab.allNow().filter { it.hanja.isBlank() && it.loan.isBlank() }.map { it.ko.trim() }.distinct()
        var filled = 0
        for (chunk in words.chunked(50)) {
            val (raw, _) = c.ai.askJson(SYSTEM, listOf(Turn(Turn.USER, chunk.joinToString("\n"))), schema())
            Log.i("HuyFanAI", "từ mượn ← ${raw.take(400)}")
            val asked = chunk.toSet()
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            val arr = if (start >= 0 && end > start) runCatching { JSONObject(raw.substring(start, end + 1)).optJSONArray("items") }.getOrNull() else null
            if (arr == null) {
                // AI dự phòng trả dạng chữ "컴퓨터 → computer" mỗi dòng
                raw.lines().forEach { line ->
                    val parts = line.split("→", "->", ":").map { it.trim().trim('-', '*', ' ', '"') }
                    if (parts.size >= 2 && parts[0] in asked) {
                        val loan = parts[1]
                        if (loan.isNotBlank() && loan.any { it.isLetter() && it !in '가'..'힣' } && loan != "null" && loan != "-" && loan != "—")
                            filled += c.vocab.setLoan(parts[0], loan)
                    }
                }
                continue
            }
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val ko = o.optString("ko").trim()
                val loan = o.optString("loan").trim()
                if (ko in asked && loan.isNotBlank() && loan != "null" && loan != "-" && loan != "—") {
                    filled += c.vocab.setLoan(ko, loan)
                }
            }
        }
        prefs.edit().putBoolean(KEY, true).apply()
        Log.i("HuyFanAI", "bổ sung từ mượn: ${words.size} từ kiểm tra, $filled mục được điền")
    }
}
