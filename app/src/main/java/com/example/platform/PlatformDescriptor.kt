package com.example.platform

data class PlatformDescriptor(
    val id: String,
    val name: String,
    val hostMatcher: (host: String) -> Boolean,
    val defaultIntent: String = "video",
    val pathClassifier: (path: String, query: String?) -> String = { _, _ -> defaultIntent },
    val allowYtDlp: Boolean = true,
    val allowGenericImageFallback: Boolean = false,
    val strategyOrder: List<String> = listOf("YTDLP", "EMBEDDED", "GENERIC_PAGE")
) {
    fun matchesHost(host: String): Boolean {
        val cleanHost = host.lowercase().trim()
        return hostMatcher(cleanHost)
    }

    fun classifyIntent(path: String, query: String?): String {
        return pathClassifier(path, query)
    }
}
