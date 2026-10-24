@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.m1xamm.mxtube.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun VideoSeekBar(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    onDraggingChanged: (Boolean) -> Unit,
    onScrubPositionChanged: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableIntStateOf(0) }

    val progress =
        if (durationMs > 0) {
            (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
    val bufferProgress =
        if (durationMs > 0) {
            (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
    val shown = if (dragging) dragFraction else progress

    val thumbSize by
        animateDpAsState(
            targetValue = if (dragging) 16.dp else 12.dp,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            label = "seek-thumb-size",
        )
    val waveAmount by
        animateFloatAsState(
            targetValue = if (isPlaying && !dragging) 0.5f else 0f,
            animationSpec = tween(220),
            label = "seek-wave",
        )

    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f)
    val bufferColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
    val activeColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val thumbRadius = with(density) { 6.dp.toPx() }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(28.dp)
                .onSizeChanged { widthPx = it.width },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val start = thumbRadius
            val end = size.width - thumbRadius
            if (end <= start) return@Canvas

            val centerY = size.height / 2f
            val stroke = 4.dp.toPx()
            drawLine(
                color = trackColor,
                start = Offset(start, centerY),
                end = Offset(end, centerY),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            if (bufferProgress > 0f) {
                drawLine(
                    color = bufferColor,
                    start = Offset(start, centerY),
                    end = Offset(start + (end - start) * bufferProgress, centerY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }

        LinearWavyProgressIndicator(
            progress = { shown },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
            color = activeColor,
            trackColor = Color.Transparent,
            gapSize = 0.dp,
            stopSize = 0.dp,
            amplitude = { waveAmount },
            wavelength = 40.dp,
            waveSpeed = 0.dp,
        )

        if (widthPx > 0) {
            val usable = (widthPx - 2 * thumbRadius).coerceAtLeast(0f)
            val thumbPx = with(density) { thumbSize.toPx() }
            val thumbCenter = thumbRadius + usable * shown
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset {
                            IntOffset(
                                x = (thumbCenter - thumbPx / 2f).roundToInt(),
                                y = 0,
                            )
                        }
                        .size(thumbSize)
                        .background(activeColor, CircleShape),
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(durationMs, thumbRadius) {
                        if (durationMs <= 0) return@pointerInput

                        fun fractionAt(x: Float): Float {
                            val usable = (size.width - 2 * thumbRadius).coerceAtLeast(1f)
                            return ((x - thumbRadius) / usable).coerceIn(0f, 1f)
                        }

                        awaitEachGesture {
                            val down = awaitFirstDown()
                            var fraction = fractionAt(down.position.x)

                            dragging = true
                            dragFraction = fraction
                            onDraggingChanged(true)
                            onScrubPositionChanged((fraction * durationMs).toLong())
                            down.consume()

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break

                                fraction = fractionAt(change.position.x)
                                dragFraction = fraction
                                onScrubPositionChanged((fraction * durationMs).toLong())
                                change.consume()
                            }

                            dragging = false
                            onDraggingChanged(false)
                            onSeek((fraction * durationMs).toLong())
                        }
                    },
        )
    }
}
