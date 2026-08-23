package com.example.extraction

import com.example.data.model.AppSettings
import com.example.routing.RoutingDecision

interface ExtractionStrategy {
    val name: String
    suspend fun canHandle(url: String, decision: RoutingDecision): Boolean
    suspend fun extract(url: String, decision: RoutingDecision, settings: AppSettings? = null): ExtractionEvidence
}
