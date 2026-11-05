package com.hankukko.huyfan.ai

import android.content.Context
import android.util.Log
import com.hankukko.huyfan.AppContainer
import org.json.JSONArray
import org.json.JSONObject

/**
 * Từ lưu trước khi app biết "từ mượn" (vd 컴퓨터) chưa có gốc từ / cách đọc → hỏi AI 1 lần theo nhóm rồi điền vào sổ.
 * Chạy ngầm khi mở app; lỗi mạng / hết hạn mức thì để lần mở sau chạy lại.
 */
object LoanBackfill {
    private const val KEY = "loan_backfill_v4"

    private const val SYSTEM = """Bạn là chuyên gia từ vựng tiếng Hàn. Với mỗi từ tiếng Hàn được cho (mỗi dòng 1 từ), xác định có phải từ mượn tiếng nước ngoài (외래어) không.
- Từ mượn → loan = từ gốc. Tiếng Anh chỉ ghi từ (vd 컴퓨터 → computer, 아이스크림 → ice cream); tiếng khác ghi kèm tên tiếng (vd 아르바이트 → Arbeit (tiếng Đức), 빵 → pão (tiếng Bồ Đào Nha)).
  loan_read = cách đọc từ gốc phiên âm kiểu tiếng Việt cho người Việt dễ đọc (vd computer → cơm-piu-tơ, ice cream → ai-x-crim, Arbeit → ác-bai).
- Từ thuần Hàn hoặc Hán Hàn → loan = "" và loan_read = "".
Không chắc chắn thì để trống, tuyệt đối không bịa. Trả về đủ mọi từ, giữ nguyên chữ ko như đầu vào.
CHỈ trả về JSON đúng dạng: {"items":[{"ko":"컴퓨터","loan":"computer","loan_read":"cơm-piu-tơ"},{"ko":"깜짝","loan":"","loan_read":""}]}"""

    private fun schema(): JSONObject {
        fun str() = JSONObject().put("type", "STRING")
        val item = JSONObject().put("type", "OBJECT")
            .put("properties", JSONObject().put("ko", str()).put("loan", str()).put("loan_read", str()))
            .put("required", JSONArray(listOf("ko", "loan", "loan_read")))
        return JSONObject().put("type", "OBJECT")
            .put("properties", JSONObject().put("items", JSONObject().put("type", "ARRAY").put("items", item)))
            .put("required", JSONArray(listOf("items")))
    }

    private fun clean(s: String) = s.trim().takeUnless { it == "null" || it == "-" || it == "—" }.orEmpty()

    suspend fun run(c: AppContainer) {
        val prefs = c.app.getSharedPreferences("huyfan_maint", Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY, false)) return
        if (!c.settings.get().hasAiKey) return
        // Chưa rõ có phải từ mượn, hoặc là từ mượn mà chưa có cách đọc
        val words = c.vocab.allNow()
            .filter { it.hanja.isBlank() && (it.loan.isBlank() || it.loanRead.isBlank()) }
            .map { it.ko.trim() }.distinct()
        var filled = 0
        for (chunk in words.chunked(50)) {
            val (raw, _) = c.ai.askJson(SYSTEM, listOf(Turn(Turn.USER, chunk.joinToString("\n"))), schema())
            val asked = chunk.toSet()
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            val arr = if (start >= 0 && end > start) runCatching { JSONObject(raw.substring(start, end + 1)).optJSONArray("items") }.getOrNull() else null
            if (arr == null) {
                Log.w("HuyFanAI", "từ mượn: AI không trả JSON – để lần sau: ${raw.take(200)}")
                return
            }
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val ko = o.optString("ko").trim()
                val loan = clean(o.optString("loan"))
                if (ko in asked && loan.isNotBlank()) filled += c.vocab.setLoan(ko, loan, clean(o.optString("loan_read")))
            }
        }
        prefs.edit().putBoolean(KEY, true).apply()
        Log.i("HuyFanAI", "bổ sung từ mượn: ${words.size} từ kiểm tra, $filled mục được điền")
    }
}
