package com.stralarp.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.stralarp.app.ui.components.OsmdroidMap
import com.stralarp.app.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRouteScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Export launcher (Save As)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri: Uri? ->
        uri?.let { viewModel.writeToDocument(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Route") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Snap", style = MaterialTheme.typography.labelMedium)
                        Switch(
                            checked = state.snapToRoad,
                            onCheckedChange = { viewModel.setSnapToRoad(it) }
                        )
                    }
                }
            )
        },
        bottomBar = {
            Column {
                // Stats bar
                if (state.currentRoute.points.isNotEmpty()) {
                    Surface(
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${"%.2f".format(state.currentRoute.totalDistanceKm)} km")
                            Text("${state.currentRoute.points.size} pts")
                            if (state.currentRoute.elevationGain > 0) {
                                Text("↑${"%.0f".format(state.currentRoute.elevationGain)}m")
                            }
                        }
                    }
                }

                // Tools + Export
                Surface(
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        // Tool buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            IconButton(
                                onClick = { viewModel.undoLastPoint() },
                                enabled = state.currentRoute.points.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Undo, contentDescription = "Undo")
                            }
                            IconButton(
                                onClick = { viewModel.clearRoute() },
                                enabled = state.currentRoute.points.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear")
                            }
                            IconButton(
                                onClick = { viewModel.reverseRoute() },
                                enabled = state.currentRoute.points.size >= 2
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = "Reverse")
                            }
                            IconButton(
                                onClick = { viewModel.closeLoop() },
                                enabled = state.currentRoute.points.size >= 2
                            ) {
                                Icon(Icons.Default.Loop, contentDescription = "Close Loop")
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Export / Share row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Save GPX
                            Button(
                                onClick = {
                                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                    val name = "Stralarp_${date}.gpx"
                                    exportLauncher.launch(name)
                                },
                                enabled = state.currentRoute.points.size >= 2,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save GPX")
                            }

                            // Share GPX
                            OutlinedButton(
                                onClick = {
                                    viewModel.exportGpxAndShare(context)
                                },
                                enabled = state.currentRoute.points.size >= 2,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Share")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            OsmdroidMap(
                modifier = Modifier.fillMaxSize(),
                points = state.currentRoute.points,
                onMapClick = { lat, lon -> viewModel.addWaypoint(lat, lon) },
                centerOnRoute = true
            )

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            state.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(error)
                }
            }
        }
    }
}
