package io.github.offlinemobility.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyProtocolTest {
    @Test
    fun sosPatternUsesShortAndLongSignalDurations() {
        val pulses = EmergencyProtocol.toPulses("... --- ...")
        val signalDurations = pulses
            .filter { it.type == PulseType.SIGNAL }
            .map { it.durationUnits }

        assertEquals(listOf(1, 1, 1, 3, 3, 3, 1, 1, 1), signalDurations)
    }

    @Test
    fun spacesBecomeLetterGaps() {
        val pulses = EmergencyProtocol.toPulses(". .")

        assertTrue(pulses.any { it.type == PulseType.GAP && it.durationUnits == 3 })
    }

    @Test
    fun everyPresetHasAUniqueIdAndPattern() {
        val presets = EmergencyProtocol.presets

        assertEquals(presets.size, presets.map { it.id }.distinct().size)
        assertTrue(presets.all { it.morsePattern.isNotBlank() })
    }
}
