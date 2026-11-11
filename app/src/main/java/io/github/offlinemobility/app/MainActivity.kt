package io.github.offlinemobility.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.offlinemobility.app.ui.AppTheme
import io.github.offlinemobility.app.ui.MainScreen
import io.github.offlinemobility.app.ui.ScreenActions
import io.github.offlinemobility.app.ui.asksPeople

class MainActivity : ComponentActivity() {
    private val engine: SignalEngine get() = (application as OfflineMobilityApp).engine

    private val listenPermissions: Array<String>
        get() = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()

    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val state by engine.state.collectAsStateWithLifecycle()
                val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    engine.refreshLocation()
                    if (hasMic()) SignalService.start(this) else engine.onMicPermissionDenied()
                }
                val startListening = {
                    if (hasMic()) SignalService.start(this) else permissionLauncher.launch(listenPermissions)
                }
                LaunchedEffect(Unit) { if (!state.listening) startListening() }
                MainScreen(
                    state = state,
                    actions = ScreenActions(
                        send = { engine.send(it, withPeople = it.asksPeople) },
                        people = engine::changePeople,
                        stop = { engine.stopTransmission() },
                        dismissResult = engine::dismissResult,
                        acknowledge = engine::acknowledge,
                        toggleListen = { if (state.listening) SignalService.stop(this) else startListening() },
                        toggleSound = engine::toggleSound,
                        toggleLight = engine::toggleLight,
                        lampGroup = engine::selectLampGroup,
                        toggleBlackout = engine::toggleBlackoutByUser,
                        toggleRelay = engine::toggleAutoRelay,
                        toggleShareLocation = engine::toggleShareLocation,
                        selectDisaster = engine::selectDisaster,
                        toggleAudibleSos = engine::toggleAudibleSos,
                        toggleVoice = engine::toggleVoiceAnnounce,
                        simulateIncoming = engine::simulateIncoming,
                        setGear = engine::setSimulatedGear,
                        retryVehicle = engine::connectVehicle,
                    ),
                )
            }
        }
    }
}
