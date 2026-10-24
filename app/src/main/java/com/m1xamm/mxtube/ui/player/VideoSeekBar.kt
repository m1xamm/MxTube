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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

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

    val handleHeight by
        animateDpAsState(
            targetValue = if (dragging) 24.dp else 18.dp,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            label = "seek-handle-height",
        )
    val waveLevel by
        animateFloatAsState(
            targetValue = if (isPlaying && !dragging) 1f else 0f,
            animationSpec = tween(220),
            label = "seek-wave-level",
        )

    val density = LocalDensity.current
    val inset = with(density) { 6.dp.toPx() }
    val handleWidth = with(density) { 4.dp.toPx() }
    val handleGap = with(density) { 3.dp.toPx() }
    val trackThickness = with(density) { 4.dp.toPx() }
    val wavelength = with(density) { 32.dp.toPx() }
    val waveAmplitude = with(density) { 1.9.dp.toPx() } * waveLevel

    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
    val bufferColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
    val darkTheme = MaterialTheme.colorScheme.surfaceContainerHighest.luminance() < 0.5f
    val activeColor =
        if (darkTheme) {
            MaterialTheme.colorScheme.primary
        } else {
            lerp(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.onSurface,
                0.18f,
            )
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(28.dp)
                .onSizeChanged { widthPx = it.width },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val start = inset
            val end = size.width - inset
            if (end <= start) return@Canvas

            val centerY = size.height / 2f
            val thumbCenter = start + (end - start) * shown
            val activeEnd = (thumbCenter - handleWidth / 2f - handleGap).coerceAtLeast(start)
            val inactiveStart = (thumbCenter + handleWidth / 2f + handleGap).coerceAtMost(end)

            if (inactiveStart < end) {
                drawLine(
                    color = trackColor,
                    start = Offset(inactiveStart, centerY),
                    end = Offset(end, centerY),
                    strokeWidth = trackThickness,
                    cap = StrokeCap.Round,
                )
            }
            val bufferEnd = start + (end - start) * bufferProgress
            if (bufferEnd > inactiveStart) {
                drawLine(
                    color = bufferColor,
                    start = Offset(inactiveStart, centerY),
                    end = Offset(bufferEnd, centerY),
                    strokeWidth = trackThickness,
                    cap = StrokeCap.Round,
                )
            }

            val activeLength = activeEnd - start
            if (activeLength <= 0f) return@Canvas

            val envelope = ((activeLength - 10.dp.toPx()) / 24.dp.toPx()).coerceIn(0f, 1f)
            val amplitude = waveAmplitude * envelope
            if (amplitude < 0.05f) {
                drawLine(
                    color = activeColor,
                    start = Offset(start, centerY),
                    end = Offset(activeEnd, centerY),
                    strokeWidth = trackThickness,
                    cap = StrokeCap.Round,
                )
            } else {
                val path = Path()
                path.moveTo(start, centerY)
                val step = 0.75.dp.toPx()
                var x = start
                while (x < activeEnd) {
                    val phase = ((x - start) / wavelength) * 2f * PI.toFloat()
                    path.lineTo(x, centerY + sin(phase) * amplitude)
                    x += step
                }
                val lastPhase = ((activeEnd - start) / wavelength) * 2f * PI.toFloat()
                path.lineTo(activeEnd, centerY + sin(lastPhase) * amplitude)
                drawPath(
                    path = path,
                    color = activeColor,
                    style =
                        Stroke(
                            width = trackThickness,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                )
            }
        }

        if (widthPx > 0) {
            val start = inset
            val end = widthPx - inset
            val thumbCenter = start + (end - start) * shown
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset {
                            IntOffset(
                                x = (thumbCenter - handleWidth / 2f).roundToInt(),
                                y = 0,
                            )
                        }
                        .size(width = 4.dp, height = handleHeight)
                        .background(activeColor, RoundedCornerShape(2.dp)),
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(durationMs, inset) {
                        if (durationMs <= 0) return@pointerInput

                        fun fractionAt(x: Float): Float {
                            val usable = (size.width - 2f * inset).coerceAtLeast(1f)
                            return ((x - inset) / usable).coerceIn(0f, 1f)
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
