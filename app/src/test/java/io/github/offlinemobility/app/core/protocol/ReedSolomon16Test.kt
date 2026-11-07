package io.github.offlinemobility.app.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ReedSolomon16Test {
    private val rs = ReedSolomon16(6)
    private val random = Random(42)

    private fun randomData() = IntArray(8) { random.nextInt(16) }

    private fun corrupt(word: IntArray, positions: List<Int>): IntArray {
        val out = word.copyOf()
        for (p in positions) out[p] = out[p] xor (1 + random.nextInt(15))
        return out
    }

    @Test
    fun cleanCodewordDecodesWithoutCorrection() {
        val data = randomData()
        val word = rs.encode(data)
        val result = rs.decode(word)
        assertNotNull(result)
        assertArrayEquals(word, result!!.codeword)
        assertEquals(0, result.corrected)
    }

    @Test
    fun correctsUpToThreeSymbolErrors() {
        repeat(500) {
            val word = rs.encode(randomData())
            val errors = (0 until 14).shuffled(random).take(1 + random.nextInt(3))
            val result = rs.decode(corrupt(word, errors))
            assertNotNull("errors at $errors", result)
            assertArrayEquals(word, result!!.codeword)
        }
    }

    @Test
    fun correctsSixErasures() {
        repeat(300) {
            val word = rs.encode(randomData())
            val erasures = (0 until 14).shuffled(random).take(6)
            val result = rs.decode(corrupt(word, erasures), erasures.toIntArray())
            assertNotNull(result)
            assertArrayEquals(word, result!!.codeword)
        }
    }

    @Test
    fun correctsMixedErrorsAndErasures() {
        repeat(300) {
            val word = rs.encode(randomData())
            val positions = (0 until 14).shuffled(random)
            val erasures = positions.take(4)
            val errors = positions.drop(4).take(1)
            val result = rs.decode(corrupt(word, erasures + errors), erasures.toIntArray())
            assertNotNull(result)
            assertArrayEquals(word, result!!.codeword)
        }
    }

    @Test
    fun rejectsOrMiscorrectsBeyondCapabilityButNeverCrashes() {
        var rejected = 0
        repeat(300) {
            val word = rs.encode(randomData())
            val errors = (0 until 14).shuffled(random).take(5)
            val result = rs.decode(corrupt(word, errors))
            if (result == null) rejected++
        }
        assertTrue("most 5-error words should be rejected, rejected=$rejected", rejected > 150)
    }

    @Test
    fun rejectsTooManyErasures() {
        val word = rs.encode(randomData())
        assertNull(rs.decode(word, IntArray(7) { it }))
    }
}
