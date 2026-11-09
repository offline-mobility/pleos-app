package io.github.offlinemobility.app.core.acoustic

import io.github.offlinemobility.app.core.protocol.RescueFrame
import io.github.offlinemobility.app.core.protocol.RescueMessage
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Writes modem frames as 48 kHz / 16-bit mono WAV files for over-the-air tests
 * (play from a laptop or phone speaker toward the receiver's microphone).
 *
 * Runs only when OM_WAV_DIR is set:
 *   OM_WAV_DIR=/tmp/om ./gradlew testDebugUnitTest --tests '*WavFixtureExport*'
 */
class WavFixtureExport {

    @Test
    fun export() {
        val dir = System.getenv("OM_WAV_DIR")
        assumeTrue("OM_WAV_DIR not set", !dir.isNullOrBlank())
        val out = File(dir!!).apply { mkdirs() }
        val config = ModemConfig()
        val frames = listOf(
            RescueFrame(RescueMessage.MEDICAL, people = 2, senderId = 0xA1, sequence = 1),
            RescueFrame(RescueMessage.TRAPPED, people = 4, senderId = 0xB2, sequence = 2, hop = 1),
            RescueFrame(RescueMessage.WATER, people = 3, senderId = 0xC3, sequence = 3),
            RescueFrame(RescueMessage.ROAD_BLOCKED, people = 1, senderId = 0xD4, sequence = 4, hop = 2),
            RescueFrame(RescueMessage.SHELTER, people = 15, senderId = 0xE5, sequence = 5),
        )
        val silence = FloatArray(config.ms(700))
        val manifest = StringBuilder()
        frames.forEachIndexed { i, frame ->
            val audio = silence + AcousticModem.modulate(frame, config) + silence
            val file = File(out, "frame_${i + 1}_${frame.message.name.lowercase()}.wav")
            writeWav(file, audio, config.sampleRate)
            manifest.appendLine("${file.name}\t$frame")
        }
        File(out, "manifest.tsv").writeText(manifest.toString())
    }

    private fun writeWav(file: File, samples: FloatArray, sampleRate: Int) {
        DataOutputStream(FileOutputStream(file)).use { out ->
            val dataBytes = samples.size * 2
            fun le32(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun le16(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            out.writeBytes("RIFF"); le32(36 + dataBytes); out.writeBytes("WAVE")
            out.writeBytes("fmt "); le32(16); le16(1); le16(1); le32(sampleRate); le32(sampleRate * 2); le16(2); le16(16)
            out.writeBytes("data"); le32(dataBytes)
            for (s in samples) le16((s.coerceIn(-1f, 1f) * 32767).toInt())
        }
    }
}
