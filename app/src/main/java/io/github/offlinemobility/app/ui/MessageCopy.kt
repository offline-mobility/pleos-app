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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import io.github.offlinemobility.app.core.protocol.RescueMessage

enum class Tone { URGENT, HELP, SAFE, NEUTRAL }

/** Plain-language wording shown to people; the protocol enum keeps the on-air codes. */
data class MessageCopy(val title: String, val icon: ImageVector, val tone: Tone)

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
        RescueMessage.SOS -> MessageCopy("SOS 긴급 요청", Icons.Filled.Sos, Tone.URGENT)
        RescueMessage.MEDICAL -> MessageCopy("응급 환자", Icons.Filled.MedicalServices, Tone.URGENT)
        RescueMessage.TRAPPED -> MessageCopy("고립됨", Icons.Filled.CarCrash, Tone.URGENT)
        RescueMessage.FIRE -> MessageCopy("화재·연기", Icons.Filled.LocalFireDepartment, Tone.URGENT)
        RescueMessage.FLOOD -> MessageCopy("침수", Icons.Filled.Flood, Tone.URGENT)
        RescueMessage.HAZARD -> MessageCopy("가스·위험물", Icons.Filled.Warning, Tone.URGENT)
        RescueMessage.VULNERABLE -> MessageCopy("노약자·어린이", Icons.Filled.Elderly, Tone.HELP)
        RescueMessage.WATER -> MessageCopy("식수 필요", Icons.Filled.WaterDrop, Tone.HELP)
        RescueMessage.POWER -> MessageCopy("연료·전력 부족", Icons.Filled.LocalGasStation, Tone.HELP)
        RescueMessage.ROAD_BLOCKED -> MessageCopy("앞길 막힘", Icons.Filled.Block, Tone.HELP)
        RescueMessage.ROUTE_CLEAR -> MessageCopy("지나갈 수 있음", Icons.Filled.AltRoute, Tone.SAFE)
        RescueMessage.SHELTER -> MessageCopy("대피소 있음", Icons.Filled.Home, Tone.SAFE)
        RescueMessage.RESCUE_TEAM -> MessageCopy("구조대 접근", Icons.Filled.HealthAndSafety, Tone.SAFE)
        RescueMessage.ALL_SAFE -> MessageCopy("모두 안전", Icons.Filled.CheckCircle, Tone.SAFE)
        RescueMessage.ACK -> MessageCopy("잘 받았어요", Icons.Filled.Done, Tone.NEUTRAL)
        RescueMessage.COLD -> MessageCopy("추위·저체온", Icons.Filled.AcUnit, Tone.URGENT)
        RescueMessage.COLLAPSE -> MessageCopy("붕괴·낙석", Icons.Filled.Landslide, Tone.URGENT)
        RescueMessage.CBRN -> MessageCopy("화생방·방사능", Icons.Filled.Masks, Tone.URGENT)
        RescueMessage.FOOD -> MessageCopy("식량 필요", Icons.Filled.Restaurant, Tone.HELP)
        RescueMessage.MEDICINE -> MessageCopy("약 필요", Icons.Filled.Medication, Tone.HELP)
    }

/** Message groups for the "다른 상황" sheet; messages already on the first screen are left out. */
private val AllGroups = listOf(
    "위험" to listOf(RescueMessage.FIRE, RescueMessage.FLOOD, RescueMessage.HAZARD, RescueMessage.COLLAPSE, RescueMessage.CBRN),
    "사람" to listOf(RescueMessage.MEDICAL, RescueMessage.TRAPPED, RescueMessage.VULNERABLE, RescueMessage.COLD, RescueMessage.MEDICINE),
    "물자" to listOf(RescueMessage.WATER, RescueMessage.FOOD, RescueMessage.POWER),
    "길·대피" to listOf(RescueMessage.ROAD_BLOCKED, RescueMessage.ROUTE_CLEAR, RescueMessage.SHELTER),
    "안전" to listOf(RescueMessage.RESCUE_TEAM, RescueMessage.ALL_SAFE),
)

fun moreGroups(primary: List<RescueMessage>): List<Pair<String, List<RescueMessage>>> =
    AllGroups.map { (name, list) -> name to list.filter { it !in primary } }.filter { it.second.isNotEmpty() }

/**
 * Icon colour on the receiving side: red picks out urgent requests in the list, everything else is
 * black. What people press to send stays black whatever the message (see [LocalAppColors]).
 */
@Composable
fun Tone.strong(): Color {
    val c = LocalAppColors.current
    return if (this == Tone.URGENT) c.urgent else c.ink
}

fun peopleText(people: Int): String? = when (people) {
    0 -> null
    15 -> "15명 이상"
    else -> "${people}명"
}

/**
 * "응급 환자  3명": the head count is set apart by space and a lighter weight, not by a "·",
 * because titles such as "화재·연기" already use that mark.
 */
@Composable
fun titleWithPeople(message: RescueMessage, people: Int): AnnotatedString {
    val c = LocalAppColors.current
    val count = peopleText(people)?.takeIf { message.asksPeople }
    return buildAnnotatedString {
        append(message.copy.title)
        if (count != null) {
            append("  ")
            withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = c.body)) { append(count) }
        }
    }
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
