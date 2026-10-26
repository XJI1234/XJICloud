package com.xjicloud.xjicloud_mobile

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

internal object SnapshotStreams {
    data class Snapshot(val size: Long, val fingerprint: String)
    fun copy(input: InputStream, output: OutputStream, limit: Long, cancelled: () -> Boolean): Snapshot {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)
        var size = 0L
        while (true) {
            if (cancelled()) throw InterruptedException()
            val count = input.read(buffer)
            if (count < 0) break
            size += count
            require(size <= limit)
            output.write(buffer, 0, count)
            digest.update(buffer, 0, count)
        }
        return Snapshot(size, digest.digest().joinToString("") { "%02x".format(it) })
    }
}
