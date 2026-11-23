package com.stralarp.app.viewmodel

import android.app.Application
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
    val simulationParams: SimulationParams = SimulationParams()
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
                val snapped = if (newPoints.size > 1 && _uiState.value.snapToRoad) {
                    repo.snapWaypoints(newPoints)
                } else newPoints
                val withEle = repo.enrichElevation(snapped)
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
            it.copy(currentRoute = it.currentRoute.copy(points = it.currentRoute.points.dropLast(1)))
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
        if (pts.size >= 2) {
            val first = pts.first()
            addWaypoint(first.lat, first.lon)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
