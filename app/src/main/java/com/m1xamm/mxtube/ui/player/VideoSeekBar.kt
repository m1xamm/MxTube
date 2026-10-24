package com.m1xamm.mxtube.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.isActive

@Composable
fun VideoSeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    onDraggingChanged: (Boolean) -> Unit,
    onScrubPositionChanged: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val progress =
        if (durationMs > 0) {
            (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
    val buffered =
        if (durationMs > 0) {
            (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
    val shown = if (dragging) dragFraction else progress
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val bufferedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val progressColor = MaterialTheme.colorScheme.primary
    val wavePhase = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            wavePhase.snapTo(0f)
            return@LaunchedEffect
        }
        while (isActive) {
            wavePhase.snapTo(0f)
            wavePhase.animateTo(
                targetValue = (2f * PI).toFloat(),
                animationSpec = tween(durationMillis = 1400, easing = LinearEasing),
            )
        }
    }

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(durationMs) {
                    if (durationMs <= 0) return@pointerInput

                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        var fraction = (down.position.x / width).coerceIn(0f, 1f)

                        dragging = true
                        dragFraction = fraction
                        onDraggingChanged(true)
                        onScrubPositionChanged((fraction * durationMs).toLong())
                        down.consume()

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            fraction = (change.position.x / width).coerceIn(0f, 1f)
                            dragFraction = fraction
                            onScrubPositionChanged((fraction * durationMs).toLong())
                            change.consume()
                        }

                        dragging = false
                        onDraggingChanged(false)
                        onSeek((fraction * durationMs).toLong())
                    }
                },
    ) {
        val centerY = size.height / 2f
        val inset = 6.dp.toPx()
        val trackWidth = (size.width - inset * 2f).coerceAtLeast(0f)
        val trackHeight = 2.5.dp.toPx()
        val progressHeight = 3.dp.toPx()
        val thumbRadius = 5.dp.toPx()
        val amplitude = 2.5.dp.toPx()
        val wavelength = 24.dp.toPx()
        val step = 1.5.dp.toPx()
        val edgeFade = 8.dp.toPx()

        fun wavePath(endX: Float): Path {
            val path = Path()
            if (endX <= inset) return path
            var x = inset
            var first = true
            while (x <= endX) {
                val fromStart = x - inset
                val toEnd = endX - x
                val taper = (minOf(fromStart, toEnd) / edgeFade).coerceIn(0f, 1f)
                val y =
                    centerY +
                        sin((fromStart / wavelength) * 2f * PI.toFloat() + wavePhase.value) *
                        amplitude * taper
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
                x += step
            }
            path.lineTo(endX, centerY)
            return path
        }

        drawPath(
            path = wavePath(inset + trackWidth),
            color = trackColor,
            style = Stroke(width = trackHeight, cap = StrokeCap.Round),
        )

        if (buffered > 0f) {
            drawPath(
                path = wavePath(inset + trackWidth * buffered),
                color = bufferedColor,
                style = Stroke(width = trackHeight, cap = StrokeCap.Round),
            )
        }

        if (shown > 0f) {
            drawPath(
                path = wavePath(inset + trackWidth * shown),
                color = progressColor,
                style = Stroke(width = progressHeight, cap = StrokeCap.Round),
            )
        }

        drawCircle(
            color = progressColor,
            radius = thumbRadius,
            center = Offset(inset + trackWidth * shown, centerY),
        )
    }
}
