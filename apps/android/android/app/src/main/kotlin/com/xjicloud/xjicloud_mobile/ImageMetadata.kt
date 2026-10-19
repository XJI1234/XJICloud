package com.xjicloud.xjicloud_mobile

internal object ImageMetadata {
    fun contentType(name: String, supplied: String): String {
        require(name.length in 1..255 && !name.contains(Regex("[/\\\\\\x00-\\x1f]")))
        val ext = name.substringAfterLast('.', "").lowercase()
        val expected = when (ext) { "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"; else -> throw IllegalArgumentException() }
        val mime = supplied.lowercase()
        require(mime.isEmpty() || mime == expected || (expected == "image/jpeg" && mime == "image/jpg"))
        return expected
    }
}
