package com.mysecret.intimatetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mysecret.intimatetracker.data.db.AppDatabase
import com.mysecret.intimatetracker.data.db.SettingEntity
import com.mysecret.intimatetracker.data.repo.FormField
import com.mysecret.intimatetracker.data.repo.SettingsRepository
import com.mysecret.intimatetracker.data.repo.TemplateRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAndTemplateTest {

    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var templates: TemplateRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepository(db.settingsDao())
        templates = TemplateRepository(db.templateDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun all_fields_visible_by_default() = runBlocking {
        val visibility = settings.observeFormVisibility().first()

        assertEquals(FormField.entries.size, visibility.size)
        assertTrue(visibility.values.all { it })
    }

    @Test
    fun hiding_field_persists_and_is_reflected_in_flow() = runBlocking {
        settings.setFieldVisible(FormField.ICON, false)

        val visibility = settings.observeFormVisibility().first()
        assertFalse(visibility.getValue(FormField.ICON))
        assertTrue(visibility.getValue(FormField.NOTES))
    }

    @Test
    fun visible_count_tracks_settings() = runBlocking {
        assertEquals(FormField.entries.size, settings.visibleCount())

        settings.setFieldVisible(FormField.TOY, false)
        settings.setFieldVisible(FormField.PHOTO, false)

        assertEquals(FormField.entries.size - 2, settings.visibleCount())
    }

    @Test
    fun template_saves_payload_and_lists() = runBlocking {
        templates.save("模板 A", """{"icon":"🔥","duration":30}""")

        val list = templates.observeAll().first()

        assertEquals(1, list.size)
        assertEquals("模板 A", list.first().name)
        assertTrue(list.first().payloadJson.contains("\"duration\":30"))
    }

    @Test
    fun deleted_template_disappears_from_list() = runBlocking {
        val id = templates.save("模板 B", "{}")

        templates.delete(id)

        assertTrue(templates.observeAll().first().isEmpty())
    }
}
