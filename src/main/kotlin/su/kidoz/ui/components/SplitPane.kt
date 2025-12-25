package su.kidoz.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Cursor

@Composable
fun HorizontalSplitPane(
    modifier: Modifier = Modifier,
    splitFraction: Float = 0.25f,
    minFirstSize: Dp = 150.dp,
    minSecondSize: Dp = 200.dp,
    firstPane: @Composable () -> Unit,
    secondPane: @Composable () -> Unit,
) {
    var fraction by remember { mutableStateOf(splitFraction) }
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val constraintsMaxWidth = maxWidth
        val maxWidthPx = with(density) { constraintsMaxWidth.toPx() }
        val minFirstPx = with(density) { minFirstSize.toPx() }
        val minSecondPx = with(density) { minSecondSize.toPx() }

        Row(modifier = Modifier.fillMaxSize()) {
            // First pane
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(constraintsMaxWidth * fraction),
            ) {
                firstPane()
            }

            // Divider
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val newFraction = fraction + (dragAmount.x / maxWidthPx)
                                val firstPanePx = maxWidthPx * newFraction
                                val secondPanePx = maxWidthPx * (1 - newFraction)

                                if (firstPanePx >= minFirstPx && secondPanePx >= minSecondPx) {
                                    fraction = newFraction.coerceIn(0.1f, 0.9f)
                                }
                            }
                        },
            )

            // Second pane
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .weight(1f),
            ) {
                secondPane()
            }
        }
    }
}

@Composable
fun VerticalSplitPane(
    modifier: Modifier = Modifier,
    splitFraction: Float = 0.6f,
    minFirstSize: Dp = 150.dp,
    minSecondSize: Dp = 100.dp,
    firstPane: @Composable () -> Unit,
    secondPane: @Composable () -> Unit,
) {
    var fraction by remember { mutableStateOf(splitFraction) }
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val constraintsMaxHeight = maxHeight
        val maxHeightPx = with(density) { constraintsMaxHeight.toPx() }
        val minFirstPx = with(density) { minFirstSize.toPx() }
        val minSecondPx = with(density) { minSecondSize.toPx() }

        Column(modifier = Modifier.fillMaxSize()) {
            // First pane
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(constraintsMaxHeight * fraction),
            ) {
                firstPane()
            }

            // Divider
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        .pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val newFraction = fraction + (dragAmount.y / maxHeightPx)
                                val firstPanePx = maxHeightPx * newFraction
                                val secondPanePx = maxHeightPx * (1 - newFraction)

                                if (firstPanePx >= minFirstPx && secondPanePx >= minSecondPx) {
                                    fraction = newFraction.coerceIn(0.1f, 0.9f)
                                }
                            }
                        },
            )

            // Second pane
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) {
                secondPane()
            }
        }
    }
}
