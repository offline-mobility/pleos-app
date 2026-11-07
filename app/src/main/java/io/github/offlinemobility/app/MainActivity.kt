package io.github.offlinemobility.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.offlinemobility.app.core.light.LampGroup
import io.github.offlinemobility.app.core.protocol.RescueMessage
import io.github.offlinemobility.app.core.safety.GearState
import io.github.offlinemobility.app.vehicle.LampSupport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = Palette.scheme) {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (granted) viewModel.startListening() else viewModel.onMicPermissionDenied()
                }
                LaunchedEffect(Unit) {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.startListening()
                    } else {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(
                        state = state,
                        actions = ScreenActions(
                            select = viewModel::select,
                            people = viewModel::changePeople,
                            toggleSound = viewModel::toggleSound,
                            toggleLight = viewModel::toggleLight,
                            lampGroup = viewModel::selectLampGroup,
                            transmit = viewModel::transmit,
                            stop = { viewModel.stopTransmission() },
                            toggleListen = {
                                if (state.listening) viewModel.stopListening()
                                else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            toggleRelay = viewModel::toggleAutoRelay,
                            toggleBlackout = viewModel::toggleBlackout,
                            simulateIncoming = viewModel::simulateIncoming,
                            acknowledge = viewModel::acknowledge,
                            setGear = viewModel::setSimulatedGear,
                            retryVehicle = viewModel::connectVehicle,
                        ),
                    )
                }
            }
        }
    }
}

private object Palette {
    val critical = Color(0xFFFF5A5F)
    val accent = Color(0xFF5BE7C4)
    val warn = Color(0xFFFFC857)
    val line = Color(0xFF243645)
    val card = Color(0xFF0F1D28)
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF03241C),
        secondary = Color(0xFF6AB7FF),
        background = Color(0xFF071018),
        surface = card,
        onSurface = Color(0xFFEAF2F7),
        onSurfaceVariant = Color(0xFF9FB1BE),
        error = critical,
    )
}

private class ScreenActions(
    val select: (RescueMessage) -> Unit,
    val people: (Int) -> Unit,
    val toggleSound: () -> Unit,
    val toggleLight: () -> Unit,
    val lampGroup: (LampGroup) -> Unit,
    val transmit: () -> Unit,
    val stop: () -> Unit,
    val toggleListen: () -> Unit,
    val toggleRelay: () -> Unit,
    val toggleBlackout: () -> Unit,
    val simulateIncoming: () -> Unit,
    val acknowledge: (InboxItem) -> Unit,
    val setGear: (GearState) -> Unit,
    val retryVehicle: () -> Unit,
)

@Composable
private fun MainScreen(state: UiState, actions: ScreenActions) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        StatusBar(state, actions)
        Spacer(Modifier.height(16.dp))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth > maxHeight) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ComposePanel(state, actions, Modifier.weight(1.2f).fillMaxHeight())
                    InboxPanel(state, actions, Modifier.weight(0.8f).fillMaxHeight())
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InboxPanel(state, actions, Modifier.weight(0.4f).fillMaxWidth())
                    ComposePanel(state, actions, Modifier.weight(0.6f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun StatusBar(state: UiState, actions: ScreenActions) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Offline Mobility", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        val vehicleColor = when (state.vehicleMode) {
            VehicleMode.PLEOS -> Palette.accent
            VehicleMode.SIMULATION -> Palette.warn
            VehicleMode.CONNECTING -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Chip(
            text = when (state.vehicleMode) {
                VehicleMode.PLEOS -> "차량 연결"
                VehicleMode.SIMULATION -> "시뮬레이션"
                VehicleMode.CONNECTING -> "연결 중"
            },
            color = vehicleColor,
            onClick = if (state.vehicleMode == VehicleMode.SIMULATION) actions.retryVehicle else null,
        )
        Chip("기어 ${state.gear.label}", if (state.gear == GearState.PARK) Palette.accent else Palette.critical)
        if (state.vehicleMode == VehicleMode.SIMULATION) {
            listOf(GearState.PARK, GearState.DRIVE).forEach { g ->
                Chip(if (g == GearState.PARK) "P로" else "D로", MaterialTheme.colorScheme.onSurfaceVariant) { actions.setGear(g) }
            }
        }
        Spacer(Modifier.weight(1f))
        LevelMeter(state.inputLevelDb, state.listening)
        Chip(
            text = if (state.listening) "수신 대기" else "수신 꺼짐",
            color = if (state.listening) Palette.accent else MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = actions.toggleListen,
        )
    }
}

@Composable
private fun ComposePanel(state: UiState, actions: ScreenActions, modifier: Modifier) {
    Panel(modifier) {
        Text("보낼 메시지", fontSize = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(11.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 220.dp),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(RescueMessage.entries.filter { it != RescueMessage.ACK }) { message ->
                MessageTile(message, selected = message == state.selected, enabled = !state.tx.active) {
                    actions.select(message)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("인원", fontSize = 21.sp)
            SmallButton("−") { actions.people(-1) }
            Text(if (state.people == 0) "미상" else if (state.people >= 15) "15+" else "${state.people}명", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            SmallButton("+") { actions.people(1) }
            Spacer(Modifier.width(8.dp))
            Toggle("소리", state.useSound, actions.toggleSound)
            Toggle("빛", state.lightEnabled, actions.toggleLight)
            Toggle("등화관제", state.blackout, actions.toggleBlackout)
        }
        if (state.blackout) {
            Spacer(Modifier.height(11.dp))
            Text(
                "등화관제 중 · 외부 조명 신호를 쓰지 않습니다 (민방공 야간 공습경보 행동요령)",
                fontSize = 18.sp,
                color = Palette.warn,
            )
        } else if (state.useLight) {
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LampGroup.entries.forEach { group ->
                    Toggle(group.label, state.lampGroup == group) { actions.lampGroup(group) }
                }
                val plan = state.lightPlan
                val label = when {
                    plan.isEmpty -> "이 차량은 신호용 조명을 제어할 수 없습니다"
                    plan.steady.isEmpty() -> "점멸: ${plan.flash.joinToString { it.label }} · ${state.lightPattern.title}"
                    else -> "점멸: ${plan.flash.joinToString { it.label }} · 유지: ${plan.steady.joinToString { it.label }}"
                }
                val dropped = plan.isEmpty
                Text(
                    text = label,
                    fontSize = 18.sp,
                    color = if (!dropped) MaterialTheme.colorScheme.onSurfaceVariant else Palette.warn,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        TransmitBar(state, actions)
    }
}

@Composable
private fun TransmitBar(state: UiState, actions: ScreenActions) {
    val tx = state.tx
    if (tx.active) {
        if (tx.soundActive) Progress("음향", tx.soundProgress, Palette.accent)
        if (tx.lightActive) Progress(if (tx.lightOn) "조명 ● ${tx.lightPattern?.title}" else "조명 ○ ${tx.lightPattern?.title}", tx.lightProgress, Palette.warn)
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = actions.stop,
            modifier = Modifier.fillMaxWidth().height(86.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.critical),
        ) { Text("송신 중단", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
    } else {
        val reason = state.safety.reasons.firstOrNull()
            ?: if (!state.useSound && !state.lightUsable) "소리 또는 빛을 하나 이상 선택하세요" else null
        Text(
            text = reason ?: tx.lastResult ?: "정차(P단) 상태에서만 송신됩니다",
            fontSize = 18.sp,
            color = if (reason != null) Palette.critical else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = actions.transmit,
            enabled = state.canTransmit,
            modifier = Modifier.fillMaxWidth().height(86.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.selected.critical) Palette.critical else Palette.accent,
            ),
        ) {
            Text("${state.selected.title} 보내기", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InboxPanel(state: UiState, actions: ScreenActions, modifier: Modifier) {
    Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("받은 신호", fontSize = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Toggle("자동 중계", state.autoRelay, actions.toggleRelay)
            Spacer(Modifier.width(6.dp))
            Toggle("가상 수신", false, actions.simulateIncoming)
        }
        state.micMessage?.let {
            Text(it, fontSize = 18.sp, color = Palette.warn, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(11.dp))
        if (state.inbox.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (state.listening) "주변 차량 신호를 듣고 있습니다" else "수신이 꺼져 있습니다",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.inbox, key = { it.id }) { item ->
                    InboxRow(item, canAck = state.safety.allowed && !state.tx.active) { actions.acknowledge(item) }
                }
            }
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.KOREA)

@Composable
private fun InboxRow(item: InboxItem, canAck: Boolean, onAck: () -> Unit) {
    val f = item.frame
    val color = if (f.message.critical) Palette.critical else Palette.accent
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (item.kind == InboxKind.ECHO) "내 신호 중계 확인 · ${f.message.title}" else f.message.title,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.weight(1f),
            )
            Text(timeFormat.format(Date(item.receivedAtMs)), fontSize = 18.sp, fontFamily = FontFamily.Monospace)
        }
        val people = when (f.people) { 0 -> "인원 미상"; 15 -> "15명 이상"; else -> "${f.people}명" }
        val route = if (f.hop == 0) "직접 수신" else "${f.hop}대 거쳐 중계"
        val extra = buildList {
            add(people)
            add(route)
            add("차량 #%02X".format(f.senderId))
            if (item.repeats > 1) add("${item.repeats}회 수신")
            add("SNR %.0fdB".format(item.snrDb))
            if (item.correctedSymbols > 0) add("오류 ${item.correctedSymbols}개 정정")
            if (item.relayState.label.isNotEmpty()) add(item.relayState.label)
        }
        Text(extra.joinToString(" · "), fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (item.kind == InboxKind.NEW && f.message != RescueMessage.ACK) {
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onAck, enabled = canAck, modifier = Modifier.heightIn(min = 65.dp)) {
                Text("수신 확인 보내기", fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun MessageTile(message: RescueMessage, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val color = if (message.critical) Palette.critical else Palette.accent
    Box(
        Modifier.fillMaxWidth().heightIn(min = 81.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) color.copy(alpha = 0.22f) else Color.Transparent)
            .border(if (selected) 2.dp else 1.dp, if (selected) color else Palette.line, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(message.title, fontSize = 22.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun Panel(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Palette.card).border(1.dp, Palette.line, RoundedCornerShape(16.dp))
            .padding(14.dp),
        content = content,
    )
}

@Composable
private fun Chip(text: String, color: Color, onClick: (() -> Unit)? = null) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).border(1.dp, color, RoundedCornerShape(50))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .heightIn(min = 49.dp).padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = color, fontSize = 20.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun Toggle(text: String, on: Boolean, onClick: () -> Unit) {
    val color = if (on) Palette.accent else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) color.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.dp, color, RoundedCornerShape(10.dp)).clickable(onClick = onClick)
            .heightIn(min = 59.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = color, fontSize = 20.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(59.dp).clip(CircleShape).border(1.dp, Palette.line, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 28.sp) }
}

@Composable
private fun Progress(label: String, value: Float, color: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontSize = 18.sp, color = color)
        LinearProgressIndicator(progress = { value }, color = color, modifier = Modifier.fillMaxWidth().height(6.dp))
    }
}

@Composable
private fun LevelMeter(db: Float, active: Boolean, width: Dp = 96.dp) {
    val fraction = ((db + 80f) / 70f).coerceIn(0f, 1f)
    Box(Modifier.width(width).height(11.dp).clip(RoundedCornerShape(4.dp)).background(Palette.line)) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(if (active) fraction else 0f)
                .background(if (fraction > 0.8f) Palette.warn else Palette.accent),
        )
    }
}
