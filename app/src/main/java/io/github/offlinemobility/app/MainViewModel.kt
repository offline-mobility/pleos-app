package io.github.offlinemobility.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TransmissionPhase {
    IDLE,
    TRANSMITTING,
    COMPLETE,
}

data class MainUiState(
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val message: String = "Pleos Vehicle SDK 연결을 준비하고 있습니다.",
    val isParked: Boolean = true,
    val selectedMessageId: String = EmergencyProtocol.presets.first().id,
    val selectedChannel: SignalChannel = SignalChannel.LIGHT,
    val transmissionPhase: TransmissionPhase = TransmissionPhase.IDLE,
    val transmissionProgress: Float = 0f,
    val lastReceivedMessageId: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val vehicleClient = PleosVehicleClient(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private var transmissionJob: Job? = null

    init {
        connect()
    }

    fun connect() {
        if (_uiState.value.isConnecting) return

        _uiState.update {
            it.copy(
                isConnecting = true,
                message = "Pleos Vehicle SDK에 연결하고 있습니다.",
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching(vehicleClient::initialize)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            isConnected = true,
                            message = "Pleos Vehicle SDK 연결됨",
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            isConnected = false,
                            message = error.message
                                ?: "데모 모드 · Vehicle Service 연결 필요",
                        )
                    }
                }
        }
    }

    fun selectMessage(messageId: String) {
        if (_uiState.value.transmissionPhase == TransmissionPhase.TRANSMITTING) return
        if (EmergencyProtocol.findMessage(messageId) == null) return
        _uiState.update { it.copy(selectedMessageId = messageId) }
    }

    fun selectChannel(channel: SignalChannel) {
        if (_uiState.value.transmissionPhase == TransmissionPhase.TRANSMITTING) return
        _uiState.update { it.copy(selectedChannel = channel) }
    }

    fun toggleParkedDemo() {
        if (_uiState.value.transmissionPhase == TransmissionPhase.TRANSMITTING) return
        _uiState.update { it.copy(isParked = !it.isParked) }
    }

    fun transmit() {
        val state = _uiState.value
        if (!state.isParked || state.transmissionPhase == TransmissionPhase.TRANSMITTING) return

        val selected = EmergencyProtocol.findMessage(state.selectedMessageId) ?: return
        val pulses = EmergencyProtocol.toPulses(selected.morsePattern)
        if (pulses.isEmpty()) return

        transmissionJob?.cancel()
        transmissionJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    transmissionPhase = TransmissionPhase.TRANSMITTING,
                    transmissionProgress = 0f,
                )
            }

            pulses.forEachIndexed { index, pulse ->
                delay(pulse.durationUnits * DEMO_UNIT_MILLIS)
                _uiState.update {
                    it.copy(transmissionProgress = (index + 1).toFloat() / pulses.size)
                }
            }

            _uiState.update { it.copy(transmissionPhase = TransmissionPhase.COMPLETE) }
            delay(COMPLETE_HOLD_MILLIS)
            _uiState.update {
                it.copy(
                    transmissionPhase = TransmissionPhase.IDLE,
                    transmissionProgress = 0f,
                )
            }
        }
    }

    fun simulateReceive() {
        val candidates = EmergencyProtocol.presets.filterNot {
            it.id == _uiState.value.lastReceivedMessageId
        }
        _uiState.update { it.copy(lastReceivedMessageId = candidates.random().id) }
    }

    override fun onCleared() {
        transmissionJob?.cancel()
        vehicleClient.release()
        super.onCleared()
    }

    private companion object {
        const val DEMO_UNIT_MILLIS = 90L
        const val COMPLETE_HOLD_MILLIS = 1_200L
    }
}

