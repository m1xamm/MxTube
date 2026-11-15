package com.m1xamm.mxtube.ui.common

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.MaterialTheme
import kotlin.math.hypot

private data class MorphPoint(
    val from: Offset,
    val to: Offset,
    val radiusFrom: Float,
    val radiusTo: Float,
)

private val LeftLobe =
    listOf(
        MorphPoint(Offset(9f, 5f), Offset(6.5f, 5f), 1.5f, 1.5f),
        MorphPoint(Offset(14.5f, 8.4f), Offset(10.5f, 5f), 0f, 1.5f),
        MorphPoint(Offset(14.5f, 15.6f), Offset(10.5f, 19f), 0f, 1.5f),
        MorphPoint(Offset(9f, 19f), Offset(6.5f, 19f), 1.5f, 1.5f),
    )

private val RightLobe =
    listOf(
        MorphPoint(Offset(14.5f, 8.4f), Offset(13.5f, 5f), 0f, 1.5f),
        MorphPoint(Offset(20f, 12f), Offset(17.5f, 5f), 1.5f, 1.5f),
        MorphPoint(Offset(14.5f, 15.6f), Offset(17.5f, 19f), 0f, 1.5f),
        MorphPoint(Offset(14.5f, 15.6f), Offset(13.5f, 19f), 0f, 1.5f),
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
            animationSpec = tween(260, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
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
        drawLobe(LeftLobe, p, unit, tint)
        drawLobe(RightLobe, p, unit, tint)
    }
}

private fun DrawScope.drawLobe(
    points: List<MorphPoint>,
    progress: Float,
    unit: Float,
    tint: Color,
) {
    val positions =
        points.map { point ->
            val position = lerp(point.from, point.to, progress)
            Offset(position.x * unit, position.y * unit)
        }
    val radii =
        points.map { point ->
            (point.radiusFrom + (point.radiusTo - point.radiusFrom) * progress) * unit
        }
    drawPath(roundedPolygonPath(positions, radii), tint)
}

private fun roundedPolygonPath(points: List<Offset>, radii: List<Float>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    val entries = mutableListOf<Offset>()
    val exits = mutableListOf<Offset>()
    for (i in points.indices) {
        val current = points[i]
        val previous = points[(i - 1 + points.size) % points.size]
        val next = points[(i + 1) % points.size]
        val radius = radii[i].coerceAtLeast(0f)
        val toPrevious = previous - current
        val toNext = next - current
        val previousLength = hypot(toPrevious.x, toPrevious.y)
        val nextLength = hypot(toNext.x, toNext.y)
        val entryRadius = radius.coerceAtMost(previousLength / 2f).coerceAtMost(nextLength / 2f)
        val exitRadius = radius.coerceAtMost(previousLength / 2f).coerceAtMost(nextLength / 2f)
        entries +=
            if (previousLength > 0.001f) {
                current + toPrevious / previousLength * entryRadius
            } else {
                current
            }
        exits +=
            if (nextLength > 0.001f) {
                current + toNext / nextLength * exitRadius
            } else {
                current
            }
    }
    path.moveTo(entries[0].x, entries[0].y)
    for (i in points.indices) {
        val current = points[i]
        val nextEntry = entries[(i + 1) % points.size]
        val exit = exits[i]
        if (exit != current) {
            path.lineTo(exit.x, exit.y)
        }
        if (exit != nextEntry) {
            path.quadraticTo(current.x, current.y, nextEntry.x, nextEntry.y)
        }
    }
    path.close()
    return path
}

private fun lerp(from: Offset, to: Offset, progress: Float): Offset =
    Offset(
        from.x + (to.x - from.x) * progress,
        from.y + (to.y - from.y) * progress,
    )
