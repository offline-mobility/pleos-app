package io.github.offlinemobility.app.core.protocol

/**
 * Predefined rescue messages. The code is the 4-bit value carried on air, so it must stay stable.
 */
enum class RescueMessage(val code: Int, val title: String, val critical: Boolean) {
    SOS(0, "긴급 구조 요청", true),
    MEDICAL(1, "응급 환자 발생", true),
    TRAPPED(2, "고립·이동 불가", true),
    FIRE(3, "화재·연기", true),
    HAZARD(4, "가스·위험물 누출", true),
    FLOOD(5, "침수 위험", true),
    VULNERABLE(6, "노약자·어린이 동승", false),
    WATER(7, "식수 필요", false),
    POWER(8, "연료·전력 부족", false),
    ROAD_BLOCKED(9, "전방 도로 차단", false),
    ROUTE_CLEAR(10, "통행 가능 경로", false),
    SHELTER(11, "대피소 확인", false),
    RESCUE_TEAM(12, "구조대 접근 중", false),
    ALL_SAFE(13, "탑승자 전원 안전", false),
    ACK(14, "수신 확인", false),
    ;

    companion object {
        fun fromCode(code: Int): RescueMessage? = entries.firstOrNull { it.code == code }
    }
}

/**
 * One on-air message: 24 information bits + CRC-8 = 8 nibbles, then RS(14, 8) over GF(16).
 *
 * | bits | field      | note                                        |
 * |------|------------|---------------------------------------------|
 * | 2    | version    | currently 0                                 |
 * | 4    | message    | [RescueMessage.code]                        |
 * | 4    | people     | 0 unknown, 1–14 exact, 15 means 15 or more  |
 * | 8    | senderId   | random per vehicle, used for relay dedupe   |
 * | 4    | sequence   | increments per new message from a sender    |
 * | 2    | hop        | 0 original, incremented by each relay (≤ 3) |
 * | 8    | crc8       | CRC-8/SMBUS (poly 0x07) over the 24 bits    |
 */
data class RescueFrame(
    val message: RescueMessage,
    val people: Int,
    val senderId: Int,
    val sequence: Int,
    val hop: Int = 0,
    val version: Int = 0,
) {
    init {
        require(people in 0..15) { "people must be 0..15" }
        require(senderId in 0..255) { "senderId must be 0..255" }
        require(sequence in 0..15) { "sequence must be 0..15" }
        require(hop in 0..MAX_HOP) { "hop must be 0..$MAX_HOP" }
        require(version in 0..3) { "version must be 0..3" }
    }

    /** Key that identifies the same original message across relays. */
    val originKey: Int get() = (senderId shl 4) or sequence

    fun relayed(): RescueFrame? = if (hop >= MAX_HOP) null else copy(hop = hop + 1)

    companion object {
        const val MAX_HOP = 3
    }
}

object RescueFrameCodec {
    const val DATA_SYMBOLS = 8
    const val PARITY_SYMBOLS = 6
    const val CODEWORD_SYMBOLS = DATA_SYMBOLS + PARITY_SYMBOLS

    private val rs = ReedSolomon16(PARITY_SYMBOLS)

    /**
     * Whitening mask XOR-ed onto every codeword. Without it the all-zero word (what silence or a
     * stuck tone demodulates to) would be a valid "SOS from sender 0" frame.
     */
    private val WHITENING = intArrayOf(0x9, 0x4, 0xE, 0x1, 0x7, 0xC, 0x2, 0xB, 0x5, 0xF, 0x8, 0x3, 0xD, 0x6)

    /** Frame → 14 nibbles ready for modulation. */
    fun encode(frame: RescueFrame): IntArray {
        val info = (frame.version shl 22) or
            (frame.message.code shl 18) or
            (frame.people shl 14) or
            (frame.senderId shl 6) or
            (frame.sequence shl 2) or
            frame.hop
        val bytes = byteArrayOf((info ushr 16).toByte(), (info ushr 8).toByte(), info.toByte())
        val word = (info shl 8) or crc8(bytes)
        val data = IntArray(DATA_SYMBOLS) { (word ushr (28 - it * 4)) and 0xF }
        val codeword = rs.encode(data)
        return IntArray(CODEWORD_SYMBOLS) { codeword[it] xor WHITENING[it] }
    }

    /**
     * 14 nibbles → frame. [erasures] mark low-confidence symbols.
     * Returns null when the codeword cannot be corrected or the CRC does not match.
     */
    fun decode(symbols: IntArray, erasures: IntArray = IntArray(0)): Decoded? {
        if (symbols.size != CODEWORD_SYMBOLS) return null
        val unwhitened = IntArray(CODEWORD_SYMBOLS) { symbols[it] xor WHITENING[it] }
        val result = rs.decode(unwhitened, erasures) ?: return null
        var word = 0
        for (i in 0 until DATA_SYMBOLS) word = (word shl 4) or result.codeword[i]
        val info = word ushr 8
        val bytes = byteArrayOf((info ushr 16).toByte(), (info ushr 8).toByte(), info.toByte())
        if (crc8(bytes) != (word and 0xFF)) return null

        val message = RescueMessage.fromCode((info ushr 18) and 0xF) ?: return null
        val frame = RescueFrame(
            version = (info ushr 22) and 0x3,
            message = message,
            people = (info ushr 14) and 0xF,
            senderId = (info ushr 6) and 0xFF,
            sequence = (info ushr 2) and 0xF,
            hop = info and 0x3,
        )
        return Decoded(frame, result.corrected)
    }

    data class Decoded(val frame: RescueFrame, val correctedSymbols: Int)

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
