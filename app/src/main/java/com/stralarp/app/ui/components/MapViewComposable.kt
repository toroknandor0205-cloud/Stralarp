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
    /** Only auto-fit when the route first appears or is replaced (e.g. import). */
    fitWhenEmptyToNonEmpty: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            // Default center roughly Europe; user will pan
            controller.setCenter(GeoPoint(47.5, 19.0))
            val rotationOverlay = RotationGestureOverlay(this)
            rotationOverlay.isEnabled = true
            overlays.add(rotationOverlay)
        }
    }

    var previousCount by remember { mutableIntStateOf(0) }

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

    // Click listener once
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

    // Redraw route – do NOT zoom on every point add
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

            val start = Marker(mapView).apply {
                position = geoPoints.first()
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Start"
            }
            mapView.overlays.add(start)

            if (geoPoints.size > 1) {
                val finish = Marker(mapView).apply {
                    position = geoPoints.last()
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Finish"
                }
                mapView.overlays.add(finish)
            }

            // Only fit bounds when going from empty → has points (import / first draw)
            // or when point count jumps a lot (e.g. after snap of many points)
            val shouldFit = fitWhenEmptyToNonEmpty &&
                    (previousCount == 0 && points.isNotEmpty()) ||
                    (points.size - previousCount > 5)

            if (shouldFit) {
                try {
                    mapView.zoomToBoundingBox(
                        org.osmdroid.util.BoundingBox.fromGeoPoints(geoPoints),
                        false, // animate = false to feel less jumpy
                        80
                    )
                } catch (_: Exception) { }
            }
        }
        previousCount = points.size
        mapView.invalidate()
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}
