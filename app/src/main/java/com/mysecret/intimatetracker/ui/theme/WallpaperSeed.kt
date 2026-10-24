package com.mysecret.intimatetracker.ui.theme

import android.app.WallpaperManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 从当前壁纸里取一个「种子色」，交给 [SeedScheme] 生成整套配色。
 *
 * 为什么不用系统的 `dynamicLightColorScheme`：那套颜色读的是框架层的 `system_accent*` 资源，
 * 而「这些资源跟不跟壁纸走」完全由 ROM 决定。实测这台 HyperOS 把
 * `com.miui.wallpaper.overlay.customize` 关着 —— 壁纸是洋红，系统给的却是一套固定的蓝紫，
 * 换多少张壁纸都不会动。改用 [WallpaperManager.getWallpaperColors] 直接问系统「这张壁纸的
 * 代表色是什么」：不需要任何权限，取到的颜色再交给本项目已有的种子引擎推导 ——
 * 换壁纸就真换色，任何 ROM、任何 API 级别（minSdk 26）都成立。
 *
 * 取不到颜色时返回 null，调用方回落到系统动态配色 / 品牌种子。
 */
private const val PREFS = "theme"
private const val KEY_WALLPAPER_SEED = "wallpaper_seed"
private const val TAG = "WallpaperSeed"

/** 上次算出来的壁纸种子。同步读，冷启动第一帧就能用，避免先闪一下别的配色。 */
fun cachedWallpaperSeed(context: Context): Int? =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getInt(KEY_WALLPAPER_SEED, 0)
        .takeIf { it != 0 }

/** 重新问一次系统（后台线程），拿到颜色就顺手更新缓存。 */
suspend fun resolveWallpaperSeed(context: Context): Int? = withContext(Dispatchers.IO) {
    val seed = runCatching {
        WallpaperManager.getInstance(context)
            .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            ?.primaryColor
            ?.toArgb()
    }
        .onFailure { Log.w(TAG, "读取壁纸配色失败，回落到系统配色", it) }
        .getOrNull() ?: return@withContext null

    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putInt(KEY_WALLPAPER_SEED, seed)
        .apply()
    seed
}
