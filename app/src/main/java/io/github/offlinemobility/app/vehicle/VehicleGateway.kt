package io.github.offlinemobility.app.vehicle

import io.github.offlinemobility.app.core.cabin.ClimateTarget
import io.github.offlinemobility.app.core.cabin.Seat
import io.github.offlinemobility.app.core.energy.EnergyReading
import io.github.offlinemobility.app.core.light.Lamp
import io.github.offlinemobility.app.core.light.LampSwitch
import io.github.offlinemobility.app.core.safety.GearState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LampSupport(val label: String) {
    WRITABLE("제어 가능"),
    READ_ONLY("조회만 가능"),
    UNSUPPORTED("미지원"),
    UNKNOWN("확인 실패"),
}

/** Which parts of the climate system the app may control on this vehicle. */
data class ClimateSupport(val heater: Boolean = false, val seatHeat: Boolean = false, val wheelHeat: Boolean = false)

/** Vehicle functions the app needs, independent of the SDK so the UI and tests can use a fake. */
interface VehicleGateway {
    val isSimulated: Boolean
    val gear: StateFlow<GearState>

    suspend fun connect(): Result<Unit>
    suspend fun lampSupport(lamp: Lamp): LampSupport
    suspend fun readLamp(lamp: Lamp): Result<LampSwitch>
    suspend fun setLamp(lamp: Lamp, value: LampSwitch): Result<Unit>

    /** Actual lamp output reported by the vehicle, which can differ from the requested switch. */
    suspend fun isLampLit(lamp: Lamp): Result<Boolean>

    /** Battery / fuel levels; fields the vehicle does not report stay null. */
    suspend fun energy(): EnergyReading

    suspend fun climateSupport(): ClimateSupport

    /** Remembers the current climate settings so [restoreClimate] can put them back. False if nothing could be read. */
    suspend fun saveClimate(): Boolean

    /** Applies [target]; seat heating goes to [seats] only. Returns how many settings the vehicle refused this time. */
    suspend fun applyClimate(target: ClimateTarget, seats: Set<Seat>): Int

    /** Puts back what [saveClimate] remembered. Returns the number of settings that could not be restored. */
    suspend fun restoreClimate(): Int

    /** Cabin air temperature in °C, or null when the vehicle does not report it. */
    suspend fun cabinTemperature(): Double?

    /** Seats the vehicle reports as occupied, or null when it does not report occupancy. */
    suspend fun occupiedSeats(): Set<Seat>?

    /**
     * Cabin, reading and ambient lamps off, remembering their switches for [restoreCabinLamps].
     * Returns how many the vehicle refused, or null when it has none the app may switch.
     */
    suspend fun darkenCabin(): Int?

    /** Returns the number of lamps that could not be put back. */
    suspend fun restoreCabinLamps(): Int

    /** Closes every window the app may move. Returns how many the vehicle refused, or null when it has none. */
    suspend fun closeWindows(): Int?

    /**
     * Ventilation to recirculation and, with [off], switched off, remembering how it was for
     * [restoreOutsideAir]. Returns how many settings the vehicle refused, or null when it has no
     * ventilation the app may control.
     */
    suspend fun shutOutsideAir(off: Boolean): Int?

    /** Returns the number of settings that could not be put back. */
    suspend fun restoreOutsideAir(): Int
    fun release()
}

/**
 * Stand-in used when no Pleos vehicle service is reachable (phone, plain emulator).
 * Every lamp is writable and the gear is driven from the UI so the full flow can be demonstrated.
 */
class SimulatedVehicleGateway : VehicleGateway {
    override val isSimulated: Boolean = true

    private val _gear = MutableStateFlow(GearState.PARK)
    override val gear: StateFlow<GearState> = _gear.asStateFlow()

    private val lamps = Lamp.entries.associateWith { LampSwitch.OFF }.toMutableMap()
    private val _lampStates = MutableStateFlow(lamps.toMap())
    val lampStates: StateFlow<Map<Lamp, LampSwitch>> = _lampStates.asStateFlow()

    fun setGear(value: GearState) {
        _gear.value = value
    }

    override suspend fun connect(): Result<Unit> = Result.success(Unit)

    override suspend fun lampSupport(lamp: Lamp): LampSupport = LampSupport.WRITABLE

    override suspend fun readLamp(lamp: Lamp): Result<LampSwitch> = Result.success(lamps.getValue(lamp))

    override suspend fun setLamp(lamp: Lamp, value: LampSwitch): Result<Unit> {
        lamps[lamp] = value
        _lampStates.value = lamps.toMap()
        return Result.success(Unit)
    }

    override suspend fun isLampLit(lamp: Lamp): Result<Boolean> = Result.success(lamps.getValue(lamp) == LampSwitch.ON)

    override suspend fun energy(): EnergyReading = EnergyReading(evRemainingWh = 54_000.0, evPercent = 70)

    private var climate = ClimateTarget(hvacOn = true, setpointC = 22f, fan = 3, freshAir = true, seatLevel = 0, wheelLevel = 0)
    private var savedClimate: ClimateTarget? = null
    private var cabinC = 20.0

    override suspend fun climateSupport() = ClimateSupport(heater = true, seatHeat = true, wheelHeat = true)

    override suspend fun saveClimate(): Boolean {
        savedClimate = climate
        return true
    }

    override suspend fun applyClimate(target: ClimateTarget, seats: Set<Seat>): Int {
        climate = target
        return 0
    }

    override suspend fun restoreClimate(): Int {
        savedClimate?.let { climate = it }
        savedClimate = null
        return 0
    }

    /** Drifts towards the setpoint while heating and cools while the heater rests, so the cycle is visible. */
    override suspend fun cabinTemperature(): Double {
        cabinC = if (climate.hvacOn && cabinC < climate.setpointC) (cabinC + 1.5).coerceAtMost(climate.setpointC.toDouble())
        else (cabinC - 1.0).coerceAtLeast(5.0)
        return cabinC
    }

    override suspend fun occupiedSeats(): Set<Seat>? = null

    override suspend fun darkenCabin(): Int = 0

    override suspend fun restoreCabinLamps(): Int = 0

    override suspend fun closeWindows(): Int = 0

    private var climateBeforeShut: ClimateTarget? = null

    override suspend fun shutOutsideAir(off: Boolean): Int {
        if (climateBeforeShut == null) climateBeforeShut = climate
        climate = climate.copy(hvacOn = climate.hvacOn && !off, freshAir = false)
        return 0
    }

    override suspend fun restoreOutsideAir(): Int {
        climateBeforeShut?.let { climate = it }
        climateBeforeShut = null
        return 0
    }

    override fun release() = Unit
}
