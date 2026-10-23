package com.m1xamm.mxtube.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun VideoSeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
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
        val thumbRadius = 5.dp.toPx()

        drawLine(
            color = Color.White.copy(alpha = 0.25f),
            start = Offset(inset, centerY),
            end = Offset(inset + trackWidth, centerY),
            strokeWidth = trackHeight,
            cap = StrokeCap.Round,
        )

        if (buffered > 0f) {
            drawLine(
                color = Color.White.copy(alpha = 0.45f),
                start = Offset(inset, centerY),
                end = Offset(inset + trackWidth * buffered, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
            )
        }

        drawLine(
            color = Color.White,
            start = Offset(inset, centerY),
            end = Offset(inset + trackWidth * shown, centerY),
            strokeWidth = trackHeight,
            cap = StrokeCap.Round,
        )

        drawCircle(
            color = Color.White,
            radius = thumbRadius,
            center = Offset(inset + trackWidth * shown, centerY),
        )
    }
}
