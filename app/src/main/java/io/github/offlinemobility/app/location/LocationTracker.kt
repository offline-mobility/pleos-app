package io.github.offlinemobility.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Fix(val latitude: Double, val longitude: Double, val accuracyM: Float, val timeMs: Long)

/**
 * Own position from satellite GPS, which keeps working when cellular and internet are down.
 * The network provider is used as an extra source only when the device offers it.
 * Positions stay in memory; they leave the car only inside a frame the user chooses to send.
 */
class LocationTracker(private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private val _fix = MutableStateFlow<Fix?>(null)
    val fix: StateFlow<Fix?> = _fix.asStateFlow()

    private var running = false

    private val listener = LocationListener { location -> accept(location) }

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (running || !hasPermission() || manager == null) return
        running = true
        val providers = manager.getProviders(true).filter {
            it == LocationManager.GPS_PROVIDER || it == LocationManager.NETWORK_PROVIDER
        }
        for (provider in providers) {
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()?.let(::accept)
            runCatching {
                manager.requestLocationUpdates(provider, UPDATE_MS, UPDATE_M, listener, Looper.getMainLooper())
            }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { manager?.removeUpdates(listener) }
    }

    /** Latest fix if it is recent enough to tell rescuers where the car is now. */
    fun current(nowMs: Long = System.currentTimeMillis()): Fix? =
        _fix.value?.takeIf { nowMs - it.timeMs <= MAX_AGE_MS }

    private fun accept(location: Location) {
        val previous = _fix.value
        // Keep the more accurate of two fixes taken close together.
        if (previous != null && location.time - previous.timeMs < 2_000 && location.accuracy > previous.accuracyM) return
        _fix.value = Fix(location.latitude, location.longitude, location.accuracy, location.time)
    }

    private companion object {
        const val UPDATE_MS = 5_000L
        const val UPDATE_M = 10f
        const val MAX_AGE_MS = 10 * 60_000L
    }
}
