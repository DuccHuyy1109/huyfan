package com.hankukko.huyfan.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hankukko.huyfan.data.ChatImages
import com.hankukko.huyfan.ui.theme.Brand
import com.hankukko.huyfan.ui.theme.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Giải mã ảnh từ file ở luồng nền, thu nhỏ vừa [maxPx] cạnh dài. */
@Composable
fun rememberChatBitmap(path: String, maxPx: Int): ImageBitmap? {
    val state by produceState<ImageBitmap?>(null, path, maxPx) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val f = File(path)
                if (!f.exists()) return@runCatching null
                val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, b)
                var s = 1
                while (maxOf(b.outWidth, b.outHeight) / (s * 2) >= maxPx) s *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
            }.getOrNull()
        }
    }
    return state
}

/** Nút 📷: chụp ảnh hoặc chọn từ thư viện → lưu vào thư mục ảnh chat rồi trả đường dẫn. */
@Composable
fun AttachImageButton(
    onPicked: (String) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    onLaunch: () -> Unit = {},
    onBusy: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    fun importUri(uri: Uri?) {
        com.hankukko.huyfan.ForegroundTracker.setPickingImage(false)
        if (uri == null) return
        busy = true
        onBusy(true)
        scope.launch {
            val path = withContext(Dispatchers.IO) { ChatImages.saveFromUri(context, uri) }
            busy = false
            onBusy(false)
            cameraFile?.delete(); cameraFile = null
            if (path != null) onPicked(path)
            else android.widget.Toast.makeText(context, "Không mở được ảnh này", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> importUri(uri) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val shoot = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) importUri(cameraUri) else {
            com.hankukko.huyfan.ForegroundTracker.setPickingImage(false)
            cameraFile?.delete(); cameraFile = null
        }
    }

    Box(modifier) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .clickable(enabled = !busy) { menu = true },
            contentAlignment = Alignment.Center,
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(size * 0.5f), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.AddPhotoAlternate, "Hỏi bằng ảnh", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size * 0.55f))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Chụp ảnh") },
                leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) },
                onClick = {
                    menu = false
                    val (f, u) = ChatImages.newCameraUri(context)
                    cameraFile = f
                    cameraUri = u
                    onLaunch()
                    com.hankukko.huyfan.ForegroundTracker.setPickingImage(true)
                    runCatching { shoot.launch(u) }.onFailure {
                        android.widget.Toast.makeText(context, "Không mở được camera", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
            )
            DropdownMenuItem(
                text = { Text("Chọn từ thư viện") },
                leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, null) },
                onClick = {
                    menu = false
                    onLaunch()
                    com.hankukko.huyfan.ForegroundTracker.setPickingImage(true)
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
        }
    }
}

/** Gợi ý câu hỏi nhanh khi đã đính kèm ảnh. */
val IMAGE_QUICK_ASKS: List<String>
    get() = com.hankukko.huyfan.Lang.current.nameVi.let { l ->
        listOf("Dịch chữ $l trong ảnh", "Cái này $l là gì?", "Giải thích ngữ pháp trong ảnh", "Đọc to chữ $l trong ảnh giúp tôi")
    }

/** Thanh ảnh đang đính kèm (chưa gửi) + câu hỏi gợi ý. */
@Composable
fun AttachmentBar(path: String, onRemove: () -> Unit, onQuickAsk: (String) -> Unit, modifier: Modifier = Modifier) {
    val x = LocalExtra.current
    val bmp = rememberChatBitmap(path, 256)
    var viewer by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(x.card)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { viewer = true },
                    contentAlignment = Alignment.Center,
                ) {
                    if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Ảnh đã đính kèm", fontWeight = FontWeight.Bold)
                Text("Gõ hoặc nói câu hỏi – ảnh sẽ gửi kèm", style = MaterialTheme.typography.bodySmall, color = x.muted)
            }
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Bỏ ảnh", tint = x.muted) }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IMAGE_QUICK_ASKS.forEach { q ->
                Text(
                    q,
                    Modifier
                        .clip(CircleShape)
                        .background(Brand.Violet.copy(alpha = 0.12f))
                        .clickable { onQuickAsk(q) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    color = Brand.Violet, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
    if (viewer) ImageViewerDialog(path) { viewer = false }
}

/** Ảnh trong bong bóng chat: giữ tỉ lệ, chạm để xem toàn màn hình. */
@Composable
fun ChatImageThumb(path: String, modifier: Modifier = Modifier, maxWidth: Dp = 230.dp) {
    val bmp = rememberChatBitmap(path, 720)
    var viewer by remember { mutableStateOf(false) }
    Box(
        modifier
            .widthIn(max = maxWidth)
            .heightIn(min = 80.dp, max = 300.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = bmp != null) { viewer = true },
        contentAlignment = Alignment.Center,
    ) {
        when {
            bmp != null -> Image(
                bmp, "Ảnh đã gửi",
                Modifier.widthIn(max = maxWidth),
                contentScale = ContentScale.Fit,
            )
            File(path).exists() -> CircularProgressIndicator(Modifier.size(22.dp).padding(2.dp), strokeWidth = 2.dp)
            else -> Text("Ảnh đã bị xoá", Modifier.padding(16.dp), color = LocalExtra.current.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (viewer) ImageViewerDialog(path) { viewer = false }
}

/** Xem ảnh toàn màn hình: chụm 2 ngón để phóng to, chạm 2 lần để phóng/thu, chạm ✕ để đóng. */
@Composable
fun ImageViewerDialog(path: String, onDismiss: () -> Unit) {
    val bmp = rememberChatBitmap(path, 2048)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (bmp != null) {
                Image(
                    bmp, "Ảnh",
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                            })
                        }
                        .transformable(transform)
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                    contentScale = ContentScale.Fit,
                )
            } else {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(12.dp)
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
            ) { Icon(Icons.Rounded.Close, "Đóng", tint = Color.White) }
        }
    }
}
