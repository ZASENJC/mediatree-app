package com.zasenjc.mediatree.util

object UrlUtils {
    fun normalizeServerUrl(input: String): String {
        var value = input.trim()
        if (value.isEmpty()) return ""
        if (!value.startsWith("http://", ignoreCase = true) &&
            !value.startsWith("https://", ignoreCase = true)
        ) {
            value = "http://$value"
        }
        return value.trimEnd('/')
    }

    fun apiBase(serverUrl: String): String = "${normalizeServerUrl(serverUrl)}/api"

    fun resolveApiUrl(serverUrl: String, value: String?): String? {
        if (value.isNullOrBlank()) return null
        if (value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)
        ) return value
        val apiBase = apiBase(serverUrl)
        return if (value.startsWith("/api/")) {
            apiBase + value.removePrefix("/api")
        } else {
            "$apiBase/media/${value.split('/').joinToString("/") { encodePathSegment(it) }}"
        }
    }

    private fun encodePathSegment(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
