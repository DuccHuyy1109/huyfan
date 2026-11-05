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


C = J + 'ui/components/Components.kt'
patch(C, [
    # Logo: khối gradient hồng → tím phát sáng
    ('''        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(Brand.gradient),''',
     '''        Modifier
            .size(size)
            .shadow(size * 0.35f, RoundedCornerShape(size * 0.32f), ambientColor = Brand.Pink, spotColor = Brand.Violet)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(Brand.gradient),'''),
    # Tên app + dòng phụ chữ hoa giãn rộng
    ('''            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = LocalExtra.current.muted)
            }''',
     '''            if (subtitle != null) {
                Text(
                    subtitle.uppercase(), color = LocalExtra.current.muted,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold),
                )
            }'''),
    # Thẻ kính: viền neon
    ('''            .clip(RoundedCornerShape(22.dp))
            .background(x.card)
            .border(1.dp, x.cardBorder, RoundedCornerShape(22.dp))
            .padding(padding),''',
     '''            .clip(RoundedCornerShape(22.dp))
            .background(x.card)
            .border(1.dp, x.neonBorder, RoundedCornerShape(22.dp))
            .padding(padding),'''),
    # Nút mic: vòng neon xoay + quầng sáng
    ('''    val pulse by inf.animateFloat(1f, 1.18f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    Box(contentAlignment = Alignment.Center) {
        if (listening) {
            Box(
                Modifier
                    .size(size)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(Brand.Red.copy(alpha = 0.25f))
            )
        }
        Box(
            Modifier
                .size(size)
                .shadow(14.dp, CircleShape, ambientColor = Brand.Blue, spotColor = Brand.Indigo)''',
     '''    val pulse by inf.animateFloat(1f, 1.2f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    val spin by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(if (listening) 1300 else 4800, easing = androidx.compose.animation.core.LinearEasing)),
        label = "spin",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size + 8.dp)) {
        if (listening) {
            Box(
                Modifier
                    .size(size)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(Brand.Pink.copy(alpha = 0.28f))
            )
        }
        // Vòng neon hồng → tím → cyan xoay quanh nút
        androidx.compose.foundation.Canvas(Modifier.size(size + 7.dp).alpha(if (enabled) 1f else 0.4f)) {
            val ring = 2.2.dp.toPx()
            androidx.compose.ui.graphics.drawscope.rotate(spin) {
                drawCircle(
                    Brush.sweepGradient(listOf(Brand.Pink, Brand.Violet, Brand.Cyan, Color.Transparent, Brand.Pink)),
                    radius = this.size.minDimension / 2f - ring / 2f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(ring),
                )
            }
        }
        Box(
            Modifier
                .size(size)
                .shadow(18.dp, CircleShape, ambientColor = Brand.Pink, spotColor = Brand.Violet)'''),
    # Tiêu đề bảng từ vựng / ngữ pháp: dải neon mờ
    ('''                .background(Brush.horizontalGradient(listOf(Brand.Blue.copy(alpha = 0.22f), Brand.Violet.copy(alpha = 0.18f))))''',
     '''                .background(Brush.horizontalGradient(listOf(Brand.Cyan.copy(alpha = 0.16f), Brand.Violet.copy(alpha = 0.16f), Color.Transparent)))'''),
    ('''                .background(Brush.horizontalGradient(listOf(Brand.Violet.copy(alpha = 0.24f), Brand.Red.copy(alpha = 0.14f))))''',
     '''                .background(Brush.horizontalGradient(listOf(Brand.Violet.copy(alpha = 0.22f), Brand.Pink.copy(alpha = 0.14f), Color.Transparent)))'''),
])

# Màn chat: ô nhập viền neon, chip "Chờ" kiểu đèn LED, thẻ thống kê có icon phát sáng
S = J + 'ui/screens/ChatScreen.kt'
patch(S, [
    ('''                    .clip(RoundedCornerShape(28.dp))
                    .background(x.card)
                    .border(1.dp, x.cardBorder, RoundedCornerShape(28.dp))
                    .padding(start = 6.dp, end = 4.dp),''',
     '''                    .clip(RoundedCornerShape(28.dp))
                    .background(x.card)
                    .border(1.dp, x.neonBorder, RoundedCornerShape(28.dp))
                    .padding(start = 6.dp, end = 4.dp),'''),
    ('''            .clip(CircleShape)
            .background(if (on) Brand.Green.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)''',
     '''            .clip(CircleShape)
            .background(if (on) Brand.Green.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, if (on) Brand.Green.copy(alpha = 0.45f) else LocalExtra.current.cardBorder, CircleShape)
            .clickable(onClick = onClick)'''),
    ('''        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (on) Brand.Green else LocalExtra.current.muted)
        )''',
     '''        Box(
            Modifier
                .size(8.dp)
                .then(if (on) Modifier.shadow(6.dp, CircleShape, ambientColor = Brand.Green, spotColor = Brand.Green) else Modifier)
                .clip(CircleShape)
                .background(if (on) Brand.Green else LocalExtra.current.muted)
        )'''),
    ('''            .clip(RoundedCornerShape(18.dp))
            .background(x.card)
            .border(1.dp, x.cardBorder, RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = x.muted)''',
     '''            .clip(RoundedCornerShape(18.dp))
            .background(x.card)
            .border(1.dp, Brush.verticalGradient(listOf(color.copy(alpha = 0.55f), x.cardBorder)), RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = color, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = x.muted)'''),
    ('''            StatTile("Hôm nay", "$statsToday từ", Icons.Rounded.AutoAwesome, Brand.Blue, Modifier.weight(1f))
            StatTile("Tổng sổ", "$statsTotal từ", Icons.Rounded.School, Brand.Violet, Modifier.weight(1f))
            StatTile("Chuỗi", "$streak ngày", Icons.Rounded.LocalFireDepartment, Brand.Red, Modifier.weight(1f))''',
     '''            StatTile("Hôm nay", "$statsToday từ", Icons.Rounded.AutoAwesome, Brand.Cyan, Modifier.weight(1f))
            StatTile("Tổng sổ", "$statsTotal từ", Icons.Rounded.School, Brand.Violet, Modifier.weight(1f))
            StatTile("Chuỗi", "$streak ngày", Icons.Rounded.LocalFireDepartment, Brand.Pink, Modifier.weight(1f))'''),
])

# Quả cầu giọng nói: màu neon
V = J + 'ui/components/VoiceOrb.kt'
patch(V, [
    ('''        Phase.LISTENING -> Triple(Brand.Blue, Brand.Teal, Color(0xFF7DD3FC))
        Phase.THINKING -> Triple(Brand.Violet, Brand.Blue, Color(0xFFF0ABFC))
        Phase.SPEAKING -> Triple(Brand.Red, Brand.Violet, Color(0xFFFFB4C2))
        Phase.GREETING -> Triple(Brand.Teal, Brand.Violet, Color(0xFFA7F3D0))
        Phase.IDLE -> Triple(Brand.Blue, Brand.Violet, Color(0xFFC4B5FD))''',
     '''        Phase.LISTENING -> Triple(Brand.Cyan, Brand.Blue, Color(0xFF9FF3FF))
        Phase.THINKING -> Triple(Brand.Violet, Brand.Cyan, Color(0xFFF0ABFC))
        Phase.SPEAKING -> Triple(Brand.Pink, Brand.Violet, Color(0xFFFFB4D9))
        Phase.GREETING -> Triple(Brand.Teal, Brand.Cyan, Color(0xFFA7F3D0))
        Phase.IDLE -> Triple(Brand.Pink, Brand.Violet, Color(0xFF8FEFFF))'''),
])

# Bảng trợ lý nổi (AssistActivity): nền tối neon
patch(J + 'ui/AssistActivity.kt', [
    ('''Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF18203A), androidx.compose.ui.graphics.Color(0xFF0B0F1E)))''',
     '''Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF141327), androidx.compose.ui.graphics.Color(0xFF07080F)))'''),
])

# Màu thông báo
patch(J + 'service/Notifications.kt', [(".setColor(0xFF4F7BFF.toInt())", ".setColor(0xFFFF3D9A.toInt())")] * 3)

# Icon app: gradient hồng → tím → xanh đêm
patch('res/drawable/ic_launcher_background.xml', [
    ('''                <item android:offset="0" android:color="#FF3D6BFF" />
                <item android:offset="0.55" android:color="#FF6A5CFF" />
                <item android:offset="1" android:color="#FFA45CFF" />''',
     '''                <item android:offset="0" android:color="#FFFF3D9A" />
                <item android:offset="0.55" android:color="#FF9B5CFF" />
                <item android:offset="1" android:color="#FF4F5BFF" />'''),
])
print('ok')
