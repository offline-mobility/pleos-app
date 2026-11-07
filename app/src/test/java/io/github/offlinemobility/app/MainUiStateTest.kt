package io.github.offlinemobility.app

import io.github.offlinemobility.app.core.light.Lamp
import io.github.offlinemobility.app.core.light.LampGroup
import io.github.offlinemobility.app.core.safety.GearState
import io.github.offlinemobility.app.vehicle.LampSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainUiStateTest {
    @Test
    fun transmitRequiresParkAndAChannel() {
        assertTrue(UiState(gear = GearState.PARK).canTransmit)
        assertFalse(UiState(gear = GearState.DRIVE).canTransmit)
        assertFalse(UiState(gear = GearState.PARK, useSound = false, useLight = false).canTransmit)
    }

    @Test
    fun blackoutDisablesLightChannel() {
        val state = UiState(gear = GearState.PARK, useSound = false, useLight = true, blackout = true)
        assertFalse(state.lightEnabled)
        assertFalse(state.canTransmit)
    }

    @Test
    fun cannotStartWhileTransmitting() {
        assertFalse(UiState(gear = GearState.PARK, tx = TxState(soundActive = true)).canTransmit)
    }

    @Test
    fun withoutRearFogLowBeamFlashesWithHighBeam() {
        val support = mapOf(
            Lamp.HIGH_BEAM to LampSupport.WRITABLE,
            Lamp.LOW_BEAM to LampSupport.WRITABLE,
            Lamp.REAR_FOG to LampSupport.UNKNOWN,
            Lamp.FRONT_FOG to LampSupport.UNKNOWN,
            Lamp.TAIL_LAMP to LampSupport.WRITABLE,
        )
        val plan = UiState(lampGroup = LampGroup.FRONT_AND_REAR, lampSupport = support).lightPlan
        assertEquals(listOf(Lamp.LOW_BEAM, Lamp.HIGH_BEAM), plan.flash)
        assertTrue(plan.steady.isEmpty())
    }

    @Test
    fun withRearFogHighBeamFlashesOverSteadyLowBeam() {
        val support = Lamp.entries.associateWith { LampSupport.WRITABLE }
        val plan = UiState(lampGroup = LampGroup.FRONT_AND_REAR, lampSupport = support).lightPlan
        assertEquals(listOf(Lamp.HIGH_BEAM, Lamp.REAR_FOG), plan.flash)
        assertEquals(listOf(Lamp.LOW_BEAM), plan.steady)
    }

    @Test
    fun noControllableLampDisablesLightOnlyTransmission() {
        val support = Lamp.entries.associateWith { LampSupport.UNSUPPORTED }
        val state = UiState(gear = GearState.PARK, useSound = false, lampSupport = support)
        assertFalse(state.lightUsable)
        assertFalse(state.canTransmit)
    }
}
