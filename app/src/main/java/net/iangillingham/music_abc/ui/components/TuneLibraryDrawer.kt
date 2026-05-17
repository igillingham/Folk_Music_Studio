package net.iangillingham.music_abc.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.iangillingham.music_abc.model.AbcTune

@Composable
fun TuneLibraryDrawer(
    parsedTunes: List<AbcTune>,
    selectedTune: AbcTune?,
    isLoadingFiles: Boolean,
    onTuneSelected: (AbcTune) -> Unit,
    onNewTune: () -> Unit,
    onSetupClick: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            
            LazyColumn {
                items(parsedTunes) { tune ->
                    val isSelected = selectedTune == tune
                    Text(
                        text = tune.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTuneSelected(tune) }
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
