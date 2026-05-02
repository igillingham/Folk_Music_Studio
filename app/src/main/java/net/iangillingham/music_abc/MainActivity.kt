package net.iangillingham.music_abc

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import net.iangillingham.music_abc.ui.theme.Music_ABCTheme
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

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
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var abcContent by rememberSaveable { mutableStateOf("X:1\nT:Sample Tune\nM:4/4\nL:1/4\nK:C\nC D E F | G A B c |") }
    var currentUri by rememberSaveable { mutableStateOf<String?>(null) }
    var showPreview by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            currentUri = it.toString()
            context.contentResolver.openInputStream(it)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    abcContent = reader.readText()
                }
            }
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            currentUri = it.toString()
            context.contentResolver.openOutputStream(it)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(abcContent)
                }
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
            Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Button(onClick = {
                        abcContent = "X:1\nT:New Tune\nM:4/4\nL:1/4\nK:C\n"
                        currentUri = null
                    }) {
                        Text("New")
                    }
                    Button(onClick = { openDocumentLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.padding(start = 8.dp)) {
                        Text("Open")
                    }
                    if (currentUri != null) {
                        Button(onClick = {
                            try {
                                val uri = Uri.parse(currentUri)
                                context.contentResolver.openOutputStream(uri)?.use { os ->
                                    os.write(abcContent.toByteArray())
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, modifier = Modifier.padding(start = 8.dp)) {
                            Text("Save")
                        }
                    }
                    Button(onClick = { createDocumentLauncher.launch("music.abc") }, modifier = Modifier.padding(start = 8.dp)) {
                        Text("Save As")
                    }
                    Button(onClick = { showPreview = !showPreview }, modifier = Modifier.padding(start = 8.dp)) {
                        Text(if (showPreview) "Edit" else "View")
                    }
                }
                
                if (showPreview) {
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