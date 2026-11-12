package com.m1xamm.mxtube.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.m1xamm.mxtube.R
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import kotlin.math.roundToInt

@Composable
internal fun GlassBottomSheet(
    hazeState: HazeState,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable (close: () -> Unit) -> Unit,
) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    var closing by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    val offset by animateFloatAsState(
        targetValue = if (settling) 0f else drag,
        animationSpec = if (settling) spring(dampingRatio = Spring.DampingRatioNoBouncy) else snap(),
        label = "glass-sheet-drag",
    )
    val dismissDistance = with(LocalDensity.current) { 96.dp.toPx() }
    val dismiss by rememberUpdatedState(onDismiss)
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            visible.targetState = false
        }
    }
    BackHandler { close() }
    LaunchedEffect(closing, visible.isIdle, visible.currentState) {
        if (closing && visible.isIdle && !visible.currentState) dismiss()
    }
    LaunchedEffect(settling, offset) {
        if (settling && offset == 0f) {
            drag = 0f
            settling = false
        }
    }
    val closeLabel = stringResource(R.string.close)
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val scrimInteraction = remember { MutableInteractionSource() }
    val panelInteraction = remember { MutableInteractionSource() }

    AnimatedVisibility(
        visibleState = visible,
        modifier = Modifier.fillMaxSize().zIndex(5f),
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(160)),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maximumHeight = maxHeight * 0.84f
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = if (dark) 0.26f else 0.16f))
                    .semantics { contentDescription = closeLabel }
                    .clickable(scrimInteraction, indication = null, onClick = close),
            )
            Box(
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .animateEnterExit(
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            ),
                            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(180)),
                        )
                        .navigationBarsPadding()
                        .padding(8.dp)
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .heightIn(max = maximumHeight)
                        .offset { IntOffset(0, offset.roundToInt()) }
                        .clip(RoundedCornerShape(30.dp))
                        .hazeBlur(input = HazeInput.Sources(hazeState), style = HazeMaterials.ultraThin())
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.32f))
                        .semantics { paneTitle = title }
                        .clickable(panelInteraction, indication = null, onClick = {}),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth().height(48.dp)
                                .semantics { contentDescription = closeLabel }
                                .clickable(onClick = close)
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragStart = { settling = false },
                                        onVerticalDrag = { change, amount ->
                                            change.consume()
                                            drag = (drag + amount).coerceAtLeast(0f)
                                        },
                                        onDragEnd = {
                                            if (drag > dismissDistance) close() else settling = true
                                        },
                                        onDragCancel = { settling = true },
                                    )
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.width(36.dp).height(4.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)),
                        )
                    }
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        content(close)
                    }
                }
            }
        }
    }
}
