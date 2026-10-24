package com.mysecret.intimatetracker.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 表单输入框：与「新增伴侣」一致的外框样式（8dp 圆角、18sp 正文、浮动标签）。
 * 伴侣页、字典弹窗、记录表单共用，改这里即可同时生效。
 */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    placeholder: String? = null,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = if (label != null) {
            { Text(label) }
        } else {
            null
        },
        placeholder = if (placeholder != null) {
            { Text(placeholder) }
        } else {
            null
        },
        singleLine = singleLine,
        minLines = minLines,
        shape = RoundedCornerShape(8.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * 表单选项框：与 [FormTextField] 同一套外框（8dp 圆角、18sp 正文、浮动标签），
 * 点击整块区域弹出下拉菜单。[label] 为空时不画浮动标签（如记录表单，分组卡标题即标签）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> FormSelectField(
    value: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    label: String? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = if (label != null) {
                { Text(label) }
            } else {
                null
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            shape = RoundedCornerShape(8.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (item, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(item)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun Stepper(
    value: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    display: (Int) -> String = { it.toString() },
    range: IntRange = 0..99,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(display(value), style = MaterialTheme.typography.bodyLarge)
        Row {
            IconButton(onClick = onMinus, enabled = value > range.first) {
                Icon(Icons.Filled.Remove, contentDescription = "减少")
            }
            IconButton(onClick = onPlus, enabled = value < range.last) {
                Icon(Icons.Filled.Add, contentDescription = "增加")
            }
        }
    }
}

@Composable
fun StarRating(value: Int, onSelect: (Int) -> Unit, max: Int = 5) {
    Row {
        repeat(max) { index ->
            val starValue = index + 1
            IconButton(onClick = { onSelect(if (value == starValue) 0 else starValue) }) {
                Icon(
                    imageVector = if (starValue <= value) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "$starValue 星",
                    tint = if (starValue <= value) {
                        com.mysecret.intimatetracker.ui.theme.StarColor
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
fun MultiChipGroup(
    items: List<Pair<String, String>>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { (id, label) ->
            FilterChip(
                selected = id in selected,
                onClick = { onToggle(id) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
fun SingleChipGroup(
    items: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { (id, label) ->
            FilterChip(
                selected = id == selected,
                onClick = { onSelect(if (selected == id) null else id) },
                label = { Text(label) },
            )
        }
    }
}
