package su.kidoz.feature.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import su.kidoz.feature.parser.validation.IssueSeverity
import su.kidoz.feature.parser.validation.ValidationIssue

/**
 * Panel displaying validation issues in a table-like format.
 * Used as a tab alongside Results and Query Plan.
 */
@Composable
fun IssuesPanel(
    issues: List<ValidationIssue>,
    content: String,
    onIssueClick: (ValidationIssue) -> Unit,
    onQuickFix: (ValidationIssue) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
    ) {
        if (issues.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No issues found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Your query looks good!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with issue counts
                IssuesHeader(issues = issues)

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Issues list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(issues, key = { "${it.code}-${it.position.start}" }) { issue ->
                        IssueRow(
                            issue = issue,
                            content = content,
                            onClick = { onIssueClick(issue) },
                            onQuickFix = { onQuickFix(issue) },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IssuesHeader(issues: List<ValidationIssue>) {
    val errorCount = issues.count { it.severity == IssueSeverity.ERROR }
    val warningCount = issues.count { it.severity == IssueSeverity.WARNING }
    val infoCount = issues.count { it.severity == IssueSeverity.INFO }
    val hintCount = issues.count { it.severity == IssueSeverity.HINT }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Issues (${issues.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.weight(1f))

        if (errorCount > 0) {
            IssueCountBadge(
                count = errorCount,
                severity = IssueSeverity.ERROR,
            )
        }
        if (warningCount > 0) {
            IssueCountBadge(
                count = warningCount,
                severity = IssueSeverity.WARNING,
            )
        }
        if (infoCount > 0) {
            IssueCountBadge(
                count = infoCount,
                severity = IssueSeverity.INFO,
            )
        }
        if (hintCount > 0) {
            IssueCountBadge(
                count = hintCount,
                severity = IssueSeverity.HINT,
            )
        }
    }
}

@Composable
private fun IssueCountBadge(
    count: Int,
    severity: IssueSeverity,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector =
                when (severity) {
                    IssueSeverity.ERROR -> Icons.Default.Error
                    IssueSeverity.WARNING -> Icons.Default.Warning
                    IssueSeverity.INFO -> Icons.Default.Info
                    IssueSeverity.HINT -> Icons.Default.Info
                },
            contentDescription = severity.name,
            tint = IssueColors.forSeverity(severity),
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = IssueColors.forSeverity(severity),
        )
    }
}

@Composable
private fun IssueRow(
    issue: ValidationIssue,
    content: String,
    onClick: () -> Unit,
    onQuickFix: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Severity icon
        Icon(
            imageVector =
                when (issue.severity) {
                    IssueSeverity.ERROR -> Icons.Default.Error
                    IssueSeverity.WARNING -> Icons.Default.Warning
                    IssueSeverity.INFO -> Icons.Default.Info
                    IssueSeverity.HINT -> Icons.Default.Info
                },
            contentDescription = issue.severity.name,
            tint = IssueColors.forSeverity(issue.severity),
            modifier = Modifier.size(18.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Issue code
        Text(
            text = issue.code,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(60.dp),
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Message and suggestion
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = issue.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            issue.suggestion?.let { suggestion ->
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Location (line:column)
        val line = getLineNumber(content, issue.position.start)
        val col = getColumnNumber(content, issue.position.start)
        Text(
            text = "$line:$col",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(50.dp),
        )

        // Quick fix button
        TextButton(
            onClick = onQuickFix,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = "Fix",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
