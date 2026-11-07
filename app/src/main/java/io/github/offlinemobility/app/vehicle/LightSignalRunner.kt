package io.github.offlinemobility.app.vehicle

import io.github.offlinemobility.app.core.light.Lamp
import io.github.offlinemobility.app.core.light.LampSwitch
import io.github.offlinemobility.app.core.light.LightPattern
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
)

/**
 * Plays a [LightPattern] on the given lamps and always restores each lamp's original switch value,
 * including when the coroutine is cancelled (user stop, gear leaves P).
 */
class LightSignalRunner(private val gateway: VehicleGateway) {

    suspend fun run(
        pattern: LightPattern,
        lamps: List<Lamp>,
        repeat: Int = 1,
        onProgress: (step: Int, total: Int, on: Boolean) -> Unit = { _, _, _ -> },
    ): LightRunReport {
        val originals = lamps.associateWith { gateway.readLamp(it).getOrDefault(LampSwitch.OFF) }
        var failures = 0
        var latencyTotal = 0L
        var switches = 0
        var lastError: String? = null
        var done = 0
        val total = pattern.steps.size * repeat

        try {
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
                    delay((step.durationMs - latency).coerceAtLeast(0))
                }
            }
        } finally {
            withContext(NonCancellable) {
                for ((lamp, value) in originals) gateway.setLamp(lamp, value)
            }
        }
        return LightRunReport(
            stepsDone = done,
            failures = failures,
            averageSwitchLatencyMs = if (switches == 0) 0 else latencyTotal / switches,
            lastError = lastError,
        )
    }
}
