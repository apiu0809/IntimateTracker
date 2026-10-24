package com.mysecret.intimatetracker.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mysecret.intimatetracker.R
import com.mysecret.intimatetracker.ui.component.ScreenScaffold

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.privacy_title), onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.privacy_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(16.dp))
            PrivacyCard(
                icon = Icons.Outlined.Lock,
                title = stringResource(R.string.privacy_data_title),
                body = stringResource(R.string.privacy_data_body),
            )
            Spacer(Modifier.size(12.dp))
            PrivacyCard(
                icon = Icons.Outlined.Cloud,
                title = stringResource(R.string.privacy_network_title),
                body = stringResource(R.string.privacy_network_body),
            )
            Spacer(Modifier.size(12.dp))
            PrivacyCard(
                icon = Icons.Outlined.Security,
                title = stringResource(R.string.privacy_permission_title),
                body = stringResource(R.string.privacy_permission_body),
            )
            Spacer(Modifier.size(12.dp))
            PrivacyCard(
                icon = Icons.Outlined.BugReport,
                title = stringResource(R.string.privacy_crash_title),
                body = stringResource(R.string.privacy_crash_body),
            )
            Spacer(Modifier.size(12.dp))
            PrivacyCard(
                icon = Icons.Outlined.DeleteSweep,
                title = stringResource(R.string.privacy_delete_title),
                body = stringResource(R.string.privacy_delete_body),
            )
            Spacer(Modifier.size(32.dp))
        }
    }
}

@Composable
private fun PrivacyCard(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
