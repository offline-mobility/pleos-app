package io.github.offlinemobility.app.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import io.github.offlinemobility.app.core.cabin.ClimateTarget
import io.github.offlinemobility.app.core.cabin.Seat
import io.github.offlinemobility.app.debugLog
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Heater, seat heating and steering wheel heating through the Pleos Vehicle SDK.
 *
 * Required manifest permissions: pleos.car.permission.CAR_CLIMATE / CONTROL_CAR_CLIMATE and
 * CAR_SEATS / CONTROL_CAR_SEATS.
 *
 * Areas and value ranges differ per vehicle (one or two temperature zones, fan steps, seat heater
 * steps), so nothing is written outside what the capability checks in [support] reported.
 */
internal class PleosClimate(private val vehicle: Vehicle, private val sdk: SdkCaller) {
    private var powerAreas: List<SeatArea> = emptyList()
    private var tempBounds: Map<SeatArea, AreaBounds<Float>> = emptyMap()
    private var fanBounds: Map<SeatArea, AreaBounds<Int>> = emptyMap()
    private var recircAreas: List<SeatArea> = emptyList()
    private var acAreas: List<SeatArea> = emptyList()
    private var cabinAreas: List<SeatArea> = emptyList()
    private var seatBounds: Map<SeatArea, AreaBounds<Int>> = emptyMap()
    private var wheelBounds: AreaBounds<Int>? = null

    private class Saved(
        val power: Map<SeatArea, Boolean>,
        var temp: Map<SeatArea, Float>,
        var fan: Map<SeatArea, Int>,
        var recirc: Map<SeatArea, Boolean>,
        var ac: Map<SeatArea, Boolean>,
        val seats: Map<SeatArea, Int>,
        val wheel: Int?,
    ) {
        val isEmpty get() = power.isEmpty() && temp.isEmpty() && seats.isEmpty() && wheel == null
    }

    /** What the vehicle has accepted so far; a refused setting stays unset here and is sent again on the next call. */
    private class Applied(
        var power: Boolean? = null,
        var setpointC: Float? = null,
        var fan: Int? = null,
        var freshAir: Boolean? = null,
        var acOff: Boolean = false,
        var seatLevel: Int? = null,
        var seats: Set<Seat> = emptySet(),
        var wheelLevel: Int? = null,
    )

    private var saved: Saved? = null
    private var applied = Applied()

    suspend fun support(): ClimateSupport {
        val hvac = vehicle.getHVAC()
        powerAreas = writable<List<SeatArea>> { ok, fail ->
            hvac.checkHvacPowerCapabilities({ c: CapabilityConfig, v: List<SeatArea> -> ok(c, v) }, fail)
        }.orEmpty()
        tempBounds = writable<Map<SeatArea, AreaBounds<Float>>> { ok, fail ->
            hvac.checkHvacTemperatureCapabilities({ c, v, _ -> ok(c, v) }, fail)
        }.orEmpty()
        fanBounds = writable<Map<SeatArea, AreaBounds<Int>>> { ok, fail ->
            hvac.checkHvacFanSpeedCapabilities({ c: CapabilityConfig, v: Map<SeatArea, AreaBounds<Int>> -> ok(c, v) }, fail)
        }.orEmpty()
        recircAreas = writable<List<SeatArea>> { ok, fail ->
            hvac.checkHvacRecirculationCapabilities({ c: CapabilityConfig, v: List<SeatArea> -> ok(c, v) }, fail)
        }.orEmpty()
        acAreas = writable<List<SeatArea>> { ok, fail ->
            hvac.checkHvacAcCapabilities({ c: CapabilityConfig, v: List<SeatArea> -> ok(c, v) }, fail)
        }.orEmpty()
        cabinAreas = sdk.call<List<SeatArea>> { done, fail ->
            hvac.checkHvacTemperatureCurrentCapabilities({ _: CapabilityConfig, v: List<SeatArea> -> done(v) }, fail)
        }.getOrNull().orEmpty()
        seatBounds = writable<Map<SeatArea, AreaBounds<Int>>> { ok, fail ->
            vehicle.getSeat().checkSeatTemperatureCapabilities({ c: CapabilityConfig, v: Map<SeatArea, AreaBounds<Int>> -> ok(c, v) }, fail)
        }.orEmpty()
        wheelBounds = writable<AreaBounds<Int>> { ok, fail ->
            vehicle.getSteeringWheel().checkHvacSteeringWheelHeatCapabilities({ c: CapabilityConfig, v: AreaBounds<Int> -> ok(c, v) }, fail)
        }
        debugLog { "climate power=${powerAreas.size} temp=${tempBounds.values} fan=${fanBounds.values} seats=${seatBounds.values} wheel=$wheelBounds" }
        return ClimateSupport(
            heater = powerAreas.isNotEmpty() && tempBounds.isNotEmpty(),
            seatHeat = seatBounds.isNotEmpty(),
            wheelHeat = wheelBounds != null,
        )
    }

    suspend fun save(): Boolean {
        val snapshot = Saved(
            power = powerAreas.read { a, ok, fail -> vehicle.getHVAC().getHvacPowerState(a, ok, fail) },
            temp = readTemperatures(),
            fan = readFans(),
            recirc = readRecirculation(),
            ac = readAc(),
            seats = seatBounds.keys.read { a, ok, fail -> vehicle.getSeat().getHvacSeatTemperature(a, ok, fail) },
            wheel = if (wheelBounds == null) null
            else sdk.call<Int> { done, fail -> vehicle.getSteeringWheel().getHvacSteeringWheelHeatState(done, fail) }.getOrNull(),
        )
        debugLog { "climate saved power=${snapshot.power.values} temp=${snapshot.temp.values} fan=${snapshot.fan.values} seats=${snapshot.seats.values} wheel=${snapshot.wheel}" }
        saved = snapshot.takeUnless { it.isEmpty }
        applied = Applied()
        return saved != null
    }

    /** Sends only what the vehicle has not accepted yet. Returns the number of settings it refused this time. */
    suspend fun apply(target: ClimateTarget, seats: Set<Seat>): Int {
        var failures = 0
        if (applied.power != target.hvacOn) {
            if (setPower(powerAreas, target.hvacOn) == 0) {
                applied.power = target.hvacOn
                if (target.hvacOn) {
                    // The vehicle needs a moment after switching on before it takes further settings.
                    delay(POWER_SETTLE_MS)
                    completeSnapshot()
                } else {
                    // Values are sent again after the next switch-on.
                    applied.setpointC = null
                    applied.fan = null
                    applied.freshAir = null
                }
            } else {
                failures++
            }
        }
        if (target.hvacOn && applied.power == true) {
            if (applied.setpointC != target.setpointC) {
                if (setTemperature(tempBounds.keys.associateWith { target.setpointC }) == 0) applied.setpointC = target.setpointC else failures++
            }
            if (applied.fan != target.fan) {
                if (setFan(fanBounds.keys.associateWith { target.fan }) == 0) applied.fan = target.fan else failures++
            }
            if (applied.freshAir != target.freshAir) {
                if (setRecirculation(recircAreas.associateWith { !target.freshAir }) == 0) applied.freshAir = target.freshAir else failures++
            }
            if (!applied.acOff) {
                if (setAc(acAreas.associateWith { false }) == 0) applied.acOff = true else failures++
            }
        }
        if (applied.seatLevel != target.seatLevel || applied.seats != seats) {
            val refused = setSeats(seats.mapNotNull { seat -> seatArea(seat)?.let { it to target.seatLevel } }.toMap())
            if (refused == 0) {
                applied.seatLevel = target.seatLevel
                applied.seats = seats
            } else {
                failures++
            }
        }
        if (applied.wheelLevel != target.wheelLevel) {
            if (setWheel(target.wheelLevel) == 0) applied.wheelLevel = target.wheelLevel else failures++
        }
        return failures
    }

    /**
     * Temperature, fan and air source cannot be read while the system is off. When the plan is the
     * one that switched it on, read them now, before they are changed, so they can be restored.
     */
    private suspend fun completeSnapshot() {
        val s = saved ?: return
        if (s.temp.isEmpty()) s.temp = readTemperatures()
        if (s.fan.isEmpty()) s.fan = readFans()
        if (s.recirc.isEmpty()) s.recirc = readRecirculation()
        if (s.ac.isEmpty()) s.ac = readAc()
    }

    suspend fun restore(): Int {
        val s = saved ?: return 0
        saved = null
        applied = Applied()
        // Most settings are only accepted while the system is on: switch everything on, restore
        // the values, and switch off what was off last.
        var failures = setPower(s.power.keys, true)
        delay(POWER_SETTLE_MS)
        failures += setTemperature(s.temp)
        failures += setFan(s.fan)
        failures += setRecirculation(s.recirc)
        failures += setAc(s.ac)
        failures += setSeats(s.seats)
        s.wheel?.let { failures += setWheel(it) }
        failures += setPower(s.power.filterValues { !it }.keys, false)
        return failures
    }

    private suspend fun readTemperatures() = tempBounds.keys.read<Float> { a, ok, fail ->
        vehicle.getHVAC().getHvacTemperature(a, TemperatureUnit.CELSIUS, ok, fail)
    }

    private suspend fun readFans() = fanBounds.keys.read<Int> { a, ok, fail -> vehicle.getHVAC().getHvacFanSpeed(a, ok, fail) }

    private suspend fun readRecirculation() = recircAreas.read<Boolean> { a, ok, fail -> vehicle.getHVAC().getHvacRecirculationState(a, ok, fail) }

    private suspend fun readAc() = acAreas.read<Boolean> { a, ok, fail -> vehicle.getHVAC().getHvacAcState(a, ok, fail) }

    suspend fun cabinTemperature(): Double? {
        val area = cabinAreas.firstOrNull() ?: powerAreas.firstOrNull() ?: return null
        return sdk.call<Float> { done, fail ->
            vehicle.getHVAC().getHvacTemperatureCurrent(area, TemperatureUnit.CELSIUS, done, fail)
        }.getOrNull()?.toDouble()
    }

    suspend fun occupiedSeats(): Set<Seat>? {
        val readings = Seat.entries.mapNotNull { seat ->
            sdk.call<Boolean> { done, fail ->
                vehicle.getSeat().getCurrentSeatOccupancy(SeatArea(seat.value), done, fail)
            }.getOrNull()?.let { seat to it }
        }
        return if (readings.isEmpty()) null else readings.filter { it.second }.map { it.first }.toSet()
    }

    // ---------------------------------------------------------------- writes

    private suspend fun setPower(areas: Collection<SeatArea>, on: Boolean): Int = areas.write { a, ok, fail ->
        vehicle.getHVAC().setHvacPower(a, on, { _, _ -> ok() }, fail)
    }

    private suspend fun setTemperature(values: Map<SeatArea, Float>): Int = values.keys.write { a, ok, fail ->
        val bounds = tempBounds[a]
        // Vehicles accept 0.5 °C steps.
        var celsius = (values.getValue(a) * 2).roundToInt() / 2f
        if (bounds != null) celsius = celsius.coerceIn(bounds.minValue, bounds.maxValue)
        vehicle.getHVAC().setHvacTemperature(a, celsius, TemperatureUnit.CELSIUS, { _, _ -> ok() }, fail)
    }

    private suspend fun setFan(values: Map<SeatArea, Int>): Int = values.keys.write { a, ok, fail ->
        val bounds = fanBounds[a]
        val speed = values.getValue(a).let { if (bounds != null) it.coerceIn(bounds.minValue, bounds.maxValue) else it }
        vehicle.getHVAC().setHvacFanSpeed(a, speed, { _, _ -> ok() }, fail)
    }

    private suspend fun setRecirculation(values: Map<SeatArea, Boolean>): Int = values.keys.write { a, ok, fail ->
        vehicle.getHVAC().setHvacRecirculation(a, values.getValue(a), { _, _ -> ok() }, fail)
    }

    private suspend fun setAc(values: Map<SeatArea, Boolean>): Int = values.keys.write { a, ok, fail ->
        vehicle.getHVAC().setHvacAc(a, values.getValue(a), { _, _ -> ok() }, fail)
    }

    private suspend fun setSeats(values: Map<SeatArea, Int>): Int = values.keys.write { a, ok, fail ->
        val bounds = seatBounds[a]
        // Negative steps are seat ventilation on some vehicles; restoring a saved value keeps them.
        val level = values.getValue(a).let { if (bounds != null) it.coerceIn(bounds.minValue, bounds.maxValue) else it }
        vehicle.getSeat().setHvacSeatTemperature(a, level, { _, _ -> ok() }, fail)
    }

    private suspend fun setWheel(level: Int): Int {
        val bounds = wheelBounds ?: return 0
        val result = sdk.call<Unit> { done, fail ->
            vehicle.getSteeringWheel().setHvacSteeringWheelHeat(level.coerceIn(bounds.minValue, bounds.maxValue), { _ -> done(Unit) }, fail)
        }
        return if (result.isSuccess) 0 else 1
    }

    // ---------------------------------------------------------------- helpers

    private fun seatArea(seat: Seat): SeatArea? = seatBounds.keys.firstOrNull { seat.value in it.values }

    private val Seat.value: SeatValue
        get() = when (this) {
            Seat.DRIVER -> SeatValue.ROW_1_LEFT
            Seat.PASSENGER -> SeatValue.ROW_1_RIGHT
            Seat.REAR_LEFT -> SeatValue.ROW_2_LEFT
            Seat.REAR_RIGHT -> SeatValue.ROW_2_RIGHT
        }

    /** Result of a capability check, or null when the vehicle does not let the app write it. */
    private suspend fun <V : Any> writable(
        check: (ok: (CapabilityConfig, V) -> Unit, fail: (Exception) -> Unit) -> Unit,
    ): V? = sdk.call<Pair<CapabilityConfig, V>> { done, fail -> check({ config, value -> done(config to value) }, fail) }
        .getOrNull()
        ?.takeIf { it.first.accessType == AccessType.WRITE || it.first.accessType == AccessType.READ_WRITE }
        ?.second

    private suspend fun <T> Collection<SeatArea>.read(
        get: (SeatArea, (T?) -> Unit, (Exception) -> Unit) -> Unit,
    ): Map<SeatArea, T> = mapNotNull { area ->
        sdk.call<T> { done, fail -> get(area, done, fail) }.getOrNull()?.let { area to it }
    }.toMap()

    /** Number of areas the vehicle refused. */
    private suspend fun Collection<SeatArea>.write(
        set: (SeatArea, () -> Unit, (Exception) -> Unit) -> Unit,
    ): Int = count { area ->
        val result = sdk.call<Unit> { done, fail -> set(area, { done(Unit) }, fail) }
        result.onFailure { debugLog { "climate write refused: ${it.message}" } }
        result.isFailure
    }

    private companion object {
        const val POWER_SETTLE_MS = 500L
    }
}
