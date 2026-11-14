package io.github.offlinemobility.app.core.online

import kotlin.math.cos

/**
 * Kinds of places listed under "주변 시설". Shelters come from [CivilShelters] and the server; the
 * others are shipped inside the app as assets/facilities/<asset>.tsv (copied from the server's
 * snapshot at build time), so they can be listed with no connection.
 */
enum class FacilityKind(val label: String, val asset: String?) {
    SHELTER("대피소", null),
    PHARMACY("약국", "pharmacy"),
    HOSPITAL("종합병원", "hospital"),
    STORE("편의점", "store"),
}

/** A place a person may want to drive or walk to. */
data class Place(
    val name: String,
    val kind: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val capacity: Int? = null,
)

fun Shelter.toPlace() = Place(name, kind, latitude, longitude, address, capacity)

/**
 * Public files name some 시·도 differently: 강원도 / 강원특별자치도, 전라북도 / 전북특별자치도, and
 * 광주광역시 · 전라남도 / 전남광주통합특별시. An area picked from one file has to find places in another.
 */
internal fun provinceKey(province: String): String = when (province) {
    "강원도", "강원특별자치도" -> "강원"
    "전라북도", "전북특별자치도" -> "전북"
    "광주광역시", "전라남도", "전남광주통합특별시" -> "전남광주"
    else -> province
}

internal fun sameArea(a: Area, b: Area): Boolean = a.district == b.district && provinceKey(a.province) == provinceKey(b.province)

/**
 * Reads a facility file: one place per line, "lat<TAB>lon<TAB>name<TAB>address". The files hold tens
 * of thousands of lines, so they are read as a stream and only the few places asked for are kept.
 */
object FacilityFile {
    fun parse(line: String, kind: FacilityKind): Place? {
        val parts = line.split('\t', limit = 4)
        if (parts.size < 3) return null
        val latitude = parts[0].toDoubleOrNull() ?: return null
        val longitude = parts[1].toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0 || parts[2].isBlank()) return null
        return Place(parts[2], kind.label, latitude, longitude, parts.getOrElse(3) { "" })
    }

    /** The [limit] places nearest to a point, nearest first. */
    fun nearest(lines: Sequence<String>, kind: FacilityKind, latitude: Double, longitude: Double, limit: Int): List<Place> {
        val lonScale = cos(Math.toRadians(latitude))
        // Kept sorted by distance; with a small limit that is cheaper than sorting every line.
        val best = ArrayList<Pair<Double, Place>>(limit + 1)
        for (line in lines) {
            val place = parse(line, kind) ?: continue
            val dLat = place.latitude - latitude
            val dLon = (place.longitude - longitude) * lonScale
            val d = dLat * dLat + dLon * dLon
            if (best.size == limit && d >= best.last().first) continue
            val at = best.indexOfFirst { it.first > d }.let { if (it < 0) best.size else it }
            best.add(at, d to place)
            if (best.size > limit) best.removeAt(best.size - 1)
        }
        return best.map { it.second }
    }

    /** Places in one 시·군·구, for when the car's position is unknown; ordered by address. */
    fun inArea(lines: Sequence<String>, kind: FacilityKind, area: Area, limit: Int): List<Place> =
        lines.mapNotNull { parse(it, kind) }
            .filter { sameArea(CivilShelters.areaOf(it.address), area) }
            .sortedBy { it.address }
            .take(limit)
            .toList()
}
