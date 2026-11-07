package io.github.offlinemobility.app.core.safety

enum class GearState(val label: String) {
    PARK("P"),
    REVERSE("R"),
    NEUTRAL("N"),
    DRIVE("D"),
    UNKNOWN("?"),
}

data class VehicleSafetyState(
    val gear: GearState = GearState.UNKNOWN,
    /** CarUxRestrictions requires distraction optimization (vehicle moving or regulated). */
    val drivingRestricted: Boolean = false,
)

enum class TransmitChannel { LIGHT, SOUND }

data class SafetyVerdict(val allowed: Boolean, val reasons: List<String>)

/**
 * Transmission is allowed only while the vehicle is parked.
 * Exterior lamps flashing on a moving vehicle can dazzle other drivers, so there is no override.
 */
object SafetyGate {
    fun evaluate(state: VehicleSafetyState, channel: TransmitChannel): SafetyVerdict {
        val reasons = buildList {
            when (state.gear) {
                GearState.PARK -> Unit
                GearState.UNKNOWN -> add("기어 상태를 확인할 수 없습니다")
                else -> add("P단에서만 송신할 수 있습니다 (현재 ${state.gear.label})")
            }
            if (state.drivingRestricted) add("주행 중에는 송신할 수 없습니다")
        }
        return SafetyVerdict(allowed = reasons.isEmpty(), reasons = reasons)
    }
}
