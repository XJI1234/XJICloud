package com.xjicloud.xjicloud_mobile

import org.junit.Assert.*
import org.junit.Test

class ImageMetadataTest {
    @Test fun acceptsAllowedExtensionAndMatchingMime() {
        assertEquals("image/jpeg", ImageMetadata.contentType("a.JPG", "image/jpeg"))
        assertEquals("image/png", ImageMetadata.contentType("a.png", ""))
        assertEquals("image/webp", ImageMetadata.contentType("a.webp", "image/webp"))
    }
    @Test fun rejectsHeicContradictoryMimeAndUnsafeName() {
        for ((name, type) in listOf("a.heic" to "image/heic", "a.jpg" to "image/png", "../a.jpg" to "image/jpeg", "a\u0000.jpg" to "image/jpeg")) {
            try { ImageMetadata.contentType(name, type); fail("Expected validation") } catch (_: IllegalArgumentException) { }
        }
    }
}
