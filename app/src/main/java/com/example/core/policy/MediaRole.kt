package com.example.core.policy

enum class MediaRole {
    PRIMARY_VIDEO,
    PRIMARY_AUDIO,
    DIRECT_MEDIA,
    THUMBNAIL,
    POSTER,
    PREVIEW,
    UNKNOWN;

    val isPrimary: Boolean
        get() = this == PRIMARY_VIDEO || this == PRIMARY_AUDIO || this == DIRECT_MEDIA

    val isAuxiliary: Boolean
        get() = this == THUMBNAIL || this == POSTER || this == PREVIEW
}
