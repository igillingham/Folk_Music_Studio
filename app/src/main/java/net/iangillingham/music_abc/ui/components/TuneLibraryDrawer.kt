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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
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
    Color(0xFFF0F7FF), // Extremely Light Blue
    Color(0xFFF2FFF2), // Extremely Light Green
    Color(0xFFFFECEC), // Extremely Light Red
    Color(0xFFFFFFF0), // Extremely Light Yellow
    Color(0xFFF9F2FF), // Extremely Light Lavender
    Color(0xFFFFF5E6), // Extremely Light Orange
    Color(0xFFE6FFFF), // Extremely Light Cyan
)

@Composable
fun TuneLibraryDrawer(
    parsedTunes: List<AbcTune>,
    selectedTune: AbcTune?,
    isLoadingFiles: Boolean,
    selectionMode: Boolean,
    selectedTunes: Set<AbcTune>,
    onTuneSelected: (AbcTune) -> Unit,
    onSelectionModeToggle: (Boolean) -> Unit,
    onSelectionChange: (AbcTune, Boolean) -> Unit,
    onBulkDelete: () -> Unit,
    onBulkCopy: () -> Unit,
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
                if (!selectionMode) {
                    IconButton(onClick = onSetupClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Setup Storage")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (selectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${selectedTunes.size} selected", style = MaterialTheme.typography.bodyMedium)
                    Row {
                        IconButton(onClick = onBulkCopy, enabled = selectedTunes.isNotEmpty()) {
                            Icon(androidx.compose.material.icons.Icons.Default.ContentCopy, contentDescription = "Copy Selected")
                        }
                        IconButton(onClick = onBulkDelete, enabled = selectedTunes.isNotEmpty()) {
                            Icon(androidx.compose.material.icons.Icons.Default.Delete, contentDescription = "Delete Selected")
                        }
                        IconButton(onClick = { onSelectionModeToggle(false) }) {
                            Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Exit Selection")
                        }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onNewTune,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("New Tune", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { onSelectionModeToggle(true) }
                    ) {
                        Text("Select", style = MaterialTheme.typography.labelMedium)
                    }
                }
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
                } else if (!selectionMode) {
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
                    val isChecked = selectedTunes.contains(tune)
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
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = Color.Black
                                ),
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 8.dp, end = 8.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    if (selectionMode) {
                                        onSelectionChange(tune, !isChecked)
                                    } else {
                                        onTuneSelected(tune) 
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            if (selectionMode) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { onSelectionChange(tune, it) }
                                )
                            }
                            Text(
                                text = tune.title,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                style = if (isSelected && !selectionMode) {
                                    MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary)
                                } else {
                                    MaterialTheme.typography.bodyMedium.copy(color = Color.Black)
                                }
                            )
                        }
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
