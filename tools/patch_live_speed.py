import io, sys, os

root = sys.argv[1]  # .../app/src/main
J = 'java/com/hankukko/huyfan/'


def patch(rel, pairs):
    p = os.path.join(root, rel)
    s = io.open(p, encoding='utf-8').read()
    for a, b in pairs:
        if a not in s:
            raise SystemExit(rel + ' NOT FOUND: ' + a[:90])
        s = s.replace(a, b, 1)
    io.open(p, 'w', encoding='utf-8').write(s)


patch(J + 'ai/AiService.kt', [
    # Phiên dịch trực tiếp: chờ API chính ngắn hơn rồi mới gọi dự phòng
    ('''    suspend fun askJson(system: String, turns: List<Turn>, schema: JSONObject?): Pair<String, String> {
        val s = settings.get()
        return withProviders(s) { p -> callRaw(p, s, system, turns, schema) }
    }''', '''    suspend fun askJson(system: String, turns: List<Turn>, schema: JSONObject?, live: Boolean = false): Pair<String, String> {
        val s = settings.get()
        return withProviders(s, if (live) LIVE_HEDGE_MS else null) { p -> callRaw(p, s, system, turns, schema) }
    }'''),
    ('''    private suspend fun <T> withProviders(s: AppSettings, run: suspend (Provider) -> T): T {''',
     '''    /** [hedge] = chờ API chính tối đa bao lâu rồi gọi song song API dự phòng (null = mặc định). */
    private suspend fun <T> withProviders(s: AppSettings, hedge: Long? = null, run: suspend (Provider) -> T): T {'''),
    ('''            val hedgeMs = if (troubled) PROVIDER_HEDGE_FAST_MS else PROVIDER_HEDGE_MS''',
     '''            val hedgeMs = minOf(if (troubled) PROVIDER_HEDGE_FAST_MS else PROVIDER_HEDGE_MS, hedge ?: Long.MAX_VALUE)'''),
    ('''        /** Groq gói miễn phí: Whisper tối đa 20 lượt / phút */
        private const val WHISPER_RPM = 20''', '''        /** Groq gói miễn phí: Whisper tối đa 20 lượt / phút (chừa 2 lượt an toàn) */
        private const val WHISPER_RPM = 18
        /** Phiên dịch trực tiếp: API chính chậm quá 2,5 s → gọi thêm dự phòng */
        private const val LIVE_HEDGE_MS = 2500L'''),
    # Chữ tạm chừa nhiều lượt hơn cho câu đã chốt
    ('''        if (groq && takeWhisper(1, keep = if (quick) 8 else 0)) {''',
     '''        if (groq && takeWhisper(1, keep = if (quick) 9 else 0)) {'''),
    # Câu bịa kinh điển bằng tiếng nước ngoài → bỏ ngay, không tốn lượt nghe lại
    ('''            "спасибо", "спасибо за просмотр", "merci", "gracias", "danke", "grazie", "obrigado", "obrigada",''',
     '''            "спасибо", "спасибо за просмотр", "merci", "gracias", "danke", "grazie", "obrigado", "obrigada",
            "продолжение следует", "субтитры сделал dimatorzok", "редактор субтитров", "to be continued",
            "sous-titres réalisés par la communauté d'amara.org", "untertitel der amara.org-community",
            "ご視聴ありがとうございました", "字幕由amara.org社区提供",'''),
])

# Gọi bản dịch "live" ở 2 màn phiên dịch
patch(J + 'ui/screens/AutoInterpreterScreen.kt', [
    ('''c.ai.askJson(Interpreter.SYSTEM, Interpreter.request(history, side, heard.text), Interpreter.schema())''',
     '''c.ai.askJson(Interpreter.SYSTEM, Interpreter.request(history, side, heard.text), Interpreter.schema(), live = true)'''),
])
patch(J + 'ui/screens/InterpreterScreen.kt', [
    ('''c.ai.askJson(Interpreter.SYSTEM, Interpreter.request(history, side, heard), Interpreter.schema())''',
     '''c.ai.askJson(Interpreter.SYSTEM, Interpreter.request(history, side, heard), Interpreter.schema(), live = true)'''),
])
print('ok')
