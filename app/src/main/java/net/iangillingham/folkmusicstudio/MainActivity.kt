/*
 * Copyright (c) 2026 Ian Gillingham
 * Licensed under the GNU General Public License v3.0
 */
package net.iangillingham.folkmusicstudio

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import net.iangillingham.folkmusicstudio.logic.AbcHandler
import net.iangillingham.folkmusicstudio.model.AbcTune
import net.iangillingham.folkmusicstudio.ui.AbcVisualizer
import net.iangillingham.folkmusicstudio.ui.components.*
import net.iangillingham.folkmusicstudio.ui.theme.FolkMusicStudioTheme
import java.io.BufferedReader
import java.io.InputStreamReader

private const val PREFS_NAME = "FolkMusicStudioPrefs"
private const val KEY_DIRECTORY_URI = "directoryUri"
private const val KEY_SELECTED_FILES = "selectedFiles"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            FolkMusicStudioTheme {
                FolkMusicStudioApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun FolkMusicStudioApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE) }
    
    // Persist selected tune and content
    var selectedTuneTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTuneUri by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTune by remember { mutableStateOf<AbcTune?>(null) }
    var abcContent by rememberSaveable { mutableStateOf("") }
    
    var directoryUri by rememberSaveable { 
        mutableStateOf(prefs.getString(KEY_DIRECTORY_URI, null)) 
    }
    var showPreview by rememberSaveable { mutableStateOf(true) }
    var isLoadingFiles by remember { mutableStateOf(false) }
    var isCreatingNewTune by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    var showSetupDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var pendingTuneSelection by remember { mutableStateOf<AbcTune?>(null) }
    var reopenSetupAfterPickerCancel by remember { mutableStateOf(false) }
    
    // Multi-selection states
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    val selectedTunes = remember { mutableStateListOf<AbcTune>() }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var showCopyTargetChoiceDialog by remember { mutableStateOf(false) }
    var duplicateTuneRequest by remember { mutableStateOf<Pair<AbcTune, CompletableDeferred<Boolean?>>?>(null) }
    
    var isPlaying by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var tempo by rememberSaveable { mutableStateOf(120f) }
    var activeTempo by rememberSaveable { mutableStateOf(120f) }
    
    var leftPaneWidth by rememberSaveable { mutableStateOf(250f) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    val parsedTunes = remember { mutableStateListOf<AbcTune>() }
    
    val selectedFilesUris = rememberSaveable(saver = listSaver(
        save = { it.toList() },
        restore = { it.toMutableStateList() }
    )) { 
        val savedFiles = prefs.getStringSet(KEY_SELECTED_FILES, emptySet()) ?: emptySet()
        savedFiles.toMutableStateList()
    }

    // Effect to persist selected files when they change
    LaunchedEffect(selectedFilesUris.toList()) {
        prefs.edit().putStringSet(KEY_SELECTED_FILES, selectedFilesUris.toSet()).apply()
    }

    var refreshCount by rememberSaveable { mutableStateOf(0) }

    // Main loading effect - triggered by changes to directory, selected files, or manual refresh
    LaunchedEffect(directoryUri, selectedFilesUris.size, context, refreshCount) {
        isLoadingFiles = true
        try {
            val allParsedTunes = mutableListOf<AbcTune>()
            val filesToProcess = mutableListOf<DocumentFile>()
            
            if (directoryUri != null) {
                val root = DocumentFile.fromTreeUri(context, Uri.parse(directoryUri!!))
                root?.listFiles()?.filter { it.name?.endsWith(".abc") == true }?.let {
                    filesToProcess.addAll(it)
                }
            }
            
            selectedFilesUris.forEach { uriString ->
                DocumentFile.fromSingleUri(context, Uri.parse(uriString))?.let {
                    filesToProcess.add(it)
                }
            }
            
            filesToProcess.forEach { file ->
                try {
                    context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                        val content = BufferedReader(InputStreamReader(inputStream)).readText()
                        allParsedTunes.addAll(AbcHandler.parseAbcContent(content, file.uri, file.name ?: "Unknown"))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            parsedTunes.clear()
            parsedTunes.addAll(allParsedTunes)
            
            // Restore selection after reload
            if (selectedTuneTitle != null && selectedTuneUri != null) {
                selectedTune = allParsedTunes.find { 
                    it.title == selectedTuneTitle && it.sourceUri.toString() == selectedTuneUri 
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoadingFiles = false
        }
    }

    fun saveTune(tune: AbcTune, newContent: String) {
        AbcHandler.saveTune(context, tune, newContent)?.let { updatedTune ->
            val index = parsedTunes.indexOf(tune)
            if (index != -1) {
                parsedTunes[index] = updatedTune
                selectedTune = updatedTune
                abcContent = newContent
            }
        }
    }

    fun deleteTune(tune: AbcTune) {
        if (AbcHandler.deleteTune(context, tune)) {
            parsedTunes.remove(tune)
            selectedTune = null
            abcContent = ""
            selectedTuneTitle = null
            selectedTuneUri = null
        }
    }

    val openDirectoryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) {
            if (reopenSetupAfterPickerCancel) {
                showSetupDialog = true
            }
            reopenSetupAfterPickerCancel = false
        } else {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                prefs.edit().putString(KEY_DIRECTORY_URI, uri.toString()).apply()
                directoryUri = uri.toString()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                reopenSetupAfterPickerCancel = false
            }
        }
    }

    val openFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) {
            if (reopenSetupAfterPickerCancel) {
                showSetupDialog = true
            }
            reopenSetupAfterPickerCancel = false
        } else {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    if (!selectedFilesUris.contains(uri.toString())) {
                        selectedFilesUris.add(uri.toString())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            reopenSetupAfterPickerCancel = false
        }
    }

    // New launchers for bulk copy
    val createTargetFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            scope.launch {
                AbcHandler.copyTunesToFile(
                    context,
                    selectedTunes.toList(),
                    it,
                    onDuplicateFound = { tune ->
                        val deferred = CompletableDeferred<Boolean?>()
                        duplicateTuneRequest = Pair(tune, deferred)
                        deferred.await()
                    }
                )
                selectionMode = false
                selectedTunes.clear()
                refreshCount++
            }
        }
    }

    val selectTargetFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                AbcHandler.copyTunesToFile(
                    context,
                    selectedTunes.toList(),
                    it,
                    onDuplicateFound = { tune ->
                        val deferred = CompletableDeferred<Boolean?>()
                        duplicateTuneRequest = Pair(tune, deferred)
                        deferred.await()
                    }
                )
                selectionMode = false
                selectedTunes.clear()
                refreshCount++
            }
        }
    }

    val createNewFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { os ->
                    os.write(abcContent.toByteArray())
                }
                isCreatingNewTune = false
                refreshCount++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val appendToFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                val existingContent = context.contentResolver.openInputStream(it)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).readText()
                } ?: ""

                val xValues = Regex("(?m)^X:\\s*(\\d+)").findAll(existingContent)
                    .map { it.groupValues[1].toInt() }
                    .toList()
                val nextX = (xValues.maxOrNull() ?: 0) + 1

                val updatedNewTune = abcContent.replaceFirst(Regex("(?m)^X:.*"), "X:$nextX")
                
                val finalContent = if (existingContent.isEmpty()) {
                    updatedNewTune
                } else if (existingContent.endsWith("\n")) {
                    existingContent + "\n" + updatedNewTune
                } else {
                    existingContent + "\n\n" + updatedNewTune
                }

                context.contentResolver.openOutputStream(it, "wt")?.use { os ->
                    os.write(finalContent.toByteArray())
                }
                
                isCreatingNewTune = false
                refreshCount++
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            TuneLibraryDrawer(
                parsedTunes = parsedTunes,
                selectedTune = selectedTune,
                isLoadingFiles = isLoadingFiles,
                selectionMode = selectionMode,
                selectedTunes = selectedTunes.toSet(),
                onTuneSelected = { tune ->
                    val hasChanges = if (isCreatingNewTune) {
                        abcContent != "X:1\nT:New Tune\nM:4/4\nL:1/4\nK:C\n"
                    } else {
                        selectedTune != null && abcContent != selectedTune?.content
                    }

                    if (hasChanges) {
                        pendingTuneSelection = tune
                        showUnsavedChangesDialog = true
                    } else {
                        selectedTune = tune
                        abcContent = tune.content
                        selectedTuneTitle = tune.title
                        selectedTuneUri = tune.sourceUri.toString()
                        isCreatingNewTune = false
                        showPreview = true
                        isPlaying = false
                        isPaused = false
                        scope.launch { drawerState.close() }
                    }
                },
                onSelectionModeToggle = { enabled ->
                    selectionMode = enabled
                    if (!enabled) selectedTunes.clear()
                },
                onSelectionChange = { tune, selected ->
                    if (selected) selectedTunes.add(tune) else selectedTunes.remove(tune)
                },
                onBulkDelete = { showBulkDeleteDialog = true },
                onBulkCopy = { showCopyTargetChoiceDialog = true },
                onNewTune = { 
                    isCreatingNewTune = true
                    selectedTune = null
                    selectedTuneTitle = null
                    selectedTuneUri = null
                    abcContent = "X:1\nT:New Tune\nM:4/4\nL:1/4\nK:C\n"
                    showPreview = false
                    scope.launch { drawerState.close() }
                },
                onSetupClick = { showSetupDialog = true },
                onAboutClick = { showAboutDialog = true },
                onRefresh = { refreshCount++ },
                modifier = Modifier.width(leftPaneWidth.dp)
            )
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val isCompact = configuration.smallestScreenWidthDp < 600
            val isSideToolbar = isCompact && isLandscape
            val showTwoRowToolbar = isCompact && !isLandscape
            val showTopToolbar = !isSideToolbar

            // Main Content Wrapper
            Row(modifier = Modifier
                .padding(innerPadding)
                .imePadding() // Resizes layout to keep content above keyboard
                .fillMaxSize()
                .padding(if (isSideToolbar) 8.dp else 16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (showTopToolbar) {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Open Tune List")
                                }

                                if (!showTwoRowToolbar) {
                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (isCreatingNewTune) {
                                        Button(onClick = { createNewFileLauncher.launch("new_tune.abc") }) {
                                            Text("Save New")
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(onClick = { appendToFileLauncher.launch(arrayOf("*/*")) }) {
                                            Text("Append")
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(onClick = {
                                            showDiscardDialog = true
                                        }) {
                                            Text("Cancel")
                                        }
                                    } else if (selectedTune != null) {
                                        val hasChanges = abcContent != selectedTune?.content
                                        IconButton(
                                            onClick = { saveTune(selectedTune!!, abcContent) },
                                            enabled = hasChanges
                                        ) {
                                            Icon(
                                                Icons.Default.Save,
                                                contentDescription = "Save",
                                                tint = if (hasChanges) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(onClick = { showDeleteDialog = true }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                                        }

                                        // Playback controls
                                        Spacer(modifier = Modifier.width(16.dp))
                                        IconButton(onClick = {
                                            isPlaying = true
                                            isPaused = false
                                        }, enabled = !isPlaying || isPaused) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                        }
                                        IconButton(onClick = {
                                            isPaused = true
                                        }, enabled = isPlaying && !isPaused) {
                                            Icon(Icons.Default.Pause, contentDescription = "Pause")
                                        }
                                        IconButton(onClick = {
                                            isPlaying = false
                                            isPaused = false
                                        }, enabled = isPlaying) {
                                            Icon(Icons.Default.Stop, contentDescription = "Stop")
                                        }

                                        // Tempo Slider
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                            Text(
                                                text = tempo.toInt().toString(),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            Slider(
                                                value = tempo,
                                                onValueChange = {
                                                    tempo = it
                                                },
                                                onValueChangeFinished = {
                                                    activeTempo = tempo
                                                },
                                                valueRange = 40f..200f,
                                                modifier = Modifier.width(150.dp)
                                            )
                                        }
                                    }
                                } else {
                                    // Row 1 content for compact portrait
                                    if (isCreatingNewTune) {
                                        Text("New Tune", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
                                    } else if (selectedTune != null) {
                                        val hasChanges = abcContent != selectedTune?.content
                                        IconButton(
                                            onClick = { saveTune(selectedTune!!, abcContent) },
                                            enabled = hasChanges
                                        ) {
                                            Icon(
                                                Icons.Default.Save,
                                                contentDescription = "Save",
                                                tint = if (hasChanges) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                            )
                                        }
                                        IconButton(onClick = { showDeleteDialog = true }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    isPlaying = false
                                    isPaused = false
                                    showPreview = !showPreview
                                }, modifier = Modifier.padding(start = 8.dp)) {
                                    Icon(
                                        if (showPreview) Icons.Default.Edit else Icons.Default.Visibility,
                                        contentDescription = if (showPreview) "Edit" else "View"
                                    )
                                }
                            }

                            if (showTwoRowToolbar) {
                                if (isCreatingNewTune) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        Button(onClick = { createNewFileLauncher.launch("new_tune.abc") }) {
                                            Text("Save New", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Button(onClick = { appendToFileLauncher.launch(arrayOf("*/*")) }) {
                                            Text("Append", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Button(onClick = { showDiscardDialog = true }) {
                                            Text("Cancel", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                } else if (selectedTune != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = {
                                            isPlaying = true
                                            isPaused = false
                                        }, enabled = !isPlaying || isPaused) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                        }
                                        IconButton(onClick = {
                                            isPaused = true
                                        }, enabled = isPlaying && !isPaused) {
                                            Icon(Icons.Default.Pause, contentDescription = "Pause")
                                        }
                                        IconButton(onClick = {
                                            isPlaying = false
                                            isPaused = false
                                        }, enabled = isPlaying) {
                                            Icon(Icons.Default.Stop, contentDescription = "Stop")
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "Tempo: ${tempo.toInt()}",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            Slider(
                                                value = tempo,
                                                onValueChange = { tempo = it },
                                                onValueChangeFinished = { activeTempo = tempo },
                                                valueRange = 40f..200f
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (abcContent.isEmpty() && !isCreatingNewTune) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_app_logo),
                                    contentDescription = "App Logo",
                                    modifier = Modifier.size(128.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                if (directoryUri == null && selectedFilesUris.isEmpty()) {
                                    Text("Start by adding tunes", style = MaterialTheme.typography.titleLarge)
                                } else {
                                    Text("Open the menu and select a tune", modifier = Modifier.padding(16.dp))
                                }
                            }
                        }
                    } else if (showPreview) {
                        AbcVisualizer(abcContent, isPlaying = isPlaying, isPaused = isPaused, tempo = activeTempo.toInt(), 
                            onTempoDetected = { detectedBpm ->
                                // Use detected BPM if Q: field exists, otherwise default to 120
                                val hasExplicitTempo = abcContent.contains(Regex("(?m)^Q:"))
                                val finalBpm = if (hasExplicitTempo) detectedBpm.coerceIn(40, 200) else 120
                                tempo = finalBpm.toFloat()
                                activeTempo = finalBpm.toFloat()
                            },
                            modifier = Modifier.fillMaxSize())
                    } else {
                        if (isLandscape && isCompact) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                // Left pane: Rendered notation
                                Box(modifier = Modifier.weight(1f)) {
                                    AbcVisualizer(abcContent, isPlaying = isPlaying, isPaused = isPaused, tempo = activeTempo.toInt(), 
                                        onTempoDetected = { detectedBpm ->
                                            val hasExplicitTempo = abcContent.contains(Regex("(?m)^Q:"))
                                            val finalBpm = if (hasExplicitTempo) detectedBpm.coerceIn(40, 200) else 120
                                            tempo = finalBpm.toFloat()
                                            activeTempo = finalBpm.toFloat()
                                        },
                                        modifier = Modifier.fillMaxSize())
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                // Right pane: ABC Notation Editor
                                TextField(
                                    value = abcContent,
                                    onValueChange = { abcContent = it },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    label = { Text("Editor") },
                                    textStyle = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Top half: Rendered notation
                                Box(modifier = Modifier.weight(if (isCompact) 0.4f else 1f)) {
                                    AbcVisualizer(abcContent, isPlaying = isPlaying, isPaused = isPaused, tempo = activeTempo.toInt(), 
                                        onTempoDetected = { detectedBpm ->
                                            val hasExplicitTempo = abcContent.contains(Regex("(?m)^Q:"))
                                            val finalBpm = if (hasExplicitTempo) detectedBpm.coerceIn(40, 200) else 120
                                            tempo = finalBpm.toFloat()
                                            activeTempo = finalBpm.toFloat()
                                        },
                                        modifier = Modifier.fillMaxSize())
                                }
                            
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    thickness = 2.dp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                
                                // Bottom half: ABC Notation Editor
                                TextField(
                                    value = abcContent,
                                    onValueChange = { abcContent = it },
                                    modifier = Modifier.weight(if (isCompact) 0.6f else 1f).fillMaxWidth(),
                                    label = { Text("Editor") },
                                    textStyle = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                if (isSideToolbar) {
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .width(110.dp)
                            .fillMaxHeight()
                            .padding(start = 8.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
                        ) {
                            // Left Column: Library & Mode Actions
                            Column(
                                modifier = Modifier.fillMaxHeight().width(50.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Open Tune List")
                                }

                                if (isCreatingNewTune) {
                                    IconButton(onClick = { createNewFileLauncher.launch("new_tune.abc") }) {
                                        Icon(Icons.Default.Save, contentDescription = "Save New")
                                    }
                                    IconButton(onClick = { showDiscardDialog = true }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Cancel")
                                    }
                                } else if (selectedTune != null) {
                                    val hasChanges = abcContent != selectedTune?.content
                                    IconButton(
                                        onClick = { saveTune(selectedTune!!, abcContent) },
                                        enabled = hasChanges
                                    ) {
                                        Icon(
                                            Icons.Default.Save,
                                            contentDescription = "Save",
                                            tint = if (hasChanges) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    IconButton(onClick = { showDeleteDialog = true }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    }
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                IconButton(onClick = {
                                    isPlaying = false
                                    isPaused = false
                                    showPreview = !showPreview
                                }) {
                                    Icon(
                                        if (showPreview) Icons.Default.Edit else Icons.Default.Visibility,
                                        contentDescription = if (showPreview) "Edit" else "View"
                                    )
                                }
                            }

                            // Right Column: Playback Actions & Tempo
                            Column(
                                modifier = Modifier.fillMaxHeight().width(50.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
                            ) {
                                if (!isCreatingNewTune && selectedTune != null) {
                                    // Tempo Controls
                                    IconButton(onClick = { 
                                        tempo = (tempo + 5f).coerceAtMost(200f)
                                        activeTempo = tempo
                                    }) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase Tempo")
                                    }
                                    
                                    Text(
                                        text = tempo.toInt().toString(),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )

                                    IconButton(onClick = { 
                                        tempo = (tempo - 5f).coerceAtLeast(40f)
                                        activeTempo = tempo
                                    }) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease Tempo")
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    IconButton(onClick = {
                                        isPlaying = true
                                        isPaused = false
                                    }, enabled = !isPlaying || isPaused) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                    }
                                    IconButton(onClick = {
                                        isPaused = true
                                    }, enabled = isPlaying && !isPaused) {
                                        Icon(Icons.Default.Pause, contentDescription = "Pause")
                                    }
                                    IconButton(onClick = {
                                        isPlaying = false
                                        isPaused = false
                                    }, enabled = isPlaying) {
                                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                                    }
                                }
                            }
                        }
                    }
                }
            }

                if (showDiscardDialog) {
                    DiscardEditsDialog(
                        onConfirm = {
                            showDiscardDialog = false
                            isCreatingNewTune = false
                            abcContent = ""
                        },
                        onDismiss = { showDiscardDialog = false }
                    )
                }

                if (showDeleteDialog) {
                    DeleteTuneDialog(
                        tuneTitle = selectedTune?.title ?: "",
                        onConfirm = {
                            selectedTune?.let { deleteTune(it) }
                            showDeleteDialog = false
                        },
                        onDismiss = { showDeleteDialog = false }
                    )
                }

                if (showUnsavedChangesDialog) {
                    UnsavedChangesDialog(
                        onConfirm = {
                            showUnsavedChangesDialog = false
                            pendingTuneSelection?.let { tune ->
                                selectedTune = tune
                                abcContent = tune.content
                                selectedTuneTitle = tune.title
                                selectedTuneUri = tune.sourceUri.toString()
                                isCreatingNewTune = false
                                showPreview = true
                                isPlaying = false
                                isPaused = false
                                scope.launch { drawerState.close() }
                            }
                        },
                        onDismiss = { showUnsavedChangesDialog = false }
                    )
                }

                if (showSetupDialog) {
                    SetupStorageDialog(
                        onAddFolder = { 
                            reopenSetupAfterPickerCancel = true
                            showSetupDialog = false
                            openDirectoryLauncher.launch(null) 
                        },
                        onAddFiles = { 
                            reopenSetupAfterPickerCancel = true
                            showSetupDialog = false
                            openFilesLauncher.launch(arrayOf("*/*")) 
                        },
                        onClearLibrary = {
                            reopenSetupAfterPickerCancel = false
                            showSetupDialog = false
                            directoryUri = null
                            selectedFilesUris.clear()
                            parsedTunes.clear()
                            selectedTune = null
                            selectedTuneTitle = null
                            selectedTuneUri = null
                            abcContent = ""
                            prefs.edit().remove(KEY_DIRECTORY_URI).remove(KEY_SELECTED_FILES).apply()
                        },
                        onDismiss = { showSetupDialog = false }
                    )
                }

                if (showAboutDialog) {
                    AboutDialog(
                        onDismiss = { showAboutDialog = false }
                    )
                }

                if (showBulkDeleteDialog) {
                    BulkDeleteConfirmationDialog(
                        count = selectedTunes.size,
                        onConfirm = {
                            showBulkDeleteDialog = false
                            scope.launch {
                                AbcHandler.deleteTunes(context, selectedTunes.toList())
                                selectionMode = false
                                selectedTunes.clear()
                                refreshCount++
                            }
                        },
                        onDismiss = { showBulkDeleteDialog = false }
                    )
                }

                if (showCopyTargetChoiceDialog) {
                    CopyTargetChoiceDialog(
                        onNewFile = {
                            showCopyTargetChoiceDialog = false
                            createTargetFileLauncher.launch("selected_tunes.abc")
                        },
                        onExistingFile = {
                            showCopyTargetChoiceDialog = false
                            selectTargetFileLauncher.launch(arrayOf("*/*"))
                        },
                        onDismiss = { showCopyTargetChoiceDialog = false }
                    )
                }

                duplicateTuneRequest?.let { (tune, deferred) ->
                    DuplicateTuneDialog(
                        tuneTitle = tune.title,
                        onOverwrite = {
                            duplicateTuneRequest = null
                            deferred.complete(true)
                        },
                        onSkip = {
                            duplicateTuneRequest = null
                            deferred.complete(false)
                        },
                        onCancel = {
                            duplicateTuneRequest = null
                            deferred.complete(null)
                        }
                    )
                }
            }
        }
    }
