package io.github.offlinemobility.app.core.light

import io.github.offlinemobility.app.core.protocol.RescueMessage

/** Exterior or interior lamp the app may switch through the vehicle SDK. */
enum class Lamp(val label: String) {
    HIGH_BEAM("상향등"),
    LOW_BEAM("하향등"),
    REAR_FOG("후방 안개등"),
    FRONT_FOG("전방 안개등"),
    TAIL_LAMP("미등"),
}

/** Mirrors the vehicle SDK switch values so the original state can be restored exactly. */
enum class LampSwitch { OFF, ON, DAYTIME_RUNNING, AUTOMATIC }

enum class LampGroup(val label: String) {
    FRONT("전방"),
    REAR("후방"),
    FRONT_AND_REAR("전후방"),
}

/** Which lamps flash with the pattern and which are held on underneath. */
data class LightPlan(val flash: List<Lamp>, val steady: List<Lamp>) {
    val isEmpty: Boolean get() = flash.isEmpty()
}

/**
 * Chooses lamps from what the vehicle can actually switch.
 *
 * Tail lamp and low beam share one headlamp switch (Off / Position / Low beam) on most cars, so the
 * tail lamp cannot flash while the low beam is held on. Most cars also light the high beam only
 * when the low beam is on.
 * - Rear fog available: flash high beam (front) and rear fog (rear), hold the low beam on.
 * - No rear fog: flash low beam together with high beam; the tail lamps follow the low beam,
 *   so the rear still signals.
 */
object LightPlanner {
    fun plan(group: LampGroup, writable: (Lamp) -> Boolean): LightPlan {
        val high = writable(Lamp.HIGH_BEAM)
        val low = writable(Lamp.LOW_BEAM)
        val rearFog = writable(Lamp.REAR_FOG)
        val tail = writable(Lamp.TAIL_LAMP)

        fun front(): LightPlan = when {
            high && low -> LightPlan(listOf(Lamp.HIGH_BEAM), listOf(Lamp.LOW_BEAM))
            high -> LightPlan(listOf(Lamp.HIGH_BEAM), emptyList())
            low -> LightPlan(listOf(Lamp.LOW_BEAM), emptyList())
            else -> LightPlan(emptyList(), emptyList())
        }

        return when (group) {
            LampGroup.FRONT -> front()
            LampGroup.REAR -> when {
                rearFog -> LightPlan(listOf(Lamp.REAR_FOG), emptyList())
                tail -> LightPlan(listOf(Lamp.TAIL_LAMP), emptyList())
                else -> LightPlan(emptyList(), emptyList())
            }
            LampGroup.FRONT_AND_REAR -> when {
                rearFog -> front().let { it.copy(flash = it.flash + Lamp.REAR_FOG) }
                low -> LightPlan(listOfNotNull(Lamp.LOW_BEAM, Lamp.HIGH_BEAM.takeIf { high }), emptyList())
                else -> front()
            }
        }
    }
}

data class LightStep(val on: Boolean, val durationMs: Long)

data class LightPattern(
    val id: String,
    val title: String,
    val description: String,
    val steps: List<LightStep>,
) {
    val totalMs: Long get() = steps.sumOf { it.durationMs }
    val onMs: Long get() = steps.filter { it.on }.sumOf { it.durationMs }
    val dutyCycle: Double get() = if (totalMs == 0L) 0.0 else onMs.toDouble() / totalMs
    val flashes: Int get() = steps.count { it.on }
}

/**
 * Human-readable light patterns based on established distress conventions.
 * Lamp switching goes through vehicle IPC, so every step is kept ≥ [MIN_STEP_MS]
 * (halogen lamps also need ~250 ms to reach 90 % output; Sivak et al., Ergonomics 1994).
 */
object LightPatterns {
    const val MIN_STEP_MS = 300L

    /**
     * Morse SOS with USCG electric distress light timing (46 CFR 161.013-7):
     * dot 1/3 s, dash 1 s, gap between flashes 1/3 s, between letters 2 s, between SOS 3 s.
     * One SOS takes 11 s, so a 14 s cycle (~4.3 per minute).
     */
    val SOS = LightPattern(
        id = "SOS",
        title = "SOS 조난 점멸",
        description = "··· — — — ··· · USCG 전자 조난등 타이밍",
        steps = buildList {
            val dot = LightStep(true, 333)
            val dash = LightStep(true, 1_000)
            val flashGap = LightStep(false, 333)
            fun letter(flash: LightStep) {
                repeat(3) { i ->
                    add(flash)
                    if (i < 2) add(flashGap)
                }
            }
            letter(dot)
            add(LightStep(false, 2_000))
            letter(dash)
            add(LightStep(false, 2_000))
            letter(dot)
            add(LightStep(false, 3_000))
        },
    )

    /** International Mountain Distress Signal: 6 signals in one minute, then one minute pause. */
    val DISTRESS_BEACON = LightPattern(
        id = "BEACON",
        title = "조난 비컨 (분당 6회)",
        description = "국제 산악 조난 신호 · 1분에 6회 점멸 후 1분 휴지",
        steps = List(6) { listOf(LightStep(true, 2_000), LightStep(false, 8_000)) }.flatten() +
            LightStep(false, 60_000),
    )

    /** Reply to a distress signal: 3 signals in one minute, then one minute pause. */
    val REPLY = LightPattern(
        id = "REPLY",
        title = "응답 신호 (분당 3회)",
        description = "국제 산악 조난 신호 응답 · 1분에 3회 점멸",
        steps = List(3) { listOf(LightStep(true, 2_000), LightStep(false, 18_000)) }.flatten() +
            LightStep(false, 60_000),
    )

    /**
     * Double flash every 2 s so approaching rescuers can pick out the exact vehicle.
     * Deliberately asymmetric: an even 1–2 Hz blink reads as ordinary hazard lights.
     */
    val GUIDE = LightPattern(
        id = "GUIDE",
        title = "위치 유도 (이중 점멸)",
        description = "접근하는 구조대에 차량 위치 표시 · 2초마다 두 번 점멸",
        steps = List(30) {
            listOf(LightStep(true, 300), LightStep(false, 300), LightStep(true, 300), LightStep(false, 1_100))
        }.flatten(),
    )

    val all = listOf(SOS, DISTRESS_BEACON, REPLY, GUIDE)

    fun forMessage(message: RescueMessage): LightPattern = when {
        message == RescueMessage.ACK -> REPLY
        message == RescueMessage.RESCUE_TEAM -> GUIDE
        message.critical -> SOS
        else -> DISTRESS_BEACON
    }
}
