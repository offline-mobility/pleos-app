package io.github.offlinemobility.app.core

import io.github.offlinemobility.app.core.cabin.KeepWarm
import io.github.offlinemobility.app.core.cabin.KeepWarmController
import io.github.offlinemobility.app.core.cabin.Seat
import io.github.offlinemobility.app.core.cabin.WarmLevel
import io.github.offlinemobility.app.core.gateway.RelayLink
import io.github.offlinemobility.app.core.protocol.GeoCell
import io.github.offlinemobility.app.core.protocol.RescueFrame
import io.github.offlinemobility.app.core.protocol.RescueMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepWarmTest {
    private val minute = 60_000L

    @Test
    fun savingHeatLastsLongerThanGentleWhichLastsLongerThanNormal() {
        val e = KeepWarm.estimate(remainingWh = 54_000.0, people = 2, sealed = false)
        assertTrue(e.saverHours > e.gentleHours)
        assertTrue(e.gentleHours > e.normalHours)
        // 54 kWh, two people, −15 °C: about a day at 22 °C with a resistive heater.
        assertTrue("normal ${e.normalHours}", e.normalHours in 18.0..28.0)
        assertTrue("saver ${e.saverHours}", e.saverHours / e.normalHours in 1.15..1.6)
    }

    @Test
    fun morePeopleNeedMoreOutsideAirAndASealedCabinNeedsNone() {
        assertEquals(2, KeepWarm.freshMinutes(1))
        assertEquals(5, KeepWarm.freshMinutes(4))
        assertEquals(10, KeepWarm.freshMinutes(15))
        assertEquals(0, KeepWarm.estimate(54_000.0, 4, sealed = true).freshMinutes)
        assertTrue(KeepWarm.estimate(54_000.0, 4, sealed = true).saverHours > KeepWarm.estimate(54_000.0, 4, sealed = false).saverHours)
    }

    @Test
    fun energyToReachTheShelterIsSetAside() {
        val near = KeepWarm.estimate(10_000.0, 2, sealed = false, shelterKm = 1.0)
        val far = KeepWarm.estimate(10_000.0, 2, sealed = false, shelterKm = 20.0)
        assertTrue(far.saverHours < near.saverHours)
        // 40 km needs more than what is left: nothing to spend, never negative.
        assertEquals(0.0, KeepWarm.estimate(10_000.0, 2, sealed = false, shelterKm = 40.0).saverHours, 0.0)
    }

    @Test
    fun saverCyclesBetweenLowAndSetpoint() {
        val c = KeepWarmController(WarmLevel.SAVER, sealed = true, freshMinutes = 0)
        assertTrue(c.next(20.0, 0).hvacOn.not())
        assertFalse(c.next(13.0, minute).hvacOn)
        assertTrue(c.next(12.0, 2 * minute).hvacOn)
        assertTrue("keeps heating until the setpoint", c.next(14.0, 3 * minute).hvacOn)
        assertFalse(c.next(16.0, 4 * minute).hvacOn)
    }

    @Test
    fun withoutCabinTemperatureTheHeaterHoldsTheSetpoint() {
        val c = KeepWarmController(WarmLevel.SAVER, sealed = true, freshMinutes = 0)
        assertTrue(c.next(null, 0).hvacOn)
        assertEquals(16f, c.next(null, minute).setpointC)
    }

    @Test
    fun gentleNeverSwitchesTheHeaterOff() {
        val c = KeepWarmController(WarmLevel.GENTLE, sealed = true, freshMinutes = 0)
        assertTrue(c.next(25.0, 0).hvacOn)
        assertEquals(18f, c.next(25.0, 0).setpointC)
    }

    @Test
    fun outsideAirRunsAtTheStartOfEveryTenMinutesEvenWhileResting() {
        val c = KeepWarmController(WarmLevel.SAVER, sealed = false, freshMinutes = 3)
        val first = c.next(20.0, 0)
        assertTrue(first.freshAir && first.hvacOn)
        assertTrue(c.next(20.0, 2 * minute + 59_000).freshAir)
        val resting = c.next(20.0, 3 * minute)
        assertFalse(resting.freshAir || resting.hvacOn)
        assertTrue(c.next(20.0, 10 * minute).freshAir)
    }

    @Test
    fun sealedCabinNeverTakesOutsideAir() {
        val c = KeepWarmController(WarmLevel.GENTLE, sealed = true, freshMinutes = 5)
        assertTrue((0L..20L).none { c.next(15.0, it * minute).freshAir })
    }

    @Test
    fun seatsFollowOccupancyWhenKnown() {
        assertEquals(setOf(Seat.DRIVER, Seat.REAR_RIGHT), KeepWarm.seatsToHeat(setOf(Seat.DRIVER, Seat.REAR_RIGHT), 4))
        assertEquals(setOf(Seat.DRIVER, Seat.PASSENGER), KeepWarm.seatsToHeat(null, 2))
        assertEquals(setOf(Seat.DRIVER), KeepWarm.seatsToHeat(emptySet(), 0))
        assertEquals(4, KeepWarm.seatsToHeat(null, 9).size)
    }

    @Test
    fun relayLinkCarriesOnlyProtocolFields() {
        val frame = RescueFrame(RescueMessage.MEDICAL, people = 2, senderId = 0xa1, sequence = 3, location = GeoCell.of(37.49787, 127.02759))
        val url = RelayLink.url("https://rescue.example/", frame, sentAtMs = 1_794_000_000_123)!!
        assertTrue(url, Regex("""https://rescue\.example/r\?m=MEDICAL&p=2&i=a1-3&h=0&t=1794000000&la=37\.497\d\d&lo=127\.027\d\d""").matches(url))
        val noLocation = RelayLink.url("https://rescue.example", frame.copy(location = null), 1_794_000_000_000)!!
        assertEquals("https://rescue.example/r?m=MEDICAL&p=2&i=a1-3&h=0&t=1794000000", noLocation)
    }

    @Test
    fun relayLinkNeedsAServerAndARealMessage() {
        val frame = RescueFrame(RescueMessage.SOS, 1, 1, 1)
        assertNull(RelayLink.url("", frame, 0))
        assertNull(RelayLink.url("https://rescue.example", frame.copy(message = RescueMessage.ACK), 0))
    }
}
