package net.iangillingham.music_abc

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
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
    
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    
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
    
    var leftPaneWidth by rememberSaveable { mutableStateOf(250f) }
    val density = LocalDensity.current
    
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

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            painterResource(it.icon),
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Row(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                // Left Pane: Tune List
                Surface(
                    modifier = Modifier
                        .width(leftPaneWidth.dp)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Button(
                            onClick = { openDirectoryLauncher.launch(null) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add Folder", style = MaterialTheme.typography.labelMedium)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = { openFilesLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add Files (Google Drive)", style = MaterialTheme.typography.labelMedium)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
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
                                            selectedTune = tune
                                            abcContent = tune.content
                                            selectedTuneTitle = tune.title
                                            selectedTuneUri = tune.sourceUri.toString()
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

                // Splitter handle
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(10.dp) // Increased hit area for easier touch
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                with(density) {
                                    val newWidth = leftPaneWidth + dragAmount.toDp().value
                                    leftPaneWidth = newWidth.coerceIn(150f, 600f)
                                }
                            }
                        }
                        .padding(horizontal = 4.dp) // Makes the visual line thinner than the hit area
                        .background(MaterialTheme.colorScheme.outline) // Higher contrast color
                )

                // Main Content
                Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        if (selectedTune != null) {
                            Button(onClick = { saveTune(selectedTune!!, abcContent) }) {
                                Text("Save")
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Button(onClick = { showPreview = !showPreview }, modifier = Modifier.padding(start = 8.dp)) {
                            Text(if (showPreview) "Edit" else "View")
                        }
                    }

                    if (abcContent.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                if (directoryUri == null && selectedFilesUris.isEmpty()) {
                                    Text("Start by adding tunes", style = MaterialTheme.typography.titleLarge)
                                } else {
                                    Text("Select a tune from the list", modifier = Modifier.padding(16.dp))
                                }
                            }
                        }
                    } else if (showPreview) {
                        AbcVisualizer(abcContent, modifier = Modifier.fillMaxSize())
                    } else {
                        TextField(
                            value = abcContent,
                            onValueChange = { abcContent = it },
                            modifier = Modifier.fillMaxSize(),
                            label = { Text("ABC Notation Editor") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AbcVisualizer(abcCode: String, modifier: Modifier = Modifier) {
    val escapedAbc = abcCode.replace("\\", "\\\\").replace("`", "\\`").replace("$", "\\$")
    val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <script src="https://cdn.jsdelivr.net/npm/abcjs@latest/dist/abcjs-basic-min.js" type="text/javascript"></script>
            <style>
                body { margin: 0; padding: 10px; }
                #paper { width: 100%; }
            </style>
        </head>
        <body>
            <div id="paper"></div>
            <script type="text/javascript">
                function render() {
                    if (typeof ABCJS !== 'undefined') {
                        ABCJS.renderAbc("paper", `$escapedAbc`, { responsive: "resize" });
                    } else {
                        setTimeout(render, 100);
                    }
                }
                render();
            </script>
        </body>
        </html>
    """.trimIndent()

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                webViewClient = WebViewClient()
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL("https://localhost", html, "text/html", "utf-8", null)
        },
        modifier = modifier
    )
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    HOME("Home", R.drawable.ic_home),
    FAVORITES("Favorites", R.drawable.ic_favorite),
    PROFILE("Profile", R.drawable.ic_account_box),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Music_ABCTheme {
        Greeting("Android")
    }
}
