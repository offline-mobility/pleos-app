package io.github.offlinemobility.app.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import io.github.offlinemobility.app.core.cabin.ClimateTarget
import io.github.offlinemobility.app.core.cabin.Seat
import io.github.offlinemobility.app.core.energy.EnergyReading
import io.github.offlinemobility.app.core.light.Lamp
import io.github.offlinemobility.app.core.light.LampSwitch
import io.github.offlinemobility.app.core.safety.GearState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pleos Vehicle SDK 2.3.2 binding.
 *
 * Required manifest permissions: pleos.car.permission.CAR_LIGHTS (read / capability),
 * CONTROL_CAR_LIGHTS (set), CAR_DRIVE (gear), CAR_ENERGY (battery / fuel); climate control is in
 * [PleosClimate]. Every SDK call goes through [SdkCaller].
 */
class PleosVehicleGateway(context: Context) : VehicleGateway {
    override val isSimulated: Boolean = false

    private val vehicle = Vehicle(context.applicationContext)
    private var initialized = false
    private val sdk = SdkCaller { initialized }
    private val climate = PleosClimate(vehicle, sdk)

    private val _gear = MutableStateFlow(GearState.UNKNOWN)
    override val gear: StateFlow<GearState> = _gear.asStateFlow()

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            _gear.value = gear?.toGearState() ?: GearState.UNKNOWN
        }

        override fun onFailed(e: Exception) {
            _gear.value = GearState.UNKNOWN
        }
    }

    override suspend fun connect(): Result<Unit> = runCatching {
        if (!initialized) {
            vehicle.initialize()
            initialized = true
        }
        val gearNow = call<VehicleGear> { done, fail ->
            vehicle.getGear().getCurrentGearSelectionState(done, fail)
        }.getOrThrow()
        _gear.value = gearNow.toGearState()
        vehicle.getGear().registerGearSelection(gearListener)
    }

    override suspend fun lampSupport(lamp: Lamp): LampSupport {
        val result = call<CapabilityConfig> { done, fail ->
            val onConfig: (CapabilityConfig?, List<LightSwitch>?) -> Unit = { config, _ -> done(config) }
            val light = vehicle.getLight()
            when (lamp) {
                Lamp.HIGH_BEAM -> light.checkHighBeamLightSwitchCapabilities(onConfig, fail)
                Lamp.LOW_BEAM -> light.checkLowBeamLightSwitchCapabilities(onConfig, fail)
                Lamp.REAR_FOG -> light.checkRearFogLightSwitchCapabilities(onConfig, fail)
                Lamp.FRONT_FOG -> light.checkFrontFogLightSwitchCapabilities(onConfig, fail)
                Lamp.TAIL_LAMP -> light.checkTailLampLightSwitchCapabilities(onConfig, fail)
            }
        }
        val config = result.getOrNull() ?: return LampSupport.UNKNOWN
        return when (config.accessType) {
            AccessType.WRITE, AccessType.READ_WRITE -> LampSupport.WRITABLE
            AccessType.READ -> LampSupport.READ_ONLY
            AccessType.NONE -> LampSupport.UNSUPPORTED
        }
    }

    override suspend fun readLamp(lamp: Lamp): Result<LampSwitch> = call<LightSwitch> { done, fail ->
        val light = vehicle.getLight()
        when (lamp) {
            Lamp.HIGH_BEAM -> light.getHighBeamLightSwitch(done, fail)
            Lamp.LOW_BEAM -> light.getLowBeamLightSwitch(done, fail)
            Lamp.REAR_FOG -> light.getRearFogLightSwitch(done, fail)
            Lamp.FRONT_FOG -> light.getFrontFogLightSwitch(done, fail)
            Lamp.TAIL_LAMP -> light.getTailLampLightSwitch(done, fail)
        }
    }.map { it.toLampSwitch() }

    override suspend fun setLamp(lamp: Lamp, value: LampSwitch): Result<Unit> = call<LightSwitch> { done, fail ->
        val light = vehicle.getLight()
        val target = value.toLightSwitch()
        when (lamp) {
            Lamp.HIGH_BEAM -> light.setHighBeamLightSwitch(target, done, fail)
            Lamp.LOW_BEAM -> light.setLowBeamLightSwitch(target, done, fail)
            Lamp.REAR_FOG -> light.setRearFogLightSwitch(target, done, fail)
            Lamp.FRONT_FOG -> light.setFrontFogLightSwitch(target, done, fail)
            Lamp.TAIL_LAMP -> light.setTailLampLightSwitch(target, done, fail)
        }
    }.map { }

    override suspend fun isLampLit(lamp: Lamp): Result<Boolean> = call<LightState> { done, fail ->
        val light = vehicle.getLight()
        when (lamp) {
            Lamp.HIGH_BEAM -> light.getCurrentHighBeamLightState(done, fail)
            Lamp.LOW_BEAM -> light.getCurrentLowBeamLightState(done, fail)
            Lamp.REAR_FOG -> light.getCurrentRearFogLightState(done, fail)
            Lamp.FRONT_FOG -> light.getCurrentFrontFogLightState(done, fail)
            Lamp.TAIL_LAMP -> light.getCurrentTailLampLightState(done, fail)
        }
    }.map { it == LightState.ON || it == LightState.DAYTIME_RUNNING }

    override suspend fun energy(): EnergyReading {
        val level = call<Float> { done, fail -> vehicle.getEvBattery().getEvBatteryLevel(done, fail) }.getOrNull()
        val soc = call<Int> { done, fail -> vehicle.getEvBattery().getEvBatteryStateOfCharge(done, fail) }.getOrNull()
        val fuel = call<Int> { done, fail -> vehicle.getFuel().getFuelPercent(done, fail) }.getOrNull()
        return EnergyReading(evRemainingWh = level?.toDouble(), evPercent = soc, fuelPercent = fuel)
    }

    override suspend fun climateSupport(): ClimateSupport = climate.support()

    override suspend fun saveClimate(): Boolean = climate.save()

    override suspend fun applyClimate(target: ClimateTarget, seats: Set<Seat>): Int = climate.apply(target, seats)

    override suspend fun restoreClimate(): Int = climate.restore()

    override suspend fun cabinTemperature(): Double? = climate.cabinTemperature()

    override suspend fun occupiedSeats(): Set<Seat>? = climate.occupiedSeats()

    override fun release() {
        if (!initialized) return
        runCatching { vehicle.getGear().unregisterGearSelection(gearListener) }
        runCatching { vehicle.release() }
        initialized = false
    }

    private suspend fun <T> call(block: (done: (T?) -> Unit, fail: (Exception) -> Unit) -> Unit): Result<T> =
        sdk.call(block = block)
}

private fun VehicleGear.toGearState(): GearState = when (this) {
    VehicleGear.GEAR_PARK -> GearState.PARK
    VehicleGear.GEAR_NEUTRAL -> GearState.NEUTRAL
    VehicleGear.GEAR_REVERSE -> GearState.REVERSE
    VehicleGear.GEAR_UNKNOWN -> GearState.UNKNOWN
    else -> GearState.DRIVE
}

private fun LightSwitch.toLampSwitch(): LampSwitch = when (this) {
    LightSwitch.OFF -> LampSwitch.OFF
    LightSwitch.ON -> LampSwitch.ON
    LightSwitch.DAYTIME_RUNNING -> LampSwitch.DAYTIME_RUNNING
    LightSwitch.AUTOMATIC -> LampSwitch.AUTOMATIC
}

private fun LampSwitch.toLightSwitch(): LightSwitch = when (this) {
    LampSwitch.OFF -> LightSwitch.OFF
    LampSwitch.ON -> LightSwitch.ON
    LampSwitch.DAYTIME_RUNNING -> LightSwitch.DAYTIME_RUNNING
    LampSwitch.AUTOMATIC -> LightSwitch.AUTOMATIC
}
