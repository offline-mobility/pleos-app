package io.github.offlinemobility.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = offlineMobilityColors) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                ChallengeScreen(
                    state = state,
                    onRetry = viewModel::connect,
                    onSelectMessage = viewModel::selectMessage,
                    onSelectChannel = viewModel::selectChannel,
                    onToggleParked = viewModel::toggleParkedDemo,
                    onTransmit = viewModel::transmit,
                    onSimulateReceive = viewModel::simulateReceive,
                )
            }
        }
    }
}

private val offlineMobilityColors = darkColorScheme(
    primary = Color(0xFF5BE7C4),
    onPrimary = Color(0xFF03241C),
    secondary = Color(0xFF6AB7FF),
    background = Color(0xFF071018),
    surface = Color(0xFF0D1923),
    onSurface = Color(0xFFEAF2F7),
    onSurfaceVariant = Color(0xFF9FB1BE),
    error = Color(0xFFFF6B6B),
)

@Composable
private fun ChallengeScreen(
    state: MainUiState,
    onRetry: () -> Unit,
    onSelectMessage: (String) -> Unit,
    onSelectChannel: (SignalChannel) -> Unit,
    onToggleParked: () -> Unit,
    onTransmit: () -> Unit,
    onSimulateReceive: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(state = state, onRetry = onRetry, onToggleParked = onToggleParked)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 28.dp, end = 28.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                MessagePresetPanel(
                    modifier = Modifier.weight(1.25f),
                    state = state,
                    onSelectMessage = onSelectMessage,
                )
                SignalPanel(
                    modifier = Modifier.weight(0.75f),
                    state = state,
                    onSelectChannel = onSelectChannel,
                    onTransmit = onTransmit,
                    onSimulateReceive = onSimulateReceive,
                )
            }
        }
    }
}

@Composable
private fun Header(
    state: MainUiState,
    onRetry: () -> Unit,
    onToggleParked: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "OM",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = "OFFLINE MOBILITY",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            Text(
                text = "통신 두절 상황을 위한 차량 간 비상 신호",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        ConnectionStatus(state = state, onRetry = onRetry)
        Spacer(Modifier.width(12.dp))
        OutlinedButton(onClick = onToggleParked) {
            Text(if (state.isParked) "P · 정차 데모" else "D · 주행 데모")
        }
    }
}

@Composable
private fun ConnectionStatus(state: MainUiState, onRetry: () -> Unit) {
    val statusColor = when {
        state.isConnected -> MaterialTheme.colorScheme.primary
        state.isConnecting -> Color(0xFFFFC857)
        else -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = !state.isConnecting && !state.isConnected, onClick = onRetry)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(statusColor),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = state.message,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MessagePresetPanel(
    modifier: Modifier,
    state: MainUiState,
    onSelectMessage: (String) -> Unit,
) {
    Panel(modifier = modifier) {
        Text(
            text = "보낼 메시지",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            modifier = Modifier.padding(top = 3.dp, bottom = 14.dp),
            text = "짧고 오해 없는 표준 메시지를 선택하세요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        EmergencyProtocol.presets.chunked(2).forEach { rowMessages ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowMessages.forEach { message ->
                    MessageCard(
                        modifier = Modifier.weight(1f),
                        message = message,
                        isSelected = message.id == state.selectedMessageId,
                        enabled = state.transmissionPhase != TransmissionPhase.TRANSMITTING,
                        onClick = { onSelectMessage(message.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    modifier: Modifier,
    message: EmergencyMessage,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val priorityColor = when (message.priority) {
        MessagePriority.CRITICAL -> MaterialTheme.colorScheme.error
        MessagePriority.HIGH -> Color(0xFFFFC857)
        MessagePriority.NORMAL -> MaterialTheme.colorScheme.secondary
    }
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF243441)

    Card(
        modifier = modifier
            .height(86.dp)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101F2A)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(13.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(99.dp))
                    .background(priorityColor),
            )
            Spacer(Modifier.width(11.dp))
            Column {
                Text(
                    text = message.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    modifier = Modifier.padding(top = 5.dp),
                    text = message.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SignalPanel(
    modifier: Modifier,
    state: MainUiState,
    onSelectChannel: (SignalChannel) -> Unit,
    onTransmit: () -> Unit,
    onSimulateReceive: () -> Unit,
) {
    val selectedMessage = EmergencyProtocol.findMessage(state.selectedMessageId)
        ?: EmergencyProtocol.presets.first()
    val receivedMessage = state.lastReceivedMessageId?.let(EmergencyProtocol::findMessage)

    Panel(modifier = modifier) {
        Text(
            text = "신호 송수신",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            modifier = Modifier.padding(top = 3.dp, bottom = 14.dp),
            text = "현재는 안전한 프로토콜 데모 모드입니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SignalChannel.entries.forEach { channel ->
                val selected = state.selectedChannel == channel
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectChannel(channel) },
                    enabled = state.transmissionPhase != TransmissionPhase.TRANSMITTING,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) Color(0xFF153B3A) else Color.Transparent,
                        contentColor = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text(channel.label)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF071018))
                .padding(14.dp),
        ) {
            Text(
                text = selectedMessage.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                modifier = Modifier.padding(top = 7.dp),
                text = selectedMessage.morsePattern,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
            )
        }

        if (state.transmissionPhase == TransmissionPhase.TRANSMITTING) {
            LinearProgressIndicator(
                progress = { state.transmissionProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp)),
            )
        } else {
            Spacer(Modifier.height(20.dp))
        }

        val canTransmit = state.isParked && state.transmissionPhase != TransmissionPhase.TRANSMITTING
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            onClick = onTransmit,
            enabled = canTransmit,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(
                text = when {
                    !state.isParked -> "주행 중 송신 잠금"
                    state.transmissionPhase == TransmissionPhase.TRANSMITTING -> "신호 송신 중"
                    state.transmissionPhase == TransmissionPhase.COMPLETE -> "송신 완료"
                    else -> "선택 신호 보내기"
                },
                fontWeight = FontWeight.Bold,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "최근 수신",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    modifier = Modifier.padding(top = 3.dp),
                    text = receivedMessage?.title ?: "수신된 신호 없음",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            OutlinedButton(onClick = onSimulateReceive) {
                Text("수신 테스트")
            }
        }
    }
}

@Composable
private fun Panel(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            content = content,
        )
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun ChallengeScreenPreview() {
    MaterialTheme(colorScheme = offlineMobilityColors) {
        ChallengeScreen(
            state = MainUiState(
                isConnected = true,
                message = "Pleos Vehicle SDK 연결됨",
                lastReceivedMessageId = "BLOCKED",
            ),
            onRetry = {},
            onSelectMessage = {},
            onSelectChannel = {},
            onToggleParked = {},
            onTransmit = {},
            onSimulateReceive = {},
        )
    }
}
