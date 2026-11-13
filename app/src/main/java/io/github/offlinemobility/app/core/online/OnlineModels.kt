package io.github.offlinemobility.app.core.online

import io.github.offlinemobility.app.core.disaster.DisasterType
import io.github.offlinemobility.app.core.protocol.Geo
import io.github.offlinemobility.app.core.protocol.GeoCell
import io.github.offlinemobility.app.core.protocol.Offset
import io.github.offlinemobility.app.core.protocol.RescueFrame
import io.github.offlinemobility.app.core.protocol.RescueMessage
import org.json.JSONArray
import org.json.JSONObject

/** Official disaster message (행정안전부 긴급재난문자) relayed by the rescue server. */
data class OfficialAlert(
    val id: String,
    val createdAtMs: Long,
    val text: String,
    val level: String?,
    /** Disaster mode the message maps to, when there is one. */
    val type: DisasterType?,
)

data class Shelter(
    val id: String,
    val name: String,
    val kind: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val capacity: Int?,
)

/** Rescue request known to the server (uploaded by other cars). */
data class RemoteIncident(
    val uid: String,
    val frame: RescueFrame,
    val lastHeardAtMs: Long,
    val confirmations: Int,
)

/** "live", "sample" (development server only) or "unconfigured". */
data class Sourced<T>(val source: String, val items: List<T>)

/**
 * Parsers for rescue-server responses. The server is treated as untrusted input: every value is
 * range-checked, text is length-capped, and entries that do not validate are skipped.
 */
object OnlineJson {
    private const val MAX_ITEMS = 300

    fun alerts(text: String): Sourced<OfficialAlert>? = parse(text) { root ->
        Sourced(
            root.optString("source"),
            root.optJSONArray("alerts").objects().mapNotNull { o ->
                val id = o.optString("id").take(64)
                val body = o.optString("text").take(500)
                val created = o.optLong("createdAt", -1)
                if (id.isEmpty() || body.isEmpty() || created <= 0) return@mapNotNull null
                val type = o.optString("type").let { name -> DisasterType.entries.firstOrNull { it.name == name } }
                OfficialAlert(id, created, body, o.optString("level").take(20).ifEmpty { null }, type)
            },
        )
    }

    fun shelters(text: String): Sourced<Shelter>? = parse(text) { root ->
        Sourced(
            root.optString("source"),
            root.optJSONArray("shelters").objects().mapNotNull { o ->
                val lat = o.optDouble("lat", Double.NaN)
                val lon = o.optDouble("lon", Double.NaN)
                val name = o.optString("name").take(80)
                if (name.isEmpty() || !validPoint(lat, lon)) return@mapNotNull null
                Shelter(
                    id = o.optString("id").take(64),
                    name = name,
                    kind = o.optString("kind").take(40),
                    latitude = lat,
                    longitude = lon,
                    address = o.optString("address").take(120),
                    capacity = o.optInt("capacity", -1).takeIf { it > 0 },
                )
            },
        )
    }

    fun incidents(text: String): List<RemoteIncident>? = parse(text) { root ->
        root.optJSONArray("incidents").objects().mapNotNull { o ->
            val id = Regex("^([0-9a-f]{2})-([0-9a-f])$").matchEntire(o.optString("id")) ?: return@mapNotNull null
            val message = RescueMessage.entries.firstOrNull { it.name == o.optString("message") } ?: return@mapNotNull null
            if (message == RescueMessage.ACK) return@mapNotNull null
            val lat = o.optDouble("lat", Double.NaN)
            val lon = o.optDouble("lon", Double.NaN)
            val heard = o.optLong("lastHeardAt", -1)
            val people = o.optInt("people", -1)
            val hop = o.optInt("hop", -1)
            if (heard <= 0 || people !in 0..15 || hop !in 0..RescueFrame.MAX_HOP) return@mapNotNull null
            RemoteIncident(
                uid = o.optString("uid").take(64),
                frame = RescueFrame(
                    message = message,
                    people = people,
                    senderId = id.groupValues[1].toInt(16),
                    sequence = id.groupValues[2].toInt(16),
                    hop = hop,
                    location = if (validPoint(lat, lon)) GeoCell.of(lat, lon) else null,
                ),
                lastHeardAtMs = heard,
                confirmations = o.optInt("confirmations", 1).coerceIn(1, 999),
            )
        }
    }

    private fun validPoint(lat: Double, lon: Double) =
        !lat.isNaN() && !lon.isNaN() && lat in -90.0..90.0 && lon in -180.0..180.0

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until minOf(length(), MAX_ITEMS)).mapNotNull { optJSONObject(it) }
    }

    private fun <T> parse(text: String, block: (JSONObject) -> T): T? = runCatching { block(JSONObject(text)) }.getOrNull()
}

object ShelterFinder {
    /**
     * Word in [Shelter.kind] for the shelters meant for a disaster: an underground civil defence
     * shelter in an air raid, an open assembly point after an earthquake, a staffed rest area in
     * extreme cold or heat. Null when any shelter will do.
     */
    fun preferredKind(disaster: DisasterType): String? = when (disaster) {
        DisasterType.AIR_RAID -> "민방위"
        DisasterType.EARTHQUAKE -> "지진옥외"
        DisasterType.SNOW_COLD -> "한파"
        DisasterType.HEAT -> "무더위"
        else -> null
    }

    /**
     * Nearest shelters with distance and bearing from the given point. Shelters whose kind contains
     * [preferKind] come first, however far, because the nearest shelter of the wrong kind can be the
     * wrong place to go.
     */
    fun nearest(
        shelters: List<Shelter>,
        latitude: Double,
        longitude: Double,
        limit: Int = 5,
        preferKind: String? = null,
    ): List<Pair<Shelter, Offset>> =
        shelters
            .map { it to Geo.offset(latitude, longitude, it.latitude, it.longitude) }
            .sortedWith(
                compareBy<Pair<Shelter, Offset>> { preferKind != null && !it.first.kind.contains(preferKind) }
                    .thenBy { it.second.meters },
            )
            .take(limit)
}
