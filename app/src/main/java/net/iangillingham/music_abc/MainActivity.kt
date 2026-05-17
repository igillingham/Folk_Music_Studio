package net.iangillingham.music_abc

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.toMutableStateList
import androidx.compose.material3.rememberDrawerState
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import net.iangillingham.music_abc.ui.theme.Music_ABCTheme
import java.io.BufferedReader
import java.io.InputStreamReader

data class AbcTune(
    val title: String,
    val content: String,
    val sourceUri: Uri,
    val originalContent: String // Used to identify the tune in the file for replacement
)

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

    fun parseAbcContent(content: String, sourceUri: Uri): List<AbcTune> {
        val tunes = mutableListOf<AbcTune>()
        val parts = content.split(Regex("(?m)^X:"))
        parts.forEach { part ->
            if (part.trim().isNotEmpty()) {
                val fullTuneContent = "X:$part"
                val titleMatch = Regex("(?m)^T:(.*)").find(fullTuneContent)
                val title = titleMatch?.groupValues?.get(1)?.trim() ?: "Untitled"
                tunes.add(AbcTune(title, fullTuneContent, sourceUri, fullTuneContent))
            }
        }
        return tunes
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
                        allParsedTunes.addAll(parseAbcContent(content, file.uri))
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
        try {
            val sourceUri = tune.sourceUri
            val currentFileContent = context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readText()
            } ?: return

            val updatedFileContent = currentFileContent.replace(tune.originalContent, newContent)

            context.contentResolver.openOutputStream(sourceUri, "wt")?.use { outputStream ->
                outputStream.write(updatedFileContent.toByteArray())
            }

            val index = parsedTunes.indexOf(tune)
            if (index != -1) {
                val updatedTune = tune.copy(content = newContent, originalContent = newContent)
                parsedTunes[index] = updatedTune
                selectedTune = updatedTune
                abcContent = newContent
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteTune(tune: AbcTune) {
        try {
            val sourceUri = tune.sourceUri
            val currentFileContent = context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readText()
            } ?: return

            // Attempt to remove the tune and leading/trailing whitespace to avoid leaving gaps
            val updatedFileContent = currentFileContent.replace(tune.originalContent, "").trim()

            context.contentResolver.openOutputStream(sourceUri, "wt")?.use { outputStream ->
                outputStream.write(updatedFileContent.toByteArray())
            }

            parsedTunes.remove(tune)
            selectedTune = null
            abcContent = ""
            selectedTuneTitle = null
            selectedTuneUri = null
        } catch (e: Exception) {
            e.printStackTrace()
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
            ModalDrawerSheet(
                modifier = Modifier.width(leftPaneWidth.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxHeight()) {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Tune Library",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showSetupDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Setup Storage")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { 
                            isCreatingNewTune = true
                            selectedTune = null
                            selectedTuneTitle = null
                            selectedTuneUri = null
                            abcContent = "X:1\nT:New Tune\nM:4/4\nL:1/4\nK:C\n"
                            showPreview = false
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("New Tune", style = MaterialTheme.typography.labelMedium)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(
                            "Tunes",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (isLoadingFiles) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else if (directoryUri != null || selectedFilesUris.isNotEmpty()) {
                            Button(
                                onClick = { refreshCount++ },
                                modifier = Modifier.height(24.dp).padding(0.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                            ) {
                                Text("Refresh", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    
                    HorizontalDivider()
                    
                    LazyColumn {
                        items(parsedTunes) { tune ->
                            val isSelected = selectedTune == tune
                            Text(
                                text = tune.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
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
                                    }
                                    .padding(8.dp),
                                style = if (isSelected) {
                                    MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary)
                                } else {
                                    MaterialTheme.typography.bodyMedium
                                }
                            )
                        }
                    }
                }
            }
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
                    AlertDialog(
                        onDismissRequest = { showDiscardDialog = false },
                        title = { Text("Discard Edits?") },
                        text = { Text("Are you sure you want to discard your changes to this new tune?") },
                        confirmButton = {
                            TextButton(onClick = {
                                showDiscardDialog = false
                                isCreatingNewTune = false
                                abcContent = ""
                            }) {
                                Text("Discard")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDiscardDialog = false }) {
                                Text("Continue Editing")
                            }
                        }
                    )
                }

                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        title = { Text("Delete Tune?") },
                        text = { Text("Are you sure you want to permanently delete '${selectedTune?.title}' from the file?") },
                        confirmButton = {
                            TextButton(onClick = {
                                selectedTune?.let { deleteTune(it) }
                                showDeleteDialog = false
                            }) {
                                Text("Delete")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                if (showUnsavedChangesDialog) {
                    AlertDialog(
                        onDismissRequest = { showUnsavedChangesDialog = false },
                        title = { Text("Unsaved Changes") },
                        text = { Text("You have unsaved changes. Do you want to discard them and switch to another tune?") },
                        confirmButton = {
                            TextButton(onClick = {
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
                            }) {
                                Text("Discard Changes")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showUnsavedChangesDialog = false }) {
                                Text("Keep Editing")
                            }
                        }
                    )
                }

                if (showSetupDialog) {
                    AlertDialog(
                        onDismissRequest = { showSetupDialog = false },
                        title = { Text("Setup Storage") },
                        text = { 
                            Column {
                                Text("Add folders or specific files to your tune library.")
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { 
                                        showSetupDialog = false
                                        openDirectoryLauncher.launch(null) 
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Add Folder")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { 
                                        showSetupDialog = false
                                        openFilesLauncher.launch(arrayOf("*/*")) 
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Add Files (Google Drive)")
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showSetupDialog = false }) {
                                Text("Close")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AbcVisualizer(abcCode: String, modifier: Modifier = Modifier, isPlaying: Boolean = false, isPaused: Boolean = false, tempo: Int = 120, onTempoDetected: (Int) -> Unit = {}) {
    val escapedAbc = abcCode.replace("\\", "\\\\").replace("`", "\\`").replace("$", "\\$")
    
    val html = remember {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <script src="file:///android_asset/abcjs-basic-min.js" type="text/javascript"></script>
            <style>
                body { margin: 0; padding: 10px; font-family: sans-serif; background-color: white; }
                #paper { width: 100%; min-height: 100px; }
                .error { color: red; font-size: 12px; }
                #loading { color: #666; font-style: italic; }
            </style>
        </head>
        <body>
            <div id="loading">Loading renderer...</div>
            <div id="paper"></div>
            <div id="errors" class="error"></div>
            <script type="text/javascript">
                let visualObj;
                let synthControl;
                let audioContext;
                let isReady = false;
                let currentBpm = 120;
                let currentAbc = "";
                let isInitializing = false;
                let currentBeat = 0;
                let timingCallbacks;

                window.onload = function() {
                    document.getElementById("loading").innerHTML = "Waiting for ABCJS...";
                    checkAbcjs();
                };

                function checkAbcjs() {
                    if (typeof ABCJS !== 'undefined') {
                        isReady = true;
                        document.getElementById("loading").style.display = "none";
                        console.log("ABCJS Ready");
                        // Check if we have pending content to render
                        const pendingAbc = window.pendingAbc;
                        if (pendingAbc) {
                            render(pendingAbc);
                            delete window.pendingAbc;
                        }
                    } else {
                        setTimeout(checkAbcjs, 100);
                    }
                }

                function render(abc) {
                    if (!isReady) {
                        window.pendingAbc = abc;
                        return;
                    }
                    if (abc === currentAbc) return; // Don't re-render if content is identical
                    currentAbc = abc;
                    currentBeat = 0;
                    if (timingCallbacks) {
                        timingCallbacks.stop();
                        timingCallbacks = null;
                    }
                    
                    console.log("Rendering ABC content");
                    document.getElementById("errors").innerHTML = "";
                    try {
                        visualObj = ABCJS.renderAbc("paper", abc, { 
                            responsive: "resize",
                            paddingbottom: 30
                        });
                        if (synthControl) {
                            synthControl.stop();
                            synthControl = null;
                        }

                        // Notify Android about detected tempo
                        if (visualObj && visualObj[0]) {
                            const beatsPerMeasure = visualObj[0].getBeatsPerMeasure();
                            const msPerMeasure = visualObj[0].millisecondsPerMeasure();
                            if (msPerMeasure > 0) {
                                const detectedBpm = Math.round((beatsPerMeasure / msPerMeasure) * 60000);
                                if (window.AndroidInterface) {
                                    window.AndroidInterface.onTempoDetected(detectedBpm);
                                }
                            }
                        }
                    } catch (e) {
                        document.getElementById("errors").innerHTML = "Render error: " + e.message;
                    }
                }

                async function play(bpm) {
                    if (!visualObj || !visualObj[0] || isInitializing) return;
                    
                    const seekToBeat = currentBeat;

                    // If tempo changed while playing, we need to restart the synth
                    // but we'll use a small threshold to avoid jitter
                    if (synthControl && Math.abs(bpm - currentBpm) > 1) {
                        synthControl.stop();
                        synthControl = null;
                        if (timingCallbacks) {
                            timingCallbacks.stop();
                            timingCallbacks = null;
                        }
                    }

                    if (isPlaying(synthControl) && bpm === currentBpm) return;

                    currentBpm = bpm;

                    try {
                        if (!audioContext) {
                            audioContext = new (window.AudioContext || window.webkitAudioContext)();
                        }
                        if (audioContext.state === 'suspended') {
                            await audioContext.resume();
                        }
                        
                        if (!synthControl) {
                            isInitializing = true;
                            synthControl = new ABCJS.synth.CreateSynth();
                            const beatsPerMeasure = visualObj[0].getBeatsPerMeasure();
                            const msPerMeasure = (beatsPerMeasure / bpm) * 60000;
                            
                            await synthControl.init({
                                audioContext: audioContext,
                                visualObj: visualObj[0],
                                millisecondsPerMeasure: msPerMeasure
                            });
                            await synthControl.prime();
                            
                            if (seekToBeat > 0) {
                                await synthControl.seek(seekToBeat, "beats");
                            }
                            
                            isInitializing = false;
                        }

                        if (!timingCallbacks) {
                            timingCallbacks = new ABCJS.TimingCallbacks(visualObj[0], {
                                beatCallback: function(beat) {
                                    currentBeat = beat;
                                },
                                eventCallback: function(event) {
                                    if (!event) {
                                        // End of tune
                                        stop();
                                    }
                                }
                            });
                        }

                        synthControl.start();
                        timingCallbacks.start();
                    } catch (e) {
                        isInitializing = false;
                        document.getElementById("errors").innerHTML = "Audio error: " + e.message;
                    }
                }

                function isPlaying(synth) {
                    return synth && synth.isRunning;
                }

                function pause() {
                    if (synthControl) synthControl.pause();
                    if (timingCallbacks) timingCallbacks.pause();
                }

                function stop() {
                    if (synthControl) {
                        synthControl.stop();
                        synthControl = null; // Ensure fresh start next time
                    }
                    if (timingCallbacks) {
                        timingCallbacks.stop();
                        timingCallbacks = null;
                    }
                    currentBeat = 0;
                }
            </script>
        </body>
        </html>
    """.trimIndent()
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun onTempoDetected(bpm: Int) {
                        post { onTempoDetected(bpm) }
                    }
                }, "AndroidInterface")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        // Initial render after page load
                        view?.evaluateJavascript("render(`${escapedAbc}`);", null)
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        android.util.Log.d("AbcVisualizer", "${consoleMessage?.message()}")
                        return true
                    }
                }
                loadDataWithBaseURL("https://localhost", html, "text/html", "utf-8", null)
            }
        },
        update = { webView ->
            // Update the notation content via JS
            webView.evaluateJavascript("if (typeof render === 'function') { render(`${escapedAbc}`); }", null)
            
            // Handle playback state
            if (isPlaying) {
                if (isPaused) {
                    webView.evaluateJavascript("if (typeof pause === 'function') pause();", null)
                } else {
                    webView.evaluateJavascript("if (typeof play === 'function') play(${tempo.toInt()});", null)
                }
            } else {
                webView.evaluateJavascript("if (typeof stop === 'function') stop();", null)
            }
        },
        modifier = modifier
    )
}
