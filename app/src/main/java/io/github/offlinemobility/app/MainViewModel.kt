package io.github.offlinemobility.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import io.github.offlinemobility.app.core.protocol.RescueFrame
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
import kotlinx.coroutines.Job
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
    val lightActive: Boolean = false,
    val lightPattern: LightPattern? = null,
    val lightProgress: Float = 0f,
    val lightOn: Boolean = false,
    val lastFrame: RescueFrame? = null,
    val lastResult: String? = null,
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

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val config = ModemConfig()
    private val senderId = Random.nextInt(256)
    private var sequence = Random.nextInt(16)

    private val pleos = PleosVehicleGateway(application)
    private val simulated = SimulatedVehicleGateway()
    private var gateway: VehicleGateway = pleos

    private val transmitter = AcousticTransmitter(application)
    private val receiver = AcousticReceiver(application, config)
    private val relayPolicy = RelayPolicy(ownSenderId = senderId)

    private val _state = MutableStateFlow(UiState(senderId = senderId))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var soundJob: Job? = null
    private var lightJob: Job? = null
    private var gearJob: Job? = null
    private var inboxId = 0L

    init {
        connectVehicle()
        viewModelScope.launch { receiver.frames.collect(::onDecoded) }
        viewModelScope.launch { receiver.levelDb.collect { db -> _state.update { it.copy(inputLevelDb = db) } } }
    }

    fun connectVehicle() {
        _state.update { it.copy(vehicleMode = VehicleMode.CONNECTING, vehicleMessage = "차량 서비스 연결 중") }
        viewModelScope.launch {
            val result = pleos.connect()
            gateway = if (result.isSuccess) pleos else simulated
            val mode = if (result.isSuccess) VehicleMode.PLEOS else VehicleMode.SIMULATION
            val message = if (result.isSuccess) "Pleos Vehicle SDK 연결됨"
            else "시뮬레이션 모드 · ${result.exceptionOrNull()?.message ?: "차량 서비스 없음"}"
            _state.update { it.copy(vehicleMode = mode, vehicleMessage = message) }

            gearJob?.cancel()
            gearJob = launch { gateway.gear.collect(::onGear) }

            val support = Lamp.entries.associateWith { gateway.lampSupport(it) }
            Log.i(TAG, "vehicle mode=$mode ${result.exceptionOrNull()?.message ?: ""} lamps=$support")
            _state.update { it.copy(lampSupport = support) }
        }
    }

    private fun onGear(gear: GearState) {
        Log.i(TAG, "gear=$gear")
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

    fun toggleBlackout() {
        val turningOn = !_state.value.blackout
        if (turningOn && _state.value.tx.lightActive) {
            lightJob?.cancel()
            _state.update { it.copy(tx = it.tx.copy(lightActive = false, lightOn = false, lastResult = "등화관제로 조명 신호를 껐습니다")) }
        }
        _state.update { it.copy(blackout = turningOn) }
    }

    fun setSimulatedGear(gear: GearState) {
        if (gateway === simulated) simulated.setGear(gear)
    }

    fun startListening() {
        val result = receiver.start(viewModelScope)
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

    fun transmit() {
        val s = _state.value
        if (!s.canTransmit) return
        sequence = (sequence + 1) % 16
        val frame = RescueFrame(s.selected, s.people, senderId, sequence)
        Log.i(TAG, "transmit $frame sound=${s.useSound} light=${s.lightUsable} plan=${s.lightPlan}")
        relayPolicy.markSent(frame, System.currentTimeMillis())
        _state.update { it.copy(tx = TxState(lastFrame = frame)) }
        if (s.useSound) sendSound(frame, repeats = SOUND_REPEATS)
        if (s.lightUsable) sendLight(s.lightPattern, s.lightPlan)
    }

    fun acknowledge(item: InboxItem) {
        if (!_state.value.safety.allowed || _state.value.tx.active) return
        sequence = (sequence + 1) % 16
        val ack = RescueFrame(RescueMessage.ACK, _state.value.people, senderId, sequence)
        relayPolicy.markSent(ack, System.currentTimeMillis())
        _state.update { it.copy(tx = TxState(lastFrame = ack)) }
        sendSound(ack, repeats = 2)
        if (_state.value.lightUsable) sendLight(LightPatterns.REPLY, _state.value.lightPlan)
    }

    fun stopTransmission(reason: String = "송신을 중단했습니다") {
        val wasActive = _state.value.tx.active
        if (wasActive) Log.i(TAG, "stop: $reason")
        soundJob?.cancel()
        lightJob?.cancel()
        if (wasActive) {
            _state.update {
                it.copy(tx = it.tx.copy(soundActive = false, lightActive = false, lightOn = false, lastResult = reason))
            }
        }
    }

    private fun sendSound(frame: RescueFrame, repeats: Int) {
        val audio = AcousticModem.modulate(frame, config)
        soundJob = viewModelScope.launch {
            _state.update { it.copy(tx = it.tx.copy(soundActive = true, soundProgress = 0f)) }
            var error: String? = null
            for (i in 0 until repeats) {
                val result = transmitter.play(audio, config.sampleRate)
                if (result.isFailure) {
                    error = result.exceptionOrNull()?.message
                    Log.w(TAG, "sound failed: $error")
                    break
                }
                _state.update { it.copy(tx = it.tx.copy(soundProgress = (i + 1f) / repeats)) }
                if (i < repeats - 1) delay(SOUND_GAP_MS)
            }
            _state.update {
                it.copy(
                    tx = it.tx.copy(
                        soundActive = false,
                        lastResult = error?.let { e -> "음향 송신 실패: $e" } ?: it.tx.lastResult ?: "음향 신호 ${repeats}회 송신 완료",
                    ),
                )
            }
        }
    }

    private fun sendLight(pattern: LightPattern, plan: LightPlan) {
        val runner = LightSignalRunner(gateway)
        lightJob = viewModelScope.launch {
            _state.update { it.copy(tx = it.tx.copy(lightActive = true, lightPattern = pattern, lightProgress = 0f)) }
            val report = runner.run(pattern, plan.flash, plan.steady) { step, total, on ->
                _state.update { it.copy(tx = it.tx.copy(lightProgress = step.toFloat() / total, lightOn = on)) }
            }
            Log.i(TAG, "light report $report")
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
            _state.update { it.copy(tx = it.tx.copy(lightActive = false, lightOn = false, lastResult = summary)) }
        }
    }

    private fun onDecoded(signal: DecodedSignal) {
        Log.i(TAG, "decoded ${signal.frame} snr=${"%.1f".format(signal.snrDb)} corrected=${signal.correctedSymbols}")
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
        viewModelScope.launch {
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
        viewModelScope.launch { receiver.injectForDemo(audio) }
    }

    override fun onCleared() {
        receiver.stop()
        soundJob?.cancel()
        lightJob?.cancel()
        pleos.release()
        super.onCleared()
    }

    private companion object {
        const val TAG = "OfflineMobility"
        const val SOUND_REPEATS = 3
        const val SOUND_GAP_MS = 400L
        const val MAX_INBOX = 30
    }
}
