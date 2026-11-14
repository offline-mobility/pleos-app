package io.github.offlinemobility.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.offlinemobility.app.InboxItem
import io.github.offlinemobility.app.InboxKind
import io.github.offlinemobility.app.UiState
import io.github.offlinemobility.app.core.online.Shelter
import io.github.offlinemobility.app.core.online.ShelterFinder
import io.github.offlinemobility.app.core.protocol.Geo
import io.github.offlinemobility.app.core.protocol.RescueMessage
import io.github.offlinemobility.app.location.Fix
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import io.github.offlinemobility.app.core.protocol.Offset as GeoOffset

private const val MAP_SHELTERS = 8
private const val SIGNAL_MAX_M = 10_000.0
private val RING_STEPS_M = listOf(100.0, 200.0, 500.0, 1_000.0, 2_000.0, 5_000.0, 10_000.0)

/** Received signals worth showing: requests and news, not replies or echoes of our own. */
internal fun UiState.receivedSignals(): List<InboxItem> =
    inbox.filter { (it.kind == InboxKind.NEW || it.kind == InboxKind.SERVER) && it.frame.message != RescueMessage.ACK }

/**
 * The area around the car, north up: shelters (outlined squares) and received signals that carry a
 * position (filled circles, red when urgent), placed by their real direction and distance.
 *
 * Drawn by the app rather than from map tiles, so it works with no connection at all — the moment
 * it matters. Street-level directions are left to the car's own navigation.
 */
@Composable
internal fun AroundMapPanel(
    state: UiState,
    onShelter: (Shelter) -> Unit,
    onSignal: (InboxItem) -> Unit,
    onShelterList: () -> Unit,
    modifier: Modifier,
) {
    val c = LocalAppColors.current
    Column(modifier.background(c.canvas).border(1.dp, c.line).padding(20.dp)) {
        Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("주변 지도", fontSize = Type.title, fontWeight = FontWeight.Bold, color = c.ink, modifier = Modifier.weight(1f))
            if (state.online.shelters.isNotEmpty()) {
                Row(
                    Modifier.heightIn(min = 56.dp).clickable(onClick = onShelterList).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("대피소 목록", fontSize = Type.body, fontWeight = FontWeight.SemiBold, color = c.ink)
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = c.ink, modifier = Modifier.size(32.dp))
                }
            }
        }
        val fix = state.myFix
        if (fix == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("내 위치를 찾는 중이에요", fontSize = Type.body, color = c.mute)
            }
            return@Column
        }
        val shelters = ShelterFinder.nearest(
            state.online.shelters, fix.latitude, fix.longitude, MAP_SHELTERS, ShelterFinder.preferredKind(state.disaster),
        )
        val signals = state.receivedSignals().mapNotNull { item ->
            item.frame.location
                ?.let { Geo.offset(fix.latitude, fix.longitude, it.latitude, it.longitude) }
                ?.takeIf { it.meters <= SIGNAL_MAX_M }
                ?.let { item to it }
        }
        val farthest = (shelters.map { it.second.meters } + signals.map { it.second.meters }).maxOrNull() ?: 0.0
        val outerM = RING_STEPS_M.firstOrNull { it >= farthest * 1.05 } ?: RING_STEPS_M.last()
        val innerM = RING_STEPS_M.lastOrNull { it <= outerM / 2 } ?: (outerM / 2)

        AroundMap(shelters, signals, outerM, innerM, onShelter, onSignal, Modifier.weight(1f).fillMaxWidth())

        shelters.firstOrNull()?.let { (shelter, at) ->
            Divider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 88.dp).clickable { onShelter(shelter) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("가장 가까운 대피소", fontSize = Type.meta, color = c.mute)
                    Text(shelter.name, fontSize = Type.body, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                Text(directionWord(at), fontSize = Type.body, fontWeight = FontWeight.Bold, color = c.ink)
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = c.mute, modifier = Modifier.size(32.dp))
            }
            Divider()
        }

        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(18.dp).border(2.dp, c.ink))
            Spacer(Modifier.width(8.dp))
            Text("대피소", fontSize = Type.meta, color = c.body)
            Spacer(Modifier.width(24.dp))
            Box(Modifier.size(18.dp).clip(CircleShape).background(c.ink))
            Spacer(Modifier.width(8.dp))
            Text("받은 신호", fontSize = Type.meta, color = c.body)
            Spacer(Modifier.weight(1f))
            Text("원 ${ringWord(innerM)} · ${ringWord(outerM)}", fontSize = Type.meta, color = c.mute)
        }
    }
}

/** A mark's centre on screen and the box it covers, so labels can stay clear of it. */
private class Placed(val center: Offset, val box: Rect)

@Composable
private fun AroundMap(
    shelters: List<Pair<Shelter, GeoOffset>>,
    signals: List<Pair<InboxItem, GeoOffset>>,
    outerM: Double,
    innerM: Double,
    onShelter: (Shelter) -> Unit,
    onSignal: (InboxItem) -> Unit,
    modifier: Modifier,
) {
    val c = LocalAppColors.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val cx = w / 2
        val cy = h / 2
        fun px(dp: Int) = with(density) { dp.dp.toPx() }
        val r = min(w, h) / 2 - px(36)
        // Square-root distance scale: near things, which matter most, get most of the room. The rings
        // are drawn on the same scale and the legend gives their real distances.
        fun radius(meters: Double) = (sqrt((meters / outerM).coerceIn(0.0, 1.0)) * r).toFloat()
        val obstacles = mutableListOf(Rect(Offset(cx, cy), px(30)))
        fun place(at: GeoOffset, half: Float): Placed {
            val k = radius(at.meters).coerceAtLeast(px(60))
            val rad = Math.toRadians(at.bearingDeg)
            val base = Offset(cx + k * sin(rad).toFloat(), cy - k * cos(rad).toFloat())
            fun free(q: Offset) = q.x in half..(w - half) && q.y in half..(h - half) &&
                obstacles.none { it.overlaps(Rect(q, half)) }
            // A mark that would land on another one takes the nearest free spot around its own place,
            // never off the map.
            val step = half * 2 + px(4)
            val p = if (free(base)) base else (1..6).asSequence()
                .flatMap { ring -> (0 until 8).asSequence().map { i -> ring to i * PI / 4 } }
                .map { (ring, a) -> base + Offset((cos(a) * ring * step).toFloat(), (sin(a) * ring * step).toFloat()) }
                .firstOrNull(::free) ?: base
            return Placed(p, Rect(p, half)).also { obstacles += it.box }
        }
        val shelterMarks = shelters.map { (shelter, at) -> shelter to place(at, px(24)) }
        val signalMarks = signals.map { (item, at) -> item to place(at, px(26)) }
        // Names only where they fit, nearest shelter first; the rest show their name when tapped.
        val labelHalfW = px(70)
        val labelH = px(26)
        val labels = mutableListOf<Rect>()
        val named = shelterMarks.filter { (_, mark) ->
            val top = mark.center.y + px(28)
            val box = Rect(mark.center.x - labelHalfW, top, mark.center.x + labelHalfW, top + labelH)
            val clear = box.top >= 0 && box.bottom <= h && box.left >= 0 && box.right <= w &&
                (obstacles + labels).none { it.overlaps(box) }
            if (clear) labels += box
            clear
        }.map { it.first }

        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 1.dp.toPx())
            drawCircle(c.line, radius = r, center = Offset(cx, cy), style = stroke)
            drawCircle(c.line, radius = radius(innerM), center = Offset(cx, cy), style = stroke)
            drawLine(c.line, Offset(cx, cy - r), Offset(cx, cy + r), strokeWidth = 1.dp.toPx())
            drawLine(c.line, Offset(cx - r, cy), Offset(cx + r, cy), strokeWidth = 1.dp.toPx())
        }
        At(cx, cy - r - px(18)) { Text("북", fontSize = Type.meta, fontWeight = FontWeight.Bold, color = c.mute) }

        At(cx, cy) {
            Box(Modifier.size(52.dp).background(c.ink), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.DirectionsCar, contentDescription = "내 차", tint = c.page, modifier = Modifier.size(34.dp))
            }
        }
        shelterMarks.forEach { (shelter, mark) ->
            At(mark.center.x, mark.center.y) {
                Box(
                    Modifier.size(48.dp).background(c.canvas).border(2.dp, c.ink).clickable { onShelter(shelter) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Home, contentDescription = shelter.name, tint = c.ink, modifier = Modifier.size(30.dp)) }
            }
            if (shelter in named) {
                At(mark.center.x, mark.center.y + px(28) + labelH / 2) {
                    Text(
                        shelter.name,
                        fontSize = Type.meta,
                        color = c.body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(140.dp).clickable { onShelter(shelter) },
                    )
                }
            }
        }
        signalMarks.forEach { (item, mark) ->
            val copy = item.frame.message.copy
            At(mark.center.x, mark.center.y) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(copy.tone.strong()).clickable { onSignal(item) },
                    contentAlignment = Alignment.Center,
                ) { Icon(copy.icon, contentDescription = copy.title, tint = c.page, modifier = Modifier.size(30.dp)) }
            }
        }
    }
}

/** Places its content centred on a point given in pixels of the enclosing box. */
@Composable
private fun At(x: Float, y: Float, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place((x - placeable.width / 2f).roundToInt(), (y - placeable.height / 2f).roundToInt())
        }
    }
}

/** "북동쪽 약 300m", or just "아주 가까이" inside the ≈40 m position grid where a direction means nothing. */
private fun directionWord(at: GeoOffset): String =
    if (at.meters < 50) "아주 가까이" else "${Geo.compassWord(at.bearingDeg)} ${Geo.distanceWord(at.meters)}"

private fun ringWord(meters: Double): String = when {
    meters < 1_000 -> "${meters.roundToInt()}m"
    meters % 1_000 == 0.0 -> "${(meters / 1_000).roundToInt()}km"
    else -> "%.1fkm".format(meters / 1_000)
}

/** Details of one shelter tapped on the map. */
@Composable
internal fun ShelterDetailSheet(shelter: Shelter, myFix: Fix?, onClose: () -> Unit) {
    val c = LocalAppColors.current
    val at = myFix?.let { Geo.offset(it.latitude, it.longitude, shelter.latitude, shelter.longitude) }
    SheetScrim(onClose) {
        Sheet(width = 1100.dp, padding = 40.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Home, contentDescription = null, tint = c.ink, modifier = Modifier.size(56.dp))
                Spacer(Modifier.width(20.dp))
                Text(shelter.name, fontSize = Type.display, fontWeight = FontWeight.ExtraBold, color = c.ink, maxLines = 2, lineHeight = Type.lines(Type.display))
            }
            Spacer(Modifier.height(24.dp))
            Divider()
            if (at != null) {
                InfoRow("방향·거리", labelWidth = 220.dp) {
                    Text(directionWord(at), fontSize = Type.title, fontWeight = FontWeight.Bold, color = c.ink)
                }
                Divider()
            }
            if (shelter.kind.isNotEmpty()) {
                InfoRow("종류", labelWidth = 220.dp) { Text(shelter.kind, fontSize = Type.body, color = c.ink) }
                Divider()
            }
            shelter.capacity?.let {
                InfoRow("수용 인원", labelWidth = 220.dp) { Text("약 ${"%,d".format(it)}명", fontSize = Type.body, color = c.ink) }
                Divider()
            }
            if (shelter.address.isNotEmpty()) {
                InfoRow("주소", labelWidth = 220.dp) {
                    Text(shelter.address.keepWords(), fontSize = Type.body, lineHeight = Type.lines(Type.body), color = c.ink, modifier = Modifier.weight(1f))
                }
                Divider()
            }
            Spacer(Modifier.height(32.dp))
            AppButton("닫기", onClose, Kind.SECONDARY, Modifier.fillMaxWidth())
        }
    }
}

/**
 * A newly received signal shown over the map until closed: the list behind it is one tap away,
 * so the right side of the screen no longer has to be a permanent list.
 */
@Composable
internal fun SignalPopup(
    item: InboxItem,
    more: Int,
    now: Long,
    myFix: Fix?,
    canReply: Boolean,
    onReply: () -> Unit,
    onClose: () -> Unit,
    onAll: () -> Unit,
    modifier: Modifier,
) {
    val c = LocalAppColors.current
    Column(modifier.background(c.canvas).border(2.dp, c.ink)) {
        Row(
            Modifier.fillMaxWidth().background(c.ink).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("새 신호", fontSize = Type.meta, fontWeight = FontWeight.Bold, color = c.page)
            if (more > 0) {
                Text("${more}건 더 · 모두 보기 ›", fontSize = Type.meta, color = c.page, modifier = Modifier.clickable(onClick = onAll))
            }
        }
        InboxCard(item, now, myFix, canReply, onReply, onClose = onClose, framed = false)
    }
}
