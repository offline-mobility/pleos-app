package io.github.offlinemobility.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.offlinemobility.app.InboxItem
import io.github.offlinemobility.app.InboxKind
import io.github.offlinemobility.app.RelayState
import io.github.offlinemobility.app.UiState
import io.github.offlinemobility.app.VehicleMode
import io.github.offlinemobility.app.core.cabin.WarmLevel
import io.github.offlinemobility.app.core.disaster.DisasterGuide
import io.github.offlinemobility.app.core.disaster.DisasterType
import io.github.offlinemobility.app.core.disaster.WindowAdvice
import io.github.offlinemobility.app.core.light.Lamp
import io.github.offlinemobility.app.core.online.OfficialAlert
import io.github.offlinemobility.app.core.online.ShelterFinder
import io.github.offlinemobility.app.core.light.LampGroup
import io.github.offlinemobility.app.core.light.LightPattern
import io.github.offlinemobility.app.core.light.LightPatterns
import io.github.offlinemobility.app.core.protocol.Geo
import io.github.offlinemobility.app.core.protocol.RescueFrame
import io.github.offlinemobility.app.core.protocol.RescueMessage
import io.github.offlinemobility.app.location.Fix
import io.github.offlinemobility.app.core.safety.GearState
import io.github.offlinemobility.app.vehicle.LampSupport
import kotlinx.coroutines.delay

class ScreenActions(
    val send: (RescueMessage) -> Unit,
    val people: (Int) -> Unit,
    val stop: () -> Unit,
    val dismissResult: () -> Unit,
    val acknowledge: (InboxItem) -> Unit,
    val toggleListen: () -> Unit,
    val toggleSound: () -> Unit,
    val toggleLight: () -> Unit,
    val lampGroup: (LampGroup) -> Unit,
    val toggleBlackout: () -> Unit,
    val toggleRelay: () -> Unit,
    val toggleShareLocation: () -> Unit,
    val selectDisaster: (DisasterType) -> Unit,
    val toggleAudibleSos: () -> Unit,
    val dismissAlert: (String) -> Unit,
    val acceptAlert: (OfficialAlert) -> Unit,
    val toggleVoice: () -> Unit,
    val warmLevel: (WarmLevel) -> Unit,
    val startKeepWarm: () -> Unit,
    val stopKeepWarm: () -> Unit,
    val simulateIncoming: () -> Unit,
    val setGear: (GearState) -> Unit,
    val retryVehicle: () -> Unit,
)

private enum class Overlay { NONE, CONFIRM, MORE, SETTINGS, DISASTER, GUIDE, SHELTERS, KEEP_WARM, RELAY }

@Composable
fun MainScreen(state: UiState, actions: ScreenActions) {
    val c = LocalAppColors.current
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    var pending by remember { mutableStateOf<RescueMessage?>(null) }
    // True when the guide was opened from the situation list, so "이전" can go back to it.
    var guideFromList by remember { mutableStateOf(false) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(15_000)
            value = System.currentTimeMillis()
        }
    }

    val pick: (RescueMessage) -> Unit = {
        pending = it
        overlay = Overlay.CONFIRM
    }
    // The back key closes an open sheet first instead of leaving the app.
    BackHandler(enabled = overlay != Overlay.NONE) {
        overlay = if (overlay == Overlay.GUIDE && guideFromList) Overlay.DISASTER else Overlay.NONE
    }
    val openGuide: () -> Unit = {
        guideFromList = false
        overlay = Overlay.GUIDE
    }

    Box(Modifier.fillMaxSize().background(c.page)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
            Header(
                state,
                onShelters = { overlay = Overlay.SHELTERS },
                onDisaster = { overlay = Overlay.DISASTER },
                onSettings = { overlay = Overlay.SETTINGS },
            )
            Spacer(Modifier.height(Size.gap))
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxWidth > maxHeight) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        SendArea(state, actions, now, pick, onMore = { overlay = Overlay.MORE }, onGuide = openGuide, onKeepWarm = { overlay = Overlay.KEEP_WARM }, onRelay = { overlay = Overlay.RELAY }, Modifier.weight(1.15f).fillMaxHeight())
                        InboxArea(state, actions, now, Modifier.weight(0.85f).fillMaxHeight())
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        SendArea(state, actions, now, pick, onMore = { overlay = Overlay.MORE }, onGuide = openGuide, onKeepWarm = { overlay = Overlay.KEEP_WARM }, onRelay = { overlay = Overlay.RELAY }, Modifier.weight(0.58f).fillMaxWidth())
                        InboxArea(state, actions, now, Modifier.weight(0.42f).fillMaxWidth())
                    }
                }
            }
        }

        when (overlay) {
            Overlay.CONFIRM -> pending?.let { message ->
                ConfirmSheet(
                    state = state,
                    message = message,
                    onPeople = actions.people,
                    onToggleLocation = actions.toggleShareLocation,
                    onCancel = { overlay = Overlay.NONE },
                    onSend = {
                        overlay = Overlay.NONE
                        actions.send(message)
                    },
                )
            }
            Overlay.MORE -> MoreSheet(state.guide.firstScreen, onPick = pick, onClose = { overlay = Overlay.NONE })
            Overlay.DISASTER -> DisasterSheet(
                current = state.disaster,
                onPick = {
                    actions.selectDisaster(it)
                    guideFromList = true
                    overlay = Overlay.GUIDE
                },
                onClose = { overlay = Overlay.NONE },
            )
            Overlay.GUIDE -> GuideSheet(
                state.guide,
                onBack = if (guideFromList) ({ overlay = Overlay.DISASTER }) else null,
                onClose = { overlay = Overlay.NONE },
            )
            Overlay.SHELTERS -> ShelterSheet(state, now, onClose = { overlay = Overlay.NONE })
            Overlay.SETTINGS -> SettingsSheet(state, actions, onClose = { overlay = Overlay.NONE })
            Overlay.KEEP_WARM -> KeepWarmSheet(state, actions, onClose = { overlay = Overlay.NONE })
            Overlay.RELAY -> RelaySheet(state.tx, onClose = { overlay = Overlay.NONE })
            Overlay.NONE -> Unit
        }
    }
}

// ---------------------------------------------------------------- header

@Composable
private fun Header(state: UiState, onShelters: () -> Unit, onDisaster: () -> Unit, onSettings: () -> Unit) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = Size.secondaryButton), verticalAlignment = Alignment.CenterVertically) {
        // Left: what to do, and below it in small type whether sending works. Right: only things to press.
        Column(Modifier.weight(1f)) {
            Text("어떤 상황인지 알려주세요", fontSize = Type.title, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(statusLine(state, c), fontSize = Type.meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(16.dp))
        if (state.online.shelters.isNotEmpty()) {
            AppButton("대피소", onShelters, Kind.SECONDARY, height = Size.secondaryButton, icon = Icons.Filled.Home)
            Spacer(Modifier.width(12.dp))
        }
        AppButton(
            if (state.disaster == DisasterType.GENERAL) "행동요령" else "${state.disaster.label} 행동요령",
            onDisaster,
            Kind.SECONDARY,
            height = Size.secondaryButton,
            icon = state.disaster.icon,
        )
        Spacer(Modifier.width(12.dp))
        AppButton("설정", onSettings, Kind.SECONDARY, height = Size.secondaryButton, icon = Icons.Filled.Settings)
    }
}

/**
 * Whether sending works, then only what is out of the ordinary: practice mode, lost connection,
 * blackout, low energy. A normal connection or a full battery is not news, so it is not shown.
 */
private fun statusLine(state: UiState, c: AppColors): AnnotatedString = buildAnnotatedString {
    fun item(text: String, color: Color, strong: Boolean = true) {
        if (length > 0) withStyle(SpanStyle(color = c.mute)) { append("  ·  ") }
        withStyle(SpanStyle(color = color, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal)) { append(text) }
    }
    item(
        when {
            state.vehicleMode == VehicleMode.CONNECTING -> "차량에 연결하는 중"
            state.gear == GearState.PARK -> "신호를 보낼 수 있어요"
            state.gear == GearState.UNKNOWN -> "차 상태를 확인하고 있어요"
            else -> "차를 세우고 기어를 P로 바꾸면 보낼 수 있어요"
        },
        c.body,
        strong = false,
    )
    if (state.vehicleMode == VehicleMode.SIMULATION) item("연습 모드", c.body, strong = false)
    if (state.gateway.enabled && !state.gateway.online) item("통신 끊김 · 소리와 빛으로 보내요", c.urgent)
    if (state.blackout) item("등화관제 중", c.ink)
    state.endurance?.takeIf { it.low }?.let { e -> item("${if (e.isEv) "배터리" else "연료"} ${e.percent}% 남음", c.urgent) }
}

// ---------------------------------------------------------------- send area

@Composable
private fun SendArea(
    state: UiState,
    actions: ScreenActions,
    now: Long,
    onPick: (RescueMessage) -> Unit,
    onMore: () -> Unit,
    onGuide: () -> Unit,
    onKeepWarm: () -> Unit,
    onRelay: () -> Unit,
    modifier: Modifier,
) {
    val tx = state.tx
    Column(modifier) {
        if (tx.active || tx.finished || tx.stopped) {
            SendingPanel(state, actions, onRelay, Modifier.weight(1f).fillMaxWidth())
        } else {
            val echo = state.inbox.firstOrNull { it.kind == InboxKind.ECHO && now - it.receivedAtMs < 10 * 60_000L }
            if (echo != null) {
                Banner(
                    icon = Icons.Filled.CheckCircle,
                    text = "내 신호를 다른 차량이 이어서 전달했어요 · ${relativeTime(echo.receivedAtMs, now)}",
                    color = LocalAppColors.current.safe,
                    background = LocalAppColors.current.safeSoft,
                )
                Spacer(Modifier.height(Size.gap))
            }
            state.topAlert?.let { alert ->
                AlertBanner(
                    alert,
                    state.disaster,
                    onAccept = {
                        actions.acceptAlert(alert)
                        onGuide()
                    },
                    onDismiss = { actions.dismissAlert(alert.id) },
                )
                Spacer(Modifier.height(Size.gap))
            }
            val endurance = state.endurance
            if (endurance != null && endurance.low && state.lightEnabled) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 88.dp).clip(RoundedCornerShape(Size.radius))
                        .background(LocalAppColors.current.helpSoft).padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "에너지가 얼마 남지 않았어요 · 불빛을 끄고 소리만 쓰면 더 오래 버틸 수 있어요",
                        fontSize = Type.body,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalAppColors.current.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    AppButton("불빛 끄기", actions.toggleLight, Kind.SECONDARY, height = 64.dp)
                }
                Spacer(Modifier.height(Size.gap))
            }
            MessagePicker(state.guide.firstScreen, enabled = state.safety.allowed, onPick = onPick, onMore = onMore, modifier = Modifier.weight(1f))
        }
        if (showKeepWarm(state)) {
            Spacer(Modifier.height(Size.gap))
            KeepWarmBar(state, onKeepWarm)
        }
    }
}

/**
 * 3×3 grid: eight messages, then "다른 상황" as the ninth cell. Every message sends the same way, so
 * none gets a bigger cell. The ninth cell opens a list instead of sending, so it sits on grey.
 */
@Composable
private fun MessagePicker(
    messages: List<RescueMessage>,
    enabled: Boolean,
    onPick: (RescueMessage) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier,
) {
    val c = LocalAppColors.current
    val cells: List<RescueMessage?> = messages + null
    Column(modifier.alpha(if (enabled) 1f else 0.4f), verticalArrangement = Arrangement.spacedBy(Size.gap)) {
        cells.chunked(GRID_COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Size.gap)) {
                row.forEach { message ->
                    val cell = Modifier.weight(1f).fillMaxHeight()
                    if (message != null) {
                        MessageTile(message, enabled, onPick, cell)
                    } else {
                        Column(
                            cell.heightIn(min = Size.tile).background(c.surface).border(1.dp, c.line)
                                .clickable(enabled = enabled, onClick = onMore).padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Filled.Apps, contentDescription = null, tint = c.ink, modifier = Modifier.size(76.dp))
                            Spacer(Modifier.height(18.dp))
                            Text("다른 상황", fontSize = Type.tileTitle, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1)
                        }
                    }
                }
                repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val GRID_COLUMNS = 3

@Composable
private fun MessageTile(message: RescueMessage, enabled: Boolean, onPick: (RescueMessage) -> Unit, modifier: Modifier) {
    val c = LocalAppColors.current
    val copy = message.copy
    Column(
        modifier.heightIn(min = Size.tile).background(c.canvas).border(1.dp, c.line)
            .clickable(enabled = enabled) { onPick(message) }
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(copy.icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(76.dp))
        Spacer(Modifier.height(18.dp))
        Text(copy.title, fontSize = Type.tileTitle, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------- sending

@Composable
private fun SendingPanel(state: UiState, actions: ScreenActions, onRelay: () -> Unit, modifier: Modifier) {
    val c = LocalAppColors.current
    val tx = state.tx
    val message = tx.message ?: RescueMessage.SOS
    val copy = message.copy
    val title = when {
        tx.finished -> "다 보냈어요"
        tx.stopped -> "보내기를 멈췄어요"
        message == RescueMessage.ACK -> "응답을 보내고 있어요"
        message == RescueMessage.SOS -> "도움 요청을 보내고 있어요"
        else -> "‘${copy.title}’ 보내는 중"
    }
    Column(modifier.background(c.canvas).border(1.dp, c.line).padding(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(copy.icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(64.dp))
            Spacer(Modifier.width(20.dp))
            Column {
                Text(title, fontSize = Type.display, fontWeight = FontWeight.ExtraBold, color = c.ink)
                Text(titleWithPeople(message, tx.people), fontSize = Type.body, color = c.body)
            }
        }
        Spacer(Modifier.height(28.dp))

        if (tx.soundTotal > 0) {
            val soundText = when {
                tx.soundFailed -> "소리를 내지 못했어요"
                tx.sosBeeping -> "사람이 알아듣는 SOS 소리를 내는 중"
                tx.soundActive -> "소리로 알리는 중 (${(tx.soundDone + 1).coerceAtMost(tx.soundTotal)}/${tx.soundTotal})"
                tx.soundDone >= tx.soundTotal -> "소리로 ${tx.soundTotal}번 알렸어요"
                else -> "소리 알림을 멈췄어요"
            }
            ProgressRow(Icons.Filled.VolumeUp, soundText, done = !tx.soundActive && tx.soundDone >= tx.soundTotal, active = tx.soundActive)
            Spacer(Modifier.height(12.dp))
        }
        if (tx.lightPlanned) {
            val lamp = lampWord(state)
            val lightText = when {
                tx.lightFailed -> "이 차에서는 불빛을 제어할 수 없어요"
                tx.lightActive -> "$lamp ${patternWord(tx.lightPattern)} 중"
                tx.finished || (!tx.lightActive && !tx.stopped) -> "$lamp ${patternWord(tx.lightPattern)}을 마쳤어요"
                else -> "불빛을 원래대로 돌려놓았어요"
            }
            ProgressRow(
                Icons.Filled.Highlight,
                lightText,
                done = !tx.lightActive && !tx.lightFailed && !tx.stopped,
                active = tx.lightActive,
                lampOn = if (tx.lightActive) tx.lightOn else null,
            )
            if (!tx.lightActive && tx.lightConfirmed == false && !tx.lightFailed && !tx.stopped) {
                Text("차량에서 불이 켜졌는지는 확인되지 않았어요", fontSize = Type.meta, color = c.mute, modifier = Modifier.padding(start = 76.dp, top = 6.dp))
            }
        } else if (state.blackout && message != RescueMessage.ACK) {
            Text("등화관제 중이라 불빛은 쓰지 않았어요", fontSize = Type.meta, color = c.mute)
        }

        Spacer(Modifier.weight(1f))
        val sentToServer = tx.lastFrame?.let { "%02x-%x".format(it.senderId, it.sequence) in state.gateway.lastUploadedKeys } == true
        if (sentToServer) {
            Spacer(Modifier.height(12.dp))
            ProgressRow(Icons.Filled.CheckCircle, "구조 서버에도 보냈어요", done = true, active = false)
        } else if (state.gateway.enabled && !state.gateway.online && message != RescueMessage.ACK) {
            Spacer(Modifier.height(12.dp))
            Text("통신이 끊겨 있어요 · 통신이 돌아오면 구조 서버로 자동으로 보내요", fontSize = Type.meta, color = c.mute)
        }
        if (tx.finished) {
            Spacer(Modifier.height(12.dp))
            Text("근처 차량이 받으면 오른쪽 목록에 표시돼요", fontSize = Type.body, color = c.body)
        }
        Spacer(Modifier.height(Size.gap))
        if (tx.relayUrl != null) {
            AppButton(
                "앱이 없는 사람에게 QR로 전달",
                onRelay,
                Kind.SECONDARY,
                Modifier.fillMaxWidth(),
                height = Size.secondaryButton,
                icon = Icons.Filled.QrCode2,
            )
            Spacer(Modifier.height(Size.gap))
        }
        if (tx.active) {
            AppButton("멈추기", actions.stop, Kind.STRONG_NEUTRAL, Modifier.fillMaxWidth())
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Size.gap)) {
                AppButton("처음으로", actions.dismissResult, Kind.SECONDARY, Modifier.weight(1f))
                if (message != RescueMessage.ACK) {
                    AppButton(
                        "다시 보내기",
                        { actions.send(message) },
                        Kind.TONE,
                        Modifier.weight(1.4f),
                        tone = copy.tone,
                        enabled = state.canTransmit,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressRow(icon: ImageVector, text: String, done: Boolean, active: Boolean, lampOn: Boolean? = null) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 84.dp).background(c.surface).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) c.ink else c.body, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, fontSize = Type.body, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
        when {
            // Mirrors the lamp: filled while it is on.
            lampOn != null -> Box(
                Modifier.size(28.dp).clip(CircleShape)
                    .background(if (lampOn) c.ink else Color.Transparent)
                    .border(2.dp, c.ink, CircleShape),
            )
            done -> Icon(Icons.Filled.Check, contentDescription = "완료", tint = c.ink, modifier = Modifier.size(36.dp))
        }
    }
}

private fun patternWord(pattern: LightPattern?): String = when (pattern) {
    LightPatterns.SOS -> "SOS 깜빡임"
    LightPatterns.DISTRESS_BEACON -> "1분에 6번 깜빡임"
    LightPatterns.REPLY -> "1분에 3번 깜빡임"
    LightPatterns.GUIDE -> "두 번씩 깜빡임"
    else -> "깜빡임"
}

private fun lampWord(state: UiState): String {
    val flash = state.lightPlan.flash
    return when {
        flash.any { it == Lamp.HIGH_BEAM || it == Lamp.LOW_BEAM } -> "전조등"
        flash.isNotEmpty() -> "뒤쪽 불빛"
        else -> "불빛"
    }
}

private fun methodText(state: UiState, message: RescueMessage): String {
    val parts = buildList {
        if (state.useSound) add("소리 3번")
        if (state.lightUsable) add("${lampWord(state)} ${patternWord(LightPatterns.forMessage(message))}")
    }
    return when {
        parts.isNotEmpty() -> parts.joinToString(" + ")
        else -> "보낼 방법이 모두 꺼져 있어요 · 설정에서 켜 주세요"
    }
}

// ---------------------------------------------------------------- inbox

@Composable
private fun InboxArea(state: UiState, actions: ScreenActions, now: Long, modifier: Modifier) {
    val c = LocalAppColors.current
    val items = state.inbox.filter { it.kind == InboxKind.NEW || it.kind == InboxKind.SERVER }
    Column(
        modifier.clip(RoundedCornerShape(Size.radius)).background(c.surface).padding(20.dp),
    ) {
        // Listening is a state, not something people change here: plain words, switched in 설정.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("주변에서 온 신호", fontSize = Type.title, fontWeight = FontWeight.Bold, color = c.ink)
            if (items.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                Text("${items.size}", fontSize = Type.title, fontWeight = FontWeight.Bold, color = c.ink)
            }
            Spacer(Modifier.weight(1f))
            Icon(
                if (state.listening) Icons.Filled.Mic else Icons.Filled.MicOff,
                contentDescription = null,
                tint = if (state.listening) c.mute else c.urgent,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (state.listening) "듣는 중" else "듣기 꺼짐",
                fontSize = Type.meta,
                color = if (state.listening) c.mute else c.urgent,
            )
        }
        state.micMessage?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, fontSize = Type.meta, color = c.help)
        }
        Spacer(Modifier.height(Size.gap))
        if (items.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(if (state.listening) Icons.Filled.Mic else Icons.Filled.MicOff, contentDescription = null, tint = c.mute, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("아직 받은 신호가 없어요", fontSize = Type.title, fontWeight = FontWeight.Bold, color = c.body)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (!state.listening) "듣기가 꺼져 있어요 · 설정에서 켤 수 있어요"
                    else when (state.guide.window) {
                        WindowAdvice.OPEN_A_LITTLE -> "주변 차량의 신호를 듣고 있어요\n창문을 조금 열면 더 멀리서도 들려요"
                        WindowAdvice.KEEP_CLOSED -> "주변 차량의 신호를 듣고 있어요\n지금은 창문을 닫아 두세요 · 들리는 거리는 줄어요"
                        WindowAdvice.NO_ADVICE -> "주변 차량의 신호를 듣고 있어요"
                    },
                    fontSize = Type.meta,
                    color = c.mute,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items, key = { it.id }) { item ->
                    InboxCard(item.copy(forwarded = "%02x-%x".format(item.frame.senderId, item.frame.sequence) in state.gateway.lastUploadedKeys), now, state.myFix, canReply = state.safety.allowed && !state.tx.active) { actions.acknowledge(item) }
                }
            }
        }
    }
}

@Composable
private fun InboxCard(item: InboxItem, now: Long, myFix: Fix?, canReply: Boolean, onReply: () -> Unit) {
    val c = LocalAppColors.current
    val copy = item.frame.message.copy
    val fromServer = item.kind == InboxKind.SERVER
    // Reading order for someone deciding whether to go and help: what and how many, where, then how it got here.
    // The only colour is the red icon of an urgent request, so those stand out in the list.
    Row(
        Modifier.fillMaxWidth().background(c.canvas).border(1.dp, c.line).padding(20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(copy.icon, contentDescription = null, tint = copy.tone.strong(), modifier = Modifier.size(44.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    titleWithPeople(item.frame.message, item.frame.people),
                    fontSize = Type.title,
                    lineHeight = Type.lines(Type.title),
                    fontWeight = FontWeight.Bold,
                    color = c.ink,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(relativeTime(item.receivedAtMs, now), fontSize = Type.meta, color = c.mute, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(6.dp))
            val where = whereText(item.frame, myFix)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Place, contentDescription = null, tint = if (where != null) c.ink else c.mute, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    where ?: "위치는 받지 못했어요",
                    fontSize = Type.body,
                    fontWeight = if (where != null) FontWeight.Bold else FontWeight.Normal,
                    color = if (where != null) c.ink else c.mute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // How it got here. A server item says nothing about the route; only a count of two or more
            // reporting cars adds anything.
            val route = listOfNotNull(
                if (fromServer) "차량 ${item.confirmations}대가 알림".takeIf { item.confirmations >= 2 } else routeText(item.frame.hop),
                "내 차도 이어서 전달했어요".takeIf { item.relayState == RelayState.SENT },
                "구조 서버로 전달했어요".takeIf { item.forwarded && !fromServer },
            )
            if (route.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(route.joinToString(" · ").keepWords(), fontSize = Type.meta, color = c.body)
            }
            if (item.frame.message != RescueMessage.ACK && !fromServer) {
                Spacer(Modifier.height(14.dp))
                if (item.acknowledged) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = c.ink, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("응답했어요", fontSize = Type.meta, fontWeight = FontWeight.SemiBold, color = c.ink)
                    }
                } else {
                    AppButton("받았다고 알리기", onReply, Kind.SECONDARY, Modifier.fillMaxWidth(), height = 64.dp, enabled = canReply)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- sheets

@Composable
internal fun SheetScrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
internal fun Sheet(
    width: Dp = 820.dp,
    modifier: Modifier = Modifier,
    padding: Dp = 32.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalAppColors.current
    Column(
        Modifier.widthIn(max = width).fillMaxWidth(0.92f).then(modifier).background(c.canvas)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(padding),
        content = content,
    )
}

@Composable
private fun ConfirmSheet(
    state: UiState,
    message: RescueMessage,
    onPeople: (Int) -> Unit,
    onToggleLocation: () -> Unit,
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
    val c = LocalAppColors.current
    val copy = message.copy
    // Wide rows with room above and below: the last check before sending should not feel cramped.
    val row = 120.dp
    val label = 300.dp
    SheetScrim(onCancel) {
        Sheet(width = 1200.dp, padding = 48.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(copy.icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(72.dp))
                Spacer(Modifier.width(24.dp))
                Text(copy.title, fontSize = Type.hero, fontWeight = FontWeight.ExtraBold, color = c.ink)
            }
            Spacer(Modifier.height(36.dp))
            Divider()
            if (message.asksPeople) {
                InfoRow("함께 있는 사람", minHeight = row, labelWidth = label) {
                    Stepper(state.people, onPeople)
                }
                Divider()
            }
            InfoRow("보내는 방법", minHeight = row, labelWidth = label) {
                Text(methodText(state, message), fontSize = Type.body, fontWeight = FontWeight.SemiBold, color = c.ink)
            }
            Divider()
            InfoRow("내 위치", minHeight = row, labelWidth = label) {
                Text(
                    locationText(state),
                    fontSize = Type.body,
                    fontWeight = FontWeight.SemiBold,
                    color = if (state.shareLocation && state.myFix != null) c.ink else c.body,
                    modifier = Modifier.weight(1f),
                )
                if (state.locationPermission) {
                    AppSwitch(state.shareLocation, onToggleLocation)
                }
            }
            Divider()
            Spacer(Modifier.height(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Size.gap)) {
                AppButton("취소", onCancel, Kind.SECONDARY, Modifier.weight(1f))
                AppButton("보내기", onSend, Kind.TONE, Modifier.weight(1.6f), tone = copy.tone, enabled = state.canTransmit)
            }
        }
    }
}

/** One outlined box split into − | value | +, like the Pleos settings stepper. */
@Composable
internal fun Stepper(value: Int, onChange: (Int) -> Unit) {
    val c = LocalAppColors.current
    Row(Modifier.height(Size.secondaryButton).border(1.dp, c.line), verticalAlignment = Alignment.CenterVertically) {
        StepKey("−", enabled = value > 0) { onChange(-1) }
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
        Text(
            peopleText(value) ?: "모름",
            fontSize = Type.title,
            fontWeight = FontWeight.Bold,
            color = c.ink,
            modifier = Modifier.widthIn(min = 170.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
        StepKey("+", enabled = value < 15) { onChange(1) }
    }
}

@Composable
private fun StepKey(text: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        Modifier.size(Size.secondaryButton).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = Type.display, color = if (enabled) c.ink else c.line) }
}

/**
 * Pleos toggle: black track when on, grey when off, a full-size white thumb either way and no outline.
 * An empty [Switch] thumbContent keeps the thumb full size when off (Material shrinks it otherwise).
 */
@Composable
private fun AppSwitch(checked: Boolean, onToggle: () -> Unit, enabled: Boolean = true) {
    val c = LocalAppColors.current
    Switch(
        checked = checked,
        onCheckedChange = { onToggle() },
        enabled = enabled,
        thumbContent = {},
        modifier = Modifier.scale(1.4f).padding(end = 12.dp),
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.page,
            checkedTrackColor = c.ink,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = c.page,
            uncheckedTrackColor = c.mute,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
private fun MoreSheet(firstScreen: List<RescueMessage>, onPick: (RescueMessage) -> Unit, onClose: () -> Unit) {
    val c = LocalAppColors.current
    SheetScrim(onClose) {
        Sheet(width = 1400.dp) {
            Text("다른 상황", fontSize = Type.display, fontWeight = FontWeight.ExtraBold, color = c.ink)
            Spacer(Modifier.height(12.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                moreGroups(firstScreen).forEach { (group, messages) ->
                    Text(group, fontSize = Type.meta, fontWeight = FontWeight.SemiBold, color = c.mute)
                    Spacer(Modifier.height(8.dp))
                    messages.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { CompactMessageTile(it, onPick, Modifier.weight(1f)) }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            AppButton("닫기", onClose, Kind.SECONDARY, Modifier.fillMaxWidth())
        }
    }
}

/** Icon beside the name: the list fits on one screen without scrolling. */
@Composable
private fun CompactMessageTile(message: RescueMessage, onPick: (RescueMessage) -> Unit, modifier: Modifier) {
    val c = LocalAppColors.current
    val copy = message.copy
    Row(
        modifier.heightIn(min = 96.dp).background(c.canvas).border(1.dp, c.line)
            .clickable { onPick(message) }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(copy.icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(44.dp))
        Spacer(Modifier.width(16.dp))
        Text(copy.title, fontSize = Type.button, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private enum class SettingsTab(val label: String, val icon: ImageVector) {
    SEND("보내는 방법", Icons.Filled.VolumeUp),
    RECEIVE("받은 신호", Icons.Filled.Mic),
    INFO("차량·서버", Icons.Filled.Info),
}

/**
 * Laid out like the Pleos settings app: sections on the left (the chosen one filled black), their
 * switches on the right. One fixed size for every section, well short of the full screen height.
 */
@Composable
private fun SettingsSheet(state: UiState, actions: ScreenActions, onClose: () -> Unit) {
    val c = LocalAppColors.current
    var tab by remember { mutableStateOf(SettingsTab.SEND) }
    SheetScrim(onClose) {
        Sheet(width = 1300.dp, modifier = Modifier.fillMaxHeight(0.72f)) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(300.dp).fillMaxHeight()) {
                    Text("설정", fontSize = Type.display, fontWeight = FontWeight.ExtraBold, color = c.ink)
                    Spacer(Modifier.height(24.dp))
                    SettingsTab.entries.forEach { t ->
                        val on = t == tab
                        Row(
                            Modifier.fillMaxWidth().height(80.dp).background(if (on) c.ink else c.canvas)
                                .clickable { tab = t }.padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(t.icon, contentDescription = null, tint = if (on) c.page else c.ink, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(t.label, fontSize = Type.body, fontWeight = FontWeight.SemiBold, color = if (on) c.page else c.ink)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    AppButton("닫기", onClose, Kind.SECONDARY, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.width(32.dp))
                Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
                Spacer(Modifier.width(32.dp))
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    when (tab) {
                        SettingsTab.SEND -> {
                            SwitchRow("소리로 알리기", "차 스피커로 신호음을 보내요. 근처 차량이 글자로 받아 봐요.", state.useSound, actions.toggleSound)
                            SwitchRow(
                                "사람이 듣는 SOS 소리",
                                "위급한 메시지 뒤에 누구나 알아듣는 SOS 소리를 붙여요.",
                                state.audibleSos,
                                actions.toggleAudibleSos,
                                enabled = state.useSound,
                            )
                            SwitchRow("불빛으로 알리기", "전조등을 국제 조난 신호 방식으로 깜빡여요.", state.useLight, actions.toggleLight, enabled = !state.blackout)
                            if (state.useLight && !state.blackout) {
                                InfoRow("깜빡일 위치") {
                                    Segmented(
                                        listOf("앞" to LampGroup.FRONT, "뒤" to LampGroup.REAR, "앞뒤" to LampGroup.FRONT_AND_REAR),
                                        state.lampGroup,
                                        actions.lampGroup,
                                    )
                                }
                            }
                            SwitchRow("등화관제", "불빛 없이 소리로만 보내요. 공습경보 때 써요.", state.blackout, actions.toggleBlackout)
                            SwitchRow(
                                "내 위치 함께 보내기",
                                if (state.locationPermission) "GPS 위치(약 40m 범위)를 같이 보내요. 보낼 때만 나가요."
                                else "기기 설정에서 위치 권한을 허용하면 쓸 수 있어요.",
                                state.shareLocation && state.locationPermission,
                                actions.toggleShareLocation,
                                enabled = state.locationPermission,
                            )
                        }
                        SettingsTab.RECEIVE -> {
                            SwitchRow("주변 신호 듣기", "마이크로 주변 차량의 신호음을 들어요.", state.listening, actions.toggleListen)
                            SwitchRow("다음 차량으로 이어서 전달", "받은 신호를 내 차에서도 다시 보내 더 멀리 전해요.", state.autoRelay, actions.toggleRelay)
                            SwitchRow(
                                "음성으로 알려 주기",
                                if (state.voiceAvailable) "받은 신호를 소리 내어 읽어 줘요." else "이 기기에는 한국어 음성 엔진이 없어요.",
                                state.voiceAnnounce && state.voiceAvailable,
                                actions.toggleVoice,
                                enabled = state.voiceAvailable,
                            )
                            Spacer(Modifier.height(Size.gap))
                            AppButton("다른 차 신호 받아 보기 (시연)", actions.simulateIncoming, Kind.SECONDARY, Modifier.fillMaxWidth())
                            if (state.vehicleMode == VehicleMode.SIMULATION) {
                                Spacer(Modifier.height(Size.gap))
                                Row(horizontalArrangement = Arrangement.spacedBy(Size.gap)) {
                                    AppButton("기어 P", { actions.setGear(GearState.PARK) }, Kind.SECONDARY, Modifier.weight(1f))
                                    AppButton("기어 D", { actions.setGear(GearState.DRIVE) }, Kind.SECONDARY, Modifier.weight(1f))
                                }
                            }
                        }
                        SettingsTab.INFO -> {
                            val g = state.gateway
                            InfoRow("구조 서버") {
                                Text(
                                    when {
                                        !g.enabled -> "정해지지 않아 꺼져 있어요 · 구조 요청 ${g.pending}건을 기기에 암호화해 7일간 보관해요"
                                        g.online && g.pending > 0 -> "연결됨 · 구조 요청 ${g.pending}건을 보내는 중이에요"
                                        g.online -> "연결됨 · 보낼 구조 요청이 없어요"
                                        else -> "통신이 돌아오면 구조 요청 ${g.pending}건을 보내요"
                                    }.keepWords(),
                                    fontSize = Type.body,
                                    lineHeight = Type.lines(Type.body),
                                    color = c.ink,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            InfoRow("차량 연결") {
                                Text(
                                    when (state.vehicleMode) {
                                        VehicleMode.PLEOS -> "연결됨"
                                        VehicleMode.SIMULATION -> "연습 모드 (차량 서비스 없음)"
                                        VehicleMode.CONNECTING -> "연결 중"
                                    },
                                    fontSize = Type.body,
                                    color = c.ink,
                                )
                            }
                            InfoRow("제어할 수 있는 불빛") {
                                val writable = state.lampSupport.filterValues { it == LampSupport.WRITABLE }.keys
                                Text(if (writable.isEmpty()) "없음" else writable.joinToString { it.label }, fontSize = Type.body, color = c.ink)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = Type.meta,
        fontWeight = FontWeight.SemiBold,
        color = LocalAppColors.current.mute,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, onToggle: () -> Unit, enabled: Boolean = true) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 96.dp).alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onToggle).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = Type.body, fontWeight = FontWeight.SemiBold, color = c.ink)
            Text(detail.keepWords(), fontSize = Type.meta, color = c.mute)
        }
        Spacer(Modifier.width(20.dp))
        AppSwitch(checked, onToggle, enabled)
    }
}

@Composable
internal fun InfoRow(
    label: String,
    minHeight: Dp = 96.dp,
    labelWidth: Dp = 240.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = minHeight).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = Type.body, color = c.body, modifier = Modifier.width(labelWidth))
        content()
    }
}

/** Options in one outlined box, the chosen one filled black, as in the Pleos settings. */
@Composable
private fun <T> Segmented(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    val c = LocalAppColors.current
    Row(Modifier.height(64.dp).border(1.dp, c.line)) {
        options.forEachIndexed { i, (label, value) ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
            val on = value == selected
            Box(
                Modifier.widthIn(min = 120.dp).fillMaxHeight().background(if (on) c.ink else c.canvas)
                    .clickable { onSelect(value) }.padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = Type.body, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) c.page else c.ink)
            }
        }
    }
}

@Composable
internal fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalAppColors.current.line))
}

// ---------------------------------------------------------------- small parts

@Composable
private fun Banner(icon: ImageVector, text: String, color: Color, background: Color) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 76.dp).clip(RoundedCornerShape(Size.radius)).background(background)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(14.dp))
        Text(text.keepWords(), fontSize = Type.body, lineHeight = Type.lines(Type.body), fontWeight = FontWeight.SemiBold, color = LocalAppColors.current.ink)
    }
}

enum class Kind { TONE, SECONDARY, STRONG_NEUTRAL }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    kind: Kind,
    modifier: Modifier = Modifier,
    height: Dp = Size.primaryButton,
    tone: Tone = Tone.NEUTRAL,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    fontSize: TextUnit = Type.button,
) {
    val c = LocalAppColors.current
    // Pleos buttons: filled black for the main action, outlined for the rest, whatever the message
    // ([tone] is kept for call sites but no longer colours a button). Disabled is shown through the
    // colours, not an alpha layer: grey fill and grey label, as the Pleos settings show it.
    val (bg, fg, border) = when {
        kind == Kind.SECONDARY -> Triple(c.canvas, if (enabled) c.ink else c.mute, BorderStroke(1.dp, c.line))
        enabled -> Triple(c.ink, c.page, null)
        else -> Triple(c.surface, c.mute, BorderStroke(1.dp, c.line))
    }
    Row(
        modifier.heightIn(min = height).background(bg)
            .let { if (border != null) it.border(border) else it }
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontSize = fontSize, fontWeight = FontWeight.Bold, color = fg, maxLines = 1)
    }
}

private fun locationText(state: UiState): String = when {
    !state.locationPermission -> "위치 권한이 없어 위치 없이 보내요"
    !state.shareLocation -> "보내지 않아요"
    state.myFix == null -> "위치를 찾는 중 · 못 찾으면 위치 없이 보내요"
    else -> "함께 보내요 (약 40m 범위)"
}

/** "북동쪽 약 300m" from our own fix to the sender, or a hint when one side is unknown. */
private fun whereText(frame: RescueFrame, myFix: Fix?): String? {
    val cell = frame.location ?: return null
    if (myFix == null) return "위치 받음"
    val offset = Geo.offset(myFix.latitude, myFix.longitude, cell.latitude, cell.longitude)
    return if (offset.meters < 50) "아주 가까이" else "${Geo.compassWord(offset.bearingDeg)} ${Geo.distanceWord(offset.meters)}"
}

// ---------------------------------------------------------------- disaster

/** The situation list and the guide share one fixed frame, so moving between them or between situations never resizes it. */
@Composable
private fun GuideFrame(content: @Composable ColumnScope.() -> Unit) {
    Sheet(width = 1500.dp, modifier = Modifier.fillMaxHeight(0.9f), content = content)
}

@Composable
private fun DisasterSheet(current: DisasterType, onPick: (DisasterType) -> Unit, onClose: () -> Unit) {
    val c = LocalAppColors.current
    SheetScrim(onClose) {
        GuideFrame {
            Text("어떤 재난인가요?", fontSize = Type.display, fontWeight = FontWeight.ExtraBold, color = c.ink)
            Text("고르면 행동요령을 보여 주고, 첫 화면 메시지도 그 재난에 맞게 바꿔요", fontSize = Type.body, color = c.body)
            Spacer(Modifier.height(24.dp))
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Size.gap)) {
                DisasterType.entries.chunked(SITUATION_COLUMNS).forEach { row ->
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Size.gap)) {
                        row.forEach { type ->
                            val selected = type == current
                            Column(
                                Modifier.weight(1f).fillMaxHeight()
                                    .background(if (selected) c.ink else c.canvas)
                                    .border(1.dp, if (selected) c.ink else c.line)
                                    .clickable { onPick(type) }.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(type.icon, contentDescription = null, tint = if (selected) c.page else c.ink, modifier = Modifier.size(56.dp))
                                Spacer(Modifier.height(12.dp))
                                Text(type.label, fontSize = Type.body, fontWeight = FontWeight.Bold, color = if (selected) c.page else c.ink, maxLines = 1)
                            }
                        }
                        repeat(SITUATION_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            AppButton("닫기", onClose, Kind.SECONDARY, Modifier.fillMaxWidth())
        }
    }
}

private const val SITUATION_COLUMNS = 4

@Composable
private fun GuideSheet(guide: DisasterGuide, onBack: (() -> Unit)?, onClose: () -> Unit) {
    val c = LocalAppColors.current
    SheetScrim(onClose) {
        GuideFrame {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(guide.type.icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(52.dp))
                Spacer(Modifier.width(16.dp))
                Text("${guide.type.label} 행동요령", fontSize = Type.display, fontWeight = FontWeight.ExtraBold, color = c.ink)
            }
            Spacer(Modifier.height(8.dp))
            // Wide screens: what to do on the left, what to avoid on the right (one column when there is nothing
            // to put on the right). Each side scrolls on its own if it ever overflows.
            val hasRight = guide.avoid.isNotEmpty() || guide.carRisk != null
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                if (maxWidth >= 1000.dp && hasRight) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { GuideDo(guide) }
                        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { GuideDont(guide) }
                    }
                } else {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        GuideDo(guide)
                        GuideDont(guide)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("출처: ${guide.sources.map { it.org }.distinct().joinToString(", ")}", fontSize = Type.meta, color = c.mute)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Size.gap)) {
                if (onBack != null) AppButton("이전", onBack, Kind.SECONDARY, Modifier.weight(1f))
                AppButton("닫기", onClose, Kind.SECONDARY, Modifier.weight(1f))
            }
        }
    }
}

// The guide is read under stress, often by older people, and the frame has room: larger type than other sheets.
@Composable
private fun GuideDo(guide: DisasterGuide) {
    val c = LocalAppColors.current
    GuideSection("이렇게 하세요")
    guide.actions.forEachIndexed { i, step -> GuideLine("${i + 1}", step.text, c.ink, large = true) }
}

@Composable
private fun GuideDont(guide: DisasterGuide) {
    val c = LocalAppColors.current
    if (guide.avoid.isNotEmpty()) {
        GuideSection("하지 마세요")
        guide.avoid.forEach { GuideLine("✕", it.text, c.urgent, large = true) }
    }
    guide.carRisk?.let {
        GuideSection("차에서 조심할 점")
        GuideLine("!", it, c.help, large = true)
    }
}

@Composable
private fun GuideSection(text: String) {
    Text(
        text,
        fontSize = Type.body,
        fontWeight = FontWeight.SemiBold,
        color = LocalAppColors.current.mute,
        modifier = Modifier.padding(top = 24.dp, bottom = 6.dp),
    )
}

@Composable
internal fun GuideLine(mark: String, text: String, markColor: Color, large: Boolean = false) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = if (large) 10.dp else 8.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(if (large) 36.dp else 30.dp), contentAlignment = Alignment.TopCenter) {
            Text(mark, fontSize = if (large) Type.title else Type.body, fontWeight = FontWeight.Bold, color = markColor)
        }
        Spacer(Modifier.width(if (large) 18.dp else 14.dp))
        val size = if (large) Type.title else Type.body
        Text(
            text.keepWords(),
            fontSize = size,
            lineHeight = Type.lines(size),
            color = c.ink,
            modifier = Modifier.padding(top = if (large) 2.dp else 4.dp),
        )
    }
}

// ---------------------------------------------------------------- online

@Composable
private fun AlertBanner(alert: OfficialAlert, current: DisasterType, onAccept: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    val suggest = alert.type?.takeIf { it != current }
    Column(
        Modifier.fillMaxWidth().background(c.canvas).border(1.dp, c.line).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Campaign, contentDescription = null, tint = c.urgent, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Text("재난문자", fontSize = Type.body, fontWeight = FontWeight.Bold, color = c.urgent)
        }
        Spacer(Modifier.height(8.dp))
        Text(alert.text.keepWords(), fontSize = Type.body, lineHeight = Type.lines(Type.body), color = c.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Size.gap)) {
            AppButton("닫기", onDismiss, Kind.SECONDARY, Modifier.weight(1f), height = 64.dp)
            if (suggest != null) {
                AppButton("‘${suggest.label}’ 행동요령 보기", onAccept, Kind.TONE, Modifier.weight(2f), height = 64.dp, tone = Tone.URGENT)
            }
        }
    }
}

@Composable
private fun ShelterSheet(state: UiState, now: Long, onClose: () -> Unit) {
    val c = LocalAppColors.current
    val fix = state.myFix
    SheetScrim(onClose) {
        Sheet(width = 1000.dp) {
            Text("가까운 대피소", fontSize = Type.display, fontWeight = FontWeight.ExtraBold, color = c.ink)
            val fetched = state.online.sheltersFetchedAtMs
            Text(
                listOfNotNull(
                    fetched?.let { "${relativeTime(it, now)}에 받아 둔 정보예요" },
                    "통신이 끊겨도 볼 수 있어요",
                    "견본 데이터".takeIf { state.online.sample },
                ).joinToString(" · "),
                fontSize = Type.meta,
                color = c.mute,
            )
            Spacer(Modifier.height(16.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                if (fix == null) {
                    Text("내 위치를 아직 몰라 거리 대신 목록만 보여 드려요", fontSize = Type.body, color = c.body)
                    Spacer(Modifier.height(8.dp))
                }
                val preferKind = ShelterFinder.preferredKind(state.disaster)
                val rows = if (fix != null) ShelterFinder.nearest(state.online.shelters, fix.latitude, fix.longitude, 6, preferKind)
                else state.online.shelters.sortedBy { preferKind != null && !it.kind.contains(preferKind) }.take(6).map { it to null }
                if (preferKind != null && rows.any { it.first.kind.contains(preferKind) }) {
                    Text("‘${state.disaster.label}’ 상황에 맞는 곳을 먼저 보여 드려요", fontSize = Type.body, color = c.body)
                    Spacer(Modifier.height(8.dp))
                }
                rows.forEach { (shelter, offset) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Home, contentDescription = null, tint = c.ink, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(shelter.name, fontSize = Type.title, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(shelter.kind.ifEmpty { null }, shelter.capacity?.let { "약 ${"%,d".format(it)}명" }, shelter.address.ifEmpty { null })
                                    .joinToString(" · "),
                                fontSize = Type.meta,
                                color = c.body,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (offset != null) {
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "${Geo.compassWord(offset.bearingDeg)} ${Geo.distanceWord(offset.meters)}",
                                fontSize = Type.body,
                                fontWeight = FontWeight.Bold,
                                color = c.safe,
                            )
                        }
                    }
                    Divider()
                }
            }
            Spacer(Modifier.height(20.dp))
            AppButton("닫기", onClose, Kind.SECONDARY, Modifier.fillMaxWidth())
        }
    }
}
