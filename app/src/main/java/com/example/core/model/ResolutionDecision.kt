package com.example.core.model

data class ResolutionDecision(
    val primaryCandidate: MediaCandidate?,
    val thumbnailCandidate: MediaCandidate?,
    val rejectedCandidates: List<MediaCandidate> = emptyList(),
    val reason: String = ""
) {
    val isResolved: Boolean
        get() = primaryCandidate != null
}
