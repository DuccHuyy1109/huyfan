import io, sys, os

root = sys.argv[1]  # .../huyfan


def patch(rel, pairs):
    p = os.path.join(root, rel)
    s = io.open(p, encoding='utf-8').read()
    for a, b in pairs:
        if a not in s:
            raise SystemExit(rel + ' NOT FOUND: ' + a[:90])
        s = s.replace(a, b, 1)
    io.open(p, 'w', encoding='utf-8').write(s)


# ---------- Gemini: cho phép schema riêng ----------
patch('ai/Clients.kt', [
    ('''        temperature: Float,
        fast: Boolean,
        client: OkHttpClient = http,
    ): String {''', '''        temperature: Float,
        fast: Boolean,
        client: OkHttpClient = http,
        schema: JSONObject? = PromptBuilder.geminiSchema(),
    ): String {'''),
    ('''                val out = call(client, baseUrl, key, model, system, turns, temperature, cfg)''',
     '''                val out = call(client, baseUrl, key, model, system, turns, temperature, cfg, schema)'''),
    ('''        turns: List<Turn>, temperature: Float, thinking: JSONObject?,
    ): String {''', '''        turns: List<Turn>, temperature: Float, thinking: JSONObject?,
        schema: JSONObject?,
    ): String {'''),
    ('''            .put("responseMimeType", "application/json")
            .put("responseSchema", PromptBuilder.geminiSchema())
        if (thinking != null)''', '''            .put("responseMimeType", "application/json")
        if (schema != null) gen.put("responseSchema", schema)
        if (thinking != null)'''),
])

# ---------- AiService: tách lõi "chạy đua API chính / dự phòng" để dùng chung ----------
patch('ai/AiService.kt', [
    ('''    suspend fun ask(system: String, turns: List<Turn>): AssistantReply {
        val s = settings.get()
        val primary = s.provider''', '''    suspend fun ask(system: String, turns: List<Turn>): AssistantReply {
        val s = settings.get()
        return withProviders(s) { p -> call(p, s, system, turns) }
    }

    /** Gọi AI trả JSON theo [schema] riêng (vd hội thoại nhập vai) – vẫn có dự phòng Gemini ↔ Groq. */
    suspend fun askJson(system: String, turns: List<Turn>, schema: JSONObject?): Pair<String, String> {
        val s = settings.get()
        return withProviders(s) { p -> callRaw(p, s, system, turns, schema) }
    }

    /** Chạy API chính; chậm/lỗi → gọi song song API dự phòng (nếu có key), lấy bên trả lời trước. */
    private suspend fun <T> withProviders(s: AppSettings, run: suspend (Provider) -> T): T {
        val primary = s.provider'''),
    ('''            return try {
                call(primary, s, system, turns)
            } catch (e: CancellationException) {''', '''            return try {
                run(primary)
            } catch (e: CancellationException) {'''),
    ('''            val ch = Channel<Pair<Provider, Result<AssistantReply>>>(Channel.UNLIMITED)
            val jobs = mutableListOf<Job>()
            fun start(p: Provider) {
                jobs += launch { ch.send(p to runCatching { call(p, s, system, turns) }) }
            }''', '''            val ch = Channel<Pair<Provider, Result<T>>>(Channel.UNLIMITED)
            val jobs = mutableListOf<Job>()
            fun start(p: Provider) {
                jobs += launch { ch.send(p to runCatching { run(p) }) }
            }'''),
    ('''    private suspend fun call(p: Provider, s0: AppSettings, system: String, turns: List<Turn>): AssistantReply {
        val t0 = System.currentTimeMillis()
        var s = s0''', '''    private suspend fun call(p: Provider, s0: AppSettings, system: String, turns: List<Turn>): AssistantReply {
        val t0 = System.currentTimeMillis()
        val (raw, model) = callRaw(p, s0, system, turns, PromptBuilder.geminiSchema())
        return ReplyParser.parse(raw).copy(model = model, latencyMs = System.currentTimeMillis() - t0)
    }

    private suspend fun callRaw(p: Provider, s0: AppSettings, system: String, turns: List<Turn>, schema: JSONObject?): Pair<String, String> {
        var s = s0'''),
    ('''        val (raw, model) = when (p) {
            Provider.GEMINI -> callGemini(s, system, turns)''', '''        return when (p) {
            Provider.GEMINI -> callGemini(s, system, turns, schema)'''),
    ('''                    openai.generate(s.openaiBaseUrl, s.openaiKey, picked, system, turns, s.temperature) to picked
                }
            }
        }
        return ReplyParser.parse(raw).copy(model = model, latencyMs = System.currentTimeMillis() - t0)
    }

    private suspend fun callGemini(s: AppSettings, system: String, turns: List<Turn>): Pair<String, String> {''', '''                    openai.generate(s.openaiBaseUrl, s.openaiKey, picked, system, turns, s.temperature) to picked
                }
            }
        }
    }

    private suspend fun callGemini(s: AppSettings, system: String, turns: List<Turn>, schema: JSONObject?): Pair<String, String> {'''),
    ('''            gemini.generate(s.geminiBaseUrl, s.geminiKey, model, system, turns, s.temperature, s.fastMode, genHttp)''',
     '''            gemini.generate(s.geminiBaseUrl, s.geminiKey, model, system, turns, s.temperature, s.fastMode, genHttp, schema)'''),
])
p = os.path.join(root, 'ai/AiService.kt')
s = io.open(p, encoding='utf-8').read()
if 'import org.json.JSONObject\n' not in s:
    s = s.replace('import okhttp3.OkHttpClient\n', 'import okhttp3.OkHttpClient\nimport org.json.JSONObject\n', 1)
io.open(p, 'w', encoding='utf-8').write(s)
print('ok')
