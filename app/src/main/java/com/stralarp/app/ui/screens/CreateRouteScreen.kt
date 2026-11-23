package com.stralarp.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
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
import java.util.Date
import java.util.Locale

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Settings")
                    }
                    Text("Snap", style = MaterialTheme.typography.labelSmall)
                    Switch(
                        checked = state.snapToRoad,
                        onCheckedChange = { viewModel.setSnapToRoad(it) }
                    )
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
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save")
                        }
                        OutlinedButton(
                            onClick = { viewModel.exportGpxAndShare(context) },
                            enabled = state.currentRoute.points.size >= 2,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Share")
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

            state.error?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Route Settings") },
            text = {
                SettingsContent(viewModel = viewModel)
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun SettingsContent(viewModel: MainViewModel) {
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
            .heightIn(max = 480.dp)
            .verticalScroll(rememberScrollState())
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))

        Text("Activity", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("running", "trail_running", "cycling", "walking").forEach { type ->
                FilterChip(
                    selected = activityType == type,
                    onClick = { activityType = type },
                    label = { Text(type.replace('_', ' '), style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { viewModel.updateMetadata(name, description, activityType) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Metadata")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text("Target Pace: ${"%.1f".format(pace)} min/km")
        Slider(value = pace, onValueChange = { pace = it }, valueRange = 3f..12f)

        Text("Variability: ${"%.0f".format(variability)}%")
        Slider(value = variability, onValueChange = { variability = it }, valueRange = 0f..20f)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("GPS Jitter")
            Spacer(Modifier.weight(1f))
            Switch(checked = jitter, onCheckedChange = { jitter = it })
        }

        Text("Elevation Scale: ${"%.1f".format(elevScale)}x")
        Slider(value = elevScale, onValueChange = { elevScale = it }, valueRange = 0.5f..2.0f)

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

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text("Trim Start: ${"%.0f".format(startTrim)}%")
        Slider(value = startTrim, onValueChange = { startTrim = it }, valueRange = 0f..90f)
        Text("Trim End: ${"%.0f".format(endTrim)}%")
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
    }
}
