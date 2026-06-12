package kz.tcloud.dcinv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Faint grid pattern for screen backgrounds (≈3–5% opacity), per the design spec.
 */
@Composable
fun GridPatternBackground(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF1A57C7),
    cell: androidx.compose.ui.unit.Dp = 28.dp,
    alpha: Float = 0.04f,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val step = cell.toPx()
        val line = color.copy(alpha = alpha)
        var x = 0f
        while (x <= size.width) {
            drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
    }
}
