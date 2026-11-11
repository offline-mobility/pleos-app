package io.github.offlinemobility.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Cyclone
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flood
import androidx.compose.material.icons.filled.Landslide
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.offlinemobility.app.core.disaster.DisasterType

val DisasterType.icon: ImageVector
    get() = when (this) {
        DisasterType.GENERAL -> Icons.Filled.Shield
        DisasterType.EARTHQUAKE -> Icons.Filled.Vibration
        DisasterType.WILDFIRE -> Icons.Filled.LocalFireDepartment
        DisasterType.FLOOD -> Icons.Filled.Flood
        DisasterType.TYPHOON -> Icons.Filled.Cyclone
        DisasterType.SNOW_COLD -> Icons.Filled.AcUnit
        DisasterType.HEAT -> Icons.Filled.WbSunny
        DisasterType.CHEMICAL -> Icons.Filled.Science
        DisasterType.RADIATION -> Icons.Filled.Dangerous
        DisasterType.AIR_RAID -> Icons.Filled.Campaign
        DisasterType.TUNNEL -> Icons.Filled.DirectionsCar
        DisasterType.LANDSLIDE -> Icons.Filled.Landslide
        DisasterType.BLACKOUT -> Icons.Filled.PowerOff
    }
