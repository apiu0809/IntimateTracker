package com.mysecret.intimatetracker.ui.prefs

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysecret.intimatetracker.R
import com.mysecret.intimatetracker.domain.ThemeMode
import com.mysecret.intimatetracker.ui.component.ColorWheel
import com.mysecret.intimatetracker.ui.component.ScreenScaffold
import com.mysecret.intimatetracker.ui.component.SettingSegmented
import com.mysecret.intimatetracker.ui.theme.SeedScheme
import com.mysecret.intimatetracker.ui.theme.ThemeViewModel
import com.mysecret.intimatetracker.ui.theme.cachedWallpaperSeed
import com.mysecret.intimatetracker.ui.theme.rememberDarkTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 外观设置。
 *
 * 与 LSPatch 的 AppearanceSheet 同一套思路：**明暗**、**取色来源**、**纯黑**三件事，
 * 改完立刻在面板背后生效 —— 换一个种子，背景、卡片、描边、强调色一起变，不用退出去看。
 *
 * 取色来源有三种，互斥：
 *  - 壁纸（Android 12+，`dynamicColorScheme`，本应用不参与取色）
 *  - 预设种子（[SeedScheme.PRESETS]，八个色相彼此拉得很开）
 *  - 色轮（同一色彩空间里选，角=色相、半径=彩度）
 */
@Composable
fun PreferencesScreen(
    onBack: () -> Unit,
    viewModel: ThemeViewModel = hiltViewModel(),
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val dark = rememberDarkTheme(prefs.mode)
    val storedSeed = prefs.seedArgb
    val seed = storedSeed ?: SeedScheme.DEFAULT_SEED
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // 跟随壁纸时，预览条展示的是从壁纸解出来的那个种子 —— 与实际生效的配色同源。
    val context = LocalContext.current
    val previewSeed = if (prefs.dynamicColor) {
        remember(prefs.dynamicColor) { cachedWallpaperSeed(context) } ?: seed
    } else {
        seed
    }

    // 色轮的位置（彩度, 色相）。本地状态让拇指跟着手指走（每帧），落库则节流 ——
    // 一次拖动有上百个 onChange 回调，每帧写库会让整棵树重组合上百次。
    val start = remember { with(SeedScheme) { Color(SeedScheme.DEFAULT_SEED).toWheel() } }
    var chroma by remember { mutableFloatStateOf(start.first) }
    var hue by remember { mutableFloatStateOf(start.second) }
    // 自定义色 = 存下来的种子既不是预设、也不是「跟随壁纸」。
    val isCustom = !prefs.dynamicColor && storedSeed != null && storedSeed !in SeedScheme.PRESETS
    // 展开状态以 isCustom 为默认，但保留用户的显式开关；null = 还没手动开过关过。
    // 不能直接 `remember { mutableStateOf(isCustom) }`：偏好是从 Room 异步来的，首帧拿到的是
    // 默认值，那个 false 会被 remember 一直记着。
    var wheelToggled by remember { mutableStateOf<Boolean?>(null) }
    val wheelOpen = wheelToggled ?: isCustom

    // 只有色轮手势会走到这里：预设与「跟随壁纸」直接落库，不需要节流，也不会被色轮的
    // 色域截断改写成「自定义」。
    val scope = rememberCoroutineScope()
    var pendingSave by remember { mutableStateOf<Job?>(null) }
    // 自己刚写下去的值：色域截断会让「写回的种子」与「手指所指的彩度」差一点点，
    // 若不跳过，拇指会在拖动时被拉回去跳一下。
    var lastWritten by remember { mutableStateOf<Int?>(null) }

    // 真实偏好到达时（以及任何外部改动）把拇指放到当前种子上；没有自定义色时停在品牌薄荷上。
    LaunchedEffect(storedSeed) {
        val target = storedSeed ?: SeedScheme.DEFAULT_SEED
        if (target != lastWritten) {
            val (c, h) = with(SeedScheme) { Color(target).toWheel() }
            chroma = c
            hue = h
        }
    }

    fun applySeed(argb: Int?) {
        val target = argb ?: SeedScheme.DEFAULT_SEED
        val (c, h) = with(SeedScheme) { Color(target).toWheel() }
        chroma = c
        hue = h
        lastWritten = target
        viewModel.setSeed(argb)
    }

    fun onWheelChange(h: Float, c: Float) {
        hue = h
        chroma = c
        pendingSave?.cancel()
        pendingSave = scope.launch {
            delay(160)
            val argb = SeedScheme.wheelColor(h, c).toArgb()
            lastWritten = argb
            viewModel.setSeed(argb)
        }
    }

    ScreenScaffold(title = stringResource(R.string.prefs_title), onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.prefs_appearance),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            SettingLabel(stringResource(R.string.prefs_dark_mode))
            SettingSegmented(
                options = listOf(
                    stringResource(R.string.prefs_mode_auto),
                    stringResource(R.string.prefs_mode_light),
                    stringResource(R.string.prefs_mode_dark),
                ),
                selectedIndex = prefs.mode.ordinal,
                onSelect = { index -> viewModel.setMode(ThemeMode.entries[index]) },
            )

            Spacer(Modifier.height(22.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingLabel(stringResource(R.string.prefs_theme_color), modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    // 「恢复默认」= 回到品牌薄荷，并且离开「跟随壁纸」—— 否则按钮按下去毫无反应。
                    viewModel.setDynamicColor(false)
                    wheelToggled = false
                    applySeed(null)
                }) {
                    Text(stringResource(R.string.prefs_reset))
                }
            }
            Text(
                text = stringResource(R.string.prefs_color_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            // 换行而不是横向滚动：所有色源一屏可见，「自定义」不会被挤出屏幕外。
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (supportsDynamic) {
                    SourceSwatch(
                        selected = prefs.dynamicColor,
                        onClick = {
                            viewModel.setDynamicColor(true)
                            wheelToggled = false
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = stringResource(R.string.prefs_color_wallpaper),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                SeedScheme.PRESETS.forEach { preset ->
                    val fill = remember(preset, dark) { SeedScheme.accentOf(preset, dark) }
                    SourceSwatch(
                        selected = !prefs.dynamicColor && seed == preset,
                        fill = fill,
                        onClick = {
                            viewModel.setDynamicColor(false)
                            applySeed(preset)
                            wheelToggled = false
                        },
                    )
                }

                SourceSwatch(
                    selected = isCustom,
                    fill = if (isCustom) SeedScheme.accentOf(seed, dark) else null,
                    onClick = {
                        viewModel.setDynamicColor(false)
                        wheelToggled = !wheelOpen
                    },
                ) {
                    if (!isCustom) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = stringResource(R.string.prefs_color_custom),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = wheelOpen && !prefs.dynamicColor,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ColorWheel(
                        hue = hue,
                        chroma = chroma,
                        dark = dark,
                        onChange = ::onWheelChange,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .fillMaxWidth(0.78f),
                    )
                    Text(
                        text = with(SeedScheme) { Color(seed).toHex() },
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SettingLabel(stringResource(R.string.prefs_tonal_preview))
            Spacer(Modifier.height(8.dp))
            TonalStrip(seedArgb = previewSeed)

            Spacer(Modifier.height(24.dp))
            AmoledToggle(
                checked = prefs.amoled,
                onCheckedChange = viewModel::setAmoled,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** 小标题：与设置行同一套排版，标题半粗、左侧不留白（外层已有 20dp）。 */
@Composable
private fun SettingLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

/**
 * 一张可选的颜色来源：一个圆点，选中时外面套一圈强调色。
 *
 * 填充色本身已经是「这个种子在当前明暗下会变成的强调色」，所以选之前就能看到结果。
 */
@Composable
private fun SourceSwatch(
    selected: Boolean,
    onClick: () -> Unit,
    fill: Color? = null,
    content: @Composable () -> Unit = {},
) {
    val ring by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "swatchRing",
    )
    Box(
        modifier = Modifier
            .size(44.dp)
            .border(width = 2.dp, color = ring, shape = CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(fill ?: MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected && fill != null) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        } else {
            content()
        }
    }
}

/** 纯黑背景开关：与其它设置行同一套排版，但不带 [com.mysecret.intimatetracker.ui.component.SettingRow] 的外层留白。 */
@Composable
private fun AmoledToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.DarkMode,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.prefs_amoled),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.size(3.dp))
            Text(
                text = stringResource(R.string.prefs_amoled_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 当前取色来源会产出的一整条色调梯，由浅到深。
 *
 * 这就是方案真正发出去的那些值 —— 不是装饰性的渐变，所以卡片、描边、文字与强调色
 * 之间的深浅关系，在这条带子上能一眼看出来。跟随壁纸时传入的是从壁纸解出来的种子，
 * 于是「预览」和「实际生效」永远是同一条梯子。
 */
@Composable
private fun TonalStrip(seedArgb: Int) {
    val tones = remember(seedArgb) { SeedScheme.previewTones(seedArgb) }
    Row(
        modifier = Modifier.fillMaxWidth().height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        tones.forEachIndexed { index, colour ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(
                        when (index) {
                            0 -> RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)
                            tones.lastIndex -> RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp)
                            else -> RoundedCornerShape(2.dp)
                        },
                    )
                    .background(colour),
            )
        }
    }
}
