package su.kidoz.feature.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import su.kidoz.feature.editor.autocomplete.AutocompleteItem
import su.kidoz.feature.editor.autocomplete.AutocompleteType
import su.kidoz.ui.theme.DBQueTheme

@Composable
fun AutocompletePopup(
    items: List<AutocompleteItem>,
    selectedIndex: Int,
    onItemSelected: (AutocompleteItem) -> Unit,
    onDismiss: () -> Unit,
    offset: IntOffset = IntOffset(0, 20),
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        return
    }

    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0 && selectedIndex < items.size) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    Popup(
        onDismissRequest = onDismiss,
        offset = offset,
        properties = PopupProperties(focusable = false),
    ) {
        Surface(
            modifier =
                modifier
                    .width(300.dp)
                    .heightIn(max = 250.dp),
            shape = MaterialTheme.shapes.small,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(items) { index, item ->
                    AutocompleteItemRow(
                        item = item,
                        isSelected = index == selectedIndex,
                        onClick = { onItemSelected(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AutocompleteItemRow(
    item: AutocompleteItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        Color.Transparent
                    },
                ).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Type icon
        Icon(
            imageVector = getTypeIcon(item.type),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = getTypeColor(item.type),
        )

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Detail
        item.detail?.let { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        // Type label
        Surface(
            color = getTypeColor(item.type).copy(alpha = 0.15f),
            shape = MaterialTheme.shapes.extraSmall,
        ) {
            Text(
                text = item.type.name.take(3),
                style = MaterialTheme.typography.labelSmall,
                color = getTypeColor(item.type),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

private fun getTypeIcon(type: AutocompleteType): ImageVector =
    when (type) {
        AutocompleteType.KEYWORD -> Icons.Default.Code
        AutocompleteType.TABLE -> Icons.Default.TableRows
        AutocompleteType.VIEW -> Icons.Default.RemoveRedEye
        AutocompleteType.COLUMN -> Icons.Default.ViewColumn
        AutocompleteType.FUNCTION -> Icons.Default.Functions
        AutocompleteType.SCHEMA -> Icons.Default.Schema
        AutocompleteType.DATABASE -> Icons.Default.Storage
        AutocompleteType.ALIAS -> Icons.Default.Bookmark
        AutocompleteType.CTE -> Icons.Default.AccountTree
    }

@Composable
private fun getTypeColor(type: AutocompleteType): Color {
    val extendedColors = DBQueTheme.extendedColors
    return when (type) {
        AutocompleteType.KEYWORD -> extendedColors.syntaxKeyword
        AutocompleteType.TABLE -> extendedColors.treeTable
        AutocompleteType.VIEW -> extendedColors.treeView
        AutocompleteType.COLUMN -> extendedColors.treeColumn
        AutocompleteType.FUNCTION -> extendedColors.syntaxFunction
        AutocompleteType.SCHEMA -> MaterialTheme.colorScheme.tertiary
        AutocompleteType.DATABASE -> MaterialTheme.colorScheme.primary
        AutocompleteType.ALIAS -> extendedColors.treeTable.copy(alpha = 0.8f)
        AutocompleteType.CTE -> extendedColors.syntaxFunction.copy(alpha = 0.8f)
    }
}
