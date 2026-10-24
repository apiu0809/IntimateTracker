package com.mysecret.intimatetracker.domain

import com.mysecret.intimatetracker.domain.model.Protection

/**
 * 记录图标的默认映射规则。
 *
 * 排序：自定义图标（若有）永远第一，**星级永远第二**，其余（类型 → 情趣用品 → 防护）依次在后。
 * 日历上每天最多显示 [MAX_CALENDAR_ICONS] 个图标。
 */
object RecordIcons {

    const val MAX_CALENDAR_ICONS = 2

    const val TOY_ICON = "🍆"
    const val PROTECTION_ICON = "☔"
    const val SATISFACTION_FIVE = "🌟"
    const val SATISFACTION_FOUR = "⭐"

    private val STAR_ICONS = setOf(SATISFACTION_FIVE, SATISFACTION_FOUR)

    /** 星级固定排第二位：至少两个图标时，把星移到下标 1。 */
    private fun starSecond(icons: List<String>): List<String> {
        if (icons.size < 2) return icons
        val star = icons.firstOrNull { it in STAR_ICONS } ?: return icons
        val rest = icons.filter { it != star }
        if (rest.isEmpty()) return listOf(star)
        return listOf(rest.first(), star) + rest.drop(1)
    }

    /** 性爱类型 → 图标（未列出的类型不产生图标）。 */
    val TYPE_ICONS: Map<String, String> = mapOf(
        "自慰" to "🖐️",
        "指交" to "☝️",
        "BDSM" to "⛓️",
        "口交" to "👅",
    )

    fun derive(
        customIcon: String?,
        typeNames: List<String>,
        hasToy: Boolean,
        protection: Protection?,
        satisfaction: Int?,
    ): List<String> {
        val others = ArrayList<String>()
        customIcon?.takeIf { it.isNotBlank() }?.let { others += it }
        // 已删类型的名字带「（已删除）」后缀，图标映射按去掉后缀的名字匹配，历史记录仍然显示原图标
        typeNames.forEach { raw -> TYPE_ICONS[raw.withoutDeletedMarker()]?.let { if (it !in others) others += it } }
        if (hasToy) others += TOY_ICON
        if (protection == Protection.YES) others += PROTECTION_ICON

        val star = when (satisfaction) {
            5 -> SATISFACTION_FIVE
            4 -> SATISFACTION_FOUR
            else -> null
        }

        if (star == null) return others.distinct()
        return starSecond((others + star).distinct())
    }

    /** 一天里所有记录合并后的图标（去重、星第二、截断到上限）。 */
    fun forDay(records: List<List<String>>): List<String> {
        val icons = LinkedHashSet<String>()
        records.forEach { icons.addAll(it) }
        return starSecond(icons.toList()).take(MAX_CALENDAR_ICONS)
    }
}
