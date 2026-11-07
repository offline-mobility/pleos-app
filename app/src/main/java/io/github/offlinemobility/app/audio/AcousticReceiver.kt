package io.github.offlinemobility.app.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import io.github.offlinemobility.app.core.acoustic.AcousticDecoder
import io.github.offlinemobility.app.core.acoustic.DecodedSignal
import io.github.offlinemobility.app.core.acoustic.ModemConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10

/**
 * Continuously captures the cabin microphone and feeds the streaming modem decoder.
 *
 * Prefers AudioSource.UNPROCESSED (no AGC / noise suppression, which would flatten the tones);
 * falls back to VOICE_RECOGNITION, which has the least processing among the always-available sources.
 */
class AcousticReceiver(
    private val context: Context,
    private val config: ModemConfig = ModemConfig(),
) {
    private val _frames = MutableSharedFlow<DecodedSignal>(extraBufferCapacity = 16)
    val frames: SharedFlow<DecodedSignal> = _frames.asSharedFlow()

    private val _levelDb = MutableStateFlow(-90f)
    val levelDb: StateFlow<Float> = _levelDb.asStateFlow()

    private var job: Job? = null
    private var decoder = AcousticDecoder(config) { _frames.tryEmit(it) }

    /** Sample rate the microphone actually delivers (some devices only guarantee 44.1 kHz). */
    var actualSampleRate: Int = config.sampleRate
        private set

    val isRunning: Boolean get() = job?.isActive == true

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope): Result<Unit> {
        if (isRunning) return Result.success(Unit)
        if (!hasPermission()) return Result.failure(SecurityException("마이크 권한이 필요합니다"))

        val minBuffer = AudioRecord.getMinBufferSize(
            config.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBuffer <= 0) return Result.failure(IllegalStateException("이 기기는 48kHz 녹음을 지원하지 않습니다"))

        val record = runCatching {
            AudioRecord.Builder()
                .setAudioSource(preferredSource())
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(config.sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBuffer * 4, config.sampleRate * Float.SIZE_BYTES / 2))
                .build()
        }.getOrElse { return Result.failure(it) }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return Result.failure(IllegalStateException("마이크를 열 수 없습니다"))
        }
        disableVoiceProcessing(record.audioSessionId)

        actualSampleRate = record.sampleRate
        decoder = AcousticDecoder(config.copy(sampleRate = actualSampleRate)) { _frames.tryEmit(it) }
        val activeDecoder = decoder
        job = scope.launch(Dispatchers.Default) {
            val chunk = FloatArray(actualSampleRate / 20) // 50 ms
            record.startRecording()
            try {
                while (isActive) {
                    val read = record.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
                    if (read <= 0) continue
                    synchronized(activeDecoder) { activeDecoder.push(chunk, 0, read) }
                    var sum = 0.0
                    for (i in 0 until read) sum += chunk[i] * chunk[i]
                    _levelDb.value = (10 * log10(sum / read + 1e-12)).toFloat()
                }
            } finally {
                runCatching { record.stop() }
                record.release()
                _levelDb.value = -90f
            }
        }
        return Result.success(Unit)
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Feeds synthetic audio through the same decoder (demo without a second vehicle). */
    fun injectForDemo(samples: FloatArray) {
        val target = decoder
        synchronized(target) { target.push(samples) }
        var sum = 0.0
        for (s in samples) sum += s * s
        _levelDb.value = (10 * log10(sum / samples.size + 1e-12)).toFloat()
    }

    /** Noise suppression, AGC and echo cancellation flatten or remove modem tones. */
    private fun disableVoiceProcessing(sessionId: Int) {
        if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(sessionId)?.enabled = false
        if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(sessionId)?.enabled = false
        if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(sessionId)?.enabled = false
    }

    private fun preferredSource(): Int {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val unprocessed = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
        return if (unprocessed == "true") MediaRecorder.AudioSource.UNPROCESSED
        else MediaRecorder.AudioSource.VOICE_RECOGNITION
    }
}
