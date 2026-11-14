package io.github.offlinemobility.app.core.online

import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.max

/** 시·도 and 시·군·구 taken from the address; [district] is empty where the 시·도 has none (세종). */
data class Area(val province: String, val district: String) {
    val label: String get() = if (district.isEmpty()) province else district
}

/**
 * Every 민방위대피소 in the country, shipped inside the app (assets/civil-shelters.json, copied from
 * the server's snapshot at build time). With it the app can point to a shelter even when it has
 * never had a connection — likely in a war, when networks fail first.
 */
class CivilShelters(
    /** Date of the public data the snapshot was taken from, e.g. "2026-11-13". */
    val generatedAt: String,
    val all: List<Shelter>,
) {
    private val byArea: Map<Area, List<Shelter>> = all.groupBy { areaOf(it.address) }

    /** 시·도 in a stable order, each with its 시·군·구. Rows whose address lacks a 시·도 are left out. */
    val areas: Map<String, List<String>> = byArea.keys
        .filter { it.province.isNotEmpty() }
        .groupBy({ it.province }, { it.district })
        .mapValues { (_, districts) -> districts.distinct().sorted() }
        .toSortedMap()

    /** Shelters within [km] of a point, nearest first. */
    fun near(latitude: Double, longitude: Double, km: Double, limit: Int): List<Shelter> {
        val dLat = km / 111.32
        val dLon = km / (111.32 * max(0.2, cos(Math.toRadians(latitude))))
        return all.asSequence()
            .filter { kotlin.math.abs(it.latitude - latitude) <= dLat && kotlin.math.abs(it.longitude - longitude) <= dLon }
            .map { it to Geo2.squaredDegrees(latitude, longitude, it.latitude, it.longitude) }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }

    /** Shelters in one 시·군·구, for when the car's position is unknown; ordered by address. */
    fun inArea(area: Area): List<Shelter> = byArea[area].orEmpty().sortedBy { it.address }

    companion object {
        const val KIND = "민방위대피소"

        /** Public shelters are planned at 0.825 ㎡ per person; the official capacity is not published. */
        const val M2_PER_PERSON = 0.825

        fun areaOf(address: String): Area {
            val parts = address.trim().split(Regex("\\s+"))
            // A few public rows start with a lot number instead of the 시·도 ("1476"): no area for those.
            val province = parts.getOrElse(0) { "" }.takeIf { it.endsWith("도") || it.endsWith("시") } ?: return Area("", "")
            val second = parts.getOrElse(1) { "" }
            val district = if (second.endsWith("시") || second.endsWith("군") || second.endsWith("구")) second else ""
            return Area(province, district)
        }

        /** Parses the snapshot: {"generatedAt", "rows": [[id, name, lat, lon, areaM2, address], ...]}. */
        fun parse(json: String): CivilShelters {
            val root = JSONObject(json)
            val rows = root.getJSONArray("rows")
            val list = ArrayList<Shelter>(rows.length())
            for (i in 0 until rows.length()) {
                val r = rows.getJSONArray(i)
                val area = r.optDouble(4, 0.0)
                list += Shelter(
                    id = "civil-${r.getString(0)}",
                    name = r.getString(1),
                    kind = KIND,
                    latitude = r.getDouble(2),
                    longitude = r.getDouble(3),
                    address = r.optString(5, ""),
                    capacity = if (area > 0) (area / M2_PER_PERSON).toInt() else null,
                )
            }
            return CivilShelters(root.optString("generatedAt", ""), list)
        }
    }
}

/** Cheap ordering distance for nearest-first sorting over thousands of rows (not metres). */
private object Geo2 {
    fun squaredDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = lat2 - lat1
        val dLon = (lon2 - lon1) * cos(Math.toRadians(lat1))
        return dLat * dLat + dLon * dLon
    }
}
