package io.github.offlinemobility.app.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RescueFrameCodecTest {

    @Test
    fun crc8MatchesStandardCheckValue() {
        // CRC-8/SMBUS check value for "123456789" is 0xF4.
        assertEquals(0xF4, RescueFrameCodec.crc8("123456789".toByteArray()))
    }

    @Test
    fun everyMessageRoundTrips() {
        for (message in RescueMessage.entries) {
            val frame = RescueFrame(message, people = 15, senderId = 0xA5, sequence = 9, hop = 2)
            val symbols = RescueFrameCodec.encode(frame)
            assertEquals(14, symbols.size)
            assertEquals(frame, RescueFrameCodec.decode(symbols)!!.frame)
        }
    }

    @Test
    fun messageCodesAreUnique() {
        val codes = RescueMessage.entries.map { it.code }
        assertEquals(codes.size, codes.distinct().size)
    }

    @Test
    fun correctsChannelErrors() {
        val frame = RescueFrame(RescueMessage.MEDICAL, people = 3, senderId = 17, sequence = 1)
        val symbols = RescueFrameCodec.encode(frame)
        symbols[0] = symbols[0] xor 0x5
        symbols[7] = symbols[7] xor 0x9
        symbols[13] = symbols[13] xor 0x1
        val decoded = RescueFrameCodec.decode(symbols)!!
        assertEquals(frame, decoded.frame)
        assertEquals(3, decoded.correctedSymbols)
    }

    @Test
    fun constantToneSequencesNeverDecode() {
        // Silence or a single stuck tone demodulates to a constant symbol sequence.
        for (value in 0..15) {
            val constant = IntArray(14) { value }
            assertNull("constant $value", RescueFrameCodec.decode(constant))
            for (erasures in listOf(2, 4, 6)) {
                assertNull("constant $value / $erasures erasures", RescueFrameCodec.decode(constant, IntArray(erasures) { it }))
            }
        }
    }

    @Test
    fun relayIncrementsHopUntilLimit() {
        var frame: RescueFrame? = RescueFrame(RescueMessage.SOS, 1, 1, 1)
        repeat(3) { frame = frame!!.relayed() }
        assertEquals(3, frame!!.hop)
        assertNull(frame!!.relayed())
    }
}
