package io.github.offlinemobility.app.core

import io.github.offlinemobility.app.core.gateway.OutboxCodec
import io.github.offlinemobility.app.core.gateway.RescueReport
import io.github.offlinemobility.app.core.gateway.UploadJson
import io.github.offlinemobility.app.core.protocol.GeoCell
import io.github.offlinemobility.app.core.protocol.RescueFrame
import io.github.offlinemobility.app.core.protocol.RescueMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayFormatTest {
    private val withLocation = RescueReport.of(
        RescueFrame(RescueMessage.MEDICAL, 2, 0xA1, 3, hop = 1, location = GeoCell.of(37.4979, 127.0276)),
        heardAtMs = 1_794_000_000_000,
        own = false,
    )
    private val withoutLocation = RescueReport.of(RescueFrame(RescueMessage.WATER, 0, 0x05, 15), 1_794_000_100_000, own = true)

    @Test
    fun outboxRoundTrips() {
        val reports = listOf(withLocation, withoutLocation)
        assertEquals(reports, OutboxCodec.decode(OutboxCodec.encode(reports)))
    }

    @Test
    fun corruptLinesAreDroppedNotFatal() {
        val text = OutboxCodec.encode(listOf(withLocation)) + "\n9,9,99,1,0,-1,-1,1,0\ngarbage\n1,2,3"
        assertEquals(listOf(withLocation), OutboxCodec.decode(text))
    }

    @Test
    fun uploadBodyIsCompactJsonWithRoundedCoordinates() {
        val body = UploadJson.body("123e4567-e89b-12d3-a456-426614174000", listOf(withLocation, withoutLocation))
        assertTrue(body.startsWith("""{"version":1,"reporter":"123e4567-e89b-12d3-a456-426614174000","reports":["""))
        assertTrue(body.contains(""""id":"a1-3","message":"MEDICAL","people":2,"hop":1,"own":false,"heardAt":1794000000000,"lat":37.49"""))
        // Coordinates come from the ≈40 m grid, five decimals.
        assertTrue(Regex(""""lat":37\.49\d{3},"lon":127\.02\d{3}""").containsMatchIn(body))
        assertTrue(body.contains(""""id":"05-f","message":"WATER","people":0,"hop":0,"own":true,"heardAt":1794000100000}"""))
        assertEquals(body.count { it == '{' }, body.count { it == '}' })
    }

    @Test(expected = IllegalArgumentException::class)
    fun reporterIdMustBeAUuid() {
        UploadJson.body("\"},{\"evil\":1", listOf(withLocation))
    }
}
