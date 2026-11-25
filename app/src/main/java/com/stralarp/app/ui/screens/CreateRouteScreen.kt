package com.stralarp.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.stralarp.app.data.model.SimulationParams
import com.stralarp.app.ui.components.ElevationProfile
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
    var showAutoDialog by remember { mutableStateOf(false) }
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
                    IconButton(onClick = { showAutoDialog = true }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Auto Route")
                    }
                    IconButton(onClick = { viewModel.fitRoute() }) {
                        Icon(Icons.Default.ZoomOutMap, contentDescription = "Fit")
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
            Surface(color = Color(0xE6121212)) {
                Column(modifier = Modifier.padding(10.dp)) {
                    if (state.currentRoute.points.any { it.elevation != null }) {
                        ElevationProfile(
                            points = state.currentRoute.points,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        )
                    }

                    if (state.currentRoute.points.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "%.2f km".format(state.currentRoute.totalDistanceKm),
                                color = Color.White
                            )
                            Text(
                                "${state.currentRoute.points.size} pts",
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            if (state.currentRoute.elevationGain > 0) {
                                Text(
                                    "↑%.0fm".format(state.currentRoute.elevationGain),
                                    color = Color(0xFF00E676)
                                )
                            }
                        }
                    }

                    state.selectedPointIndex?.let { idx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Point #${idx + 1} selected",
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.deleteSelectedPoint() }) {
                                Text("Delete", color = Color(0xFFFF5252))
                            }
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, null, tint = Color.White)
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
                            Icon(Icons.Default.Undo, null, tint = Color.White)
                        }
                        IconButton(
                            onClick = { viewModel.clearRoute() },
                            enabled = state.currentRoute.points.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, null, tint = Color.White)
                        }
                        IconButton(
                            onClick = { viewModel.reverseRoute() },
                            enabled = state.currentRoute.points.size >= 2
                        ) {
                            Icon(Icons.Default.SwapHoriz, null, tint = Color.White)
                        }
                        IconButton(
                            onClick = { viewModel.closeLoop() },
                            enabled = state.currentRoute.points.size >= 2
                        ) {
                            Icon(Icons.Default.Loop, null, tint = Color.White)
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
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            GlassText("Save")
                        }
                        GlassOutlinedButton(
                            onClick = { viewModel.exportGpxAndShare(context) },
                            enabled = state.currentRoute.points.size >= 2,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            GlassText("Share")
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    GlassButton(
                        onClick = { viewModel.simulateAndShare(context) },
                        enabled = state.currentRoute.points.size >= 2,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        GlassText("Simulate & Share")
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            OsmdroidMap(
                modifier = Modifier.fillMaxSize(),
                points = state.currentRoute.points,
                onMapClick = { lat, lon -> viewModel.onMapTap(lat, lon) },
                fitRouteRequest = state.requestFitRoute,
                onFitHandled = { viewModel.clearFitRequest() },
                onCenterChanged = { lat, lon -> viewModel.updateMapCenter(lat, lon) }
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    if (state.selectedPointIndex != null)
                        "Tap Delete to remove point"
                    else
                        "Tap map to add · Auto for generated route",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }

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
            text = { SettingsContent(viewModel) },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) { Text("Close") }
            }
        )
    }

    if (showAutoDialog) {
        AutoRouteDialog(
            onDismiss = { showAutoDialog = false },
            onGenerate = { km, loop ->
                showAutoDialog = false
                viewModel.generateAutoRoute(km, loop)
            }
        )
    }
}

@Composable
private fun AutoRouteDialog(
    onDismiss: () -> Unit,
    onGenerate: (targetKm: Double, loop: Boolean) -> Unit
) {
    var targetKm by remember { mutableFloatStateOf(5f) }
    var loop by remember { mutableStateOf(true) }
    val presets = listOf(3f, 5f, 8f, 10f, 15f, 21f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto Route") },
        text = {
            Column {
                Text(
                    "Generates a route around the current map center.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text("Distance: %.0f km".format(targetKm))
                Slider(
                    value = targetKm,
                    onValueChange = { targetKm = it },
                    valueRange = 2f..30f,
                    steps = 27
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { p ->
                        FilterChip(
                            selected = kotlin.math.abs(targetKm - p) < 0.5f,
                            onClick = { targetKm = p },
                            label = { Text("%.0f".format(p), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Loop (round trip)")
                    Spacer(Modifier.weight(1f))
                    Switch(checked = loop, onCheckedChange = { loop = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onGenerate(targetKm.toDouble(), loop) }) {
                Text("Generate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
    var sampling by remember {
        mutableStateOf(state.simulationParams.samplingIntervalSeconds.toFloat())
    }
    var jitter by remember { mutableStateOf(state.simulationParams.injectGpsJitter) }
    var hr by remember { mutableStateOf(state.simulationParams.injectHeartRate) }
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
            value = name, onValueChange = { name = it },
            label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = description, onValueChange = { description = it },
            label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), singleLine = true
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
        ) { Text("Save Metadata") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text("Start time (yyyy-MM-dd HH:mm)", style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(
            value = startTimeText, onValueChange = { startTimeText = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Use Pace")
            Spacer(Modifier.weight(1f))
            Switch(checked = usePace, onCheckedChange = { usePace = it })
        }
        if (usePace) {
            Text("Pace: %.1f min/km".format(pace))
            Slider(value = pace, onValueChange = { pace = it }, valueRange = 3f..12f)
        } else {
            Text("Duration: %.0f min".format(durationMin))
            Slider(value = durationMin, onValueChange = { durationMin = it }, valueRange = 5f..300f)
        }

        Text("Variability: %.0f%%".format(variability))
        Slider(value = variability, onValueChange = { variability = it }, valueRange = 0f..20f)

        Text("Sampling: every %.0f s".format(sampling))
        Slider(
            value = sampling,
            onValueChange = { sampling = it },
            valueRange = 1f..5f,
            steps = 3
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("GPS Jitter")
            Spacer(Modifier.weight(1f))
            Switch(checked = jitter, onCheckedChange = { jitter = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Heart rate in GPX")
            Spacer(Modifier.weight(1f))
            Switch(checked = hr, onCheckedChange = { hr = it })
        }

        Text("Elevation Scale: %.1fx".format(elevScale))
        Slider(value = elevScale, onValueChange = { elevScale = it }, valueRange = 0.5f..2.0f)

        if (state.currentRoute.points.any { it.elevation != null }) {
            Text("Elevation profile", style = MaterialTheme.typography.labelMedium)
            ElevationProfile(points = state.currentRoute.points)
            Spacer(Modifier.height(8.dp))
        }

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
                        samplingIntervalSeconds = sampling.toInt().coerceIn(1, 5),
                        injectGpsJitter = jitter,
                        scaleElevation = elevScale,
                        injectHeartRate = hr
                    )
                )
                viewModel.runSimulation()
            },
            enabled = state.currentRoute.points.size >= 2,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Apply Simulation") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text("Trim Start: %.0f%%".format(startTrim))
        Slider(value = startTrim, onValueChange = { startTrim = it }, valueRange = 0f..90f)
        Text("Trim End: %.0f%%".format(endTrim))
        Slider(value = endTrim, onValueChange = { endTrim = it }, valueRange = 10f..100f)
        Button(
            onClick = { viewModel.trimRoute(startTrim, endTrim) },
            enabled = state.currentRoute.points.size >= 3,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Apply Trim") }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { viewModel.reEnrichElevation() },
            enabled = state.currentRoute.points.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Re-fetch Elevation") }
    }
}
