package com.mysecret.intimatetracker.ui.prefs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysecret.intimatetracker.R
import com.mysecret.intimatetracker.domain.ThemeMode
import com.mysecret.intimatetracker.ui.component.ScreenScaffold
import com.mysecret.intimatetracker.ui.component.SectionHeader
import com.mysecret.intimatetracker.ui.theme.Accent
import com.mysecret.intimatetracker.ui.theme.ThemeViewModel

@Composable
fun PreferencesScreen(
    onBack: () -> Unit,
    viewModel: ThemeViewModel = hiltViewModel(),
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val startColor = prefs.accentArgb?.let { Color(it) } ?: Accent

    var hue by remember(startColor) { mutableFloatStateOf(hsvOf(startColor).first) }
    var saturation by remember(startColor) { mutableFloatStateOf(hsvOf(startColor).second) }
    var value by remember(startColor) { mutableFloatStateOf(hsvOf(startColor).third) }
    var mode by remember(prefs.mode) { mutableStateOf(prefs.mode) }

    val color = Color.hsv(hue, saturation, value)

    ScreenScaffold(title = stringResource(R.string.prefs_title), onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            SectionHeader(stringResource(R.string.prefs_appearance))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.prefs_theme_color),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    val hsv = hsvOf(Accent)
                    hue = hsv.first
                    saturation = hsv.second
                    value = hsv.third
                    viewModel.setAccent(null)
                }) { Text(stringResource(R.string.prefs_reset)) }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(color, CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
                Spacer(Modifier.size(16.dp))
                Column {
                    Text(
                        text = String.format("#%06X", 0xFFFFFF and color.toArgb()),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.prefs_preview_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ColorSlider(
                label = stringResource(R.string.prefs_hue),
                value = hue,
                range = 0f..360f,
                onChange = { hue = it },
                onFinished = { viewModel.setAccent(color.toArgb()) },
                previewColor = Color.hsv(hue, saturation, value),
            )
            ColorSlider(
                label = stringResource(R.string.prefs_saturation),
                value = saturation,
                range = 0f..1f,
                onChange = { saturation = it },
                onFinished = { viewModel.setAccent(color.toArgb()) },
                previewColor = Color.hsv(hue, saturation, value),
            )
            ColorSlider(
                label = stringResource(R.string.prefs_value),
                value = value,
                range = 0.1f..1f,
                onChange = { value = it },
                onFinished = { viewModel.setAccent(color.toArgb()) },
                previewColor = Color.hsv(hue, saturation, value),
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.prefs_dark_mode),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(
                    ThemeMode.AUTO to stringResource(R.string.prefs_mode_auto),
                    ThemeMode.LIGHT to stringResource(R.string.prefs_mode_light),
                    ThemeMode.DARK to stringResource(R.string.prefs_mode_dark),
                )
                options.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = mode == value,
                        onClick = {
                            mode = value
                            viewModel.setMode(value)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    ) { Text(label) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    previewColor: Color,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (range.endInclusive > 2f) value.toInt().toString() else "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Color → (hue, saturation, value)，hue 0..360。 */
private fun hsvOf(color: Color): Triple<Float, Float, Float> {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0) it + 360f else it }
    val saturation = if (max == 0f) 0f else delta / max
    return Triple(hue, saturation, max)
}
