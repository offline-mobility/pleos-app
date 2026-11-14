package io.github.offlinemobility.app

import android.app.Application
import io.github.offlinemobility.app.audio.AcousticReceiver
import io.github.offlinemobility.app.audio.AcousticTransmitter
import io.github.offlinemobility.app.audio.VoiceAnnouncer
import io.github.offlinemobility.app.core.acoustic.AudibleSos
import io.github.offlinemobility.app.core.protocol.Geo
import io.github.offlinemobility.app.ui.copy
import io.github.offlinemobility.app.core.acoustic.AcousticModem
import io.github.offlinemobility.app.core.acoustic.DecodedSignal
import io.github.offlinemobility.app.core.acoustic.ModemConfig
import io.github.offlinemobility.app.core.cabin.KeepWarm
import io.github.offlinemobility.app.core.cabin.KeepWarmController
import io.github.offlinemobility.app.core.cabin.KeepWarmEstimate
import io.github.offlinemobility.app.core.cabin.WarmLevel
import io.github.offlinemobility.app.core.disaster.DisasterGuide
import io.github.offlinemobility.app.core.disaster.DisasterGuides
import io.github.offlinemobility.app.core.disaster.DisasterType
import io.github.offlinemobility.app.core.disaster.WindowAdvice
import io.github.offlinemobility.app.core.energy.Endurance
import io.github.offlinemobility.app.core.energy.EnduranceEstimate
import io.github.offlinemobility.app.core.energy.EnergyReading
import io.github.offlinemobility.app.core.gateway.RelayLink
import io.github.offlinemobility.app.core.gateway.RescueReport
import io.github.offlinemobility.app.core.online.OfficialAlert
import io.github.offlinemobility.app.core.online.RemoteIncident
import io.github.offlinemobility.app.core.online.Area
import io.github.offlinemobility.app.core.online.CivilShelters
import io.github.offlinemobility.app.core.online.Shelter
import io.github.offlinemobility.app.core.position.PositionGuard
import io.github.offlinemobility.app.core.position.positionStillValid
import io.github.offlinemobility.app.core.online.ShelterFinder
import io.github.offlinemobility.app.gateway.GatewayStatus
import io.github.offlinemobility.app.net.ServerClient
import io.github.offlinemobility.app.online.OnlineState
import io.github.offlinemobility.app.online.OnlineSync
import io.github.offlinemobility.app.gateway.GatewayUploader
import io.github.offlinemobility.app.gateway.RescueOutbox
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
import io.github.offlinemobility.app.vehicle.ClimateSupport
import io.github.offlinemobility.app.vehicle.KeepWarmRunner
import io.github.offlinemobility.app.vehicle.LampSupport
import io.github.offlinemobility.app.vehicle.LightSignalRunner
import io.github.offlinemobility.app.vehicle.PleosVehicleGateway
import io.github.offlinemobility.app.vehicle.SimulatedVehicleGateway
import io.github.offlinemobility.app.vehicle.VehicleGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

enum class InboxKind { NEW, ECHO, SERVER }

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
    /** Uploaded to the rescue server by this car. */
    val forwarded: Boolean = false,
    /** For [InboxKind.SERVER]: the server's incident id and how many cars reported it. */
    val serverUid: String? = null,
    val confirmations: Int = 0,
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
    /** Link for the QR code that lets a phone carry [lastFrame] to the rescue server; null without a server. */
    val relayUrl: String? = null,
    val lastResult: String? = null,
    val sosBeeping: Boolean = false,
    /** Every planned channel has finished on its own (not stopped). */
    val finished: Boolean = false,
    val stopped: Boolean = false,
) {
    val active: Boolean get() = soundActive || lightActive
}

/** Energy-saving heating while waiting for rescue. */
data class KeepWarmState(
    val support: ClimateSupport = ClimateSupport(),
    val level: WarmLevel = WarmLevel.SAVER,
    val active: Boolean = false,
    /** Heater or blower currently running (false while the heater rests between cycles). */
    val hvacOn: Boolean = false,
    val freshAir: Boolean = false,
    val cabinC: Double? = null,
    /** The vehicle refused at least one setting. */
    val refused: Boolean = false,
    /** Why heating went back to normal without the user asking. */
    val endedNote: String? = null,
)

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
    /**
     * Own position the app trusts (see [PositionGuard]): the latest believable GPS reading, or one
     * remembered from earlier. It may be minutes or hours old; [Fix.timeMs] says when it was taken.
     */
    val myFix: Fix? = null,
    /** A GPS reading that jumped too far to believe, held until the person decides; metres from [myFix]. */
    val positionJumpM: Double? = null,
    /** Readings near a place the person rejected are being ignored (likely jamming or spoofing). */
    val positionIgnoring: Boolean = false,
    /** When the car was last put in P and has stayed there; null while it is not parked. */
    val parkedSinceMs: Long? = null,
    /** Area picked by hand while the car's position is unknown. */
    val chosenArea: Area? = null,
    /** 시·도 → 시·군·구 of the shelters inside the app, for picking an area by hand. */
    val shelterAreas: Map<String, List<String>> = emptyMap(),
    /** Date of the shelter data inside the app, e.g. "2026-11-13"; null until it is loaded. */
    val bundledSheltersDate: String? = null,
    /**
     * Shelters to show: around [myFix] from the app's own data plus whatever the server sent, or
     * those in [chosenArea] when the position is unknown.
     */
    val shelters: List<Shelter> = emptyList(),
    val locationPermission: Boolean = false,
    /** Attach the car's position to outgoing messages. */
    val shareLocation: Boolean = true,
    val disaster: DisasterType = DisasterType.EVACUATION,
    /** Plain Morse SOS beeps after critical data frames, for people without the app. */
    val audibleSos: Boolean = true,
    /** Read received messages aloud. */
    val voiceAnnounce: Boolean = true,
    val voiceAvailable: Boolean = false,
    val energy: EnergyReading? = null,
    val gateway: GatewayStatus = GatewayStatus(enabled = false),
    val online: OnlineState = OnlineState(),
    val dismissedAlerts: Set<String> = emptySet(),
    val keepWarm: KeepWarmState = KeepWarmState(),
) {
    val guide: DisasterGuide get() = DisasterGuides.of(disaster)

    /** Newest official message the user has not closed yet. */
    val topAlert: OfficialAlert? get() = online.alerts.firstOrNull { it.id !in dismissedAlerts }

    /** Outside air has to stay out in this disaster mode (chemical, radiation, smoke). */
    val cabinSealed: Boolean get() = guide.window == WindowAdvice.KEEP_CLOSED

    /** Null on vehicles without a traction battery reading or a controllable heater. */
    val keepWarmEstimate: KeepWarmEstimate?
        get() {
            if (!keepWarm.support.heater) return null
            val remainingWh = energy?.evRemainingWh?.takeIf { it > 0 } ?: return null
            val shelterKm = myFix?.let { fix ->
                ShelterFinder.nearest(shelters, fix.latitude, fix.longitude, 1).firstOrNull()?.second?.meters?.div(1000.0)
            }
            return KeepWarm.estimate(remainingWh, people, cabinSealed, shelterKm)
        }

    val endurance: EnduranceEstimate? get() = energy?.let { Endurance.estimate(it, lightsOn = lightUsable) }

    /** [myFix] if it may still be sent as where the car is now (see [positionStillValid]). */
    fun sendablePosition(nowMs: Long = System.currentTimeMillis()): Fix? =
        myFix?.takeIf { positionStillValid(it, nowMs, parkedSinceMs) }

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
    private val announcer = VoiceAnnouncer(application, scope)
    private val outbox = RescueOutbox(application)
    private val serverUrl: String = application.getString(R.string.server_url)
    private val server = ServerClient(serverUrl)
    private val uploader = GatewayUploader(application, outbox, server, scope)
    private val onlineSync = OnlineSync(
        application,
        server,
        scope,
        isOnline = { _state.value.gateway.online },
        position = { _state.value.myFix },
    )
    private val positionGuard = PositionGuard()
    private var civil: CivilShelters? = null
    /** Where the shelter list was last worked out; it is redone after moving [SHELTER_REFRESH_M]. */
    private var sheltersCenter: Fix? = null
    private var lastSavedFix: Fix? = null
    private val prefs = application.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    private val relayPolicy = RelayPolicy(ownSenderId = senderId)

    private val _state = MutableStateFlow(UiState(senderId = senderId))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var soundJob: Job? = null
    private var lightJob: Job? = null
    private var gearJob: Job? = null
    private var energyJob: Job? = null
    private var warmJob: Job? = null
    private var peopleEdited = false
    private var inboxId = 0L

    fun dismissAlert(id: String) = _state.update { it.copy(dismissedAlerts = it.dismissedAlerts + id) }

    /** Switches to the disaster mode an official message points to. */
    fun acceptAlert(alert: OfficialAlert) {
        alert.type?.let(::selectDisaster)
        dismissAlert(alert.id)
    }

    /** Shows rescue requests known to the server that this car has not heard directly. */
    private fun mergeServerIncidents(incidents: List<RemoteIncident>) = _state.update { s ->
        val heardLocally = s.inbox.filter { it.kind != InboxKind.SERVER }.map { it.frame.originKey }.toSet()
        val mine = ownSentKeys()
        val fromServer = incidents
            .filter { it.frame.senderId != senderId && it.frame.originKey !in heardLocally && it.frame.originKey !in mine }
            .map { incident ->
                val existing = s.inbox.firstOrNull { it.serverUid == incident.uid }
                InboxItem(
                    id = existing?.id ?: ++inboxId,
                    frame = incident.frame,
                    receivedAtMs = incident.lastHeardAtMs,
                    snrDb = 0.0,
                    correctedSymbols = 0,
                    kind = InboxKind.SERVER,
                    relayState = RelayState.NONE,
                    serverUid = incident.uid,
                    confirmations = incident.confirmations,
                )
            }
        val local = s.inbox.filter { it.kind != InboxKind.SERVER }
        s.copy(inbox = (local + fromServer).sortedByDescending { it.receivedAtMs }.take(MAX_INBOX))
    }

    /**
     * Messages this car sent in earlier sessions. The sender id changes every time the app starts,
     * so without this list the car's own requests would come back from the server looking like
     * someone else's. Kept as "originKey:sentAtMs" for as long as the server keeps a request.
     */
    private fun ownSentKeys(): Set<Int> {
        val cutoff = System.currentTimeMillis() - OWN_SENT_KEEP_MS
        return prefs.getStringSet(KEY_OWN_SENT, emptySet()).orEmpty().mapNotNull { entry ->
            val (key, at) = entry.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            key.toIntOrNull()?.takeIf { (at.toLongOrNull() ?: 0L) >= cutoff }
        }.toSet()
    }

    private fun rememberOwnSent(frame: RescueFrame) {
        val now = System.currentTimeMillis()
        val kept = prefs.getStringSet(KEY_OWN_SENT, emptySet()).orEmpty()
            .filter { (it.substringAfter(':').toLongOrNull() ?: 0L) >= now - OWN_SENT_KEEP_MS }
        prefs.edit().putStringSet(KEY_OWN_SENT, (kept + "${frame.originKey}:$now").toSet()).apply()
    }

    /** True while blackout is on only because the air-raid mode turned it on. */
    private var blackoutByMode = false

    init {
        uploader.start()
        onlineSync.start()
        scope.launch {
            onlineSync.state.collect { online ->
                val sheltersChanged = online.shelters != _state.value.online.shelters
                _state.update { it.copy(online = online) }
                mergeServerIncidents(online.incidents)
                if (sheltersChanged) refreshShelters(force = true)
            }
        }
        scope.launch { uploader.status.collect { st -> _state.update { it.copy(gateway = st) } } }
        scope.launch { announcer.available.collect { ok -> _state.update { it.copy(voiceAvailable = ok) } } }
        val savedDisaster = prefs.getString(KEY_DISASTER, null)
            ?.let { name -> DisasterType.entries.firstOrNull { it.name == name } }
        if (savedDisaster != null) {
            val modeBlackout = DisasterGuides.of(savedDisaster).blackout
            blackoutByMode = modeBlackout
            _state.update { it.copy(disaster = savedDisaster, blackout = modeBlackout) }
        }
        restoreSavedPosition()
        scope.launch {
            civil = withContext(Dispatchers.IO) {
                runCatching {
                    application.assets.open(CIVIL_ASSET).bufferedReader().use { CivilShelters.parse(it.readText()) }
                }.onFailure { debugLog { "civil shelters not loaded: ${it.message}" } }.getOrNull()
            }
            _state.update { it.copy(shelterAreas = civil?.areas.orEmpty(), bundledSheltersDate = civil?.generatedAt) }
            refreshShelters(force = true)
        }
        scope.launch { locationTracker.fix.collect { fix -> if (fix != null) onReading(fix) } }
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

            energyJob?.cancel()
            energyJob = launch {
                while (true) {
                    val reading = gateway.energy()
                    _state.update { it.copy(energy = reading.takeIf { r -> r.evRemainingWh != null || r.fuelPercent != null }) }
                    debugLog { "energy $reading" }
                    delay(ENERGY_POLL_MS)
                }
            }

            val support = Lamp.entries.associateWith { gateway.lampSupport(it) }
            val climate = gateway.climateSupport()
            debugLog { "vehicle mode=$mode ${result.exceptionOrNull()?.message ?: ""} lamps=$support climate=$climate" }
            _state.update { it.copy(lampSupport = support, keepWarm = it.keepWarm.copy(support = climate)) }
            // Head count starts from the seat sensors until the user sets it.
            val occupied = gateway.occupiedSeats()?.size ?: 0
            if (occupied > 0 && !peopleEdited) _state.update { it.copy(people = occupied) }
        }
    }

    private fun onGear(gear: GearState) {
        debugLog { "gear=$gear" }
        _state.update {
            it.copy(
                gear = gear,
                parkedSinceMs = if (gear == GearState.PARK) it.parkedSinceMs ?: System.currentTimeMillis() else null,
            )
        }
        // Leaving P stops every outgoing signal immediately; lamps are restored by the runner.
        if (gear != GearState.PARK) {
            stopTransmission("P단 해제로 송신을 중단했습니다")
            stopKeepWarm("기어를 바꿔서 난방을 원래대로 돌려놓았어요")
        }
    }

    fun selectWarmLevel(level: WarmLevel) =
        _state.update { if (it.keepWarm.active) it else it.copy(keepWarm = it.keepWarm.copy(level = level)) }

    /** Starts energy-saving heating. Only while parked; the runner restores the climate settings when it ends. */
    fun startKeepWarm() {
        val s = _state.value
        val estimate = s.keepWarmEstimate ?: return
        if (s.gear != GearState.PARK) return
        val controller = KeepWarmController(s.keepWarm.level, s.cabinSealed, estimate.freshMinutes)
        val runner = KeepWarmRunner(gateway)
        val previous = warmJob
        _state.update { it.copy(keepWarm = it.keepWarm.copy(active = true, hvacOn = false, freshAir = false, refused = false, endedNote = null)) }
        warmJob = scope.launch {
            // A plan that is still restoring the climate must finish first, or its restore would undo this one.
            previous?.cancelAndJoin()
            runner.run(controller, s.people.coerceAtLeast(1)) { tick ->
                _state.update {
                    it.copy(
                        keepWarm = it.keepWarm.copy(
                            hvacOn = tick.target.hvacOn,
                            freshAir = tick.target.freshAir,
                            cabinC = tick.cabinC,
                            refused = tick.failures > 0,
                        ),
                    )
                }
            }
        }
    }

    fun stopKeepWarm(note: String? = null) {
        if (!_state.value.keepWarm.active) return
        warmJob?.cancel()
        _state.update { it.copy(keepWarm = it.keepWarm.copy(active = false, hvacOn = false, freshAir = false, endedNote = note)) }
    }

    fun select(message: RescueMessage) = _state.update { if (it.tx.active) it else it.copy(selected = message) }

    fun changePeople(delta: Int) {
        peopleEdited = true
        _state.update { it.copy(people = (it.people + delta).coerceIn(0, 15)) }
    }

    fun toggleSound() = _state.update { if (it.tx.active) it else it.copy(useSound = !it.useSound) }

    fun toggleLight() = _state.update { if (it.tx.active) it else it.copy(useLight = !it.useLight) }

    fun selectLampGroup(group: LampGroup) = _state.update { if (it.tx.active) it else it.copy(lampGroup = group) }

    fun toggleAutoRelay() = _state.update { it.copy(autoRelay = !it.autoRelay) }

    fun toggleShareLocation() = _state.update { it.copy(shareLocation = !it.shareLocation) }

    fun toggleAudibleSos() = _state.update { it.copy(audibleSos = !it.audibleSos) }

    fun toggleVoiceAnnounce() {
        _state.update { it.copy(voiceAnnounce = !it.voiceAnnounce) }
        if (!_state.value.voiceAnnounce) announcer.stop()
    }



    /**
     * Switches the disaster mode. A mode that calls for blackout (air raid) turns it on, and leaving
     * that mode turns it off again unless the user had switched it on themselves.
     */
    fun selectDisaster(type: DisasterType) {
        prefs.edit().putString(KEY_DISASTER, type.name).apply()
        val guide = DisasterGuides.of(type)
        val blackout = _state.value.blackout
        when {
            guide.blackout && !blackout -> {
                toggleBlackout()
                blackoutByMode = true
            }
            !guide.blackout && blackout && blackoutByMode -> {
                toggleBlackout()
                blackoutByMode = false
            }
        }
        val wasSealed = _state.value.cabinSealed
        _state.update { it.copy(disaster = type) }
        // Whether outside air may come in is fixed when the heating plan starts, so start it again.
        if (_state.value.keepWarm.active && _state.value.cabinSealed != wasSealed) startKeepWarm()
    }

    /** Starts GPS when permission is available (called again after the user grants it). */
    fun refreshLocation() {
        val granted = locationTracker.hasPermission()
        _state.update { it.copy(locationPermission = granted) }
        if (granted) locationTracker.start()
    }

    private fun onReading(fix: Fix) {
        val before = positionGuard.trusted
        positionGuard.offer(fix)
        val live = positionGuard.trusted?.takeIf { it != before }
        publishPosition(clearArea = live != null)
        live?.let(::savePosition)
    }

    /** The car really moved: use the reading that looked like a jump. */
    fun usePositionJump() {
        positionGuard.useSuspect()
        publishPosition(clearArea = true)
        positionGuard.trusted?.let(::savePosition)
    }

    /** The car has not moved: keep the old position and stop asking about that place. */
    fun keepPosition() {
        positionGuard.keepTrusted()
        publishPosition(clearArea = false)
    }

    /** Area picked by hand while GPS gives no position; null goes back to the position. */
    fun chooseArea(area: Area?) {
        _state.update { it.copy(chosenArea = area) }
        refreshShelters(force = true)
    }

    private fun publishPosition(clearArea: Boolean) {
        _state.update {
            it.copy(
                myFix = positionGuard.trusted,
                positionJumpM = positionGuard.suspectJumpM(),
                positionIgnoring = positionGuard.ignoring,
                chosenArea = if (clearArea) null else it.chosenArea,
            )
        }
        refreshShelters()
    }

    /** Shelters for the current position or chosen area, from the app's data and the server's. */
    private fun refreshShelters(force: Boolean = false) {
        val s = _state.value
        val area = s.chosenArea
        val fix = s.myFix
        val list = when {
            area != null -> civil?.inArea(area).orEmpty()
            fix != null -> {
                val moved = sheltersCenter?.let { Geo.offset(it.latitude, it.longitude, fix.latitude, fix.longitude).meters }
                if (!force && moved != null && moved < SHELTER_REFRESH_M) return
                sheltersCenter = fix
                (s.online.shelters + civil?.near(fix.latitude, fix.longitude, SHELTER_RADIUS_KM, SHELTER_COUNT).orEmpty())
                    .distinctBy { it.id }
            }
            else -> s.online.shelters
        }
        _state.update { it.copy(shelters = list) }
    }

    /** Remembered across restarts so a later jam still leaves the last known place. */
    private fun savePosition(fix: Fix) {
        val last = lastSavedFix
        if (last != null && fix.timeMs - last.timeMs < 60_000 &&
            Geo.offset(last.latitude, last.longitude, fix.latitude, fix.longitude).meters < 50
        ) return
        lastSavedFix = fix
        prefs.edit().putString(KEY_LAST_FIX, "${fix.latitude},${fix.longitude},${fix.timeMs}").apply()
    }

    private fun restoreSavedPosition() {
        val parts = prefs.getString(KEY_LAST_FIX, null)?.split(',') ?: return
        val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return
        val lon = parts.getOrNull(1)?.toDoubleOrNull() ?: return
        val time = parts.getOrNull(2)?.toLongOrNull() ?: return
        positionGuard.restore(Fix(lat, lon, 0f, time))
        lastSavedFix = positionGuard.trusted
        publishPosition(clearArea = false)
    }

    private fun outgoingLocation(): GeoCell? {
        if (!_state.value.shareLocation) return null
        val fix = _state.value.sendablePosition() ?: return null
        return GeoCell.of(fix.latitude, fix.longitude)
    }

    fun toggleBlackoutByUser() {
        blackoutByMode = false
        toggleBlackout()
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
        rememberOwnSent(frame)
        queueForRescue(frame, own = true)
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
                    relayUrl = RelayLink.url(serverUrl, frame, System.currentTimeMillis()),
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
            if (error == null && frame.message.critical && _state.value.audibleSos) {
                delay(SOUND_GAP_MS)
                _state.update { it.copy(tx = it.tx.copy(sosBeeping = true)) }
                transmitter.play(AudibleSos.samples(config.sampleRate), config.sampleRate)
                _state.update { it.copy(tx = it.tx.copy(sosBeeping = false)) }
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
                announce(signal.frame)
                queueForRescue(signal.frame, own = false)
            }
        }
    }

    /** Keeps the message for the rescue server; uploaded as soon as any network is available. */
    private fun queueForRescue(frame: RescueFrame, own: Boolean) {
        if (frame.message == RescueMessage.ACK) return
        runCatching { outbox.add(RescueReport.of(frame, System.currentTimeMillis(), own)) }
            .onFailure { debugLog { "outbox write failed: $it" } }
        uploader.onQueued()
    }

    /** Speaks a new message once our own sound transmission (if any) has finished. */
    private fun announce(frame: RescueFrame) {
        if (!_state.value.voiceAnnounce || !_state.value.voiceAvailable) return
        val text = spokenText(frame, _state.value.myFix)
        scope.launch {
            while (_state.value.tx.soundActive) delay(300)
            announcer.say(text)
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
        val base = _state.value.myFix
        val lat0 = base?.latitude ?: 37.5665
        val lon0 = base?.longitude ?: 126.9780
        val distance = random.nextDouble(100.0, 900.0)
        val bearing = random.nextDouble(0.0, 2 * PI)
        val dLat = distance * cos(bearing) / 111_320.0
        val dLon = distance * kotlin.math.sin(bearing) / (111_320.0 * cos(Math.toRadians(lat0)))
        return GeoCell.of(lat0 + dLat, lon0 + dLon)
    }

    private companion object {
        const val CIVIL_ASSET = "civil-shelters.json"
        const val SHELTER_RADIUS_KM = 10.0
        const val SHELTER_COUNT = 80
        const val SHELTER_REFRESH_M = 200.0
        const val KEY_LAST_FIX = "last_fix"
        const val KEY_DISASTER = "disaster"
        const val KEY_OWN_SENT = "own_sent"
        const val OWN_SENT_KEEP_MS = 72 * 3_600_000L
        const val ENERGY_POLL_MS = 60_000L
        const val SOUND_REPEATS = 3
        const val SOUND_GAP_MS = 400L
        const val MAX_INBOX = 30
    }
}

/** Sentence read aloud for a received frame, e.g. "가까운 차량에서 응급 환자 2명 신호를 받았어요. 북동쪽 약 300미터." */
internal fun spokenText(frame: RescueFrame, myFix: io.github.offlinemobility.app.location.Fix?): String {
    val title = frame.message.copy.title
    val people = when {
        frame.people == 0 || frame.message.name in NO_PEOPLE -> ""
        frame.people >= 15 -> " 15명 이상"
        else -> " ${frame.people}명"
    }
    val from = if (frame.hop == 0) "가까운 차량에서" else "차량 ${frame.hop}대를 거쳐"
    val where = frame.location?.let { cell ->
        if (myFix == null) return@let null
        val offset = Geo.offset(myFix.latitude, myFix.longitude, cell.latitude, cell.longitude)
        val distance = Geo.distanceWord(offset.meters).replace("km", "킬로미터").replace("m", "미터")
        if (offset.meters < 50) "아주 가까이 있어요." else "${Geo.compassWord(offset.bearingDeg)} $distance."
    }
    return listOfNotNull("$from $title$people 신호를 받았어요.", where).joinToString(" ")
}

private val NO_PEOPLE = setOf("ROAD_BLOCKED", "ROUTE_CLEAR", "SHELTER", "RESCUE_TEAM", "ACK")
