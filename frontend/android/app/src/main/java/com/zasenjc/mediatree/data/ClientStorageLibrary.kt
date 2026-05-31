package com.zasenjc.mediatree.data

private const val SmbLibraryPrefix = "smb/"

fun smbLibraryPath(sourceId: String): String = "$SmbLibraryPrefix$sourceId"

fun String.smbLibrarySourceId(): String? =
    takeIf { it.startsWith(SmbLibraryPrefix) }
        ?.removePrefix(SmbLibraryPrefix)
        ?.substringBefore('?')
        ?.takeIf { it.isNotBlank() }
