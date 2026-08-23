package com.example.platform

import java.net.URI

object PlatformRegistry {

    private val descriptors = mutableListOf<PlatformDescriptor>().apply {
        addAll(BuiltInPlatforms.ALL_PLATFORMS)
    }

    fun findDescriptor(url: String): PlatformDescriptor {
        val host = try {
            val uri = URI(url)
            uri.host ?: ""
        } catch (e: Exception) {
            ""
        }

        return descriptors.firstOrNull { it.matchesHost(host) } ?: BuiltInPlatforms.GENERIC
    }

    fun getAllDescriptors(): List<PlatformDescriptor> = descriptors.toList()
}
