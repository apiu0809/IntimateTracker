package com.mysecret.intimatetracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysecret.intimatetracker.data.repo.SettingsRepository
import com.mysecret.intimatetracker.domain.ThemeMode
import com.mysecret.intimatetracker.domain.ThemePrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val prefs: StateFlow<ThemePrefs> = settings.observeTheme()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemePrefs())

    /** 设种子色；传 null 回到品牌默认薄荷。 */
    fun setSeed(argb: Int?) {
        viewModelScope.launch { settings.saveSeed(argb) }
    }

    fun setMode(mode: ThemeMode) {
        viewModelScope.launch { settings.saveThemeMode(mode) }
    }

    fun setAmoled(enabled: Boolean) {
        viewModelScope.launch { settings.saveAmoled(enabled) }
    }

    /** 是否跟随壁纸取色（Android 12+）。 */
    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settings.saveDynamicColor(enabled) }
    }
}

/** 由偏好推出此刻是否深色。AUTO 跟随系统，其余两种强行指定。 */
@Composable
fun rememberDarkTheme(mode: ThemeMode): Boolean {
    val systemDark = isSystemInDarkTheme()
    return when (mode) {
        ThemeMode.AUTO -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

/** 全局主题包装：读取偏好、跟随系统或强制深浅色、用种子色生成整套方案。 */
@Composable
fun ThemedApp(
    viewModel: ThemeViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val prefs by viewModel.prefs.collectAsState()
    IntimateTrackerTheme(
        darkTheme = rememberDarkTheme(prefs.mode),
        seedArgb = prefs.seedArgb,
        amoled = prefs.amoled,
        dynamicColor = prefs.dynamicColor,
        content = content,
    )
}
