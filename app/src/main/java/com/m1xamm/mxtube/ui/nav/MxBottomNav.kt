package com.m1xamm.mxtube.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials

private val NavEdgePadding = 8.dp
private val NavItemGap = 4.dp
private val ActiveSlotPadding = 8.dp
private val InactiveSlotPadding = 10.dp
private val ActivePillPadding = 10.dp
private val IconLabelGap = 6.dp
private val NavIconSize = 24.dp
private val BarEndPadding = 6.dp
private const val SpatialMillis = 360
private const val CollapseMillis = 240
private const val ColorMillis = 240
private val ExpressiveEasing = CubicBezierEasing(0.26f, 1.06f, 0.18f, 1f)

@Composable
fun MxBottomNav(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val startEdgePadding by
        animateDpAsState(
            targetValue = if (selected == MainTab.Home) 0.dp else BarEndPadding,
            animationSpec = tween(SpatialMillis, easing = FastOutSlowInEasing),
            label = "nav-start-edge",
        )
    val endEdgePadding by
        animateDpAsState(
            targetValue = if (selected == MainTab.Settings) 0.dp else BarEndPadding,
            animationSpec = tween(SpatialMillis, easing = FastOutSlowInEasing),
            label = "nav-end-edge",
        )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = NavEdgePadding, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(56.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .hazeBlur(
                    input = HazeInput.Sources(hazeState),
                    style = HazeMaterials.ultraThin(),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier =
                    Modifier
                        .padding(
                            start = startEdgePadding,
                            end = endEdgePadding,
                            top = 4.dp,
                            bottom = 4.dp,
                        )
                        .height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(NavItemGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MainTab.entries.forEach { tab ->
                    FloatingNavItem(
                        tab = tab,
                        selected = tab == selected,
                        onClick = { onSelect(tab) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingNavItem(
    tab: MainTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container by
        animateColorAsState(
            targetValue =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
            animationSpec = tween(ColorMillis, easing = FastOutSlowInEasing),
            label = "nav-container-${tab.name}",
        )
    val content by
        animateColorAsState(
            targetValue =
                if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            animationSpec = tween(ColorMillis, easing = FastOutSlowInEasing),
            label = "nav-content-${tab.name}",
        )
    val itemPadding by
        animateDpAsState(
            targetValue = if (selected) ActiveSlotPadding else InactiveSlotPadding,
            animationSpec = tween(SpatialMillis, easing = ExpressiveEasing),
            label = "nav-item-padding-${tab.name}",
        )
    val label = stringResource(tab.labelRes)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier =
            Modifier
                .height(48.dp)
                .clip(CircleShape)
                .selectable(
                    selected = selected,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    role = Role.Tab,
                )
                .drawBehind {
                    if (pressed && !selected) {
                        drawCircle(
                            color = content.copy(alpha = 0.08f),
                            radius = 18.dp.toPx(),
                        )
                    }
                }
                .padding(horizontal = itemPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(container)
                    .background(
                        if (pressed && selected) {
                            content.copy(alpha = 0.08f)
                        } else {
                            Color.Transparent
                        },
                    )
                    .padding(horizontal = if (selected) ActivePillPadding else 0.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(tab.iconRes),
                contentDescription = if (selected) null else label,
                modifier = Modifier.size(NavIconSize),
                tint = content,
            )
            AnimatedVisibility(
                visible = selected,
                enter =
                    fadeIn(tween(180, easing = FastOutSlowInEasing)) +
                        expandHorizontally(
                            animationSpec = tween(SpatialMillis, easing = ExpressiveEasing),
                        ),
                exit =
                    fadeOut(tween(120, easing = FastOutSlowInEasing)) +
                        shrinkHorizontally(
                            animationSpec = tween(CollapseMillis, easing = FastOutSlowInEasing),
                        ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(IconLabelGap))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
