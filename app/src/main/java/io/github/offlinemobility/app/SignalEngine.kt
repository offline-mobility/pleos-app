package io.github.offlinemobility.app

import android.app.Application
import io.github.offlinemobility.app.audio.AcousticReceiver
import io.github.offlinemobility.app.audio.AcousticTransmitter
import io.github.offlinemobility.app.core.acoustic.AcousticModem
import io.github.offlinemobility.app.core.acoustic.DecodedSignal
import io.github.offlinemobility.app.core.acoustic.ModemConfig
import io.github.offlinemobility.app.core.light.Lamp
import io.github.offlinemobility.app.core.light.LampGroup
import io.github.offlinemobility.app.core.light.LightPattern
import io.github.offlinemobility.app.core.light.LightPlan
import io.github.offlinemobility.app.core.light.LightPlanner
import io.github.offlinemobility.app.core.light.LightPatterns
import io.github.offlinemobility.app.core.protocol.GeoCell
import io.github.offlinemobility.app.core.protocol.RescueFrame
import io.github.offlinemobility.app.location.Fix
import io.github.offlinemobility.app.location.LocationTracker
import io.github.offlinemobility.app.core.protocol.RescueMessage
import io.github.offlinemobility.app.core.relay.RelayDecision
import io.github.offlinemobility.app.core.relay.RelayPolicy
import io.github.offlinemobility.app.core.safety.GearState
import io.github.offlinemobility.app.core.safety.SafetyGate
import io.github.offlinemobility.app.core.safety.SafetyVerdict
import io.github.offlinemobility.app.core.safety.TransmitChannel
import io.github.offlinemobility.app.core.safety.VehicleSafetyState
import io.github.offlinemobility.app.vehicle.LampSupport
import io.github.offlinemobility.app.vehicle.LightSignalRunner
import io.github.offlinemobility.app.vehicle.PleosVehicleGateway
import io.github.offlinemobility.app.vehicle.SimulatedVehicleGateway
import io.github.offlinemobility.app.vehicle.VehicleGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

enum class VehicleMode { CONNECTING, PLEOS, SIMULATION }

enum class InboxKind { NEW, ECHO }

data class InboxItem(
    val id: Long,
    val frame: RescueFrame,
    val receivedAtMs: Long,
    val snrDb: Double,
    val correctedSymbols: Int,
    val kind: InboxKind,
    val relayState: RelayState,
    val repeats: Int = 1,
    val acknowledged: Boolean = false,
)

enum class RelayState(val label: String) {
    NONE(""),
    SCHEDULED("중계 대기"),
    SENT("중계 완료"),
    NOT_RELAYED("중계 안 함"),
}

data class TxState(
    val soundActive: Boolean = false,
    val soundProgress: Float = 0f,
    val soundTotal: Int = 0,
    val soundDone: Int = 0,
    val soundFailed: Boolean = false,
    val lightActive: Boolean = false,
    val lightPlanned: Boolean = false,
    val lightPattern: LightPattern? = null,
    val lightProgress: Float = 0f,
    val lightOn: Boolean = false,
    val lightConfirmed: Boolean? = null,
    val lightFailed: Boolean = false,
    val message: RescueMessage? = null,
    val people: Int = 0,
    val lastFrame: RescueFrame? = null,
    val lastResult: String? = null,
    /** Every planned channel has finished on its own (not stopped). */
    val finished: Boolean = false,
    val stopped: Boolean = false,
) {
    val active: Boolean get() = soundActive || lightActive
}

data class UiState(
    val vehicleMode: VehicleMode = VehicleMode.CONNECTING,
    val vehicleMessage: String = "차량 서비스 연결 중",
    val gear: GearState = GearState.UNKNOWN,
    val lampSupport: Map<Lamp, LampSupport> = emptyMap(),
    val selected: RescueMessage = RescueMessage.SOS,
    val people: Int = 1,
    val useSound: Boolean = true,
    val useLight: Boolean = true,
    val lampGroup: LampGroup = LampGroup.FRONT_AND_REAR,
    val autoRelay: Boolean = true,
    /** Air-raid blackout (민방공 야간 공습경보: 차량 등화 소등). Exterior light signalling is disabled. */
    val blackout: Boolean = false,
    val listening: Boolean = false,
    val micMessage: String? = null,
    val inputLevelDb: Float = -90f,
    val inbox: List<InboxItem> = emptyList(),
    val tx: TxState = TxState(),
    val senderId: Int = 0,
    /** Own GPS fix, if known and recent. */
    val myFix: Fix? = null,
    val locationPermission: Boolean = false,
    /** Attach the car's position to outgoing messages. */
    val shareLocation: Boolean = true,
) {
    val safety: SafetyVerdict
        get() = SafetyGate.evaluate(VehicleSafetyState(gear), if (useLight) TransmitChannel.LIGHT else TransmitChannel.SOUND)
    val lightPattern: LightPattern get() = LightPatterns.forMessage(selected)
    /** Lamps used for the selected group, chosen from what this vehicle reports as writable. */
    val lightPlan: LightPlan
        get() = LightPlanner.plan(lampGroup) { lampSupport[it] == null || lampSupport[it] == LampSupport.WRITABLE }

    val lightEnabled: Boolean get() = useLight && !blackout
    val lightUsable: Boolean get() = lightEnabled && !lightPlan.isEmpty
    val canTransmit: Boolean get() = safety.allowed && (useSound || lightUsable) && !tx.active
}

/**
 * Owns every long-lived part of the app: vehicle connection, microphone receiver, speaker
 * transmitter, relay policy and UI state. Lives for the whole process so reception and relaying
 * keep running while the screen is closed (see [SignalService]).
 */
class SignalEngine(application: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val config = ModemConfig()
    private val senderId = Random.nextInt(256)
    private var sequence = Random.nextInt(16)

    private val pleos = PleosVehicleGateway(application)
    private val simulated = SimulatedVehicleGateway()
    private var gateway: VehicleGateway = pleos

    private val transmitter = AcousticTransmitter(application)
    private val receiver = AcousticReceiver(application, config)
    private val locationTracker = LocationTracker(application)
    private val relayPolicy = RelayPolicy(ownSenderId = senderId)

    private val _state = MutableStateFlow(UiState(senderId = senderId))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var soundJob: Job? = null
    private var lightJob: Job? = null
    private var gearJob: Job? = null
    private var inboxId = 0L

    init {
        scope.launch { locationTracker.fix.collect { fix -> _state.update { it.copy(myFix = fix) } } }
        refreshLocation()
        connectVehicle()
        scope.launch { receiver.frames.collect(::onDecoded) }
        scope.launch {
            var lastLog = 0L
            receiver.levelDb.collect { db ->
                _state.update { it.copy(inputLevelDb = db) }
                val now = System.currentTimeMillis()
                if (now - lastLog > 2_000) {
                    lastLog = now
                    debugLog { "mic level ${"%.1f".format(db)} dBFS" }
                }
            }
        }
    }

    fun connectVehicle() {
        _state.update { it.copy(vehicleMode = VehicleMode.CONNECTING, vehicleMessage = "차량 서비스 연결 중") }
        scope.launch {
            val result = pleos.connect()
            gateway = if (result.isSuccess) pleos else simulated
            val mode = if (result.isSuccess) VehicleMode.PLEOS else VehicleMode.SIMULATION
            val message = if (result.isSuccess) "Pleos Vehicle SDK 연결됨"
            else "시뮬레이션 모드 · ${result.exceptionOrNull()?.message ?: "차량 서비스 없음"}"
            _state.update { it.copy(vehicleMode = mode, vehicleMessage = message) }

            gearJob?.cancel()
            gearJob = launch { gateway.gear.collect(::onGear) }

            val support = Lamp.entries.associateWith { gateway.lampSupport(it) }
            debugLog { "vehicle mode=$mode ${result.exceptionOrNull()?.message ?: ""} lamps=$support" }
            _state.update { it.copy(lampSupport = support) }
        }
    }

    private fun onGear(gear: GearState) {
        debugLog { "gear=$gear" }
        _state.update { it.copy(gear = gear) }
        // Leaving P stops every outgoing signal immediately; lamps are restored by the runner.
        if (gear != GearState.PARK) stopTransmission("P단 해제로 송신을 중단했습니다")
    }

    fun select(message: RescueMessage) = _state.update { if (it.tx.active) it else it.copy(selected = message) }

    fun changePeople(delta: Int) = _state.update { it.copy(people = (it.people + delta).coerceIn(0, 15)) }

    fun toggleSound() = _state.update { if (it.tx.active) it else it.copy(useSound = !it.useSound) }

    fun toggleLight() = _state.update { if (it.tx.active) it else it.copy(useLight = !it.useLight) }

    fun selectLampGroup(group: LampGroup) = _state.update { if (it.tx.active) it else it.copy(lampGroup = group) }

    fun toggleAutoRelay() = _state.update { it.copy(autoRelay = !it.autoRelay) }

    fun toggleShareLocation() = _state.update { it.copy(shareLocation = !it.shareLocation) }

    /** Starts GPS when permission is available (called again after the user grants it). */
    fun refreshLocation() {
        val granted = locationTracker.hasPermission()
        _state.update { it.copy(locationPermission = granted) }
        if (granted) locationTracker.start()
    }

    private fun outgoingLocation(): GeoCell? {
        if (!_state.value.shareLocation) return null
        val fix = locationTracker.current() ?: return null
        return GeoCell.of(fix.latitude, fix.longitude)
    }

    fun toggleBlackout() {
        val turningOn = !_state.value.blackout
        if (turningOn && _state.value.tx.lightActive) {
            lightJob?.cancel()
            _state.update { it.copy(tx = it.tx.copy(lightActive = false, lightOn = false, lightPlanned = false).settle()) }
        }
        _state.update { it.copy(blackout = turningOn) }
    }

    fun setSimulatedGear(gear: GearState) {
        if (gateway === simulated) simulated.setGear(gear)
    }

    fun startListening() {
        val result = receiver.start(scope)
        _state.update {
            it.copy(
                listening = result.isSuccess,
                micMessage = result.exceptionOrNull()?.message,
            )
        }
    }

    fun stopListening() {
        receiver.stop()
        _state.update { it.copy(listening = false) }
    }

    fun onMicPermissionDenied() = _state.update { it.copy(micMessage = "마이크 권한이 없어 수신할 수 없습니다") }

    /** [withPeople] is false for messages about roads or shelters, which carry no head count. */
    fun send(message: RescueMessage, withPeople: Boolean = true) {
        if (_state.value.tx.active) return
        _state.update { it.copy(selected = message) }
        transmit(withPeople)
    }

    fun transmit(withPeople: Boolean = true) {
        val s = _state.value
        if (!s.canTransmit) return
        sequence = (sequence + 1) % 16
        val frame = RescueFrame(s.selected, if (withPeople) s.people else 0, senderId, sequence, location = outgoingLocation())
        debugLog { "transmit $frame sound=${s.useSound} light=${s.lightUsable} plan=${s.lightPlan}" }
        relayPolicy.markSent(frame, System.currentTimeMillis())
        startTx(frame, s.lightPattern, soundRepeats = if (s.useSound) SOUND_REPEATS else 0)
    }

    fun acknowledge(item: InboxItem) {
        val s = _state.value
        if (!s.safety.allowed || s.tx.active) return
        sequence = (sequence + 1) % 16
        val ack = RescueFrame(RescueMessage.ACK, s.people, senderId, sequence, location = outgoingLocation())
        relayPolicy.markSent(ack, System.currentTimeMillis())
        _state.update { st -> st.copy(inbox = st.inbox.map { if (it.id == item.id) it.copy(acknowledged = true) else it }) }
        startTx(ack, LightPatterns.REPLY, soundRepeats = if (s.useSound) 2 else 0)
    }

    private fun startTx(frame: RescueFrame, pattern: LightPattern, soundRepeats: Int) {
        val s = _state.value
        val light = s.lightUsable
        _state.update {
            it.copy(
                tx = TxState(
                    soundActive = soundRepeats > 0,
                    soundTotal = soundRepeats,
                    lightActive = light,
                    lightPlanned = light,
                    lightPattern = if (light) pattern else null,
                    message = frame.message,
                    people = frame.people,
                    lastFrame = frame,
                ),
            )
        }
        if (soundRepeats > 0) sendSound(frame, soundRepeats)
        if (light) sendLight(pattern, s.lightPlan)
    }

    /** Back to the message list after a finished or stopped transmission. */
    fun dismissResult() {
        if (_state.value.tx.active) return
        _state.update { it.copy(tx = TxState()) }
    }

    fun stopTransmission(reason: String = "보내기를 멈췄어요") {
        val wasActive = _state.value.tx.active
        if (wasActive) debugLog { "stop: $reason" }
        soundJob?.cancel()
        lightJob?.cancel()
        if (wasActive) {
            _state.update {
                it.copy(
                    tx = it.tx.copy(
                        soundActive = false,
                        lightActive = false,
                        lightOn = false,
                        stopped = true,
                        finished = false,
                        lastResult = reason,
                    ),
                )
            }
        }
    }

    private fun TxState.settle(): TxState = if (!active && !stopped) copy(finished = true) else this

    private fun sendSound(frame: RescueFrame, repeats: Int) {
        val audio = AcousticModem.modulate(frame, config)
        soundJob = scope.launch {
            _state.update { it.copy(tx = it.tx.copy(soundActive = true, soundProgress = 0f)) }
            var error: String? = null
            for (i in 0 until repeats) {
                val result = transmitter.play(audio, config.sampleRate)
                if (result.isFailure) {
                    error = result.exceptionOrNull()?.message
                    debugLog { "sound failed: $error" }
                    break
                }
                _state.update { it.copy(tx = it.tx.copy(soundProgress = (i + 1f) / repeats, soundDone = i + 1)) }
                if (i < repeats - 1) delay(SOUND_GAP_MS)
            }
            _state.update {
                it.copy(
                    tx = it.tx.copy(
                        soundActive = false,
                        soundFailed = error != null,
                        lastResult = error?.let { e -> "음향 송신 실패: $e" } ?: it.tx.lastResult,
                    ).settle(),
                )
            }
        }
    }

    private fun sendLight(pattern: LightPattern, plan: LightPlan) {
        val runner = LightSignalRunner(gateway)
        lightJob = scope.launch {
            _state.update { it.copy(tx = it.tx.copy(lightActive = true, lightPattern = pattern, lightProgress = 0f)) }
            val report = runner.run(pattern, plan.flash, plan.steady) { step, total, on ->
                _state.update { it.copy(tx = it.tx.copy(lightProgress = step.toFloat() / total, lightOn = on)) }
            }
            debugLog { "light report $report" }
            val confirmed = report.litConfirmed.filterValues { it == true }.keys
            val unconfirmed = report.litConfirmed.filterValues { it != true }.keys
            val litText = buildList {
                if (confirmed.isNotEmpty()) add("점등 확인 ${confirmed.joinToString { it.label }}")
                if (unconfirmed.isNotEmpty()) add("점등 미확인 ${unconfirmed.joinToString { it.label }}")
            }.joinToString(" · ")
            val summary = if (report.failures == 0) {
                "${pattern.title} 완료 · 평균 전환 ${report.averageSwitchLatencyMs}ms · $litText"
            } else {
                "조명 제어 실패 ${report.failures}회 · 차량이 이 조명 제어를 허용하지 않습니다"
            }
            debugLog { summary }
            _state.update {
                it.copy(
                    tx = it.tx.copy(
                        lightActive = false,
                        lightOn = false,
                        lightConfirmed = confirmed.isNotEmpty(),
                        lightFailed = report.failures > 0,
                    ).settle(),
                )
            }
        }
    }

    private fun onDecoded(signal: DecodedSignal) {
        debugLog { "decoded ${signal.frame} snr=${"%.1f".format(signal.snrDb)} corrected=${signal.correctedSymbols}" }
        val now = System.currentTimeMillis()
        when (val decision = relayPolicy.onReceived(signal.frame, now)) {
            is RelayDecision.Duplicate -> bumpRepeat(decision.frame)
            is RelayDecision.EchoOfOwn -> addInbox(signal, InboxKind.ECHO, RelayState.NONE)
            is RelayDecision.Fresh -> {
                val relay = decision.relay
                val willRelay = relay != null && _state.value.autoRelay
                val item = addInbox(
                    signal,
                    InboxKind.NEW,
                    when {
                        relay == null -> RelayState.NONE
                        willRelay -> RelayState.SCHEDULED
                        else -> RelayState.NOT_RELAYED
                    },
                )
                if (willRelay) scheduleRelay(item.id, relay!!, decision.relayDelayMs)
            }
        }
    }

    private fun scheduleRelay(itemId: Long, frame: RescueFrame, delayMs: Long) {
        scope.launch {
            delay(delayMs)
            // Relaying is acoustic only and still requires the vehicle to be parked.
            while (_state.value.tx.soundActive) delay(500)
            if (!_state.value.safety.allowed) {
                setRelayState(itemId, RelayState.NOT_RELAYED)
                return@launch
            }
            relayPolicy.markSent(frame, System.currentTimeMillis())
            val result = transmitter.play(AcousticModem.modulate(frame, config), config.sampleRate)
            setRelayState(itemId, if (result.isSuccess) RelayState.SENT else RelayState.NOT_RELAYED)
        }
    }

    private fun addInbox(signal: DecodedSignal, kind: InboxKind, relay: RelayState): InboxItem {
        val item = InboxItem(
            id = ++inboxId,
            frame = signal.frame,
            receivedAtMs = System.currentTimeMillis(),
            snrDb = signal.snrDb,
            correctedSymbols = signal.correctedSymbols,
            kind = kind,
            relayState = relay,
        )
        _state.update { it.copy(inbox = (listOf(item) + it.inbox).take(MAX_INBOX)) }
        return item
    }

    private fun bumpRepeat(frame: RescueFrame) = _state.update { s ->
        val index = s.inbox.indexOfFirst { it.frame.originKey == frame.originKey }
        if (index < 0) s
        else s.copy(inbox = s.inbox.toMutableList().also { it[index] = it[index].copy(repeats = it[index].repeats + 1) })
    }

    private fun setRelayState(itemId: Long, relay: RelayState) = _state.update { s ->
        s.copy(inbox = s.inbox.map { if (it.id == itemId) it.copy(relayState = relay) else it })
    }

    /**
     * Demo without a second vehicle: synthesizes another car's frame, degrades it with distance
     * attenuation, reflection and noise, and runs it through the real decoder.
     */
    fun simulateIncoming() {
        val random = Random.Default
        val frame = RescueFrame(
            message = RescueMessage.entries.filter { it != RescueMessage.ACK }.random(),
            people = 1 + random.nextInt(5),
            senderId = (senderId + 1 + random.nextInt(254)) % 256,
            sequence = random.nextInt(16),
            hop = random.nextInt(2),
            location = demoLocation(random),
        )
        val tx = AcousticModem.modulate(frame, config)
        val lead = config.ms(200)
        val delay = config.ms(14)
        val noise = 0.01
        val audio = FloatArray(lead + tx.size + config.ms(300)) { i ->
            val j = i - lead
            var v = 0.0
            if (j in tx.indices) v += 0.08 * tx[j]
            if (j - delay in tx.indices) v += 0.04 * tx[j - delay]
            val u1 = random.nextDouble().coerceAtLeast(1e-12)
            v += noise * sqrt(-2 * ln(u1)) * cos(2 * PI * random.nextDouble())
            v.toFloat()
        }
        scope.launch { receiver.injectForDemo(audio) }
    }


    /** A point 100–900 m away from our own fix (or central Seoul when we have none). */
    private fun demoLocation(random: Random): GeoCell {
        val base = locationTracker.current()
        val lat0 = base?.latitude ?: 37.5665
        val lon0 = base?.longitude ?: 126.9780
        val distance = random.nextDouble(100.0, 900.0)
        val bearing = random.nextDouble(0.0, 2 * PI)
        val dLat = distance * cos(bearing) / 111_320.0
        val dLon = distance * kotlin.math.sin(bearing) / (111_320.0 * cos(Math.toRadians(lat0)))
        return GeoCell.of(lat0 + dLat, lon0 + dLon)
    }

    private companion object {
        const val SOUND_REPEATS = 3
        const val SOUND_GAP_MS = 400L
        const val MAX_INBOX = 30
    }
}
