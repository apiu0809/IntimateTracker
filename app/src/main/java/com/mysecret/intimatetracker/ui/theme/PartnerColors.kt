package com.mysecret.intimatetracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.absoluteValue

/** 日历中每个伴侣对应的环色（与筛选面板的色块一致）。 */
object PartnerColors {

    /** 深色主题下的亮色板。 */
    val palette: List<Color> = listOf(
        Color(0xFF6C5CE7), // 紫
        Color(0xFFE05A47), // 红
        Color(0xFFC0326F), // 品红
        Color(0xFFE8B75A), // 金
        Color(0xFF2E8B80), // 青
    )

    /** 浅色主题下压深一档，保证白底上的对比度。 */
    val lightPalette: List<Color> = listOf(
        Color(0xFF5145C4),
        Color(0xFFB93A29),
        Color(0xFF952150),
        Color(0xFF9A7418),
        Color(0xFF1E6A61),
    )

    val solo: Color = Color(0xFF4A5568)
    val soloLight: Color = Color(0xFF3A4453)

    fun forPartner(id: String?, lightTheme: Boolean = false): Color {
        if (id == null) return if (lightTheme) soloLight else solo
        val list = if (lightTheme) lightPalette else palette
        return list[id.hashCode().absoluteValue % list.size]
    }

    /** 当前是否浅色主题（以背景亮度判断，避免额外传参）。 */
    @Composable
    fun isLightTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() > 0.5f

    /** 记录行/详情用的星级金色：浅色下加深。 */
    @Composable
    fun starColor(): Color = if (isLightTheme()) Color(0xFF9A7418) else Color(0xFFE8B75A)
}
