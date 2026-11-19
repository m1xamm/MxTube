package com.mixam.mxtube.data.network

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
            .addInterceptor(VttCueSettingsInterceptor)
            .build()
    }
}

internal object VttCueSettingsInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!response.isSuccessful) return response
        val mediaType = response.body.contentType()
        if (!isWebVtt(request.url.toString(), mediaType)) return response
        val text = response.body.string()
        val cleaned = stripCueSettings(text)
        return response
            .newBuilder()
            .body(cleaned.toResponseBody(mediaType ?: "text/vtt; charset=utf-8".toMediaType()))
            .build()
    }

    private fun isWebVtt(url: String, mediaType: MediaType?): Boolean {
        val type = mediaType?.toString().orEmpty()
        if (type.contains("text/vtt", ignoreCase = true)) return true
        if (type.isNotBlank() && !type.startsWith("text/", ignoreCase = true)) return false
        val lowercase = url.lowercase()
        return lowercase.contains("timedtext") ||
            lowercase.contains("fmt=vtt") ||
            lowercase.contains(".vtt")
    }

    private fun stripCueSettings(vtt: String): String {
        if (!vtt.contains(" --> ")) return vtt
        var inStyle = false
        return vtt.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed == "STYLE" -> {
                    inStyle = true
                    line
                }
                inStyle -> {
                    if (trimmed.isEmpty()) inStyle = false
                    line
                }
                trimmed.contains(" --> ") -> {
                    val timing =
                        trimmed.substringBefore(" --> ") + " --> " +
                            trimmed.substringAfter(" --> ").substringBefore(" ")
                    line.substringBefore(line.trimStart()) + timing
                }
                else -> line
            }
        }
    }
}
