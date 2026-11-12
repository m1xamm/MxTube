package com.m1xamm.mxtube.notifications

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.m1xamm.mxtube.MainActivity
import com.m1xamm.mxtube.data.youtube.ChannelIdentity
import com.m1xamm.mxtube.data.youtube.VideoSummary
import java.net.URI

object SubscriptionNotificationRoute {
    const val ACTION_OPEN_VIDEO = "com.m1xamm.mxtube.notifications.OPEN_UPLOAD"
    const val EXTRA_VIDEO_URL = "com.m1xamm.mxtube.notifications.VIDEO_URL"
    const val EXTRA_TITLE = "com.m1xamm.mxtube.notifications.TITLE"
    const val EXTRA_UPLOADER = "com.m1xamm.mxtube.notifications.UPLOADER"
    const val EXTRA_THUMBNAIL_URL = "com.m1xamm.mxtube.notifications.THUMBNAIL_URL"
    const val EXTRA_UPLOADED_AT = "com.m1xamm.mxtube.notifications.UPLOADED_AT"
    const val EXTRA_UPLOADER_URL = "com.m1xamm.mxtube.notifications.UPLOADER_URL"

    fun createIntent(context: Context, video: VideoSummary): Intent {
        val id = requireNotNull(UploadVideoIdentity.fromUrl(video.url)) { "Not a canonical YouTube watch URL" }
        return Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_VIDEO)
            .setData(Uri.parse(UploadVideoIdentity.url(id)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_VIDEO_URL, UploadVideoIdentity.url(id))
            .putExtra(EXTRA_TITLE, video.title.take(512))
            .putExtra(EXTRA_UPLOADER, video.uploader.take(256))
            .putExtra(EXTRA_THUMBNAIL_URL, safeThumbnail(video.thumbnailUrl))
            .putExtra(EXTRA_UPLOADER_URL, ChannelIdentity.from(video.uploaderUrl)?.url)
            .apply { video.uploadedAtEpochMillis?.let { putExtra(EXTRA_UPLOADED_AT, it) } }
    }

    fun videoFrom(intent: Intent?): VideoSummary? {
        if (intent?.action != ACTION_OPEN_VIDEO) return null
        return runCatching {
            val raw = intent.dataString ?: intent.getStringExtra(EXTRA_VIDEO_URL)
            val id = UploadVideoIdentity.fromUrl(raw) ?: return null
            val extraUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
            if (extraUrl != null && extraUrl != UploadVideoIdentity.url(id)) return null
            VideoSummary(
                url = UploadVideoIdentity.url(id),
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty().take(512),
                uploader = intent.getStringExtra(EXTRA_UPLOADER).orEmpty().take(256),
                durationSeconds = -1L,
                viewCount = -1L,
                uploadedAtEpochMillis = intent.getLongExtra(EXTRA_UPLOADED_AT, 0L).takeIf { it > 0L },
                thumbnailUrl = safeThumbnail(intent.getStringExtra(EXTRA_THUMBNAIL_URL)),
                uploaderUrl = ChannelIdentity.from(intent.getStringExtra(EXTRA_UPLOADER_URL))?.url,
            )
        }.getOrNull()
    }

    private fun safeThumbnail(raw: String?): String? = raw?.takeIf { it.length <= 2048 }?.let {
        runCatching {
            val uri = URI(it)
            it.takeIf {
                uri.scheme == "https" && uri.userInfo == null && uri.port == -1 &&
                    uri.host in setOf("i.ytimg.com", "img.youtube.com")
            }
        }.getOrNull()
    }
}
