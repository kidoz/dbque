package su.kidoz.feature.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import su.kidoz.feature.parser.validation.IssueSeverity
import su.kidoz.feature.parser.validation.ValidationIssue

/**
 * Colors for different severity levels
 */
object IssueColors {
    val error = Color(0xFFF44747)
    val warning = Color(0xFFFFCC00)
    val info = Color(0xFF3794FF)
    val hint = Color(0xFF6A9955)

    fun forSeverity(severity: IssueSeverity): Color =
        when (severity) {
            IssueSeverity.ERROR -> error
            IssueSeverity.WARNING -> warning
            IssueSeverity.INFO -> info
            IssueSeverity.HINT -> hint
        }
}

/**
 * Draw wavy underline markers for validation issues.
 * This should be drawn as an overlay on top of the text field.
 */
@Composable
fun InlineErrorMarkers(
    issues: List<ValidationIssue>,
    textLayoutResult: TextLayoutResult?,
    lineHeight: Dp,
    modifier: Modifier = Modifier,
    onIssueClick: (ValidationIssue) -> Unit = {},
) {
    if (issues.isEmpty() || textLayoutResult == null) return

    val density = LocalDensity.current

    Canvas(modifier = modifier) {
        issues.forEach { issue ->
            val startOffset = issue.position.start.coerceIn(0, textLayoutResult.layoutInput.text.length)
            val endOffset = issue.position.end.coerceIn(startOffset, textLayoutResult.layoutInput.text.length)

            if (startOffset == endOffset) return@forEach

            val color = IssueColors.forSeverity(issue.severity)

            try {
                // Get bounding boxes for the issue range
                val startBounds = textLayoutResult.getBoundingBox(startOffset)
                val endBounds = textLayoutResult.getBoundingBox((endOffset - 1).coerceAtLeast(startOffset))

                // For single-line issues, draw a wavy underline
                if (startBounds.top == endBounds.top) {
                    drawWavyLine(
                        color = color,
                        startX = startBounds.left,
                        endX = endBounds.right,
                        y = startBounds.bottom + 2f,
                    )
                } else {
                    // Multi-line issue: draw wavy lines for each line
                    val startLine = textLayoutResult.getLineForOffset(startOffset)
                    val endLine = textLayoutResult.getLineForOffset(endOffset - 1)

                    for (line in startLine..endLine) {
                        val lineStart = if (line == startLine) startBounds.left else textLayoutResult.getLineLeft(line)
                        val lineEnd = if (line == endLine) endBounds.right else textLayoutResult.getLineRight(line)
                        val lineBottom = textLayoutResult.getLineBottom(line)

                        drawWavyLine(
                            color = color,
                            startX = lineStart,
                            endX = lineEnd,
                            y = lineBottom + 2f,
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore layout exceptions for out-of-bounds positions
            }
        }
    }
}

/**
 * Draw a wavy/squiggly line
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWavyLine(
    color: Color,
    startX: Float,
    endX: Float,
    y: Float,
    amplitude: Float = 2f,
    wavelength: Float = 4f,
) {
    val path = Path()
    var x = startX
    var phase = 0f

    path.moveTo(startX, y)

    while (x < endX) {
        val nextX = (x + wavelength / 2).coerceAtMost(endX)
        val yOffset = if (phase % 2 == 0f) amplitude else -amplitude
        path.quadraticTo(
            x + wavelength / 4,
            y + yOffset,
            nextX,
            y,
        )
        x = nextX
        phase++
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 1.5f),
    )
}

/**
 * Issue indicator that can be placed in the gutter or at issue position.
 * Shows a colored dot that can be clicked to see issue details.
 */
@Composable
fun IssueIndicator(
    issue: ValidationIssue,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = IssueColors.forSeverity(issue.severity)

    Box(
        modifier =
            modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(4.dp))
                .clickable { onClick() },
    )
}

/**
 * Tooltip-style popup showing issue details
 */
@Composable
fun IssueTooltip(
    issue: ValidationIssue,
    modifier: Modifier = Modifier,
) {
    val backgroundColor =
        when (issue.severity) {
            IssueSeverity.ERROR -> Color(0xFF3D2020)
            IssueSeverity.WARNING -> Color(0xFF3D3D20)
            IssueSeverity.INFO -> Color(0xFF203D3D)
            IssueSeverity.HINT -> Color(0xFF203D20)
        }

    Box(
        modifier =
            modifier
                .background(backgroundColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = "[${issue.code}] ${issue.message}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
        )
    }
}

/**
 * Calculate position information for issues based on text layout
 */
data class IssuePosition(
    val issue: ValidationIssue,
    val line: Int,
    val column: Int,
    val x: Float,
    val y: Float,
    val width: Float,
)

/**
 * Calculate positions for all issues
 */
fun calculateIssuePositions(
    issues: List<ValidationIssue>,
    textLayoutResult: TextLayoutResult?,
    content: String,
): List<IssuePosition> {
    if (textLayoutResult == null || issues.isEmpty()) return emptyList()

    return issues.mapNotNull { issue ->
        try {
            val startOffset = issue.position.start.coerceIn(0, content.length)
            val endOffset = issue.position.end.coerceIn(startOffset, content.length)

            if (startOffset == endOffset) return@mapNotNull null

            val startBounds = textLayoutResult.getBoundingBox(startOffset)
            val line = textLayoutResult.getLineForOffset(startOffset)

            // Calculate column (character position within line)
            val lineStart = textLayoutResult.getLineStart(line)
            val column = startOffset - lineStart

            IssuePosition(
                issue = issue,
                line = line + 1, // 1-indexed for display
                column = column + 1, // 1-indexed for display
                x = startBounds.left,
                y = startBounds.top,
                width =
                    if (startOffset < content.length) {
                        val endBounds = textLayoutResult.getBoundingBox((endOffset - 1).coerceAtLeast(startOffset))
                        endBounds.right - startBounds.left
                    } else {
                        10f
                    },
            )
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Get line number for a character offset
 */
fun getLineNumber(
    content: String,
    offset: Int,
): Int {
    if (offset <= 0) return 1
    val safeOffset = offset.coerceAtMost(content.length)
    return content.substring(0, safeOffset).count { it == '\n' } + 1
}

/**
 * Get column number for a character offset
 */
fun getColumnNumber(
    content: String,
    offset: Int,
): Int {
    if (offset <= 0) return 1
    val safeOffset = offset.coerceAtMost(content.length)
    val lastNewline = content.substring(0, safeOffset).lastIndexOf('\n')
    return safeOffset - lastNewline
}
