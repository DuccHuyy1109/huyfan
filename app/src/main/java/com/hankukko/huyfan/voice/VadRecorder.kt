package com.hankukko.huyfan.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Ghi âm liên tục và tự cắt thành từng câu nói (VAD theo năng lượng, tự thích nghi với tiếng ồn nền).
 * Mỗi câu (PCM 16 kHz mono 16-bit) được trả về qua [onSegment] để nhận dạng + dịch.
 */
class VadRecorder(
    private val scope: CoroutineScope,
    /** [speechMs] = thời gian thật sự có tiếng nói trong câu (để kiểm tra chữ nhận dạng có hợp lý không). */
    private val onSegment: (pcm: ByteArray, ms: Long, speechMs: Long) -> Unit,
    private val onLevel: (Float, Boolean) -> Unit = { _, _ -> },
    /** Đang nói dở: gửi phần âm thanh đã có (mỗi ~1,5 s) để hiện chữ tạm thời. [seq] = số thứ tự câu. */
    private val onPartial: ((seq: Int, pcm: ByteArray) -> Unit)? = null,
) {
    /** Số thứ tự câu hiện tại (tăng mỗi khi 1 câu kết thúc). */
    @Volatile
    var seq = 0
        private set
    companion object {
        private const val TAG = "VadRecorder"
        const val RATE = 16000
        private const val FRAME = 480 // 30 ms
        private const val FRAME_MS = 30
        private const val PRE_ROLL_FRAMES = 10 // 300 ms trước khi bắt đầu nói
        private const val START_FRAMES = 3     // ~90 ms vượt ngưỡng mới tính là nói
        private const val END_SILENCE_MS = 750 // im 0,75 s → hết câu
        private const val MIN_SPEECH_MS = 220 // đủ cho "네", "응", "ừ"
        private const val MAX_SEGMENT_MS = 12_000
        private const val LONG_SEGMENT_MS = 5_000
        private const val SHORT_END_SILENCE_MS = 450
        private const val PARTIAL_FIRST_MS = 1_200
        private const val PARTIAL_EVERY_MS = 1_500
        /** Câu phải có ít nhất chừng này ms giọng người (dây thanh rung) – tiếng gõ, sột soạt, lạch cạch không có. */
        private const val MIN_VOICED_MS = 120

        /**
         * Khung 30 ms này có phải giọng người không: giọng nói (nguyên âm) dao động lặp lại đều theo cao độ 80–400 Hz,
         * còn tiếng gõ / sột soạt / quạt thì không → đo tự tương quan theo cao độ.
         */
        fun isVoiced(f: ShortArray, n: Int): Boolean {
            var best = 0.0
            var lag = 40 // 400 Hz
            while (lag <= 200) { // 80 Hz
                var s = 0.0
                var e1 = 0.0
                var e2 = 0.0
                var i = 0
                while (i < n - lag) {
                    val a = f[i].toDouble()
                    val b = f[i + lag].toDouble()
                    s += a * b; e1 += a * a; e2 += b * b
                    i++
                }
                if (e1 > 0 && e2 > 0) {
                    val r = s / sqrt(e1 * e2)
                    if (r > best) best = r
                }
                lag += 2
            }
            return best > 0.55
        }

        /** PCM 16-bit mono → WAV. */
        fun wav(pcm: ByteArray, rate: Int = RATE): ByteArray {
            val b = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
            b.put("RIFF".toByteArray()); b.putInt(36 + pcm.size); b.put("WAVE".toByteArray())
            b.put("fmt ".toByteArray()); b.putInt(16); b.putShort(1); b.putShort(1)
            b.putInt(rate); b.putInt(rate * 2); b.putShort(2); b.putShort(16)
            b.put("data".toByteArray()); b.putInt(pcm.size); b.put(pcm)
            return b.array()
        }
    }

    private var job: Job? = null
    @Volatile
    var paused = false

    /** App đang tự đọc to → không nghe (tránh nghe lại chính tiếng loa của mình rồi dịch tiếp). */
    @Volatile
    private var muted = false

    @Volatile
    private var muteUntil = 0L

    /** Bật/tắt "bịt tai". Tắt xong vẫn bỏ thêm [tailMs] để tiếng vang của loa tắt hẳn. */
    fun mute(on: Boolean, tailMs: Long = 700) {
        if (on) muted = true
        else { muteUntil = System.currentTimeMillis() + tailMs; muted = false }
    }

    val isRunning: Boolean get() = job?.isActive == true

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (isRunning) return true
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, FRAME * 2 * 8),
            )
        }.getOrNull() ?: return false
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return false }
        rec.startRecording()
        job = scope.launch(Dispatchers.IO) {
            val frame = ShortArray(FRAME)
            val pre = ArrayDeque<ShortArray>()
            val seg = ByteArrayOutputStream()
            var floor = -55.0
            var speaking = false
            var loud = 0
            var silentMs = 0
            var segMs = 0
            var speechMs = 0
            var nextPartial = PARTIAL_FIRST_MS
            var voicedRun = 0
            var voicedMs = 0
            fun append(f: ShortArray) {
                val bb = ByteBuffer.allocate(f.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                f.forEach { bb.putShort(it) }
                seg.write(bb.array())
            }
            /** Bỏ câu đang thu dở (tiếng loa của app) – không gửi đi nhận dạng. */
            fun discard() {
                if (speaking) Log.i(TAG, "bỏ ${segMs}ms âm thanh lúc app đang đọc to")
                seg.reset(); speaking = false; silentMs = 0; segMs = 0; speechMs = 0; loud = 0; voicedRun = 0; voicedMs = 0
                nextPartial = PARTIAL_FIRST_MS
                seq++
            }
            fun flush() {
                if (speechMs >= MIN_SPEECH_MS && voicedMs >= MIN_VOICED_MS) {
                    val bytes = seg.toByteArray()
                    Log.i(TAG, "câu ${segMs}ms (nói ${speechMs}ms, giọng người ${voicedMs}ms)")
                    onSegment(bytes, segMs.toLong(), speechMs.toLong())
                } else if (speechMs >= MIN_SPEECH_MS) {
                    Log.i(TAG, "bỏ ${segMs}ms tiếng động – không có giọng người (giọng ${voicedMs}ms)")
                }
                seg.reset(); speaking = false; silentMs = 0; segMs = 0; speechMs = 0; loud = 0; voicedRun = 0; voicedMs = 0
                nextPartial = PARTIAL_FIRST_MS
                seq++
            }
            try {
                while (isActive) {
                    val n = rec.read(frame, 0, FRAME)
                    if (n <= 0) continue
                    if (paused) {
                        if (speaking) flush()
                        pre.clear(); onLevel(0f, false); continue
                    }
                    if (muted || System.currentTimeMillis() < muteUntil) {
                        if (speaking) discard()
                        pre.clear(); loud = 0; onLevel(0f, false); continue
                    }
                    var sum = 0.0
                    for (i in 0 until n) sum += frame[i].toDouble() * frame[i]
                    val rms = sqrt(sum / n)
                    val db = if (rms < 1) -90.0 else 20 * log10(rms / 32768.0)
                    val copy = frame.copyOf(n)
                    // Người Hàn hay nói nhỏ → ngưỡng bắt đầu nói thấp hơn chút
                    val isLoud = db > maxOf(floor + 10, -52.0)
                    if (!speaking) {
                        // Tiếng ồn nền thay đổi dần → ngưỡng tự thích nghi
                        floor = if (db < floor) floor * 0.9 + db * 0.1 else floor * 0.995 + db * 0.005
                        pre.addLast(copy); if (pre.size > PRE_ROLL_FRAMES) pre.removeFirst()
                        loud = if (isLoud) loud + 1 else 0
                        if (loud >= START_FRAMES) {
                            speaking = true
                            pre.forEach { append(it); segMs += FRAME_MS; if (isVoiced(it, it.size)) voicedMs += FRAME_MS }
                            pre.clear()
                            speechMs = loud * FRAME_MS
                        }
                    } else {
                        append(copy); segMs += FRAME_MS
                        if (db > floor + 7) {
                            speechMs += FRAME_MS
                            if (isVoiced(copy, n)) voicedMs += FRAME_MS
                            voicedRun++
                            // Phải có tiếng nói liền ~90 ms mới tính là nói tiếp; tiếng lạch cạch ngắn không làm câu dài ra
                            if (voicedRun >= 3) silentMs = 0 else silentMs += FRAME_MS
                        } else {
                            voicedRun = 0
                            silentMs += FRAME_MS
                            // Tiếng ồn nền tăng dần (quạt, xe, TV) → ngưỡng tự nâng theo
                            floor = floor * 0.99 + db * 0.01
                        }
                        if (onPartial != null && segMs >= nextPartial && silentMs < END_SILENCE_MS && voicedMs >= MIN_VOICED_MS) {
                            nextPartial = segMs + PARTIAL_EVERY_MS
                            onPartial.invoke(seq, seg.toByteArray())
                        }
                        // Câu đã dài → ngừng ngắn hơn cũng tính là hết câu (dịch từng câu, không dồn cục)
                        val endMs = if (segMs > LONG_SEGMENT_MS) SHORT_END_SILENCE_MS else END_SILENCE_MS
                        if (silentMs >= endMs || segMs >= MAX_SEGMENT_MS) flush()
                    }
                    onLevel(((db + 60) / 50).toFloat().coerceIn(0f, 1f), speaking)
                }
            } finally {
                runCatching { rec.stop() }
                rec.release()
            }
        }
        return true
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
