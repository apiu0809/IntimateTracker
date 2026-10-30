package com.mysecret.intimatetracker

import com.mysecret.intimatetracker.data.db.PartnerEntity
import com.mysecret.intimatetracker.ui.calendar.CalendarFilter
import com.mysecret.intimatetracker.ui.calendar.legendSwatches
import com.mysecret.intimatetracker.ui.theme.PartnerColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 筛选面板「图例方块 ↔ 下方选项」的联动。
 *
 * 真机截图暴露过三种错位：选 2 个伴侣只画 1 个方块（方块数 ≠ 已选数）、
 * 两个伴侣都画成色板第一个颜色（颜色不跟选项走）、「自慰」画成紫色（"" 被当成伴侣 id 取色）。
 */
class CalendarFilterLegendTest {

    private val partners = listOf(partner("p1"), partner("p2"), partner("p3"))

    private fun partner(id: String) = PartnerEntity(id = id, name = id, createdAt = 0L, updatedAt = 0L)

    @Test
    fun `every selected partner gets its own swatch in option order`() {
        val filter = CalendarFilter(partnerIds = setOf("p1", "p3"))
        val swatches = filter.legendSwatches(partners, lightTheme = false)

        assertEquals("方块数必须等于已选数", filter.activeCount, swatches.size)
        assertEquals(listOf("p1", "p3"), swatches.map { it.partnerId })
        assertEquals(
            listOf(PartnerColors.forPartner("p1"), PartnerColors.forPartner("p3")),
            swatches.map { it.color },
        )
        assertNotEquals("不同伴侣不能共用同一枚色块", swatches[0].color, swatches[1].color)
    }

    @Test
    fun `solo swatch uses the neutral color the calendar draws for solo days`() {
        val swatches = CalendarFilter(onlySolo = true).legendSwatches(partners, lightTheme = false)

        assertEquals(1, swatches.size)
        assertNull(swatches.single().partnerId)
        assertEquals(PartnerColors.forPartner(null, false), swatches.single().color)
        assertTrue(
            "自慰不是伴侣，不能落到伴侣色板",
            swatches.none { it.color in PartnerColors.palette },
        )
    }

    @Test
    fun `solo comes first, then partners, one swatch per selected option`() {
        val filter = CalendarFilter(partnerIds = setOf("p3", "p1"), onlySolo = true)
        val swatches = filter.legendSwatches(partners, lightTheme = false)

        assertEquals(filter.activeCount, swatches.size)
        assertEquals(3, swatches.size)
        assertEquals(listOf(null, "p1", "p3"), swatches.map { it.partnerId })
    }

    @Test
    fun `no selection means no swatch`() {
        assertTrue(CalendarFilter().legendSwatches(partners, lightTheme = false).isEmpty())
    }

    @Test
    fun `light theme uses the dimmed colours so squares match the rings`() {
        val filter = CalendarFilter(partnerIds = setOf("p1"), onlySolo = true)
        val dark = filter.legendSwatches(partners, lightTheme = false)
        val light = filter.legendSwatches(partners, lightTheme = true)

        assertEquals(PartnerColors.solo, dark[0].color)
        assertEquals(PartnerColors.soloLight, light[0].color)
        assertEquals(PartnerColors.forPartner("p1", true), light[1].color)
        assertNotEquals("浅色主题不能沿用深色色板", dark[1].color, light[1].color)
    }
}
