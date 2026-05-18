/*
 * Copyright (c) 2026 Ian Gillingham
 * Licensed under the GNU General Public License v3.0
 */
package net.iangillingham.music_abc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.iangillingham.music_abc.model.AbcTune

val PastelColors = listOf(
    Color(0xFFE3F2FD), // Light Blue
    Color(0xFFE8F5E9), // Light Green
    Color(0xFFfcb8b8), // Light Red
    Color(0xFFFFF9C4), // Light Yellow
    Color(0xFFF3E5F5), // Light Lavender
    Color(0xFFFFc18a), // Light Orange
    Color(0xFFb9fcfc), // Light Cyan
)

@Composable
fun TuneLibraryDrawer(
    parsedTunes: List<AbcTune>,
    selectedTune: AbcTune?,
    isLoadingFiles: Boolean,
    onTuneSelected: (AbcTune) -> Unit,
    onNewTune: () -> Unit,
    onSetupClick: () -> Unit,
    onAboutClick: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fileColorMap = remember(parsedTunes.toList()) {
        parsedTunes.map { it.sourceUri }.distinct().mapIndexed { index, uri ->
            uri to PastelColors[index % PastelColors.size]
        }.toMap()
    }

    ModalDrawerSheet(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp).fillMaxHeight()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Tune Library",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onSetupClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Setup Storage")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onNewTune,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("New Tune", style = MaterialTheme.typography.labelMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
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
                } else {
                    Button(
                        onClick = onRefresh,
                        modifier = Modifier.height(24.dp).padding(0.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("Refresh", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            
            HorizontalDivider()
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(parsedTunes.size) { index ->
                    val tune = parsedTunes[index]
                    val isSelected = selectedTune == tune
                    val backgroundColor = fileColorMap[tune.sourceUri] ?: Color.Transparent
                    
                    Column(modifier = Modifier.background(backgroundColor)) {
                        // Add filename header if file changes
                        if (index == 0 || parsedTunes[index - 1].sourceUri != tune.sourceUri) {
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(top = 8.dp),
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                            Text(
                                text = tune.sourceFileName,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 8.dp, end = 8.dp)
                            )
                        }

                        Text(
                            text = tune.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTuneSelected(tune) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            style = if (isSelected) {
                                MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary)
                            } else {
                                MaterialTheme.typography.bodyMedium
                            }
                        )
                    }
                }
            }

            HorizontalDivider()
            TextButton(
                onClick = onAboutClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("About")
            }
        }
    }
}
