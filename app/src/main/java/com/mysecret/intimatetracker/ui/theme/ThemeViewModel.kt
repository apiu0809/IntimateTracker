package com.mysecret.intimatetracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
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

    fun setAccent(argb: Int?) {
        viewModelScope.launch { settings.saveAccent(argb) }
    }

    fun setMode(mode: ThemeMode) {
        viewModelScope.launch { settings.saveThemeMode(mode) }
    }
}

/** 全局主题包装：读取偏好、跟随系统或强制深浅色、应用自定义强调色。 */
@Composable
fun ThemedApp(
    viewModel: ThemeViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val prefs by viewModel.prefs.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val dark = when (prefs.mode) {
        ThemeMode.AUTO -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val accent = prefs.accentArgb?.let { Color(it) } ?: Accent
    IntimateTrackerTheme(darkTheme = dark, accent = accent, content = content)
}
