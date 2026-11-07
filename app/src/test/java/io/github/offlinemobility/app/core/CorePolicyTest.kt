package io.github.offlinemobility.app.core

import io.github.offlinemobility.app.core.light.LightPatterns
import io.github.offlinemobility.app.core.protocol.RescueFrame
import io.github.offlinemobility.app.core.protocol.RescueMessage
import io.github.offlinemobility.app.core.relay.RelayDecision
import io.github.offlinemobility.app.core.relay.RelayPolicy
import io.github.offlinemobility.app.core.safety.GearState
import io.github.offlinemobility.app.core.safety.SafetyGate
import io.github.offlinemobility.app.core.safety.TransmitChannel
import io.github.offlinemobility.app.core.safety.VehicleSafetyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CorePolicyTest {

    @Test
    fun sosFollowsUscgDistressLightTiming() {
        val steps = LightPatterns.SOS.steps
        assertEquals(listOf(333L, 333L, 333L, 1_000L, 1_000L, 1_000L, 333L, 333L, 333L), steps.filter { it.on }.map { it.durationMs })
        // dots/dashes 1 + 3 + 1 s, flash gaps 6 × 1/3 s, letter gaps 2 × 2 s → 11 s, then 3 s pause.
        assertEquals(13_996L, LightPatterns.SOS.totalMs)
    }

    @Test
    fun guideIsNotAHazardLookalike() {
        val offs = LightPatterns.GUIDE.steps.filter { !it.on }.map { it.durationMs }.distinct()
        assertTrue("guide must not be an even blink", offs.size > 1)
    }

    @Test
    fun distressBeaconIsSixFlashesPerMinute() {
        val beacon = LightPatterns.DISTRESS_BEACON
        assertEquals(6, beacon.flashes)
        val firstMinute = beacon.steps.take(12).sumOf { it.durationMs }
        assertEquals(60_000L, firstMinute)
    }

    @Test
    fun replyIsThreeFlashesPerMinute() {
        assertEquals(3, LightPatterns.REPLY.flashes)
        assertEquals(60_000L, LightPatterns.REPLY.steps.take(6).sumOf { it.durationMs })
    }

    @Test
    fun everyStepRespectsLampSwitchingLimit() {
        for (pattern in LightPatterns.all) {
            assertTrue(pattern.id, pattern.steps.all { it.durationMs >= LightPatterns.MIN_STEP_MS })
        }
    }

    @Test
    fun criticalMessagesUseSos() {
        assertEquals(LightPatterns.SOS, LightPatterns.forMessage(RescueMessage.MEDICAL))
        assertEquals(LightPatterns.DISTRESS_BEACON, LightPatterns.forMessage(RescueMessage.WATER))
        assertEquals(LightPatterns.REPLY, LightPatterns.forMessage(RescueMessage.ACK))
    }

    @Test
    fun relayFloodsOnceAndDetectsEcho() {
        val policy = RelayPolicy(ownSenderId = 1, random = Random(1))
        val incoming = RescueFrame(RescueMessage.SOS, 2, senderId = 9, sequence = 4)

        val first = policy.onReceived(incoming, nowMs = 0)
        assertTrue(first is RelayDecision.Fresh)
        first as RelayDecision.Fresh
        assertEquals(1, first.relay!!.hop)
        assertTrue(first.relayDelayMs in 1_500L..6_000L)

        assertTrue(policy.onReceived(incoming.copy(hop = 1), nowMs = 5_000) is RelayDecision.Duplicate)

        val own = RescueFrame(RescueMessage.WATER, 1, senderId = 1, sequence = 0)
        policy.markSent(own, nowMs = 6_000)
        // Our own speaker heard by our own microphone is not a delivery confirmation.
        assertTrue(policy.onReceived(own, nowMs = 6_100) is RelayDecision.Duplicate)
        assertTrue(policy.onReceived(own.copy(hop = 1), nowMs = 9_000) is RelayDecision.EchoOfOwn)
    }

    @Test
    fun relayStopsAtHopLimitAndNeverRelaysAck() {
        val policy = RelayPolicy(ownSenderId = 1)
        val atLimit = policy.onReceived(RescueFrame(RescueMessage.SOS, 0, 7, 1, hop = 3), 0)
        assertNull((atLimit as RelayDecision.Fresh).relay)
        val ack = policy.onReceived(RescueFrame(RescueMessage.ACK, 0, 8, 1), 0)
        assertNull((ack as RelayDecision.Fresh).relay)
    }

    @Test
    fun duplicateWindowExpires() {
        val policy = RelayPolicy(ownSenderId = 1, dedupeWindowMs = 1_000)
        val f = RescueFrame(RescueMessage.FIRE, 0, 5, 2)
        policy.onReceived(f, 0)
        assertTrue(policy.onReceived(f, 2_000) is RelayDecision.Fresh)
    }

    @Test
    fun transmissionOnlyInPark() {
        assertTrue(SafetyGate.evaluate(VehicleSafetyState(GearState.PARK), TransmitChannel.LIGHT).allowed)
        assertFalse(SafetyGate.evaluate(VehicleSafetyState(GearState.DRIVE), TransmitChannel.LIGHT).allowed)
        assertFalse(SafetyGate.evaluate(VehicleSafetyState(GearState.UNKNOWN), TransmitChannel.SOUND).allowed)
        val regulated = VehicleSafetyState(GearState.PARK, drivingRestricted = true)
        assertFalse(SafetyGate.evaluate(regulated, TransmitChannel.SOUND).allowed)
    }
}
