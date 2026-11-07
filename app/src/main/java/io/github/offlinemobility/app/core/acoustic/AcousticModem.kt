package io.github.offlinemobility.app.core.acoustic

import io.github.offlinemobility.app.core.protocol.RescueFrame
import io.github.offlinemobility.app.core.protocol.RescueFrameCodec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Air-gap acoustic modem parameters.
 *
 * Frame on air: linear up-chirp preamble (matched-filter sync) → gap → 14 × 16-FSK symbols.
 * One symbol = one RS(14, 8) nibble. Guard silence between symbols absorbs cabin reverberation.
 */
data class ModemConfig(
    val sampleRate: Int = 48_000,
    val chirpStartHz: Double = 1_000.0,
    val chirpEndHz: Double = 5_000.0,
    val chirpMs: Int = 256,
    val postChirpGapMs: Int = 40,
    val baseToneHz: Double = 1_500.0,
    val toneSpacingHz: Double = 125.0,
    val symbolMs: Int = 80,
    val guardMs: Int = 20,
    val rampMs: Int = 5,
    val amplitude: Float = 0.8f,
    val analysisSkipStartMs: Int = 8,
    val analysisSkipEndMs: Int = 5,
    /** Normalized matched-filter score (0–1) required to accept a preamble. */
    val syncThreshold: Double = 0.2,
) {
    val tones: Int get() = 16
    val chirpSamples: Int get() = ms(chirpMs)
    val gapSamples: Int get() = ms(postChirpGapMs)
    val symbolSamples: Int get() = ms(symbolMs)
    val guardSamples: Int get() = ms(guardMs)
    val rampSamples: Int get() = ms(rampMs)
    val slotSamples: Int get() = symbolSamples + guardSamples
    val frameSamples: Int
        get() = chirpSamples + gapSamples + RescueFrameCodec.CODEWORD_SYMBOLS * slotSamples
    val frameDurationMs: Long get() = frameSamples * 1000L / sampleRate

    fun toneHz(symbol: Int): Double = baseToneHz + symbol * toneSpacingHz

    fun ms(value: Int): Int = sampleRate / 1000 * value
}

object AcousticModem {

    fun modulate(frame: RescueFrame, config: ModemConfig = ModemConfig()): FloatArray =
        modulateSymbols(RescueFrameCodec.encode(frame), config)

    fun modulateSymbols(symbols: IntArray, config: ModemConfig = ModemConfig()): FloatArray {
        val out = FloatArray(config.chirpSamples + config.gapSamples + symbols.size * config.slotSamples)
        chirp(config).copyInto(out, 0)
        var pos = config.chirpSamples + config.gapSamples
        for (symbol in symbols) {
            require(symbol in 0 until config.tones) { "symbol out of range" }
            writeTone(out, pos, config.symbolSamples, config.toneHz(symbol), config)
            pos += config.slotSamples
        }
        return out
    }

    /** Preamble template. Tukey-tapered so the matched filter is not dominated by edge clicks. */
    fun chirp(config: ModemConfig): FloatArray {
        val n = config.chirpSamples
        val t = n.toDouble() / config.sampleRate
        val k = (config.chirpEndHz - config.chirpStartHz) / t
        val taper = config.ms(10)
        return FloatArray(n) { i ->
            val time = i.toDouble() / config.sampleRate
            val phase = 2 * PI * (config.chirpStartHz * time + 0.5 * k * time * time)
            (config.amplitude * sin(phase) * edgeGain(i, n, taper)).toFloat()
        }
    }

    private fun writeTone(out: FloatArray, from: Int, length: Int, hz: Double, config: ModemConfig) {
        val w = 2 * PI * hz / config.sampleRate
        for (i in 0 until length) {
            out[from + i] = (config.amplitude * sin(w * i) * edgeGain(i, length, config.rampSamples)).toFloat()
        }
    }

    private fun edgeGain(i: Int, n: Int, ramp: Int): Double {
        if (ramp <= 0) return 1.0
        val edge = minOf(i, n - 1 - i)
        if (edge >= ramp) return 1.0
        return 0.5 - 0.5 * cos(PI * edge / ramp)
    }
}

/** Result of one successfully decoded frame. */
data class DecodedSignal(
    val frame: RescueFrame,
    /** Absolute sample index of the preamble start in the input stream. */
    val startSample: Long,
    val syncScore: Double,
    val snrDb: Double,
    val correctedSymbols: Int,
    val erasuresUsed: Int,
)

/**
 * Streaming receiver: feed microphone samples with [push]; decoded frames are delivered to [onFrame].
 *
 * Sync: FFT overlap-save matched filter against the chirp, normalized by local energy so the
 * threshold is independent of gain. Demodulation: Goertzel per tone on each symbol slot.
 * Error control: generalized minimum distance decoding — retry RS with the least confident
 * symbols marked as erasures (0, 2, 4, 6), accept only CRC-valid results.
 */
class AcousticDecoder(
    private val config: ModemConfig = ModemConfig(),
    private val onFrame: (DecodedSignal) -> Unit,
) {
    private val m = config.chirpSamples
    private val n = Fft.nextPowerOfTwo(2 * m)
    private val step = n - m + 1

    private val templateRe = DoubleArray(n)
    private val templateIm = DoubleArray(n)
    private val templateNorm: Double

    private val workRe = DoubleArray(n)
    private val workIm = DoubleArray(n)
    private val energyPrefix = DoubleArray(n + 1)

    private var buffer = FloatArray(n * 4)
    private var bufferStart = 0L // absolute index of buffer[0]
    private var bufferLength = 0
    private var searchFrom = 0L
    private var pendingSync: Pair<Long, Double>? = null

    /** Highest normalized preamble score seen in the latest analysed block (for UI meters). */
    var lastSyncScore: Double = 0.0
        private set

    init {
        val chirp = AcousticModem.chirp(config)
        var energy = 0.0
        for (i in chirp.indices) {
            templateRe[i] = chirp[i].toDouble()
            energy += chirp[i] * chirp[i]
        }
        templateNorm = sqrt(energy)
        Fft.transform(templateRe, templateIm)
    }

    private val bandPass = BandPassFilter(
        lowHz = config.chirpStartHz * 0.7,
        highHz = config.chirpEndHz * 1.25,
        sampleRate = config.sampleRate,
    )

    fun push(samples: FloatArray, offset: Int = 0, length: Int = samples.size) {
        ensureCapacity(bufferLength + length)
        bandPass.process(samples, offset, length, buffer, bufferLength)
        bufferLength += length
        process()
        trim()
    }

    fun reset() {
        bufferStart += bufferLength
        bufferLength = 0
        searchFrom = bufferStart
        pendingSync = null
    }

    private val bufferEnd: Long get() = bufferStart + bufferLength

    private fun process() {
        while (true) {
            val pending = pendingSync
            if (pending != null) {
                if (bufferEnd < pending.first + config.frameSamples) return
                pendingSync = null
                val decoded = demodulate(pending.first, pending.second)
                if (decoded != null) {
                    onFrame(decoded)
                    searchFrom = pending.first + config.frameSamples
                } else {
                    searchFrom = pending.first + m / 2
                }
                continue
            }

            if (bufferEnd < searchFrom + n) return
            val (lag, score) = bestLagInBlock(searchFrom)
            lastSyncScore = score
            if (score < config.syncThreshold) {
                searchFrom += step
                continue
            }
            val nearEnd = lag > searchFrom + step - m / 4
            if (nearEnd) {
                searchFrom = lag - m / 4
                continue
            }
            pendingSync = lag to score
        }
    }

    private fun bestLagInBlock(blockStart: Long): Pair<Long, Double> {
        val base = (blockStart - bufferStart).toInt()
        energyPrefix[0] = 0.0
        for (i in 0 until n) {
            val v = buffer[base + i].toDouble()
            workRe[i] = v
            workIm[i] = 0.0
            energyPrefix[i + 1] = energyPrefix[i] + v * v
        }
        Fft.transform(workRe, workIm)
        for (i in 0 until n) {
            // X · conj(H)
            val re = workRe[i] * templateRe[i] + workIm[i] * templateIm[i]
            val im = workIm[i] * templateRe[i] - workRe[i] * templateIm[i]
            workRe[i] = re
            workIm[i] = im
        }
        Fft.transform(workRe, workIm, inverse = true)

        var bestLag = 0
        var bestScore = 0.0
        val minEnergy = m * 1e-9
        for (t in 0 until step) {
            val e = energyPrefix[t + m] - energyPrefix[t]
            if (e < minEnergy) continue
            val score = workRe[t] / (sqrt(e) * templateNorm)
            if (score > bestScore) {
                bestScore = score
                bestLag = t
            }
        }
        return (blockStart + bestLag) to bestScore
    }

    private fun demodulate(syncAt: Long, syncScore: Double): DecodedSignal? {
        val symbolsCount = RescueFrameCodec.CODEWORD_SYMBOLS
        val symbols = IntArray(symbolsCount)
        val confidence = DoubleArray(symbolsCount)
        val skipStart = config.ms(config.analysisSkipStartMs)
        val window = config.symbolSamples - skipStart - config.ms(config.analysisSkipEndMs)
        var signalPower = 0.0
        var otherPower = 0.0

        val syncIndex = (syncAt - bufferStart).toInt()
        var preambleEnergy = 0.0
        for (i in syncIndex until syncIndex + m) preambleEnergy += buffer[i] * buffer[i]
        // A slot carrying a tone should hold energy comparable to the preamble; much less means
        // silence or a dropout, which is treated as an erasure rather than a guessed symbol.
        val slotEnergyFloor = 0.1 * preambleEnergy / m * window

        val first = syncIndex + m + config.gapSamples
        for (k in 0 until symbolsCount) {
            val from = first + k * config.slotSamples + skipStart
            var slotEnergy = 0.0
            for (i in from until from + window) slotEnergy += buffer[i] * buffer[i]
            var best = -1
            var bestPower = -1.0
            var second = 0.0
            var total = 0.0
            for (tone in 0 until config.tones) {
                val p = goertzelPower(buffer, from, window, config.toneHz(tone), config.sampleRate)
                total += p
                if (p > bestPower) {
                    second = maxOf(second, bestPower)
                    bestPower = p
                    best = tone
                } else if (p > second) {
                    second = p
                }
            }
            symbols[k] = best
            confidence[k] = when {
                slotEnergy < slotEnergyFloor -> 0.0
                second <= 0.0 -> Double.MAX_VALUE
                else -> bestPower / second
            }
            signalPower += bestPower
            otherPower += (total - bestPower) / (config.tones - 1)
        }

        val order = confidence.indices.sortedBy { confidence[it] }
        val dead = confidence.count { it == 0.0 }
        if (dead > RescueFrameCodec.PARITY_SYMBOLS) return null
        for (erasureCount in intArrayOf(0, 2, 4, 6)) {
            if (erasureCount < dead) continue
            val erasures = order.take(erasureCount).toIntArray()
            val decoded = RescueFrameCodec.decode(symbols, erasures) ?: continue
            val snr = if (otherPower <= 0.0) 99.0 else 10 * log10(signalPower / otherPower)
            return DecodedSignal(
                frame = decoded.frame,
                startSample = syncAt,
                syncScore = syncScore,
                snrDb = snr,
                correctedSymbols = decoded.correctedSymbols,
                erasuresUsed = erasureCount,
            )
        }
        return null
    }

    private fun ensureCapacity(required: Int) {
        if (required <= buffer.size) return
        var size = buffer.size
        while (size < required) size *= 2
        buffer = buffer.copyOf(size)
    }

    private fun trim() {
        val keepFrom = minOf(searchFrom, pendingSync?.first ?: Long.MAX_VALUE)
        val drop = (keepFrom - bufferStart).toInt()
        if (drop <= n) return
        buffer.copyInto(buffer, 0, drop, bufferLength)
        bufferLength -= drop
        bufferStart += drop
    }
}
