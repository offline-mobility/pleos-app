package io.github.offlinemobility.app.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import io.github.offlinemobility.app.debugLog

/**
 * Lamps inside the cabin and the windows, through the Pleos Vehicle SDK.
 *
 * Required manifest permissions: pleos.car.permission.CAR_LIGHTS / CONTROL_CAR_LIGHTS and
 * CAR_WINDOWS / CONTROL_CAR_WINDOWS. Nothing is written that the capability checks did not report
 * as writable.
 */
internal class PleosBody(private val vehicle: Vehicle, private val sdk: SdkCaller) {

    private class CabinLamps(
        val cabin: LightSwitch?,
        val reading: Map<SeatArea, LightSwitch>,
        val ambient: Map<SeatArea, LightSwitch>,
    )

    private var lampsSaved: CabinLamps? = null

    /**
     * Switches the cabin, reading and ambient lamps off and remembers their switches. Returns the
     * number of lamps the vehicle refused, or null when it has none the app may switch.
     */
    suspend fun darkenCabin(): Int? {
        val light = vehicle.getLight()
        val cabin = sdk.writable<List<LightSwitch>> { ok, fail ->
            light.checkCabinLightSwitchCapabilities({ c: CapabilityConfig, v: List<LightSwitch> -> ok(c, v) }, fail)
        } != null
        val readingAreas = sdk.writable<Map<SeatArea, List<LightSwitch>>> { ok, fail ->
            light.checkReadingLightSwitchCapabilities({ c: CapabilityConfig, v: Map<SeatArea, List<LightSwitch>> -> ok(c, v) }, fail)
        }?.keys.orEmpty()
        val ambientAreas = sdk.writable<Map<SeatArea, List<LightSwitch>>> { ok, fail ->
            light.checkAmbientLightSwitchCapabilities({ c: CapabilityConfig, v: Map<SeatArea, List<LightSwitch>> -> ok(c, v) }, fail)
        }?.keys.orEmpty()
        if (!cabin && readingAreas.isEmpty() && ambientAreas.isEmpty()) return null

        val now = CabinLamps(
            cabin = if (cabin) sdk.call<LightSwitch> { done, fail -> light.getCabinLightSwitch(done, fail) }.getOrNull() else null,
            reading = readingAreas.mapNotNull { a ->
                sdk.call<LightSwitch> { done, fail -> light.getReadingLightSwitch(a, done, fail) }.getOrNull()?.let { a to it }
            }.toMap(),
            ambient = ambientAreas.mapNotNull { a -> ambientSwitch(a)?.let { a to it } }.toMap(),
        )
        if (lampsSaved == null) lampsSaved = now
        debugLog { "cabin lamps cabin=${now.cabin} reading=${now.reading.values} ambient=${now.ambient.values}" }
        return write(CabinLamps(now.cabin?.let { LightSwitch.OFF }, now.reading.off(), now.ambient.off()), skip = now)
    }

    /** The ambient switch; a lamp that reports itself lit counts as on whatever the switch says. */
    private suspend fun ambientSwitch(area: SeatArea): LightSwitch? {
        val light = vehicle.getLight()
        val switch = sdk.call<LightSwitch> { done, fail -> light.getAmbientLightSwitch(area, done, fail) }.getOrNull()
        val state = sdk.call<LightState> { done, fail -> light.getAmbientLightState(area, done, fail) }.getOrNull()
        debugLog { "ambient switch=$switch state=$state" }
        return if (state == LightState.ON && switch == LightSwitch.OFF) LightSwitch.ON else switch
    }

    /** Returns the number of lamps the vehicle refused; they stay remembered and the next call tries again. */
    suspend fun restoreCabinLamps(): Int {
        val s = lampsSaved ?: return 0
        val failures = write(s, skip = null)
        if (failures == 0) lampsSaved = null
        return failures
    }

    /** Sends [target]; a lamp already at its target in [skip] is left alone. Returns the number refused. */
    private suspend fun write(target: CabinLamps, skip: CabinLamps?): Int {
        val light = vehicle.getLight()
        var failures = 0
        fun count(result: Result<*>) {
            result.onFailure {
                failures++
                debugLog { "cabin lamp write refused: ${it.message}" }
            }
        }
        target.cabin?.takeIf { it != skip?.cabin }?.let { value ->
            count(sdk.call<LightSwitch> { done, fail -> light.setCabinLightSwitch(value, done, fail) })
        }
        for ((area, value) in target.reading) {
            if (skip?.reading?.get(area) == value) continue
            count(sdk.call<LightSwitch> { done, fail -> light.setReadingLightSwitch(area, value, done, fail) })
        }
        for ((area, value) in target.ambient) {
            if (skip?.ambient?.get(area) == value) continue
            count(sdk.call<LightSwitch> { done, fail -> light.setAmbientLightSwitch(area, value, { _, v -> done(v) }, fail) })
        }
        return failures
    }

    private fun Map<SeatArea, LightSwitch>.off() = mapValues { LightSwitch.OFF }

    /**
     * Closes every window (and roof) the app may move. Returns the number the vehicle refused, or
     * null when it has none. Position 0 is closed; the upper bound is fully open.
     */
    suspend fun closeWindows(): Int? {
        val bounds = sdk.writable<Map<WindowArea, AreaBounds<Int>>> { ok, fail ->
            vehicle.getWindow().checkWindowPositionCapabilities({ c: CapabilityConfig, v: Map<WindowArea, AreaBounds<Int>> -> ok(c, v) }, fail)
        }.orEmpty()
        if (bounds.isEmpty()) return null
        var failures = 0
        for ((area, range) in bounds) {
            val closed = WINDOW_CLOSED.coerceIn(range.minValue, range.maxValue)
            val position = sdk.call<Int> { done, fail -> vehicle.getWindow().getCurrentWindowPosition(area, done, fail) }.getOrNull()
            debugLog { "window ${area.values.joinToString()} position=$position range=${range.minValue}..${range.maxValue}" }
            if (position == closed) continue
            sdk.call<Int> { done, fail ->
                vehicle.getWindow().setWindowPosition(area, closed, { _, v -> done(v) }, fail)
            }.onFailure {
                failures++
                debugLog { "window close refused: ${it.message}" }
            }
        }
        return failures
    }

    private companion object {
        const val WINDOW_CLOSED = 0
    }
}
