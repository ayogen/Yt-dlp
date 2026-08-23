package com.example.extraction

import com.example.data.model.AppSettings
import com.example.routing.RoutingDecision

abstract class PlatformStrategy : ExtractionStrategy {
    abstract val supportedPlatformId: String

    override suspend fun canHandle(url: String, decision: RoutingDecision): Boolean {
        return decision.platform == supportedPlatformId
    }
}
