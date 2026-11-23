package com.stralarp.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stralarp.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    var showCreateScreen by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importGpx(it) }
    }

    if (showCreateScreen) {
        CreateRouteScreen(
            viewModel = viewModel,
            onBack = { showCreateScreen = false }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Stralarp") })
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Stralarp", style = MaterialTheme.typography.headlineLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "GPS Route Simulator & GPX Editor",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(32.dp))

                if (state.currentRoute.points.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                state.currentRoute.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${state.currentRoute.points.size} points")
                            Text("${"%.2f".format(state.currentRoute.totalDistanceKm)} km")
                            if (state.currentRoute.elevationGain > 0) {
                                Text("↑ ${"%.0f".format(state.currentRoute.elevationGain)} m")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                Button(
                    onClick = { showCreateScreen = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Map, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create / Edit Route")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        importLauncher.launch(
                            arrayOf(
                                "application/gpx+xml",
                                "application/xml",
                                "text/xml",
                                "*/*"
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Import GPX")
                }

                if (state.isLoading) {
                    Spacer(modifier = Modifier.height(24.dp))
                    CircularProgressIndicator()
                }

                state.error?.let { error ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
