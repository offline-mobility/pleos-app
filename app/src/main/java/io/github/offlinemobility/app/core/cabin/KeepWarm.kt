package io.github.offlinemobility.app.core.cabin

import io.github.offlinemobility.app.core.energy.Endurance
import kotlin.math.ceil
import kotlin.math.max

enum class Seat { DRIVER, PASSENGER, REAR_LEFT, REAR_RIGHT }

/** How the cabin is kept warm while waiting for rescue. */
enum class WarmLevel(
    /** Cabin temperature the energy estimate is based on. */
    val averageCabinC: Double,
    val setpointC: Float,
    val seatLevel: Int,
    val wheelLevel: Int,
) {
    /** Heater cycles between [KeepWarmController.CYCLE_LOW_C] and the setpoint; seat heaters carry most of the comfort. */
    SAVER(averageCabinC = 14.0, setpointC = 16f, seatLevel = 2, wheelLevel = 1),

    /** Heater holds a lower setpoint without cycling, for older people and children. */
    GENTLE(averageCabinC = 18.0, setpointC = 18f, seatLevel = 1, wheelLevel = 0),
}

/**
 * Assumptions behind the estimate. The vehicle reports neither outside temperature nor heater type,
 * so every value is chosen to make the estimate shorter rather than longer.
 */
data class CabinAssumptions(
    /** 한파경보 기준(아침 최저 영하 15도). */
    val outsideC: Double = -15.0,
    /** Heat lost through body and glass per degree of difference, W/K. */
    val uaWattsPerK: Double = 45.0,
    /** 1.0 = resistive (PTC) heater. A heat pump does better, so real endurance is longer. */
    val heaterCop: Double = 1.0,
    val baseLoadW: Double = Endurance.BASE_LOAD_W,
    val bodyHeatW: Double = 80.0,
    /** Fresh air needed to hold CO₂ about 1,000 ppm above outside air: 0.005 L/s CO₂ per person. */
    val freshAirLpsPerPerson: Double = 5.0,
    /** Air the blower brings in while set to outside air, L/s. */
    val freshAirFlowLps: Double = 40.0,
    /** Air that leaks in with the vents closed, L/s. */
    val leakLps: Double = 2.0,
    val seatHeaterW: Map<Int, Double> = mapOf(0 to 0.0, 1 to 35.0, 2 to 50.0, 3 to 75.0),
    val wheelHeaterW: Double = 40.0,
    val normalCabinC: Double = 22.0,
    /** Share of the remaining energy that is never counted. */
    val reserveShare: Double = Endurance.RESERVE,
    val driveWhPerKm: Double = 250.0,
    val driveMargin: Double = 1.3,
) {
    companion object {
        /** Volumetric heat capacity of air, J/(L·K). */
        const val AIR_J_PER_LITRE_K = 1.2 * 1005.0 / 1000.0
    }
}

data class KeepWarmEstimate(
    /** Heater left at [CabinAssumptions.normalCabinC]. */
    val normalHours: Double,
    val saverHours: Double,
    val gentleHours: Double,
    /** Minutes of outside air in every [KeepWarmController.FRESH_CYCLE_MIN] minutes; 0 when the cabin stays sealed. */
    val freshMinutes: Int,
) {
    fun hours(level: WarmLevel): Double = if (level == WarmLevel.SAVER) saverHours else gentleHours
}

object KeepWarm {
    /**
     * @param sealed outside air must stay out (chemical, radiation, smoke), so only leakage ventilates.
     * @param shelterKm distance to the nearest known shelter; energy to drive there is set aside.
     */
    fun estimate(
        remainingWh: Double,
        people: Int,
        sealed: Boolean,
        shelterKm: Double? = null,
        a: CabinAssumptions = CabinAssumptions(),
    ): KeepWarmEstimate {
        val n = people.coerceAtLeast(1)
        val drive = (shelterKm ?: 0.0) * a.driveWhPerKm * a.driveMargin
        val usable = max(0.0, remainingWh - max(remainingWh * a.reserveShare, drive))
        val ventLps = if (sealed) a.leakLps else max(a.leakLps, n * a.freshAirLpsPerPerson)

        fun hours(cabinC: Double, seatLevel: Int, wheelLevel: Int): Double {
            val lossPerK = a.uaWattsPerK + CabinAssumptions.AIR_J_PER_LITRE_K * ventLps
            val heat = max(0.0, lossPerK * (cabinC - a.outsideC) - n * a.bodyHeatW)
            val seats = n.coerceAtMost(Seat.entries.size) * (a.seatHeaterW[seatLevel] ?: 0.0)
            val wheel = if (wheelLevel > 0) a.wheelHeaterW else 0.0
            return usable / (heat / a.heaterCop + seats + wheel + a.baseLoadW)
        }

        return KeepWarmEstimate(
            normalHours = hours(a.normalCabinC, 0, 0),
            saverHours = hours(WarmLevel.SAVER.averageCabinC, WarmLevel.SAVER.seatLevel, WarmLevel.SAVER.wheelLevel),
            gentleHours = hours(WarmLevel.GENTLE.averageCabinC, WarmLevel.GENTLE.seatLevel, WarmLevel.GENTLE.wheelLevel),
            freshMinutes = if (sealed) 0 else freshMinutes(n, a),
        )
    }

    fun freshMinutes(people: Int, a: CabinAssumptions = CabinAssumptions()): Int =
        ceil(people.coerceAtLeast(1) * a.freshAirLpsPerPerson / a.freshAirFlowLps * KeepWarmController.FRESH_CYCLE_MIN)
            .toInt()
            .coerceIn(1, KeepWarmController.FRESH_CYCLE_MIN)

    /** Seats to heat: the ones the car reports as occupied, otherwise the first [people] seats. */
    fun seatsToHeat(occupied: Set<Seat>?, people: Int): Set<Seat> =
        if (!occupied.isNullOrEmpty()) occupied
        else Seat.entries.take(people.coerceIn(1, Seat.entries.size)).toSet()
}

/** What the heater should be doing right now. */
data class ClimateTarget(
    val hvacOn: Boolean,
    val setpointC: Float,
    val fan: Int,
    /** Outside air instead of recirculation. */
    val freshAir: Boolean,
    val seatLevel: Int,
    val wheelLevel: Int,
)

/**
 * Decides the heater state every tick.
 *
 * - [WarmLevel.SAVER]: heat up to the setpoint, then leave the heater off until the cabin falls to
 *   [CYCLE_LOW_C]. Without a cabin temperature reading the heater simply holds the setpoint.
 * - Outside air is brought in for [freshMinutes] of every [FRESH_CYCLE_MIN] minutes so the air does
 *   not go stale; the blower has to run for that even while the heater is resting. With [sealed]
 *   the cabin stays on recirculation the whole time.
 */
class KeepWarmController(
    private val level: WarmLevel,
    private val sealed: Boolean,
    private val freshMinutes: Int,
) {
    private var heating = true

    fun next(cabinC: Double?, elapsedMs: Long): ClimateTarget {
        heating = when {
            level != WarmLevel.SAVER || cabinC == null -> true
            cabinC <= CYCLE_LOW_C -> true
            cabinC >= level.setpointC -> false
            else -> heating
        }
        val minuteInCycle = (elapsedMs / 60_000L % FRESH_CYCLE_MIN).toInt()
        val fresh = !sealed && minuteInCycle < freshMinutes
        return ClimateTarget(
            hvacOn = heating || fresh,
            setpointC = level.setpointC,
            fan = if (fresh) FAN_FRESH else FAN_HEAT,
            freshAir = fresh,
            seatLevel = level.seatLevel,
            wheelLevel = level.wheelLevel,
        )
    }

    companion object {
        const val CYCLE_LOW_C = 12.0
        const val FRESH_CYCLE_MIN = 10
        const val FAN_HEAT = 3
        const val FAN_FRESH = 4
        const val TICK_MS = 30_000L
    }
}
