package com.stralarp.app.ui.screens

import androidx.compose.foundation.layout.*
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

    if (showCreateScreen) {
        CreateRouteScreen(
            viewModel = viewModel,
            onBack = { showCreateScreen = false }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Stralarp") }
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
                    text = "Points: ${state.currentRoute.points.size}",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Distance: ${"%.2f".format(state.currentRoute.totalDistanceKm)} km",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { showCreateScreen = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create New Route")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { /* Future: Import GPX */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Import GPX (coming soon)")
                }
            }
        }
    }
}
