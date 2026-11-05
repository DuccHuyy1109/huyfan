import io, sys, os

root = sys.argv[1]  # .../app/src/main/java/com/hankukko/huyfan


def patch(rel, pairs):
    p = os.path.join(root, rel)
    s = io.open(p, encoding='utf-8').read()
    for a, b in pairs:
        if a not in s:
            raise SystemExit(rel + ' NOT FOUND: ' + a[:90])
        s = s.replace(a, b, 1)
    io.open(p, 'w', encoding='utf-8').write(s)


# ---------- Gửi kèm lịch ôn lên Sheet ----------
srs_json = '''        .put("breakdown", breakdown)
        .put("next_review", Dates.dayKeyOf(Srs.effectiveDue(dueAt, createdAt)))
        .put("interval", intervalDays)
        .put("srs", "$dueAt,$intervalDays,$ease,$reps,$lapses")
}'''
p = os.path.join(root, 'data/Entities.kt')
s = io.open(p, encoding='utf-8').read()
old = '''        .put("breakdown", breakdown)
}'''
assert s.count(old) == 2, s.count(old)
s = s.replace(old, srs_json)
io.open(p, 'w', encoding='utf-8').write(s)

# ---------- Ôn xong → đánh dấu cần đồng bộ ----------
patch('data/Repositories.kt', [
    ('''        val rows = dao.findAllByKey(keyOf(v.ko)).ifEmpty { listOf(v) }
        rows.forEach { dao.update(it.withSrs(next)) }''', '''        val rows = dao.findAllByKey(keyOf(v.ko)).ifEmpty { listOf(v) }
        rows.forEach { dao.update(it.withSrs(next).copy(synced = false)) }'''),
    ('''        val rows = dao.findAllByKey(grammarKeyOf(g.pattern)).ifEmpty { listOf(g) }
        rows.forEach { dao.update(it.withSrs(next)) }''', '''        val rows = dao.findAllByKey(grammarKeyOf(g.pattern)).ifEmpty { listOf(g) }
        rows.forEach { dao.update(it.withSrs(next).copy(synced = false)) }'''),
])

# ---------- Khôi phục từ Sheet ----------
patch('data/SheetsSync.kt', [
    ('''    suspend fun resyncAll(): Result<Int> {''', '''    data class RestoreResult(val vocabAdded: Int, val grammarAdded: Int, val skipped: Int)

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

    suspend fun resyncAll(): Result<Int> {'''),
    ('''            post(url, JSONObject().put("token", settings.get().sheetsToken).put("items", JSONArray()))
            "Kết nối OK · ghi được vào trang tính “$sheet”"''', '''            val o = postForJson(url, JSONObject().put("token", settings.get().sheetsToken).put("items", JSONArray()))
            if (o.optInt("version", 1) < 2) "Kết nối OK · nhưng script là bản cũ – dán mã mới & Triển khai phiên bản mới để có Khôi phục và lịch ôn"
            else "Kết nối OK · ghi được vào trang tính “$sheet”"'''),
    ('''    private suspend fun post(url: String, payload: JSONObject) {
        val req = Request.Builder().url(url).post(payload.toString().toRequestBody(json)).build()
        http.newCall(req).await().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val o = runCatching { JSONObject(body) }.getOrNull()
                ?: error("Script không trả JSON (kiểm tra quyền truy cập \\"Bất kỳ ai\\")")
            if (!o.optBoolean("ok")) error(o.optString("error", "Script báo lỗi"))
        }
    }''', '''    private suspend fun post(url: String, payload: JSONObject) {
        postForJson(url, payload)
    }

    private suspend fun postForJson(url: String, payload: JSONObject): JSONObject {
        val req = Request.Builder().url(url).post(payload.toString().toRequestBody(json)).build()
        return http.newCall(req).await().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val o = runCatching { JSONObject(body) }.getOrNull()
                ?: error("Script không trả JSON (kiểm tra quyền truy cập \\"Bất kỳ ai\\")")
            if (!o.optBoolean("ok")) error(o.optString("error", "Script báo lỗi"))
            o
        }
    }'''),
])

# ---------- ViewModel ----------
patch('ui/MainViewModel.kt', [
    ('''    fun resyncAll(onDone: (String) -> Unit) = viewModelScope.launch {''', '''    fun restoreFromSheets(onDone: (String) -> Unit) = viewModelScope.launch {
        val r = c.sheets.restore()
        HuyFanWidget.refresh(getApplication())
        onDone(
            r.fold(
                { if (it.vocabAdded + it.grammarAdded == 0) "Máy đã có đủ dữ liệu – không có mục nào cần khôi phục (${it.skipped} mục đã có)"
                  else "Đã khôi phục ${it.vocabAdded} từ vựng, ${it.grammarAdded} ngữ pháp (bỏ qua ${it.skipped} mục đã có)" },
                { "Lỗi: ${it.message}" },
            )
        )
    }

    /** Ôn xong 1 phiên → đẩy lịch ôn mới lên Sheet (nếu bật tự đồng bộ). */
    fun syncAfterStudy() = viewModelScope.launch { c.sheets.syncPending() }

    fun resyncAll(onDone: (String) -> Unit) = viewModelScope.launch {'''),
])

# ---------- Nút khôi phục ----------
patch('ui/screens/SettingsScreen.kt', [
    ('''                    FilledTonalButton(onClick = { vm.resyncAll { sheetsResult = it to !it.startsWith("Lỗi") } }) { Text("Đồng bộ lại tất cả") }
                }''', '''                    FilledTonalButton(onClick = { vm.resyncAll { sheetsResult = it to !it.startsWith("Lỗi") } }) { Text("Đồng bộ lại tất cả") }
                }
                ActionRow(
                    "Khôi phục từ Google Sheets", "Kéo sổ từ trên Sheet về máy (máy mới, lỡ xoá…) – chỉ thêm mục còn thiếu",
                    Icons.Rounded.CloudDownload,
                ) {
                    sheetsResult = "Đang tải dữ liệu từ Sheet…" to true
                    vm.restoreFromSheets { sheetsResult = it to !it.startsWith("Lỗi") }
                }'''),
    ('''                ActionRow("Xuất sổ từ vựng (Excel/CSV)", "Mở được bằng Excel, Google Sheets", Icons.AutoMirrored.Rounded.OpenInNew) { vm.exportCsv(context) }''',
     '''                ActionRow("Xuất sổ từ vựng (Excel/CSV)", "Mở được bằng Excel, Google Sheets", Icons.AutoMirrored.Rounded.OpenInNew) { vm.exportCsv(context) }
                var restoreMsg by remember { mutableStateOf<String?>(null) }
                ActionRow(
                    "Khôi phục sổ từ Google Sheets",
                    restoreMsg ?: if (s.sheetsUrl.isBlank()) "Cần kết nối Google Sheets trước" else "Kéo từ vựng, ngữ pháp và lịch ôn từ Sheet về máy",
                    Icons.Rounded.CloudDownload,
                ) {
                    restoreMsg = "Đang tải dữ liệu từ Sheet…"
                    vm.restoreFromSheets { restoreMsg = it }
                }'''),
])
p = os.path.join(root, 'ui/screens/SettingsScreen.kt')
s = io.open(p, encoding='utf-8').read()
line = 'import androidx.compose.material.icons.rounded.CloudDownload\n'
if line not in s:
    s = s.replace('import androidx.compose.material.icons.Icons\n', 'import androidx.compose.material.icons.Icons\n' + line, 1)
io.open(p, 'w', encoding='utf-8').write(s)

patch('ui/MainActivity.kt', [
    ('''    study?.let { StudyDialog(vm, it, onDismiss = { study = null }) }''',
     '''    study?.let { StudyDialog(vm, it, onDismiss = { study = null; vm.syncAfterStudy() }) }'''),
])

patch('ui/screens/Dialogs.kt', [
    ('''        "Sao chép “URL ứng dụng web” (đuôi /exec) → dán vào ô Link Apps Script → bấm Gửi thử.",
    )''', '''        "Sao chép “URL ứng dụng web” (đuôi /exec) → dán vào ô Link Apps Script → bấm Gửi thử.",
        "Đã cài bản cũ trước đây? Dán đè mã mới → Lưu → Triển khai → Quản lý triển khai → ✏️ → Phiên bản: Phiên bản mới → Triển khai. Link /exec giữ nguyên.",
    )'''),
])

# import cho Entities/SheetsSync
p = os.path.join(root, 'data/SheetsSync.kt')
s = io.open(p, encoding='utf-8').read()
io.open(p, 'w', encoding='utf-8').write(s)
print('ok')
