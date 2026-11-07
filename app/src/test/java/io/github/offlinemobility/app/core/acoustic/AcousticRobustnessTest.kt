package io.github.offlinemobility.app.core.acoustic

import io.github.offlinemobility.app.core.protocol.RescueFrame
import io.github.offlinemobility.app.core.protocol.RescueMessage
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Monte Carlo sweep: random frames through attenuation, two reflections, engine rumble and white
 * noise. SNR is measured over the full 0–24 kHz band against the frame's own RMS.
 * Results are printed to the test report (system-out) for the design document.
 */
class AcousticRobustnessTest {
    private val config = ModemConfig()
    private val random = Random(2026)

    private fun gaussian(): Double {
        val u1 = random.nextDouble().coerceAtLeast(1e-12)
        return sqrt(-2 * ln(u1)) * cos(2 * PI * random.nextDouble())
    }

    private fun trial(snrDb: Double): Boolean {
        val frame = RescueFrame(
            message = RescueMessage.entries.random(random),
            people = random.nextInt(16),
            senderId = random.nextInt(256),
            sequence = random.nextInt(16),
        )
        val tx = AcousticModem.modulate(frame, config)
        val lead = config.ms(100 + random.nextInt(400))
        val total = lead + tx.size + config.ms(400)
        val gain = 0.05
        val signalRms = gain * sqrt(tx.sumOf { it.toDouble() * it } / tx.size)
        val noiseRms = signalRms / 10.0.pow(snrDb / 20)
        val d1 = config.ms(8 + random.nextInt(10))
        val d2 = config.ms(25 + random.nextInt(20))
        val rumblePhase = random.nextDouble() * 2 * PI

        val rx = FloatArray(total) { i ->
            val j = i - lead
            var v = 0.0
            if (j in tx.indices) v += gain * tx[j]
            if (j - d1 in tx.indices) v += gain * 0.5 * tx[j - d1]
            if (j - d2 in tx.indices) v += gain * 0.25 * tx[j - d2]
            v += noiseRms * gaussian()
            v += 0.1 * sin(2 * PI * 80 * i / config.sampleRate + rumblePhase)
            v.toFloat()
        }

        var result: RescueFrame? = null
        val decoder = AcousticDecoder(config) { result = it.frame }
        var pos = 0
        while (pos < rx.size) {
            val len = minOf(2_048, rx.size - pos)
            decoder.push(rx, pos, len)
            pos += len
        }
        return result == frame
    }

    @Test
    fun successRateAcrossSnr() {
        val trials = 40
        val rates = linkedMapOf<Double, Double>()
        for (snr in listOf(0.0, -5.0, -10.0, -15.0, -20.0)) {
            val ok = (1..trials).count { trial(snr) }
            rates[snr] = ok.toDouble() / trials
        }
        println("acoustic frame success rate (full-band SNR → rate): $rates")
        assertTrue("expected ≥ 95% at 0 dB, got $rates", rates.getValue(0.0) >= 0.95)
        assertTrue("expected ≥ 90% at -10 dB, got $rates", rates.getValue(-10.0) >= 0.9)
    }
}
