package com.zasenjc.mediatree.data

import android.util.Log
import com.zasenjc.mediatree.playback.LocalProxyPlaybackSource
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.LinkedHashMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

private const val SmbRangeProxyLogTag = "SmbRangeProxy"
private const val SmbRangeCacheMaxBytes = 2 * 1024 * 1024
private const val SmbRangeCachedReadMaxBytes = 512 * 1024
private const val SmbRangeStreamBufferBytes = 64 * 1024

data class ByteRange(
    val start: Long,
    val endInclusive: Long,
) {
    val length: Long = endInclusive - start + 1
}

class SmbRangeProxy(
    private val smbClient: SmbClient = SmbClient(),
    private val port: Int = 0,
) {
    private data class ProxyRequest(
        val source: ClientStorageSource,
        val path: String,
    ) {
        val lease = ProxyRequestLease(source, path)
        val cache = RangeReadCache(maxBytes = SmbRangeCacheMaxBytes)

        fun close() {
            lease.close()
            cache.clear()
        }
    }

    private class ProxyRequestLease(
        private val source: ClientStorageSource,
        private val path: String,
    ) {
        private var file: SmbReadableFile? = null
        private var closed = false

        @Synchronized
        fun acquireFile(smbClient: SmbClient): SmbReadableFile {
            check(!closed) { "SMB proxy request is closed" }
            file?.let { return it }
            return smbClient.open(source, path).also { opened -> file = opened }
        }

        @Synchronized
        fun read(
            smbClient: SmbClient,
            offset: Long,
            buffer: ByteArray,
            bufferOffset: Int,
            length: Int,
        ): Int =
            acquireFile(smbClient).read(offset, buffer, bufferOffset, length)

        @Synchronized
        fun close() {
            closed = true
            file?.close()
            file = null
        }
    }

    private class RangeReadCache(private val maxBytes: Int) {
        private val entries = LinkedHashMap<ByteRange, ByteArray>(0, 0.75f, true)
        private var totalBytes = 0

        @Synchronized
        fun get(range: ByteRange): ByteArray? = entries[range]

        @Synchronized
        fun put(range: ByteRange, bytes: ByteArray) {
            if (bytes.size > maxBytes) return
            entries.remove(range)?.let { removed -> totalBytes -= removed.size }
            entries[range] = bytes
            totalBytes += bytes.size
            while (totalBytes > maxBytes && entries.isNotEmpty()) {
                val oldestKey = entries.entries.first().key
                entries.remove(oldestKey)?.let { removed -> totalBytes -= removed.size }
            }
        }

        @Synchronized
        fun clear() {
            entries.clear()
            totalBytes = 0
        }
    }

    private val requests = ConcurrentHashMap<String, ProxyRequest>()
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var running = false

    fun playbackSource(source: ClientStorageSource, path: String): LocalProxyPlaybackSource {
        val token = UUID.randomUUID().toString()
        requests[token] = ProxyRequest(source, path)
        val server = ensureStarted()
        val uri = "http://127.0.0.1:${server.localPort}/smb/$token/${path.substringAfterLast('/').encodePathSegment()}"
        return LocalProxyPlaybackSource(
            uri = uri,
            origin = SmbClient.buildSmbUrl(source, path),
            onClose = { requests.remove(token)?.close() },
        )
    }

    private fun ensureStarted(): ServerSocket {
        serverSocket?.let { return it }
        synchronized(this) {
            serverSocket?.let { return it }
            val socket = ServerSocket(port, 8, java.net.InetAddress.getByName("127.0.0.1"))
            serverSocket = socket
            running = true
            thread(name = "mediatree-smb-range-proxy", isDaemon = true) {
                while (running) {
                    runCatching {
                        val client = socket.accept()
                        thread(name = "mediatree-smb-range-client", isDaemon = true) {
                            runCatching { client.use { handle(it) } }
                                .onFailure { error ->
                                    if (!error.isClientDisconnect()) {
                                        Log.w(SmbRangeProxyLogTag, "SMB range request failed", error)
                                    }
                                }
                        }
                    }
                }
            }
            return socket
        }
    }

    private fun handle(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val requestLine = reader.readLine().orEmpty()
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) break
            val index = line.indexOf(':')
            if (index > 0) headers[line.take(index).trim()] = line.drop(index + 1).trim()
        }
        val path = requestLine.split(" ").getOrNull(1).orEmpty()
        val token = path.split("/").getOrNull(2)?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }
        val request = token?.let { requests[it] }
        if (request == null) {
            socket.getOutputStream().writeStatus(404, "Not Found")
            return
        }
        val timing = SmbRangeRequestTiming()
        val totalStart = System.nanoTime()
        val openStart = System.nanoTime()
        val file = request.lease.acquireFile(smbClient)
        timing.openMs = elapsedMs(openStart)
        val range = parseRange(headers.entries.firstOrNull { it.key.equals("Range", ignoreCase = true) }?.value, file.sizeBytes)
        if (range == null) {
            socket.getOutputStream().writeRangeNotSatisfiable(file.sizeBytes)
            return
        }
        val cachedBytes = request.cache.get(range)
        val status = if (range.start == 0L && range.endInclusive == file.sizeBytes - 1) "200 OK" else "206 Partial Content"
        val out = socket.getOutputStream()
        out.writeAscii("HTTP/1.1 $status\r\n")
        out.writeAscii("Accept-Ranges: bytes\r\n")
        out.writeAscii("Content-Type: application/octet-stream\r\n")
        out.writeAscii("Content-Length: ${range.length}\r\n")
        if (status.startsWith("206")) {
            out.writeAscii("Content-Range: bytes ${range.start}-${range.endInclusive}/${file.sizeBytes}\r\n")
        }
        out.writeAscii("Connection: close\r\n\r\n")
        val streamStart = System.nanoTime()
        val bytesWritten = if (cachedBytes != null) {
            timing.cacheHit = true
            out.write(cachedBytes)
            cachedBytes.size.toLong()
        } else {
            streamFile(request.lease, smbClient, range, out, request.cache, timing)
        }
        timing.streamMs = elapsedMs(streamStart)
        timing.totalMs = elapsedMs(totalStart)
        Log.d(
            SmbRangeProxyLogTag,
            "range=${range.start}-${range.endInclusive} bytes=$bytesWritten cacheHit=${timing.cacheHit} " +
                "openMs=${timing.openMs} firstReadMs=${timing.firstReadMs} streamMs=${timing.streamMs} totalMs=${timing.totalMs} " +
                "sourceHash=${request.source.id.memorySafeHash()} pathHash=${request.path.memorySafeHash()}",
        )
    }

    private fun streamFile(
        lease: ProxyRequestLease,
        smbClient: SmbClient,
        range: ByteRange,
        out: OutputStream,
        cache: RangeReadCache,
        timing: SmbRangeRequestTiming,
    ): Long {
        val buffer = ByteArray(SmbRangeStreamBufferBytes)
        val cachedBytes = if (range.length <= SmbRangeCachedReadMaxBytes) ByteArray(range.length.toInt()) else null
        var cachedOffset = 0
        var offset = range.start
        var remaining = range.length
        var totalRead = 0L
        while (remaining > 0) {
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val readStart = System.nanoTime()
            val read = lease.read(smbClient, offset, buffer, 0, toRead)
            if (timing.firstReadMs < 0L) timing.firstReadMs = elapsedMs(readStart)
            if (read <= 0) break
            out.write(buffer, 0, read)
            cachedBytes?.let { target ->
                System.arraycopy(buffer, 0, target, cachedOffset, read)
                cachedOffset += read
            }
            offset += read
            remaining -= read
            totalRead += read
        }
        if (cachedBytes != null && cachedOffset == cachedBytes.size) {
            cache.put(range, cachedBytes)
        }
        return totalRead
    }

    companion object {
        fun parseRange(rangeHeader: String?, sizeBytes: Long): ByteRange? {
            if (sizeBytes <= 0) return ByteRange(0L, 0L)
            val fallback = ByteRange(0L, sizeBytes - 1)
            val value = rangeHeader?.takeIf { it.startsWith("bytes=") }?.removePrefix("bytes=") ?: return fallback
            val parts = value.split("-", limit = 2)
            if (parts.size != 2) return fallback
            val startText = parts[0]
            val endText = parts[1]
            if (startText.isBlank()) {
                val suffixLength = endText.toLongOrNull()?.takeIf { it > 0 } ?: return null
                val length = suffixLength.coerceAtMost(sizeBytes)
                return ByteRange(sizeBytes - length, sizeBytes - 1)
            }
            val start = startText.toLongOrNull() ?: return null
            if (start >= sizeBytes) return null
            val end = endText.toLongOrNull() ?: (sizeBytes - 1)
            if (end < start) return null
            return ByteRange(
                start = start,
                endInclusive = end.coerceAtMost(sizeBytes - 1),
            )
        }
    }
}

private data class SmbRangeRequestTiming(
    var openMs: Long = 0L,
    var firstReadMs: Long = -1L,
    var streamMs: Long = 0L,
    var totalMs: Long = 0L,
    var cacheHit: Boolean = false,
)

private fun elapsedMs(startNanos: Long): Long =
    (System.nanoTime() - startNanos) / 1_000_000L

private fun Throwable.isClientDisconnect(): Boolean = this is IOException

private fun OutputStream.writeStatus(code: Int, message: String) {
    writeAscii("HTTP/1.1 $code $message\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
}

private fun OutputStream.writeRangeNotSatisfiable(sizeBytes: Long) {
    writeAscii("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$sizeBytes\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
}

private fun OutputStream.writeAscii(value: String) {
    write(value.toByteArray(Charsets.US_ASCII))
}

private fun String.encodePathSegment(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")
