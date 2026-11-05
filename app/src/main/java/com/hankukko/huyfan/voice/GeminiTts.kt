package com.hankukko.huyfan.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.hankukko.huyfan.util.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Âm thanh PCM 16-bit mono. */
class Pcm(val bytes: ByteArray, val sampleRate: Int)

/** 1 giọng AI dựng sẵn của Gemini. */
data class AiVoice(val id: String, val female: Boolean, val desc: String)

/** 14 giọng tiêu biểu (7 nữ, 7 nam) – đều đọc được tiếng Việt và tiếng Hàn. */
val AI_VOICES = listOf(
    AiVoice("Kore", true, "Rõ ràng, chắc giọng"),
    AiVoice("Aoede", true, "Nhẹ nhàng, thoáng"),
    AiVoice("Leda", true, "Trẻ trung"),
    AiVoice("Zephyr", true, "Tươi sáng"),
    AiVoice("Sulafat", true, "Ấm áp"),
    AiVoice("Despina", true, "Mượt mà"),
    AiVoice("Achernar", true, "Dịu dàng"),
    AiVoice("Puck", false, "Vui tươi, năng động"),
    AiVoice("Charon", false, "Điềm đạm, truyền cảm"),
    AiVoice("Fenrir", false, "Hào hứng"),
    AiVoice("Achird", false, "Thân thiện"),
    AiVoice("Algieba", false, "Trầm mượt"),
    AiVoice("Sadachbia", false, "Sôi nổi"),
    AiVoice("Orus", false, "Chắc, rõ"),
)

/** Gọi Gemini TTS (responseModalities = AUDIO) để tạo giọng đọc tự nhiên. */
class GeminiTts(http: OkHttpClient) {

    private val client = http.newBuilder().readTimeout(20, TimeUnit.SECONDS).build()
    private val json = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var models: List<String>? = null

    private suspend fun ttsModels(base: String, key: String): List<String> {
        models?.let { return it }
        val req = Request.Builder().url("${base.trimEnd('/')}/models?pageSize=1000").header("x-goog-api-key", key).get().build()
        val list = client.newCall(req).await().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            val arr = JSONObject(text).optJSONArray("models") ?: JSONArray()
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name")?.removePrefix("models/") }
        }
        val versionRe = Regex("gemini-(\\d+(?:\\.\\d+)?)")
        fun ver(m: String) = versionRe.find(m)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        // Ưu tiên bản ổn định, bản lite (nhanh) trước, phiên bản mới trước
        val sorted = list.filter { it.contains("tts") && !it.contains("live") }
            .sortedWith(
                compareByDescending<String> { !it.contains("preview") }
                    .thenByDescending { it.contains("lite") }
                    .thenByDescending { ver(it) }
            )
        Log.i(TAG, "tts models: $sorted")
        models = sorted
        return sorted
    }

    suspend fun synth(base: String, key: String, text: String, voice: String): Pcm {
        val candidates = ttsModels(base, key).ifEmpty { throw IOException("Key không có model giọng AI (TTS)") }
        var last: Exception? = null
        for (m in candidates.take(3)) {
            try {
                return call(base, key, m, text, voice)
            } catch (e: HttpError) {
                last = e
                // Model bị gỡ/quá tải → thử model khác; lỗi khác thì dừng
                if (e.code !in setOf(404, 429, 500, 503)) throw e
                if (e.code == 404) models = models?.minus(m)
            }
        }
        throw last ?: IOException("Không tạo được giọng AI")
    }

    private class HttpError(val code: Int, msg: String) : IOException(msg)

    private suspend fun call(base: String, key: String, model: String, text: String, voice: String): Pcm {
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))))
            .put(
                "generationConfig", JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put(
                        "speechConfig", JSONObject().put(
                            "voiceConfig", JSONObject().put(
                                "prebuiltVoiceConfig", JSONObject().put("voiceName", voice)
                            )
                        )
                    )
            )
        val req = Request.Builder()
            .url("${base.trimEnd('/')}/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody(json))
            .build()
        val t0 = System.currentTimeMillis()
        client.newCall(req).await().use { res ->
            val txt = res.body?.string().orEmpty()
            Log.i(TAG, "tts $model/$voice → HTTP ${res.code} in ${System.currentTimeMillis() - t0}ms")
            if (!res.isSuccessful) {
                val msg = runCatching { JSONObject(txt).optJSONObject("error")?.optString("message") }.getOrNull()
                throw HttpError(res.code, "Giọng AI lỗi ${res.code}${msg?.let { ": " + it.take(120) } ?: ""}")
            }
            val parts = JSONObject(txt).optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
            for (i in 0 until parts.length()) {
                val inline = parts.optJSONObject(i)?.optJSONObject("inlineData") ?: continue
                val data = inline.optString("data")
                if (data.isBlank()) continue
                val mime = inline.optString("mimeType")
                val rate = Regex("rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull() ?: 24000
                return Pcm(Base64.decode(data, Base64.DEFAULT), rate)
            }
            throw IOException("Giọng AI không trả âm thanh")
        }
    }

    companion object {
        private const val TAG = "HuyFanVoice"
    }
}

/** Phát PCM bằng AudioTrack, báo khi phát xong. */
class PcmPlayer {
    private val main = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private var doneTask: Runnable? = null

    fun play(pcm: Pcm, speed: Float, onDone: () -> Unit) {
        stop()
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(pcm.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.bytes.size.coerceAtLeast(4096))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(pcm.bytes, 0, pcm.bytes.size)
        val sp = speed.coerceIn(0.5f, 2.0f)
        runCatching { t.playbackParams = PlaybackParams().setSpeed(sp) }
        track = t
        t.play()
        // Thời lượng = số mẫu / tần số; chia tốc độ phát
        val ms = (pcm.bytes.size / 2 * 1000L / pcm.sampleRate / sp).toLong() + 250
        doneTask = Runnable {
            stop()
            onDone()
        }.also { main.postDelayed(it, ms) }
    }

    fun stop() {
        doneTask?.let { main.removeCallbacks(it) }
        doneTask = null
        track?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        track = null
    }
}
