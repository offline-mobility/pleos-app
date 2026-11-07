package io.github.offlinemobility.app.core.relay

import io.github.offlinemobility.app.core.protocol.RescueFrame
import io.github.offlinemobility.app.core.protocol.RescueMessage
import kotlin.random.Random

sealed interface RelayDecision {
    /** First time this message is heard. [relay] is the frame to rebroadcast, if any. */
    data class Fresh(val frame: RescueFrame, val relay: RescueFrame?, val relayDelayMs: Long) : RelayDecision

    /** Same origin message heard again (repeat or another relay). */
    data class Duplicate(val frame: RescueFrame) : RelayDecision

    /** Our own message came back through another vehicle: passive delivery confirmation. */
    data class EchoOfOwn(val frame: RescueFrame) : RelayDecision
}

/**
 * Controlled flooding for a multi-hop chain of parked cars.
 *
 * - Duplicate suppression by (senderId, sequence) within [dedupeWindowMs].
 * - Hop limit [RescueFrame.MAX_HOP].
 * - Random backoff so neighbours that heard the same frame do not transmit at the same time.
 * - ACK is never relayed.
 */
class RelayPolicy(
    private val ownSenderId: Int,
    private val dedupeWindowMs: Long = 10 * 60_000L,
    private val minBackoffMs: Long = 1_500L,
    private val maxBackoffMs: Long = 6_000L,
    private val random: Random = Random.Default,
) {
    private val seen = LinkedHashMap<Int, Long>()
    private val sentHop = HashMap<Int, Int>()

    fun onReceived(frame: RescueFrame, nowMs: Long): RelayDecision {
        evict(nowMs)
        if (frame.senderId == ownSenderId) {
            // Our microphone also hears our own speaker; only a higher hop proves another car relayed it.
            val ownHop = sentHop[frame.originKey] ?: return RelayDecision.Duplicate(frame)
            return if (frame.hop > ownHop) RelayDecision.EchoOfOwn(frame) else RelayDecision.Duplicate(frame)
        }

        val key = frame.originKey
        if (seen.containsKey(key)) return RelayDecision.Duplicate(frame)
        seen[key] = nowMs

        val relay = if (frame.message == RescueMessage.ACK) null else frame.relayed()
        val delay = if (relay == null) 0L else random.nextLong(minBackoffMs, maxBackoffMs + 1)
        return RelayDecision.Fresh(frame, relay, delay)
    }

    /** Remember our own transmissions so a reflected copy is not treated as new. */
    fun markSent(frame: RescueFrame, nowMs: Long) {
        seen[frame.originKey] = nowMs
        if (frame.senderId == ownSenderId) sentHop[frame.originKey] = frame.hop
    }

    private fun evict(nowMs: Long) {
        val it = seen.entries.iterator()
        while (it.hasNext()) {
            if (nowMs - it.next().value > dedupeWindowMs) it.remove()
        }
    }
}
