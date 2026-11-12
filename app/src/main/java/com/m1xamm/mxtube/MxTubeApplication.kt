package com.m1xamm.mxtube

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.m1xamm.mxtube.data.home.HomeTopics
import com.m1xamm.mxtube.data.local.HomeFeedCache
import com.m1xamm.mxtube.data.local.LocalHistoryStore
import com.m1xamm.mxtube.data.local.SubscriptionsFeedCache
import com.m1xamm.mxtube.data.local.SubscriptionsStore
import com.m1xamm.mxtube.data.local.UserPreferences
import com.m1xamm.mxtube.data.network.Http
import com.m1xamm.mxtube.data.youtube.OkHttpDownloader
import com.m1xamm.mxtube.notifications.SubscriptionNotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale

class MxTubeApplication : Application(), SingletonImageLoader.Factory {
    val historyStore: LocalHistoryStore by lazy { LocalHistoryStore(this) }
    val homeFeedCache: HomeFeedCache by lazy { HomeFeedCache(this) }
    val subscriptionsStore: SubscriptionsStore by lazy { SubscriptionsStore(this) }
    val subscriptionsFeedCache: SubscriptionsFeedCache by lazy { SubscriptionsFeedCache(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        AppLocale.init(this)
        HomeTopics.init(this)
        UserPreferences.init(this)

        val locale = AppLocale.locale
        val countryCode =
            locale.country
                .ifBlank { COUNTRY_BY_LANGUAGE[locale.language] }
                .orEmpty()
                .ifBlank { Locale.getDefault().country }
                .ifBlank { "US" }
        NewPipe.init(
            OkHttpDownloader(),
            Localization(locale.language, countryCode),
            ContentCountry(countryCode),
        )
        scope.launch {
            subscriptionsStore.snapshot()
            subscriptionsStore.state
                .map { it.channels }
                .distinctUntilChangedBy { channels ->
                    channels.map { Triple(it.key, it.notificationMode, it.notificationsEnabledAtEpochMillis) }
                }
                .collect { channels ->
                    SubscriptionNotificationScheduler.update(this@MxTubeApplication, channels)
                }
        }
    }

    companion object {
        /**
         * A per-app language leaves the region empty, and YouTube rejects a channel request whose
         * language and region disagree, so an unmapped language must not inherit the device region.
         */
        private val COUNTRY_BY_LANGUAGE = mapOf("ru" to "RU", "uk" to "UA", "en" to "US")
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { Http.client },
                    ),
                )
            }
            .build()
}
