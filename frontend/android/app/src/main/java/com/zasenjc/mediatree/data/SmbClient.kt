package com.zasenjc.mediatree.data

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URLEncoder
import java.util.EnumSet
import java.util.concurrent.TimeUnit

data class SmbRemoteFile(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modified: Long,
)

data class SmbEntry(
    val sourceId: String,
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val modified: Long = 0L,
) {
    val isPlayableVideo: Boolean
        get() = !isDirectory && name.substringAfterLast('.', "").lowercase() in SmbVideoExtensions

    val isViewableImage: Boolean
        get() = !isDirectory && isViewableImageFileName(name)
}

class SmbClient(
    private val clientFactory: () -> SMBClient = { SMBClient() },
) {
    suspend fun list(source: ClientStorageSource, path: String = ""): List<SmbEntry> = withContext(Dispatchers.IO) {
        require(source.type == ClientStorageType.SMB) { "只支持 SMB 存储源" }
        val target = parseTarget(source)
        clientFactory().use { client ->
            client.connect(target.host).use { connection ->
                val auth = AuthenticationContext(source.username, source.secret.toCharArray(), "")
                val session = connection.authenticate(auth)
                (session.connectShare(target.share) as DiskShare).use { share ->
                    val directory = listOf(target.basePath, path.toSmbRelativePath())
                        .filter { it.isNotBlank() }
                        .joinToString("\\")
                    toEntries(
                        source = source,
                        currentPath = path,
                        files = share.list(directory).mapNotNull { it.toRemoteFile() },
                    )
                }
            }
        }
    }

    fun open(source: ClientStorageSource, path: String): SmbReadableFile {
        require(source.type == ClientStorageType.SMB) { "只支持 SMB 存储源" }
        val target = parseTarget(source)
        val client = clientFactory()
        val connection = client.connect(target.host)
        val auth = AuthenticationContext(source.username, source.secret.toCharArray(), "")
        val session = connection.authenticate(auth)
        val share = session.connectShare(target.share) as DiskShare
        val filePath = listOf(target.basePath, path.toSmbRelativePath())
            .filter { it.isNotBlank() }
            .joinToString("\\")
        val file = share.openFile(
            filePath,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OPEN,
            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE),
        )
        return SmbReadableFile(
            sizeBytes = file.getFileInformation(FileStandardInformation::class.java).endOfFile,
            read = { offset, buffer, bufferOffset, length ->
                file.read(buffer, offset, bufferOffset, length)
            },
            close = {
                runCatching { file.close() }
                runCatching { share.close() }
                runCatching { connection.close(true) }
                runCatching { client.close() }
            },
        )
    }

    companion object {
        fun buildSmbUrl(source: ClientStorageSource, relativePath: String): String {
            val target = parseTarget(source)
            val segments = listOf(target.share) +
                listOf(target.basePath, relativePath)
                    .flatMap { it.split("/", "\\") }
                    .filter { it.isNotBlank() }
            return "smb://${target.host}/${segments.joinToString("/") { it.encodeSmbSegment() }}"
        }

        fun toEntries(
            source: ClientStorageSource,
            currentPath: String,
            files: List<SmbRemoteFile>,
        ): List<SmbEntry> = files
            .filterNot { it.name == "." || it.name == ".." }
            .map { file ->
                val childPath = currentPath
                    .trim('/', '\\')
                    .let { parent -> if (parent.isBlank()) file.name else "$parent/${file.name}" }
                SmbEntry(
                    sourceId = source.id,
                    name = file.name,
                    path = childPath,
                    isDirectory = file.isDirectory,
                    sizeBytes = file.sizeBytes,
                    modified = file.modified,
                )
            }
            .sortedWith(
                compareBy<SmbEntry> { !it.isDirectory }
                    .thenBy { !it.isPlayableVideo }
                    .thenBy { !it.isViewableImage }
                    .thenBy { it.name.lowercase() },
            )

        internal fun parseTarget(source: ClientStorageSource): SmbTarget {
            val endpoint = source.endpoint.trim()
            val uri = if (endpoint.startsWith("smb://", ignoreCase = true)) URI(endpoint) else URI("smb://$endpoint")
            val host = requireNotNull(uri.host) { "SMB 地址缺少主机" }
            val pathSegments = source.path
                .split("/", "\\")
                .filter { it.isNotBlank() }
            require(pathSegments.isNotEmpty()) { "SMB 共享路径不能为空" }
            return SmbTarget(
                host = host,
                share = pathSegments.first(),
                basePath = pathSegments.drop(1).joinToString("\\"),
            )
        }
    }
}

data class SmbTarget(
    val host: String,
    val share: String,
    val basePath: String,
)

data class SmbReadableFile(
    val sizeBytes: Long,
    val read: (offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int) -> Int,
    val close: () -> Unit,
) : AutoCloseable {
    override fun close() = close.invoke()
}

private val SmbVideoExtensions = setOf("mp4", "m4v", "mkv", "mov", "avi", "wmv", "flv", "webm", "ts", "m2ts")

private fun FileIdBothDirectoryInformation.toRemoteFile(): SmbRemoteFile? {
    val name = fileName ?: return null
    return SmbRemoteFile(
        name = name,
        isDirectory = (fileAttributes and com.hierynomus.msfscc.FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L,
        sizeBytes = endOfFile,
        modified = changeTime.toEpochMillis(),
    )
}

private fun com.hierynomus.msdtyp.FileTime.toEpochMillis(): Long =
    toDate().time

private fun String.toSmbRelativePath(): String =
    trim('/', '\\')
        .split('/', '\\')
        .filter { it.isNotBlank() }
        .joinToString("\\")

private fun String.encodeSmbSegment(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")
