@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.mixam.mxtube.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mixam.mxtube.R
import com.mixam.mxtube.data.local.SubscriptionNotificationMode

@Composable
fun SubscribePill(
    subscribed: Boolean,
    pending: Boolean,
    notificationMode: SubscriptionNotificationMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fullWidth: Boolean = false,
    compact: Boolean = false,
    channelName: String? = null,
) {
    val container by
        animateColorAsState(
            targetValue =
                if (subscribed) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "subscribe-container",
        )
    val content by
        animateColorAsState(
            targetValue =
                if (subscribed) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "subscribe-content",
        )
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val horizontalPadding by
        animateDpAsState(
            targetValue = if (fullWidth) 20.dp else 14.dp,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "subscribe-padding",
        )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val modeLabel = subscriptionNotificationLabel(notificationMode)
    val manageLabel =
        channelName?.let { stringResource(R.string.subscription_manage, it) }
            ?: stringResource(R.string.subscribed)
    Surface(
        onClick = onClick,
        enabled = !pending,
        interactionSource = interactionSource,
        modifier =
            modifier
                .graphicsLayer {
                    val scale = if (pressed) 0.97f else 1f
                    scaleX = scale
                    scaleY = scale
                }
                .then(
                    if (subscribed) {
                        Modifier.semantics {
                            contentDescription = manageLabel
                            stateDescription = modeLabel
                        }
                    } else {
                        Modifier
                    },
                ),
        shape = CircleShape,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier =
                Modifier
                    .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
                    .height(40.dp)
                    .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = subscribed,
                enter = expandHorizontally(spatial, expandFrom = Alignment.Start) + fadeIn(),
                exit = shrinkHorizontally(spatial, shrinkTowards = Alignment.Start) + fadeOut(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(subscriptionNotificationIcon(notificationMode)),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = content,
                    )
                    Spacer(Modifier.width(6.dp))
                }
            }
            AnimatedVisibility(
                visible = !(compact && subscribed),
                enter = expandHorizontally(spatial, expandFrom = Alignment.Start) + fadeIn(),
                exit = shrinkHorizontally(spatial, shrinkTowards = Alignment.Start) + fadeOut(),
            ) {
                AnimatedContent(
                    targetState = subscribed && !compact,
                    transitionSpec = {
                        (fadeIn() togetherWith fadeOut())
                            .using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> spatial }))
                    },
                    label = "subscribe-label",
                ) { isSubscribed ->
                    Text(
                        text =
                            stringResource(
                                if (isSubscribed) R.string.subscribed else R.string.subscribe,
                            ),
                        style = MaterialTheme.typography.labelLarge,
                        color = content,
                        maxLines = 1,
                    )
                }
            }
            AnimatedVisibility(
                visible = subscribed,
                enter = expandHorizontally(spatial, expandFrom = Alignment.Start) + fadeIn(),
                exit = shrinkHorizontally(spatial, shrinkTowards = Alignment.Start) + fadeOut(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_expand_more_rounded),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = content,
                    )
                }
            }
        }
    }
}
