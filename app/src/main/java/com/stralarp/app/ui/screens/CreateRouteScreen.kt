package com.stralarp.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.stralarp.app.data.model.SimulationParams
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
    var showSettings by remember { mutableStateOf(false) }

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
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Settings")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Snap", style = MaterialTheme.typography.labelSmall)
                        Switch(
                            checked = state.snapToRoad,
                            onCheckedChange = { viewModel.setSnapToRoad(it) }
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 8.dp) {
                Column(modifier = Modifier.padding(8.dp)) {
                    if (state.currentRoute.points.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${"%.2f".format(state.currentRoute.totalDistanceKm)} km")
                            Text("${state.currentRoute.points.size} pts")
                            if (state.currentRoute.elevationGain > 0) {
                                Text("↑${"%.0f".format(state.currentRoute.elevationGain)}m")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(
                            onClick = { viewModel.undo() },
                            enabled = state.undoStack.isNotEmpty()
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                exportLauncher.launch("Stralarp_$date.gpx")
                            },
                            enabled = state.currentRoute.points.size >= 2,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save")
                        }
                        OutlinedButton(
                            onClick = { viewModel.exportGpxAndShare(context) },
                            enabled = state.currentRoute.points.size >= 2,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Share")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier = Modifier.fillMaxSize().padding(padding)) {
            OsmdroidMap(
                modifier = Modifier.fillMaxSize(),
                points = state.currentRoute.points,
                onMapClick = { lat, lon -> viewModel.addWaypoint(lat, lon) },
                centerOnRoute = true
            )

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            SettingsSheet(
                viewModel = viewModel,
                onClose = { showSettings = false }
            )
        }
    }
}

@Composable
private fun SettingsSheet(
    viewModel: MainViewModel,
    onClose: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var name by remember { mutableStateOf(state.currentRoute.name) }
    var description by remember { mutableStateOf(state.currentRoute.description) }
    var activityType by remember { mutableStateOf(state.currentRoute.activityType) }
    var pace by remember { mutableStateOf(state.simulationParams.targetPaceMinPerKm.toFloat()) }
    var variability by remember { mutableStateOf(state.simulationParams.paceVariabilityPercent) }
    var jitter by remember { mutableStateOf(state.simulationParams.injectGpsJitter) }
    var elevScale by remember { mutableStateOf(state.simulationParams.scaleElevation) }
    var startTrim by remember { mutableStateOf(0f) }
    var endTrim by remember { mutableStateOf(100f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Route Settings", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        // Metadata
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        Text("Activity type")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("running", "trail_running", "cycling", "walking").forEach { type ->
                FilterChip(
                    selected = activityType == type,
                    onClick = { activityType = type },
                    label = { Text(type.replace('_', ' ')) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                viewModel.updateMetadata(name, description, activityType)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Metadata")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // Simulation
        Text("Simulation", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Text("Target Pace: ${"%.1f".format(pace)} min/km")
        Slider(
            value = pace,
            onValueChange = { pace = it },
            valueRange = 3f..12f
        )

        Text("Pace Variability: ${"%.0f".format(variability)}%")
        Slider(
            value = variability,
            onValueChange = { variability = it },
            valueRange = 0f..20f
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("GPS Jitter")
            Spacer(Modifier.weight(1f))
            Switch(checked = jitter, onCheckedChange = { jitter = it })
        }

        Text("Elevation Scale: ${"%.1f".format(elevScale)}x")
        Slider(
            value = elevScale,
            onValueChange = { elevScale = it },
            valueRange = 0.5f..2.0f
        )

        Button(
            onClick = {
                viewModel.updateSimulationParams(
                    SimulationParams(
                        targetPaceMinPerKm = pace.toDouble(),
                        paceVariabilityPercent = variability,
                        injectGpsJitter = jitter,
                        scaleElevation = elevScale
                    )
                )
                viewModel.runSimulation()
            },
            enabled = state.currentRoute.points.size >= 2,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Apply Simulation")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // Trim
        Text("Trim Route", style = MaterialTheme.typography.titleMedium)
        Text("Start: ${"%.0f".format(startTrim)}%")
        Slider(value = startTrim, onValueChange = { startTrim = it }, valueRange = 0f..90f)
        Text("End: ${"%.0f".format(endTrim)}%")
        Slider(value = endTrim, onValueChange = { endTrim = it }, valueRange = 10f..100f)

        Button(
            onClick = { viewModel.trimRoute(startTrim, endTrim) },
            enabled = state.currentRoute.points.size >= 3,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Apply Trim")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { viewModel.reEnrichElevation() },
            enabled = state.currentRoute.points.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Re-fetch Elevation")
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Close")
        }
        Spacer(modifier.height(32.dp))
    }
}
