package io.github.offlinemobility.app.vehicle

import io.github.offlinemobility.app.debugLog
import io.github.offlinemobility.app.core.light.Lamp
import io.github.offlinemobility.app.core.light.LampSwitch
import io.github.offlinemobility.app.core.light.LightPattern
import io.github.offlinemobility.app.core.light.restoreOrder
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class LightRunReport(
    val stepsDone: Int,
    val failures: Int,
    val averageSwitchLatencyMs: Long,
    val lastError: String?,
    /** Whether each flashing lamp was reported lit at least once while switched on (null: unreadable). */
    val litConfirmed: Map<Lamp, Boolean?> = emptyMap(),
)

/**
 * Plays a [LightPattern] on the given lamps and always restores each lamp's original switch value,
 * including when the coroutine is cancelled (user stop, gear leaves P).
 */
class LightSignalRunner(private val gateway: VehicleGateway) {

    suspend fun run(
        pattern: LightPattern,
        lamps: List<Lamp>,
        steady: List<Lamp> = emptyList(),
        repeat: Int = 1,
        onProgress: (step: Int, total: Int, on: Boolean) -> Unit = { _, _, _ -> },
    ): LightRunReport {
        // The tail lamp shares the headlamp switch with the low beam, so it is saved and restored too.
        val touched = (lamps + steady).distinct()
        val related = if (Lamp.LOW_BEAM in touched) listOf(Lamp.TAIL_LAMP) else emptyList()
        val originals = (touched + related).distinct()
            .mapNotNull { lamp -> gateway.readLamp(lamp).getOrNull()?.let { lamp to it } }
            .toMap()
        debugLog { "originals=$originals" }
        val lit = lamps.associateWith<Lamp, Boolean?> { null }.toMutableMap()
        var failures = 0
        var latencyTotal = 0L
        var switches = 0
        var lastError: String? = null
        var done = 0
        val total = pattern.steps.size * repeat

        try {
            for (lamp in steady) gateway.setLamp(lamp, LampSwitch.ON)
            repeat(repeat) {
                for (step in pattern.steps) {
                    val started = System.currentTimeMillis()
                    val results = coroutineScope {
                        lamps.map { lamp ->
                            async { gateway.setLamp(lamp, if (step.on) LampSwitch.ON else LampSwitch.OFF) }
                        }.awaitAll()
                    }
                    val latency = System.currentTimeMillis() - started
                    latencyTotal += latency
                    switches++
                    results.filter { it.isFailure }.forEach {
                        failures++
                        lastError = it.exceptionOrNull()?.message
                    }
                    done++
                    onProgress(done, total, step.on)
                    if (step.on) {
                        for (lamp in lamps) {
                            if (lit[lamp] == true) continue
                            gateway.isLampLit(lamp).onSuccess { lit[lamp] = it || (lit[lamp] ?: false) }
                        }
                    }
                    delay((step.durationMs - latency).coerceAtLeast(0))
                }
            }
        } finally {
            withContext(NonCancellable) {
                restoreOrder(originals).forEach { (lamp, value) -> gateway.setLamp(lamp, value) }
            }
        }
        return LightRunReport(
            stepsDone = done,
            failures = failures,
            averageSwitchLatencyMs = if (switches == 0) 0 else latencyTotal / switches,
            lastError = lastError,
            litConfirmed = lit,
        )
    }
}
