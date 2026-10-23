package com.mysecret.intimatetracker.domain

/** 深色模式偏好。 */
enum class ThemeMode { AUTO, LIGHT, DARK }

/** 外观偏好：强调色（ARGB，null = 默认绿）+ 深色模式。 */
data class ThemePrefs(
    val accentArgb: Int? = null,
    val mode: ThemeMode = ThemeMode.AUTO,
) {
    companion object {
        const val KEY_ACCENT = "theme.accent_argb"
        const val KEY_MODE = "theme.mode"
    }
}
