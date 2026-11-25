package com.cleanzip.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cleanzip.app.archive.ArchiveEntry
import com.cleanzip.app.archive.ArchiveException
import com.cleanzip.app.archive.ArchiveFormat
import com.cleanzip.app.archive.ArchiveReader
import com.cleanzip.app.archive.SafExtractor
import com.cleanzip.app.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SelectedArchive(
    val name: String,
    val format: ArchiveFormat,
    val entries: List<ArchiveEntry>,
    val file: File
) {
    val fileCount = entries.count { !it.isDirectory }
    val totalSize = entries.filter { !it.isDirectory && it.size > 0 }.sumOf { it.size }
}

sealed interface Status {
    data object Idle : Status
    data object Loading : Status
    data class Extracting(val progress: Float?, val currentFile: String) : Status
    data class Done(val folderName: String, val files: Int) : Status
    data class Error(val message: String) : Status
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)

    var archive by mutableStateOf<SelectedArchive?>(null)
        private set
    var destination by mutableStateOf<Uri?>(null)
        private set
    var destinationName by mutableStateOf<String?>(null)
        private set
    var status by mutableStateOf<Status>(Status.Idle)
        private set

    private var job: Job? = null

    init {
        // Recupera la última carpeta de destino si seguimos teniendo permiso sobre ella.
        prefs.lastDestination?.let { uri ->
            val granted = app.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
            if (granted) setDestinationInternal(uri)
        }
    }

    fun onArchiveSelected(uri: Uri) {
        if (status is Status.Extracting || status is Status.Loading) return
        job?.cancel()
        job = viewModelScope.launch {
            status = Status.Loading
            archive = null
            try {
                val selected = withContext(Dispatchers.IO) { load(uri) }
                archive = selected
                status = Status.Idle
            } catch (e: ArchiveException) {
                status = Status.Error(e.message ?: "Error desconocido")
            } catch (e: SecurityException) {
                status = Status.Error("No hay permiso para leer este archivo.")
            } catch (e: java.io.IOException) {
                status = Status.Error("No se pudo abrir el archivo.")
            }
        }
    }

    private fun load(uri: Uri): SelectedArchive {
        val app = getApplication<Application>()
        val name = displayName(uri) ?: "archivo"
        val cacheDir = File(app.cacheDir, "archives").apply { deleteRecursively(); mkdirs() }
        val local = File(cacheDir, "current")
        app.contentResolver.openInputStream(uri)?.use { input ->
            local.outputStream().use { input.copyTo(it, 64 * 1024) }
        } ?: throw ArchiveException("No se pudo abrir el archivo.")
        val format = ArchiveReader.detectFormat(name, local)
        val entries = ArchiveReader.list(local, format, name)
        if (entries.isEmpty()) throw ArchiveException("El archivo está vacío.")
        return SelectedArchive(name, format, entries, local)
    }

    fun onDestinationSelected(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        runCatching {
            resolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        prefs.lastDestination = uri
        setDestinationInternal(uri)
        if (status is Status.Done || status is Status.Error) status = Status.Idle
    }

    private fun setDestinationInternal(uri: Uri) {
        destination = uri
        destinationName = DocumentFile.fromTreeUri(getApplication(), uri)?.name ?: uri.lastPathSegment
    }

    fun extract() {
        val a = archive ?: return
        val dest = destination ?: return
        if (status is Status.Extracting) return
        job = viewModelScope.launch {
            status = Status.Extracting(0f, "")
            status = try {
                var lastPct = -1
                var lastFile = ""
                val result = withContext(Dispatchers.IO) {
                    SafExtractor(getApplication()).extract(a.file, a.format, a.name, a.totalSize, dest) { p, f ->
                        // Limita las actualizaciones de la interfaz: solo al cambiar de % o de fichero.
                        val pct = p?.let { (it * 100).toInt() } ?: -1
                        if (isActive && (pct != lastPct || f != lastFile)) {
                            lastPct = pct
                            lastFile = f
                            status = Status.Extracting(p, f)
                        }
                    }
                }
                Status.Done(result.folderName, result.files)
            } catch (e: ArchiveException) {
                Status.Error(e.message ?: "Error desconocido")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Status.Error("Error al descomprimir: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }
    }

    fun cancel() {
        job?.cancel()
        status = Status.Idle
    }

    fun dismissStatus() {
        if (status is Status.Done || status is Status.Error) status = Status.Idle
    }

    private fun displayName(uri: Uri): String? {
        val resolver = getApplication<Application>().contentResolver
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    override fun onCleared() {
        File(getApplication<Application>().cacheDir, "archives").deleteRecursively()
    }
}
