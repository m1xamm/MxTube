package com.m1xamm.mxtube.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer

object PlaybackHolder {
    private var player: ExoPlayer? = null

    fun player(context: Context): ExoPlayer {
        player?.let { return it }

        val audioAttributes =
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()

        return ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { player = it }
    }

    fun stop() {
        player?.let {
            it.pause()
            it.stop()
            it.clearMediaItems()
        }
    }
}
