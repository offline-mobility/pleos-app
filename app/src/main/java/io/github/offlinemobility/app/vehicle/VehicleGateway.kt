package io.github.offlinemobility.app.vehicle

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

/** Vehicle functions the app needs, independent of the SDK so the UI and tests can use a fake. */
interface VehicleGateway {
    val isSimulated: Boolean
    val gear: StateFlow<GearState>

    suspend fun connect(): Result<Unit>
    suspend fun lampSupport(lamp: Lamp): LampSupport
    suspend fun readLamp(lamp: Lamp): Result<LampSwitch>
    suspend fun setLamp(lamp: Lamp, value: LampSwitch): Result<Unit>
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

    override fun release() = Unit
}
