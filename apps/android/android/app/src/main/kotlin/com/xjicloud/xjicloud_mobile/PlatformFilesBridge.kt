package com.xjicloud.xjicloud_mobile

import android.app.Activity
import android.content.Intent
import android.os.StatFs
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import android.net.Uri
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** SAF ownership ends after a bounded private snapshot; no storage permission. */
class PlatformFilesBridge(private val activity: Activity, channel: MethodChannel) {
    private val executor = Executors.newSingleThreadExecutor()
    private val sources = File(activity.filesDir, "model-sources").apply { mkdirs() }
    private val cache = File(activity.cacheDir, "model-assets").apply { mkdirs() }
    private val training = File(activity.filesDir, "training").apply { mkdirs() }
    private var pending: MethodChannel.Result? = null
    private var cancelled = AtomicBoolean(false)
    @Volatile private var closed = false

    init {
        sources.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "directories" -> result.success(mapOf("sources" to sources.absolutePath, "cache" to cache.absolutePath, "training" to training.absolutePath))
                "chooseImages" -> {
                    if (pending != null) result.error("source_busy", "A selection is already open", null)
                    else {
                        pending = result; cancelled = AtomicBoolean(false)
                        val tree = call.argument<Boolean>("directory") == true
                        try {
                            val picker = if (tree) Intent(Intent.ACTION_OPEN_DOCUMENT_TREE) else Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                            }
                            picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            activity.startActivityForResult(picker, if (tree) REQUEST_TREE else REQUEST_IMAGES)
                        } catch (_: Exception) { pending = null; result.error("storage", "File picker unavailable", null) }
                    }
                }
                "chooseModel" -> {
                    if (pending != null) { result.error("source_busy", "A selection is already open", null) }
                    else {
                        pending = result
                        cancelled = AtomicBoolean(false)
                        try {
                            activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, REQUEST_MODEL)
                        } catch (_: Exception) { pending = null; result.error("storage", "File picker unavailable", null) }
                    }
                }
                "cancelSelection" -> { cancelled.set(true); result.success(null) }
                "fingerprint" -> {
                    val id = call.argument<String>("id") ?: ""
                    if (!Regex("^[a-f0-9-]{36}$").matches(id)) result.error("source_changed", "Invalid source", null)
                    else executor.execute {
                        try {
                            val file = File(sources, id)
                            val digest = MessageDigest.getInstance("SHA-256")
                            file.inputStream().use { input ->
                                val buffer = ByteArray(65536)
                                while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
                            }
                            val data = mapOf("sizeBytes" to file.length(), "fingerprint" to hex(digest.digest()))
                            activity.runOnUiThread { if (!closed) result.success(data) }
                        } catch (_: Exception) { activity.runOnUiThread { if (!closed) result.error("source_changed", "Re-select the source", null) } }
                    }
                }
                else -> result.notImplemented()
            }
        }
    }
    fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?): Boolean {
        if (requestCode == REQUEST_IMAGES || requestCode == REQUEST_TREE) {
            receiveImages(requestCode, resultCode, intent); return true
        }
        if (requestCode != REQUEST_MODEL) return false
        val response = pending ?: return true
        val cancellation = cancelled
        val uri = intent?.data
        if (resultCode != Activity.RESULT_OK || uri == null || cancellation.get()) {
            pending = null; response.success(null); return true
        }
        executor.execute {
            val id = UUID.randomUUID().toString()
            val part = File(sources, "$id.part")
            val destination = File(sources, id)
            try {
                var name = ""
                var declared = -1L
                activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val n = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val s = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (n >= 0) name = cursor.getString(n) ?: ""
                        if (s >= 0 && !cursor.isNull(s)) declared = cursor.getLong(s)
                    }
                }
                require(name.length <= 255 && !name.contains(Regex("[/\\\\\\x00-\\x1f]")) && Regex("^.+\\.(ply|spz)$", RegexOption.IGNORE_CASE).matches(name))
                require(declared != 0L && declared <= MAX_SIZE)
                val available = StatFs(sources.absolutePath).availableBytes - RESERVE
                require(available > 0 && (declared < 0 || declared <= available))
                val snapshot = activity.contentResolver.openInputStream(uri)?.use { input ->
                    part.outputStream().use { output ->
                        val copied = SnapshotStreams.copy(input, output, minOf(MAX_SIZE, available)) { cancellation.get() || closed }
                        output.fd.sync()
                        copied
                    }
                } ?: throw IllegalStateException()
                require(snapshot.size > 0 && (declared < 0 || declared == snapshot.size))
                if (cancellation.get() || closed) throw InterruptedException()
                check(part.renameTo(destination))
                val data = mapOf("id" to id, "fileName" to name, "sizeBytes" to snapshot.size, "fingerprint" to snapshot.fingerprint)
                activity.runOnUiThread {
                    pending = null
                    if (!closed && !cancellation.get()) response.success(data)
                    else { destination.delete(); if (!closed) response.success(null) }
                }
            } catch (error: Exception) {
                part.delete(); destination.delete()
                activity.runOnUiThread {
                    pending = null
                    if (!closed) {
                        if (error is InterruptedException) response.success(null)
                        else response.error(if (error is IllegalArgumentException) "model_validation" else "storage", "Unable to prepare source", null)
                    }
                }
            }
        }
        return true
    }
    private data class ImageInput(val uri: Uri, val name: String, val relative: String, val mime: String, val declared: Long)
    private fun receiveImages(request: Int, resultCode: Int, intent: Intent?) {
        val response = pending ?: return
        val cancellation = cancelled
        if (resultCode != Activity.RESULT_OK || intent == null || cancellation.get()) { pending = null; response.success(null); return }
        executor.execute {
            val created = mutableListOf<File>()
            try {
                val selected = mutableListOf<ImageInput>()
                fun checkActive() { if (cancellation.get() || closed) throw InterruptedException() }
                fun metadata(uri: Uri): ImageInput {
                    var name = ""; var size = -1L
                    activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                        require(cursor.moveToFirst())
                        val n = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME); val s = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (n >= 0) name = cursor.getString(n) ?: ""
                        if (s >= 0 && !cursor.isNull(s)) size = cursor.getLong(s)
                    }
                    return ImageInput(uri, name, name, activity.contentResolver.getType(uri) ?: "", size)
                }
                if (request == REQUEST_TREE) {
                    val tree = intent.data ?: throw IllegalArgumentException()
                    val seen = mutableSetOf<String>(); var visited = 0
                    fun visit(documentId: String, prefix: String, depth: Int) {
                        checkActive(); require(depth <= 32 && prefix.length <= 4096 && seen.add(documentId))
                        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
                        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE)
                        val cursor = activity.contentResolver.query(children, columns, null, null, null) ?: throw IllegalStateException()
                        cursor.use {
                            while (it.moveToNext()) {
                                checkActive(); require(++visited <= 20000)
                                val id = it.getString(0); val name = it.getString(1) ?: ""; val mime = it.getString(2) ?: ""
                                require(name.length in 1..255 && !name.contains(Regex("[/\\\\\\x00-\\x1f]")))
                                val relative = if (prefix.isEmpty()) name else "$prefix/$name"
                                require(relative.length <= 4096)
                                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) visit(id, relative, depth + 1)
                                else if (mime.startsWith("image/") || Regex(".*\\.(jpg|jpeg|png|webp|heic|heif)$", RegexOption.IGNORE_CASE).matches(name)) {
                                    selected.add(ImageInput(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, relative, mime, if (it.isNull(3)) -1 else it.getLong(3)))
                                    require(selected.size <= 9999)
                                }
                            }
                        }
                    }
                    visit(DocumentsContract.getTreeDocumentId(tree), "", 0)
                } else {
                    val uris = linkedSetOf<Uri>()
                    intent.clipData?.let { clips -> require(clips.itemCount <= 9999); for (i in 0 until clips.itemCount) uris.add(clips.getItemAt(i).uri) }
                    intent.data?.let { uris.add(it) }
                    require(uris.size <= 9999)
                    for (uri in uris) { checkActive(); selected.add(metadata(uri)) }
                }
                require(selected.isNotEmpty())
                selected.sortWith(compareBy<ImageInput> { it.relative }.thenBy { it.uri.toString() })
                val result = mutableListOf<Map<String, Any>>()
                for (image in selected) {
                    checkActive()
                    val type = ImageMetadata.contentType(image.name, image.mime)
                    require(image.declared != 0L && image.declared <= MAX_SIZE)
                    val available = StatFs(sources.absolutePath).availableBytes - RESERVE
                    require(available > 0 && (image.declared < 0 || image.declared <= available))
                    val id = UUID.randomUUID().toString(); val part = File(sources, "$id.part"); val destination = File(sources, id)
                    created.add(part); created.add(destination)
                    val snapshot = activity.contentResolver.openInputStream(image.uri)?.use { input ->
                        part.outputStream().use { output ->
                            val copy = SnapshotStreams.copy(input, output, minOf(MAX_SIZE, available)) { cancellation.get() || closed }
                            output.fd.sync(); copy
                        }
                    } ?: throw IllegalStateException()
                    require(snapshot.size > 0 && (image.declared < 0 || image.declared == snapshot.size)); checkActive()
                    check(part.renameTo(destination))
                    result.add(mapOf("id" to id, "fileName" to image.name, "relativeName" to image.relative, "contentType" to type, "sizeBytes" to snapshot.size, "fingerprint" to snapshot.fingerprint))
                }
                activity.runOnUiThread {
                    pending = null
                    if (!closed && !cancellation.get()) response.success(result)
                    else { created.forEach { it.delete() }; if (!closed) response.success(null) }
                }
            } catch (error: Exception) {
                created.forEach { it.delete() }
                activity.runOnUiThread { pending = null; if (!closed) { if (error is InterruptedException) response.success(null) else response.error(if (error is IllegalArgumentException) "dataset_validation" else "storage", "Unable to prepare images", null) } }
            }
        }
    }
    fun dispose() { closed = true; cancelled.set(true); executor.shutdownNow() }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    companion object { const val REQUEST_MODEL = 5405; const val REQUEST_IMAGES = 5406; const val REQUEST_TREE = 5407; const val MAX_SIZE = 2147483648L; const val RESERVE = 67108864L }
}
