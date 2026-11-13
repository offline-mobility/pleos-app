package io.github.offlinemobility.app.vehicle

import io.github.offlinemobility.app.core.cabin.ClimateTarget
import io.github.offlinemobility.app.core.cabin.KeepWarm
import io.github.offlinemobility.app.core.cabin.KeepWarmController
import io.github.offlinemobility.app.debugLog
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class KeepWarmTick(
    val target: ClimateTarget,
    val cabinC: Double?,
    /** Settings the vehicle is still refusing; they are sent again on the next tick. */
    val failures: Int,
)

/**
 * Runs the energy-saving heating plan until cancelled (user stop, gear leaves P) and then always
 * puts the climate settings back the way they were.
 */
class KeepWarmRunner(private val gateway: VehicleGateway) {

    suspend fun run(controller: KeepWarmController, people: Int, onTick: (KeepWarmTick) -> Unit) {
        val saved = gateway.saveClimate()
        val seats = KeepWarm.seatsToHeat(gateway.occupiedSeats(), people)
        debugLog { "keep warm start saved=$saved seats=$seats" }
        val started = System.currentTimeMillis()
        try {
            while (true) {
                val cabin = gateway.cabinTemperature()
                val target = controller.next(cabin, System.currentTimeMillis() - started)
                val failures = gateway.applyClimate(target, seats)
                debugLog { "keep warm cabin=$cabin target=$target failures=$failures" }
                onTick(KeepWarmTick(target, cabin, failures))
                delay(KeepWarmController.TICK_MS)
            }
        } finally {
            withContext(NonCancellable) {
                val notRestored = gateway.restoreClimate()
                debugLog { "keep warm restored, failures=$notRestored" }
            }
        }
    }
}
