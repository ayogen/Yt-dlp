package com.example.extraction

import com.example.core.model.MediaCandidate

data class ExtractionEvidence(
    val candidates: List<MediaCandidate> = emptyList(),
    val warnings: List<String> = emptyList(),
    val failedStrategies: List<String> = emptyList(),
    val logs: List<String> = emptyList()
) {
    fun addCandidate(candidate: MediaCandidate): ExtractionEvidence {
        return copy(candidates = candidates + candidate)
    }

    fun addCandidates(newCandidates: List<MediaCandidate>): ExtractionEvidence {
        return copy(candidates = candidates + newCandidates)
    }

    fun addWarning(warning: String): ExtractionEvidence {
        return copy(warnings = warnings + warning)
    }

    fun addFailedStrategy(strategyName: String): ExtractionEvidence {
        return copy(failedStrategies = failedStrategies + strategyName)
    }
}
