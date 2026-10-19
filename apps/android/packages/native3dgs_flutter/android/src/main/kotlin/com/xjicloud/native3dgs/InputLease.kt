package com.xjicloud.native3dgs

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.*

/** A seekable provider is used directly; a pipe gets a bounded private copy. */
internal class InputLease(val uri: Uri, private val copy: File? = null) {
    fun release() {
        if (copy != null && copy.exists() && !copy.delete()) {
            android.util.Log.w("Native3dgs", "Could not remove released private input copy")
        }
    }
    companion object {
        suspend fun acquire(context: Context, uri: Uri): InputLease = acquireWithHandoff(
            Dispatchers.IO, InputLease::release) {
            require(uri.scheme == "content")
            val resolver = context.contentResolver
            val seekable = resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                try { Os.lseek(descriptor.fileDescriptor, 0, OsConstants.SEEK_CUR); true }
                catch (error: ErrnoException) {
                    if (error.errno != OsConstants.ESPIPE) throw error
                    false
                }
            } ?: throw java.io.IOException("Document is not readable")
            if (seekable) return@acquireWithHandoff InputLease(uri)
            val directory = File(context.cacheDir, "native3dgs-input").apply {
                check(isDirectory || mkdirs()) { "Cannot create private input cache" }
            }
            val limit = minOf(2L * 1024 * 1024 * 1024,
                StatFs(directory.path).availableBytes - 64L * 1024 * 1024)
            check(limit > 0) { "Insufficient free storage for provider copy" }
            val target = File.createTempFile("model-", ".bin", directory)
            try {
                resolver.openInputStream(uri)?.use { source ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = source.read(buffer)
                            if (read < 0) break
                            total += read
                            check(total <= limit) { "Provider input exceeds storage budget" }
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: throw java.io.IOException("Document stream is unavailable")
                InputLease(FileProvider.getUriForFile(context,
                    "${context.packageName}.native3dgs.files", target), target)
            } catch (error: Exception) {
                InputLease(uri, target).release()
                throw error
            }
        }
    }
}
