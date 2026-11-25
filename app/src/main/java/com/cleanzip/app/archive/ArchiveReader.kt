package com.cleanzip.app.archive

import com.github.junrar.Archive
import com.github.junrar.exception.UnsupportedRarV5Exception
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

data class ArchiveEntry(val path: String, val isDirectory: Boolean, val size: Long)

enum class ArchiveFormat(val label: String) {
    ZIP("ZIP"), SEVEN_Z("7z"), RAR("RAR"),
    TAR("TAR"), TAR_GZ("TAR.GZ"), TAR_BZ2("TAR.BZ2"), TAR_XZ("TAR.XZ"),
    GZ("GZ"), BZ2("BZ2"), XZ("XZ")
}

/** Error con un mensaje listo para mostrar al usuario. */
class ArchiveException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Lee distintos formatos de archivo comprimido a partir de un fichero local
 * (la app copia primero el archivo elegido a la caché).
 */
object ArchiveReader {

    fun detectFormat(fileName: String, file: File): ArchiveFormat {
        val n = fileName.lowercase()
        when {
            n.endsWith(".tar.gz") || n.endsWith(".tgz") -> return ArchiveFormat.TAR_GZ
            n.endsWith(".tar.bz2") || n.endsWith(".tbz2") || n.endsWith(".tbz") -> return ArchiveFormat.TAR_BZ2
            n.endsWith(".tar.xz") || n.endsWith(".txz") -> return ArchiveFormat.TAR_XZ
            n.endsWith(".zip") || n.endsWith(".jar") || n.endsWith(".apk") -> return ArchiveFormat.ZIP
            n.endsWith(".7z") -> return ArchiveFormat.SEVEN_Z
            n.endsWith(".rar") -> return ArchiveFormat.RAR
            n.endsWith(".tar") -> return ArchiveFormat.TAR
            n.endsWith(".gz") -> return ArchiveFormat.GZ
            n.endsWith(".bz2") -> return ArchiveFormat.BZ2
            n.endsWith(".xz") -> return ArchiveFormat.XZ
        }
        return detectByContent(file)
            ?: throw ArchiveException("Formato no reconocido. Formatos admitidos: ZIP, 7z, RAR, TAR, GZ, BZ2 y XZ.")
    }

    private fun detectByContent(file: File): ArchiveFormat? {
        val head = ByteArray(8)
        val read = FileInputStream(file).use { it.read(head) }
        if (read >= 4 && head[0] == 'R'.code.toByte() && head[1] == 'a'.code.toByte() &&
            head[2] == 'r'.code.toByte() && head[3] == '!'.code.toByte()
        ) return ArchiveFormat.RAR

        runCatching {
            BufferedInputStream(FileInputStream(file)).use { input ->
                when (ArchiveStreamFactory.detect(input)) {
                    ArchiveStreamFactory.ZIP, ArchiveStreamFactory.JAR -> return ArchiveFormat.ZIP
                    ArchiveStreamFactory.SEVEN_Z -> return ArchiveFormat.SEVEN_Z
                    ArchiveStreamFactory.TAR -> return ArchiveFormat.TAR
                    else -> Unit
                }
            }
        }
        runCatching {
            BufferedInputStream(FileInputStream(file)).use { input ->
                val (single, tar) = when (CompressorStreamFactory.detect(input)) {
                    CompressorStreamFactory.GZIP -> ArchiveFormat.GZ to ArchiveFormat.TAR_GZ
                    CompressorStreamFactory.BZIP2 -> ArchiveFormat.BZ2 to ArchiveFormat.TAR_BZ2
                    CompressorStreamFactory.XZ -> ArchiveFormat.XZ to ArchiveFormat.TAR_XZ
                    else -> return null
                }
                // ¿Hay un TAR dentro?
                val isTar = runCatching {
                    BufferedInputStream(decompressor(single, FileInputStream(file))).use {
                        ArchiveStreamFactory.detect(it) == ArchiveStreamFactory.TAR
                    }
                }.getOrDefault(false)
                return if (isTar) tar else single
            }
        }
        return null
    }

    /** Lista el contenido del archivo. */
    fun list(file: File, format: ArchiveFormat, archiveName: String): List<ArchiveEntry> {
        val result = mutableListOf<ArchiveEntry>()
        forEachEntry(file, format, archiveName, readData = false) { entry, _ -> result += entry }
        return result
    }

    /**
     * Recorre las entradas del archivo. Si [readData] es true, [block] recibe un flujo con
     * los datos de cada fichero (null para carpetas). El flujo solo es válido dentro de [block].
     */
    fun forEachEntry(
        file: File,
        format: ArchiveFormat,
        archiveName: String,
        readData: Boolean = true,
        block: (ArchiveEntry, InputStream?) -> Unit
    ) = wrapErrors {
        when (format) {
            ArchiveFormat.ZIP -> readZip(file, readData, block)
            ArchiveFormat.SEVEN_Z -> readSevenZ(file, readData, block)
            ArchiveFormat.RAR -> readRar(file, readData, block)
            ArchiveFormat.TAR -> readTar(FileInputStream(file), block)
            ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_BZ2, ArchiveFormat.TAR_XZ -> {
                val single = when (format) {
                    ArchiveFormat.TAR_GZ -> ArchiveFormat.GZ
                    ArchiveFormat.TAR_BZ2 -> ArchiveFormat.BZ2
                    else -> ArchiveFormat.XZ
                }
                readTar(decompressor(single, FileInputStream(file)), block)
            }
            ArchiveFormat.GZ, ArchiveFormat.BZ2, ArchiveFormat.XZ -> {
                val outName = archiveName.substringBeforeLast('.', archiveName + ".out")
                    .ifBlank { "archivo" }
                decompressor(format, FileInputStream(file)).use { input ->
                    block(ArchiveEntry(outName, false, -1), if (readData) input else null)
                }
            }
        }
    }

    private fun decompressor(format: ArchiveFormat, raw: InputStream): InputStream {
        val buffered = BufferedInputStream(raw, 64 * 1024)
        return when (format) {
            ArchiveFormat.GZ -> GzipCompressorInputStream(buffered, true)
            ArchiveFormat.BZ2 -> BZip2CompressorInputStream(buffered, true)
            ArchiveFormat.XZ -> XZCompressorInputStream(buffered, true)
            else -> error("No es un compresor: $format")
        }
    }

    private fun readZip(file: File, readData: Boolean, block: (ArchiveEntry, InputStream?) -> Unit) {
        ZipFile.builder().setFile(file).get().use { zip ->
            for (e in zip.entries.asSequence()) {
                val entry = ArchiveEntry(e.name, e.isDirectory, e.size)
                if (!readData || e.isDirectory) {
                    block(entry, null)
                    continue
                }
                if (!zip.canReadEntryData(e)) {
                    throw ArchiveException(
                        if (e.generalPurposeBit.usesEncryption()) "El archivo está protegido con contraseña y no se puede descomprimir."
                        else "El archivo usa un método de compresión no compatible."
                    )
                }
                zip.getInputStream(e).use { block(entry, it) }
            }
        }
    }

    private fun readSevenZ(file: File, readData: Boolean, block: (ArchiveEntry, InputStream?) -> Unit) {
        SevenZFile.builder().setFile(file).get().use { sz ->
            if (!readData) {
                sz.entries.forEach { block(ArchiveEntry(it.name, it.isDirectory, it.size), null) }
                return
            }
            val stream = object : InputStream() {
                override fun read(): Int = sz.read()
                override fun read(b: ByteArray, off: Int, len: Int): Int = sz.read(b, off, len)
            }
            var e = sz.nextEntry
            while (e != null) {
                val entry = ArchiveEntry(e.name, e.isDirectory, e.size)
                block(entry, if (e.isDirectory || !e.hasStream()) null else stream)
                e = sz.nextEntry
            }
        }
    }

    private fun readRar(file: File, readData: Boolean, block: (ArchiveEntry, InputStream?) -> Unit) {
        Archive(file).use { rar ->
            if (rar.isEncrypted) throw ArchiveException("El archivo RAR está protegido con contraseña y no se puede descomprimir.")
            for (h in rar.fileHeaders) {
                val name = h.fileName.replace('\\', '/')
                val entry = ArchiveEntry(name, h.isDirectory, h.fullUnpackSize)
                if (!readData || h.isDirectory) {
                    block(entry, null)
                    continue
                }
                if (h.isEncrypted) throw ArchiveException("El archivo RAR está protegido con contraseña y no se puede descomprimir.")
                rar.getInputStream(h).use { block(entry, it) }
            }
        }
    }

    private fun readTar(raw: InputStream, block: (ArchiveEntry, InputStream?) -> Unit) {
        TarArchiveInputStream(BufferedInputStream(raw, 64 * 1024)).use { tar ->
            var e = tar.nextEntry
            while (e != null) {
                when {
                    e.isDirectory -> block(ArchiveEntry(e.name, true, 0), null)
                    e.isFile -> block(ArchiveEntry(e.name, false, e.size), tar)
                    // Enlaces simbólicos, dispositivos, etc. se ignoran.
                }
                e = tar.nextEntry
            }
        }
    }

    private inline fun wrapErrors(action: () -> Unit) {
        try {
            action()
        } catch (e: ArchiveException) {
            throw e
        } catch (e: UnsupportedRarV5Exception) {
            throw ArchiveException("Los archivos RAR5 todavía no son compatibles. Solo se admite RAR4.", e)
        } catch (e: org.apache.commons.compress.PasswordRequiredException) {
            throw ArchiveException("El archivo está protegido con contraseña y no se puede descomprimir.", e)
        } catch (e: OutOfMemoryError) {
            throw ArchiveException("No hay memoria suficiente para abrir este archivo.")
        } catch (e: Exception) {
            if (e is java.io.InterruptedIOException || e is kotlinx.coroutines.CancellationException) throw e
            throw ArchiveException("No se pudo leer el archivo. Puede que esté dañado o no sea compatible.", e)
        }
    }

    /**
     * Convierte la ruta de una entrada en una lista de segmentos seguros
     * (evita rutas absolutas y "..", el llamado "zip slip").
     */
    fun safeSegments(path: String): List<String> =
        path.replace('\\', '/')
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .map { it.replace(Regex("[:*?\"<>|\\u0000]"), "_") }
}
