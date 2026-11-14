package io.github.offlinemobility.app.core.protocol

/**
 * Predefined rescue messages. The code is the 5-bit value carried on air, so it must stay stable.
 */
enum class RescueMessage(val code: Int, val title: String, val critical: Boolean) {
    SOS(0, "SOS 긴급 요청", true),
    MEDICAL(1, "응급 환자 발생", true),
    TRAPPED(2, "고립·이동 불가", true),
    FIRE(3, "화재·연기", true),
    HAZARD(4, "가스·위험물 누출", true),
    FLOOD(5, "침수 위험", true),
    VULNERABLE(6, "노약자·어린이 동승", false),
    WATER(7, "식수·식량 필요", false),
    POWER(8, "연료·전력 부족", false),
    ROAD_BLOCKED(9, "전방 도로 차단", false),
    ROUTE_CLEAR(10, "통행 가능 경로", false),
    SHELTER(11, "대피소 확인", false),
    RESCUE_TEAM(12, "구조대 접근 중", false),
    ALL_SAFE(13, "탑승자 전원 안전", false),
    ACK(14, "수신 확인", false),
    COLD(15, "추위·저체온 위험", true),
    COLLAPSE(16, "붕괴·낙석", true),
    CBRN(17, "화생방", true),
    FOOD(18, "식량 필요", false),
    MEDICINE(19, "약 필요", false),
    ;

    companion object {
        fun fromCode(code: Int): RescueMessage? = entries.firstOrNull { it.code == code }

        /**
         * The eight messages people can send, in screen order, for one scene: a war evacuation
         * stuck in traffic with networks down. 식수 also stands for 식량.
         */
        val OFFERED: List<RescueMessage> = listOf(POWER, WATER, MEDICAL, COLD, MEDICINE, SOS, CBRN, FIRE)

        /**
         * Everything else (except the reply [ACK]) is no longer sent. Still decoded when an older
         * version sends it, so the codes stay reserved.
         */
        val RETIRED: Set<RescueMessage> = entries.toSet() - OFFERED.toSet() - ACK
    }
}

/**
 * One on-air message (protocol v1).
 *
 * | bits | field    | note                                               |
 * |------|----------|----------------------------------------------------|
 * | 2    | version  | 1                                                  |
 * | 5    | message  | [RescueMessage.code]                               |
 * | 4    | people   | 0 unknown, 1–14 exact, 15 means 15 or more         |
 * | 8    | senderId | random per app session, used for relay dedupe      |
 * | 4    | sequence | increments per new message from a sender           |
 * | 2    | hop      | 0 original, incremented by each relay (≤ 3)        |
 * | 19   | latitude | [GeoCell]; all ones = no location                  |
 * | 20   | longitude| [GeoCell]                                          |
 * | 8    | crc8     | CRC-8/SMBUS over the 64 bits above                 |
 *
 * 72 bits = 18 nibbles → two RS(15, 9) codewords over GF(16), interleaved symbol by symbol
 * (A0 B0 A1 B1 …) so a burst of noise is split between both codewords. 30 symbols on air.
 */
data class RescueFrame(
    val message: RescueMessage,
    val people: Int,
    val senderId: Int,
    val sequence: Int,
    val hop: Int = 0,
    val location: GeoCell? = null,
) {
    init {
        require(people in 0..15) { "people must be 0..15" }
        require(senderId in 0..255) { "senderId must be 0..255" }
        require(sequence in 0..15) { "sequence must be 0..15" }
        require(hop in 0..MAX_HOP) { "hop must be 0..$MAX_HOP" }
    }

    /** Key that identifies the same original message across relays. */
    val originKey: Int get() = (senderId shl 4) or sequence

    fun relayed(): RescueFrame? = if (hop >= MAX_HOP) null else copy(hop = hop + 1)

    companion object {
        const val MAX_HOP = 3
        const val VERSION = 1
    }
}

object RescueFrameCodec {
    private const val DATA_PER_CODEWORD = 9
    const val PARITY_SYMBOLS = 6
    private const val CODEWORD = DATA_PER_CODEWORD + PARITY_SYMBOLS
    private const val CODEWORDS = 2
    const val FRAME_SYMBOLS = CODEWORD * CODEWORDS

    private val rs = ReedSolomon16(PARITY_SYMBOLS)

    /**
     * XOR-ed onto every symbol on air. Without it, silence or a stuck tone (a constant symbol
     * sequence) could demodulate to a valid frame.
     */
    private val WHITENING = intArrayOf(
        0x9, 0x4, 0xE, 0x1, 0x7, 0xC, 0x2, 0xB, 0x5, 0xF, 0x8, 0x3, 0xD, 0x6, 0xA,
        0x0, 0xB, 0x6, 0x3, 0xE, 0x9, 0x1, 0xC, 0x4, 0xF, 0x7, 0x2, 0xD, 0x5, 0x8,
    )

    /** Frame → 30 nibbles ready for modulation. */
    fun encode(frame: RescueFrame): IntArray {
        var info = 0L
        info = (info shl 2) or RescueFrame.VERSION.toLong()
        info = (info shl 5) or frame.message.code.toLong()
        info = (info shl 4) or frame.people.toLong()
        info = (info shl 8) or frame.senderId.toLong()
        info = (info shl 4) or frame.sequence.toLong()
        info = (info shl 2) or frame.hop.toLong()
        info = (info shl GeoCell.LAT_BITS) or (frame.location?.latQ ?: GeoCell.LAT_NONE).toLong()
        info = (info shl GeoCell.LON_BITS) or (frame.location?.lonQ ?: 0).toLong()
        val bytes = ByteArray(8) { (info ushr (56 - it * 8)).toByte() }
        val crc = crc8(bytes)

        val nibbles = IntArray(CODEWORDS * DATA_PER_CODEWORD)
        for (i in 0 until 16) nibbles[i] = ((info ushr (60 - i * 4)) and 0xF).toInt()
        nibbles[16] = crc ushr 4
        nibbles[17] = crc and 0xF

        val a = rs.encode(nibbles.copyOfRange(0, DATA_PER_CODEWORD))
        val b = rs.encode(nibbles.copyOfRange(DATA_PER_CODEWORD, 2 * DATA_PER_CODEWORD))
        return IntArray(FRAME_SYMBOLS) { i ->
            val symbol = if (i % 2 == 0) a[i / 2] else b[i / 2]
            symbol xor WHITENING[i]
        }
    }

    /**
     * 30 nibbles → frame.
     *
     * [confidence] is the demodulator's per-symbol reliability (higher is better, 0 = no signal).
     * Each codeword is decoded with generalized minimum distance: retry with its 0, 2, 4, 6 least
     * reliable symbols as erasures. Symbols with confidence 0 are always erased.
     * Returns null when either codeword cannot be corrected or the CRC does not match.
     */
    fun decode(symbols: IntArray, confidence: DoubleArray? = null): Decoded? {
        if (symbols.size != FRAME_SYMBOLS) return null
        val conf = confidence ?: DoubleArray(FRAME_SYMBOLS) { Double.MAX_VALUE }
        val plain = IntArray(FRAME_SYMBOLS) { symbols[it] xor WHITENING[it] }

        // A codeword with more errors than it can fix may "correct" to the wrong word, so every
        // erasure level yields a candidate and the CRC picks the combination that is consistent.
        val candidates = (0 until CODEWORDS).map { cw ->
            val word = IntArray(CODEWORD) { plain[it * 2 + cw] }
            val wordConf = DoubleArray(CODEWORD) { conf[it * 2 + cw] }
            codewordCandidates(word, wordConf).ifEmpty { return null }
        }
        for (a in candidates[0]) {
            for (b in candidates[1]) {
                val frame = parse(a.codeword, b.codeword) ?: continue
                return Decoded(frame, a.corrected + b.corrected, a.erasures + b.erasures)
            }
        }
        return null
    }

    private fun parse(first: IntArray, second: IntArray): RescueFrame? {
        val data = IntArray(CODEWORDS * DATA_PER_CODEWORD)
        first.copyInto(data, 0, 0, DATA_PER_CODEWORD)
        second.copyInto(data, DATA_PER_CODEWORD, 0, DATA_PER_CODEWORD)

        var info = 0L
        for (i in 0 until 16) info = (info shl 4) or data[i].toLong()
        val crc = (data[16] shl 4) or data[17]
        val bytes = ByteArray(8) { (info ushr (56 - it * 8)).toByte() }
        if (crc8(bytes) != crc) return null

        var rest = info
        val lonQ = (rest and ((1L shl GeoCell.LON_BITS) - 1)).toInt(); rest = rest ushr GeoCell.LON_BITS
        val latQ = (rest and ((1L shl GeoCell.LAT_BITS) - 1)).toInt(); rest = rest ushr GeoCell.LAT_BITS
        val hop = (rest and 0x3).toInt(); rest = rest ushr 2
        val sequence = (rest and 0xF).toInt(); rest = rest ushr 4
        val senderId = (rest and 0xFF).toInt(); rest = rest ushr 8
        val people = (rest and 0xF).toInt(); rest = rest ushr 4
        val code = (rest and 0x1F).toInt(); rest = rest ushr 5
        val version = (rest and 0x3).toInt()
        if (version != RescueFrame.VERSION) return null
        val message = RescueMessage.fromCode(code) ?: return null
        val location = when {
            latQ == GeoCell.LAT_NONE -> null
            latQ > GeoCell.LAT_MAX -> return null
            else -> GeoCell(latQ, lonQ)
        }
        return RescueFrame(message, people, senderId, sequence, hop, location)
    }

    data class Decoded(val frame: RescueFrame, val correctedSymbols: Int, val erasuresUsed: Int = 0)

    private data class CodewordResult(val codeword: IntArray, val corrected: Int, val erasures: Int)

    /** Distinct RS decodings of one codeword, from fewest to most erasures (GMD order). */
    private fun codewordCandidates(word: IntArray, conf: DoubleArray): List<CodewordResult> {
        val order = conf.indices.sortedBy { conf[it] }
        val dead = conf.count { it <= 0.0 }
        if (dead > PARITY_SYMBOLS) return emptyList()
        val out = ArrayList<CodewordResult>(4)
        for (erasureCount in intArrayOf(0, 2, 4, 6)) {
            if (erasureCount < dead) continue
            val erasures = order.take(erasureCount).toIntArray()
            val result = rs.decode(word, erasures) ?: continue
            if (out.none { it.codeword.contentEquals(result.codeword) }) {
                out += CodewordResult(result.codeword, result.corrected, erasureCount)
            }
        }
        return out
    }

    fun crc8(bytes: ByteArray): Int {
        var crc = 0
        for (b in bytes) {
            crc = crc xor (b.toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 0x80 != 0) ((crc shl 1) xor 0x07) and 0xFF else (crc shl 1) and 0xFF
            }
        }
        return crc
    }
}
