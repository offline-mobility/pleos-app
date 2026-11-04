package io.github.offlinemobility.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val message: String = "Pleos Vehicle SDK 연결을 준비하고 있습니다.",
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val vehicleClient = PleosVehicleClient(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

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
                    _uiState.value = MainUiState(
                        isConnected = true,
                        message = "Pleos Vehicle SDK가 연결되었습니다.",
                    )
                }
                .onFailure { error ->
                    _uiState.value = MainUiState(
                        message = error.message
                            ?: "Pleos 전용 에뮬레이터와 Vehicle Service를 확인해 주세요.",
                    )
                }
        }
    }

    override fun onCleared() {
        vehicleClient.release()
        super.onCleared()
    }
}

