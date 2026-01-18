package su.kidoz.feature.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LightbulbCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import su.kidoz.feature.editor.quickfix.QuickFix
import su.kidoz.feature.parser.validation.IssueSeverity
import su.kidoz.feature.parser.validation.ValidationIssue

/**
 * Light bulb icon button that shows quick-fix options when clicked.
 * Appears in the editor gutter when there are available fixes.
 */
@Composable
fun QuickFixButton(
    issue: ValidationIssue,
    fixes: List<QuickFix>,
    onApplyFix: (QuickFix) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    if (fixes.isEmpty()) return

    val iconColor =
        when (issue.severity) {
            IssueSeverity.ERROR -> Color(0xFFF44747)
            IssueSeverity.WARNING -> Color(0xFFFFCC00)
            IssueSeverity.INFO -> Color(0xFF3794FF)
            IssueSeverity.HINT -> Color(0xFF6A9955)
        }

    IconButton(
        onClick = { showMenu = true },
        modifier = modifier.size(20.dp),
    ) {
        Icon(
            imageVector = Icons.Default.LightbulbCircle,
            contentDescription = "Quick fixes available",
            tint = iconColor,
            modifier = Modifier.size(16.dp),
        )

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            Text(
                text = "Quick Fixes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )

            fixes.forEach { fix ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = fix.title,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    },
                    onClick = {
                        showMenu = false
                        onApplyFix(fix)
                    },
                )
            }
        }
    }
}

/**
 * Popup menu showing available quick-fixes for an issue.
 * Can be triggered by clicking an issue or pressing Alt+Enter.
 */
@Composable
fun QuickFixPopup(
    issue: ValidationIssue,
    fixes: List<QuickFix>,
    onApplyFix: (QuickFix) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor =
        when (issue.severity) {
            IssueSeverity.ERROR -> Color(0xFF3D2020)
            IssueSeverity.WARNING -> Color(0xFF3D3D20)
            IssueSeverity.INFO -> Color(0xFF203D3D)
            IssueSeverity.HINT -> Color(0xFF203D20)
        }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 8.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(4.dp),
        ) {
            // Issue header
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(backgroundColor, RoundedCornerShape(4.dp))
                        .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.LightbulbCircle,
                    contentDescription = null,
                    tint = IssueColors.forSeverity(issue.severity),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "[${issue.code}] ${issue.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                    )
                    issue.suggestion?.let { suggestion ->
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            if (fixes.isNotEmpty()) {
                // Available fixes
                Text(
                    text = "Available Fixes (Alt+Enter)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )

                fixes.forEachIndexed { index, fix ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onApplyFix(fix)
                                    onDismiss()
                                }.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = fix.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            } else {
                Text(
                    text = "No quick-fixes available",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

/**
 * Inline quick-fix suggestion shown below the issue
 */
@Composable
fun InlineQuickFixSuggestion(
    issue: ValidationIssue,
    fixes: List<QuickFix>,
    onApplyFix: (QuickFix) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (fixes.isEmpty()) return

    val primaryFix = fixes.first()
    val hasMoreFixes = fixes.size > 1

    Row(
        modifier =
            modifier
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    RoundedCornerShape(4.dp),
                ).clickable { onApplyFix(primaryFix) }
                .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Default.LightbulbCircle,
            contentDescription = null,
            tint = IssueColors.forSeverity(issue.severity),
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = primaryFix.title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (hasMoreFixes) {
            Text(
                text = "(+${fixes.size - 1} more)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
