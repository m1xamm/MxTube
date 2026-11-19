package com.mixam.mxtube

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mixam.mxtube.data.youtube.VideoSummary
import com.mixam.mxtube.notifications.SubscriptionNotificationRoute
import com.mixam.mxtube.theme.MxTubeTheme

class MainActivity : ComponentActivity() {
    private var notificationVideo by mutableStateOf<VideoSummary?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            notificationVideo = SubscriptionNotificationRoute.videoFrom(intent)
        }

        enableEdgeToEdge()
        setContent {
            MxTubeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MxTubeApp(
                        notificationVideo = notificationVideo,
                        onNotificationConsumed = {
                            notificationVideo = null
                            intent.action = null
                            intent.data = null
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationVideo = SubscriptionNotificationRoute.videoFrom(intent)
    }
}
