package com.mysecret.intimatetracker.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysecret.intimatetracker.R
import com.mysecret.intimatetracker.ui.component.ScreenScaffold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WebDavSettingsScreen(
    onBack: () -> Unit,
    viewModel: WebDavSettingsViewModel = hiltViewModel(),
) {
    ScreenScaffold(title = stringResource(R.string.more_webdav), onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = viewModel.url,
                onValueChange = viewModel::onUrlChange,
                label = { Text("服务器地址") },
                placeholder = { Text("https://example.com/dav") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.user,
                onValueChange = viewModel::onUserChange,
                label = { Text("账号") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = viewModel::testConnection,
                    enabled = !viewModel.busy && viewModel.url.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("测试连接")
                }
                Button(
                    onClick = viewModel::syncNow,
                    enabled = !viewModel.busy && viewModel.url.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("立即同步")
                }
            }

            if (viewModel.busy) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    Text("处理中…", style = MaterialTheme.typography.bodyMedium)
                }
            }

            viewModel.status?.let { status ->
                Text(text = status, style = MaterialTheme.typography.bodyMedium)
            }

            viewModel.lastSyncAt?.let { millis ->
                val formatted = SimpleDateFormat("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE).format(Date(millis))
                Text(
                    text = "上次同步：$formatted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = stringResource(R.string.webdav_auto_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.webdav_auto_enabled))
                    Text(
                        text = stringResource(R.string.webdav_auto_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = viewModel.autoEnabled,
                    onCheckedChange = viewModel::updateAutoEnabled,
                )
            }

            if (viewModel.autoEnabled) {
                Text(
                    text = stringResource(R.string.webdav_auto_interval),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        15 to R.string.webdav_interval_15,
                        60 to R.string.webdav_interval_60,
                        360 to R.string.webdav_interval_360,
                        1440 to R.string.webdav_interval_1440,
                    ).forEach { (minutes, labelRes) ->
                        FilterChip(
                            selected = viewModel.intervalMinutes == minutes,
                            onClick = { viewModel.updateInterval(minutes) },
                            label = { Text(stringResource(labelRes)) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.webdav_wifi_only), modifier = Modifier.weight(1f))
                    Switch(checked = viewModel.wifiOnly, onCheckedChange = viewModel::updateWifiOnly)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.webdav_charging_only), modifier = Modifier.weight(1f))
                    Switch(checked = viewModel.chargingOnly, onCheckedChange = viewModel::updateChargingOnly)
                }
            }

            Text(
                text = stringResource(R.string.webdav_retention),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.webdav_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
