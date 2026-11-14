package io.github.offlinemobility.app.vehicle

import io.github.offlinemobility.app.core.light.Lamp
import io.github.offlinemobility.app.core.light.LampSwitch
import io.github.offlinemobility.app.core.light.restoreOrder

/**
 * Air-raid blackout on the car itself (민방공 공습경보: 전조등·미등·실내등 소등): switches off every
 * lamp the vehicle lets the app switch and puts each one back afterwards.
 *
 * [remembered] are the exterior switches as they were before the lamps went dark. They are handed
 * in again after a restart, so a second [darken] does not take "off" for the original state.
 */
class CarBlackout(remembered: Map<Lamp, LampSwitch> = emptyMap(), private val log: (String) -> Unit = {}) {
    var remembered: Map<Lamp, LampSwitch> = remembered
        private set

    /**
     * Returns the number of lamps the vehicle refused to switch off, or null when it has no lamp
     * the app may switch.
     */
    suspend fun darken(gateway: VehicleGateway): Int? {
        val writable = OFF_ORDER.filter { gateway.lampSupport(it) == LampSupport.WRITABLE }
        val now = writable.mapNotNull { lamp -> gateway.readLamp(lamp).getOrNull()?.let { lamp to it } }.toMap()
        // Only lamps that are on need putting back; writing "off" to the rest later could undo the driver.
        if (remembered.isEmpty()) remembered = now.filterValues { it != LampSwitch.OFF }
        log("blackout lamps now=$now remembered=$remembered")
        var failures = 0
        for ((lamp, value) in now) {
            if (value == LampSwitch.OFF) continue
            gateway.setLamp(lamp, LampSwitch.OFF).onFailure {
                failures++
                log("blackout ${lamp.name} refused: ${it.message}")
            }
        }
        val cabin = gateway.darkenCabin()
        return if (writable.isEmpty() && cabin == null) null else failures + (cabin ?: 0)
    }

    /**
     * Returns the number of lamps that could not be put back. The vehicle refuses lamp control once
     * it leaves P, so whatever failed stays in [remembered] and the next call tries again.
     */
    suspend fun restore(gateway: VehicleGateway): Int {
        val failed = restoreOrder(remembered).filter { (lamp, value) ->
            gateway.setLamp(lamp, value).onFailure { log("restore ${lamp.name} refused: ${it.message}") }.isFailure
        }
        remembered = failed.toMap()
        return failed.size + gateway.restoreCabinLamps()
    }

    companion object {
        /** High beam and fog lamps first; the tail lamp last, because it follows the headlamp switch. */
        private val OFF_ORDER = listOf(Lamp.HIGH_BEAM, Lamp.FRONT_FOG, Lamp.REAR_FOG, Lamp.LOW_BEAM, Lamp.TAIL_LAMP)

        fun encode(lamps: Map<Lamp, LampSwitch>): String = lamps.entries.joinToString(",") { "${it.key.name}=${it.value.name}" }

        fun decode(text: String?): Map<Lamp, LampSwitch> = text.orEmpty().split(',').mapNotNull { entry ->
            val (name, value) = entry.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            val lamp = Lamp.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
            val switch = LampSwitch.entries.firstOrNull { it.name == value } ?: return@mapNotNull null
            lamp to switch
        }.toMap()
    }
}
