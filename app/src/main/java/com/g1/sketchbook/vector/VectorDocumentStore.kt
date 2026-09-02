package com.g1.sketchbook.vector

import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/** Injectable boundary so failed and interrupted replacement paths are deterministic in unit tests. */
interface VectorDocumentFileSystem {
    fun exists(file: File): Boolean
    fun readText(file: File): String
    fun ensureDirectory(directory: File): Boolean
    fun writeTextAndSync(file: File, text: String)
    fun moveAtomically(source: File, target: File): Boolean
    fun delete(file: File): Boolean
}

open class DefaultVectorDocumentFileSystem : VectorDocumentFileSystem {
    override fun exists(file: File): Boolean = file.exists()
    override fun readText(file: File): String = file.readText()
    override fun ensureDirectory(directory: File): Boolean = directory.exists() || directory.mkdirs()

    override fun writeTextAndSync(file: File, text: String) {
        FileOutputStream(file).use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
    }

    /** Every caller first moves an existing target aside, so same-directory rename avoids the
     * API-26-only java.nio file bridge while preserving the recoverable replacement protocol. */
    override fun moveAtomically(source: File, target: File): Boolean = source.renameTo(target)

    override fun delete(file: File): Boolean = !file.exists() || file.delete()
}

class VectorDocumentStore(
    private val bookDir: File,
    private val fileSystem: VectorDocumentFileSystem = DefaultVectorDocumentFileSystem(),
) {
    private val v1 = File(bookDir, "vector_canvas.json")
    private val v2 = File(bookDir, "vector_canvas_v2.json")
    private val temporary = File(bookDir, "vector_canvas_v2.json.tmp")
    private val previous = File(bookDir, "vector_canvas_v2.previous")

    /** Reads only v2 files, including a valid interrupted-replacement backup, never the v1 fallback. */
    fun loadV2(): VectorDocument? = recoverableV2Candidate()?.second

    /** Timestamp for the same valid v2 candidate [loadV2] would read; v1 never participates. */
    fun recoverableV2UpdatedAt(): Long = recoverableV2Candidate()?.first?.lastModified() ?: 0L

    private fun recoverableV2Candidate(): Pair<File, VectorDocument>? {
        readDocument(v2)?.let { return v2 to it }
        // A process can die after primary -> previous but before tmp -> primary. The valid prior v2 wins over v1.
        readDocument(previous)?.let { return previous to it }
        return null
    }

    fun load(): VectorDocument? {
        loadV2()?.let { return it }
        return readDocument(v1, legacy = true)
    }

    private val writeLock = writeLocks.computeIfAbsent(bookDir.absolutePath) { Any() }

    fun saveV2(document: VectorDocument) = synchronized(writeLock) {
        val encoded = encodeVectorDocument(document)
        require(fileSystem.ensureDirectory(bookDir)) { "Could not create vector document directory" }
        fileSystem.writeTextAndSync(temporary, encoded)
        require(decodeVectorDocument(fileSystem.readText(temporary)) == document) { "Temporary v2 validation failed" }

        val prior = readDocument(v2)
        if (prior != null) {
            // Move the known-good primary aside first. A failed final move can then restore it or load it from .previous.
            check(fileSystem.moveAtomically(v2, previous)) { "Could not preserve existing v2" }
            if (!fileSystem.moveAtomically(temporary, v2)) {
                // Cleanup is safe only once the prior file is back at primary. If restore is interrupted,
                // retain both artifacts so load() can recover the valid .previous document.
                if (fileSystem.moveAtomically(previous, v2)) fileSystem.delete(temporary)
                throw IllegalStateException("Could not atomically replace v2")
            }
            check(readDocument(v2) == document) { "Replaced v2 failed validation" }
            fileSystem.delete(previous)
        } else {
            check(fileSystem.moveAtomically(temporary, v2)) { "Could not atomically install v2" }
            check(readDocument(v2) == document) { "Installed v2 failed validation" }
        }
    }

    private companion object {
        val writeLocks = ConcurrentHashMap<String, Any>()
    }

    private fun readDocument(file: File, legacy: Boolean = false): VectorDocument? = runCatching {
        if (!fileSystem.exists(file)) null
        else if (legacy) decodeCanonicalLegacyPage(fileSystem.readText(file))?.let(::legacyPageAsDocument)
        else decodeVectorDocument(fileSystem.readText(file))
    }.getOrNull()
}
