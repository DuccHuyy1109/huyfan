package com.hankukko.huyfan.ink

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 1 điểm của nét vẽ (toạ độ + thời gian ms). */
data class InkPoint(val x: Float, val y: Float, val t: Long)

/**
 * Nhận dạng chữ viết tay bằng Google ML Kit Digital Ink – chạy trên máy, miễn phí.
 * Lần đầu dùng 1 ngôn ngữ sẽ tải bộ nhận dạng (~20MB).
 */
class InkRecognizer {

    private val manager = RemoteModelManager.getInstance()
    private val clients = mutableMapOf<String, DigitalInkRecognizer>()

    private fun model(lang: String): DigitalInkRecognitionModel {
        val id = runCatching { DigitalInkRecognitionModelIdentifier.fromLanguageTag(lang) }.getOrNull()
            ?: throw IllegalStateException("Chưa hỗ trợ nhận dạng chữ viết cho ngôn ngữ $lang")
        return DigitalInkRecognitionModel.builder(id).build()
    }

    suspend fun isDownloaded(lang: String): Boolean = runCatching { manager.isModelDownloaded(model(lang)).await() }.getOrDefault(false)

    /** Trả về các phương án chữ đọc được (tốt nhất trước). */
    suspend fun recognize(lang: String, strokes: List<List<InkPoint>>): List<String> {
        val m = model(lang)
        if (!manager.isModelDownloaded(m).await()) {
            Log.i(TAG, "downloading ink model $lang")
            manager.download(m, DownloadConditions.Builder().build()).await()
        }
        val client = synchronized(clients) {
            clients.getOrPut(lang) { DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(m).build()) }
        }
        val ink = Ink.builder().apply {
            strokes.filter { it.isNotEmpty() }.forEach { st ->
                val b = Ink.Stroke.builder()
                st.forEach { b.addPoint(Ink.Point.create(it.x, it.y, it.t)) }
                addStroke(b.build())
            }
        }.build()
        val result = client.recognize(ink).await()
        val out = result.candidates.map { it.text }
        Log.i(TAG, "ink $lang → ${out.take(3)}")
        return out
    }

    companion object {
        private const val TAG = "HuyFanInk"
    }
}

/** Chờ Task của Google Play Services dạng suspend. */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { if (cont.isActive) cont.resume(it) }
    addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
