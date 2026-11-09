package io.github.offlinemobility.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.offlinemobility.app.ui.AppTheme
import io.github.offlinemobility.app.ui.MainScreen
import io.github.offlinemobility.app.ui.ScreenActions
import io.github.offlinemobility.app.ui.asksPeople

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
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
                MainScreen(
                    state = state,
                    actions = ScreenActions(
                        send = { viewModel.send(it, withPeople = it.asksPeople) },
                        people = viewModel::changePeople,
                        stop = { viewModel.stopTransmission() },
                        dismissResult = viewModel::dismissResult,
                        acknowledge = viewModel::acknowledge,
                        toggleListen = {
                            if (state.listening) viewModel.stopListening()
                            else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        toggleSound = viewModel::toggleSound,
                        toggleLight = viewModel::toggleLight,
                        lampGroup = viewModel::selectLampGroup,
                        toggleBlackout = viewModel::toggleBlackout,
                        toggleRelay = viewModel::toggleAutoRelay,
                        simulateIncoming = viewModel::simulateIncoming,
                        setGear = viewModel::setSimulatedGear,
                        retryVehicle = viewModel::connectVehicle,
                    ),
                )
            }
        }
    }
}
