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


# ---- Logo + tên: nhận modifier, dòng phụ không bao giờ đẩy mất nút bên phải ----
patch(J + 'ui/components/Components.kt', [
    ('''fun BrandTitle(subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {''',
     '''fun BrandTitle(subtitle: String? = null, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {'''),
    ('''                Text(
                    subtitle.uppercase(), color = LocalExtra.current.muted,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold),
                )''',
     '''                Text(
                    subtitle.uppercase(), color = LocalExtra.current.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold),
                )'''),
])

patch(J + 'ui/screens/ChatScreen.kt', [
    ('''            BrandTitle(subtitle = "Trợ lý học tiếng Hàn")
            Spacer(Modifier.weight(1f))''',
     '''            BrandTitle(subtitle = "Trợ lý tiếng Hàn", modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))'''),
    ('''        Spacer(Modifier.width(6.dp))
        Icon(Icons.Rounded.PowerSettingsNew, null, modifier = Modifier.size(14.dp), tint = if (on) Brand.Green else LocalExtra.current.muted)
        Spacer(Modifier.width(4.dp))''',
     '''        Spacer(Modifier.width(6.dp))'''),
])

# ---- Tab Phiên dịch: thẻ kính tối, viền + quầng neon, chip tự xuống dòng gọn ----
T = J + 'ui/screens/TranslateScreen.kt'
patch(T, [
    ('''                tags = listOf("2 nút", "Đọc to", "Luân phiên"),
                colors = listOf(Brand.Teal, Brand.Blue), onClick = onConversation,''',
     '''                tags = listOf("2 nút", "Đọc to", "Luân phiên"),
                colors = listOf(Brand.Cyan, Brand.Blue), onClick = onConversation,'''),
    ('''                colors = listOf(Brand.Violet, Brand.Blue), onClick = onAuto, badge = "MỚI",''',
     '''                colors = listOf(Brand.Pink, Brand.Violet), onClick = onAuto, badge = "MỚI",'''),
    ('''                    .clip(RoundedCornerShape(18.dp))
                    .background(Brand.Amber.copy(alpha = 0.12f))
                    .padding(14.dp),''',
     '''                    .clip(RoundedCornerShape(18.dp))
                    .background(x.card)
                    .border(1.dp, Brush.horizontalGradient(listOf(Brand.Amber.copy(alpha = 0.5f), x.cardBorder)), RoundedCornerShape(18.dp))
                    .padding(14.dp),'''),
    ('''@Composable
private fun ModeCard(''', '''@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ModeCard('''),
    ('''    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(colors))
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$number · $title", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (badge != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            badge,
                            Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 8.dp, vertical = 2.dp),
                            color = colors.first(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color.White)
        }
        Spacer(Modifier.height(12.dp))
        Text(desc, color = Color.White.copy(alpha = 0.93f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { t ->
                Text(
                    t,
                    Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }''',
     '''    val x = LocalExtra.current
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
    }'''),
])

# ---- Sổ từ: thanh ngày dính trên cùng thành dải kính nổi có vạch neon ----
patch(J + 'ui/screens/VocabScreen.kt', [
    ('''    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onSelectDay != null) {''',
     '''    val x = LocalExtra.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(x.navBg.copy(alpha = 0.97f))
            .border(1.dp, x.cardBorder, RoundedCornerShape(16.dp))
            .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 18.dp)
                .clip(CircleShape)
                .background(Brand.gradient)
        )
        Spacer(Modifier.width(10.dp))
        if (onSelectDay != null) {'''),
])

# ---- Bong bóng nổi: gradient neon như logo mới ----
patch(J + 'service/Bubble.kt', [
    ('''intArrayOf(Color.parseColor("#4F7BFF"), Color.parseColor("#6A5CFF"), Color.parseColor("#A45CFF")),''',
     '''intArrayOf(Color.parseColor("#FF3D9A"), Color.parseColor("#9B5CFF"), Color.parseColor("#4F5BFF")),'''),
])
print('ok')
