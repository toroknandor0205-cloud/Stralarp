package com.stralarp.app.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.stralarp.app.data.repository.SavedRouteMeta
import com.stralarp.app.ui.components.GlassButton
import com.stralarp.app.ui.components.GlassOutlinedButton
import com.stralarp.app.ui.components.GlassText
import com.stralarp.app.ui.components.MeshGradientBackground
import com.stralarp.app.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showCreateScreen by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importGpx(it)
            showCreateScreen = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.filterKeys {
            it == Manifest.permission.ACCESS_FINE_LOCATION ||
                it == Manifest.permission.ACCESS_COARSE_LOCATION
        }.values.any { it }) {
            viewModel.startRecording(context)
            showCreateScreen = true
        }
    }

    LaunchedEffect(Unit) { viewModel.refreshSavedRoutes() }

    if (showCreateScreen) {
        CreateRouteScreen(
            viewModel = viewModel,
            onBack = { showCreateScreen = false }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MeshGradientBackground()

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Stralarp", color = Color.White) },
                    actions = {
                        IconButton(onClick = { viewModel.setDarkTheme(!state.darkTheme) }) {
                            Icon(
                                if (state.darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Theme",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "GPS Route Simulator & GPX Editor",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f)
                )

                Spacer(Modifier.height(20.dp))

                if (state.currentRoute.points.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = Color.White.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(state.currentRoute.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text(
                                "${state.currentRoute.points.size} pts · %.2f km".format(state.currentRoute.totalDistanceKm),
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            if (state.currentRoute.elevationGain > 0) {
                                Text("↑ %.0f m".format(state.currentRoute.elevationGain), color = Color(0xFF00E676))
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                GlassButton(
                    onClick = { showCreateScreen = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Map, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    GlassText("Create / Edit Route")
                }
                Spacer(Modifier.height(10.dp))

                GlassOutlinedButton(
                    onClick = {
                        importLauncher.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FileOpen, null)
                    Spacer(Modifier.width(8.dp))
                    GlassText("Import GPX")
                }
                Spacer(Modifier.height(10.dp))

                if (state.isRecording) {
                    GlassButton(
                        onClick = { viewModel.stopRecording() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Stop, null, tint = Color(0xFFFF5252))
                        Spacer(Modifier.width(8.dp))
                        GlassText("Stop Recording")
                    }
                } else {
                    GlassOutlinedButton(
                        onClick = {
                            val perms = mutableListOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                            if (Build.VERSION.SDK_INT >= 33) {
                                perms.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionLauncher.launch(perms.toTypedArray())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FiberManualRecord, null, tint = Color(0xFFFF5252))
                        Spacer(Modifier.width(8.dp))
                        GlassText("Live GPS Record")
                    }
                }

                Spacer(Modifier.height(10.dp))

                GlassOutlinedButton(
                    onClick = {
                        viewModel.refreshSavedRoutes()
                        showSaved = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GlassText("Saved Routes (${state.savedRoutes.size})")
                }

                if (state.isLoading) {
                    Spacer(Modifier.height(20.dp))
                    CircularProgressIndicator(color = Color(0xFF00E676))
                }
                state.error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                state.message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Color(0xFF00E676))
                }
            }
        }
    }

    if (showSaved) {
        AlertDialog(
            onDismissRequest = { showSaved = false },
            title = { Text("Saved Routes") },
            text = {
                Column {
                    OutlinedTextField(
                        value = state.savedSearchQuery,
                        onValueChange = { viewModel.setSavedSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search by name") },
                        placeholder = { Text("e.g. Morning") }
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text("Favorites only", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = state.savedFavoritesOnly,
                            onCheckedChange = { viewModel.setSavedFavoritesOnly(it) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (state.savedRoutes.isEmpty()) {
                        Text(
                            if (state.savedSearchQuery.isNotBlank() || state.savedFavoritesOnly)
                                "No matching routes."
                            else
                                "No saved routes yet. Save from Create screen."
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                            items(state.savedRoutes, key = { it.id }) { meta ->
                                SavedRouteRow(
                                    meta = meta,
                                    onLoad = {
                                        viewModel.loadSavedRoute(meta.id)
                                        showSaved = false
                                        showCreateScreen = true
                                    },
                                    onDelete = { viewModel.deleteSavedRoute(meta.id) },
                                    onToggleFavorite = { viewModel.toggleFavorite(meta.id) }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSaved = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SavedRouteRow(
    meta: SavedRouteMeta,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        .format(Date(meta.savedAt))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (meta.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Favorite",
                tint = if (meta.favorite) Color(0xFFFFD54F) else Color.Gray
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(meta.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "%.2f km · ↑%.0fm · %s".format(meta.distanceKm, meta.elevationGain, date),
                style = MaterialTheme.typography.bodySmall
            )
        }
        TextButton(onClick = onLoad) { Text("Load") }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, null, tint = Color(0xFFFF5252))
        }
    }
}
