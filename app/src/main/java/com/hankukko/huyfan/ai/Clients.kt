package com.hankukko.huyfan.ai

import android.util.Log
import com.hankukko.huyfan.util.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

/** Lỗi HTTP thô từ nhà cung cấp – AiService sẽ dịch sang câu dễ hiểu. */
class ProviderHttpException(val code: Int, val body: String) : IOException("HTTP $code: ${body.take(300)}")

/** Gọi Gemini API gốc (generateContent) với structured output JSON. */
class GeminiClient(private val http: OkHttpClient) {

    /** Cấu hình "suy nghĩ" đã chạy được cho từng model (null = không gửi). */
    private val workingThinking = mutableMapOf<String, JSONObject?>()

    suspend fun generate(
        baseUrl: String,
        key: String,
        model: String,
        system: String,
        turns: List<Turn>,
        temperature: Float,
        fast: Boolean,
        client: OkHttpClient = http,
        schema: JSONObject? = PromptBuilder.geminiSchema(),
    ): String {
        val candidates: List<JSONObject?> = when {
            !fast -> listOf(null)
            workingThinking.containsKey(model) -> listOf(workingThinking[model])
            else -> thinkingCandidates(model)
        }
        var last: ProviderHttpException? = null
        for (cfg in candidates) {
            try {
                val out = call(client, baseUrl, key, model, system, turns, temperature, cfg, schema)
                workingThinking[model] = cfg
                return out
            } catch (e: ProviderHttpException) {
                // Model không nhận kiểu cấu hình này → thử kiểu tiếp theo
                if (cfg != null && e.code == 400 && e.body.contains("thinking", ignoreCase = true)) {
                    last = e
                    continue
                }
                throw e
            }
        }
        throw last ?: IllegalStateException("no thinking config")
    }

    private suspend fun call(
        client: OkHttpClient, baseUrl: String, key: String, model: String, system: String,
        turns: List<Turn>, temperature: Float, thinking: JSONObject?,
        schema: JSONObject?,
    ): String {
        val contents = JSONArray()
        mergeTurns(turns).forEach { t ->
            val parts = JSONArray()
            t.image?.let { com.hankukko.huyfan.data.ChatImages.base64(it) }?.let { b64 ->
                parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", b64)))
            }
            t.audioWav?.let { wav ->
                val b64 = android.util.Base64.encodeToString(wav, android.util.Base64.NO_WRAP)
                parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "audio/wav").put("data", b64)))
            }
            parts.put(JSONObject().put("text", t.text.ifBlank { "(ảnh)" }))
            contents.put(
                JSONObject()
                    .put("role", if (t.role == Turn.USER) "user" else "model")
                    .put("parts", parts)
            )
        }
        val gen = JSONObject()
            .put("temperature", temperature.toDouble())
            .put("maxOutputTokens", 4096)
            .put("responseMimeType", "application/json")
        if (schema != null) gen.put("responseSchema", schema)
        if (thinking != null) gen.put("thinkingConfig", thinking)

        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", gen)

        val req = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody(JSON_TYPE))
            .build()

        val t0 = System.currentTimeMillis()
        client.newCall(req).await().use { res ->
            val text = res.body?.string().orEmpty()
            Log.i(TAG, "generate $model thinking=$thinking → HTTP ${res.code} in ${System.currentTimeMillis() - t0}ms")
            if (!res.isSuccessful) {
                Log.w(TAG, "error body: ${text.take(600)}")
                throw ProviderHttpException(res.code, text)
            }
            val o = JSONObject(text)
            val cand = o.optJSONArray("candidates")?.optJSONObject(0)
            if (cand == null) {
                val reason = o.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                throw AiException("blocked $reason", "Gemini từ chối trả lời câu này ($reason). Ông hỏi cách khác thử nha.")
            }
            val parts = cand.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
            val out = buildString {
                for (i in 0 until parts.length()) {
                    val p = parts.optJSONObject(i) ?: continue
                    if (p.optBoolean("thought", false)) continue
                    append(p.optString("text"))
                }
            }
            if (out.isBlank()) {
                throw AiException(
                    "empty (${cand.optString("finishReason")})",
                    "Gemini trả về rỗng (${cand.optString("finishReason")}). Thử lại giúp tui nha."
                )
            }
            return out
        }
    }

    suspend fun listModels(baseUrl: String, key: String): List<String> {
        val req = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/models?pageSize=1000")
            .header("x-goog-api-key", key)
            .get().build()
        http.newCall(req).await().use { res ->
            val text = res.body?.string().orEmpty()
            Log.i(TAG, "listModels → HTTP ${res.code}")
            if (!res.isSuccessful) {
                Log.w(TAG, "error body: ${text.take(600)}")
                throw ProviderHttpException(res.code, text)
            }
            val arr = JSONObject(text).optJSONArray("models") ?: JSONArray()
            return (0 until arr.length()).mapNotNull { i ->
                val m = arr.optJSONObject(i) ?: return@mapNotNull null
                val methods = m.optJSONArray("supportedGenerationMethods")
                val ok = methods == null || (0 until methods.length()).any { methods.optString(it) == "generateContent" }
                if (ok) m.optString("name").removePrefix("models/") else null
            }.filter { it.isNotBlank() }
        }
    }

    companion object {
        private const val TAG = "HuyFanAI"
        private val versionRe = Regex("gemini-(\\d+(?:\\.\\d+)?)")
        private val excluded = listOf(
            "tts", "image", "live", "audio", "embedding", "vision", "thinking", "learnlm", "gemma",
            "robotics", "computer", "native", "translate", "banana", "omni", "veo", "imagen", "aqa", "nano"
        )

        /** Model chat được (loại bỏ model đọc giọng -tts, tạo ảnh, live, embedding…). */
        fun isChatModel(model: String): Boolean = model.startsWith("gemini") && excluded.none { model.contains(it) }

        /** Alias "-latest" không rõ phiên bản → xếp sau các model có số phiên bản cụ thể. */
        fun versionOf(model: String): Double =
            versionRe.find(model)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

        /** Các model chữ dòng Flash đáng đo tốc độ (mới nhất trước, tối đa [limit]). */
        fun benchCandidates(models: List<String>, limit: Int = 8): List<String> =
            models.filter { m -> m.startsWith("gemini") && m.contains("flash") && excluded.none { m.contains(it) } }
                // Dòng 2.x đã ngừng cho người dùng mới (404) → bỏ, chỉ giữ 3.x trở lên và alias -latest
                .filter { versionOf(it) >= 3.0 || it.contains("-latest") }
                .sortedWith(compareByDescending<String> { !it.contains("preview") }.thenByDescending { versionOf(it) })
                .take(limit)

        /** Chọn model Flash tốt nhất trong danh sách (ổn định > preview, đúng ưu tiên lite/không lite, mới nhất). */
        fun pickBest(models: List<String>, preferLite: Boolean): String? {
            val candidates = models.filter { m ->
                m.startsWith("gemini") && m.contains("flash") && excluded.none { m.contains(it) }
            }
            if (candidates.isEmpty()) return null
            return candidates.sortedWith(
                compareByDescending<String> { it.contains("lite") == preferLite }
                    .thenByDescending { !it.contains("preview") && !it.contains("exp") }
                    .thenByDescending { versionOf(it) }
                    .thenBy { it.length }
            ).first()
        }

        /**
         * Các cách tắt/giảm "suy nghĩ" theo thứ tự thử: Gemini 3+ dùng thinkingLevel, 2.5 dùng thinkingBudget.
         * Alias "-latest" không rõ phiên bản → thử cả hai, cuối cùng là không gửi gì.
         */
        fun thinkingCandidates(model: String): List<JSONObject?> {
            val v = versionOf(model)
            val isPro = model.contains("pro")
            val minimal = JSONObject().put("thinkingLevel", "minimal")
            val low = JSONObject().put("thinkingLevel", "low")
            val budget = JSONObject().put("thinkingBudget", if (isPro) 128 else 0)
            return when {
                isPro -> listOf(low, null)
                v >= 3.0 || model.contains("-latest") -> listOf(minimal, low, null)
                v >= 2.5 -> listOf(budget, null)
                else -> listOf(null)
            }
        }

        /** Gộp các lượt cùng vai liên tiếp và bỏ lượt "model" ở đầu (Gemini yêu cầu bắt đầu bằng user). */
        fun mergeTurns(turns: List<Turn>): List<Turn> {
            val out = mutableListOf<Turn>()
            for (t in turns) {
                if (out.isEmpty() && t.role != Turn.USER) continue
                val last = out.lastOrNull()
                if (last != null && last.role == t.role) out[out.lastIndex] = Turn(t.role, last.text + "\n" + t.text, t.image ?: last.image, t.audioWav ?: last.audioWav)
                else out += t
            }
            return out
        }
    }
}

/** Gọi bất kỳ API chuẩn OpenAI: Groq, OpenRouter, Gemini OpenAI-compat, Ollama, LM Studio... */
class OpenAiCompatClient(private val http: OkHttpClient) {

    private val noJsonMode = mutableSetOf<String>()

    /** [logprob] = độ tin cậy trung bình (gần 0 = chắc chắn, càng âm càng đoán mò). */
    data class Transcript(
        val text: String,
        val language: String,
        val noSpeech: Double,
        val logprob: Double = 0.0,
        /** > 2,4 = chữ lặp đi lặp lại (Whisper bị "kẹt vòng" khi đoán mò). */
        val compression: Double = 0.0,
    )

    /**
     * POST {base}/audio/transcriptions (Whisper).
     * [language] = null → model tự nhận ra ngôn ngữ; "vi"/"ko" → ép nghe theo ngôn ngữ đó.
     */
    suspend fun transcribe(baseUrl: String, key: String, wav: ByteArray, model: String, prompt: String?, language: String? = null): Transcript {
        val builder = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("file", "speech.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("temperature", "0")
        if (!prompt.isNullOrBlank()) builder.addFormDataPart("prompt", prompt)
        if (language != null) builder.addFormDataPart("language", language)
        val body = builder.build()
        val req = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/audio/transcriptions")
            .header("Authorization", "Bearer $key")
            .post(body)
            .build()
        val t0 = System.currentTimeMillis()
        http.newCall(req).await().use { res ->
            val text = res.body?.string().orEmpty()
            Log.i("HuyFanAI", "whisper $model${language?.let { " [$it]" } ?: ""} → HTTP ${res.code} in ${System.currentTimeMillis() - t0}ms")
            if (!res.isSuccessful) throw ProviderHttpException(res.code, text)
            val o = JSONObject(text)
            val segs = o.optJSONArray("segments") ?: JSONArray()
            val noSpeech = if (segs.length() == 0) 0.0 else
                (0 until segs.length()).minOf { segs.optJSONObject(it)?.optDouble("no_speech_prob", 0.0) ?: 0.0 }
            var lp = 0.0
            var dur = 0.0
            var comp = 0.0
            for (i in 0 until segs.length()) {
                val sg = segs.optJSONObject(i) ?: continue
                val d = (sg.optDouble("end", 0.0) - sg.optDouble("start", 0.0)).coerceAtLeast(0.1)
                lp += sg.optDouble("avg_logprob", 0.0) * d
                dur += d
                comp = maxOf(comp, sg.optDouble("compression_ratio", 0.0))
            }
            return Transcript(o.optString("text").trim(), o.optString("language"), noSpeech, if (dur > 0) lp / dur else 0.0, comp)
        }
    }

    suspend fun generate(
        baseUrl: String,
        key: String,
        model: String,
        system: String,
        turns: List<Turn>,
        temperature: Float,
    ): String {
        val jsonMode = model !in noJsonMode
        return try {
            call(baseUrl, key, model, system, turns, temperature, jsonMode)
        } catch (e: ProviderHttpException) {
            if (jsonMode && e.code in listOf(400, 422) && e.body.contains("response_format", ignoreCase = true)) {
                noJsonMode += model
                call(baseUrl, key, model, system, turns, temperature, false)
            } else throw e
        }
    }

    private suspend fun call(
        baseUrl: String, key: String, model: String, system: String,
        turns: List<Turn>, temperature: Float, jsonMode: Boolean,
    ): String {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        turns.forEach {
            val b64 = it.image?.let { p -> com.hankukko.huyfan.data.ChatImages.base64(p) }
            val content: Any = if (b64 == null) it.text else JSONArray()
                .put(JSONObject().put("type", "text").put("text", it.text.ifBlank { "(ảnh)" }))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64")))
            messages.put(JSONObject().put("role", if (it.role == Turn.USER) "user" else "assistant").put("content", content))
        }
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", temperature.toDouble())
            .put("max_tokens", 1500)
        if (jsonMode) body.put("response_format", JSONObject().put("type", "json_object"))

        val rb = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .header("X-Title", "HUY FAN")
            .post(body.toString().toRequestBody(JSON_TYPE))
        if (key.isNotBlank()) rb.header("Authorization", "Bearer $key")

        http.newCall(rb.build()).await().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw ProviderHttpException(res.code, text)
            val content = JSONObject(text).optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content").orEmpty()
            if (content.isBlank()) throw AiException("empty", "API trả về rỗng. Thử lại giúp tui nha.")
            // Một số model "suy nghĩ" kèm thẻ <think>…</think>
            return content.replace(Regex("(?s)<think>.*?</think>"), "").trim()
        }
    }

    suspend fun listModels(baseUrl: String, key: String): List<String> {
        val rb = Request.Builder().url("${baseUrl.trimEnd('/')}/models").get()
        if (key.isNotBlank()) rb.header("Authorization", "Bearer $key")
        http.newCall(rb.build()).await().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw ProviderHttpException(res.code, text)
            val arr = JSONObject(text).optJSONArray("data") ?: JSONArray()
            return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("id") }
                .filter { it.isNotBlank() }.sorted()
        }
    }
}
