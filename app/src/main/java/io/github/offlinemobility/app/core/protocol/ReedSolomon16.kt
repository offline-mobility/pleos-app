package io.github.offlinemobility.app.core.protocol

/**
 * Reed-Solomon code over GF(16) (primitive polynomial x^4 + x + 1, generator roots α^0..α^(nsym-1)).
 *
 * One code symbol is one nibble, which maps 1:1 to one 16-FSK tone of the acoustic modem.
 * Supports errors-and-erasures decoding: 2·errors + erasures ≤ nsym.
 *
 * Polynomials are stored highest degree first, as in the reference algorithm
 * (Wikiversity, "Reed–Solomon codes for coders").
 */
class ReedSolomon16(private val nsym: Int) {

    init {
        require(nsym in 1..14) { "nsym must be in 1..14" }
    }

    private val generator: IntArray = run {
        var g = intArrayOf(1)
        for (i in 0 until nsym) {
            g = polyMul(g, intArrayOf(1, Gf16.pow(2, i)))
        }
        g
    }

    /** Returns data followed by nsym parity symbols. Total length must be ≤ 15. */
    fun encode(data: IntArray): IntArray {
        require(data.size + nsym <= Gf16.ORDER) { "codeword longer than 15 symbols" }
        require(data.all { it in 0..15 }) { "symbols must be nibbles" }
        val padded = data + IntArray(nsym)
        val (_, remainder) = polyDiv(padded, generator)
        return data + remainder
    }

    /**
     * Corrects [received] in place of a copy. [erasures] are indices whose symbols are unreliable.
     * Returns the corrected codeword, or null when the errata exceed the code's capability.
     */
    fun decode(received: IntArray, erasures: IntArray = IntArray(0)): DecodeResult? {
        if (erasures.size > nsym) return null
        val msg = received.copyOf()
        for (pos in erasures) msg[pos] = 0

        val synd = syndromes(msg)
        if (synd.all { it == 0 }) return DecodeResult(msg, corrected = 0)

        val fsynd = forneySyndromes(synd, erasures, msg.size)
        val errLoc = findErrorLocator(fsynd, erasures.size) ?: return null
        val errPos = findErrors(errLoc.reversedArray(), msg.size) ?: return null

        val allPos = erasures + errPos
        val corrected = correctErrata(msg, synd, allPos) ?: return null
        if (syndromes(corrected).any { it != 0 }) return null

        val changed = corrected.indices.count { corrected[it] != received[it] }
        return DecodeResult(corrected, corrected = changed)
    }

    data class DecodeResult(val codeword: IntArray, val corrected: Int) {
        override fun equals(other: Any?): Boolean =
            other is DecodeResult && codeword.contentEquals(other.codeword) && corrected == other.corrected

        override fun hashCode(): Int = 31 * codeword.contentHashCode() + corrected
    }

    // Syndromes with a leading 0 pad, as in the reference implementation.
    private fun syndromes(msg: IntArray): IntArray {
        val s = IntArray(nsym + 1)
        for (i in 0 until nsym) s[i + 1] = polyEval(msg, Gf16.pow(2, i))
        return s
    }

    private fun forneySyndromes(synd: IntArray, erasePos: IntArray, n: Int): IntArray {
        val fsynd = synd.copyOfRange(1, synd.size)
        for (p in erasePos) {
            val x = Gf16.pow(2, n - 1 - p)
            for (j in 0 until fsynd.size - 1) {
                fsynd[j] = Gf16.mul(fsynd[j], x) xor fsynd[j + 1]
            }
        }
        return fsynd
    }

    private fun findErrorLocator(synd: IntArray, eraseCount: Int): IntArray? {
        var errLoc = intArrayOf(1)
        var oldLoc = intArrayOf(1)
        val syndShift = if (synd.size > nsym) synd.size - nsym else 0

        for (i in 0 until nsym - eraseCount) {
            val k = i + syndShift
            var delta = synd[k]
            for (j in 1 until errLoc.size) {
                if (k - j < 0) break
                delta = delta xor Gf16.mul(errLoc[errLoc.size - 1 - j], synd[k - j])
            }
            oldLoc += 0
            if (delta != 0) {
                if (oldLoc.size > errLoc.size) {
                    val newLoc = polyScale(oldLoc, delta)
                    oldLoc = polyScale(errLoc, Gf16.inverse(delta))
                    errLoc = newLoc
                }
                errLoc = polyAdd(errLoc, polyScale(oldLoc, delta))
            }
        }
        val firstNonZero = errLoc.indexOfFirst { it != 0 }
        if (firstNonZero < 0) return null
        errLoc = errLoc.copyOfRange(firstNonZero, errLoc.size)
        val errs = errLoc.size - 1
        if (errs * 2 + eraseCount > nsym) return null
        return errLoc
    }

    private fun findErrors(errLoc: IntArray, n: Int): IntArray? {
        val errs = errLoc.size - 1
        val positions = ArrayList<Int>()
        for (i in 0 until n) {
            if (polyEval(errLoc, Gf16.pow(2, i)) == 0) positions += n - 1 - i
        }
        if (positions.size != errs) return null
        return positions.toIntArray()
    }

    private fun correctErrata(msg: IntArray, synd: IntArray, errPos: IntArray): IntArray? {
        val coefPos = errPos.map { msg.size - 1 - it }
        var errLoc = intArrayOf(1)
        for (p in coefPos) errLoc = polyMul(errLoc, polyAdd(intArrayOf(1), intArrayOf(Gf16.pow(2, p), 0)))

        val reversedSynd = synd.reversedArray()
        val (_, rem) = polyDiv(polyMul(reversedSynd, errLoc), intArrayOf(1) + IntArray(errLoc.size))
        val errEval = rem.reversedArray()

        val xs = coefPos.map { Gf16.pow(2, it) }
        val e = IntArray(msg.size)
        for ((i, xi) in xs.withIndex()) {
            val xiInv = Gf16.inverse(xi)
            var errLocPrime = 1
            for ((j, xj) in xs.withIndex()) {
                if (j != i) errLocPrime = Gf16.mul(errLocPrime, 1 xor Gf16.mul(xiInv, xj))
            }
            if (errLocPrime == 0) return null
            val y = Gf16.mul(xi, polyEval(errEval.reversedArray(), xiInv))
            e[errPos[i]] = Gf16.div(y, errLocPrime)
        }
        return IntArray(msg.size) { msg[it] xor e[it] }
    }

    private fun polyScale(p: IntArray, x: Int) = IntArray(p.size) { Gf16.mul(p[it], x) }

    private fun polyAdd(p: IntArray, q: IntArray): IntArray {
        val r = IntArray(maxOf(p.size, q.size))
        for (i in p.indices) r[i + r.size - p.size] = p[i]
        for (i in q.indices) r[i + r.size - q.size] = r[i + r.size - q.size] xor q[i]
        return r
    }

    private fun polyMul(p: IntArray, q: IntArray): IntArray {
        val r = IntArray(p.size + q.size - 1)
        for (j in q.indices) for (i in p.indices) r[i + j] = r[i + j] xor Gf16.mul(p[i], q[j])
        return r
    }

    private fun polyEval(p: IntArray, x: Int): Int {
        var y = p[0]
        for (i in 1 until p.size) y = Gf16.mul(y, x) xor p[i]
        return y
    }

    /** Synthetic division by a monic divisor. Returns (quotient, remainder). */
    private fun polyDiv(dividend: IntArray, divisor: IntArray): Pair<IntArray, IntArray> {
        val out = dividend.copyOf()
        for (i in 0 until dividend.size - (divisor.size - 1)) {
            val coef = out[i]
            if (coef != 0) {
                for (j in 1 until divisor.size) {
                    if (divisor[j] != 0) out[i + j] = out[i + j] xor Gf16.mul(divisor[j], coef)
                }
            }
        }
        val sep = out.size - (divisor.size - 1)
        return out.copyOfRange(0, sep) to out.copyOfRange(sep, out.size)
    }
}

internal object Gf16 {
    const val ORDER = 15
    private const val PRIMITIVE = 0x13 // x^4 + x + 1

    private val exp = IntArray(ORDER * 2)
    private val log = IntArray(16)

    init {
        var x = 1
        for (i in 0 until ORDER) {
            exp[i] = x
            log[x] = i
            x = x shl 1
            if (x and 0x10 != 0) x = x xor PRIMITIVE
        }
        for (i in ORDER until ORDER * 2) exp[i] = exp[i - ORDER]
    }

    fun mul(a: Int, b: Int): Int = if (a == 0 || b == 0) 0 else exp[log[a] + log[b]]

    fun div(a: Int, b: Int): Int {
        require(b != 0) { "division by zero" }
        if (a == 0) return 0
        return exp[(log[a] + ORDER - log[b]) % ORDER]
    }

    fun pow(a: Int, power: Int): Int {
        if (a == 0) return 0
        val e = ((log[a] * power) % ORDER + ORDER) % ORDER
        return exp[e]
    }

    fun inverse(a: Int): Int = exp[ORDER - log[a]]
}
