package com.mysecret.intimatetracker.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mysecret.intimatetracker.R
import com.mysecret.intimatetracker.data.repo.DictionaryKind
import com.mysecret.intimatetracker.ui.component.ScreenScaffold
import com.mysecret.intimatetracker.ui.component.SectionHeader

@Composable
fun MoreScreen(
    onOpenDictionary: (DictionaryKind) -> Unit,
    onOpenWebDav: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenPrefs: () -> Unit,
    lockViewModel: com.mysecret.intimatetracker.ui.lock.AppLockViewModel =
        androidx.hilt.navigation.compose.hiltViewModel(),
    moreViewModel: MoreViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    var comingSoon by remember { mutableStateOf<String?>(null) }
    var showLockDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    val prefsLabel = stringResource(R.string.more_prefs)
    val lockLabel = stringResource(R.string.more_lock)
    val webdavLabel = stringResource(R.string.more_webdav)
    val exportLabel = stringResource(R.string.more_export)

    ScreenScaffold(title = stringResource(R.string.more_title)) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.more_browse))
            MoreRow(Icons.Outlined.LocalLaundryService, stringResource(R.string.dict_position)) {
                onOpenDictionary(DictionaryKind.POSITION)
            }
            MoreRow(Icons.Outlined.LocationOn, stringResource(R.string.dict_location)) {
                onOpenDictionary(DictionaryKind.LOCATION)
            }
            MoreRow(Icons.Outlined.FavoriteBorder, stringResource(R.string.dict_sex_type)) {
                onOpenDictionary(DictionaryKind.SEX_TYPE)
            }
            MoreRow(Icons.Outlined.Category, stringResource(R.string.dict_toy)) {
                onOpenDictionary(DictionaryKind.TOY)
            }

            SectionHeader(stringResource(R.string.more_app))
            MoreRow(Icons.Outlined.Settings, prefsLabel, onOpenPrefs)
            MoreRow(Icons.Outlined.Lock, lockLabel) { showLockDialog = true }
            MoreRow(Icons.Outlined.CloudUpload, webdavLabel, onOpenWebDav)
            MoreRow(Icons.Outlined.FileDownload, exportLabel) { showExportDialog = true }
            MoreRow(Icons.Outlined.Info, "关于", onOpenAbout)

            Spacer(Modifier.height(112.dp))
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(exportLabel) },
            text = { Text("将数据库与照片打包为 zip，保存到系统“下载/IntimateTracker”。") },
            confirmButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    moreViewModel.export()
                }) { Text("导出") }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    moreViewModel.message?.let { message ->
        AlertDialog(
            onDismissRequest = moreViewModel::dismissMessage,
            title = { Text(message) },
            confirmButton = {
                TextButton(onClick = moreViewModel::dismissMessage) { Text(stringResource(R.string.action_close)) }
            },
        )
    }

    if (showLockDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val canAuthenticate = remember(showLockDialog) {
            androidx.biometric.BiometricManager.from(context).canAuthenticate(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        }
        AlertDialog(
            onDismissRequest = { showLockDialog = false },
            title = { Text(lockLabel) },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("启动时要求验证", modifier = Modifier.weight(1f))
                        androidx.compose.material3.Switch(
                            checked = lockViewModel.enabled,
                            enabled = canAuthenticate || lockViewModel.enabled,
                            onCheckedChange = { lockViewModel.updateEnabled(it) },
                        )
                    }
                    if (!canAuthenticate && !lockViewModel.enabled) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "此设备未设置锁屏密码或生物识别，无法开启应用锁。请先在系统设置中设置锁屏。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLockDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }

    comingSoon?.let { label ->
        AlertDialog(
            onDismissRequest = { comingSoon = null },
            title = { Text(label) },
            text = { Text("该功能将在后续版本提供。") },
            confirmButton = {
                TextButton(onClick = { comingSoon = null }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

@Composable
private fun MoreRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp),
        )
    }
}
