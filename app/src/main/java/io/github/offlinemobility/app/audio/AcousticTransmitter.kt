package io.github.offlinemobility.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Plays modem audio through the vehicle speakers.
 *
 * Follows the Pleos audio focus guide: request focus before playback, stop immediately on loss,
 * abandon focus afterwards. Usage ALARM routes to the cabin speakers on Android Automotive.
 */
class AcousticTransmitter(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    @Volatile
    private var focusLost = false

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                focusLost = true
            }
        }
        .build()

    suspend fun play(samples: FloatArray, sampleRate: Int): Result<Unit> = withContext(Dispatchers.IO) {
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return@withContext Result.failure(IllegalStateException("오디오 포커스를 얻지 못했습니다"))
        }
        focusLost = false
        // Pre-roll silence so the amplifier is awake before the preamble (Pleos voice guide: ≥150 ms).
        val preRoll = sampleRate / 1000 * PRE_ROLL_MS
        val pcm = FloatArray(preRoll + samples.size + preRoll)
        samples.copyInto(pcm, preRoll)

        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * Float.SIZE_BYTES)
            .build()

        try {
            track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
            track.play()
            val durationMs = pcm.size * 1000L / sampleRate
            val started = System.currentTimeMillis()
            while (coroutineContext.isActive && track.playbackHeadPosition < pcm.size) {
                if (focusLost) return@withContext Result.failure(IllegalStateException("다른 오디오에 의해 중단되었습니다"))
                if (System.currentTimeMillis() - started > durationMs + 1_000) break
                delay(20)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { track.stop() }
            track.release()
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    private companion object {
        const val PRE_ROLL_MS = 180
    }
}
