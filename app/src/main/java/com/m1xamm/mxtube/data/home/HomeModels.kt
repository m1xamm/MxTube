package com.m1xamm.mxtube.data.home

import com.m1xamm.mxtube.data.youtube.VideoSummary

enum class HomeTopicKind {
    Personalized,
    Kiosk,
    Search,
}

data class HomeTopic(
    val id: String,
    val label: String,
    val kind: HomeTopicKind,
    val kioskId: String? = null,
    val query: String? = null,
    val keywords: List<String> = emptyList(),
    val isDynamic: Boolean = false,
)

data class HomeFeedItem(
    val video: VideoSummary,
    val topics: Set<String> = emptySet(),
)
