package com.zasenjc.mediatree.data

private const val SmbLibraryPrefix = "smb/"
private const val WebDavLibraryPrefix = "webdav/"

fun smbLibraryPath(sourceId: String): String = "$SmbLibraryPrefix$sourceId"

fun String.smbLibrarySourceId(): String? =
    takeIf { it.startsWith(SmbLibraryPrefix) }
        ?.removePrefix(SmbLibraryPrefix)
        ?.substringBefore('?')
        ?.takeIf { it.isNotBlank() }

fun webDavLibraryPath(sourceId: String): String = "$WebDavLibraryPrefix$sourceId"

fun String.webDavLibrarySourceId(): String? =
    takeIf { it.startsWith(WebDavLibraryPrefix) }
        ?.removePrefix(WebDavLibraryPrefix)
        ?.substringBefore('?')
        ?.takeIf { it.isNotBlank() }
