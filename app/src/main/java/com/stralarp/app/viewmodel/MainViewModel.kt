package com.stralarp.app.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stralarp.app.data.model.Route
import com.stralarp.app.data.model.SimulationParams
import com.stralarp.app.data.model.TrackPoint
import com.stralarp.app.data.repository.RouteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppUiState(
    val currentRoute: Route = Route(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val snapToRoad: Boolean = true,
    val simulationParams: SimulationParams = SimulationParams(),
    val undoStack: List<Route> = emptyList(),
    val requestFitRoute: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = RouteRepository(application)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private fun pushUndo() {
        val current = _uiState.value.currentRoute
        _uiState.update {
            val stack = (it.undoStack + current).takeLast(20)
            it.copy(undoStack = stack)
        }
    }

    fun setSnapToRoad(enabled: Boolean) {
        _uiState.update { it.copy(snapToRoad = enabled) }
    }

    fun addWaypoint(lat: Double, lon: Double) {
        viewModelScope.launch {
            pushUndo()
            _uiState.update { it.copy(isLoading = true, error = null, message = null) }
            try {
                val existing = _uiState.value.currentRoute.points
                val snap = _uiState.value.snapToRoad

                val newPoint = if (snap) {
                    repo.nearestPoint(lat, lon) ?: TrackPoint(lat, lon)
                } else {
                    TrackPoint(lat, lon)
                }

                val updatedPoints = if (existing.isEmpty()) {
                    listOf(newPoint)
                } else if (snap) {
                    // Only route the new segment — more reliable than re-routing entire path
                    val last = existing.last()
                    val segment = repo.routeSegment(last, newPoint)
                    if (segment.size >= 2) {
                        existing.dropLast(1) + segment
                    } else {
                        existing + newPoint
                    }
                } else {
                    existing + newPoint
                }

                // Elevation only for new points at end (keep it light)
                val withEle = if (updatedPoints.size <= 30) {
                    repo.enrichElevation(updatedPoints)
                } else {
                    val head = updatedPoints.dropLast(20)
                    val tail = repo.enrichElevation(updatedPoints.takeLast(20))
                    head + tail
                }

                val name = ensureRouteName(_uiState.value.currentRoute.name, withEle.size)
                _uiState.update {
                    it.copy(
                        currentRoute = it.currentRoute.copy(name = name, points = withEle),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to add point")
                }
            }
        }
    }

    private fun ensureRouteName(current: String, pointCount: Int): String {
        if (current.isNotBlank() && current != "Stralarp Route") return current
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "Run – $date"
    }

    fun undo() {
        val stack = _uiState.value.undoStack
        if (stack.isEmpty()) return
        val previous = stack.last()
        _uiState.update {
            it.copy(currentRoute = previous, undoStack = stack.dropLast(1))
        }
    }

    fun clearRoute() {
        pushUndo()
        _uiState.update { it.copy(currentRoute = Route()) }
    }

    fun reverseRoute() {
        pushUndo()
        _uiState.update {
            it.copy(currentRoute = it.currentRoute.copy(points = it.currentRoute.points.reversed()))
        }
    }

    fun closeLoop() {
        val pts = _uiState.value.currentRoute.points
        if (pts.size >= 2) {
            val first = pts.first()
            addWaypoint(first.lat, first.lon)
        }
    }

    fun fitRoute() {
        _uiState.update { it.copy(requestFitRoute = true) }
    }

    fun clearFitRequest() {
        _uiState.update { it.copy(requestFitRoute = false) }
    }

    fun importGpx(uri: Uri) {
        viewModelScope.launch {
            pushUndo()
            _uiState.update { it.copy(isLoading = true, error = null, message = null) }
            try {
                val route = repo.parseGpx(uri)
                val withEle = if (route.points.any { it.elevation == null }) {
                    repo.enrichElevation(route.points)
                } else route.points
                _uiState.update {
                    it.copy(
                        currentRoute = route.copy(points = withEle),
                        isLoading = false,
                        message = "Imported ${withEle.size} points",
                        requestFitRoute = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Import failed")
                }
            }
        }
    }

    fun updateMetadata(name: String, description: String, activityType: String) {
        pushUndo()
        _uiState.update {
            it.copy(
                currentRoute = it.currentRoute.copy(
                    name = name.ifBlank { it.currentRoute.name },
                    description = description,
                    activityType = activityType
                ),
                message = "Metadata saved"
            )
        }
    }

    fun updateSimulationParams(params: SimulationParams) {
        _uiState.update { it.copy(simulationParams = params) }
    }

    fun runSimulation() {
        viewModelScope.launch {
            pushUndo()
            _uiState.update { it.copy(isLoading = true, error = null, message = null) }
            try {
                val simulated = repo.simulate(
                    _uiState.value.currentRoute,
                    _uiState.value.simulationParams
                )
                _uiState.update {
                    it.copy(
                        currentRoute = simulated,
                        isLoading = false,
                        message = "Simulation applied (${simulated.points.size} points)"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun trimRoute(startPercent: Float, endPercent: Float) {
        val pts = _uiState.value.currentRoute.points
        if (pts.size < 3) return
        pushUndo()
        val startIdx = (pts.size * startPercent / 100f).toInt().coerceIn(0, pts.size - 2)
        val endIdx = (pts.size * endPercent / 100f).toInt().coerceIn(startIdx + 1, pts.size)
        _uiState.update {
            it.copy(
                currentRoute = it.currentRoute.copy(points = pts.subList(startIdx, endIdx)),
                message = "Trimmed to ${endIdx - startIdx} points"
            )
        }
    }

    fun reEnrichElevation() {
        viewModelScope.launch {
            pushUndo()
            _uiState.update { it.copy(isLoading = true, message = null) }
            try {
                val withEle = repo.enrichElevation(_uiState.value.currentRoute.points)
                _uiState.update {
                    it.copy(
                        currentRoute = it.currentRoute.copy(points = withEle),
                        isLoading = false,
                        message = "Elevation updated"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun writeToDocument(uri: Uri) {
        viewModelScope.launch {
            try {
                val route = autoNameIfNeeded(_uiState.value.currentRoute)
                repo.writeToUri(route, uri)
                _uiState.update {
                    it.copy(currentRoute = route, message = "GPX saved")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Save failed") }
            }
        }
    }

    fun exportGpxAndShare(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, message = null) }
            try {
                val route = autoNameIfNeeded(_uiState.value.currentRoute)
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val fileName = "Stralarp_$date.gpx"
                val file = repo.exportGpx(route, fileName)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/gpx+xml"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share GPX"))

                _uiState.update {
                    it.copy(
                        currentRoute = route,
                        isLoading = false,
                        message = "Ready to share"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Share failed")
                }
            }
        }
    }

    private fun autoNameIfNeeded(route: Route): Route {
        if (route.name.isNotBlank() && route.name != "Stralarp Route") return route
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return route.copy(name = "Run – $date")
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
