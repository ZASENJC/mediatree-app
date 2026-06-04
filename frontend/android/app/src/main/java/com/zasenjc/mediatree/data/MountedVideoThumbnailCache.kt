package com.zasenjc.mediatree.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.SystemClock
import com.zasenjc.mediatree.playback.PlaybackSource
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class MountedVideoThumbnailSpec(
    val width: Int,
    val height: Int,
)

data class MountedVideoThumbnailRequest(
    val source: ClientStorageSource?,
    val movie: MovieDto,
    val spec: MountedVideoThumbnailSpec,
)

private data class MountedVideoThumbnailSource(
    val uri: String,
    val headers: Map<String, String>,
    val onClose: (() -> Unit)? = null,
)

private const val MountedVideoFrameParallelism = 2
private val mountedVideoFrameDispatcher = Dispatchers.IO.limitedParallelism(MountedVideoFrameParallelism)
private const val MountedVideoThumbnailCacheTtlMillis = 7 * 24 * 60 * 60 * 1000L
private const val MountedVideoThumbnailMemoryCacheMaxBytes = 12 * 1024 * 1024L
private const val MountedVideoThumbnailJpegQuality = 82

class MountedVideoThumbnailCache(
    context: Context,
    private val container: AppContainer,
) {
    private val memoryCache = MountedVideoThumbnailMemoryCache()
    private val diskCache = MountedVideoThumbnailDiskCache(context)
    private val requestMutex = Mutex()
    private val requests = mutableMapOf<String, Deferred<Bitmap?>>()

    suspend fun getOrCreate(request: MountedVideoThumbnailRequest): Bitmap? {
        val cacheKey = request.cacheKey() ?: return null
        memoryCache.getCached(cacheKey)?.let { return it }
        diskCache.getCached(cacheKey)?.let { bitmap ->
            memoryCache.putCached(cacheKey, bitmap)
            return bitmap
        }
        val inFlight = requestMutex.withLock {
            requests[cacheKey] ?: container.applicationScope.async {
                createThumbnail(cacheKey, request)
            }.also { newRequest ->
                requests[cacheKey] = newRequest
                newRequest.invokeOnCompletion {
                    container.applicationScope.async {
                        requestMutex.withLock { requests.remove(cacheKey) }
                    }
                }
            }
        }
        return inFlight.await()
    }

    suspend fun clear() {
        val pendingRequests = requestMutex.withLock {
            requests.values.toList().also { requests.clear() }
        }
        pendingRequests.forEach { request -> request.cancel() }
        memoryCache.clear()
        diskCache.clear()
    }

    private suspend fun createThumbnail(cacheKey: String, request: MountedVideoThumbnailRequest): Bitmap? {
        memoryCache.getCached(cacheKey)?.let { return it }
        diskCache.getCached(cacheKey)?.let { bitmap ->
            memoryCache.putCached(cacheKey, bitmap)
            return bitmap
        }
        val sourceInfo = request.sourceInfo() ?: return null
        return try {
            extractMountedVideoFrame(sourceInfo, request.spec)
                ?.also { frame ->
                    memoryCache.putCached(cacheKey, frame)
                    runCatching { diskCache.putCached(cacheKey, frame) }
                }
        } finally {
            sourceInfo.onClose?.invoke()
        }
    }

    private fun MountedVideoThumbnailRequest.sourceInfo(): MountedVideoThumbnailSource? {
        if (!movie.isMountedLibraryItem()) return null
        val resolvedSource = source ?: return null
        return when (resolvedSource.type) {
            ClientStorageType.SMB -> {
                val playbackSource = container.smbRangeProxy.playbackSource(source = resolvedSource, path = movie.path)
                MountedVideoThumbnailSource(
                    uri = playbackSource.uri,
                    headers = playbackSource.headers,
                    onClose = playbackSource.onClose,
                )
            }
            ClientStorageType.WebDAV -> {
                val playbackSource = PlaybackSource.webDav(source = resolvedSource, path = movie.path)
                MountedVideoThumbnailSource(
                    uri = playbackSource.uri,
                    headers = playbackSource.headers,
                )
            }
        }
    }
}

private class MountedVideoThumbnailMemoryCache(
    private val ttlMillis: Long = MountedVideoThumbnailCacheTtlMillis,
    private val maxBytes: Long = MountedVideoThumbnailMemoryCacheMaxBytes,
) {
    private data class Entry(
        val bitmap: Bitmap,
        val createdAtMillis: Long,
        val bytes: Long,
    )

    private val entries = LinkedHashMap<String, Entry>(0, 0.75f, true)
    private var totalBytes = 0L

    @Synchronized
    fun getCached(key: String): Bitmap? {
        val entry = entries[key] ?: return null
        if (SystemClock.elapsedRealtime() - entry.createdAtMillis > ttlMillis) {
            removeEntry(key)
            return null
        }
        return entry.bitmap
    }

    @Synchronized
    fun putCached(key: String, bitmap: Bitmap) {
        removeEntry(key)
        val bytes = bitmap.allocationByteCount.toLong()
        entries[key] = Entry(
            bitmap = bitmap,
            createdAtMillis = SystemClock.elapsedRealtime(),
            bytes = bytes,
        )
        totalBytes += bytes
        while (totalBytes > maxBytes && entries.isNotEmpty()) {
            removeOldestCacheEntry()
        }
    }

    @Synchronized
    fun clear() {
        entries.clear()
        totalBytes = 0L
    }

    @Synchronized
    fun removeOldestCacheEntry() {
        val oldestKey = entries.entries.firstOrNull()?.key ?: return
        removeEntry(oldestKey)
    }

    private fun removeEntry(key: String) {
        entries.remove(key)?.let { removed -> totalBytes -= removed.bytes }
    }
}

private class MountedVideoThumbnailDiskCache(
    context: Context,
    private val ttlMillis: Long = MountedVideoThumbnailCacheTtlMillis,
) {
    private val directory: File = context.cacheDir.resolve("mounted_video_thumbnails")

    suspend fun getCached(key: String): Bitmap? =
        withContext(Dispatchers.IO) {
            val file = fileForKey(key)
            if (!file.exists()) return@withContext null
            if (System.currentTimeMillis() - file.lastModified() > ttlMillis) {
                file.delete()
                return@withContext null
            }
            BitmapFactory.decodeFile(file.absolutePath)?.toLowMemoryThumbnail()
        }

    suspend fun putCached(key: String, bitmap: Bitmap) {
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            val file = fileForKey(key)
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, MountedVideoThumbnailJpegQuality, out)
            }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            directory.deleteRecursively()
            directory.mkdirs()
        }
    }

    private fun fileForKey(key: String): File =
        directory.resolve("${key.sha256()}.jpg")
}

private suspend fun extractMountedVideoFrame(source: MountedVideoThumbnailSource, spec: MountedVideoThumbnailSpec): Bitmap? =
    withContext(mountedVideoFrameDispatcher) {
        runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(source.uri, source.headers)
                retriever.scaledFrameAtStart(spec)
            }
        }.getOrNull()
    }

private fun MediaMetadataRetriever.scaledFrameAtStart(spec: MountedVideoThumbnailSpec): Bitmap? =
    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        getScaledFrameAtTime(
            0L,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            spec.width,
            spec.height,
        )
    } else {
        getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?.let { Bitmap.createScaledBitmap(it, spec.width, spec.height, true) }
    })?.toLowMemoryThumbnail()

private fun Bitmap.toLowMemoryThumbnail(): Bitmap =
    if (config == Bitmap.Config.RGB_565) {
        this
    } else {
        copy(Bitmap.Config.RGB_565, false).also {
            if (it !== this) recycle()
        }
    }

fun mountedThumbnailKey(source: ClientStorageSource?, movie: MovieDto, spec: MountedVideoThumbnailSpec): String? =
    MountedVideoThumbnailRequest(source = source, movie = movie, spec = spec).cacheKey()

private fun MountedVideoThumbnailRequest.cacheKey(): String? {
    val resolvedSource = source ?: return null
    if (!movie.isMountedLibraryItem()) return null
    val version = movie.fileSize ?: movie.size ?: 0L
    return listOf(
        resolvedSource.type.name,
        resolvedSource.id,
        movie.path,
        version.toString(),
        spec.width.toString(),
        spec.height.toString(),
    ).joinToString("|")
}

private fun MovieDto.isMountedLibraryItem(): Boolean =
    mediaRoot?.mountedLibrarySourceId() != null

private fun String.mountedLibrarySourceId(): String? =
    smbLibrarySourceId() ?: webDavLibrarySourceId()

private fun String.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { "%02x".format(it) }
}
