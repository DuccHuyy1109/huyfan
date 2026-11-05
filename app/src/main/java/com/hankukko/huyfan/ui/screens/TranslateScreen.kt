package com.hankukko.huyfan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hankukko.huyfan.ui.theme.Brand
import com.hankukko.huyfan.ui.theme.LocalExtra

/** Tab "Phiên dịch": chọn chế độ dịch. */
@Composable
fun TranslateScreen(onConversation: () -> Unit, onAuto: () -> Unit) {
    val x = LocalExtra.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.padding(start = 4.dp)) {
                Text("Phiên dịch", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(if (com.hankukko.huyfan.Lang.isEn) "🇻🇳 Việt ⇄ Anh 🇬🇧" else "🇻🇳 Việt ⇄ Hàn 🇰🇷", style = MaterialTheme.typography.bodySmall, color = x.muted)
            }
        }
        item {
            ModeCard(
                number = "1", title = "Dịch hội thoại", icon = Icons.Rounded.Forum,
                desc = "Bấm nút rồi nói – dịch xong đọc to.",
                tags = listOf("2 nút", "Đọc to", "Luân phiên"),
                colors = listOf(Brand.Cyan, Brand.Blue), onClick = onConversation,
            )
        }
        item {
            ModeCard(
                number = "2", title = "Dịch tự động", icon = Icons.Rounded.GraphicEq,
                desc = "Luôn nghe – ai nói gì dịch nấy.",
                tags = listOf("Không cần bấm", "Tự nhận ngôn ngữ"),
                colors = listOf(Brand.Pink, Brand.Violet), onClick = onAuto, badge = "MỚI",
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ModeCard(
    number: String,
    title: String,
    icon: ImageVector,
    desc: String,
    tags: List<String>,
    colors: List<Color>,
    onClick: () -> Unit,
    badge: String? = null,
) {
    val x = LocalExtra.current
    val accent = colors.first()
    // Cyan / hồng neon khó đọc trên nền trắng → giao diện sáng dùng màu thứ 2 (đậm hơn) cho chữ
    val ink = if (x.isDark) accent else colors.last()
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(if (x.isDark) 20.dp else 8.dp, shape, ambientColor = accent, spotColor = accent)
            .clip(shape)
            .background(x.navBg)
            .background(Brush.linearGradient(listOf(colors[0].copy(alpha = 0.22f), colors[1].copy(alpha = 0.07f), Color.Transparent)))
            .border(1.dp, Brush.linearGradient(listOf(colors[0].copy(alpha = 0.85f), colors[1].copy(alpha = 0.3f), x.cardBorder)), shape)
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .shadow(14.dp, RoundedCornerShape(16.dp), ambientColor = accent, spotColor = accent)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(colors)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "CHẾ ĐỘ 0$number", color = ink,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    if (badge != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            badge,
                            Modifier.clip(CircleShape).background(Brand.gradient).padding(horizontal = 8.dp, vertical = 2.dp),
                            color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f))
                    .border(1.dp, accent.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = ink, modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.height(14.dp))
        Text(desc, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(14.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tags.forEach { t ->
                Text(
                    t,
                    Modifier
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.10f))
                        .border(1.dp, accent.copy(alpha = 0.35f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    color = ink, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Nút 🌐 mở menu 2 chế độ phiên dịch (dùng trong cửa sổ bong bóng). */
@Composable
fun TranslateModeButton(onConversation: () -> Unit, onAuto: () -> Unit, size: Dp = 40.dp) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brand.Teal.copy(alpha = 0.16f))
                .border(1.dp, Brand.Teal.copy(alpha = 0.3f), CircleShape)
                .clickable { menu = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Translate, "Phiên dịch", tint = Brand.Teal, modifier = Modifier.size(size * 0.55f)) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Dịch hội thoại") },
                leadingIcon = { Icon(Icons.Rounded.Forum, null, tint = Brand.Teal) },
                onClick = { menu = false; onConversation() },
            )
            DropdownMenuItem(
                text = { Text("Dịch tự động") },
                leadingIcon = { Icon(Icons.Rounded.GraphicEq, null, tint = Brand.Violet) },
                onClick = { menu = false; onAuto() },
            )
        }
    }
}
