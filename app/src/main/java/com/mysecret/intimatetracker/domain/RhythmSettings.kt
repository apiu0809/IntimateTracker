package com.mysecret.intimatetracker.domain

/** 节奏卡的可自定义参数。 */
data class RhythmSettings(
    val colorize: Boolean = true,
    val neutralCapDays: Int = 7,
    val longGapDays: Int = 14,
    val weeklyUnit: Boolean = false,
    val partnerOnly: Boolean = false,
    val excludeSolo: Boolean = false,
    val rangeDays: Int = 10,
    val showLongestGap: Boolean = true,
) {
    companion object {
        const val KEY_COLORIZE = "rhythm.colorize"
        const val KEY_NEUTRAL = "rhythm.neutral_days"
        const val KEY_LONG_GAP = "rhythm.long_gap_days"
        const val KEY_WEEKLY = "rhythm.weekly_unit"
        const val KEY_PARTNER_ONLY = "rhythm.partner_only"
        const val KEY_EXCLUDE_SOLO = "rhythm.exclude_solo"
        const val KEY_RANGE = "rhythm.range_days"
        const val KEY_SHOW_LONGEST = "rhythm.show_longest"
    }
}
