package com.m1xamm.mxtube

import android.content.Context
import androidx.annotation.StringRes
import java.util.Locale

object AppLocale {
    lateinit var locale: Locale
        private set

    private lateinit var context: Context

    fun init(context: Context) {
        this.context = context.applicationContext
        locale = context.resources.configuration.locales[0]
    }

    fun string(@StringRes id: Int): String = context.getString(id)
}
