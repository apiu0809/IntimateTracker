package com.mysecret.intimatetracker.domain

/** 深色模式偏好。 */
enum class ThemeMode { AUTO, LIGHT, DARK }

/**
 * 外观偏好：主题种子色（ARGB，null = 品牌默认薄荷）、深色模式、纯黑（OLED）开关、
 * 以及是否跟随壁纸取色（Android 12+）。
 *
 * 只存**种子**，不存整套色板：方案由 `SeedScheme` 在渲染时从种子推导，所以换算法、修色域，
 * 已存的数据依然有意义。
 */
data class ThemePrefs(
    val seedArgb: Int? = null,
    val mode: ThemeMode = ThemeMode.AUTO,
    val amoled: Boolean = false,
    val dynamicColor: Boolean = false,
) {
    companion object {
        /** 键名沿用 `accent`，老版本存下的强调色会直接成为种子色。 */
        const val KEY_ACCENT = "theme.accent_argb"
        const val KEY_MODE = "theme.mode"
        const val KEY_AMOLED = "theme.amoled"
        const val KEY_DYNAMIC = "theme.dynamic_color"
    }
}
