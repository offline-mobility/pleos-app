package io.github.offlinemobility.app.core.acoustic

import io.github.offlinemobility.app.core.protocol.RescueFrame
import io.github.offlinemobility.app.core.protocol.RescueMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class AcousticModemTest {
    private val config = ModemConfig()
    private val frame = RescueFrame(RescueMessage.TRAPPED, people = 4, senderId = 0x3C, sequence = 7)

    private fun decodeAll(signal: FloatArray, chunk: Int = 4_800): List<DecodedSignal> {
        val out = mutableListOf<DecodedSignal>()
        val decoder = AcousticDecoder(config) { out += it }
        var pos = 0
        while (pos < signal.size) {
            val len = minOf(chunk, signal.size - pos)
            decoder.push(signal, pos, len)
            pos += len
        }
        return out
    }

    private fun silence(ms: Int) = FloatArray(config.ms(ms))

    private fun gaussian(random: Random): Double {
        // Box–Muller
        val u1 = random.nextDouble().coerceAtLeast(1e-12)
        val u2 = random.nextDouble()
        return sqrt(-2 * kotlin.math.ln(u1)) * kotlin.math.cos(2 * PI * u2)
    }

    private fun rms(x: FloatArray): Double = sqrt(x.sumOf { it.toDouble() * it } / x.size)

    @Test
    fun frameDurationIsUnderFourSeconds() {
        assertTrue(config.frameDurationMs < 3_500)
    }

    @Test
    fun locationSurvivesTheAirPath() {
        val located = frame.copy(location = io.github.offlinemobility.app.core.protocol.GeoCell.of(37.5006, 127.0367))
        val signal = silence(300) + AcousticModem.modulate(located, config) + silence(400)
        assertEquals(listOf(located), decodeAll(signal).map { it.frame })
    }

    @Test
    fun cleanSignalDecodes() {
        val signal = silence(370) + AcousticModem.modulate(frame, config) + silence(500)
        val decoded = decodeAll(signal)
        assertEquals(1, decoded.size)
        assertEquals(frame, decoded.single().frame)
        assertEquals(0, decoded.single().correctedSymbols)
    }

    @Test
    fun decodesWithAttenuationNoiseAndReverb() {
        val random = Random(7)
        val tx = silence(250) + AcousticModem.modulate(frame, config) + silence(600)
        // Attenuate (distance) and add two reflections (cabin / car body).
        val rx = FloatArray(tx.size)
        val d1 = config.ms(12)
        val d2 = config.ms(35)
        for (i in tx.indices) {
            var v = 0.05 * tx[i]
            if (i >= d1) v += 0.03 * tx[i - d1]
            if (i >= d2) v += 0.015 * tx[i - d2]
            rx[i] = v.toFloat()
        }
        val signalRms = rms(AcousticModem.modulate(frame, config)) * 0.05
        // White noise at roughly -3 dB SNR over the full band.
        val noiseRms = signalRms * 1.4
        for (i in rx.indices) rx[i] = (rx[i] + noiseRms * gaussian(random)).toFloat()
        // Low-frequency rumble typical of engine / wind.
        for (i in rx.indices) rx[i] = (rx[i] + 0.2 * sin(2 * PI * 90 * i / config.sampleRate)).toFloat()

        val decoded = decodeAll(rx, chunk = 1_024)
        assertEquals(1, decoded.size)
        assertEquals(frame, decoded.single().frame)
    }

    @Test
    fun decodesThreeRepeatedFramesInOneStream() {
        val one = AcousticModem.modulate(frame, config)
        val signal = silence(100) + one + silence(300) + one + silence(300) + one + silence(400)
        val decoded = decodeAll(signal, chunk = 777)
        assertEquals(3, decoded.size)
        assertTrue(decoded.all { it.frame == frame })
    }

    @Test
    fun noFalseFramesFromNoiseOrMusicLikeTones() {
        val random = Random(99)
        val seconds = 20
        val n = config.sampleRate * seconds
        val noise = FloatArray(n) { i ->
            val tone = 0.2 * sin(2 * PI * (440 + 220 * ((i / 12_000) % 5)) * i / config.sampleRate)
            (0.1 * gaussian(random) + tone).toFloat()
        }
        assertTrue(decodeAll(noise).isEmpty())
    }

    @Test
    fun decoderResynchronizesAfterTruncatedFrame() {
        val full = AcousticModem.modulate(frame, config)
        val truncated = full.copyOf(full.size / 2)
        val other = RescueFrame(RescueMessage.WATER, 2, 0x11, 3)
        val signal = silence(200) + truncated + silence(800) + AcousticModem.modulate(other, config) + silence(400)
        val decoded = decodeAll(signal)
        assertEquals(listOf(other), decoded.map { it.frame })
    }

    @Test
    fun receiverAt44100DecodesTransmitterAt48000() {
        val tx = silence(200) + AcousticModem.modulate(frame, config) + silence(400)
        // Linear-interpolation resample 48 kHz → 44.1 kHz.
        val ratio = 48_000.0 / 44_100.0
        val rx = FloatArray((tx.size / ratio).toInt()) { i ->
            val pos = i * ratio
            val j = pos.toInt().coerceAtMost(tx.size - 2)
            val f = pos - j
            (tx[j] * (1 - f) + tx[j + 1] * f).toFloat()
        }
        val out = mutableListOf<DecodedSignal>()
        val decoder = AcousticDecoder(config.copy(sampleRate = 44_100)) { out += it }
        decoder.push(rx)
        assertEquals(listOf(frame), out.map { it.frame })
    }

    @Test
    fun survivesHardClippingFromOverdrivenSpeaker() {
        val tx = silence(200) + AcousticModem.modulate(frame, config) + silence(400)
        val clipped = FloatArray(tx.size) { (tx[it] * 4f).coerceIn(-0.9f, 0.9f) }
        assertEquals(listOf(frame), decodeAll(clipped).map { it.frame })
    }
}
