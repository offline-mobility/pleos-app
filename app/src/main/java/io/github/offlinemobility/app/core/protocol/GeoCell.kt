package io.github.offlinemobility.app.core.protocol

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A position quantized to the on-air grid: latitude 19 bits (≈38 m), longitude 20 bits
 * (≈31 m at 36°N). Storing the quantized integers keeps round trips exact.
 */
data class GeoCell(val latQ: Int, val lonQ: Int) {
    init {
        require(latQ in 0..LAT_MAX) { "latQ out of range" }
        require(lonQ in 0..LON_MAX) { "lonQ out of range" }
    }

    val latitude: Double get() = latQ.toDouble() / LAT_MAX * 180.0 - 90.0
    val longitude: Double get() = lonQ.toDouble() / (LON_MAX + 1) * 360.0 - 180.0

    companion object {
        const val LAT_BITS = 19
        const val LON_BITS = 20

        /** Highest latitude step; the all-ones value is reserved for "no location". */
        const val LAT_MAX = (1 shl LAT_BITS) - 2
        const val LAT_NONE = (1 shl LAT_BITS) - 1
        const val LON_MAX = (1 shl LON_BITS) - 1

        fun of(latitude: Double, longitude: Double): GeoCell {
            require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "invalid coordinate" }
            val latQ = ((latitude + 90.0) / 180.0 * LAT_MAX).roundToInt().coerceIn(0, LAT_MAX)
            val lonQ = (((longitude + 180.0) / 360.0 * (LON_MAX + 1)).roundToInt()) and LON_MAX
            return GeoCell(latQ, lonQ)
        }
    }
}

/** Distance in metres and initial bearing in degrees (0 = north) from [from] to [to]. */
data class Offset(val meters: Double, val bearingDeg: Double)

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun offset(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Offset {
        val p1 = Math.toRadians(fromLat)
        val p2 = Math.toRadians(toLat)
        val dp = p2 - p1
        val dl = Math.toRadians(toLon - fromLon)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        val meters = 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        val bearing = (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
        return Offset(meters, bearing)
    }

    /** Eight-way Korean compass word for a bearing. */
    fun compassWord(bearingDeg: Double): String {
        val words = listOf("북쪽", "북동쪽", "동쪽", "남동쪽", "남쪽", "남서쪽", "서쪽", "북서쪽")
        val index = (((bearingDeg % 360 + 360) % 360 + 22.5) / 45.0).toInt() % 8
        return words[index]
    }

    /** "약 300m" / "약 1.2km", rounded to what the ≈40 m grid can honestly claim. */
    fun distanceWord(meters: Double): String = when {
        meters < 50 -> "아주 가까이"
        meters < 1_000 -> "약 ${((meters / 50).roundToInt() * 50)}m"
        meters < 10_000 -> "약 ${"%.1f".format(meters / 1_000)}km"
        else -> "약 ${(meters / 1_000).roundToInt()}km"
    }
}
