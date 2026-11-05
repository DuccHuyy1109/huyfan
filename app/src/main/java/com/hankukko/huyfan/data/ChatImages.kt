package com.hankukko.huyfan.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Ảnh đính kèm trong đoạn chat: lưu trong bộ nhớ riêng của app (không lẫn vào thư viện ảnh),
 * đã xoay đúng chiều và nén nhỏ (~1280px) để gửi AI nhanh. Xoá đoạn chat → xoá luôn file ảnh.
 */
object ChatImages {
    private const val TAG = "ChatImages"
    private const val MAX_SIDE = 1280
    private const val QUALITY = 82

    fun dir(context: Context): File = File(context.filesDir, "chat_images").apply { mkdirs() }

    /** Chỗ để camera ghi ảnh tạm (trả về Uri cho ứng dụng Camera). */
    fun newCameraUri(context: Context): Pair<File, Uri> {
        val f = File(File(context.cacheDir, "camera").apply { mkdirs() }, "shot_${System.currentTimeMillis()}.jpg")
        return f to FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    }

    /** Đọc ảnh từ [uri] → xoay theo EXIF, thu nhỏ, nén JPEG → lưu vào thư mục ảnh chat. Trả về đường dẫn file. */
    fun saveFromUri(context: Context, uri: Uri): String? {
        val t0 = System.currentTimeMillis()
        val fast = if (android.os.Build.VERSION.SDK_INT >= 28) runCatching { saveFast(context, uri) }
            .onFailure { Log.w(TAG, "fast decode failed, fallback", it) }.getOrNull() else null
        val out = fast ?: saveCompat(context, uri)
        Log.i(TAG, "processed in ${System.currentTimeMillis() - t0}ms (${if (fast != null) "ImageDecoder" else "compat"})")
        return out
    }

    /** Android 9+: giải mã thẳng ở kích thước đích (nhanh, ít RAM), tự xoay theo EXIF. */
    @androidx.annotation.RequiresApi(28)
    private fun saveFast(context: Context, uri: Uri): String {
        val src = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
        val bmp = android.graphics.ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(w, h))
            if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.memorySizePolicy = android.graphics.ImageDecoder.MEMORY_POLICY_LOW_RAM
        }
        val f = File(dir(context), "img_${UUID.randomUUID()}.jpg")
        f.outputStream().buffered().use { bmp.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        Log.i(TAG, "saved ${f.name} ${bmp.width}x${bmp.height} ${f.length() / 1024}KB")
        bmp.recycle()
        return f.absolutePath
    }

    private fun saveCompat(context: Context, uri: Uri): String? = runCatching {
        val cr = context.contentResolver
        // 1) Đo kích thước để giải mã vừa đủ (tiết kiệm RAM)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("Không đọc được ảnh")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bmp = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Không đọc được ảnh")
        // 2) Xoay theo EXIF (ảnh chụp bằng camera hay bị nằm ngang)
        val rotation = runCatching {
            cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        // 3) Thu nhỏ cạnh dài nhất về MAX_SIDE
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height))
        val m = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0f) postRotate(rotation)
        }
        val out = if (scale < 1f || rotation != 0f) Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true) else bmp
        val f = File(dir(context), "img_${UUID.randomUUID()}.jpg")
        f.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        if (out !== bmp) bmp.recycle()
        Log.i(TAG, "saved ${f.name} ${out.width}x${out.height} ${f.length() / 1024}KB")
        f.absolutePath
    }.onFailure { Log.w(TAG, "save failed", it) }.getOrNull()

    /** Base64 để gửi AI (null nếu file không còn). */
    fun base64(path: String): String? = runCatching {
        val f = File(path)
        if (!f.exists()) null else Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
    }.getOrNull()

    fun delete(paths: Collection<String>) {
        val n = paths.filter { it.isNotBlank() }.count { p -> runCatching { File(p).delete() }.getOrDefault(false) }
        if (paths.isNotEmpty()) Log.i(TAG, "deleted $n/${paths.size} images")
    }

    /** Xoá ảnh không còn thuộc tin nhắn nào (vd đính kèm rồi bỏ, app bị tắt giữa chừng) + ảnh camera tạm. */
    fun cleanOrphans(context: Context, referenced: Set<String>, keep: String? = null) {
        val now = System.currentTimeMillis()
        dir(context).listFiles()?.forEach { f ->
            // Ảnh vừa đính kèm (chưa gửi) thì chừa lại
            if (f.absolutePath !in referenced && f.absolutePath != keep && now - f.lastModified() > 60 * 60_000L) {
                runCatching { f.delete() }
            }
        }
        File(context.cacheDir, "camera").listFiles()?.forEach { f ->
            if (now - f.lastModified() > 10 * 60_000L) runCatching { f.delete() }
        }
    }
}
