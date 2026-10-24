package com.mysecret.intimatetracker.ui.theme

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.mysecret.intimatetracker.R

// ---------------------------------------------------------------------------
// 主题：种子色 -> LCh 色调梯 -> 完整 Material 3 方案
//
// 取色逻辑见 [SeedScheme]（移植自 LSPatch / Vector）。这里只负责三件事：
// 把种子变成一个 ColorScheme、把它接到 Material 上、以及让系统栏跟着明暗走。
// 应用里不再有手写的深色色板 —— 所有颜色都是同一个种子的不同明度切片，
// 于是换一个种子，整套界面（背景、卡片、描边、强调、错误色）会一起换掉。
// ---------------------------------------------------------------------------

/** 品牌默认种子色（薄荷），与 [SeedScheme.DEFAULT_SEED] 同源。 */
val Accent: Color = Color(SeedScheme.DEFAULT_SEED)

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
    displaySmall = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.6).sp),
    headlineLarge = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp, letterSpacing = (-0.1).sp),
    titleMedium = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = PlusJakarta, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
)

/** 圆角整体放大一档，与卡片惯用的 20dp 对齐，观感更柔和。 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

/**
 * 全局主题。
 *
 * @param darkTheme 是否深色（由偏好或系统决定，见 `ThemedApp`）
 * @param seedArgb 种子色；null 表示品牌默认薄荷
 * @param amoled 深色下是否把背景压成纯黑（OLED）
 * @param dynamicColor 是否跟随壁纸取色（仅 Android 12+，且优先级高于种子）
 */
@Composable
fun IntimateTrackerTheme(
    darkTheme: Boolean = true,
    seedArgb: Int? = null,
    amoled: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val seed = seedArgb ?: SeedScheme.DEFAULT_SEED
    val context = LocalContext.current
    val dynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // 系统配色（Monet）/ 壁纸换了，方案就必须重算 —— 见 rememberPaletteRevision。
    val paletteRevision = rememberPaletteRevision(dynamic)

    // 壁纸自己解出来的种子：先给上次缓存的值（首帧不闪），再在后台重算。
    val wallpaperSeed by produceState(
        initialValue = if (dynamic) cachedWallpaperSeed(context) else null,
        dynamic,
        paletteRevision,
    ) {
        if (!dynamic) {
            value = null
            return@produceState
        }
        resolveWallpaperSeed(context)?.let { value = it }
    }

    val scheme = remember(seed, darkTheme, amoled, dynamic, wallpaperSeed) {
        val wallpaper = wallpaperSeed
        val base = when {
            // 优先用壁纸自己的取色：不依赖 ROM 是否把壁纸喂给了 system_accent*。
            dynamic && wallpaper != null -> SeedScheme.of(wallpaper, darkTheme)
            // 读不到位图时，退回系统的动态配色（能用的机型上效果一样）。
            dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            else -> SeedScheme.of(seed, darkTheme)
        }
        if (darkTheme && amoled) base.toAmoled() else base
    }

    // 系统栏图标跟着明暗走，否则浅色主题下状态栏仍然是白字，等于看不见。
    // 用 findActivity 而不是硬转 Activity：语言覆盖会包一层 Context，硬转会直接崩。
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalAccentColor provides scheme.primary) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * 系统配色的「版本号」：换壁纸、换系统色彩后 +1，让 [IntimateTrackerTheme] 重新读一遍。
 *
 * 动态取色读的是 `android.R.color.system_accent*`，这些值来自系统在壁纸之上生成的色彩覆盖层。
 * 覆盖层更新时 Activity **不一定会重建**，而 `remember` 的 key 里也没有任何东西会变 ——
 * 结果就是「换了好几张壁纸，颜色一点没动」。所以这里显式做两件事：
 *
 *  1. 用 [WallpaperManager.OnColorsChangedListener] 收听壁纸配色变化（这是驱动 Monet 的
 *     官方信号；`ACTION_WALLPAPER_CHANGED` 广播在 API 34 已废弃）；
 *  2. 每次回到前台（`ON_RESUME`）再补一次 —— 少数 ROM 不回调第三方应用，
 *     而用户换完壁纸总是要回到应用里来的。
 *
 * 只在「跟随壁纸」打开时注册，关掉后不留监听。
 */
@Composable
private fun rememberPaletteRevision(enabled: Boolean): Int {
    var revision by remember { mutableIntStateOf(0) }
    if (enabled) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(context, lifecycleOwner) {
            val manager = WallpaperManager.getInstance(context)
            val listener = WallpaperManager.OnColorsChangedListener { _, _ -> revision++ }
            manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) revision++
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                manager.removeOnColorsChangedListener(listener)
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }
    return revision
}
