package com.example.routing

import com.example.platform.PlatformDescriptor
import com.example.platform.PlatformRegistry
import java.net.URI

data class PlatformMatch(
    val platformId: String,
    val platformName: String,
    val intent: String,
    val isYtDlpEligible: Boolean,
    val descriptor: PlatformDescriptor
)

object PlatformClassifier {

    fun classify(url: String): PlatformMatch {
        val descriptor = PlatformRegistry.findDescriptor(url)

        val path: String
        val query: String?
        try {
            val uri = URI(url)
            path = uri.path ?: ""
            query = uri.query
        } catch (e: Exception) {
            return PlatformMatch(
                platformId = descriptor.id,
                platformName = descriptor.name,
                intent = descriptor.defaultIntent,
                isYtDlpEligible = descriptor.allowYtDlp,
                descriptor = descriptor
            )
        }

        val intent = descriptor.classifyIntent(path, query)

        return PlatformMatch(
            platformId = descriptor.id,
            platformName = descriptor.name,
            intent = intent,
            isYtDlpEligible = descriptor.allowYtDlp,
            descriptor = descriptor
        )
    }
}
