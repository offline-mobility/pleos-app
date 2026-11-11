package io.github.offlinemobility.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import io.github.offlinemobility.app.debugLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import ai.pleos.playground.tts.TextToSpeech as PleosTts
import ai.pleos.playground.tts.constant.ErrorType as PleosError
import ai.pleos.playground.tts.constant.Mode as PleosMode
import ai.pleos.playground.tts.listener.EventListener as PleosEvents
import ai.pleos.playground.tts.listener.OnInitializeListener as PleosInit

/**
 * Reads received messages aloud so the driver does not have to look at the screen.
 *
 * Engine order: the device's Android text-to-speech with Korean, otherwise the Pleos TextToSpeech
 * SDK in ON_DEVICE mode (no network, no app registration). Utterances are queued and spoken one at
 * a time; if no engine is available nothing is spoken.
 */
class VoiceAnnouncer(context: Context, scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private val queue = Channel<String>(capacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var backend: Backend? = null

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    init {
        AndroidBackend.create(appContext, attributes) { android ->
            if (android != null) use(android, "android") else PleosBackend.create(appContext, attributes) { pleos -> use(pleos, "pleos") }
        }
        scope.launch {
            for (text in queue) backend?.speak(text)
        }
    }

    private fun use(engine: Backend?, name: String) {
        backend = engine
        _available.value = engine != null
        debugLog { "voice engine=${if (engine != null) name else "none"}" }
    }

    fun say(text: String) {
        if (backend != null) queue.trySend(text)
    }

    fun stop() {
        while (queue.tryReceive().isSuccess) Unit
        backend?.stop()
    }

    private interface Backend {
        suspend fun speak(text: String)
        fun stop()
    }

    private class AndroidBackend(private val tts: TextToSpeech) : Backend {
        private var pending: CompletableDeferred<Unit>? = null

        init {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { pending?.complete(Unit) }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { pending?.complete(Unit) }
            })
        }

        override suspend fun speak(text: String) {
            val done = CompletableDeferred<Unit>().also { pending = it }
            // Pleos voice guidance guide: 150–200 ms of leading silence so the first syllable is not cut.
            tts.playSilentUtterance(PRE_ROLL_MS, TextToSpeech.QUEUE_ADD, UUID.randomUUID().toString())
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
            withTimeoutOrNull(MAX_UTTERANCE_MS) { done.await() }
        }

        override fun stop() {
            tts.stop()
            pending?.complete(Unit)
        }

        companion object {
            fun create(context: Context, attributes: AudioAttributes, onReady: (Backend?) -> Unit) {
                var tts: TextToSpeech? = null
                tts = TextToSpeech(context) { status ->
                    val engine = tts
                    val korean = engine != null && status == TextToSpeech.SUCCESS &&
                        engine.setLanguage(Locale.KOREA) !in setOf(TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED)
                    if (korean) {
                        engine!!.setAudioAttributes(attributes)
                        engine.setSpeechRate(0.9f)
                        onReady(AndroidBackend(engine))
                    } else {
                        runCatching { engine?.shutdown() }
                        onReady(null)
                    }
                }
            }
        }
    }

    private class PleosBackend(private val tts: PleosTts) : Backend {
        private var pending: CompletableDeferred<Unit>? = null

        init {
            tts.addEventListener(object : PleosEvents {
                override fun onStart() = debugLog { "pleos tts started" }
                override fun onReady() = Unit
                override fun onDone() { pending?.complete(Unit) }
                override fun onError(type: PleosError) {
                    debugLog { "pleos tts error: $type" }
                    pending?.complete(Unit)
                }
                override fun onStop() { pending?.complete(Unit) }
                override fun onUpdatedRms(rms: Double) = Unit
            })
        }

        override suspend fun speak(text: String) {
            val done = CompletableDeferred<Unit>().also { pending = it }
            debugLog { "pleos tts speak: $text" }
            runCatching { tts.speak(text, false) }.onFailure {
                debugLog { "pleos tts speak failed: $it" }
                done.complete(Unit)
            }
            val finished = withTimeoutOrNull(MAX_UTTERANCE_MS) { done.await() } != null
            debugLog { "pleos tts finished=$finished" }
        }

        override fun stop() {
            runCatching { tts.stop() }
            pending?.complete(Unit)
        }

        companion object {
            fun create(context: Context, attributes: AudioAttributes, onReady: (Backend?) -> Unit) {
                val tts = runCatching { PleosTts(context, PleosMode.ON_DEVICE) }
                    .onFailure { debugLog { "pleos tts create failed: $it" } }
                    .getOrNull() ?: return onReady(null)
                runCatching {
                    tts.initialize(object : PleosInit {
                        override fun onSuccess() {
                            runCatching {
                                tts.setLocale(Locale.KOREA)
                                tts.setAudioAttributes(attributes)
                            }
                            onReady(PleosBackend(tts))
                        }

                        override fun onFailure(type: PleosError) {
                            debugLog { "pleos tts init failed: $type" }
                            runCatching { tts.release() }
                            onReady(null)
                        }
                    })
                }.onFailure {
                    debugLog { "pleos tts init threw: $it" }
                    onReady(null)
                }
            }
        }
    }

    private companion object {
        const val PRE_ROLL_MS = 180L
        const val MAX_UTTERANCE_MS = 20_000L
    }
}
