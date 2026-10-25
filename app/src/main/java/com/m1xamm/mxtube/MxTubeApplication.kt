package com.m1xamm.mxtube

import android.app.Application
import com.m1xamm.mxtube.data.local.HomeFeedCache
import com.m1xamm.mxtube.data.local.LocalHistoryStore
import com.m1xamm.mxtube.data.youtube.OkHttpDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class MxTubeApplication : Application() {
    val historyStore: LocalHistoryStore by lazy { LocalHistoryStore(this) }
    val homeFeedCache: HomeFeedCache by lazy { HomeFeedCache(this) }

    override fun onCreate() {
        super.onCreate()

        NewPipe.init(
            OkHttpDownloader(),
            Localization("ru", "RU"),
            ContentCountry("RU"),
        )
    }
}
