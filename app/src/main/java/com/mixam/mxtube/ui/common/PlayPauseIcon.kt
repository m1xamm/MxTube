package com.mixam.mxtube.ui.common

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.hypot

private class Vertex(
    val from: Offset,
    val to: Offset,
    val radiusFrom: Float,
    val radiusTo: Float,
)

private val LeftLobe =
    listOf(
        Vertex(Offset(8.6f, 4.8f), Offset(6f, 5f), 2.4f, 1.2f),
        Vertex(Offset(12f, 8.2f), Offset(10f, 5f), 0f, 1.2f),
        Vertex(Offset(12f, 15.8f), Offset(10f, 19f), 0f, 1.2f),
        Vertex(Offset(8.6f, 19.2f), Offset(6f, 19f), 2.4f, 1.2f),
    )

private val RightLobe =
    listOf(
        Vertex(Offset(12f, 8.2f), Offset(14f, 5f), 0f, 1.2f),
        Vertex(Offset(19.4f, 12f), Offset(18f, 5f), 2.4f, 1.2f),
        Vertex(Offset(12f, 15.8f), Offset(18f, 19f), 0f, 1.2f),
        Vertex(Offset(12f, 12f), Offset(14f, 19f), 0f, 1.2f),
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
            animationSpec = tween(240, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
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
    vertices: List<Vertex>,
    progress: Float,
    unit: Float,
    tint: Color,
) {
    val points =
        vertices.map { vertex ->
            val x = vertex.from.x + (vertex.to.x - vertex.from.x) * progress
            val y = vertex.from.y + (vertex.to.y - vertex.from.y) * progress
            Offset(x * unit, y * unit)
        }
    val radii =
        vertices.map { vertex ->
            (vertex.radiusFrom + (vertex.radiusTo - vertex.radiusFrom) * progress) * unit
        }
    drawPath(roundedPolygonPath(points, radii), tint)
}

private fun roundedPolygonPath(
    points: List<Offset>,
    radii: List<Float>,
): Path {
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
        val previousLength = hypot(toPrevious.x, toPrevious.y)
        val nextLength = hypot(toNext.x, toNext.y)
        val corner = radii[i].coerceAtMost(previousLength / 2f).coerceAtMost(nextLength / 2f)
        entries +=
            if (previousLength > 0.01f) current + toPrevious / previousLength * corner else current
        exits += if (nextLength > 0.01f) current + toNext / nextLength * corner else current
    }
    path.moveTo(entries[0].x, entries[0].y)
    for (i in points.indices) {
        val current = points[i]
        val nextEntry = entries[(i + 1) % points.size]
        val exit = exits[i]
        if ((exit - current).getDistance() > 0.01f) {
            path.lineTo(exit.x, exit.y)
        }
        if ((nextEntry - current).getDistance() > 0.01f) {
            path.quadraticTo(current.x, current.y, nextEntry.x, nextEntry.y)
        }
    }
    path.close()
    return path
}
