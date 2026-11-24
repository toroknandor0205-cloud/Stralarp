package com.stralarp.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.stralarp.app.data.model.SimulationParams
import com.stralarp.app.ui.components.GlassButton
import com.stralarp.app.ui.components.GlassOutlinedButton
import com.stralarp.app.ui.components.GlassText
import com.stralarp.app.ui.components.OsmdroidMap
import com.stralarp.app.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri: Uri? ->
        uri?.let { viewModel.writeToDocument(it) }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Create Route") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fitRoute() }) {
                        Icon(Icons.Default.CenterFocusStrong, contentDescription = "Fit route")
                    }
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
            Surface(
                color = Color(0xE6121212),
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    if (state.currentRoute.points.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${"%.2f".format(state.currentRoute.totalDistanceKm)} km",
                                color = Color.White
                            )
                            Text(
                                "${state.currentRoute.points.size} pts",
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            if (state.currentRoute.elevationGain > 0) {
                                Text(
                                    "↑${"%.0f".format(state.currentRoute.elevationGain)}m",
                                    color = Color(0xFF00E676)
                                )
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
                            Icon(Icons.Default.Undo, contentDescription = "Undo", tint = Color.White)
                        }
                        IconButton(
                            onClick = { viewModel.clearRoute() },
                            enabled = state.currentRoute.points.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color.White)
                        }
                        IconButton(
                            onClick = { viewModel.reverseRoute() },
                            enabled = state.currentRoute.points.size >= 2
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = "Reverse", tint = Color.White)
                        }
                        IconButton(
                            onClick = { viewModel.closeLoop() },
                            enabled = state.currentRoute.points.size >= 2
                        ) {
                            Icon(Icons.Default.Loop, contentDescription = "Close Loop", tint = Color.White)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlassButton(
                            onClick = {
                                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                exportLauncher.launch("Stralarp_$date.gpx")
                            },
                            enabled = state.currentRoute.points.size >= 2,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            GlassText("Save")
                        }
                        GlassOutlinedButton(
                            onClick = { viewModel.exportGpxAndShare(context) },
                            enabled = state.currentRoute.points.size >= 2,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            GlassText("Share")
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
                fitRouteRequest = state.requestFitRoute,
                onFitHandled = { viewModel.clearFitRequest() }
            )

            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF00E676)
                )
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Route Settings") },
            text = { SettingsContent(viewModel = viewModel) },
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
    var usePace by remember { mutableStateOf(state.simulationParams.usePace) }
    var pace by remember { mutableStateOf(state.simulationParams.targetPaceMinPerKm.toFloat()) }
    var durationMin by remember {
        mutableStateOf(((state.simulationParams.targetDurationSeconds ?: 1800L) / 60f))
    }
    var variability by remember { mutableStateOf(state.simulationParams.paceVariabilityPercent) }
    var jitter by remember { mutableStateOf(state.simulationParams.injectGpsJitter) }
    var elevScale by remember { mutableStateOf(state.simulationParams.scaleElevation) }
    var startTrim by remember { mutableStateOf(0f) }
    var endTrim by remember { mutableStateOf(100f) }
    var startTimeText by remember {
        mutableStateOf(
            state.simulationParams.startDateTime.format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState())
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text("Activity", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("running", "trail_running", "cycling", "walking").forEach { type ->
                FilterChip(
                    selected = activityType == type,
                    onClick = { activityType = type },
                    label = {
                        Text(type.replace('_', ' '), style = MaterialTheme.typography.labelSmall)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.updateMetadata(name, description, activityType) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Metadata")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text("Start time (yyyy-MM-dd HH:mm)", style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(
            value = startTimeText,
            onValueChange = { startTimeText = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Use Pace")
            Spacer(Modifier.weight(1f))
            Switch(checked = usePace, onCheckedChange = { usePace = it })
        }

        if (usePace) {
            Text("Target Pace: ${"%.1f".format(pace)} min/km")
            Slider(value = pace, onValueChange = { pace = it }, valueRange = 3f..12f)
        } else {
            Text("Duration: ${"%.0f".format(durationMin)} min")
            Slider(value = durationMin, onValueChange = { durationMin = it }, valueRange = 5f..300f)
        }

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
                val startDt = try {
                    LocalDateTime.parse(
                        startTimeText.trim(),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    )
                } catch (_: Exception) {
                    LocalDateTime.now()
                }
                viewModel.updateSimulationParams(
                    SimulationParams(
                        startDateTime = startDt,
                        targetPaceMinPerKm = pace.toDouble(),
                        targetDurationSeconds = (durationMin * 60).toLong(),
                        usePace = usePace,
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

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { viewModel.reEnrichElevation() },
            enabled = state.currentRoute.points.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Re-fetch Elevation")
        }
    }
}
