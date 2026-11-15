package com.m1xamm.mxtube.ui.common

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

private val PlayShape =
    listOf(
        Offset(9.4f, 4.6f),
        Offset(20.2f, 12f),
        Offset(9.4f, 19.4f),
    )

private val PauseBars =
    listOf(
        listOf(Offset(7f, 5f), Offset(11.2f, 5f), Offset(11.2f, 19f), Offset(7f, 19f)),
        listOf(Offset(12.8f, 5f), Offset(17f, 5f), Offset(17f, 19f), Offset(12.8f, 19f)),
    )

@Composable
fun PlayPauseIcon(
    playing: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    contentDescription: String? = null,
) {
    val progress by
        animateFloatAsState(
            targetValue = if (playing) 1f else 0f,
            animationSpec = tween(220, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
            label = "play-pause-morph",
        )
    Canvas(
        modifier =
            modifier.then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
    ) {
        val unit = size.minDimension / 24f
        val p = progress.coerceIn(0f, 1f)
        val center = Offset(size.width / 2f, size.height / 2f)
        if (p < 1f) {
            val scaleValue = 0.72f + 0.28f * (1f - p)
            scale(scaleValue, scaleValue, center) {
                drawShape(PlayShape, 2.2f, unit, tint.copy(alpha = 1f - p))
            }
        }
        if (p > 0f) {
            val scaleValue = 0.72f + 0.28f * p
            scale(scaleValue, scaleValue, center) {
                PauseBars.forEach { bar -> drawShape(bar, 1.9f, unit, tint.copy(alpha = p)) }
            }
        }
    }
}

private fun DrawScope.drawShape(
    points: List<Offset>,
    radius: Float,
    unit: Float,
    color: Color,
) {
    val scaled = points.map { Offset(it.x * unit, it.y * unit) }
    drawPath(roundedPolygonPath(scaled, radius * unit), color)
}

private fun roundedPolygonPath(points: List<Offset>, radius: Float): Path {
    val path = Path()
    if (points.size < 3) return path
    val entries = ArrayList<Offset>(points.size)
    val exits = ArrayList<Offset>(points.size)
    for (i in points.indices) {
        val current = points[i]
        val previous = points[(i - 1 + points.size) % points.size]
        val next = points[(i + 1) % points.size]
        val toPrevious = previous - current
        val toNext = next - current
        val previousLength = kotlin.math.hypot(toPrevious.x, toPrevious.y)
        val nextLength = kotlin.math.hypot(toNext.x, toNext.y)
        val corner = radius.coerceAtMost(previousLength / 2f).coerceAtMost(nextLength / 2f)
        entries +=
            if (previousLength > 0.001f) current + toPrevious / previousLength * corner else current
        exits += if (nextLength > 0.001f) current + toNext / nextLength * corner else current
    }
    path.moveTo(entries[0].x, entries[0].y)
    for (i in points.indices) {
        val current = points[i]
        val nextEntry = entries[(i + 1) % points.size]
        val exit = exits[i]
        path.lineTo(exit.x, exit.y)
        path.quadraticTo(current.x, current.y, nextEntry.x, nextEntry.y)
    }
    path.close()
    return path
}
