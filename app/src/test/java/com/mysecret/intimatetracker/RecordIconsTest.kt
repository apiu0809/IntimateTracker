package com.mysecret.intimatetracker

import com.mysecret.intimatetracker.domain.RecordIcons
import com.mysecret.intimatetracker.domain.model.Protection
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordIconsTest {

    @Test
    fun `custom icon comes first`() {
        val icons = RecordIcons.derive(
            customIcon = "🔥",
            typeNames = listOf("自慰"),
            hasToy = false,
            protection = null,
            satisfaction = null,
        )

        assertEquals(listOf("🔥", "🖐️"), icons)
    }

    @Test
    fun `masturbation maps to hand icon`() {
        assertEquals("🖐️", RecordIcons.derive(null, listOf("自慰"), false, null, null).first())
    }

    @Test
    fun `deleted type keeps its icon`() {
        assertEquals("👅", RecordIcons.derive(null, listOf("口交（已删除）"), false, null, null).first())
        assertEquals("🖐️", RecordIcons.derive(null, listOf("自慰（已删除）"), false, null, null).first())
    }

    @Test
    fun `fingering oral bdsm mapping`() {
        assertEquals("☝️", RecordIcons.derive(null, listOf("指交"), false, null, null).first())
        assertEquals("👅", RecordIcons.derive(null, listOf("口交"), false, null, null).first())
        assertEquals("⛓️", RecordIcons.derive(null, listOf("BDSM"), false, null, null).first())
    }

    @Test
    fun `star always takes the second slot`() {
        val icons = RecordIcons.derive(
            customIcon = null,
            typeNames = emptyList(),
            hasToy = true,
            protection = Protection.YES,
            satisfaction = 5,
        )

        assertEquals(listOf("🍆", "🌟", "☔"), icons)
    }

    @Test
    fun `custom icon first and star second`() {
        val icons = RecordIcons.derive(
            customIcon = "🔥",
            typeNames = listOf("自慰"),
            hasToy = true,
            protection = Protection.YES,
            satisfaction = 4,
        )

        assertEquals(listOf("🔥", "⭐", "🖐️", "🍆", "☔"), icons)
    }

    @Test
    fun `star alone stays alone`() {
        assertEquals(listOf("🌟"), RecordIcons.derive(null, emptyList(), false, null, 5))
    }

    @Test
    fun `day keeps star in second position`() {
        val record = RecordIcons.derive(null, listOf("自慰"), true, Protection.YES, 5)

        assertEquals(listOf("🖐️", "🌟"), RecordIcons.forDay(listOf(record)))
    }

    @Test
    fun `four stars uses plain star and below four has none`() {
        assertEquals(listOf("⭐"), RecordIcons.derive(null, emptyList(), false, null, 4))
        assertEquals(emptyList<String>(), RecordIcons.derive(null, emptyList(), false, null, 3))
        assertEquals(emptyList<String>(), RecordIcons.derive(null, emptyList(), false, null, 1))
    }

    @Test
    fun `protection without yes adds nothing`() {
        assertEquals(emptyList<String>(), RecordIcons.derive(null, emptyList(), false, Protection.NO, null))
        assertEquals(emptyList<String>(), RecordIcons.derive(null, emptyList(), false, Protection.UNKNOWN, null))
    }

    @Test
    fun `day shows at most two icons after dedup`() {
        val first = listOf("🖐️", "🌟")
        val second = listOf("🖐️", "🍆")

        assertEquals(listOf("🖐️", "🌟"), RecordIcons.forDay(listOf(first, second)))
        assertEquals(2, RecordIcons.forDay(listOf(first, second, listOf("☔"))).size)
    }

    @Test
    fun `day keeps star second even when it comes from another record`() {
        val rated = listOf("🌟")
        val protected = listOf("☔")

        assertEquals(listOf("☔", "🌟"), RecordIcons.forDay(listOf(rated, protected)))
        assertEquals(listOf("☔", "🌟"), RecordIcons.forDay(listOf(protected, rated)))
    }
}
