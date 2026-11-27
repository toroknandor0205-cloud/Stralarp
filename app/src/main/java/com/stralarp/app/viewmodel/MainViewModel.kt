package com.stralarp.app.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stralarp.app.data.model.Route
import com.stralarp.app.data.model.RouteStats
import com.stralarp.app.data.model.SimulationParams
import com.stralarp.app.data.model.TrackPoint
import com.stralarp.app.data.model.computeStats
import com.stralarp.app.data.network.OsrmClient
import com.stralarp.app.data.prefs.SavedSettings
import com.stralarp.app.data.prefs.UserPrefs
import com.stralarp.app.data.repository.RouteRepository
import com.stralarp.app.data.repository.SavedRouteMeta
import com.stralarp.app.data.repository.SavedRoutesStore
import com.stralarp.app.service.TrackingService
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

data class AppUiState(
    val currentRoute: Route = Route(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val snapToRoad: Boolean = true,
    val simulationParams: SimulationParams = SimulationParams(),
    val undoStack: List<Route> = emptyList(),
    val requestFitRoute: Boolean = false,
    val selectedPointIndex: Int? = null,
    val mapCenterLat: Double = 47.5,
    val mapCenterLon: Double = 19.0,
    val darkTheme: Boolean = true,
    val savedRoutes: List<SavedRouteMeta> = emptyList(),
    val isRecording: Boolean = false,
    val myLocation: TrackPoint? = null,
    val insertMode: Boolean = false,
    val moveMode: Boolean = false,
    val savedSearchQuery: String = "",
    val savedFavoritesOnly: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = RouteRepository(application)
    private val prefs = UserPrefs(application)
    private val savedStore = SavedRoutesStore(application)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null

    init {
        viewModelScope.launch {
            try {
                val s = prefs.settings.first()
                _uiState.update {
                    it.copy(
                        snapToRoad = s.snapToRoad,
                        darkTheme = s.darkTheme,
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
            refreshSavedRoutes()
        }
    }

    fun routeStats(): RouteStats = _uiState.value.currentRoute.computeStats()

    fun updateMapCenter(lat: Double, lon: Double) {
        _uiState.update { it.copy(mapCenterLat = lat, mapCenterLon = lon) }
    }

    fun setDarkTheme(dark: Boolean) {
        _uiState.update { it.copy(darkTheme = dark) }
        persistSettings()
    }

    fun setInsertMode(enabled: Boolean) {
        _uiState.update { it.copy(insertMode = enabled, moveMode = if (enabled) false else it.moveMode) }
    }

    fun setMoveMode(enabled: Boolean) {
        _uiState.update { it.copy(moveMode = enabled, insertMode = if (enabled) false else it.insertMode) }
    }

    private fun pushUndo() {
        val current = _uiState.value.currentRoute
        _uiState.update { it.copy(undoStack = (it.undoStack + current).takeLast(20)) }
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
                    injectHeartRate = st.simulationParams.injectHeartRate,
                    darkTheme = st.darkTheme
                )
            )
        }
    }

    fun setSnapToRoad(enabled: Boolean) {
        _uiState.update { it.copy(snapToRoad = enabled) }
        persistSettings()
    }

    fun refreshSavedRoutes() {
        viewModelScope.launch {
            try {
                val st = _uiState.value
                val list = savedStore.list(
                    query = st.savedSearchQuery,
                    favoritesOnly = st.savedFavoritesOnly
                )
                _uiState.update { it.copy(savedRoutes = list) }
            } catch (_: Exception) { }
        }
    }

    fun setSavedSearchQuery(query: String) {
        _uiState.update { it.copy(savedSearchQuery = query) }
        refreshSavedRoutes()
    }

    fun setSavedFavoritesOnly(only: Boolean) {
        _uiState.update { it.copy(savedFavoritesOnly = only) }
        refreshSavedRoutes()
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch {
            try {
                val current = _uiState.value.savedRoutes.find { it.id == id } ?: return@launch
                savedStore.setFavorite(id, !current.favorite)
                refreshSavedRoutes()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Favorite failed") }
            }
        }
    }

    fun resnapEntireRoute() {
        viewModelScope.launch {
            val pts = _uiState.value.currentRoute.points
            if (pts.size < 2) {
                _uiState.update { it.copy(error = "Need at least 2 points to re-snap") }
                return@launch
            }
            pushUndo()
            _uiState.update { it.copy(isLoading = true, error = null, message = null) }
            try {
                val keys = mutableListOf(pts.first())
                var acc = 0.0
                for (i in 1 until pts.size - 1) {
                    acc += pts[i - 1].distanceTo(pts[i])
                    if (acc >= 150.0) {
                        keys.add(pts[i])
                        acc = 0.0
                    }
                }
                keys.add(pts.last())
                val snapped = repo.snapWaypoints(keys)
                val withEle = try { repo.enrichElevation(snapped) } catch (_: Exception) { snapped }
                _uiState.update {
                    it.copy(
                        currentRoute = it.currentRoute.copy(points = withEle),
                        isLoading = false,
                        selectedPointIndex = null,
                        message = "Re-snapped · ${withEle.size} pts",
                        requestFitRoute = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = OsrmClient.friendlyNetworkError(e))
                }
            }
        }
    }

    fun saveCurrentRoute() {
        viewModelScope.launch {
            val route = autoNameIfNeeded(_uiState.value.currentRoute)
            if (route.points.size < 2) {
                _uiState.update { it.copy(error = "Need at least 2 points to save") }
                return@launch
            }
            try {
                savedStore.save(route)
                refreshSavedRoutes()
                _uiState.update { it.copy(currentRoute = route, message = "Route saved") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Save failed") }
            }
        }
    }

    fun loadSavedRoute(id: String) {
        viewModelScope.launch {
            pushUndo()
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val route = savedStore.load(id)
                if (route == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Route not found") }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        currentRoute = route,
                        isLoading = false,
                        message = "Loaded ${route.name}",
                        requestFitRoute = true,
                        selectedPointIndex = null,
                        moveMode = false,
                        insertMode = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Load failed") }
            }
        }
    }

    fun deleteSavedRoute(id: String) {
        viewModelScope.launch {
            try {
                savedStore.delete(id)
                refreshSavedRoutes()
                _uiState.update { it.copy(message = "Deleted") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Delete failed") }
            }
        }
    }

    fun generateAutoRoute(targetKm: Double, loop: Boolean = true) {
        viewModelScope.launch {
            pushUndo()
            _uiState.update { it.copy(isLoading = true, error = null, message = null, selectedPointIndex = null) }
            try {
                val st = _uiState.value
                val points = repo.generateAutoRoute(
                    centerLat = st.mapCenterLat,
                    centerLon = st.mapCenterLon,
                    targetKm = targetKm,
                    loop = loop,
                    activityType = st.currentRoute.activityType
                )
                if (points.size < 4) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Could not generate route – check internet or move map center"
                        )
                    }
                    return@launch
                }
                val withEle = try { repo.enrichElevation(points) } catch (_: Exception) { points }
                val km = Route(points = withEle).totalDistanceKm
                val name = "%.0f km Auto".format(targetKm) + " – " +
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                _uiState.update {
                    it.copy(
                        currentRoute = it.currentRoute.copy(
                            name = name, points = withEle,
                            description = "Auto ~%.1f km".format(km)
                        ),
                        isLoading = false,
                        message = "Generated %.2f km".format(km),
                        requestFitRoute = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = OsrmClient.friendlyNetworkError(e))
                }
            }
        }
    }

    fun onMapTap(lat: Double, lon: Double) {
        val st = _uiState.value
        if (st.moveMode && st.selectedPointIndex != null) {
            moveSelectedPoint(lat, lon)
            return
        }
        if (st.insertMode && st.selectedPointIndex != null) {
            insertPointAfter(st.selectedPointIndex, lat, lon)
            return
        }
        val pts = st.currentRoute.points
        val nearIdx = findNearestIndex(pts, lat, lon, 25.0)
        if (nearIdx != null) {
            _uiState.update { it.copy(selectedPointIndex = nearIdx) }
            return
        }
        _uiState.update { it.copy(selectedPointIndex = null, moveMode = false, insertMode = false) }
        addWaypoint(lat, lon)
    }

    fun moveSelectedPoint(lat: Double, lon: Double) {
        val idx = _uiState.value.selectedPointIndex ?: return
        val pts = _uiState.value.currentRoute.points
        if (idx !in pts.indices) return
        viewModelScope.launch {
            pushUndo()
            _uiState.update { it.copy(isLoading = true, moveMode = false) }
            try {
                val snap = _uiState.value.snapToRoad
                val newPt = if (snap) repo.nearestPoint(lat, lon) ?: TrackPoint(lat, lon)
                else TrackPoint(lat, lon)
                val next = pts.toMutableList()
                next[idx] = newPt
                val rebuilt = if (snap) repo.rebuildAroundIndex(next, idx, true) else next
                val withEle = try { repo.enrichElevation(rebuilt) } catch (_: Exception) { rebuilt }
                _uiState.update {
                    it.copy(
                        currentRoute = it.currentRoute.copy(points = withEle),
                        isLoading = false,
                        selectedPointIndex = null,
                        message = if (snap) "Point moved · path re-routed" else "Point moved"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = OsrmClient.friendlyNetworkError(e))
                }
            }
        }
    }

    fun insertPointAfter(afterIndex: Int, lat: Double, lon: Double) {
        viewModelScope.launch {
            pushUndo()
            _uiState.update { it.copy(isLoading = true, insertMode = false) }
            try {
                val pts = _uiState.value.currentRoute.points.toMutableList()
                val snap = _uiState.value.snapToRoad
                val newPt = if (snap) repo.nearestPoint(lat, lon) ?: TrackPoint(lat, lon)
                else TrackPoint(lat, lon)
                val insertAt = (afterIndex + 1).coerceIn(0, pts.size)
                pts.add(insertAt, newPt)
                val rebuilt = if (snap) repo.rebuildAroundIndex(pts, insertAt, true) else pts
                val withEle = try { repo.enrichElevation(rebuilt) } catch (_: Exception) { rebuilt }
                _uiState.update {
                    it.copy(
                        currentRoute = it.currentRoute.copy(points = withEle),
                        isLoading = false,
                        selectedPointIndex = null,
                        message = if (snap) "Point inserted · path re-routed" else "Point inserted"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = OsrmClient.friendlyNetworkError(e))
                }
            }
        }
    }

    fun deleteSelectedPoint() {
        val idx = _uiState.value.selectedPointIndex ?: return
        val pts = _uiState.value.currentRoute.points
        if (idx !in pts.indices) return
        viewModelScope.launch {
            pushUndo()
            val next = pts.toMutableList().also { it.removeAt(idx) }
            val snap = _uiState.value.snapToRoad
            val rebuilt = if (snap && next.size >= 2 && idx > 0 && idx <= next.size) {
                val bridgeIdx = (idx - 1).coerceAtLeast(0)
                try { repo.rebuildAroundIndex(next, bridgeIdx, true) } catch (_: Exception) { next }
            } else next
            val withEle = try {
                if (rebuilt.size <= 60) repo.enrichElevation(rebuilt) else rebuilt
            } catch (_: Exception) { rebuilt }
            _uiState.update {
                it.copy(
                    currentRoute = it.currentRoute.copy(points = withEle),
                    selectedPointIndex = null,
                    moveMode = false,
                    insertMode = false,
                    message = "Point deleted"
                )
            }
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPointIndex = null, insertMode = false, moveMode = false) }
    }

    private fun findNearestIndex(
        points: List<TrackPoint>, lat: Double, lon: Double, thresholdMeters: Double
    ): Int? {
        if (points.isEmpty()) return null
        var bestIdx = -1
        var bestDist = Double.MAX_VALUE
        val probe = TrackPoint(lat, lon)
        points.forEachIndexed { i, p ->
            val d = p.distanceTo(probe)
            if (d < bestDist) { bestDist = d; bestIdx = i }
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
                val newPoint = if (snap) repo.nearestPoint(lat, lon) ?: TrackPoint(lat, lon)
                else TrackPoint(lat, lon)
                val updatedPoints = if (existing.isEmpty()) listOf(newPoint)
                else if (snap) {
                    val segment = repo.routeSegment(existing.last(), newPoint)
                    if (segment.size >= 2) existing.dropLast(1) + segment else existing + newPoint
                } else existing + newPoint
                val withEle = try {
                    if (updatedPoints.size <= 40) repo.enrichElevation(updatedPoints)
                    else updatedPoints.dropLast(15) + repo.enrichElevation(updatedPoints.takeLast(15))
                } catch (_: Exception) { updatedPoints }
                val name = ensureRouteName(_uiState.value.currentRoute.name)
                _uiState.update {
                    it.copy(
                        currentRoute = it.currentRoute.copy(name = name, points = withEle),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = OsrmClient.friendlyNetworkError(e))
                }
            }
        }
    }

    fun startRecording(context: Context) {
        if (_uiState.value.isRecording) return
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine) {
            _uiState.update { it.copy(error = "Location permission required") }
            return
        }
        pushUndo()
        _uiState.update {
            it.copy(
                isRecording = true,
                currentRoute = it.currentRoute.copy(points = emptyList()),
                message = "Recording (background OK)…",
                selectedPointIndex = null,
                moveMode = false,
                insertMode = false
            )
        }
        TrackingService.onPoint = { pt ->
            _uiState.update { st ->
                if (!st.isRecording) return@update st
                st.copy(
                    currentRoute = st.currentRoute.copy(points = st.currentRoute.points + pt),
                    myLocation = pt
                )
            }
        }
        TrackingService.onStopped = {
            viewModelScope.launch {
                if (_uiState.value.isRecording) finalizeRecording()
            }
        }
        try {
            TrackingService.start(context.applicationContext)
        } catch (e: Exception) {
            _uiState.update { it.copy(isRecording = false, error = e.message ?: "Could not start tracking") }
        }
    }

    fun stopRecording() {
        val ctx = getApplication<Application>()
        TrackingService.stop(ctx)
        TrackingService.onPoint = null
        TrackingService.onStopped = null
        locationListener?.let { listener ->
            try { locationManager?.removeUpdates(listener) } catch (_: Exception) {}
        }
        locationListener = null
        locationManager = null
        finalizeRecording()
    }

    private fun finalizeRecording() {
        _uiState.update {
            it.copy(
                isRecording = false,
                message = "Recording stopped · ${it.currentRoute.points.size} points"
            )
        }
        viewModelScope.launch {
            val pts = _uiState.value.currentRoute.points
            if (pts.size >= 2) {
                try {
                    val withEle = repo.enrichElevation(pts)
                    _uiState.update {
                        it.copy(currentRoute = it.currentRoute.copy(points = withEle))
                    }
                } catch (_: Exception) { }
            }
        }
    }

    override fun onCleared() {
        if (_uiState.value.isRecording) {
            try { TrackingService.stop(getApplication()) } catch (_: Exception) {}
            TrackingService.onPoint = null
            TrackingService.onStopped = null
        }
        locationListener?.let { try { locationManager?.removeUpdates(it) } catch (_: Exception) {} }
        super.onCleared()
    }

    private fun ensureRouteName(current: String): String {
        if (current.isNotBlank() && current != "Stralarp Route") return current
        val hour = LocalTime.now().hour
        val period = when {
            hour < 5 -> "Night"; hour < 11 -> "Morning"; hour < 14 -> "Midday"
            hour < 18 -> "Afternoon"; hour < 22 -> "Evening"; else -> "Night"
        }
        return "$period Run – " + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    fun undo() {
        val stack = _uiState.value.undoStack
        if (stack.isEmpty()) return
        _uiState.update {
            it.copy(
                currentRoute = stack.last(),
                undoStack = stack.dropLast(1),
                selectedPointIndex = null,
                moveMode = false,
                insertMode = false
            )
        }
    }

    fun clearRoute() {
        pushUndo()
        _uiState.update {
            it.copy(
                currentRoute = Route(activityType = it.currentRoute.activityType),
                selectedPointIndex = null,
                moveMode = false,
                insertMode = false
            )
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

    fun fitRoute() { _uiState.update { it.copy(requestFitRoute = true) } }
    fun clearFitRequest() { _uiState.update { it.copy(requestFitRoute = false) } }

    fun importGpx(uri: Uri) {
        viewModelScope.launch {
            pushUndo()
            _uiState.update { it.copy(isLoading = true, error = null, message = null) }
            try {
                val route = repo.parseGpx(uri)
                val withEle = try {
                    if (route.points.any { it.elevation == null }) repo.enrichElevation(route.points)
                    else route.points
                } catch (_: Exception) { route.points }
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
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Import failed") }
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
                val simulated = repo.simulate(_uiState.value.currentRoute, _uiState.value.simulationParams)
                _uiState.update {
                    it.copy(
                        currentRoute = simulated, isLoading = false,
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
                val simulated = repo.simulate(_uiState.value.currentRoute, _uiState.value.simulationParams)
                val named = autoNameIfNeeded(simulated)
                _uiState.update { it.copy(currentRoute = named) }
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val file = repo.exportGpx(named, "Stralarp_$date.gpx")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/gpx+xml"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share GPX"))
                _uiState.update { it.copy(isLoading = false, message = "Simulated & ready to share") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed") }
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
                message = "Trimmed", selectedPointIndex = null
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
                        isLoading = false, message = "Elevation updated"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Elevation offline – check internet")
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
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/gpx+xml"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share GPX"))
                _uiState.update { it.copy(currentRoute = route, isLoading = false, message = "Ready to share") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Share failed") }
            }
        }
    }

    private fun autoNameIfNeeded(route: Route): Route {
        if (route.name.isNotBlank() && route.name != "Stralarp Route") return route
        return route.copy(name = ensureRouteName(route.name))
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun clearMessage() { _uiState.update { it.copy(message = null) } }
}
