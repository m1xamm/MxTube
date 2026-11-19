package com.mixam.mxtube.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mixam.mxtube.R
import com.mixam.mxtube.data.local.SubscriptionNotificationMode
import com.mixam.mxtube.ui.common.GlassBottomSheet
import com.mixam.mxtube.ui.common.clusterCardColor
import dev.chrisbanes.haze.HazeState

@Composable
internal fun SubscriptionMenuSheet(
    mode: SubscriptionNotificationMode,
    hazeState: HazeState,
    notificationsEnabled: Boolean,
    onEnableNotifications: () -> Unit,
    onModeSelected: (SubscriptionNotificationMode) -> Unit,
    onUnsubscribe: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.subscription_notifications_title)
    GlassBottomSheet(hazeState = hazeState, title = title, onDismiss = onDismiss) { close ->
        Column(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.padding(start = 16.dp, bottom = 16.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SubscriptionNotificationMode.entries.forEachIndexed { index, option ->
                    val selected = mode == option
                    Surface(
                        onClick = {
                            onModeSelected(option)
                            close()
                        },
                        modifier =
                            Modifier.fillMaxWidth().semantics {
                                role = Role.RadioButton
                                this.selected = selected
                            },
                        shape =
                            RoundedCornerShape(
                                topStart = if (index == 0) 24.dp else 4.dp,
                                topEnd = if (index == 0) 24.dp else 4.dp,
                                bottomStart = if (index == SubscriptionNotificationMode.entries.lastIndex) 24.dp else 4.dp,
                                bottomEnd = if (index == SubscriptionNotificationMode.entries.lastIndex) 24.dp else 4.dp,
                            ),
                        color =
                            (if (selected) MaterialTheme.colorScheme.secondaryContainer else clusterCardColor())
                                .copy(alpha = 0.60f),
                        contentColor =
                            if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    ) {
                        Row(
                            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Icon(
                                painter = painterResource(subscriptionNotificationIcon(option)),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                text = subscriptionNotificationLabel(option),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (selected) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check_rounded),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                onClick = {
                    onUnsubscribe()
                    close()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = clusterCardColor().copy(alpha = 0.60f),
            ) {
                Row(
                    modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_person_remove_rounded),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(stringResource(R.string.unsubscribe_confirm), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (!notificationsEnabled) {
                Text(
                    text = stringResource(R.string.subscription_notifications_blocked),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onEnableNotifications) {
                    Text(stringResource(R.string.subscription_notifications_settings))
                }
            }
        }
    }
}

internal fun subscriptionNotificationIcon(mode: SubscriptionNotificationMode): Int =
    when (mode) {
        SubscriptionNotificationMode.All -> R.drawable.ic_notifications_active_rounded
        SubscriptionNotificationMode.Personalized -> R.drawable.ic_notifications_rounded
        SubscriptionNotificationMode.None -> R.drawable.ic_notifications_off_rounded
    }

@Composable
internal fun subscriptionNotificationLabel(mode: SubscriptionNotificationMode): String =
    stringResource(
        when (mode) {
            SubscriptionNotificationMode.All -> R.string.subscription_notifications_all
            SubscriptionNotificationMode.Personalized -> R.string.subscription_notifications_personalized
            SubscriptionNotificationMode.None -> R.string.subscription_notifications_none
        },
    )
