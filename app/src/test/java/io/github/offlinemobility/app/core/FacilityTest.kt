package io.github.offlinemobility.app.core

import io.github.offlinemobility.app.core.online.Area
import io.github.offlinemobility.app.core.online.FacilityFile
import io.github.offlinemobility.app.core.online.FacilityKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FacilityTest {
    private val lines = listOf(
        "37.49800\t127.02800\t강남역약국\t서울특별시 강남구 강남대로 396",
        "37.50500\t127.02500\t신논현약국\t서울특별시 강남구 강남대로 470",
        "37.56650\t126.97800\t시청약국\t서울특별시 중구 세종대로 110",
        "35.15950\t126.85260\t상무약국\t전남광주통합특별시 서구 상무중앙로 7",
        "38.20679\t127.22049\t새서울약국\t강원도 철원군 동송읍 이평2로 11",
        "broken line",
        "99.00000\t127.00000\t좌표가 틀린 곳\t",
    )

    @Test
    fun nearestComeFirstAndBadLinesAreSkipped() {
        val found = FacilityFile.nearest(lines.asSequence(), FacilityKind.PHARMACY, 37.49785, 127.0277, limit = 2)
        assertEquals(listOf("강남역약국", "신논현약국"), found.map { it.name })
        assertEquals("약국", found.first().kind)
        assertEquals(5, FacilityFile.nearest(lines.asSequence(), FacilityKind.PHARMACY, 37.5, 127.0, limit = 50).size)
    }

    @Test
    fun aLineNeedsAPositionAndAName() {
        assertNull(FacilityFile.parse("37.5\t127.0\t \t주소", FacilityKind.STORE))
        assertNull(FacilityFile.parse("37.5\t\t이름\t주소", FacilityKind.STORE))
        assertEquals("", FacilityFile.parse("37.5\t127.0\t이름", FacilityKind.STORE)?.address)
    }

    @Test
    fun anAreaPickedFromTheShelterListFindsPlacesUnderTheOtherProvinceNames() {
        fun inArea(province: String, district: String) =
            FacilityFile.inArea(lines.asSequence(), FacilityKind.PHARMACY, Area(province, district), 10).map { it.name }
        assertEquals(listOf("강남역약국", "신논현약국"), inArea("서울특별시", "강남구"))
        // 민방위대피소 data still says 광주광역시 and 강원특별자치도 / 강원도 differ between files.
        assertEquals(listOf("상무약국"), inArea("광주광역시", "서구"))
        assertEquals(listOf("새서울약국"), inArea("강원특별자치도", "철원군"))
        assertEquals(emptyList<String>(), inArea("부산광역시", "서구"))
    }
}
