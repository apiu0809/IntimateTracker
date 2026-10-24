package com.mysecret.intimatetracker.ui.more

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mysecret.intimatetracker.R
import com.mysecret.intimatetracker.data.CrashLogger
import com.mysecret.intimatetracker.ui.component.SettingGroupLabel
import com.mysecret.intimatetracker.ui.component.SettingRow
import com.mysecret.intimatetracker.ui.component.ScreenScaffold

private const val DEVELOPER_URL = "https://github.com/apiu0809"
private const val PROJECT_URL = "https://github.com/apiu0809/IntimateTracker"

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenChangelog: () -> Unit,
    onOpenLicenses: () -> Unit,
    moreViewModel: MoreViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")
    }
    val updateState = moreViewModel.updateState

    LaunchedEffect(updateState) {
        when (updateState) {
            is UpdateUiState.UpToDate -> {
                Toast.makeText(context, R.string.about_check_update_latest, Toast.LENGTH_SHORT).show()
                moreViewModel.clearUpdateState()
            }
            is UpdateUiState.Failed -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.about_check_update_failed, updateState.reason),
                    Toast.LENGTH_LONG,
                ).show()
                moreViewModel.clearUpdateState()
            }
            else -> Unit
        }
    }
    var crashLogs by remember { mutableStateOf(CrashLogger.readAll(context)) }
    var showCrash by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }

    ScreenScaffold(title = stringResource(R.string.more_about), onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            BrandBlock(tagline = stringResource(R.string.about_tagline))

            SettingGroupLabel(stringResource(R.string.about_section_data))
            SettingRow(
                title = stringResource(R.string.about_where_data),
                subtitle = stringResource(R.string.about_where_data_sub),
                icon = Icons.Outlined.Lock,
                onClick = onOpenPrivacy,
                trailing = { Chevron() },
            )
            SettingRow(
                title = stringResource(R.string.more_clear),
                subtitle = stringResource(R.string.about_clear_sub),
                icon = Icons.Outlined.Delete,
                titleColor = MaterialTheme.colorScheme.error,
                iconTint = MaterialTheme.colorScheme.error,
                onClick = { showClear = true },
            )

            SettingGroupLabel(stringResource(R.string.about_section_app))
            SettingRow(
                title = stringResource(R.string.about_changelog),
                subtitle = stringResource(R.string.about_changelog_sub, versionName),
                icon = Icons.Outlined.History,
                onClick = onOpenChangelog,
                trailing = { Chevron() },
            )
            SettingRow(
                title = stringResource(R.string.about_check_update),
                subtitle = if (updateState is UpdateUiState.Checking) {
                    stringResource(R.string.about_check_update_checking)
                } else {
                    stringResource(R.string.about_check_update_sub)
                },
                icon = Icons.Outlined.SystemUpdateAlt,
                onClick = { moreViewModel.checkUpdate(versionName) },
                trailing = {
                    if (updateState is UpdateUiState.Checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Chevron()
                    }
                },
            )
            SettingRow(
                title = stringResource(R.string.about_crash),
                subtitle = if (crashLogs.isEmpty()) {
                    stringResource(R.string.about_crash_none)
                } else {
                    stringResource(R.string.about_crash_count, crashLogs.size)
                },
                icon = Icons.Outlined.BugReport,
                onClick = { showCrash = true },
                trailing = { Chevron() },
            )

            SettingGroupLabel(stringResource(R.string.about_section_about))
            SettingRow(
                title = stringResource(R.string.about_developer),
                subtitle = stringResource(R.string.about_developer_sub),
                icon = Icons.Outlined.PersonOutline,
                onClick = { openUrl(context, DEVELOPER_URL) },
                trailing = { Chevron() },
            )
            SettingRow(
                title = stringResource(R.string.about_project),
                subtitle = stringResource(R.string.about_project_sub),
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
                onClick = { openUrl(context, PROJECT_URL) },
                trailing = { Chevron() },
            )
            SettingRow(
                title = stringResource(R.string.about_licenses),
                icon = Icons.Outlined.Description,
                onClick = onOpenLicenses,
                trailing = { Chevron() },
            )

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.about_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showCrash) {
        CrashLogDialog(
            logs = crashLogs,
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("crash-logs", crashLogs.joinToString("\n\n")))
                Toast.makeText(context, R.string.about_crash_copied, Toast.LENGTH_SHORT).show()
            },
            onClear = {
                CrashLogger.clear(context)
                crashLogs = emptyList()
                showCrash = false
                Toast.makeText(context, R.string.about_crash_cleared, Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showCrash = false },
        )
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text(stringResource(R.string.more_clear)) },
            text = { Text(stringResource(R.string.about_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showClear = false
                    moreViewModel.clearAll()
                }) {
                    Text(
                        text = stringResource(R.string.about_clear_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (updateState is UpdateUiState.Available) {
        AlertDialog(
            onDismissRequest = moreViewModel::clearUpdateState,
            title = {
                Text(stringResource(R.string.about_update_available, updateState.info.version))
            },
            text = {
                Text(
                    text = updateState.info.notes.toPlainReleaseNotes().ifBlank {
                        stringResource(R.string.about_update_notes_empty)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    openUrl(context, updateState.info.pageUrl)
                    moreViewModel.clearUpdateState()
                }) { Text(stringResource(R.string.about_update_download)) }
            },
            dismissButton = {
                TextButton(onClick = moreViewModel::clearUpdateState) {
                    Text(stringResource(R.string.about_update_later))
                }
            },
        )
    }

    moreViewModel.message?.let { message ->
        AlertDialog(
            onDismissRequest = moreViewModel::dismissMessage,
            title = { Text(message) },
            confirmButton = {
                TextButton(onClick = moreViewModel::dismissMessage) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

@Composable
private fun BrandBlock(tagline: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 28.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_brand_mark),
            contentDescription = null,
            modifier = Modifier.size(76.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = tagline,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun CrashLogDialog(
    logs: List<String>,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_crash_dialog_title)) },
        text = {
            if (logs.isEmpty()) {
                Text(stringResource(R.string.about_crash_empty))
            } else {
                Box(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = logs.joinToString("\n\n"),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
        dismissButton = {
            if (logs.isNotEmpty()) {
                Row {
                    TextButton(onClick = onCopy) {
                        Text(stringResource(R.string.about_crash_copy))
                    }
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.about_crash_clear))
                    }
                }
            }
        },
    )
}

/** Release 说明是 Markdown，弹窗里只做轻量清理：去掉标题井号与加粗星号。 */
private fun String.toPlainReleaseNotes(): String =
    lineSequence()
        .map { line -> line.trim().trimStart('#').trim().replace("**", "") }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, R.string.about_link_failed, Toast.LENGTH_SHORT).show()
        }
}
