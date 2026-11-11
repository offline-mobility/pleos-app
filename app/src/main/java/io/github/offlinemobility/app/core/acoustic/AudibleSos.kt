package io.github.offlinemobility.app.core.acoustic

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Morse SOS as plain beeps, so people without the app (pedestrians, rescuers) recognise a call for
 * help. ITU-R M.1677-1 timing at a 120 ms unit (≈10 words per minute): dot 1, dash 3, gap inside a
 * letter 1, between letters 3, trailing word gap 7 → 34 units ≈ 4.1 s.
 *
 * 1 kHz sits below the modem's 1.5–3.4 kHz tone band and carries no chirp, so the receiver never
 * mistakes it for a data frame.
 */
object AudibleSos {
    const val UNIT_MS = 120
    const val TONE_HZ = 1_000.0

    /** Units per element: positive = tone, negative = silence. */
    private val PATTERN = intArrayOf(1, -1, 1, -1, 1, -3, 3, -1, 3, -1, 3, -3, 1, -1, 1, -1, 1, -7)

    fun samples(sampleRate: Int, amplitude: Float = 0.6f): FloatArray {
        val unit = sampleRate * UNIT_MS / 1000
        val total = PATTERN.sumOf { kotlin.math.abs(it) } * unit
        val out = FloatArray(total)
        val ramp = sampleRate * 5 / 1000
        var pos = 0
        for (units in PATTERN) {
            val length = kotlin.math.abs(units) * unit
            if (units > 0) {
                val w = 2 * PI * TONE_HZ / sampleRate
                for (i in 0 until length) {
                    val edge = minOf(i, length - 1 - i)
                    val gain = if (edge >= ramp) 1.0 else 0.5 - 0.5 * cos(PI * edge / ramp)
                    out[pos + i] = (amplitude * gain * sin(w * i)).toFloat()
                }
            }
            pos += length
        }
        return out
    }

    val durationMs: Int get() = PATTERN.sumOf { kotlin.math.abs(it) } * UNIT_MS
}
