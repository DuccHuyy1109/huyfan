package com.hankukko.huyfan.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hankukko.huyfan.Lang
import com.hankukko.huyfan.StudyLang
import com.hankukko.huyfan.ui.theme.Brand
import com.hankukko.huyfan.ui.theme.LocalExtra

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Nút cờ ngôn ngữ đang học (🇰🇷 / 🇬🇧). Chạm → hỏi lại rồi chuyển hẳn sang "app" ngôn ngữ kia
 * (sổ từ, lịch sử chat… riêng biệt), mở lại [reopen] với cuộc trò chuyện mới.
 */
@Composable
fun LangSwitchButton(reopen: Class<out Activity>, compact: Boolean = false, onBeforeSwitch: () -> Unit = {}) {
    val ctx = LocalContext.current
    val x = LocalExtra.current
    var ask by remember { mutableStateOf(false) }
    val cur = Lang.current
    Row(
        Modifier
            .semantics { contentDescription = "Đang học ${cur.nameVi} – chạm để đổi ngôn ngữ" }
            .clip(CircleShape)
            .background(x.card)
            .border(1.dp, x.neonBorder, CircleShape)
            .clickable { ask = true }
            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(cur.flag, fontSize = if (compact) 15.sp else 16.sp)
        if (!compact) {
            Spacer(Modifier.width(5.dp))
            Text(cur.id.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
    if (ask) {
        val to = cur.other
        AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text("${to.flag}  Học ${to.nameVi}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Chuyển sang không gian học ${to.nameVi} – như mở một app riêng.")
                    LangPoint("Sổ từ, ngữ pháp, lịch ôn, lịch sử chat tách biệt hoàn toàn")
                    LangPoint("Bắt đầu cuộc trò chuyện mới với HUY FAN")
                    LangPoint("Đổi lại bất cứ lúc nào, dữ liệu ${cur.nameVi} vẫn giữ nguyên")
                }
            },
            confirmButton = {
                Button(onClick = {
                    ask = false
                    onBeforeSwitch()
                    ctx.findActivity()?.let { Lang.switchTo(it, to, reopen) }
                }) { Text("Chuyển sang ${to.title}") }
            },
            dismissButton = { TextButton(onClick = { ask = false }) { Text("Ở lại") } },
        )
    }
}

@Composable
private fun LangPoint(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Brand.gradient))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
    }
}
