package com.mysecret.intimatetracker

import com.mysecret.intimatetracker.data.db.CalendarEntry
import com.mysecret.intimatetracker.ui.calendar.CalendarFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarFilterTest {

    private fun entry(
        id: String = "r1",
        partner: String? = null,
        types: List<String> = emptyList(),
    ) = CalendarEntry(
        recordId = id,
        timestamp = 0L,
        typeNames = types.joinToString("|"),
        partnerIds = partner,
    )

    @Test
    fun `empty filter matches everything`() {
        assertTrue(CalendarFilter().matches(entry(partner = "p1", types = listOf("自慰"))))
        assertEquals(0, CalendarFilter().activeCount)
    }

    @Test
    fun `partner filter matches selected partner only`() {
        val filter = CalendarFilter(partnerIds = setOf("p1"))

        assertTrue(filter.matches(entry(partner = "p1")))
        assertFalse(filter.matches(entry(partner = "p2")))
        assertFalse(filter.matches(entry(partner = null)))
        assertEquals(1, filter.activeCount)
    }

    @Test
    fun `only solo filter checks masturbation type`() {
        val filter = CalendarFilter(onlySolo = true)

        assertTrue(filter.matches(entry(types = listOf("自慰"))))
        assertFalse(filter.matches(entry(types = listOf("口交"))))
    }

    @Test
    fun `partner and solo filters are combined with or`() {
        val filter = CalendarFilter(partnerIds = setOf("p1"), onlySolo = true)

        assertEquals(2, filter.activeCount)
        assertTrue(filter.matches(entry(partner = "p1")))
        assertTrue(filter.matches(entry(types = listOf("自慰"))))
        assertFalse(filter.matches(entry(partner = "p2", types = listOf("口交"))))
    }
}
