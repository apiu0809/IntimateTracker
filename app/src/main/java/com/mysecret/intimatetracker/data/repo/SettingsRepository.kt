package com.mysecret.intimatetracker.data.repo

import com.mysecret.intimatetracker.R
import com.mysecret.intimatetracker.data.db.SettingsDao
import com.mysecret.intimatetracker.data.db.SettingEntity
import com.mysecret.intimatetracker.domain.RhythmSettings
import com.mysecret.intimatetracker.domain.ThemeMode
import com.mysecret.intimatetracker.domain.ThemePrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 记录表单中可被「简化输入」隐藏的 14 个字段（顺序即表单顺序）。 */
enum class FormField(val key: String, val labelRes: Int) {
    ICON("icon", R.string.record_icon),
    PHOTO("photo", R.string.record_photo),
    PARTNER("partner", R.string.partner_title),
    INITIATOR("initiator", R.string.record_initiator),
    DURATION("duration", R.string.record_duration),
    SATISFACTION("satisfaction", R.string.record_satisfaction),
    SEX_TYPE("sexType", R.string.record_type),
    TOY("toy", R.string.dict_toy),
    LOCATION("location", R.string.dict_location),
    POSITION("position", R.string.dict_position),
    PROTECTION("protection", R.string.record_protection),
    MY_ORGASM("myOrgasm", R.string.record_my_orgasm),
    PARTNER_ORGASM("partnerOrgasm", R.string.record_partner_orgasm),
    NOTES("notes", R.string.record_notes),
    ;

    val settingKey: String get() = "form.field.$key.visible"
}

@Singleton
class SettingsRepository @Inject constructor(private val dao: SettingsDao) {

    fun observeFormVisibility(): Flow<Map<FormField, Boolean>> = dao.observeAll().map { settings ->
        val map = settings.associate { it.key to it.value }
        FormField.entries.associateWith { field -> map[field.settingKey] != "false" }
    }


    // ---- 节奏卡自定义 ----
    suspend fun rhythmSettings(): RhythmSettings {
        val map = dao.observeAll().first().associate { it.key to it.value }
        fun int(key: String, fallback: Int) = map[key]?.toIntOrNull() ?: fallback
        fun bool(key: String, fallback: Boolean) = map[key]?.toBooleanStrictOrNull() ?: fallback
        return RhythmSettings(
            colorize = bool(RhythmSettings.KEY_COLORIZE, true),
            neutralCapDays = int(RhythmSettings.KEY_NEUTRAL, 7),
            longGapDays = int(RhythmSettings.KEY_LONG_GAP, 14),
            weeklyUnit = bool(RhythmSettings.KEY_WEEKLY, false),
            partnerOnly = bool(RhythmSettings.KEY_PARTNER_ONLY, false),
            excludeSolo = bool(RhythmSettings.KEY_EXCLUDE_SOLO, false),
            rangeDays = int(RhythmSettings.KEY_RANGE, 10),
            showLongestGap = bool(RhythmSettings.KEY_SHOW_LONGEST, true),
        )
    }

    suspend fun saveRhythmSettings(settings: RhythmSettings) {
        dao.put(SettingEntity(RhythmSettings.KEY_COLORIZE, settings.colorize.toString()))
        dao.put(SettingEntity(RhythmSettings.KEY_NEUTRAL, settings.neutralCapDays.toString()))
        dao.put(SettingEntity(RhythmSettings.KEY_LONG_GAP, settings.longGapDays.toString()))
        dao.put(SettingEntity(RhythmSettings.KEY_WEEKLY, settings.weeklyUnit.toString()))
        dao.put(SettingEntity(RhythmSettings.KEY_PARTNER_ONLY, settings.partnerOnly.toString()))
        dao.put(SettingEntity(RhythmSettings.KEY_EXCLUDE_SOLO, settings.excludeSolo.toString()))
        dao.put(SettingEntity(RhythmSettings.KEY_RANGE, settings.rangeDays.toString()))
        dao.put(SettingEntity(RhythmSettings.KEY_SHOW_LONGEST, settings.showLongestGap.toString()))
    }

    // ---- 外观 ----
    fun observeTheme(): Flow<ThemePrefs> = dao.observeAll().map { settings ->
        val map = settings.associate { it.key to it.value }
        ThemePrefs(
            seedArgb = map[ThemePrefs.KEY_ACCENT]?.toIntOrNull(),
            mode = runCatching { ThemeMode.valueOf(map[ThemePrefs.KEY_MODE].orEmpty()) }
                .getOrDefault(ThemeMode.AUTO),
            amoled = map[ThemePrefs.KEY_AMOLED] == "true",
            dynamicColor = map[ThemePrefs.KEY_DYNAMIC] == "true",
        )
    }

    suspend fun saveSeed(argb: Int?) {
        if (argb == null) {
            dao.delete(ThemePrefs.KEY_ACCENT)
        } else {
            dao.put(SettingEntity(ThemePrefs.KEY_ACCENT, argb.toString()))
        }
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        dao.put(SettingEntity(ThemePrefs.KEY_MODE, mode.name))
    }

    suspend fun saveAmoled(enabled: Boolean) {
        dao.put(SettingEntity(ThemePrefs.KEY_AMOLED, enabled.toString()))
    }

    suspend fun saveDynamicColor(enabled: Boolean) {
        dao.put(SettingEntity(ThemePrefs.KEY_DYNAMIC, enabled.toString()))
    }
    suspend fun setFieldVisible(field: FormField, visible: Boolean) =
        dao.put(SettingEntity(field.settingKey, visible.toString()))

    suspend fun visibleCount(): Int = FormField.entries.count { dao.get(it.settingKey) != "false" }
}
