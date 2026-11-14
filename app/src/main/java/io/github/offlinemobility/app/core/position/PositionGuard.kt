package io.github.offlinemobility.app.core.position

import io.github.offlinemobility.app.core.protocol.Geo
import io.github.offlinemobility.app.location.Fix

/**
 * Decides which GPS reading the app trusts.
 *
 * In a war GPS may be jammed (no reading) or spoofed (a false one). A reading that implies an
 * impossible move — more than [minJumpM] at more than [maxSpeedMps] since the last trusted one — is
 * not used; it is held as [suspect] until the person decides. If they keep the old position, later
 * readings near the rejected place are dropped quietly, so the question is not asked every few
 * seconds while the false signal lasts. A plausible reading near the trusted position clears that.
 *
 * Gradual spoofing is not caught; this guards against the jump, which is what sends people to the
 * wrong shelter.
 */
class PositionGuard(
    private val maxSpeedMps: Double = MAX_SPEED_MPS,
    private val minJumpM: Double = MIN_JUMP_M,
    private val rejectRadiusM: Double = REJECT_RADIUS_M,
) {
    var trusted: Fix? = null
        private set
    var suspect: Fix? = null
        private set
    private var rejected: Fix? = null

    /** Readings dropped because they sit near a place the person already rejected. */
    var ignoring: Boolean = false
        private set

    /** Starts from a position remembered from an earlier run; ignored once a live one is trusted. */
    fun restore(fix: Fix) {
        if (trusted == null) trusted = fix
    }

    fun offer(fix: Fix) {
        val last = trusted
        if (last == null) {
            trusted = fix
            return
        }
        rejected?.let { r ->
            if (distance(r, fix) <= rejectRadiusM) {
                ignoring = true
                return
            }
        }
        if (isJump(last, fix)) {
            suspect = fix
            return
        }
        trusted = fix
        suspect = null
        rejected = null
        ignoring = false
    }

    /** The person says the car really is at the new place. */
    fun useSuspect() {
        suspect?.let { trusted = it }
        suspect = null
        rejected = null
        ignoring = false
    }

    /** The person says the car has not moved: keep the old position and stop asking about this place. */
    fun keepTrusted() {
        rejected = suspect
        suspect = null
        ignoring = rejected != null
    }

    /** How far the held-back reading is from the trusted position, for the question shown to people. */
    fun suspectJumpM(): Double? {
        val s = suspect ?: return null
        val t = trusted ?: return null
        return distance(t, s)
    }

    private fun isJump(from: Fix, to: Fix): Boolean {
        val meters = distance(from, to)
        if (meters <= minJumpM) return false
        val seconds = ((to.timeMs - from.timeMs).coerceAtLeast(1_000L)) / 1000.0
        return meters / seconds > maxSpeedMps
    }

    private fun distance(a: Fix, b: Fix) = Geo.offset(a.latitude, a.longitude, b.latitude, b.longitude).meters

    companion object {
        /** About 250 km/h: faster than any car on a Korean road. */
        const val MAX_SPEED_MPS = 70.0
        /** Ordinary GPS wander and a short drive are never treated as a jump. */
        const val MIN_JUMP_M = 1_000.0
        const val REJECT_RADIUS_M = 2_000.0
    }
}

/**
 * Whether a position may still be sent as "where the car is now": recent, or taken while the car
 * was already parked and it has not moved since (a stranded car's last fix stays true for hours).
 */
fun positionStillValid(fix: Fix, nowMs: Long, parkedSinceMs: Long?, maxAgeMs: Long = 10 * 60_000L): Boolean =
    nowMs - fix.timeMs <= maxAgeMs || (parkedSinceMs != null && parkedSinceMs <= fix.timeMs)
