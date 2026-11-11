package io.github.offlinemobility.app.core

import io.github.offlinemobility.app.core.energy.Endurance
import io.github.offlinemobility.app.core.energy.EnergyReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnduranceTest {
    @Test
    fun evEstimateKeepsReserveAndCountsLights() {
        val reading = EnergyReading(evRemainingWh = 60_000.0, evPercent = 80)
        val dark = Endurance.estimate(reading, lightsOn = false)!!
        val lit = Endurance.estimate(reading, lightsOn = true)!!
        assertEquals(60_000 * 0.9 / 250.0, dark.hours, 0.01)
        assertTrue(lit.hours < dark.hours)
        assertTrue(dark.isEv && !dark.low)
    }

    @Test
    fun lowEnergyIsFlagged() {
        val estimate = Endurance.estimate(EnergyReading(evRemainingWh = 300.0, evPercent = 1), lightsOn = true)!!
        assertTrue(estimate.low)
    }

    @Test
    fun combustionUsesFuelForIdling() {
        val estimate = Endurance.estimate(EnergyReading(fuelPercent = 40), lightsOn = true)!!
        assertEquals(50.0 * 0.4 / 0.8, estimate.hours, 0.01)
        assertFalse(estimate.isEv)
    }

    @Test
    fun unknownVehicleGivesNoEstimate() {
        assertNull(Endurance.estimate(EnergyReading(), lightsOn = false))
        assertEquals("약 3일", Endurance.hoursWord(80.0))
        assertEquals("1시간 미만", Endurance.hoursWord(0.5))
    }
}
