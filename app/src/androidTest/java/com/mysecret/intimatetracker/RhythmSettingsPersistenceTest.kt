package com.mysecret.intimatetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mysecret.intimatetracker.data.db.AppDatabase
import com.mysecret.intimatetracker.data.repo.SettingsRepository
import com.mysecret.intimatetracker.domain.RhythmSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RhythmSettingsPersistenceTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: SettingsRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = SettingsRepository(db.settingsDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun defaults_match_design(): Unit = runBlocking {
        val s = repo.rhythmSettings()
        assertEquals(true, s.colorize)
        assertEquals(7, s.neutralCapDays)
        assertEquals(14, s.longGapDays)
        assertEquals(false, s.weeklyUnit)
        assertEquals(false, s.partnerOnly)
        assertEquals(false, s.excludeSolo)
        assertEquals(10, s.rangeDays)
        assertEquals(true, s.showLongestGap)
    }

    @Test
    fun custom_values_round_trip(): Unit = runBlocking {
        val custom = RhythmSettings(
            colorize = false,
            neutralCapDays = 3,
            longGapDays = 21,
            weeklyUnit = true,
            partnerOnly = true,
            excludeSolo = true,
            rangeDays = 5,
            showLongestGap = false,
        )
        repo.saveRhythmSettings(custom)

        assertEquals(custom, repo.rhythmSettings())
    }

    @Test
    fun switching_back_restores_defaults(): Unit = runBlocking {
        repo.saveRhythmSettings(RhythmSettings(colorize = false, weeklyUnit = true, rangeDays = 1))
        repo.saveRhythmSettings(RhythmSettings())

        assertEquals(RhythmSettings(), repo.rhythmSettings())
    }
}
