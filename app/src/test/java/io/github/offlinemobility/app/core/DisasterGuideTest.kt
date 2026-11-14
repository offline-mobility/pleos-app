package io.github.offlinemobility.app.core

import io.github.offlinemobility.app.core.disaster.DisasterGuide
import io.github.offlinemobility.app.core.disaster.DisasterGuides
import io.github.offlinemobility.app.core.disaster.DisasterType
import io.github.offlinemobility.app.core.disaster.SourceKind
import io.github.offlinemobility.app.core.disaster.WindowAdvice
import io.github.offlinemobility.app.core.protocol.RescueMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisasterGuideTest {

    @Test
    fun everyDisasterTypeHasExactlyOneGuide() {
        assertEquals(DisasterType.entries.toSet(), DisasterGuides.all.map { it.type }.toSet())
        assertEquals(DisasterType.entries.size, DisasterGuides.all.size)
    }

    @Test
    fun primaryMessagesAreFiveDistinctSendableMessages() {
        for (guide in DisasterGuides.all) {
            assertEquals(guide.type.name, 5, guide.primary.size)
            assertEquals(guide.type.name, 5, guide.primary.distinct().size)
            assertTrue(guide.type.name, RescueMessage.SOS !in guide.primary && RescueMessage.ACK !in guide.primary)
        }
    }

    @Test
    fun firstScreenIsEightMessagesInTheFixedOrderIncludingTheSituationsOwn() {
        val order = DisasterGuide.FIRST_SCREEN_ORDER
        for (guide in DisasterGuides.all) {
            val first = guide.firstScreen
            assertEquals(guide.type.name, 8, first.size)
            assertEquals(guide.type.name, 8, first.distinct().size)
            assertTrue(guide.type.name, first.containsAll(guide.primary))
            assertTrue(guide.type.name, guide.primary.all { it in order })
            assertEquals(guide.type.name, first.sortedBy { order.indexOf(it) }, first)
            assertTrue(guide.type.name, RescueMessage.SOS !in first && RescueMessage.ACK !in first)
        }
        val general = listOf(
            RescueMessage.MEDICAL, RescueMessage.POWER, RescueMessage.TRAPPED, RescueMessage.FIRE,
            RescueMessage.FLOOD, RescueMessage.WATER, RescueMessage.COLD, RescueMessage.VULNERABLE,
        )
        assertEquals(general, DisasterGuides.of(DisasterType.GENERAL).firstScreen)
        assertEquals(general, DisasterGuides.of(DisasterType.HEAT).firstScreen)
    }

    @Test
    fun everyGuideCitesAnOfficialKoreanSourceOverHttps() {
        for (guide in DisasterGuides.all) {
            assertTrue(guide.type.name, guide.sources.any { it.kind == SourceKind.KOREA_OFFICIAL })
            assertTrue(guide.type.name, guide.sources.all { it.url.startsWith("https://") })
            assertTrue(guide.type.name, guide.actions.isNotEmpty())
            assertTrue(guide.type.name, guide.sources.all { it.org.isNotBlank() && it.org.length <= 12 })
        }
    }

    @Test
    fun foreignStepsOnlyWhereAForeignSourceIsCited() {
        for (guide in DisasterGuides.all) {
            val hasForeignStep = (guide.actions + guide.avoid).any { it.foreign }
            if (hasForeignStep) assertTrue(guide.type.name, guide.sources.any { it.kind == SourceKind.FOREIGN_OFFICIAL })
        }
    }

    @Test
    fun modeBehaviourMatchesOfficialGuidance() {
        // 야간 공습경보: 전조등·미등·실내등 소등 (행정안전부 민방공 경보 행동요령)
        assertTrue(DisasterGuides.of(DisasterType.AIR_RAID).blackout)
        // 화학사고: 창문을 닫고 외기 차단
        assertEquals(WindowAdvice.KEEP_CLOSED, DisasterGuides.of(DisasterType.CHEMICAL).window)
        assertEquals(WindowAdvice.KEEP_CLOSED, DisasterGuides.of(DisasterType.WILDFIRE).window)
        assertTrue(DisasterGuides.all.count { it.blackout } == 1)
    }
}
