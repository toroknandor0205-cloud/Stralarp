package com.stralarp.app.viewmodel

import android.app.Application
import android.net.Uri
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

data class AppUiState(
    val currentRoute: Route = Route(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val snapToRoad: Boolean = true,
    val simulationParams: SimulationParams = SimulationParams(),
    val exportedFile: java.io.File? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = RouteRepository(application)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    fun setSnapToRoad(enabled: Boolean) {
        _uiState.update { it.copy(snapToRoad = enabled) }
    }

    fun addWaypoint(lat: Double, lon: Double) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val point = if (_uiState.value.snapToRoad) {
                    repo.nearestPoint(lat, lon) ?: TrackPoint(lat, lon)
                } else {
                    TrackPoint(lat, lon)
                }
                val newPoints = _uiState.value.currentRoute.points + point
                val enriched = if (newPoints.size > 1 && _uiState.value.snapToRoad) {
                    repo.snapWaypoints(newPoints)
                } else newPoints
                val withEle = repo.enrichElevation(enriched)
                _uiState.update {
                    it.copy(
                        currentRoute = it.currentRoute.copy(points = withEle),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun undoLastPoint() {
        _uiState.update {
            val pts = it.currentRoute.points.dropLast(1)
            it.copy(currentRoute = it.currentRoute.copy(points = pts))
        }
    }

    fun clearRoute() {
        _uiState.update { it.copy(currentRoute = Route()) }
    }

    fun reverseRoute() {
        _uiState.update {
            it.copy(currentRoute = it.currentRoute.copy(points = it.currentRoute.points.reversed()))
        }
    }

    fun closeLoop() {
        val pts = _uiState.value.currentRoute.points
        if (pts.size < 2) return
        val first = pts.first()
        addWaypoint(first.lat, first.lon)
    }

    fun importGpx(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val route = repo.parseGpx(uri)
                val withEle = if (route.points.any { it.elevation == null }) {
                    repo.enrichElevation(route.points)
                } else route.points
                _uiState.update {
                    it.copy(
                        currentRoute = route.copy(points = withEle),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun updateSimulationParams(params: SimulationParams) {
        _uiState.update { it.copy(simulationParams = params) }
    }

    fun runSimulation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val simulated = repo.simulate(_uiState.value.currentRoute, _uiState.value.simulationParams)
                _uiState.update {
                    it.copy(currentRoute = simulated, isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun exportGpx(fileName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val file = repo.exportGpx(_uiState.value.currentRoute, fileName)
                _uiState.update { it.copy(exportedFile = file, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun writeToDocument(uri: Uri) {
        viewModelScope.launch {
            repo.writeToUri(_uiState.value.currentRoute, uri)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun updateMetadata(name: String, desc: String, type: String) {
        _uiState.update {
            it.copy(currentRoute = it.currentRoute.copy(name = name, description = desc, activityType = type))
        }
    }

    fun trimRoute(startPercent: Float, endPercent: Float) {
        val pts = _uiState.value.currentRoute.points
        if (pts.isEmpty()) return
        val startIdx = (pts.size * startPercent / 100f).toInt().coerceIn(0, pts.size - 1)
        val endIdx = (pts.size * endPercent / 100f).toInt().coerceIn(startIdx + 1, pts.size)
        _uiState.update {
            it.copy(currentRoute = it.currentRoute.copy(points = pts.subList(startIdx, endIdx)))
        }
    }
}
