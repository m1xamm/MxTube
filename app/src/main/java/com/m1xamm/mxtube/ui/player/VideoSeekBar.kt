@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.m1xamm.mxtube.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun VideoSeekBar(
    positionMs: Long,
    durationMs: Long,
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
    val shown = if (dragging) dragFraction else progress
    val amplitude: (Float) -> Float =
        if (isPlaying) {
            { progress -> WavyProgressIndicatorDefaults.indicatorAmplitude(progress) * 0.22f }
        } else {
            { 0f }
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(28.dp),
    ) {
        LinearWavyProgressIndicator(
            progress = { shown },
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            amplitude = amplitude,
        )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
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
        )
    }
}
