package io.github.offlinemobility.app.core

import io.github.offlinemobility.app.core.online.Area
import io.github.offlinemobility.app.core.online.CivilShelters
import io.github.offlinemobility.app.core.position.PositionGuard
import io.github.offlinemobility.app.core.position.positionStillValid
import io.github.offlinemobility.app.location.Fix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PositionTest {
    private val gangnam = Fix(37.4979, 127.0276, 5f, 0)
    private fun at(lat: Double, lon: Double, seconds: Long) = Fix(lat, lon, 5f, seconds * 1000)

    @Test
    fun ordinaryDrivingIsTrusted() {
        val g = PositionGuard()
        g.offer(gangnam)
        g.offer(at(37.5079, 127.0276, 60)) // about 1.1 km in a minute: 67 km/h
        assertEquals(37.5079, g.trusted!!.latitude, 1e-9)
        assertNull(g.suspect)
    }

    @Test
    fun anImpossibleJumpIsHeldForThePersonToDecide() {
        val g = PositionGuard()
        g.offer(gangnam)
        val busan = at(35.1796, 129.0756, 10)
        g.offer(busan)
        assertEquals(gangnam, g.trusted)
        assertEquals(busan, g.suspect)
        assertTrue(g.suspectJumpM()!! > 300_000)

        g.useSuspect()
        assertEquals(busan, g.trusted)
        assertNull(g.suspect)
    }

    @Test
    fun keepingTheOldPositionStopsAskingAboutTheFalsePlace() {
        val g = PositionGuard()
        g.offer(gangnam)
        g.offer(at(37.80, 127.0276, 5)) // 33 km away five seconds later
        g.keepTrusted()
        assertNull(g.suspect)
        // The false signal keeps coming: dropped quietly, no new question.
        g.offer(at(37.801, 127.0277, 10))
        assertNull(g.suspect)
        assertTrue(g.ignoring)
        assertEquals(gangnam, g.trusted)
        // A believable reading near the real place clears it.
        g.offer(at(37.4981, 127.0278, 20))
        assertFalse(g.ignoring)
        assertEquals(37.4981, g.trusted!!.latitude, 1e-9)
    }

    @Test
    fun aLongGapMakesAFarMoveBelievable() {
        val g = PositionGuard()
        g.offer(gangnam)
        g.offer(at(37.80, 127.0276, 3 * 3600)) // 33 km in three hours
        assertEquals(37.80, g.trusted!!.latitude, 1e-9)
    }

    @Test
    fun aRememberedPositionGivesWayToALiveOne() {
        val g = PositionGuard()
        g.restore(at(37.0, 127.0, 0))
        g.restore(gangnam) // only the first restore counts
        assertEquals(37.0, g.trusted!!.latitude, 1e-9)
        g.offer(at(37.0005, 127.0005, 3600))
        assertEquals(37.0005, g.trusted!!.latitude, 1e-9)
    }

    @Test
    fun anOldPositionIsStillSentWhileTheCarHasStayedParked() {
        val fix = at(37.0, 127.0, 1_000)
        val twoHoursLater = fix.timeMs + 2 * 3_600_000
        assertTrue(positionStillValid(fix, fix.timeMs + 60_000, parkedSinceMs = null))
        assertFalse(positionStillValid(fix, twoHoursLater, parkedSinceMs = null))
        assertTrue(positionStillValid(fix, twoHoursLater, parkedSinceMs = fix.timeMs - 1))
        // Parked only after the fix: it may have moved in between.
        assertFalse(positionStillValid(fix, twoHoursLater, parkedSinceMs = fix.timeMs + 60_000))
    }

    @Test
    fun civilSheltersParseAndAnswerByPositionAndByArea() {
        val json = """
            {"generatedAt":"2026-11-13","columns":["id","name","lat","lon","areaM2","address"],"rows":[
            ["1","강남역(2호선)",37.4979,127.0276,6390,"서울특별시 강남구 강남대로 지하 396 (역삼동)"],
            ["2","역삼역",37.5006,127.0364,825,"서울특별시 강남구 테헤란로 156"],
            ["3","시청역",37.5657,126.9769,0,"서울특별시 중구 세종대로 지하 101"],
            ["4","정부청사",36.5040,127.2494,100,"세종특별자치시 한누리대로 2130"],
            ["5","마을회관",37.9663,127.2609,100,"97"]]}
        """.trimIndent()
        val civil = CivilShelters.parse(json)
        assertEquals("2026-11-13", civil.generatedAt)
        assertEquals(5, civil.all.size)
        assertEquals(setOf("서울특별시", "세종특별자치시"), civil.areas.keys)
        val first = civil.all.first()
        assertEquals("civil-1", first.id)
        assertEquals(CivilShelters.KIND, first.kind)
        assertEquals(7_745, first.capacity)
        assertNull(civil.all[2].capacity)

        val near = civil.near(37.4980, 127.0280, km = 2.0, limit = 5)
        assertEquals(listOf("civil-1", "civil-2"), near.map { it.id })

        assertEquals(listOf("강남구", "중구"), civil.areas["서울특별시"])
        assertEquals(listOf(""), civil.areas["세종특별자치시"])
        assertEquals(listOf("civil-1", "civil-2"), civil.inArea(Area("서울특별시", "강남구")).map { it.id })
        assertEquals("세종특별자치시", Area("세종특별자치시", "").label)
    }

    @Test
    fun theShippedSnapshotCoversTheCountry() {
        val file = listOf("../server/data/civil-shelters.json", "server/data/civil-shelters.json").map(::File).firstOrNull { it.exists() }
        assertNotNull("server snapshot not found", file)
        val civil = CivilShelters.parse(file!!.readText())
        assertTrue(civil.all.size > 15_000)
        assertTrue(civil.areas.size >= 17)
        assertTrue(civil.near(37.4979, 127.0276, km = 1.0, limit = 3).isNotEmpty())
    }
}
