package com.example.core.policy

enum class ConfidenceTier(val score: Int) {
    VERIFIED(100),
    HIGH(80),
    MEDIUM(50),
    LOW(20),
    FALLBACK(10),
    UNKNOWN(0)
}

data class Confidence(
    val tier: ConfidenceTier = ConfidenceTier.HIGH,
    val score: Int = tier.score,
    val reason: String = ""
)
