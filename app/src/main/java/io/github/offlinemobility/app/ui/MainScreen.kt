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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.offlinemobility.app.InboxItem
import io.github.offlinemobility.app.InboxKind
import io.github.offlinemobility.app.RelayState
import io.github.offlinemobility.app.UiState
import io.github.offlinemobility.app.VehicleMode
import io.github.offlinemobility.app.core.disaster.DisasterGuide
import io.github.offlinemobility.app.core.disaster.DisasterType
import io.github.offlinemobility.app.core.disaster.WindowAdvice
import io.github.offlinemobility.app.core.energy.Endurance
import io.github.offlinemobility.app.core.light.Lamp
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
    val toggleVoice: () -> Unit,
    val simulateIncoming: () -> Unit,
    val setGear: (GearState) -> Unit,
    val retryVehicle: () -> Unit,
)

private enum class Overlay { NONE, CONFIRM, MORE, SETTINGS, DISASTER, GUIDE }

@Composable
fun MainScreen(state: UiState, actions: ScreenActions) {
    val c = LocalAppColors.current
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    var pending by remember { mutableStateOf<RescueMessage?>(null) }
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
    BackHandler(enabled = overlay != Overlay.NONE) { overlay = Overlay.NONE }

    Box(Modifier.fillMaxSize().background(c.page)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
            Header(state, onDisaster = { overlay = Overlay.DISASTER }, onSettings = { overlay = Overlay.SETTINGS })
            Spacer(Modifier.height(Size.gap))
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxWidth > maxHeight) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        SendArea(state, actions, now, pick, onMore = { overlay = Overlay.MORE }, onGuide = { overlay = Overlay.GUIDE }, Modifier.weight(1.15f).fillMaxHeight())
                        InboxArea(state, actions, now, Modifier.weight(0.85f).fillMaxHeight())
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        SendArea(state, actions, now, pick, onMore = { overlay = Overlay.MORE }, onGuide = { overlay = Overlay.GUIDE }, Modifier.weight(0.58f).fillMaxWidth())
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
            Overlay.MORE -> MoreSheet(state.guide.primary, onPick = pick, onClose = { overlay = Overlay.NONE })
            Overlay.DISASTER -> DisasterSheet(
                current = state.disaster,
                onPick = {
                    actions.selectDisaster(it)
                    overlay = if (it == DisasterType.GENERAL) Overlay.NONE else Overlay.GUIDE
                },
                onClose = { overlay = Overlay.NONE },
            )
            Overlay.GUIDE -> GuideSheet(state.guide, onClose = { overlay = Overlay.NONE })
            Overlay.SETTINGS -> SettingsSheet(state, actions, onClose = { overlay = Overlay.NONE })
            Overlay.NONE -> Unit
        }
    }
}

// ---------------------------------------------------------------- header

@Composable
private fun Header(state: UiState, onDisaster: () -> Unit, onSettings: () -> Unit) {
    val c = LocalAppColors.current
    val (dot, text) = when {
        state.vehicleMode == VehicleMode.CONNECTING -> c.mute to "차량에 연결하는 중"
        state.gear == GearState.PARK -> c.safe to "정차 중 · 신호를 보낼 수 있어요"
        state.gear == GearState.UNKNOWN -> c.help to "차 상태를 확인하고 있어요"
        else -> c.help to "주행 중 · 차를 세우고 P단에서 보낼 수 있어요"
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(text, fontSize = Type.title, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            state.endurance?.let { e ->
                Text(
                    "${if (e.isEv) "배터리" else "연료"} ${e.percent}% · 지금처럼 쓰면 ${Endurance.hoursWord(e.hours)} 버틸 수 있어요 (추정, 냉난방 제외)",
                    fontSize = Type.meta,
                    color = if (e.low) c.help else c.mute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (state.vehicleMode == VehicleMode.SIMULATION) {
            Spacer(Modifier.width(14.dp))
            Tag("연습 모드", c.body, c.surface)
        }
        Spacer(Modifier.weight(1f))
        if (state.blackout) {
            Tag("등화관제 중", c.help, c.helpSoft)
            Spacer(Modifier.width(12.dp))
        }
        AppButton(
            "상황: ${state.disaster.label}",
            onDisaster,
            Kind.SECONDARY,
            height = Size.secondaryButton,
            icon = state.disaster.icon,
        )
        Spacer(Modifier.width(12.dp))
        AppButton("설정", onSettings, Kind.SECONDARY, height = Size.secondaryButton, icon = Icons.Filled.Settings)
    }
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
    modifier: Modifier,
) {
    val tx = state.tx
    Column(modifier) {
        if (tx.active || tx.finished || tx.stopped) {
            SendingPanel(state, actions)
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
            if (state.disaster != DisasterType.GENERAL) {
                GuideBanner(state.guide, onGuide)
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
            MessagePicker(state.guide.primary, enabled = state.safety.allowed, onPick = onPick, onMore = onMore, modifier = Modifier.weight(1f))
            if (!state.safety.allowed) {
                Spacer(Modifier.height(Size.gap))
                Banner(
                    icon = Icons.Filled.Info,
                    text = if (state.gear == GearState.UNKNOWN) "차 상태를 확인한 뒤 보낼 수 있어요"
                    else "차를 세우고 기어를 P로 바꾸면 보낼 수 있어요",
                    color = LocalAppColors.current.help,
                    background = LocalAppColors.current.helpSoft,
                )
            }
        }
    }
}

@Composable
private fun MessagePicker(
    primary: List<RescueMessage>,
    enabled: Boolean,
    onPick: (RescueMessage) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier,
) {
    val c = LocalAppColors.current
    Column(modifier.alpha(if (enabled) 1f else 0.4f), verticalArrangement = Arrangement.spacedBy(Size.gap)) {
        val sos = RescueMessage.SOS.copy
        Row(
            Modifier.fillMaxWidth().weight(1.25f).heightIn(min = Size.sosTile)
                .clip(RoundedCornerShape(Size.radius)).background(c.urgent)
                .clickable(enabled = enabled) { onPick(RescueMessage.SOS) }
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(sos.icon, contentDescription = null, tint = c.onStrong, modifier = Modifier.size(104.dp))
            Spacer(Modifier.width(28.dp))
            Column {
                Text(sos.title, fontSize = Type.hero, fontWeight = FontWeight.ExtraBold, color = c.onStrong)
                Text(sos.detail, fontSize = Type.title, color = c.onStrong.copy(alpha = 0.92f))
            }
        }
        val rows = primary.chunked(3)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Size.gap)) {
            rows[0].forEach { MessageTile(it, enabled, onPick, Modifier.weight(1f).fillMaxHeight()) }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Size.gap)) {
            rows[1].forEach { MessageTile(it, enabled, onPick, Modifier.weight(1f).fillMaxHeight()) }
            Tile(
                icon = Icons.Filled.Apps,
                iconTint = c.body,
                title = "다른 메시지",
                detail = "${moreGroups(primary).sumOf { it.second.size }}가지 더 보기",
                enabled = enabled,
                onClick = onMore,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun MessageTile(message: RescueMessage, enabled: Boolean, onPick: (RescueMessage) -> Unit, modifier: Modifier) {
    val copy = message.copy
    Tile(copy.icon, copy.tone.strong(), copy.title, copy.detail, enabled, { onPick(message) }, modifier)
}

@Composable
private fun Tile(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    detail: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val c = LocalAppColors.current
    Column(
        modifier.heightIn(min = Size.tile).clip(RoundedCornerShape(Size.radius)).background(c.canvas)
            .border(1.dp, c.line, RoundedCornerShape(Size.radius))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(60.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize = Type.tileTitle, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(detail, fontSize = Type.meta, color = c.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------- sending

@Composable
private fun SendingPanel(state: UiState, actions: ScreenActions) {
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
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(Size.radius)).background(c.canvas)
            .border(1.dp, c.line, RoundedCornerShape(Size.radius)).padding(28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(copy.tone.soft()), contentAlignment = Alignment.Center) {
                Icon(copy.icon, contentDescription = null, tint = copy.tone.strong(), modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text(title, fontSize = Type.display, fontWeight = FontWeight.ExtraBold, color = c.ink)
                Text(
                    listOfNotNull(copy.title, peopleText(tx.people).takeIf { message.asksPeople }).joinToString(" · "),
                    fontSize = Type.body,
                    color = c.body,
                )
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
        if (tx.finished) {
            Text("근처 차량이 받으면 오른쪽 목록에 표시돼요", fontSize = Type.body, color = c.body)
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
        Modifier.fillMaxWidth().heightIn(min = 84.dp).clip(RoundedCornerShape(Size.radius)).background(c.surface)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) c.accent else c.body, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, fontSize = Type.body, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
        when {
            lampOn != null -> Box(
                Modifier.size(28.dp).clip(CircleShape)
                    .background(if (lampOn) c.help else Color.Transparent)
                    .border(2.dp, c.help, CircleShape),
            )
            done -> Icon(Icons.Filled.Check, contentDescription = "완료", tint = c.safe, modifier = Modifier.size(36.dp))
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
    val items = state.inbox.filter { it.kind == InboxKind.NEW }
    Column(
        modifier.clip(RoundedCornerShape(Size.radius)).background(c.surface).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("주변에서 온 신호", fontSize = Type.title, fontWeight = FontWeight.Bold, color = c.ink)
            if (items.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                Text("${items.size}", fontSize = Type.title, fontWeight = FontWeight.Bold, color = c.accent)
            }
            Spacer(Modifier.weight(1f))
            ListenToggle(state.listening, actions.toggleListen)
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
                    if (!state.listening) "듣기가 꺼져 있어요 · 위의 버튼을 눌러 켜 주세요"
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
                    InboxCard(item, now, state.myFix, canReply = state.safety.allowed && !state.tx.active) { actions.acknowledge(item) }
                }
            }
        }
    }
}

@Composable
private fun ListenToggle(listening: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier.heightIn(min = 60.dp).clip(RoundedCornerShape(30.dp))
            .background(if (listening) c.safeSoft else c.canvas)
            .border(1.dp, if (listening) c.safe else c.line, RoundedCornerShape(30.dp))
            .clickable(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (listening) Icons.Filled.Mic else Icons.Filled.MicOff, contentDescription = null, tint = if (listening) c.safe else c.mute, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (listening) "듣는 중" else "듣기 꺼짐", fontSize = Type.meta, fontWeight = FontWeight.SemiBold, color = if (listening) c.safe else c.body)
    }
}

@Composable
private fun InboxCard(item: InboxItem, now: Long, myFix: Fix?, canReply: Boolean, onReply: () -> Unit) {
    val c = LocalAppColors.current
    val copy = item.frame.message.copy
    val urgent = copy.tone == Tone.URGENT
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Size.radius))
            .background(if (urgent) c.urgentSoft else c.canvas)
            .border(1.dp, if (urgent) c.urgent.copy(alpha = 0.35f) else c.line, RoundedCornerShape(Size.radius))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(60.dp).clip(CircleShape).background(if (urgent) c.canvas else copy.tone.soft()), contentAlignment = Alignment.Center) {
                Icon(copy.icon, contentDescription = null, tint = copy.tone.strong(), modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(copy.title, peopleText(item.frame.people).takeIf { item.frame.message.asksPeople }).joinToString(" · "),
                    fontSize = Type.title,
                    fontWeight = FontWeight.Bold,
                    color = c.ink,
                )
                Text(
                    listOfNotNull(routeText(item.frame.hop), whereText(item.frame, myFix)).joinToString(" · "),
                    fontSize = Type.body,
                    color = c.body,
                )
                if (item.relayState == RelayState.SENT) {
                    Text("내 차도 이어서 전달했어요", fontSize = Type.meta, color = c.mute)
                }
            }
            Text(relativeTime(item.receivedAtMs, now), fontSize = Type.meta, color = c.mute)
        }
        if (item.frame.message != RescueMessage.ACK) {
            Spacer(Modifier.height(14.dp))
            if (item.acknowledged) {
                Row(Modifier.padding(start = 76.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = c.safe, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("응답했어요", fontSize = Type.meta, fontWeight = FontWeight.SemiBold, color = c.safe)
                }
            } else {
                AppButton(
                    "받았다고 알리기",
                    onReply,
                    Kind.SECONDARY,
                    Modifier.padding(start = 76.dp),
                    height = 64.dp,
                    enabled = canReply,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- sheets

@Composable
private fun SheetScrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Sheet(width: Dp = 820.dp, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalAppColors.current
    Column(
        Modifier.widthIn(max = width).fillMaxWidth(0.92f).clip(RoundedCornerShape(24.dp)).background(c.canvas)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(32.dp),
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
    SheetScrim(onCancel) {
        Sheet {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(copy.tone.soft()), contentAlignment = Alignment.Center) {
                    Icon(copy.icon, contentDescription = null, tint = copy.tone.strong(), modifier = Modifier.size(52.dp))
                }
                Spacer(Modifier.width(22.dp))
                Column {
                    Text(copy.title, fontSize = Type.display, fontWeight = FontWeight.ExtraBold, color = c.ink)
                    Text(copy.detail, fontSize = Type.body, color = c.body)
                }
            }
            Spacer(Modifier.height(28.dp))
            Divider()
            if (message.asksPeople) {
                InfoRow("함께 있는 사람") {
                    Stepper(state.people, onPeople)
                }
                Divider()
            }
            InfoRow("보내는 방법") {
                Text(methodText(state, message), fontSize = Type.body, fontWeight = FontWeight.SemiBold, color = c.ink)
            }
            Divider()
            InfoRow("내 위치") {
                Text(
                    locationText(state),
                    fontSize = Type.body,
                    fontWeight = FontWeight.SemiBold,
                    color = if (state.shareLocation && state.myFix != null) c.ink else c.body,
                    modifier = Modifier.weight(1f),
                )
                if (state.locationPermission) {
                    Switch(
                        checked = state.shareLocation,
                        onCheckedChange = { onToggleLocation() },
                        modifier = Modifier.scale(1.4f).padding(end = 12.dp),
                        colors = SwitchDefaults.colors(checkedTrackColor = c.accent),
                    )
                }
            }
            Divider()
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Size.gap)) {
                AppButton("취소", onCancel, Kind.SECONDARY, Modifier.weight(1f))
                AppButton("보내기", onSend, Kind.TONE, Modifier.weight(1.6f), tone = copy.tone, enabled = state.canTransmit)
            }
        }
    }
}

@Composable
private fun Stepper(value: Int, onChange: (Int) -> Unit) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundButton("−", enabled = value > 0) { onChange(-1) }
        Text(
            peopleText(value) ?: "모름",
            fontSize = Type.display,
            fontWeight = FontWeight.Bold,
            color = c.ink,
            modifier = Modifier.widthIn(min = 150.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        RoundButton("+", enabled = value < 15) { onChange(1) }
    }
}

@Composable
private fun RoundButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        Modifier.size(Size.secondaryButton).clip(CircleShape).background(c.surface)
            .border(1.dp, c.line, CircleShape).alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = Type.display, fontWeight = FontWeight.Bold, color = c.ink) }
}

@Composable
private fun MoreSheet(primary: List<RescueMessage>, onPick: (RescueMessage) -> Unit, onClose: () -> Unit) {
    val c = LocalAppColors.current
    SheetScrim(onClose) {
        Sheet(width = 1100.dp) {
            Text("다른 메시지", fontSize = Type.display, fontWeight = FontWeight.ExtraBold, color = c.ink)
            Spacer(Modifier.height(20.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                moreGroups(primary).forEach { (group, messages) ->
                    Text(group, fontSize = Type.meta, fontWeight = FontWeight.SemiBold, color = c.mute)
                    Spacer(Modifier.height(10.dp))
                    messages.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Size.gap)) {
                            row.forEach { MessageTile(it, true, onPick, Modifier.weight(1f)) }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(Size.gap))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            AppButton("닫기", onClose, Kind.SECONDARY, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SettingsSheet(state: UiState, actions: ScreenActions, onClose: () -> Unit) {
    val c = LocalAppColors.current
    SheetScrim(onClose) {
        Sheet(width = 980.dp) {
            Text("설정", fontSize = Type.display, fontWeight = FontWeight.ExtraBold, color = c.ink)
            Spacer(Modifier.height(12.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                SectionTitle("보내는 방법")
                SwitchRow("소리로 알리기", "차 스피커로 신호음을 보내요. 근처 차량이 이 소리를 받아 글자로 보여 줘요.", state.useSound, actions.toggleSound)
                SwitchRow(
                    "사람이 듣는 SOS 소리",
                    "위급한 메시지는 신호음 뒤에 '삐삐삐 삐- 삐- 삐- 삐삐삐'를 내서 앱이 없는 사람도 알아듣게 해요.",
                    state.audibleSos,
                    actions.toggleAudibleSos,
                    enabled = state.useSound,
                )
                SwitchRow("불빛으로 알리기", "전조등을 국제 조난 신호 방식으로 깜빡여요.", state.useLight, actions.toggleLight, enabled = !state.blackout)
                if (state.useLight && !state.blackout) {
                    InfoRow("깜빡일 위치") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Segment("앞", state.lampGroup == LampGroup.FRONT) { actions.lampGroup(LampGroup.FRONT) }
                            Segment("뒤", state.lampGroup == LampGroup.REAR) { actions.lampGroup(LampGroup.REAR) }
                            Segment("앞뒤", state.lampGroup == LampGroup.FRONT_AND_REAR) { actions.lampGroup(LampGroup.FRONT_AND_REAR) }
                        }
                    }
                }
                SwitchRow(
                    "등화관제",
                    "공습경보 같은 상황에서 불빛을 쓰지 않고 소리로만 보내요.",
                    state.blackout,
                    actions.toggleBlackout,
                )
                SwitchRow(
                    "내 위치 함께 보내기",
                    if (state.locationPermission) "구조대가 찾아올 수 있게 GPS 위치(약 40m 범위)를 같이 보내요. 보낼 때만 나가요."
                    else "위치 권한이 없어요. 기기 설정에서 위치 권한을 허용하면 쓸 수 있어요.",
                    state.shareLocation && state.locationPermission,
                    actions.toggleShareLocation,
                    enabled = state.locationPermission,
                )
                SectionTitle("받은 신호")
                SwitchRow("다음 차량으로 이어서 전달", "받은 신호를 잠시 뒤 내 차에서도 다시 보내 더 멀리 퍼지게 해요.", state.autoRelay, actions.toggleRelay)
                SwitchRow(
                    "받은 신호 음성으로 알려 주기",
                    if (state.voiceAvailable) "화면을 보지 않아도 '가까운 차량에서 응급 환자 2명 신호를 받았어요'처럼 읽어 줘요."
                    else "이 기기에는 한국어 음성 엔진이 없어 쓸 수 없어요.",
                    state.voiceAnnounce && state.voiceAvailable,
                    actions.toggleVoice,
                    enabled = state.voiceAvailable,
                )
                SectionTitle("시연")
                Row(horizontalArrangement = Arrangement.spacedBy(Size.gap), modifier = Modifier.padding(vertical = 12.dp)) {
                    AppButton("다른 차 신호 받아 보기", actions.simulateIncoming, Kind.SECONDARY, Modifier.weight(1f))
                    if (state.vehicleMode == VehicleMode.SIMULATION) {
                        AppButton("기어 P", { actions.setGear(GearState.PARK) }, Kind.SECONDARY, Modifier.weight(0.6f))
                        AppButton("기어 D", { actions.setGear(GearState.DRIVE) }, Kind.SECONDARY, Modifier.weight(0.6f))
                    }
                }
                SectionTitle("차량 정보")
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
            Spacer(Modifier.height(20.dp))
            AppButton("닫기", onClose, Kind.SECONDARY, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
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
            Text(detail, fontSize = Type.meta, color = c.mute)
        }
        Spacer(Modifier.width(20.dp))
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            enabled = enabled,
            modifier = Modifier.scale(1.4f).padding(end = 12.dp),
            colors = SwitchDefaults.colors(checkedTrackColor = c.accent),
        )
    }
}

@Composable
private fun InfoRow(label: String, content: @Composable RowScope.() -> Unit) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = Type.body, color = c.body, modifier = Modifier.width(240.dp))
        content()
    }
}

@Composable
private fun Segment(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        Modifier.heightIn(min = 64.dp).widthIn(min = 110.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) c.accent else c.surface)
            .border(1.dp, if (selected) c.accent else c.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = Type.body, fontWeight = FontWeight.SemiBold, color = if (selected) c.onStrong else c.ink) }
}

@Composable
private fun Divider() {
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
        Text(text, fontSize = Type.body, fontWeight = FontWeight.SemiBold, color = LocalAppColors.current.ink)
    }
}

@Composable
private fun Tag(text: String, color: Color, background: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(background).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, fontSize = Type.meta, fontWeight = FontWeight.SemiBold, color = color) }
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
    val (bg, fg, border) = when (kind) {
        Kind.TONE -> Triple(tone.strong(), c.onStrong, null)
        Kind.SECONDARY -> Triple(c.canvas, c.ink, BorderStroke(1.dp, c.line))
        Kind.STRONG_NEUTRAL -> Triple(c.ink, c.page, null)
    }
    Row(
        modifier.heightIn(min = height).clip(RoundedCornerShape(Size.radius)).background(bg)
            .let { if (border != null) it.border(border, RoundedCornerShape(Size.radius)) else it }
            .alpha(if (enabled) 1f else 0.4f)
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

@Composable
private fun GuideBanner(guide: DisasterGuide, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 88.dp).clip(RoundedCornerShape(Size.radius)).background(c.helpSoft)
            .clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(guide.type.icon, contentDescription = null, tint = c.help, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(16.dp))
        Text(guide.headline, fontSize = Type.body, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text("행동요령 ›", fontSize = Type.body, fontWeight = FontWeight.Bold, color = c.help)
    }
}

@Composable
private fun DisasterSheet(current: DisasterType, onPick: (DisasterType) -> Unit, onClose: () -> Unit) {
    val c = LocalAppColors.current
    SheetScrim(onClose) {
        Sheet(width = 1200.dp) {
            Text("지금 어떤 상황인가요?", fontSize = Type.display, fontWeight = FontWeight.ExtraBold, color = c.ink)
            Text("고르면 그 상황에 맞는 행동요령과 메시지를 먼저 보여 줘요", fontSize = Type.body, color = c.body)
            Spacer(Modifier.height(20.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                DisasterType.entries.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Size.gap)) {
                        row.forEach { type ->
                            val selected = type == current
                            Column(
                                Modifier.weight(1f).heightIn(min = 120.dp).clip(RoundedCornerShape(Size.radius))
                                    .background(if (selected) c.accentSoft else c.canvas)
                                    .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.line, RoundedCornerShape(Size.radius))
                                    .clickable { onPick(type) }.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(type.icon, contentDescription = null, tint = if (selected) c.accent else c.body, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(type.label, fontSize = Type.body, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1)
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(Size.gap))
                }
            }
            AppButton("닫기", onClose, Kind.SECONDARY, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun GuideSheet(guide: DisasterGuide, onClose: () -> Unit) {
    val c = LocalAppColors.current
    SheetScrim(onClose) {
        Sheet(width = 1100.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(guide.type.icon, contentDescription = null, tint = c.help, modifier = Modifier.size(52.dp))
                Spacer(Modifier.width(16.dp))
                Text("${guide.type.label} 행동요령", fontSize = Type.display, fontWeight = FontWeight.ExtraBold, color = c.ink)
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                SectionTitle("이렇게 하세요")
                guide.actions.forEachIndexed { i, step ->
                    GuideLine("${i + 1}", step.text, c.ink, step.foreign)
                }
                if (guide.avoid.isNotEmpty()) {
                    SectionTitle("하지 마세요")
                    guide.avoid.forEach { GuideLine("✕", it.text, c.urgent, it.foreign) }
                }
                guide.carRisk?.let {
                    SectionTitle("차에서 조심할 점")
                    Text(it, fontSize = Type.body, color = c.ink, modifier = Modifier.padding(vertical = 6.dp))
                }
                SectionTitle("출처")
                guide.sources.forEach {
                    Text("${it.kind.label} · ${it.name}", fontSize = Type.meta, color = c.mute)
                }
                if ((guide.actions + guide.avoid).any { it.foreign }) {
                    Spacer(Modifier.height(6.dp))
                    Text("(해외) 표시는 국내 공식 지침이 없어 해외 정부 자료를 옮긴 내용이에요", fontSize = Type.meta, color = c.mute)
                }
            }
            Spacer(Modifier.height(20.dp))
            AppButton("닫기", onClose, Kind.SECONDARY, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun GuideLine(mark: String, text: String, markColor: Color, foreign: Boolean) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.surface), contentAlignment = Alignment.Center) {
            Text(mark, fontSize = Type.meta, fontWeight = FontWeight.Bold, color = markColor)
        }
        Spacer(Modifier.width(14.dp))
        Text(
            if (foreign) "$text (해외)" else text,
            fontSize = Type.body,
            color = c.ink,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
