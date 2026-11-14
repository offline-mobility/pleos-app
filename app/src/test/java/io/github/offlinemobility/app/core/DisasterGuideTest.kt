package io.github.offlinemobility.app.core

import io.github.offlinemobility.app.core.disaster.DisasterGuides
import io.github.offlinemobility.app.core.disaster.DisasterType
import io.github.offlinemobility.app.core.disaster.SourceKind
import io.github.offlinemobility.app.core.disaster.WindowAdvice
import io.github.offlinemobility.app.core.protocol.RescueMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisasterGuideTest {

    @Test
    fun everyAlertStageHasExactlyOneGuide() {
        assertEquals(DisasterType.entries.toSet(), DisasterGuides.all.map { it.type }.toSet())
        assertEquals(DisasterType.entries.size, DisasterGuides.all.size)
        assertEquals(
            listOf("피난 중", "경계경보", "공습경보", "화생방경보", "대설·한파", "호우·침수", "지진", "산불", "태풍·강풍"),
            DisasterType.entries.map { it.label },
        )
    }

    @Test
    fun sevenMessagesAreOfferedInScreenOrderAndTheRestOnlyDecoded() {
        assertEquals(
            listOf(
                RescueMessage.POWER, RescueMessage.TRAPPED, RescueMessage.COLD, RescueMessage.MEDICAL,
                RescueMessage.WATER, RescueMessage.MEDICINE, RescueMessage.SOS,
            ),
            RescueMessage.OFFERED,
        )
        assertTrue(RescueMessage.OFFERED.none { it in RescueMessage.RETIRED })
        assertTrue(RescueMessage.ACK !in RescueMessage.RETIRED)
        assertEquals(RescueMessage.entries.size - 7 - 1, RescueMessage.RETIRED.size)
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
    fun stageBehaviourMatchesOfficialGuidance() {
        // 야간 공습경보: 전조등·미등·실내등 소등 (행정안전부 민방공 경보 행동요령)
        assertTrue(DisasterGuides.of(DisasterType.AIR_RAID).blackout)
        assertEquals(1, DisasterGuides.all.count { it.blackout })
        // 화생방·산불: 창문을 닫고 외기 차단
        assertEquals(
            setOf(DisasterType.CBRN, DisasterType.WILDFIRE),
            DisasterGuides.all.filter { it.window == WindowAdvice.KEEP_CLOSED }.map { it.type }.toSet(),
        )
    }

    @Test
    fun officialMessagesPointToTheirAlertStage() {
        fun stage(type: String?, text: String) = DisasterGuides.stageOf(type, text)
        assertEquals(DisasterType.AIR_RAID, stage("AIR_RAID", "[행정안전부] 공습경보 발령. 가까운 대피소로 대피하십시오"))
        assertEquals(DisasterType.ALERT, stage("AIR_RAID", "경계경보 발령. 대피할 준비를 하십시오"))
        assertEquals(DisasterType.CBRN, stage("AIR_RAID", "화생방경보 발령"))
        assertEquals(DisasterType.CBRN, stage("CHEMICAL", "유독가스 유출. 창문을 닫으십시오"))
        assertEquals(DisasterType.AIR_RAID, stage(null, "북한 미사일 발사"))
        assertNull(stage("AIR_RAID", "오늘 14시 민방공 대피훈련이 실시됩니다"))
        assertEquals(DisasterType.SNOW_COLD, stage("SNOW_COLD", "대설경보 발효. 차량 고립에 유의하십시오"))
        assertEquals(DisasterType.EARTHQUAKE, stage("EARTHQUAKE", "규모 4.8 지진 발생"))
        assertNull(stage("HEAT", "폭염경보 발효"))
        assertNull(stage("SNOW_COLD", "대설 대비 훈련을 실시합니다"))
    }
}
