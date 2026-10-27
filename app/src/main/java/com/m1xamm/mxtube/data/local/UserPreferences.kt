package com.m1xamm.mxtube.data.local

import android.content.Context
import android.content.SharedPreferences

object UserPreferences {
    private const val NAME = "mx_user_prefs"
    private const val KEY_SPEED = "playback_speed"
    private const val KEY_QUALITY = "quality_label"
    private const val KEY_AUDIO = "audio_label"
    private const val KEY_SUBTITLE = "subtitle_label"
    private const val KEY_HOME_TOPIC = "home_topic_id"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    }

    var playbackSpeed: Float
        get() = prefs.getFloat(KEY_SPEED, 1f).coerceIn(0.25f, 5f)
        set(value) {
            prefs.edit().putFloat(KEY_SPEED, value.coerceIn(0.25f, 5f)).apply()
        }

    var qualityLabel: String?
        get() = prefs.getString(KEY_QUALITY, null)
        set(value) {
            prefs.edit().putString(KEY_QUALITY, value).apply()
        }

    var audioLabel: String?
        get() = prefs.getString(KEY_AUDIO, null)
        set(value) {
            prefs.edit().putString(KEY_AUDIO, value).apply()
        }

    var subtitleLabel: String?
        get() = prefs.getString(KEY_SUBTITLE, null)
        set(value) {
            prefs.edit().putString(KEY_SUBTITLE, value).apply()
        }

    var homeTopicId: String?
        get() = prefs.getString(KEY_HOME_TOPIC, null)
        set(value) {
            prefs.edit().putString(KEY_HOME_TOPIC, value).apply()
        }
}
