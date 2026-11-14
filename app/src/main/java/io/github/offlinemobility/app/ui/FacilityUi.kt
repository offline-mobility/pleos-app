package io.github.offlinemobility.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.offlinemobility.app.UiState
import io.github.offlinemobility.app.core.online.FacilityKind
import io.github.offlinemobility.app.core.online.Place
import io.github.offlinemobility.app.core.online.ShelterFinder
import io.github.offlinemobility.app.core.online.toPlace
import io.github.offlinemobility.app.core.protocol.Geo
import io.github.offlinemobility.app.location.Fix
import io.github.offlinemobility.app.vehicle.RouteRequest

val FacilityKind.icon: ImageVector
    get() = when (this) {
        FacilityKind.SHELTER -> Icons.Filled.Home
        FacilityKind.PHARMACY -> Icons.Filled.LocalPharmacy
        FacilityKind.HOSPITAL -> Icons.Filled.LocalHospital
        FacilityKind.STORE -> Icons.Filled.Storefront
    }

/** Shelters carry their own kind names (민방위대피소 …); every other place is named after its [FacilityKind]. */
private val Place.icon: ImageVector
    get() = FacilityKind.entries.firstOrNull { it != FacilityKind.SHELTER && it.label == kind }?.icon ?: Icons.Filled.Home

/** "북동쪽 약 300m", or just "아주 가까이" inside the ≈40 m position grid where a direction means nothing. */
internal fun directionWord(fix: Fix, place: Place): String {
    val at = Geo.offset(fix.latitude, fix.longitude, place.latitude, place.longitude)
    return if (at.meters < 50) "아주 가까이" else "${Geo.compassWord(at.bearingDeg)} ${Geo.distanceWord(at.meters)}"
}

/** What the list shows for the chosen kind: nearest first, or the picked area's places while the position is unknown. */
internal fun UiState.facilityRows(): List<Place> {
    if (facilityKind != FacilityKind.SHELTER) return facilities
    val prefer = ShelterFinder.preferredKind(disaster)
    val fix = myFix
    return if (fix != null && chosenArea == null) {
        ShelterFinder.nearest(shelters, fix.latitude, fix.longitude, FACILITY_ROWS, prefer).map { it.first.toPlace() }
    } else {
        shelters.sortedBy { prefer != null && !it.kind.contains(prefer) }.take(FACILITY_AREA_ROWS).map { it.toPlace() }
    }
}

private const val FACILITY_ROWS = 20
private const val FACILITY_AREA_ROWS = 60

/**
 * "주변 시설": kinds on the left, the nearest places of the chosen kind on the right, as in the
 * Pleos settings app. The frame keeps one size whatever the list holds.
 */
@Composable
internal fun FacilitySheet(state: UiState, onKind: (FacilityKind) -> Unit, onPick: (Place) -> Unit, onClose: () -> Unit) {
    val c = LocalAppColors.current
    // Distances are only shown from a position the app trusts; a hand-picked area has none.
    val fix = state.myFix.takeIf { state.chosenArea == null }
    val rows = state.facilityRows()
    SheetScrim(onClose) {
        Sheet(width = 1400.dp, modifier = Modifier.fillMaxHeight(0.8f)) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(300.dp).fillMaxHeight()) {
                    Text("주변 시설", fontSize = Type.display, fontWeight = FontWeight.ExtraBold, color = c.ink)
                    Spacer(Modifier.height(24.dp))
                    FacilityKind.entries.forEach { kind ->
                        val on = kind == state.facilityKind
                        Row(
                            Modifier.fillMaxWidth().height(80.dp).background(if (on) c.ink else c.canvas)
                                .clickable { onKind(kind) }.padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(kind.icon, contentDescription = null, tint = if (on) c.page else c.ink, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(kind.label, fontSize = Type.body, fontWeight = FontWeight.SemiBold, color = if (on) c.page else c.ink)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    AppButton("닫기", onClose, Kind.SECONDARY, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.width(32.dp))
                Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
                Spacer(Modifier.width(32.dp))
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    when {
                        state.myFix == null && state.chosenArea == null ->
                            Text("위치를 아직 몰라요. 주변 지도에서 지역을 고르면 그 지역 시설을 보여 드려요".keepWords(), fontSize = Type.body, lineHeight = Type.lines(Type.body), color = c.body)
                        rows.isEmpty() && state.chosenArea != null ->
                            Text("${state.chosenArea.label}에는 앱에 든 ${state.facilityKind.label} 정보가 없어요", fontSize = Type.body, color = c.body)
                    }
                    rows.forEach { place ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(place) }.padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(place.name, fontSize = Type.title, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (place.address.isNotEmpty()) {
                                    Text(place.address, fontSize = Type.meta, color = c.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (fix != null) {
                                Spacer(Modifier.width(16.dp))
                                Text(directionWord(fix, place), fontSize = Type.body, fontWeight = FontWeight.Bold, color = c.ink)
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = c.mute, modifier = Modifier.size(32.dp))
                        }
                        Divider()
                    }
                }
            }
        }
    }
}

/** Details of one place, with the way there in the car's own navigation. [onBack] returns to the list it was picked from. */
@Composable
internal fun PlaceDetailSheet(place: Place, myFix: Fix?, route: RouteRequest, onRoute: () -> Unit, onBack: (() -> Unit)?, onClose: () -> Unit) {
    val c = LocalAppColors.current
    SheetScrim(onClose) {
        Sheet(width = 1100.dp, padding = 40.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(place.icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(56.dp))
                Spacer(Modifier.width(20.dp))
                Text(place.name, fontSize = Type.display, fontWeight = FontWeight.ExtraBold, color = c.ink, maxLines = 2, lineHeight = Type.lines(Type.display))
            }
            Spacer(Modifier.height(24.dp))
            Divider()
            if (myFix != null) {
                InfoRow("방향·거리", labelWidth = 220.dp) {
                    Text(directionWord(myFix, place), fontSize = Type.title, fontWeight = FontWeight.Bold, color = c.ink)
                }
                Divider()
            }
            if (place.kind.isNotEmpty()) {
                InfoRow("종류", labelWidth = 220.dp) { Text(place.kind, fontSize = Type.body, color = c.ink) }
                Divider()
            }
            place.capacity?.let {
                InfoRow("수용 인원", labelWidth = 220.dp) { Text("약 ${"%,d".format(it)}명", fontSize = Type.body, color = c.ink) }
                Divider()
            }
            if (place.address.isNotEmpty()) {
                InfoRow("주소", labelWidth = 220.dp) {
                    Text(place.address.keepWords(), fontSize = Type.body, lineHeight = Type.lines(Type.body), color = c.ink, modifier = Modifier.weight(1f))
                }
                Divider()
            }
            if (route == RouteRequest.FAILED) {
                Spacer(Modifier.height(20.dp))
                Text("차 내비로 길 안내를 시작하지 못했어요", fontSize = Type.body, fontWeight = FontWeight.SemiBold, color = c.urgent)
            }
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Size.gap)) {
                if (onBack != null) AppButton("이전", onBack, Kind.SECONDARY, Modifier.weight(1f))
                AppButton("닫기", onClose, Kind.SECONDARY, Modifier.weight(1f))
                AppButton("길 안내", onRoute, Kind.STRONG_NEUTRAL, Modifier.weight(1f), enabled = route != RouteRequest.ASKED)
            }
        }
    }
}
