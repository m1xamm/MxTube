package com.m1xamm.mxtube

import android.app.Application
import com.m1xamm.mxtube.data.youtube.OkHttpDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class MxTubeApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        NewPipe.init(
            OkHttpDownloader(),
            Localization("ru", "RU"),
            ContentCountry("RU"),
        )
    }
}
