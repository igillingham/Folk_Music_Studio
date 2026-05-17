package net.iangillingham.music_abc

import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.launch
import net.iangillingham.music_abc.logic.AbcHandler
import net.iangillingham.music_abc.model.AbcTune
import net.iangillingham.music_abc.ui.AbcVisualizer
import net.iangillingham.music_abc.ui.components.*
import net.iangillingham.music_abc.ui.theme.Music_ABCTheme
import java.io.BufferedReader
import java.io.InputStreamReader

private const val PREFS_NAME = "MusicAbcPrefs"
private const val KEY_DIRECTORY_URI = "directoryUri"
private const val KEY_SELECTED_FILES = "selectedFiles"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            Music_ABCTheme {
                Music_ABCApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun Music_ABCApp() {
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
    var pendingTuneSelection by remember { mutableStateOf<AbcTune?>(null) }
    
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
                        allParsedTunes.addAll(AbcHandler.parseAbcContent(content, file.uri))
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
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                prefs.edit().putString(KEY_DIRECTORY_URI, it.toString()).apply()
                directoryUri = it.toString()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val openFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
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
                onRefresh = { refreshCount++ },
                modifier = Modifier.width(leftPaneWidth.dp)
            )
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            // Main Content
            Column(modifier = Modifier
                .padding(innerPadding)
                .imePadding() // Resizes layout to keep content above keyboard
                .fillMaxSize()
                .padding(16.dp)
            ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Tune List")
                        }
                        
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

                    if (abcContent.isEmpty() && !isCreatingNewTune) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_app_logo),
                                    contentDescription = "App Logo",
                                    modifier = Modifier.size(128.dp),
                                    tint = MaterialTheme.colorScheme.primary
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
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top half: Rendered notation
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
                        
                        // Visible horizontal line
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            thickness = 2.dp,
                            color = MaterialTheme.colorScheme.outline
                        )
                        
                        // Bottom half: ABC Notation Editor
                        TextField(
                            value = abcContent,
                            onValueChange = { abcContent = it },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            label = { Text("ABC Notation Editor") }
                        )
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
                            showSetupDialog = false
                            openDirectoryLauncher.launch(null) 
                        },
                        onAddFiles = { 
                            showSetupDialog = false
                            openFilesLauncher.launch(arrayOf("*/*")) 
                        },
                        onDismiss = { showSetupDialog = false }
                    )
                }
            }
        }
    }
}
