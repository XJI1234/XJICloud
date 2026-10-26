package com.xjicloud.xjicloud_mobile
import java.io.File
/** Accept only a real file under a trusted private store, resolving traversal/symlinks. */
internal fun privateModelFile(path: String, roots: List<File>): File {
    val file = File(path).canonicalFile
    require(file.isFile && roots.any { file.path.startsWith(it.canonicalPath + File.separator) })
    return file
}
