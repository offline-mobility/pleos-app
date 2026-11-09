package io.github.offlinemobility.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Elderly
import androidx.compose.material.icons.filled.Flood
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MedicalServices
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
    }

/** Messages on the first screen: the most urgent ones. */
val PrimaryMessages = listOf(
    RescueMessage.MEDICAL,
    RescueMessage.TRAPPED,
    RescueMessage.FIRE,
    RescueMessage.FLOOD,
    RescueMessage.WATER,
)

/** Remaining messages grouped for the "다른 메시지" sheet. */
val MoreMessageGroups = listOf(
    "위험" to listOf(RescueMessage.HAZARD),
    "도움 필요" to listOf(RescueMessage.VULNERABLE, RescueMessage.POWER),
    "길·대피" to listOf(RescueMessage.ROAD_BLOCKED, RescueMessage.ROUTE_CLEAR, RescueMessage.SHELTER),
    "안전" to listOf(RescueMessage.RESCUE_TEAM, RescueMessage.ALL_SAFE),
)

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
