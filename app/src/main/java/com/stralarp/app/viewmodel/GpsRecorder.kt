package com.stralarp.app.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.stralarp.app.data.model.TrackPoint

class GpsRecorder(private val onLocation: (TrackPoint) -> Unit) {
    private var lm: LocationManager? = null
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onLocation(
                TrackPoint(
                    location.latitude,
                    location.longitude,
                    elevation = if (location.hasAltitude()) location.altitude else null
                )
            )
        }
        @Deprecated("Deprecated")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun start(ctx: Context) {
        lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 3f, listener)
            lm?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 5f, listener)
        } catch (_: SecurityException) {}
    }

    fun stop() {
        lm?.removeUpdates(listener)
        lm = null
    }
}
