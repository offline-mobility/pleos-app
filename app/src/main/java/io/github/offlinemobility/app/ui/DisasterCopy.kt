package io.github.offlinemobility.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Masks
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.offlinemobility.app.core.disaster.DisasterType

val DisasterType.icon: ImageVector
    get() = when (this) {
        DisasterType.EVACUATION -> Icons.Filled.DirectionsCar
        DisasterType.ALERT -> Icons.Filled.Campaign
        DisasterType.AIR_RAID -> Icons.Filled.Warning
        DisasterType.CBRN -> Icons.Filled.Masks
    }
