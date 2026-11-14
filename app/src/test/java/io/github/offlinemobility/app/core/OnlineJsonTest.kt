package io.github.offlinemobility.app.core

import io.github.offlinemobility.app.core.disaster.DisasterType
import io.github.offlinemobility.app.core.online.OnlineJson
import io.github.offlinemobility.app.core.online.Shelter
import io.github.offlinemobility.app.core.online.ShelterFinder
import io.github.offlinemobility.app.core.protocol.RescueMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineJsonTest {
    @Test
    fun alertsParseAndUnknownTypeIsNull() {
        val result = OnlineJson.alerts(
            """{"source":"live","alerts":[
              {"id":"1","createdAt":1794000000000,"text":"공습경보 발령","level":"긴급재난","type":"AIR_RAID"},
              {"id":"2","createdAt":1794000000001,"text":"실종자를 찾습니다","type":null},
              {"id":"3","createdAt":1794000000002,"text":"x","type":"NOT_A_TYPE"},
              {"id":"","createdAt":1,"text":"no id"}, {"id":"5","text":"no time"}, "garbage"
            ]}""",
        )!!
        assertEquals("live", result.source)
        assertEquals(listOf(DisasterType.AIR_RAID, null, null), result.items.map { it.type })
    }

    @Test
    fun sheltersSkipInvalidCoordinatesAndSortByDistance() {
        val result = OnlineJson.shelters(
            """{"source":"sample","shelters":[
              {"id":"far","name":"먼 곳","kind":"민방위 대피소","lat":37.52,"lon":127.05,"address":"a","capacity":100},
              {"id":"near","name":"가까운 곳","kind":"지진 옥외대피장소","lat":37.4985,"lon":127.028,"address":"b"},
              {"id":"bad","name":"잘못된 좌표","lat":137.0,"lon":127.0},
              {"id":"noname","lat":37.0,"lon":127.0}
            ]}""",
        )!!
        assertEquals(listOf("far", "near"), result.items.map { it.id })
        val nearest = ShelterFinder.nearest(result.items, 37.4979, 127.0276, 5)
        assertEquals("near", nearest.first().first.id)
        assertTrue(nearest.first().second.meters < 100)
        assertNull(result.items.first { it.id == "near" }.capacity)
    }

    @Test
    fun theSheltersMeantForTheSituationComeFirstHoweverFar() {
        fun shelter(name: String, kind: String, lat: Double) = Shelter(name, name, kind, lat, 127.0, "", null)
        val all = listOf(
            shelter("경로당", "한파쉼터,무더위쉼터", 37.5001),
            shelter("운동장", "지진옥외대피장소", 37.5050),
            shelter("지하주차장", "민방위대피소", 37.5200),
            shelter("지하상가", "민방위대피소", 37.5100),
        )
        fun first(stage: DisasterType) =
            ShelterFinder.nearest(all, 37.5, 127.0, 3, ShelterFinder.preferredKind(stage)).map { it.first.name }
        // Every 민방위 경보 stage: underground shelters first.
        for (stage in listOf(DisasterType.EVACUATION, DisasterType.ALERT, DisasterType.AIR_RAID, DisasterType.CBRN)) {
            assertEquals(stage.name, listOf("지하상가", "지하주차장", "경로당"), first(stage))
        }
        assertEquals(listOf("운동장", "경로당", "지하상가"), first(DisasterType.EARTHQUAKE))
        assertEquals(listOf("경로당", "운동장", "지하상가"), first(DisasterType.SNOW_COLD))
        // No kind is meant for the others: simply the nearest.
        assertEquals(listOf("경로당", "운동장", "지하상가"), first(DisasterType.TYPHOON))
    }

    @Test
    fun incidentsBecomeFramesAndBadEntriesAreSkipped() {
        val result = OnlineJson.incidents(
            """{"incidents":[
              {"uid":"u1","id":"a1-3","message":"MEDICAL","people":2,"hop":1,"lat":37.499,"lon":127.03,"lastHeardAt":1794000000000,"confirmations":2},
              {"uid":"u2","id":"zz-3","message":"MEDICAL","people":2,"hop":1,"lastHeardAt":1},
              {"uid":"u3","id":"a1-3","message":"ACK","people":0,"hop":0,"lastHeardAt":1},
              {"uid":"u4","id":"b2-f","message":"WATER","people":99,"hop":0,"lastHeardAt":1},
              {"uid":"u5","id":"c3-0","message":"WATER","people":0,"hop":0,"lastHeardAt":5}
            ]}""",
        )!!
        assertEquals(listOf("u1", "u5"), result.map { it.uid })
        val first = result.first().frame
        assertEquals(listOf(0xA1, 3, 2, 1), listOf(first.senderId, first.sequence, first.people, first.hop))
        assertEquals(RescueMessage.MEDICAL, first.message)
        assertTrue(first.location != null && result.last().frame.location == null)
    }

    @Test
    fun malformedJsonReturnsNull() {
        assertNull(OnlineJson.alerts("<html>502</html>"))
        assertNull(OnlineJson.incidents(""))
    }
}
