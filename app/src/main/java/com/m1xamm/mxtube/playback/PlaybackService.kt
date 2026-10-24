package com.m1xamm.mxtube.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.m1xamm.mxtube.MainActivity
import com.m1xamm.mxtube.R

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.playback_channel_name)
                .build()
                .apply { setSmallIcon(R.drawable.ic_stat_mxtube) },
        )

        val player = PlaybackHolder.player(this)
        val sessionActivity =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        mediaSession =
            MediaSession.Builder(this, player)
                .setSessionActivity(sessionActivity)
                .build()
                .also { addSession(it) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!PlaybackHolder.player(this).playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        PlaybackHolder.stop()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "mx_playback"

        fun start(context: Context) {
            context.startService(Intent(context, PlaybackService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlaybackService::class.java))
        }
    }
}
