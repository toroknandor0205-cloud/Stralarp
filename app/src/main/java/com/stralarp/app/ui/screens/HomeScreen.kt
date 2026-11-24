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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stralarp.app.ui.components.GlassButton
import com.stralarp.app.ui.components.GlassOutlinedButton
import com.stralarp.app.ui.components.GlassText
import com.stralarp.app.ui.components.MeshGradientBackground
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
        Box(modifier = Modifier.fillMaxSize()) {
            MeshGradientBackground()

            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = { Text("Stralarp", color = Color.White) },
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
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Stralarp",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "GPS Route Simulator & GPX Editor",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    if (state.currentRoute.points.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = Color.White.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                Color.White.copy(alpha = 0.2f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    state.currentRoute.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${state.currentRoute.points.size} points · " +
                                            "${"%.2f".format(state.currentRoute.totalDistanceKm)} km",
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                if (state.currentRoute.elevationGain > 0) {
                                    Text(
                                        "↑ ${"%.0f".format(state.currentRoute.elevationGain)} m",
                                        color = Color(0xFF00E676)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    GlassButton(
                        onClick = { showCreateScreen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        GlassText("Create / Edit Route")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    GlassOutlinedButton(
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
                        GlassText("Import GPX")
                    }

                    if (state.isLoading) {
                        Spacer(modifier = Modifier.height(24.dp))
                        CircularProgressIndicator(color = Color(0xFF00E676))
                    }

                    state.error?.let { error ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
