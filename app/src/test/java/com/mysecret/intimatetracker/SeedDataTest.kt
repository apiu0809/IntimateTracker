package com.mysecret.intimatetracker

import com.mysecret.intimatetracker.data.db.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedDataTest {

    @Test
    fun `sex types count matches spec`() {
        assertEquals(10, SeedData.sexTypes.size)
    }

    @Test
    fun `locations count matches spec`() {
        assertEquals(23, SeedData.locations.size)
    }

    @Test
    fun `positions are user defined only`() {
        assertTrue(SeedData.positions.isEmpty())
    }

    @Test
    fun `ids are unique across all dictionaries`() {
        val ids = SeedData.sexTypes.map { it.id } +
            SeedData.locations.map { it.id } +
            SeedData.positions.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `names are unique inside each dictionary`() {
        assertEquals(10, SeedData.sexTypes.map { it.name }.distinct().size)
        assertEquals(23, SeedData.locations.map { it.name }.distinct().size)
        assertTrue(SeedData.positions.isEmpty())
    }

    @Test
    fun `sex types no longer carry icons`() {
        assertTrue(SeedData.sexTypes.all { it.emoji.isBlank() })
    }

    @Test
    fun `spec seed names include the screenshot anchors`() {
        val names = SeedData.sexTypes.map { it.name }.toSet()
        assertTrue(names.containsAll(setOf("阴道性交", "口交", "自慰", "BDSM")))
        val locations = SeedData.locations.map { it.name }.toSet()
        assertTrue(locations.containsAll(setOf("卧室", "厕所", "公共场所")))
    }
}
