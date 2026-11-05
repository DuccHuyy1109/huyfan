package com.hankukko.huyfan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hankukko.huyfan.R
import com.hankukko.huyfan.data.ThemeMode

/** Bảng màu "Neon Seoul" – biển hiệu đêm Seoul: hồng neon, tím, cyan trên nền đen ánh xanh. */
object Brand {
    val Pink = Color(0xFFFF3D9A)
    val Cyan = Color(0xFF2EE6FF)
    val Blue = Color(0xFF4F8BFF)
    val Indigo = Color(0xFF6C63FF)
    val Violet = Color(0xFF9B5CFF)
    val Red = Color(0xFFFF4D6D)
    val Teal = Color(0xFF2BE4C6)
    val Amber = Color(0xFFFFC247)
    val Green = Color(0xFF3CEB97)

    /** Gradient chữ ký: hồng → tím (nút chính, logo, thẻ nổi bật). */
    val gradient = Brush.linearGradient(listOf(Pink, Violet))
    /** Neon 3 màu: hồng → tím → cyan (chữ logo, viền phát sáng). */
    val neon = Brush.linearGradient(listOf(Pink, Violet, Cyan))
    /** Đang nghe / cảnh báo: đỏ → hồng. */
    val warm = Brush.linearGradient(listOf(Red, Pink))
    /** Lạnh: tím → cyan. */
    val cool = Brush.linearGradient(listOf(Violet, Cyan))
}

@Immutable
data class ExtraColors(
    val bubbleUser: Brush,
    val bubbleAi: Color,
    val card: Color,
    val cardBorder: Color,
    val muted: Color,
    val tableHeader: Color,
    val tableStripe: Color,
    val glow: Color,
    val isDark: Boolean,
    /** Nền thanh tab nổi */
    val navBg: Color = card,
    /** Viền phát sáng hồng → tím → cyan cho các khối chính (thanh tab, ô nhập, thẻ kính) */
    val neonBorder: Brush = Brush.linearGradient(listOf(cardBorder, cardBorder)),
    /** Gradient cho tiêu đề lớn */
    val headline: Brush = Brush.linearGradient(listOf(Color.White, Color.White)),
)

val LocalExtra = staticCompositionLocalOf {
    ExtraColors(Brand.gradient, Color.DarkGray, Color.DarkGray, Color.Gray, Color.Gray, Color.Gray, Color.Gray, Brand.Pink, true)
}

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF3DE6FF),
    onPrimary = Color(0xFF00222B),
    primaryContainer = Color(0xFF0B3542),
    onPrimaryContainer = Color(0xFFC2F6FF),
    secondary = Color(0xFFB794FF),
    onSecondary = Color(0xFF220C4A),
    secondaryContainer = Color(0xFF2A1D52),
    onSecondaryContainer = Color(0xFFE9DEFF),
    tertiary = Color(0xFFFF5CAA),
    onTertiary = Color(0xFF3A0020),
    tertiaryContainer = Color(0xFF4A1233),
    onTertiaryContainer = Color(0xFFFFD8EA),
    background = Color(0xFF06070D),
    onBackground = Color(0xFFEDF1FF),
    surface = Color(0xFF0A0C16),
    onSurface = Color(0xFFEDF1FF),
    surfaceVariant = Color(0xFF181C2E),
    onSurfaceVariant = Color(0xFFA6AECB),
    surfaceContainerLowest = Color(0xFF05060B),
    surfaceContainerLow = Color(0xFF0C0F1B),
    surfaceContainer = Color(0xFF111424),
    surfaceContainerHigh = Color(0xFF161A2D),
    surfaceContainerHighest = Color(0xFF1D2238),
    outline = Color(0xFF2E3454),
    outlineVariant = Color(0xFF1C2038),
    error = Color(0xFFFF5C7A),
    onError = Color(0xFF3B0010),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF6A35F0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E0FF),
    onPrimaryContainer = Color(0xFF240A66),
    secondary = Color(0xFFD81B78),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0EE),
    onSecondaryContainer = Color(0xFF4A0026),
    tertiary = Color(0xFF0B8DAE),
    onTertiary = Color.White,
    background = Color(0xFFF5F5FB),
    onBackground = Color(0xFF0E1020),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0E1020),
    surfaceVariant = Color(0xFFEFEEF8),
    onSurfaceVariant = Color(0xFF5B6080),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9F8FE),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF2F1FA),
    surfaceContainerHighest = Color(0xFFE9E8F4),
    outline = Color(0xFFD7D6E8),
    outlineVariant = Color(0xFFE8E7F2),
    error = Color(0xFFD92D4B),
)

// ---------------------------------------------------------------------------------------------
// Font: Be Vietnam Pro (thiết kế riêng cho tiếng Việt, dấu đẹp). Tải qua Google Play Services lần đầu,
// chưa tải được thì dùng tạm font hệ thống. Chữ Hàn tự dùng font Hàn của máy.
// ---------------------------------------------------------------------------------------------
private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)
private val beVietnam = GoogleFont("Be Vietnam Pro")

val AppFont = FontFamily(
    Font(googleFont = beVietnam, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = beVietnam, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = beVietnam, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = beVietnam, fontProvider = fontProvider, weight = FontWeight.Bold),
    Font(googleFont = beVietnam, fontProvider = fontProvider, weight = FontWeight.ExtraBold),
    Font(googleFont = beVietnam, fontProvider = fontProvider, weight = FontWeight.Black),
)

private fun TextStyle.f(weight: FontWeight? = null, spacing: Float? = null, brush: Brush? = null): TextStyle {
    var s = copy(fontFamily = AppFont)
    if (weight != null) s = s.copy(fontWeight = weight)
    if (spacing != null) s = s.copy(letterSpacing = spacing.sp)
    if (brush != null) s = s.copy(brush = brush)
    return s
}

/** Tiêu đề lớn có gradient ánh kim; chữ thân giãn chữ 0 cho gọn (font hơi rộng). */
private fun typography(headline: Brush): Typography {
    val t = Typography()
    return Typography(
        displayLarge = t.displayLarge.f(FontWeight.Black, -1f, headline),
        displayMedium = t.displayMedium.f(FontWeight.Black, -0.8f, headline),
        displaySmall = t.displaySmall.f(FontWeight.Black, -0.6f, headline),
        headlineLarge = t.headlineLarge.f(FontWeight.ExtraBold, -0.6f, headline),
        headlineMedium = t.headlineMedium.f(FontWeight.ExtraBold, -0.5f, headline),
        headlineSmall = t.headlineSmall.f(FontWeight.Bold, -0.3f),
        titleLarge = t.titleLarge.f(FontWeight.Bold, -0.2f),
        titleMedium = t.titleMedium.f(FontWeight.SemiBold, -0.1f),
        titleSmall = t.titleSmall.f(FontWeight.SemiBold, 0f),
        bodyLarge = t.bodyLarge.f(spacing = 0f),
        bodyMedium = t.bodyMedium.f(spacing = 0f),
        bodySmall = t.bodySmall.f(spacing = 0.1f),
        labelLarge = t.labelLarge.f(FontWeight.SemiBold, 0.1f),
        labelMedium = t.labelMedium.f(FontWeight.SemiBold, 0.2f),
        labelSmall = t.labelSmall.f(FontWeight.Medium, 0.3f),
    )
}

private val HeadlineDark = Brush.linearGradient(listOf(Color.White, Color(0xFFE4D9FF), Color(0xFFA9EEFF)))
private val HeadlineLight = Brush.linearGradient(listOf(Color(0xFF0E1020), Color(0xFF3B1C9E)))
private val TypographyDark = typography(HeadlineDark)
private val TypographyLight = typography(HeadlineLight)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun HuyFanTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val dark = isDarkTheme(mode)
    val scheme = if (dark) DarkScheme else LightScheme
    val extra = if (dark) {
        ExtraColors(
            bubbleUser = Brush.linearGradient(listOf(Color(0xFFF2338F), Color(0xFF8B4DFF))),
            bubbleAi = Color(0xE60F1222),
            card = Color(0xD90D1020),
            cardBorder = Color(0x2EA5B4FF),
            muted = Color(0xFF8F98B8),
            tableHeader = Color(0xFF141A2E),
            tableStripe = Color(0xFF0B0E1B),
            glow = Brand.Pink,
            isDark = true,
            navBg = Color(0xFF0B0D19),
            neonBorder = Brush.linearGradient(
                listOf(Brand.Pink.copy(alpha = 0.55f), Brand.Violet.copy(alpha = 0.30f), Brand.Cyan.copy(alpha = 0.50f)),
            ),
            headline = HeadlineDark,
        )
    } else {
        ExtraColors(
            bubbleUser = Brush.linearGradient(listOf(Color(0xFFE8287F), Color(0xFF7440F5))),
            bubbleAi = Color.White,
            card = Color.White,
            cardBorder = Color(0xFFE3E2F0),
            muted = Color(0xFF6B7090),
            tableHeader = Color(0xFFF2EEFF),
            tableStripe = Color(0xFFF8F8FD),
            glow = Brand.Violet,
            isDark = false,
            navBg = Color.White,
            neonBorder = Brush.linearGradient(
                listOf(Brand.Pink.copy(alpha = 0.45f), Brand.Violet.copy(alpha = 0.25f), Color(0xFF0B8DAE).copy(alpha = 0.45f)),
            ),
            headline = HeadlineLight,
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalExtra provides extra) {
        MaterialTheme(colorScheme = scheme, typography = if (dark) TypographyDark else TypographyLight, shapes = AppShapes) {
            // Chữ không gán màu riêng → dùng màu chữ của nền (sáng trên giao diện tối),
            // tránh rơi về màu đen mặc định gây khó đọc
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides scheme.onBackground,
                content = content,
            )
        }
    }
}

/** Kiểu chữ neon cho logo "HUY FAN". */
val BrandTextStyle = TextStyle(brush = Brand.neon, fontFamily = AppFont, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
