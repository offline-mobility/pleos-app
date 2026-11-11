package io.github.offlinemobility.app.core.gateway

import io.github.offlinemobility.app.core.protocol.GeoCell
import io.github.offlinemobility.app.core.protocol.RescueFrame
import io.github.offlinemobility.app.core.protocol.RescueMessage
import java.util.Locale

/**
 * A rescue message waiting to be forwarded to the rescue server once any network comes back.
 * Only protocol fields are kept — no free text — so nothing user-typed ever reaches the server.
 */
data class RescueReport(
    val senderId: Int,
    val sequence: Int,
    val message: RescueMessage,
    val people: Int,
    val hop: Int,
    val latQ: Int?,
    val lonQ: Int?,
    val heardAtMs: Long,
    /** True when this car sent the message itself. */
    val own: Boolean,
) {
    /** Same original message across relays and repeats. */
    val key: String get() = "%02x-%x".format(senderId, sequence)

    companion object {
        fun of(frame: RescueFrame, heardAtMs: Long, own: Boolean) = RescueReport(
            senderId = frame.senderId,
            sequence = frame.sequence,
            message = frame.message,
            people = frame.people,
            hop = frame.hop,
            latQ = frame.location?.latQ,
            lonQ = frame.location?.lonQ,
            heardAtMs = heardAtMs,
            own = own,
        )
    }
}

/** Compact line format for the encrypted on-device outbox (one report per line). */
object OutboxCodec {
    fun encode(reports: List<RescueReport>): String = reports.joinToString("\n") { r ->
        listOf(r.senderId, r.sequence, r.message.code, r.people, r.hop, r.latQ ?: -1, r.lonQ ?: -1, r.heardAtMs, if (r.own) 1 else 0)
            .joinToString(",")
    }

    /** Lines that do not parse are dropped rather than failing the whole outbox. */
    fun decode(text: String): List<RescueReport> = text.lineSequence().mapNotNull { line ->
        val f = line.split(",")
        if (f.size != 9) return@mapNotNull null
        runCatching {
            val lat = f[5].toInt()
            val lon = f[6].toInt()
            RescueReport(
                senderId = f[0].toInt().also { require(it in 0..255) },
                sequence = f[1].toInt().also { require(it in 0..15) },
                message = RescueMessage.fromCode(f[2].toInt())!!,
                people = f[3].toInt().also { require(it in 0..15) },
                hop = f[4].toInt().also { require(it in 0..RescueFrame.MAX_HOP) },
                latQ = lat.takeIf { it >= 0 }?.also { require(it <= GeoCell.LAT_MAX) },
                lonQ = lon.takeIf { it >= 0 }?.also { require(it <= GeoCell.LON_MAX) },
                heardAtMs = f[7].toLong(),
                own = f[8] == "1",
            )
        }.getOrNull()
    }.toList()
}

/**
 * Request body for the rescue server (see docs/GATEWAY_API.md). Every value is a number, a boolean
 * or a fixed enum name, so the JSON is built by hand without any escaping concerns.
 */
object UploadJson {
    fun body(reporterId: String, reports: List<RescueReport>): String {
        require(reporterId.matches(Regex("[0-9a-f-]{36}"))) { "reporterId must be a UUID" }
        val items = reports.joinToString(",") { r ->
            val location = if (r.latQ != null && r.lonQ != null) {
                val cell = GeoCell(r.latQ, r.lonQ)
                ""","lat":${fmt(cell.latitude)},"lon":${fmt(cell.longitude)}"""
            } else ""
            """{"id":"${r.key}","message":"${r.message.name}","people":${r.people},"hop":${r.hop},""" +
                """"own":${r.own},"heardAt":${r.heardAtMs}$location}"""
        }
        return """{"version":1,"reporter":"$reporterId","reports":[$items]}"""
    }

    private fun fmt(value: Double) = String.format(Locale.ROOT, "%.5f", value)
}
