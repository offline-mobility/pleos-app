package io.github.offlinemobility.app

import io.github.offlinemobility.app.core.safety.GearState
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
}
