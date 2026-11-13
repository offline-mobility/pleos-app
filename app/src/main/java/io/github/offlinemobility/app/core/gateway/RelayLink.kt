package io.github.offlinemobility.app.core.gateway

import io.github.offlinemobility.app.core.protocol.RescueFrame
import io.github.offlinemobility.app.core.protocol.RescueMessage
import java.util.Locale

/**
 * Link shown as a QR code so a phone without the app can carry a rescue message to the server:
 * the phone opens the link wherever it has a connection and the server's page submits the report
 * (server/public/relay.js parses the same parameters).
 *
 * Only protocol fields go into the link — no free text.
 */
object RelayLink {
    fun url(serverBase: String, frame: RescueFrame, sentAtMs: Long): String? {
        if (serverBase.isBlank() || frame.message == RescueMessage.ACK) return null
        val location = frame.location?.let {
            "&la=${fmt(it.latitude)}&lo=${fmt(it.longitude)}"
        }.orEmpty()
        return serverBase.trimEnd('/') +
            "/r?m=${frame.message.name}&p=${frame.people}&i=${"%02x-%x".format(frame.senderId, frame.sequence)}" +
            "&h=${frame.hop}&t=${sentAtMs / 1000}$location"
    }

    private fun fmt(value: Double) = String.format(Locale.ROOT, "%.5f", value)
}
