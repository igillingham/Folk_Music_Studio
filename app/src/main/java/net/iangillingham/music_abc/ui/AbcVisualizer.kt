/*
 * Copyright (c) 2026 Ian Gillingham
 * Licensed under the Open Source License
 */
package net.iangillingham.music_abc.ui

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun AbcVisualizer(
    abcCode: String,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    isPaused: Boolean = false,
    tempo: Int = 120,
    onTempoDetected: (Int) -> Unit = {}
) {
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
                    webView.evaluateJavascript("if (typeof play === 'function') play(${tempo});", null)
                }
            } else {
                webView.evaluateJavascript("if (typeof stop === 'function') stop();", null)
            }
        },
        modifier = modifier
    )
}
