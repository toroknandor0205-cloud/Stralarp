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
import com.stralarp.app.data.prefs.SavedSettings
import com.stralarp.app.data.prefs.UserPrefs
import com.stralarp.app.data.repository.RouteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class AppUiState(
    val currentRoute: Route = Route(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val snapToRoad: Boolean = true,
    val simulationParams: SimulationParams = SimulationParams(),
    val undoStack: List<Route> = emptyList(),
    val requestFitRoute: Boolean = false,
    val selectedPointIndex: Int? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = RouteRepository(application)
    private val prefs = UserPrefs(application)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val s = prefs.settings.first()
                _uiState.update {
                    it.copy(
                        snapToRoad = s.snapToRoad,
                        simulationParams = it.simulationParams.copy(
                            targetPaceMinPerKm = s.targetPaceMinPerKm,
                            paceVariabilityPercent = s.paceVariabilityPercent,
                            samplingIntervalSeconds = s.samplingIntervalSeconds,
                            injectGpsJitter = s.injectGpsJitter,
                            injectHeartRate = s.injectHeartRate
                        ),
                        currentRoute = it.currentRoute.copy(activityType = s.activityType)
                    )
                }
            } catch (_: Exception) { }
        }
    }

    private fun pushUndo() {
        val current = _uiState.value.currentRoute
        _uiState.update {
            it.copy(undoStack = (it.undoStack + current).takeLast(20))
        }
    }

    private fun persistSettings() {
        viewModelScope.launch {
            val st = _uiState.value
            prefs.save(
                SavedSettings(
                    snapToRoad = st.snapToRoad,
                    targetPaceMinPerKm = st.simulationParams.targetPaceMinPerKm,
                    paceVariabilityPercent = st.simulationParams.paceVariabilityPercent,
                    samplingIntervalSeconds = st.simulationParams.samplingIntervalSeconds,
                    injectGpsJitter = st.simulationParams.injectGpsJitter,
                    activityType = st.currentRoute.activityType,
                    injectHeartRate = st.simulationParams.injectHeartRate
                )
            )
        }
    }

    fun setSnapToRoad(enabled: Boolean) {
        _uiState.update { it.copy(snapToRoad = enabled) }
        persistSettings()
    }

    /** Tap map: if near existing point → select it; else add waypoint. */
    fun onMapTap(lat: Double, lon: Double) {
        val pts = _uiState.value.currentRoute.points
        val nearIdx = findNearestIndex(pts, lat, lon, thresholdMeters = 25.0)
        if (nearIdx != null) {
            _uiState.update { it.copy(selectedPointIndex = nearIdx) }
            return
        }
        _uiState.update { it.copy(selectedPointIndex = null) }
        addWaypoint(lat, lon)
    }

    fun deleteSelectedPoint() {
        val idx = _uiState.value.selectedPointIndex ?: return
        val pts = _uiState.value.currentRoute.points
        if (idx !in pts.indices) return
        pushUndo()
        val next = pts.toMutableList().also { it.removeAt(idx) }
        _uiState.update {
            it.copy(
                currentRoute = it.currentRoute.copy(points = next),
                selectedPointIndex = null,
                message = "Point deleted"
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPointIndex = null) }
    }

    private fun findNearestIndex(
        points: List<TrackPoint>,
        lat: Double,
        lon: Double,
        thresholdMeters: Double
    ): Int? {
        if (points.isEmpty()) return null
        var bestIdx = -1
        var bestDist = Double.MAX_VALUE
        val probe = TrackPoint(lat, lon)
        points.forEachIndexed { i, p ->
            val d = p.distanceTo(probe)
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }
        return if (bestDist <= thresholdMeters) bestIdx else null
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

                val withEle = try {
                    if (updatedPoints.size <= 40) {
                        repo.enrichElevation(updatedPoints)
                    } else {
                        val head = updatedPoints.dropLast(15)
                        val tail = repo.enrichElevation(updatedPoints.takeLast(15))
                        head + tail
                    }
                } catch (_: Exception) {
                    updatedPoints // offline elevation fallback
                }

                val name = ensureRouteName(_uiState.value.currentRoute.name)
                _uiState.update {
                    it.copy(
                        currentRoute = it.currentRoute.copy(name = name, points = withEle),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Network error – try again or turn Snap off"
                    )
                }
            }
        }
    }

    private fun ensureRouteName(current: String): String {
        if (current.isNotBlank() && current != "Stralarp Route") return current
        val hour = LocalTime.now().hour
        val period = when {
            hour < 5 -> "Night"
            hour < 11 -> "Morning"
            hour < 14 -> "Midday"
            hour < 18 -> "Afternoon"
            hour < 22 -> "Evening"
            else -> "Night"
        }
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "$period Run – $date"
    }

    fun undo() {
        val stack = _uiState.value.undoStack
        if (stack.isEmpty()) return
        _uiState.update {
            it.copy(
                currentRoute = stack.last(),
                undoStack = stack.dropLast(1),
                selectedPointIndex = null
            )
        }
    }

    fun clearRoute() {
        pushUndo()
        _uiState.update {
            it.copy(currentRoute = Route(activityType = it.currentRoute.activityType), selectedPointIndex = null)
        }
    }

    fun reverseRoute() {
        pushUndo()
        _uiState.update {
            it.copy(
                currentRoute = it.currentRoute.copy(points = it.currentRoute.points.reversed()),
                selectedPointIndex = null
            )
        }
    }

    fun closeLoop() {
        val pts = _uiState.value.currentRoute.points
        if (pts.size >= 2) addWaypoint(pts.first().lat, pts.first().lon)
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
                val withEle = try {
                    if (route.points.any { it.elevation == null }) {
                        repo.enrichElevation(route.points)
                    } else route.points
                } catch (_: Exception) {
                    route.points
                }
                _uiState.update {
                    it.copy(
                        currentRoute = route.copy(points = withEle),
                        isLoading = false,
                        message = "Imported ${withEle.size} points",
                        requestFitRoute = true,
                        selectedPointIndex = null
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
        persistSettings()
    }

    fun updateSimulationParams(params: SimulationParams) {
        _uiState.update { it.copy(simulationParams = params) }
        persistSettings()
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

    fun simulateAndShare(context: Context) {
        viewModelScope.launch {
            pushUndo()
            _uiState.update { it.copy(isLoading = true, error = null, message = null) }
            try {
                val simulated = repo.simulate(
                    _uiState.value.currentRoute,
                    _uiState.value.simulationParams
                )
                val named = autoNameIfNeeded(simulated)
                _uiState.update { it.copy(currentRoute = named) }

                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val file = repo.exportGpx(named, "Stralarp_$date.gpx")
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
                    it.copy(isLoading = false, message = "Simulated & ready to share")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Simulate & Share failed")
                }
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
                message = "Trimmed to ${endIdx - startIdx} points",
                selectedPointIndex = null
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
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Elevation offline – try later"
                    )
                }
            }
        }
    }

    fun writeToDocument(uri: Uri) {
        viewModelScope.launch {
            try {
                val route = autoNameIfNeeded(_uiState.value.currentRoute)
                repo.writeToUri(route, uri)
                _uiState.update { it.copy(currentRoute = route, message = "GPX saved") }
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
                val file = repo.exportGpx(route, "Stralarp_$date.gpx")
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
                    it.copy(currentRoute = route, isLoading = false, message = "Ready to share")
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
        return route.copy(name = ensureRouteName(route.name))
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
