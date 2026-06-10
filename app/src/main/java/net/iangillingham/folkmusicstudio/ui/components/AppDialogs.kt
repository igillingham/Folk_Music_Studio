/*
 * Copyright (c) 2026 Ian Gillingham
 * Licensed under the GNU General Public License v3.0
 */
package net.iangillingham.folkmusicstudio.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.iangillingham.folkmusicstudio.model.AbcTune

@Composable
fun DiscardEditsDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discard Edits?") },
        text = { Text("Are you sure you want to discard your changes to this new tune?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Discard")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Continue Editing")
            }
        }
    )
}

@Composable
fun DeleteTuneDialog(
    tuneTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Tune?") },
        text = { Text("Are you sure you want to permanently delete '$tuneTitle' from the file?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun UnsavedChangesDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unsaved Changes") },
        text = { Text("You have unsaved changes. Do you want to discard them and switch to another tune?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Discard Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep Editing")
            }
        }
    )
}

@Composable
fun SetupStorageDialog(
    onAddFolder: () -> Unit,
    onAddFiles: () -> Unit,
    onClearLibrary: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Setup Storage") },
        text = { 
            Column {
                Text("Manage your tune library sources.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAddFolder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Folder")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onAddFiles,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Files (Google Drive)")
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = onClearLibrary,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear Library")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun AboutDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About") },
        text = {
            Column {
                Text("Music ABC Notation Editor")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Author: Ian Gillingham")
                Text("Copyright © 2026 Ian Gillingham")
                Spacer(modifier = Modifier.height(8.dp))
                Text("This application is free software released under the GNU GPL v3 license.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun BulkDeleteConfirmationDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Multiple Tunes") },
        text = { Text("Are you sure you want to permanently delete $count selected tunes from their source files?") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DuplicateTuneDialog(
    tuneTitle: String,
    onOverwrite: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Tune Already Exists") },
        text = { Text("The tune '$tuneTitle' already exists in the target file. Would you like to overwrite it or skip it?") },
        confirmButton = {
            Row {
                TextButton(onClick = onOverwrite) {
                    Text("Overwrite")
                }
                TextButton(onClick = onSkip) {
                    Text("Skip")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CopyTargetChoiceDialog(
    onNewFile: () -> Unit,
    onExistingFile: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copy Tunes") },
        text = { Text("Do you want to copy the selected tunes to a new file or append them to an existing file?") },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onNewFile, modifier = Modifier.fillMaxWidth()) {
                    Text("Create New File")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onExistingFile, modifier = Modifier.fillMaxWidth()) {
                    Text("Select Existing File")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
