package com.example.core.model

sealed class MetadataValue<out T> {
    data class Known<T>(val value: T, val source: String = "provider") : MetadataValue<T>()
    data class Unknown(val reason: String = "Not provided") : MetadataValue<Nothing>()
    object NotApplicable : MetadataValue<Nothing>()

    fun getOrNull(): T? = when (this) {
        is Known -> value
        is Unknown, is NotApplicable -> null
    }

    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Known -> value
        is Unknown, is NotApplicable -> default
    }
}
