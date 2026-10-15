package me.rain.git.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** 只在活动任务期间绘制连续正弦波；空闲时保持高度，避免页面跳动。 */
@Composable
internal fun WaveLoadingBar(active: Boolean, modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.primary
    if (!active) {
        Canvas(modifier.height(9.dp)) { }
        return
    }
    val transition = rememberInfiniteTransition(label = "raingit-wave")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(1450), repeatMode = RepeatMode.Restart),
        label = "wave-phase"
    )
    Canvas(modifier.fillMaxWidth().height(9.dp)) {
        val mid = size.height * 0.5f
        drawLine(tint.copy(alpha = 0.10f), Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
        val path = Path()
        val step = 4.dp.toPx().coerceAtLeast(1f)
        val length = (size.width / 3.5f).coerceAtLeast(step)
        val amplitude = size.height * 0.32f
        var x = 0f
        while (x <= size.width + step) {
            val y = mid + sin((x / length * 2f * PI + phase.value).toDouble()).toFloat() * amplitude
            if (x == 0f) path.moveTo(x, y) else path.lineTo(x.coerceAtMost(size.width), y)
            x += step
        }
        drawPath(path, tint, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}
