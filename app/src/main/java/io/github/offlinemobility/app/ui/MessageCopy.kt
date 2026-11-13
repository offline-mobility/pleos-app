package io.github.offlinemobility.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Elderly
import androidx.compose.material.icons.filled.Flood
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Landslide
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Masks
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.offlinemobility.app.core.protocol.RescueMessage

enum class Tone { URGENT, HELP, SAFE, NEUTRAL }

/** Plain-language wording shown to people; the protocol enum keeps the on-air codes. */
data class MessageCopy(val title: String, val detail: String, val icon: ImageVector, val tone: Tone)

/** Head count only means something for messages about the people in the car. */
val RescueMessage.asksPeople: Boolean
    get() = this !in setOf(
        RescueMessage.ROAD_BLOCKED,
        RescueMessage.ROUTE_CLEAR,
        RescueMessage.SHELTER,
        RescueMessage.RESCUE_TEAM,
        RescueMessage.ACK,
    )

val RescueMessage.copy: MessageCopy
    get() = when (this) {
        RescueMessage.SOS -> MessageCopy("긴급 구조 요청", "지금 바로 도움이 필요해요", Icons.Filled.Sos, Tone.URGENT)
        RescueMessage.MEDICAL -> MessageCopy("응급 환자", "다친 사람이 있어요", Icons.Filled.MedicalServices, Tone.URGENT)
        RescueMessage.TRAPPED -> MessageCopy("고립됨", "차가 움직일 수 없어요", Icons.Filled.CarCrash, Tone.URGENT)
        RescueMessage.FIRE -> MessageCopy("화재·연기", "불이 났거나 연기가 나요", Icons.Filled.LocalFireDepartment, Tone.URGENT)
        RescueMessage.FLOOD -> MessageCopy("침수", "물이 차오르고 있어요", Icons.Filled.Flood, Tone.URGENT)
        RescueMessage.HAZARD -> MessageCopy("가스·위험물", "가스나 위험한 물질이 새요", Icons.Filled.Warning, Tone.URGENT)
        RescueMessage.VULNERABLE -> MessageCopy("노약자·어린이", "어르신이나 아이가 함께 있어요", Icons.Filled.Elderly, Tone.HELP)
        RescueMessage.WATER -> MessageCopy("식수 필요", "마실 물이 없어요", Icons.Filled.WaterDrop, Tone.HELP)
        RescueMessage.POWER -> MessageCopy("연료·전력 부족", "기름이나 배터리가 떨어져 가요", Icons.Filled.LocalGasStation, Tone.HELP)
        RescueMessage.ROAD_BLOCKED -> MessageCopy("앞길 막힘", "이쪽으로는 갈 수 없어요", Icons.Filled.Block, Tone.HELP)
        RescueMessage.ROUTE_CLEAR -> MessageCopy("지나갈 수 있음", "이 길로 이동할 수 있어요", Icons.Filled.AltRoute, Tone.SAFE)
        RescueMessage.SHELTER -> MessageCopy("대피소 있음", "근처에 대피소가 있어요", Icons.Filled.Home, Tone.SAFE)
        RescueMessage.RESCUE_TEAM -> MessageCopy("구조대 접근", "구조대가 오고 있어요", Icons.Filled.HealthAndSafety, Tone.SAFE)
        RescueMessage.ALL_SAFE -> MessageCopy("모두 안전", "우리 차 사람들은 괜찮아요", Icons.Filled.CheckCircle, Tone.SAFE)
        RescueMessage.ACK -> MessageCopy("잘 받았어요", "신호를 받았다는 응답", Icons.Filled.Done, Tone.NEUTRAL)
        RescueMessage.COLD -> MessageCopy("추위·저체온", "몸이 차가워 난방이 필요해요", Icons.Filled.AcUnit, Tone.URGENT)
        RescueMessage.COLLAPSE -> MessageCopy("붕괴·낙석", "건물이나 흙·돌이 무너졌어요", Icons.Filled.Landslide, Tone.URGENT)
        RescueMessage.CBRN -> MessageCopy("화생방·방사능", "유독가스나 방사능 경보가 있어요", Icons.Filled.Masks, Tone.URGENT)
        RescueMessage.FOOD -> MessageCopy("식량 필요", "먹을 것이 없어요", Icons.Filled.Restaurant, Tone.HELP)
        RescueMessage.MEDICINE -> MessageCopy("약 필요", "꼭 먹어야 할 약이 떨어졌어요", Icons.Filled.Medication, Tone.HELP)
    }

/** Message groups for the "다른 메시지" sheet; messages already on the first screen are left out. */
private val AllGroups = listOf(
    "위험" to listOf(RescueMessage.FIRE, RescueMessage.FLOOD, RescueMessage.HAZARD, RescueMessage.COLLAPSE, RescueMessage.CBRN),
    "사람" to listOf(RescueMessage.MEDICAL, RescueMessage.TRAPPED, RescueMessage.VULNERABLE, RescueMessage.COLD, RescueMessage.MEDICINE),
    "물자" to listOf(RescueMessage.WATER, RescueMessage.FOOD, RescueMessage.POWER),
    "길·대피" to listOf(RescueMessage.ROAD_BLOCKED, RescueMessage.ROUTE_CLEAR, RescueMessage.SHELTER),
    "안전" to listOf(RescueMessage.RESCUE_TEAM, RescueMessage.ALL_SAFE),
)

fun moreGroups(primary: List<RescueMessage>): List<Pair<String, List<RescueMessage>>> =
    AllGroups.map { (name, list) -> name to list.filter { it !in primary } }.filter { it.second.isNotEmpty() }

@Composable
fun Tone.strong(): Color {
    val c = LocalAppColors.current
    return when (this) {
        Tone.URGENT -> c.urgent
        Tone.HELP -> c.help
        Tone.SAFE -> c.safe
        Tone.NEUTRAL -> c.accent
    }
}

@Composable
fun Tone.soft(): Color {
    val c = LocalAppColors.current
    return when (this) {
        Tone.URGENT -> c.urgentSoft
        Tone.HELP -> c.helpSoft
        Tone.SAFE -> c.safeSoft
        Tone.NEUTRAL -> c.accentSoft
    }
}

fun peopleText(people: Int): String? = when (people) {
    0 -> null
    15 -> "15명 이상"
    else -> "${people}명"
}

fun relativeTime(atMs: Long, nowMs: Long): String {
    val sec = (nowMs - atMs) / 1000
    return when {
        sec < 60 -> "방금"
        sec < 3600 -> "${sec / 60}분 전"
        else -> java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA).format(java.util.Date(atMs))
    }
}

fun routeText(hop: Int): String = if (hop == 0) "가까운 차량" else "차량 ${hop}대를 거쳐 전달"

/**
 * Korean text wraps between any two syllables, which splits words across lines ("가까 / 이").
 * A word joiner between the letters of each word leaves the spaces as the only places to break.
 * For sentences only; a single word wider than its line would no longer wrap.
 */
fun String.keepWords(): String = split(' ').joinToString(" ") { word -> word.asIterable().joinToString("\u2060") }
