package com.xjicloud.xjicloud_mobile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class SnapshotStreamsTest {
    @Test fun boundedStreamingHandlesNonSeekableProviderAndHashesExactly() {
        val data = ByteArray(150000) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        val result = SnapshotStreams.copy(ByteArrayInputStream(data), output, data.size.toLong(), { false })
        assertEquals(150000L, result.size)
        assertArrayEquals(data, output.toByteArray())
        assertEquals("02675bf9284bd74223e98ceea96ebee4c9a469272ead358f462d89753f8c909b", result.fingerprint)
    }
    @Test fun cancellationStopsBeforeWritingAnotherBuffer() {
        var cancelled = false
        val output = object : ByteArrayOutputStream() {
            override fun write(buffer: ByteArray, offset: Int, count: Int) { super.write(buffer, offset, count); cancelled = true }
        }
        try { SnapshotStreams.copy(ByteArrayInputStream(ByteArray(150000)), output, 150000, { cancelled }); fail() }
        catch (_: InterruptedException) { assertEquals(65536, output.size()) }
    }
    @Test fun capAndDiskFailurePropagateWithoutSuccessfulSnapshot() {
        try { SnapshotStreams.copy(ByteArrayInputStream(ByteArray(10)), ByteArrayOutputStream(), 9, { false }); fail() }
        catch (_: IllegalArgumentException) {}
        val output = object : java.io.OutputStream() { override fun write(value: Int) { throw IOException("disk full") } }
        try { SnapshotStreams.copy(ByteArrayInputStream(ByteArray(10)), output, 10, { false }); fail() }
        catch (_: IOException) {}
    }
}
