package com.mixam.mxtube.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mixam.mxtube.R
import com.mixam.mxtube.data.local.SubscriptionEntry
import com.mixam.mxtube.data.local.SubscriptionNotificationMode
import com.mixam.mxtube.data.youtube.VideoSummary
import java.security.MessageDigest

internal object SubscriptionUploadNotifications {
    const val CHANNEL_ID = "subscription_uploads"
    private const val TAG_PREFIX = "mx-upload:"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.subscriptions_updates_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.subscriptions_updates_description)
        })
    }

    fun deliveryAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 26 ||
            manager.getNotificationChannel(CHANNEL_ID)?.importance?.let { it != NotificationManager.IMPORTANCE_NONE } == true
    }

    fun post(context: Context, channel: SubscriptionEntry, video: VideoSummary): Boolean {
        if (!deliveryAllowed(context)) return false
        val videoId = UploadVideoIdentity.fromUrl(video.url) ?: return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val contentIntent = PendingIntent.getActivity(
            context,
            stableId(videoId),
            SubscriptionNotificationRoute.createIntent(context, video),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mxtube)
            .setContentTitle(video.title.ifBlank { channel.name }.take(512))
            .setContentText(video.uploader.ifBlank { channel.name }.take(256))
            .setStyle(NotificationCompat.BigTextStyle().bigText(video.title.take(512)))
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setWhen(video.uploadedAtEpochMillis ?: System.currentTimeMillis())
            .build()
        return try {
            manager.notify(tag(channel.key, videoId), stableId(videoId), notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun cancelDisabled(context: Context, channels: List<SubscriptionEntry>) {
        val enabled = channels.filter { it.notificationMode != SubscriptionNotificationMode.None }
            .mapTo(hashSetOf()) { channelPrefix(it.key) }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.activeNotifications.forEach { active ->
            val tag = active.tag ?: return@forEach
            if (tag.startsWith(TAG_PREFIX) && enabled.none { tag.startsWith(it) }) {
                manager.cancel(tag, active.id)
            }
        }
    }

    fun cancelChannel(context: Context, key: String) {
        val prefix = channelPrefix(key)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.activeNotifications.forEach { active ->
            if (active.tag?.startsWith(prefix) == true) manager.cancel(active.tag, active.id)
        }
    }

    private fun tag(key: String, videoId: String): String = "${channelPrefix(key)}$videoId"
    private fun channelPrefix(key: String): String = "$TAG_PREFIX${digest(key)}:"
    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun stableId(value: String): Int = value.hashCode() and Int.MAX_VALUE
}
