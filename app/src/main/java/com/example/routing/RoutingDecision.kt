package com.example.routing

data class RoutingDecision(
    val platform: String,
    val intent: String,
    val allowYtDlp: Boolean,
    val allowGenericImageFallback: Boolean,
    val strategyOrder: List<String>,
    val reason: String
)
