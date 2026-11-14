package io.github.offlinemobility.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Cyclone
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flood
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Masks
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.offlinemobility.app.UiState
import io.github.offlinemobility.app.core.disaster.DisasterType

val DisasterType.icon: ImageVector
    get() = when (this) {
        DisasterType.EVACUATION -> Icons.Filled.DirectionsCar
        DisasterType.ALERT -> Icons.Filled.Campaign
        DisasterType.AIR_RAID -> Icons.Filled.Warning
        DisasterType.CBRN -> Icons.Filled.Masks
        DisasterType.SNOW_COLD -> Icons.Filled.AcUnit
        DisasterType.FLOOD -> Icons.Filled.Flood
        DisasterType.EARTHQUAKE -> Icons.Filled.Vibration
        DisasterType.WILDFIRE -> Icons.Filled.LocalFireDepartment
        DisasterType.TYPHOON -> Icons.Filled.Cyclone
    }

/** One line in the guide about what the car did by itself for this stage; [failed] when the person has to do it. */
data class CarNote(val text: String, val failed: Boolean = false)

fun UiState.carNote(): CarNote? {
    val dark = carDarkRefused
    val seal = carSeal
    return when {
        guide.blackout && dark != null ->
            if (dark == 0) CarNote("차 불을 모두 껐어요") else CarNote("차 불을 다 끄지 못했어요. 직접 꺼 주세요", failed = true)
        cabinSealed && seal != null -> when {
            seal.refused -> CarNote("창문이나 공조를 바꾸지 못했어요. 직접 닫아 주세요", failed = true)
            seal.windowsRefused != null && seal.airRefused != null -> CarNote("창문을 닫고 바깥 공기를 막았어요")
            seal.windowsRefused != null -> CarNote("창문을 닫았어요")
            seal.airRefused != null -> CarNote("공조로 들어오는 바깥 공기를 막았어요")
            else -> null
        }
        else -> null
    }
}
