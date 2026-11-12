package com.m1xamm.mxtube.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.m1xamm.mxtube.MxTubeApplication
import com.m1xamm.mxtube.data.local.SubscriptionEntry
import com.m1xamm.mxtube.data.local.SubscriptionNotificationMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class SubscriptionNotificationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as MxTubeApplication
        try {
            val finished = withTimeoutOrNull(TOTAL_BUDGET_MILLIS) { poll(app) }
            if (finished != true && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            Log.w(TAG, "Upload notification state unavailable", error)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        }
    }

    private suspend fun poll(app: MxTubeApplication): Boolean {
        app.subscriptionsStore.snapshot()
        app.historyStore.snapshot()
        val channels = synchronized(SubscriptionNotificationRuntime.guard) {
            val current = app.subscriptionsStore.state.value.channels
            SubscriptionNotificationRuntime.requests.update(current)
            SubscriptionUploadNotifications.cancelDisabled(app, current)
            SubscriptionUploadNotifications.createChannel(app)
            current.filter { it.notificationMode != SubscriptionNotificationMode.None }
        }
        if (channels.isEmpty()) return true
        val states = SubscriptionNotificationState.mutex.withLock {
            val current = app.subscriptionsStore.snapshot().channels
            SubscriptionNotificationState.read(app.filesDir).also {
                SubscriptionNotificationState.prune(it, current)
                currentCoroutineContext().ensureActive()
                SubscriptionNotificationState.write(app.filesDir, it)
            }
        }
        val selected = channels.sortedWith(
            compareBy<SubscriptionEntry> { if (runAttemptCount > 0 && states[it.key]?.retryPending == true) 0 else 1 }
                .thenBy { states[it.key]?.lastCheckedAtEpochMillis ?: 0L }.thenBy { it.key },
        ).take(MAX_CHANNELS_PER_RUN)
        val transientFailure = AtomicBoolean(false)
        coroutineScope {
            selected.forEach { channel ->
                launch {
                    SubscriptionNotificationRuntime.feedSemaphore.withPermit {
                        currentCoroutineContext().ensureActive()
                        val generation = synchronized(SubscriptionNotificationRuntime.guard) {
                            val current = app.subscriptionsStore.state.value.channels.firstOrNull { it.key == channel.key }
                            SubscriptionNotificationRuntime.requests.capture(channel)?.takeIf {
                                SubscriptionNotificationRuntime.requests.isCurrent(channel, it, current)
                            }
                        } ?: return@withPermit
                        try {
                            val previous = states[channel.key]?.takeIf {
                                it.addedAtEpochMillis == channel.addedAtEpochMillis && it.channelUrl == channel.url
                            }
                            val feed = withTimeoutOrNull(CHANNEL_BUDGET_MILLIS) {
                                SubscriptionUploadFeedSource.fetch(channel.url, previous?.resolvedChannelUrl)
                            }
                            if (feed == null) {
                                transientFailure.set(true)
                                recordFailure(app, channel, generation, transient = true)
                            } else {
                                deliver(app, channel, generation, feed)
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            val transient = isTransient(error)
                            if (transient) transientFailure.set(true)
                            Log.w(TAG, "Upload check failed for ${channel.key}", error)
                            try {
                                recordFailure(app, channel, generation, transient)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (storageError: IOException) {
                                transientFailure.set(true)
                                Log.w(TAG, "Cannot save upload check progress", storageError)
                            }
                        }
                    }
                }
            }
        }
        return !transientFailure.get()
    }

    private suspend fun deliver(
        app: MxTubeApplication,
        channel: SubscriptionEntry,
        generation: Long,
        feed: SubscriptionUploadFeed,
    ) {
        SubscriptionNotificationState.mutex.withLock {
            app.subscriptionsStore.snapshot()
            app.historyStore.snapshot()
            val states = SubscriptionNotificationState.read(app.filesDir)
            SubscriptionNotificationState.prune(states, app.subscriptionsStore.state.value.channels)
            val previous = states[channel.key]
            val now = System.currentTimeMillis()
            val coroutineContext = currentCoroutineContext()
            val next = synchronized(SubscriptionNotificationRuntime.guard) {
                val current = app.subscriptionsStore.state.value.channels.firstOrNull { it.key == channel.key }
                if (!SubscriptionNotificationRuntime.requests.isCurrent(channel, generation, current)) return@synchronized null
                val plan = SubscriptionNotificationPolicy.plan(
                    channel, current, previous, feed.videos, app.historyStore.state.value.watches, now,
                    SubscriptionUploadNotifications.deliveryAllowed(app), feed.channelUrl,
                )
                if (!plan.accepted) return@synchronized null
                val delivered = hashSetOf<String>()
                plan.videos.forEach { video ->
                    coroutineContext.ensureActive()
                    val live = app.subscriptionsStore.state.value.channels.firstOrNull { it.key == channel.key }
                    if (!SubscriptionNotificationRuntime.requests.isCurrent(channel, generation, live)) {
                        SubscriptionUploadNotifications.cancelChannel(app, channel.key)
                        return@synchronized null
                    }
                    val livePlan = SubscriptionNotificationPolicy.plan(
                        channel, live, previous, feed.videos, app.historyStore.state.value.watches, now,
                        SubscriptionUploadNotifications.deliveryAllowed(app), feed.channelUrl,
                    )
                    if (livePlan.videos.any { it.url == video.url } && SubscriptionUploadNotifications.post(app, channel, video)) {
                        UploadVideoIdentity.fromUrl(video.url)?.let(delivered::add)
                    }
                    val after = app.subscriptionsStore.state.value.channels.firstOrNull { it.key == channel.key }
                    if (!SubscriptionNotificationRuntime.requests.isCurrent(channel, generation, after)) {
                        SubscriptionUploadNotifications.cancelChannel(app, channel.key)
                        return@synchronized null
                    }
                }
                SubscriptionNotificationPolicy.acknowledge(plan, channel.notificationMode, delivered, now)
            } ?: return@withLock
            coroutineContext.ensureActive()
            app.subscriptionsStore.snapshot()
            synchronized(SubscriptionNotificationRuntime.guard) {
                val latest = app.subscriptionsStore.state.value.channels
                val live = latest.firstOrNull { it.key == channel.key }
                if (!SubscriptionNotificationRuntime.requests.isCurrent(channel, generation, live)) {
                    SubscriptionUploadNotifications.cancelChannel(app, channel.key)
                    return@withLock
                }
                states[channel.key] = next
                SubscriptionNotificationState.prune(states, latest)
                SubscriptionNotificationState.write(app.filesDir, states)
            }
        }
    }

    private suspend fun recordFailure(app: MxTubeApplication, channel: SubscriptionEntry, generation: Long, transient: Boolean) {
        SubscriptionNotificationState.mutex.withLock {
            app.subscriptionsStore.snapshot()
            val states = SubscriptionNotificationState.read(app.filesDir)
            val coroutineContext = currentCoroutineContext()
            synchronized(SubscriptionNotificationRuntime.guard) {
                val current = app.subscriptionsStore.state.value.channels
                SubscriptionNotificationState.prune(states, current)
                if (!SubscriptionNotificationRuntime.requests.isCurrent(channel, generation, current.firstOrNull { it.key == channel.key })) {
                    return@withLock
                }
                val previous = states[channel.key] ?: ChannelUploadNotificationState(channel.addedAtEpochMillis, channel.url)
                states[channel.key] = previous.copy(lastCheckedAtEpochMillis = System.currentTimeMillis(), retryPending = transient)
                coroutineContext.ensureActive()
                SubscriptionNotificationState.write(app.filesDir, states)
            }
        }
    }

    private fun isTransient(error: Throwable): Boolean = when (error) {
        is UploadFeedHttpException -> error.status == 429 || error.status in 500..599
        is IOException -> true
        else -> error.cause?.takeIf { it !== error }?.let(::isTransient) ?: false
    }

    private companion object {
        const val TAG = "SubscriptionUploads"
        const val MAX_CHANNELS_PER_RUN = 48
        const val CHANNEL_BUDGET_MILLIS = 25_000L
        const val TOTAL_BUDGET_MILLIS = 180_000L
        const val MAX_RETRIES = 3
    }
}
