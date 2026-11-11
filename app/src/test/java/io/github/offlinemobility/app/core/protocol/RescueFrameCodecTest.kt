package io.github.offlinemobility.app.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class RescueFrameCodecTest {
    private val gangnam = GeoCell.of(37.4979, 127.0276)

    @Test
    fun crc8MatchesStandardCheckValue() {
        // CRC-8/SMBUS check value for "123456789" is 0xF4.
        assertEquals(0xF4, RescueFrameCodec.crc8("123456789".toByteArray()))
    }

    @Test
    fun everyMessageRoundTripsWithAndWithoutLocation() {
        for (message in RescueMessage.entries) {
            for (location in listOf(null, gangnam)) {
                val frame = RescueFrame(message, people = 15, senderId = 0xA5, sequence = 9, hop = 2, location = location)
                val symbols = RescueFrameCodec.encode(frame)
                assertEquals(RescueFrameCodec.FRAME_SYMBOLS, symbols.size)
                assertEquals(frame, RescueFrameCodec.decode(symbols)!!.frame)
            }
        }
    }

    @Test
    fun messageCodesAreUniqueAndFitFiveBits() {
        val codes = RescueMessage.entries.map { it.code }
        assertEquals(codes.size, codes.distinct().size)
        assertTrue(codes.all { it in 0..31 })
    }

    @Test
    fun locationGridIsAboutFortyMetres() {
        val random = Random(3)
        repeat(200) {
            val lat = random.nextDouble(-89.0, 89.0)
            val lon = random.nextDouble(-179.0, 179.0)
            val cell = GeoCell.of(lat, lon)
            val error = Geo.offset(lat, lon, cell.latitude, cell.longitude).meters
            assertTrue("error $error m at $lat,$lon", error < 30)
        }
    }

    @Test
    fun correctsThreeErrorsInEachCodeword() {
        val frame = RescueFrame(RescueMessage.MEDICAL, people = 3, senderId = 17, sequence = 1, location = gangnam)
        val symbols = RescueFrameCodec.encode(frame)
        // Even positions belong to codeword A, odd to codeword B.
        for (i in listOf(0, 4, 8, 1, 5, 9)) symbols[i] = symbols[i] xor 0x6
        val decoded = RescueFrameCodec.decode(symbols)!!
        assertEquals(frame, decoded.frame)
        assertEquals(6, decoded.correctedSymbols)
    }

    @Test
    fun interleavingSurvivesABurstOfSixSymbols() {
        val frame = RescueFrame(RescueMessage.TRAPPED, 2, 0x31, 4, location = gangnam)
        val symbols = RescueFrameCodec.encode(frame)
        for (i in 10 until 16) symbols[i] = symbols[i] xor 0xF
        assertEquals(frame, RescueFrameCodec.decode(symbols)!!.frame)
    }

    @Test
    fun lowConfidenceSymbolsAreErased() {
        val frame = RescueFrame(RescueMessage.WATER, 1, 0x40, 2)
        val symbols = RescueFrameCodec.encode(frame)
        val confidence = DoubleArray(symbols.size) { 50.0 }
        // Five wrong symbols in codeword A (its last five positions) exceed plain error correction
        // (t = 3) but are marked unreliable, so GMD erases them.
        for (i in listOf(20, 22, 24, 26, 28)) {
            symbols[i] = symbols[i] xor 0x3
            confidence[i] = 1.1
        }
        assertNotNull(RescueFrameCodec.decode(symbols, confidence))
        assertNull(RescueFrameCodec.decode(symbols))
    }

    @Test
    fun constantToneSequencesNeverDecode() {
        for (value in 0..15) {
            val constant = IntArray(RescueFrameCodec.FRAME_SYMBOLS) { value }
            assertNull("constant $value", RescueFrameCodec.decode(constant))
            val weak = DoubleArray(constant.size) { if (it % 5 == 0) 0.0 else 1.5 }
            assertNull("constant $value with erasures", RescueFrameCodec.decode(constant, weak))
        }
    }

    @Test
    fun relayIncrementsHopUntilLimitAndKeepsLocation() {
        var frame: RescueFrame? = RescueFrame(RescueMessage.SOS, 1, 1, 1, location = gangnam)
        repeat(3) { frame = frame!!.relayed() }
        assertEquals(3, frame!!.hop)
        assertEquals(gangnam, frame!!.location)
        assertNull(frame!!.relayed())
    }

    @Test
    fun compassAndDistanceWords() {
        val north = Geo.offset(37.0, 127.0, 37.01, 127.0)
        assertTrue(abs(north.meters - 1_112) < 5)
        assertEquals("북쪽", Geo.compassWord(north.bearingDeg))
        assertEquals("동쪽", Geo.compassWord(Geo.offset(37.0, 127.0, 37.0, 127.01).bearingDeg))
        assertEquals("남서쪽", Geo.compassWord(225.0))
        assertEquals("약 300m", Geo.distanceWord(310.0))
        assertEquals("약 1.1km", Geo.distanceWord(1_112.0))
        assertEquals("아주 가까이", Geo.distanceWord(20.0))
    }
}
