package com.m1xamm.mxtube

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.m1xamm.mxtube.data.home.HomeTopics
import com.m1xamm.mxtube.data.local.HomeFeedCache
import com.m1xamm.mxtube.data.local.LocalHistoryStore
import com.m1xamm.mxtube.data.local.UserPreferences
import com.m1xamm.mxtube.data.network.Http
import com.m1xamm.mxtube.data.youtube.OkHttpDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale

class MxTubeApplication : Application(), SingletonImageLoader.Factory {
    val historyStore: LocalHistoryStore by lazy { LocalHistoryStore(this) }
    val homeFeedCache: HomeFeedCache by lazy { HomeFeedCache(this) }

    override fun onCreate() {
        super.onCreate()

        AppLocale.init(this)
        HomeTopics.init(this)
        UserPreferences.init(this)

        val locale = AppLocale.locale
        val countryCode =
            locale.country
                .ifBlank { Locale.getDefault().country }
                .ifBlank { "US" }
        NewPipe.init(
            OkHttpDownloader(),
            Localization(locale.language, countryCode),
            ContentCountry(countryCode),
        )
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
