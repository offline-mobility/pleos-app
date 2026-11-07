package io.github.offlinemobility.app.core.acoustic

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** In-place iterative radix-2 complex FFT. */
internal object Fft {
    fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean = false) {
        val n = re.size
        require(n == im.size && n > 0 && n and (n - 1) == 0) { "size must be a power of two" }

        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }

        var len = 2
        while (len <= n) {
            val ang = 2 * PI / len * if (inverse) 1 else -1
            val wRe = cos(ang)
            val wIm = sin(ang)
            var i = 0
            while (i < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until len / 2) {
                    val aRe = re[i + k]
                    val aIm = im[i + k]
                    val bRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val bIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k] = aRe + bRe
                    im[i + k] = aIm + bIm
                    re[i + k + len / 2] = aRe - bRe
                    im[i + k + len / 2] = aIm - bIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }

        if (inverse) {
            for (i in 0 until n) {
                re[i] /= n
                im[i] /= n
            }
        }
    }

    fun nextPowerOfTwo(x: Int): Int {
        var n = 1
        while (n < x) n = n shl 1
        return n
    }
}

/** Second-order IIR section, RBJ Audio EQ Cookbook coefficients. Stateful, process in stream order. */
internal class Biquad private constructor(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x
        y2 = y1; y1 = y
        return y
    }

    companion object {
        private const val Q_BUTTERWORTH = 0.7071067811865476

        fun highPass(cutoffHz: Double, sampleRate: Int): Biquad {
            val w0 = 2 * PI * cutoffHz / sampleRate
            val alpha = sin(w0) / (2 * Q_BUTTERWORTH)
            val c = cos(w0)
            val a0 = 1 + alpha
            return Biquad((1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
        }

        fun lowPass(cutoffHz: Double, sampleRate: Int): Biquad {
            val w0 = 2 * PI * cutoffHz / sampleRate
            val alpha = sin(w0) / (2 * Q_BUTTERWORTH)
            val c = cos(w0)
            val a0 = 1 + alpha
            return Biquad((1 - c) / 2 / a0, (1 - c) / a0, (1 - c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)
        }
    }
}

/** 4th-order band-pass (two Butterworth HP + two LP sections) keeping only the modem band. */
internal class BandPassFilter(lowHz: Double, highHz: Double, sampleRate: Int) {
    private val sections = listOf(
        Biquad.highPass(lowHz, sampleRate),
        Biquad.highPass(lowHz, sampleRate),
        Biquad.lowPass(highHz, sampleRate),
        Biquad.lowPass(highHz, sampleRate),
    )

    fun process(input: FloatArray, offset: Int, length: Int, output: FloatArray, outOffset: Int) {
        for (i in 0 until length) {
            var v = input[offset + i].toDouble()
            for (s in sections) v = s.process(v)
            output[outOffset + i] = v.toFloat()
        }
    }
}

/** Power of a single frequency over samples[from, from + length) (generalized Goertzel). */
internal fun goertzelPower(
    samples: FloatArray,
    from: Int,
    length: Int,
    frequency: Double,
    sampleRate: Int,
): Double {
    val w = 2 * PI * frequency / sampleRate
    val coeff = 2 * cos(w)
    var s1 = 0.0
    var s2 = 0.0
    for (i in from until from + length) {
        val s0 = samples[i] + coeff * s1 - s2
        s2 = s1
        s1 = s0
    }
    return s1 * s1 + s2 * s2 - coeff * s1 * s2
}
