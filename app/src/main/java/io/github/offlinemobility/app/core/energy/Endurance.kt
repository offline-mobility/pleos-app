package io.github.offlinemobility.app.core.energy

/** What the vehicle reports; any field may be missing depending on the model. */
data class EnergyReading(
    /** Remaining traction battery energy (EV), Wh. */
    val evRemainingWh: Double? = null,
    /** Traction battery state of charge (EV), %. */
    val evPercent: Int? = null,
    /** Fuel level (combustion / hybrid), %. */
    val fuelPercent: Int? = null,
)

data class EnduranceEstimate(
    val percent: Int,
    val isEv: Boolean,
    /** Hours of listening with periodic light signalling at the current level. */
    val hours: Double,
    val low: Boolean,
)

/**
 * Rough endurance estimate, shown to people as an approximation ("약 N시간").
 *
 * Assumptions, chosen conservatively:
 * - Vehicle awake (infotainment, body/gateway modules, DC-DC losses, microphone and amplifier):
 *   [BASE_LOAD_W]. Climate control is excluded; heating or cooling can use several kilowatts.
 * - Light signalling: [LAMP_W] while lit; the beacon pattern keeps lamps lit about 10 % of the time,
 *   SOS about 36 %. [LIGHT_DUTY] averages a mix of both.
 * - EV: [RESERVE] of the remaining energy is left untouched so the car can still drive away.
 * - Combustion: idling at [IDLE_L_PER_H] keeps the 12 V system charged; tank size [TANK_L].
 */
object Endurance {
    const val BASE_LOAD_W = 250.0
    const val LAMP_W = 120.0
    const val LIGHT_DUTY = 0.2
    const val RESERVE = 0.1
    const val IDLE_L_PER_H = 0.8
    const val TANK_L = 50.0
    const val LOW_HOURS = 6.0

    fun estimate(reading: EnergyReading, lightsOn: Boolean): EnduranceEstimate? {
        val load = BASE_LOAD_W + if (lightsOn) LAMP_W * LIGHT_DUTY else 0.0
        reading.evRemainingWh?.takeIf { it > 0 }?.let { wh ->
            val hours = wh * (1 - RESERVE) / load
            val percent = reading.evPercent ?: return null
            return EnduranceEstimate(percent, isEv = true, hours = hours, low = hours < LOW_HOURS)
        }
        reading.fuelPercent?.let { pct ->
            val hours = TANK_L * pct / 100.0 / IDLE_L_PER_H
            return EnduranceEstimate(pct, isEv = false, hours = hours, low = hours < LOW_HOURS)
        }
        return null
    }

    /** "약 3시간" / "약 2일" / "1시간 미만". */
    fun hoursWord(hours: Double): String = when {
        hours < 1 -> "1시간 미만"
        hours < 48 -> "약 ${hours.toInt()}시간"
        else -> "약 ${(hours / 24).toInt()}일"
    }
}
