package com.m1xamm.mxtube.notifications

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.m1xamm.mxtube.MxTubeApplication
import com.m1xamm.mxtube.data.local.SubscriptionEntry
import com.m1xamm.mxtube.data.local.SubscriptionNotificationMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.TimeUnit

object SubscriptionNotificationScheduler {
    const val PERIODIC_WORK_NAME = "mx_subscription_uploads_periodic"
    const val CHECK_WORK_NAME = "mx_subscription_uploads_check"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun update(context: Context, channels: List<SubscriptionEntry>) {
        val appContext = context.applicationContext
        schedule(appContext, channels, forceImmediate = false)
        scope.launch {
            try {
                val app = appContext as MxTubeApplication
                SubscriptionNotificationState.mutex.withLock {
                    val current = app.subscriptionsStore.snapshot().channels
                    val states = SubscriptionNotificationState.read(app.filesDir)
                    val previous = states.toMap()
                    SubscriptionNotificationState.prune(states, current)
                    if (states != previous) SubscriptionNotificationState.write(app.filesDir, states)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                Log.w("SubscriptionUploads", "Cannot prune upload state", error)
            }
        }
    }

    fun requestCheck(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val app = appContext as MxTubeApplication
            app.subscriptionsStore.snapshot()
            synchronized(SubscriptionNotificationRuntime.guard) {
                schedule(appContext, app.subscriptionsStore.state.value.channels, forceImmediate = true)
            }
        }
    }

    private fun schedule(context: Context, channels: List<SubscriptionEntry>, forceImmediate: Boolean) {
        synchronized(SubscriptionNotificationRuntime.guard) {
            val newlyEnabled = SubscriptionNotificationRuntime.requests.update(channels)
            SubscriptionUploadNotifications.cancelDisabled(context, channels)
            val manager = WorkManager.getInstance(context)
            if (channels.none { it.notificationMode != SubscriptionNotificationMode.None }) {
                manager.cancelUniqueWork(PERIODIC_WORK_NAME)
                manager.cancelUniqueWork(CHECK_WORK_NAME)
                return
            }
            SubscriptionUploadNotifications.createChannel(context)
            manager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SubscriptionNotificationWorker>(15, TimeUnit.MINUTES)
                    .setInitialDelay(15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build(),
            )
            if (newlyEnabled || forceImmediate) {
                manager.enqueueUniqueWork(
                    CHECK_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<SubscriptionNotificationWorker>()
                        .setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                        .build(),
                )
            }
        }
    }
}
