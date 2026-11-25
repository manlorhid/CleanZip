package com.cleanzip.app.archive

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/**
 * Extrae un archivo comprimido dentro de una carpeta elegida por el usuario
 * mediante el Storage Access Framework.
 */
class SafExtractor(private val context: Context) {

    data class Result(val folderName: String, val files: Int)

    /**
     * Crea una subcarpeta con el nombre del archivo dentro de [treeUri] y extrae ahí todo.
     * [onProgress] recibe un valor entre 0 y 1, o null si el tamaño total es desconocido.
     */
    suspend fun extract(
        archive: File,
        format: ArchiveFormat,
        archiveName: String,
        totalBytes: Long,
        treeUri: Uri,
        onProgress: (Float?, String) -> Unit
    ): Result {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?.takeIf { it.canWrite() }
            ?: throw ArchiveException("No se puede escribir en la carpeta de destino. Elige otra carpeta.")

        val folderName = uniqueName(root, baseName(archiveName))
        val target = root.createDirectory(folderName)
            ?: throw ArchiveException("No se pudo crear la carpeta «$folderName» en el destino.")

        val dirs = HashMap<String, DocumentFile>().apply { put("", target) }
        fun dirFor(segments: List<String>): DocumentFile {
            var current = target
            var key = ""
            for (s in segments) {
                key = if (key.isEmpty()) s else "$key/$s"
                current = dirs.getOrPut(key) {
                    current.createDirectory(s)
                        ?: throw ArchiveException("No se pudo crear la carpeta «$s».")
                }
            }
            return current
        }

        var written = 0L
        var files = 0
        val buffer = ByteArray(64 * 1024)
        val ctx = coroutineContext

        ArchiveReader.forEachEntry(archive, format, archiveName) { entry, input ->
            ctx.ensureActive()
            val segments = ArchiveReader.safeSegments(entry.path)
            if (segments.isEmpty()) return@forEachEntry
            if (entry.isDirectory) {
                dirFor(segments)
                return@forEachEntry
            }
            val parent = dirFor(segments.dropLast(1))
            val name = segments.last()
            onProgress(progress(written, totalBytes), name)
            val doc = parent.createFile("application/octet-stream", name)
                ?: throw ArchiveException("No se pudo crear el archivo «$name».")
            val out = context.contentResolver.openOutputStream(doc.uri)
                ?: throw ArchiveException("No se pudo escribir el archivo «$name».")
            out.use { o ->
                if (input != null) {
                    copy(input, o, buffer) { n ->
                        ctx.ensureActive()
                        written += n
                        onProgress(progress(written, totalBytes), name)
                    }
                }
            }
            files++
        }
        onProgress(1f, "")
        return Result(folderName, files)
    }

    private inline fun copy(input: InputStream, out: java.io.OutputStream, buffer: ByteArray, onChunk: (Int) -> Unit) {
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            onChunk(n)
        }
    }

    private fun progress(written: Long, total: Long): Float? =
        if (total > 0) (written.toFloat() / total).coerceIn(0f, 1f) else null

    private fun baseName(name: String): String {
        val lower = name.lowercase()
        val doubleExt = listOf(".tar.gz", ".tar.bz2", ".tar.xz").firstOrNull { lower.endsWith(it) }
        val base = when {
            doubleExt != null -> name.dropLast(doubleExt.length)
            name.contains('.') -> name.substringBeforeLast('.')
            else -> name
        }
        return ArchiveReader.safeSegments(base).joinToString("_").ifBlank { "Extraído" }
    }

    private fun uniqueName(parent: DocumentFile, base: String): String {
        val existing = parent.listFiles().mapNotNull { it.name }.toHashSet()
        if (base !in existing) return base
        var i = 1
        while ("$base ($i)" in existing) i++
        return "$base ($i)"
    }
}
