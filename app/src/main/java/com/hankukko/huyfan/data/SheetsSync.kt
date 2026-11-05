package com.hankukko.huyfan.data

import com.hankukko.huyfan.util.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Đẩy sổ từ vựng lên Google Sheets thông qua Web App Apps Script của chính người dùng
 * (không cần đăng nhập Google trong app). Mã script nằm ở res/raw/apps_script.txt.
 */
class SheetsSync(
    private val http: OkHttpClient,
    private val settings: SettingsRepository,
    private val dao: VocabDao,
    private val grammarDao: GrammarDao,
) {
    private val mutex = Mutex()
    private val json = "application/json; charset=utf-8".toMediaType()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    /** Gửi các từ chưa đồng bộ. force = gửi kể cả khi đã tắt tự động đồng bộ. */
    suspend fun syncPending(force: Boolean = false): Result<Int> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val s = settings.get()
                if (s.sheetsUrl.isBlank()) {
                    if (force) error("Chưa nhập link Apps Script") else return@runCatching 0
                }
                if (!force && !s.sheetsAutoSync) return@runCatching 0
                urlProblem(s.sheetsUrl.trim())?.let { error(it) }
                val items = dao.unsynced()
                val grammar = grammarDao.unsynced()
                if (items.isEmpty() && grammar.isEmpty()) return@runCatching 0
                var sent = 0
                items.chunked(100).forEach { chunk ->
                    val payload = JSONObject()
                        .put("token", s.sheetsToken)
                        .put("items", JSONArray().apply { chunk.forEach { put(it.toSheetJson()) } })
                    post(s.sheetsUrl.trim(), payload)
                    dao.markSynced(chunk.map { it.id })
                    sent += chunk.size
                }
                grammar.chunked(100).forEach { chunk ->
                    val payload = JSONObject()
                        .put("token", s.sheetsToken)
                        .put("grammar", JSONArray().apply { chunk.forEach { put(it.toSheetJson()) } })
                    post(s.sheetsUrl.trim(), payload)
                    grammarDao.markSynced(chunk.map { it.id })
                    sent += chunk.size
                }
                sent
            }.onSuccess { n ->
                if (n > 0) _status.value = "Đã đồng bộ $n mục lên Google Sheets"
            }.onFailure { e ->
                _status.value = "Lỗi đồng bộ: ${e.message}"
            }
        }
    }

    suspend fun deleteRemote(uids: List<String>) {
        if (uids.isEmpty()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val s = settings.get()
                if (s.sheetsUrl.isBlank() || !s.sheetsAutoSync || urlProblem(s.sheetsUrl.trim()) != null) return@runCatching
                post(s.sheetsUrl.trim(), JSONObject().put("token", s.sheetsToken).put("deletes", JSONArray(uids)))
            }
        }
    }

    data class RestoreResult(val vocabAdded: Int, val grammarAdded: Int, val skipped: Int)

    /**
     * Kéo toàn bộ sổ từ trên Sheet về máy (cài app sang máy mới, lỡ xoá dữ liệu…).
     * Chỉ thêm mục còn thiếu (theo ID) – không ghi đè dữ liệu đang có trên máy.
     */
    suspend fun restore(): Result<RestoreResult> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val s = settings.get()
                val url = s.sheetsUrl.trim()
                if (url.isBlank()) error("Chưa nhập link Apps Script")
                urlProblem(url)?.let { error(it) }
                val o = postForJson(url, JSONObject().put("token", s.sheetsToken).put("action", "export"))
                val items = o.optJSONArray("items")
                val grammar = o.optJSONArray("grammar")
                if (items == null || grammar == null) {
                    error("Script trên Sheet là bản cũ. Mở “Hướng dẫn & mã Apps Script”, dán mã mới rồi Triển khai phiên bản mới")
                }
                var va = 0
                var ga = 0
                var skip = 0
                for (i in 0 until items.length()) {
                    val it = items.optJSONObject(i) ?: continue
                    val uid = it.optString("uid")
                    val ko = it.optString("ko").trim()
                    val vi = it.optString("vi").trim()
                    if (uid.isBlank() || ko.isBlank() || vi.isBlank() || dao.byUid(uid) != null) { skip++; continue }
                    val (day, created) = dayAndTime(it.optString("date"), it.optString("time"))
                    val e = VocabEntity(
                        uid = uid, vi = vi, ko = ko, romaja = it.optString("romaja"), hanja = it.optString("hanja"),
                        hanViet = it.optString("han_viet"), pos = it.optString("pos"), exampleKo = it.optString("example_ko"),
                        exampleVi = it.optString("example_vi"), note = it.optString("note"), breakdown = it.optString("breakdown"),
                        day = day, createdAt = created, synced = true,
                    )
                    dao.insert(parseSrs(it.optString("srs"))?.let { st -> e.withSrs(st, 0L).copy(lastReviewedAt = 0) } ?: e)
                    va++
                }
                for (i in 0 until grammar.length()) {
                    val it = grammar.optJSONObject(i) ?: continue
                    val uid = it.optString("uid")
                    val pattern = it.optString("pattern").trim()
                    val meaning = it.optString("meaning").trim()
                    if (uid.isBlank() || pattern.isBlank() || meaning.isBlank() || grammarDao.byUid(uid) != null) { skip++; continue }
                    val (day, created) = dayAndTime(it.optString("date"), it.optString("time"))
                    val g = GrammarEntity(
                        uid = uid, pattern = pattern, meaning = meaning, form = it.optString("form"), combine = it.optString("combine"),
                        exampleKo = it.optString("example_ko"), exampleVi = it.optString("example_vi"), note = it.optString("note"),
                        breakdown = it.optString("breakdown"), day = day, createdAt = created, synced = true,
                    )
                    grammarDao.insert(parseSrs(it.optString("srs"))?.let { st -> g.withSrs(st, 0L).copy(lastReviewedAt = 0) } ?: g)
                    ga++
                }
                RestoreResult(va, ga, skip)
            }.onSuccess { r ->
                _status.value = "Đã khôi phục ${r.vocabAdded} từ, ${r.grammarAdded} ngữ pháp từ Google Sheets"
            }
        }
    }

    private fun dayAndTime(date: String, time: String): Pair<String, Long> {
        val d = runCatching { java.time.LocalDate.parse(date.trim().take(10)) }.getOrNull() ?: java.time.LocalDate.now()
        val t = runCatching { java.time.LocalTime.parse(time.trim().take(5)) }.getOrNull() ?: java.time.LocalTime.NOON
        return d.toString() to d.atTime(t).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /** "dueAt,intervalDays,ease,reps,lapses" → lịch ôn. */
    private fun parseSrs(raw: String): SrsState? {
        val p = raw.split(',').map { it.trim() }
        if (p.size < 5) return null
        return runCatching { SrsState(p[0].toLong(), p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4].toInt()) }.getOrNull()
    }

    suspend fun resyncAll(): Result<Int> {
        dao.markAllUnsynced()
        grammarDao.markAllUnsynced()
        return syncPending(force = true)
    }

    /** Kiểm tra link: gọi doGet của script, rồi gửi thử 1 gói rỗng để kiểm tra mã bí mật & quyền ghi. */
    suspend fun test(rawUrl: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = rawUrl.trim()
            if (url.isBlank()) error("Chưa nhập link Apps Script")
            urlProblem(url)?.let { error(it) }
            val res = http.newCall(Request.Builder().url(url).get().build()).await()
            val sheet = res.use { r ->
                val body = r.body?.string().orEmpty()
                val o = runCatching { JSONObject(body) }.getOrNull()
                    ?: error(
                        if (r.code == 404) "Không tìm thấy script (HTTP 404) – kiểm tra lại link"
                        else "Script chưa trả JSON – kiểm tra đã triển khai Ứng dụng web với quyền \"Bất kỳ ai\" chưa"
                    )
                if (!o.optBoolean("ok")) error(o.optString("error", "Script báo lỗi"))
                o.optString("sheet")
            }
            val o = postForJson(url, JSONObject().put("token", settings.get().sheetsToken).put("items", JSONArray()))
            if (o.optInt("version", 1) < 2) "Kết nối OK · nhưng script là bản cũ – dán mã mới & Triển khai phiên bản mới để có Khôi phục và lịch ôn"
            else "Kết nối OK · ghi được vào trang tính “$sheet”"
        }
    }

    companion object {
        /** Lỗi hay gặp khi dán link: dán link Google Sheet, link /dev… → câu hướng dẫn; null = link dùng được. */
        fun urlProblem(url: String): String? = when {
            url.contains("docs.google.com/spreadsheets") ->
                "Đây là link của Google Sheet. Cần link Ứng dụng web của Apps Script (https://script.google.com/macros/s/…/exec) – xem “Hướng dẫn & mã Apps Script”"
            !url.startsWith("http") -> "Link phải bắt đầu bằng https://script.google.com/macros/s/…/exec"
            url.contains("script.google.com") && url.trimEnd('/').endsWith("/dev") ->
                "Đây là link /dev (chỉ chủ script mở được). Dùng link kết thúc bằng /exec trong Triển khai → Quản lý triển khai"
            else -> null
        }
    }

    private suspend fun post(url: String, payload: JSONObject) {
        postForJson(url, payload)
    }

    private suspend fun postForJson(url: String, payload: JSONObject): JSONObject {
        val req = Request.Builder().url(url).post(payload.toString().toRequestBody(json)).build()
        return http.newCall(req).await().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val o = runCatching { JSONObject(body) }.getOrNull()
                ?: error("Script không trả JSON (kiểm tra quyền truy cập \"Bất kỳ ai\")")
            if (!o.optBoolean("ok")) error(o.optString("error", "Script báo lỗi"))
            o
        }
    }
}
