package com.xjicloud.xjicloud_mobile
import java.io.File
import org.junit.Assert.*
import org.junit.Test
class PrivateModelFilesTest {
    @Test fun permitsOwnedPayloadButRejectsTraversalSiblingAndDirectory() {
        val temp = java.nio.file.Files.createTempDirectory("viewer-private").toFile()
        try {
            val root=File(temp,"models").apply { mkdirs() }
            val owned=File(root,"model.ply").apply { writeBytes(byteArrayOf(1)) }
            val outside=File(temp,"private.txt").apply { writeBytes(byteArrayOf(2)) }
            val sibling=File(temp,"models-other").apply { mkdirs() }
            val other=File(sibling,"model.ply").apply { writeBytes(byteArrayOf(3)) }
            assertEquals(owned.canonicalFile,privateModelFile(owned.path,listOf(root)))
            for(path in listOf(File(root,"../private.txt").path,outside.path,other.path,root.path)) {
                try { privateModelFile(path,listOf(root));fail("Unowned path was accepted") }
                catch (_:IllegalArgumentException) {}
            }
        } finally { temp.deleteRecursively() }
    }
}
