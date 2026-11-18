package com.m1xamm.mxtube.data.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object Http {
    const val BROWSER_AGENT =
        "Mozilla/5.0 (Linux; Android 15; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request()
                val next =
                    if (request.header("User-Agent") == null) {
                        request.newBuilder().header("User-Agent", BROWSER_AGENT).build()
                    } else {
                        request
                    }
                chain.proceed(next)
            }
            .build()
    }
}
