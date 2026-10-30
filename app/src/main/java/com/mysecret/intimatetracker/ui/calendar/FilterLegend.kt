package com.mysecret.intimatetracker.ui.calendar

import androidx.compose.ui.graphics.Color
import com.mysecret.intimatetracker.data.db.PartnerEntity
import com.mysecret.intimatetracker.ui.theme.PartnerColors

/**
 * 筛选面板顶部的一枚图例方块。
 *
 * [partnerId] 为 null 表示「自慰」一项：无伴侣的记录在月历上画中性灰环，所以它的图例也必须是同一个灰，
 * 不能落到伴侣色板里。
 */
data class FilterLegendSwatch(
    val partnerId: String?,
    val color: Color,
)

/**
 * 图例方块必须与下方「已选」的选项一一对应。
 *
 * 顺序跟选项一致（自慰在前，伴侣按 [partners] 列表顺序），颜色直接取 [PartnerColors.forPartner]，
 * 与月历日期环色同源，避免出现「选了 2 项只画 1 个方块」「两个伴侣都画成第一个人的颜色」
 * 「自慰画成紫色伴侣色」这类方块与选项脱节的问题。
 */
fun CalendarFilter.legendSwatches(
    partners: List<PartnerEntity>,
    lightTheme: Boolean,
): List<FilterLegendSwatch> {
    val swatches = ArrayList<FilterLegendSwatch>(activeCount)
    if (onlySolo) {
        swatches += FilterLegendSwatch(
            partnerId = null,
            color = PartnerColors.forPartner(null, lightTheme),
        )
    }
    partners.forEach { partner ->
        if (partner.id in partnerIds) {
            swatches += FilterLegendSwatch(
                partnerId = partner.id,
                color = PartnerColors.forPartner(partner.id, lightTheme),
            )
        }
    }
    return swatches
}
