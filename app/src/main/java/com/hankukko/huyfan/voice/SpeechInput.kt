package com.hankukko.huyfan.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/** Bọc SpeechRecognizer của hệ thống (ưu tiên Google – nhận tiếng Việt tốt nhất). */
class SpeechInput(private val context: Context) {

    interface Listener {
        fun onReady() {}
        fun onRms(db: Float) {}
        fun onPartial(text: String) {}
        fun onResult(text: String)
        fun onError(code: Int)
    }

    private var recognizer: SpeechRecognizer? = null
    private var session = 0
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var silenceCheck: Runnable? = null
    private val watchdogs = mutableListOf<Runnable>()

    /** [endSilenceMs]: đã nghe được chữ mà im lặng quá khoảng này → chốt câu, gửi luôn. */
    fun start(language: String, preferGoogle: Boolean, listener: Listener, endSilenceMs: Long = 1500L) {
        cancel()
        val r = create(preferGoogle)
        if (r == null) {
            listener.onError(ERROR_UNAVAILABLE)
            return
        }
        recognizer = r
        Log.i(TAG, "start lang=$language google=$preferGoogle")
        val my = ++session
        r.setRecognitionListener(object : RecognitionListener {
            private var partial = ""
            private var done = false
            private fun alive() = my == session && !done

            override fun onReadyForSpeech(params: Bundle?) {
                Log.i(TAG, "ready ($language)")
                if (!alive()) return
                listener.onReady()
                // Nơi ồn (nhất là màn hình khoá) Google có thể nghe mãi → tự giới hạn
                watchdogs.forEach { handler.removeCallbacks(it) }
                watchdogs.clear()
                watchdogs += Runnable {
                    if (alive() && partial.isBlank()) {
                        Log.i(TAG, "no speech ${NO_SPEECH_MS}ms → stop")
                        done = true
                        listener.onError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                        cancelRecognizerOnly()
                    }
                }.also { handler.postDelayed(it, NO_SPEECH_MS) }
                watchdogs += Runnable {
                    if (alive()) {
                        Log.i(TAG, "max listen ${MAX_LISTEN_MS}ms → stop")
                        done = true
                        if (partial.isNotBlank()) listener.onResult(partial) else listener.onError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                        cancelRecognizerOnly()
                    }
                }.also { handler.postDelayed(it, MAX_LISTEN_MS) }
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                if (alive()) listener.onRms(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                Log.i(TAG, "error $error alive=${alive()} partial='${partial.take(40)}'")
                if (!alive()) return
                done = true
                // Có kết quả tạm mà bị lỗi "không khớp" → dùng luôn kết quả tạm
                if ((error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) && partial.isNotBlank()) {
                    listener.onResult(partial)
                } else listener.onError(error)
            }

            override fun onResults(results: Bundle?) {
                if (!alive()) return
                done = true
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                Log.i(TAG, "result '${text.take(80)}'")
                listener.onResult(text.ifBlank { partial })
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!alive()) return
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank() && text != partial) {
                    partial = text
                    listener.onPartial(text)
                    // Có chữ mới → hẹn giờ chốt câu nếu im lặng đủ lâu
                    silenceCheck?.let { handler.removeCallbacks(it) }
                    silenceCheck = Runnable {
                        if (alive()) {
                            Log.i(TAG, "silence ${endSilenceMs}ms → submit '${partial.take(40)}'")
                            done = true
                            listener.onResult(partial)
                            cancelRecognizerOnly()
                        }
                    }.also { handler.postDelayed(it, endSilenceMs) }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            // Nghiêng về các cụm hay dùng → bớt nghe nhầm "tiếng Hàn" thành "tiếng Anh"
            if (Build.VERSION.SDK_INT >= 33 && language == "vi-VN") {
                putStringArrayListExtra(
                    RecognizerIntent.EXTRA_BIASING_STRINGS,
                    arrayListOf("tiếng Hàn", "tiếng Hàn là gì", "là gì", "dịch sang tiếng Hàn", "ngữ pháp", "HUY FAN", "danh sách từ hôm nay"),
                )
            }
        }
        runCatching { r.startListening(intent) }.onFailure { listener.onError(SpeechRecognizer.ERROR_CLIENT) }
    }

    private fun create(preferGoogle: Boolean): SpeechRecognizer? {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return null
        if (preferGoogle) {
            googleService()?.let { return runCatching { SpeechRecognizer.createSpeechRecognizer(context, it) }.getOrNull() }
        }
        return runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
    }

    private fun googleService(): ComponentName? = runCatching {
        context.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .firstOrNull { it.serviceInfo.packageName == "com.google.android.googlequicksearchbox" }
            ?.let { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
    }.getOrNull()

    /** Dừng nghe và trả kết quả ngay. */
    fun stop() {
        runCatching { recognizer?.stopListening() }
    }

    private fun cancelRecognizerOnly() {
        recognizer?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
    }

    /** Huỷ, không trả kết quả. */
    fun cancel() {
        silenceCheck?.let { handler.removeCallbacks(it) }
        silenceCheck = null
        watchdogs.forEach { handler.removeCallbacks(it) }
        watchdogs.clear()
        session++
        recognizer?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
    }

    companion object {
        private const val TAG = "HuyFanVoice"
        /** Chưa nghe ra chữ nào sau khoảng này → coi như im lặng */
        private const val NO_SPEECH_MS = 8000L
        /** Giới hạn 1 lượt nghe */
        private const val MAX_LISTEN_MS = 25000L
        const val ERROR_UNAVAILABLE = -1

        fun isSilence(code: Int) =
            code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

        fun describe(code: Int): String = when (code) {
            ERROR_UNAVAILABLE -> "Máy chưa có dịch vụ nhận diện giọng nói (cài/ cập nhật ứng dụng Google)."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Chưa cấp quyền micro cho HUY FAN."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Mất mạng – nhận diện giọng nói cần Internet."
            SpeechRecognizer.ERROR_AUDIO -> "Micro đang bị app khác dùng."
            SpeechRecognizer.ERROR_SERVER -> "Máy chủ nhận diện giọng nói lỗi, thử lại nha."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Bộ nhận diện đang bận, thử lại nha."
            12, 13 -> "Chưa có gói ngôn ngữ cho nhận diện giọng nói. Vào ứng dụng Google → Giọng nói để tải tiếng Việt/Hàn."
            else -> "Lỗi nhận diện giọng nói ($code)."
        }
    }
}
