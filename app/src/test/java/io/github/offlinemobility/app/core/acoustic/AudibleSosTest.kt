package io.github.offlinemobility.app.core.acoustic

import io.github.offlinemobility.app.core.protocol.RescueFrame
import io.github.offlinemobility.app.core.protocol.RescueMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudibleSosTest {
    private val config = ModemConfig()

    @Test
    fun sosFollowsItuTimingAtTenWordsPerMinute() {
        assertEquals(34 * 120, AudibleSos.durationMs)
        val samples = AudibleSos.samples(config.sampleRate)
        assertEquals(config.sampleRate * AudibleSos.durationMs / 1000, samples.size)
        // Count tone bursts: 9 (3 dots, 3 dashes, 3 dots).
        var bursts = 0
        var inTone = false
        val window = config.sampleRate / 1000
        for (i in samples.indices step window) {
            val loud = (i until minOf(i + window, samples.size)).any { kotlin.math.abs(samples[it]) > 0.05f }
            if (loud && !inTone) bursts++
            inTone = loud
        }
        assertEquals(9, bursts)
    }

    @Test
    fun beepsAreNeverDecodedAsAFrame() {
        val decoded = mutableListOf<DecodedSignal>()
        val decoder = AcousticDecoder(config) { decoded += it }
        repeat(5) { decoder.push(AudibleSos.samples(config.sampleRate)) }
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun frameFollowedBySosStillDecodes() {
        val frame = RescueFrame(RescueMessage.SOS, 2, 0x22, 3)
        val audio = FloatArray(config.ms(200)) + AcousticModem.modulate(frame, config) +
            FloatArray(config.ms(400)) + AudibleSos.samples(config.sampleRate) + FloatArray(config.ms(300))
        val decoded = mutableListOf<DecodedSignal>()
        AcousticDecoder(config) { decoded += it }.push(audio)
        assertEquals(listOf(frame), decoded.map { it.frame })
    }
}
