package com.stralarp.app.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.stralarp.app.data.model.TrackPoint
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay

@Composable
fun OsmdroidMap(
    modifier: Modifier = Modifier,
    points: List<TrackPoint>,
    onMapClick: (Double, Double) -> Unit,
    /** When true, zoom to fit the current route once, then caller should clear the flag. */
    fitRouteRequest: Boolean = false,
    onFitHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(47.5, 19.0))
            val rotationOverlay = RotationGestureOverlay(this)
            rotationOverlay.isEnabled = true
            overlays.add(rotationOverlay)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    LaunchedEffect(Unit) {
        mapView.overlays.add(object : org.osmdroid.views.overlay.Overlay() {
            override fun onSingleTapConfirmed(e: android.view.MotionEvent?, mapView: MapView?): Boolean {
                val projection = mapView?.projection ?: return false
                val geo = projection.fromPixels(e!!.x.toInt(), e.y.toInt()) as GeoPoint
                onMapClick(geo.latitude, geo.longitude)
                return true
            }
        })
    }

    // Draw route — never auto-zoom on every point
    LaunchedEffect(points) {
        mapView.overlays.removeAll {
            it is Polyline || (it is Marker && it.title in listOf("Start", "Finish"))
        }

        if (points.isNotEmpty()) {
            val geoPoints = points.map { GeoPoint(it.lat, it.lon) }
            val line = Polyline().apply {
                setPoints(geoPoints)
                outlinePaint.color = AndroidColor.parseColor("#00E676")
                outlinePaint.strokeWidth = 12f
            }
            mapView.overlays.add(line)

            mapView.overlays.add(Marker(mapView).apply {
                position = geoPoints.first()
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Start"
            })

            if (geoPoints.size > 1) {
                mapView.overlays.add(Marker(mapView).apply {
                    position = geoPoints.last()
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Finish"
                })
            }
        }
        mapView.invalidate()
    }

    // Fit only when requested (Fit button / import)
    LaunchedEffect(fitRouteRequest, points) {
        if (fitRouteRequest && points.isNotEmpty()) {
            try {
                val geoPoints = points.map { GeoPoint(it.lat, it.lon) }
                mapView.zoomToBoundingBox(
                    org.osmdroid.util.BoundingBox.fromGeoPoints(geoPoints),
                    true,
                    100
                )
            } catch (_: Exception) { }
            onFitHandled()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
