package io.github.offlinemobility.app

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle

class PleosVehicleClient(context: Context) {
    private val vehicle = Vehicle(context.applicationContext)
    private var initialized = false

    @Synchronized
    fun initialize() {
        if (initialized) return
        vehicle.initialize()
        initialized = true
    }

    @Synchronized
    fun release() {
        if (!initialized) return
        vehicle.release()
        initialized = false
    }
}

