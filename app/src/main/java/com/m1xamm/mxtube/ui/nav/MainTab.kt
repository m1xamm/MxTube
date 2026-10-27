package com.m1xamm.mxtube.ui.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.m1xamm.mxtube.R

enum class MainTab(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    Home(R.string.nav_home, R.drawable.ic_nav_home),
    Shorts(R.string.nav_shorts, R.drawable.ic_nav_shorts),
    Subscriptions(R.string.nav_subscriptions, R.drawable.ic_nav_subscriptions),
    Settings(R.string.nav_settings, R.drawable.ic_nav_settings),
}
