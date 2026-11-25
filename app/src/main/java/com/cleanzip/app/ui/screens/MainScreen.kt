package com.cleanzip.app.ui.screens

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cleanzip.app.MainViewModel
import com.cleanzip.app.Status
import com.cleanzip.app.data.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, onShowTutorial: () -> Unit) {
    val archive = viewModel.archive
    val status = viewModel.status
    val busy = status is Status.Loading || status is Status.Extracting

    val pickArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onArchiveSelected)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::onDestinationSelected)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CleanZip", fontWeight = FontWeight.SemiBold) },
                actions = {
                    ThemeMenu(viewModel.prefs.themeMode, viewModel.prefs::updateThemeMode)
                    IconButton(onClick = onShowTutorial, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "Ver tutorial")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Paso 1: archivo
            item {
                StepCard(
                    step = 1,
                    icon = Icons.Outlined.FolderZip,
                    title = "Archivo comprimido",
                    value = when {
                        status is Status.Loading -> "Abriendo…"
                        archive != null -> archive.name
                        else -> "Ningún archivo seleccionado"
                    },
                    detail = archive?.let {
                        val size = if (it.totalSize > 0) " · " + Formatter.formatShortFileSize(LocalContext.current, it.totalSize) else ""
                        "${it.format.label} · ${it.fileCount} ${if (it.fileCount == 1) "archivo" else "archivos"}$size"
                    },
                    buttonText = if (archive == null) "Seleccionar archivo" else "Cambiar archivo",
                    enabled = !busy,
                    loading = status is Status.Loading,
                    onClick = { pickArchive.launch(arrayOf("*/*")) }
                )
            }

            // Paso 2: destino
            item {
                StepCard(
                    step = 2,
                    icon = Icons.Outlined.FolderOpen,
                    title = "Carpeta de destino",
                    value = viewModel.destinationName ?: "Ninguna carpeta seleccionada",
                    detail = null,
                    buttonText = if (viewModel.destination == null) "Elegir carpeta" else "Cambiar carpeta",
                    enabled = !busy,
                    onClick = { pickFolder.launch(viewModel.destination) }
                )
            }

            // Paso 3: descomprimir
            item {
                ExtractSection(
                    status = status,
                    enabled = archive != null && viewModel.destination != null && !busy,
                    onExtract = viewModel::extract,
                    onCancel = viewModel::cancel
                )
            }

            item {
                AnimatedVisibility(visible = status is Status.Done || status is Status.Error) {
                    when (status) {
                        is Status.Done -> MessageCard(
                            icon = Icons.Outlined.CheckCircle,
                            text = "¡Listo! ${status.files} ${if (status.files == 1) "archivo extraído" else "archivos extraídos"} en «${viewModel.destinationName}/${status.folderName}».",
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                            onDismiss = viewModel::dismissStatus
                        )
                        is Status.Error -> MessageCard(
                            icon = Icons.Outlined.ErrorOutline,
                            text = status.message,
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer,
                            onDismiss = viewModel::dismissStatus
                        )
                        else -> Unit
                    }
                }
            }

            // Contenido del archivo
            if (archive != null) {
                item {
                    Text(
                        "Contenido (${archive.entries.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    )
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        val shown = archive.entries.take(MAX_ENTRIES_SHOWN)
                        shown.forEachIndexed { i, entry ->
                            EntryRow(entry.path, entry.isDirectory, entry.size)
                            if (i < shown.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                        if (archive.entries.size > MAX_ENTRIES_SHOWN) {
                            Text(
                                "… y ${archive.entries.size - MAX_ENTRIES_SHOWN} elementos más",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_ENTRIES_SHOWN = 300

@Composable
private fun ThemeMenu(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val icon = when (current) {
        ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
        ThemeMode.LIGHT -> Icons.Outlined.LightMode
        ThemeMode.DARK -> Icons.Outlined.DarkMode
    }
    Box {
        IconButton(onClick = { open = true }) { Icon(icon, contentDescription = "Tema") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                ThemeMode.SYSTEM to "Según el sistema",
                ThemeMode.LIGHT to "Claro",
                ThemeMode.DARK to "Oscuro"
            ).forEach { (mode, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { RadioButton(selected = mode == current, onClick = null) },
                    onClick = {
                        onSelect(mode)
                        open = false
                    }
                )
            }
        }
    }
}

@Composable
private fun StepCard(
    step: Int,
    icon: ImageVector,
    title: String,
    value: String,
    detail: String?,
    buttonText: String,
    enabled: Boolean,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Paso $step · $title",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (detail != null) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun ExtractSection(status: Status, enabled: Boolean, onExtract: () -> Unit, onCancel: () -> Unit) {
    if (status is Status.Extracting) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp)) {
                Text("Descomprimiendo…", style = MaterialTheme.typography.titleMedium)
                if (status.currentFile.isNotEmpty()) {
                    Text(
                        status.currentFile,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (status.progress != null) {
                    LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text("Cancelar") }
            }
        }
    } else {
        Button(
            onClick = onExtract,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(Icons.Outlined.Unarchive, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Descomprimir", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun MessageCard(icon: ImageVector, text: String, container: Color, content: Color, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = container, contentColor = content)) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("OK", color = content) }
        }
    }
}

@Composable
private fun EntryRow(path: String, isDirectory: Boolean, size: Long) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isDirectory) Icons.Outlined.Folder else Icons.Outlined.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            path.trimEnd('/'),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!isDirectory && size >= 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                Formatter.formatShortFileSize(context, size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
