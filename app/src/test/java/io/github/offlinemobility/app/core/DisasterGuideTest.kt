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
    fun firstScreenKeepsSevenFixedCellsAndOneForTheSituation() {
        val fixed = listOf(
            RescueMessage.SOS, RescueMessage.TRAPPED, RescueMessage.POWER, RescueMessage.FLOOD,
            RescueMessage.FIRE, RescueMessage.WATER, RescueMessage.MEDICAL,
        )
        assertEquals(fixed, DisasterGuide.FIRST_SCREEN_FIXED)
        for (guide in DisasterGuides.all) {
            val first = guide.firstScreen
            assertEquals(guide.type.name, 8, first.size)
            assertEquals(guide.type.name, 8, first.distinct().size)
            assertEquals(guide.type.name, fixed, first.take(7))
            assertTrue(guide.type.name, RescueMessage.ACK !in first)
            assertTrue(guide.type.name, first.none { it in RescueMessage.RETIRED })
        }
        fun situationCell(type: DisasterType) = DisasterGuides.of(type).firstScreen.last()
        assertEquals(RescueMessage.COLD, situationCell(DisasterType.GENERAL))
        assertEquals(RescueMessage.ROAD_BLOCKED, situationCell(DisasterType.EARTHQUAKE))
        assertEquals(RescueMessage.CBRN, situationCell(DisasterType.AIR_RAID))
        assertEquals(RescueMessage.VULNERABLE, situationCell(DisasterType.HEAT))
        assertEquals(RescueMessage.CBRN, situationCell(DisasterType.CHEMICAL))
        assertEquals(RescueMessage.MEDICINE, situationCell(DisasterType.BLACKOUT))
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
