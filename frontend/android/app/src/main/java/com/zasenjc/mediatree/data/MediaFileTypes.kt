package com.zasenjc.mediatree.data

private val ViewableImageExtensions = setOf(
    "jpg",
    "jpeg",
    "jpe",
    "jfif",
    "png",
    "webp",
    "bmp",
    "gif",
    "heic",
    "heif",
    "avif",
    "tif",
    "tiff",
)

fun isViewableImageFileName(name: String): Boolean =
    name.substringAfterLast('.', "")
        .lowercase()
        .takeIf { it.isNotBlank() }
        ?.let { it in ViewableImageExtensions }
        ?: false
