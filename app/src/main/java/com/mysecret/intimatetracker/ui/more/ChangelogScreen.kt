package com.mysecret.intimatetracker.ui.more

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mysecret.intimatetracker.R
import com.mysecret.intimatetracker.ui.component.EmptyState
import com.mysecret.intimatetracker.ui.component.ScreenScaffold

private data class ChangeLogEntry(
    val version: String,
    val date: String?,
    val items: List<String>,
)

/** 从 assets/CHANGELOG.md 解析版本日志（构建时由根目录 CHANGELOG.md 复制而来）。 */
private fun loadChangelog(context: Context): List<ChangeLogEntry> = runCatching {
    val text = context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() }
    val pattern = Regex(
        "(?m)^## \\[(\\d+\\.\\d+\\.\\d+)\\](?: - (\\d{4}-\\d{2}-\\d{2}))?[^\\n]*\\n" +
            "([\\s\\S]*?)(?=\\n## \\[|\\n\\[Unreleased\\]:|\\z)",
    )
    pattern.findAll(text).map { match ->
        ChangeLogEntry(
            version = match.groupValues[1],
            date = match.groupValues[2].ifBlank { null },
            items = match.groupValues[3].lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("- ") }
                .map { it.removePrefix("- ").replace("**", "").replace("`", "") }
                .toList(),
        )
    }.toList()
}.getOrDefault(emptyList())

@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val entries = remember(context) { loadChangelog(context) }
    var expanded by remember(entries) {
        mutableStateOf(
            entries.firstOrNull()?.version?.let { setOf(it) } ?: emptySet(),
        )
    }

    ScreenScaffold(title = stringResource(R.string.changelog_title), onBack = onBack) { modifier ->
        if (entries.isEmpty()) {
            EmptyState(
                text = stringResource(R.string.changelog_empty),
                modifier = modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { it.version }) { entry ->
                    ChangeLogCard(
                        entry = entry,
                        expanded = entry.version in expanded,
                        onToggle = {
                            expanded = if (entry.version in expanded) {
                                expanded - entry.version
                            } else {
                                expanded + entry.version
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangeLogCard(
    entry: ChangeLogEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "v${entry.version}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            entry.date?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (expanded) 180f else 0f),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = 10.dp)) {
                entry.items.forEach { item ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(14.dp),
                        )
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
