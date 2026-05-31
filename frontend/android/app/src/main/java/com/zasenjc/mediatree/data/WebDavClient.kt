package com.zasenjc.mediatree.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.StringReader
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

data class WebDavEntry(
    val sourceId: String,
    val name: String,
    val path: String,
    val href: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val contentType: String = "",
    val modified: String = "",
) {
    val isPlayableVideo: Boolean
        get() = !isDirectory && (contentType.startsWith("video/") || name.substringAfterLast('.', "").lowercase() in VideoExtensions)
}

class WebDavClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun list(source: ClientStorageSource, path: String = ""): List<WebDavEntry> = withContext(Dispatchers.IO) {
        require(source.type == ClientStorageType.WebDAV) { "只支持 WebDAV 存储源" }
        val request = Request.Builder()
            .url(buildResourceUrl(source, path))
            .method("PROPFIND", PropfindBody)
            .header("Depth", "1")
            .header("Content-Type", "application/xml; charset=utf-8")
            .apply {
                authorizationHeaders(source).forEach { (name, value) -> header(name, value) }
            }
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(response.code, body.ifBlank { response.message })
            parseDirectoryListing(source, path, body)
        }
    }

    companion object {
        fun authorizationHeaders(source: ClientStorageSource): Map<String, String> {
            if (source.secret.isBlank()) return emptyMap()
            return when (source.authType) {
                ClientStorageAuthType.Basic -> {
                    val credentials = "${source.username}:${source.secret}"
                    val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
                    mapOf("Authorization" to "Basic $encoded")
                }
                ClientStorageAuthType.Bearer -> mapOf("Authorization" to "Bearer ${source.secret}")
            }
        }

        fun buildResourceUrl(source: ClientStorageSource, path: String): String {
            val base = source.endpoint.trimEnd('/')
            val encodedPath = path
                .split("/")
                .filter { it.isNotBlank() }
                .joinToString("/") { segment -> URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20") }
            return if (encodedPath.isBlank()) base else "$base/$encodedPath"
        }

        fun parseDirectoryListing(
            source: ClientStorageSource,
            currentPath: String,
            xml: String,
        ): List<WebDavEntry> {
            val document = DocumentBuilderFactory.newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(InputSource(StringReader(xml)))
            val responses = document.getElementsByTagNameNS("*", "response")
            val currentUrl = buildResourceUrl(source, currentPath).trimEnd('/')
            return (0 until responses.length).mapNotNull { index ->
                val response = responses.item(index) as? Element ?: return@mapNotNull null
                val href = response.firstText("href").ifBlank { return@mapNotNull null }
                if (sameWebDavResource(href, currentUrl)) return@mapNotNull null
                val hrefPath = childPathFromHref(source, href)
                val decodedName = response.firstText("displayname")
                    .ifBlank { hrefPath.substringAfterLast('/') }
                    .trimEnd('/')
                if (decodedName.isBlank() || hrefPath.isBlank()) return@mapNotNull null
                val isDirectory = response.getElementsByTagNameNS("*", "collection").length > 0 || href.endsWith("/")
                WebDavEntry(
                    sourceId = source.id,
                    name = decodedName,
                    path = hrefPath,
                    href = href,
                    isDirectory = isDirectory,
                    sizeBytes = response.firstText("getcontentlength").toLongOrNull() ?: 0L,
                    contentType = response.firstText("getcontenttype"),
                    modified = response.firstText("getlastmodified"),
                )
            }.sortedWith(compareBy<WebDavEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }
}

private val PropfindBody = """
    <?xml version="1.0" encoding="utf-8" ?>
    <d:propfind xmlns:d="DAV:">
      <d:prop>
        <d:displayname/>
        <d:resourcetype/>
        <d:getcontentlength/>
        <d:getcontenttype/>
        <d:getlastmodified/>
      </d:prop>
    </d:propfind>
""".trimIndent().toRequestBody("application/xml; charset=utf-8".toMediaType())

private val VideoExtensions = setOf("mp4", "m4v", "mkv", "mov", "avi", "wmv", "flv", "webm", "ts", "m2ts")

private fun Element.firstText(localName: String): String {
    val nodes = getElementsByTagNameNS("*", localName)
    return nodes.item(0)?.textContent?.trim().orEmpty()
}

private fun childPathFromHref(source: ClientStorageSource, href: String): String {
    val rawHrefPath = runCatching { URI(href).rawPath }.getOrNull() ?: href
    val rawBasePath = runCatching { URI(source.endpoint).rawPath }.getOrNull().orEmpty()
    val relative = rawHrefPath.trimEnd('/')
        .removePrefix(rawBasePath.trimEnd('/'))
        .trim('/')
    return URLDecoder.decode(relative, Charsets.UTF_8.name())
}

private fun sameWebDavResource(href: String, currentUrl: String): Boolean {
    val hrefPath = runCatching { URI(href).path }.getOrDefault(href).trimEnd('/')
    val currentPath = runCatching { URI(currentUrl).path }.getOrDefault(currentUrl).trimEnd('/')
    return hrefPath == currentPath || href.trimEnd('/') == currentUrl
}
