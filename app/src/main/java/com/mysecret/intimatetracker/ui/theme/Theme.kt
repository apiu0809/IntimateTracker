package com.mysecret.intimatetracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysecret.intimatetracker.R

// ---------------------------------------------------------------------------
// B 分支视觉基线：冷调深色 + 薄荷强调色
// 深色主题色板（背景 #11161A / 卡片 #1D2228 / 强调 #6FB6A6）
// ---------------------------------------------------------------------------

val Background = Color(0xFF11161A)
val Surface = Color(0xFF1C2227)
val SurfaceAlt = Color(0xFF181D22)
val SurfaceHigh = Color(0xFF252D34)
val SurfaceVariant = Color(0xFF2A333A)
val Accent = Color(0xFF6FB6A6)
val AccentPill = Color(0xFF233733)
val AccentSoft = Color(0xFF9AC7BC)
val TextPrimary = Color(0xFFE9EDF0)
val TextSecondary = Color(0xFF9AA9AE)
val TextTertiary = Color(0xFF6B7A80)
val Outline = Color(0xFF30363A)
val Divider = Color(0xFF30363A)
val Danger = Color(0xFFE07A6E)

/** 星级固定金色，不随强调色变化。 */
val StarColor = Color(0xFFF0BE4E)

/** Plus Jakarta Sans：OFL 授权，可直接随包分发。 */
val PlusJakarta = FontFamily(
    Font(R.font.pjs_regular, FontWeight.Normal),
    Font(R.font.pjs_medium, FontWeight.Medium),
    Font(R.font.pjs_semibold, FontWeight.SemiBold),
    Font(R.font.pjs_bold, FontWeight.Bold),
    Font(R.font.pjs_extrabold, FontWeight.ExtraBold),
)

/** 当前主题强调色，供 Canvas 等非 Material 场景读取。 */
val LocalAccentColor = staticCompositionLocalOf { Accent }

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 46.sp),
    headlineLarge = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
    titleLarge = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private fun darkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color(0xFF0C1512),
    primaryContainer = lerp(Background, accent, 0.30f),
    onPrimaryContainer = lerp(accent, Color.White, 0.72f),
    secondary = accent,
    secondaryContainer = lerp(Background, accent, 0.22f),
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    outline = Outline,
    outlineVariant = Outline,
    error = Danger,
)

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = lerp(accent, Color.Black, 0.22f),
    onPrimary = Color.White,
    primaryContainer = lerp(accent, Color.White, 0.72f),
    onPrimaryContainer = lerp(accent, Color.Black, 0.55f),
    secondary = lerp(accent, Color.Black, 0.10f),
    secondaryContainer = lerp(accent, Color.White, 0.80f),
    background = Color(0xFFF4F6F6),
    onBackground = Color(0xFF14201F),
    surface = Color.White,
    onSurface = Color(0xFF14201F),
    surfaceVariant = Color(0xFFE4EAEA),
    onSurfaceVariant = Color(0xFF566462),
    outline = Color(0xFFC2CCCB),
    outlineVariant = Color(0xFFDCE3E2),
    error = Color(0xFFB3261E),
)

@Composable
fun IntimateTrackerTheme(
    darkTheme: Boolean = true,
    accent: Color = Accent,
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) darkScheme(accent) else lightScheme(accent)
    AmbientColors.update(darkTheme)
    CompositionLocalProvider(LocalAccentColor provides accent) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** 记录当前是否为深色，供少数硬编码颜色的组件取用。 */
object AmbientColors {
    var isDark: Boolean = true
        private set

    fun update(dark: Boolean) {
        isDark = dark
    }

    fun surfaceFor(dark: Boolean): Color = if (dark) Surface else Color.White
}

@Suppress("unused")
private fun Color.isLight(): Boolean = luminance() > 0.5f
