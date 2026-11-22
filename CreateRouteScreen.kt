package com.stralarp.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stralarp.app.ui.components.OsmdroidMap
import com.stralarp.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRouteScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

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
            Surface(tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = { viewModel.undoLastPoint() }, enabled = state.currentRoute.points.isNotEmpty()) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { viewModel.clearRoute() }, enabled = state.currentRoute.points.isNotEmpty()) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear")
                    }
                    IconButton(onClick = { viewModel.reverseRoute() }, enabled = state.currentRoute.points.size >= 2) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Reverse")
                    }
                    IconButton(onClick = { viewModel.closeLoop() }, enabled = state.currentRoute.points.size >= 2) {
                        Icon(Icons.Default.Loop, contentDescription = "Close Loop")
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

            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .fillMaxWidth(0.9f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${"%.2f".format(state.currentRoute.totalDistanceKm)} km  •  " +
                                "${"%.2f".format(state.currentRoute.totalDistanceMiles)} mi",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${state.currentRoute.points.size} points",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
