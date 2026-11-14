package io.github.offlinemobility.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.offlinemobility.app.KeepWarmState
import io.github.offlinemobility.app.UiState
import io.github.offlinemobility.app.core.cabin.CabinAssumptions
import io.github.offlinemobility.app.core.cabin.KeepWarmController
import io.github.offlinemobility.app.core.cabin.WarmLevel
import io.github.offlinemobility.app.core.disaster.DisasterType
import io.github.offlinemobility.app.core.safety.GearState
import kotlin.math.roundToInt

/** Offered while parked in a car whose heater the app can control; not in a heat wave. */
internal fun showKeepWarm(state: UiState): Boolean =
    state.keepWarm.active ||
        (state.keepWarmEstimate != null && state.gear == GearState.PARK)

/** "약 22시간" / "약 3일" / "1시간 미만". Hours are kept up to three days so two plans can be compared. */
internal fun warmHoursWord(hours: Double): String = when {
    hours < 1 -> "1시간 미만"
    hours < 72 -> "약 ${hours.toInt()}시간"
    else -> "약 ${(hours / 24).toInt()}일"
}

private fun nowWord(warm: KeepWarmState): String = when {
    warm.freshAir -> "바깥 공기를 넣는 중"
    warm.hvacOn -> "난방하는 중"
    else -> "난방을 쉬는 중"
}

private fun cabinWord(cabinC: Double?): String? = cabinC?.let { "실내 약 ${it.roundToInt()}도" }

@Composable
internal fun KeepWarmBar(state: UiState, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val warm = state.keepWarm
    val estimate = state.keepWarmEstimate
    val shape = RoundedCornerShape(Size.radius)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 92.dp).clip(shape)
            .background(if (warm.active) c.safeSoft else c.canvas)
            .border(1.dp, if (warm.active) c.safe.copy(alpha = 0.35f) else c.line, shape)
            .clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Thermostat, contentDescription = null, tint = if (warm.active) c.safe else c.accent, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (warm.active) "난방을 아끼는 중" else "난방 아끼며 오래 버티기",
                fontSize = Type.body,
                fontWeight = FontWeight.Bold,
                color = c.ink,
            )
            val meta = when {
                warm.active -> listOfNotNull(
                    cabinWord(warm.cabinC),
                    nowWord(warm),
                    estimate?.let { "${warmHoursWord(it.hours(warm.level))} 버틸 수 있어요 (추정)" },
                ).joinToString(" · ")
                warm.endedNote != null -> warm.endedNote
                estimate != null -> "지금처럼 틀면 ${warmHoursWord(estimate.normalHours)} · 아껴 쓰면 ${warmHoursWord(estimate.saverHours)} (추정)"
                else -> ""
            }
            Text(meta, fontSize = Type.meta, color = c.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (warm.active) "보기 ›" else "열기 ›",
            fontSize = Type.body,
            fontWeight = FontWeight.Bold,
            color = if (warm.active) c.safe else c.accent,
        )
    }
}

@Composable
internal fun KeepWarmSheet(state: UiState, actions: ScreenActions, onClose: () -> Unit) {
    val c = LocalAppColors.current
    val warm = state.keepWarm
    val estimate = state.keepWarmEstimate
    SheetScrim(onClose) {
        Sheet(width = 1000.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Thermostat, contentDescription = null, tint = if (warm.active) c.safe else c.accent, modifier = Modifier.size(52.dp))
                Spacer(Modifier.width(16.dp))
                Text(
                    if (warm.active) "난방을 아끼는 중" else "난방 아끼며 오래 버티기",
                    fontSize = Type.display,
                    fontWeight = FontWeight.ExtraBold,
                    color = c.ink,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("구조를 기다리는 동안 차가 난방을 알아서 조절해 배터리를 오래 써요".keepWords(), fontSize = Type.body, lineHeight = Type.lines(Type.body), color = c.body)
            Spacer(Modifier.height(16.dp))

            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                if (warm.active) {
                    Divider()
                    InfoRow("지금") {
                        Text(
                            listOfNotNull(nowWord(warm), cabinWord(warm.cabinC)).joinToString(" · "),
                            fontSize = Type.body,
                            fontWeight = FontWeight.SemiBold,
                            color = c.ink,
                        )
                    }
                    Divider()
                    InfoRow("버틸 수 있는 시간") {
                        Text(
                            estimate?.let { "${warmHoursWord(it.hours(warm.level))} (추정)" } ?: "알 수 없어요",
                            fontSize = Type.body,
                            fontWeight = FontWeight.SemiBold,
                            color = c.ink,
                        )
                    }
                    Divider()
                    if (warm.refused) {
                        Spacer(Modifier.height(10.dp))
                        Text("이 차가 일부 난방 설정을 받아들이지 않았어요. 직접 조절해야 할 수 있어요.".keepWords(), fontSize = Type.meta, color = c.help)
                    }
                } else {
                    Divider()
                    InfoRow("함께 있는 사람") { Stepper(state.people, actions.people) }
                    Divider()
                    Spacer(Modifier.height(Size.gap))
                    if (estimate != null) {
                        LevelCard(
                            title = "아껴 쓰기",
                            detail = listOfNotNull(
                                "실내 ${KeepWarmController.CYCLE_LOW_C.toInt()}~${WarmLevel.SAVER.setpointC.toInt()}도",
                                "앉은 자리 열선".takeIf { warm.support.seatHeat },
                            ).joinToString(" · "),
                            hours = estimate.saverHours,
                            selected = warm.level == WarmLevel.SAVER,
                            onClick = { actions.warmLevel(WarmLevel.SAVER) },
                        )
                        Spacer(Modifier.height(12.dp))
                        LevelCard(
                            title = "덜 춥게",
                            detail = "실내 ${WarmLevel.GENTLE.setpointC.toInt()}도 유지 · 어르신이나 아이가 있을 때",
                            hours = estimate.gentleHours,
                            selected = warm.level == WarmLevel.GENTLE,
                            onClick = { actions.warmLevel(WarmLevel.GENTLE) },
                        )
                    }
                }

                SectionTitle(if (warm.active) "이렇게 하고 있어요" else "시작하면 이렇게 해요")
                val fresh = estimate?.freshMinutes ?: 0
                GuideLine(
                    "1",
                    if (state.cabinSealed) "‘${state.disaster.label}’ 상황이라 바깥 공기는 들이지 않아요"
                    else "공기가 탁해지지 않게 ${KeepWarmController.FRESH_CYCLE_MIN}분마다 ${fresh}분씩 바깥 공기를 넣어요",
                    c.ink,
                )
                GuideLine("2", "두꺼운 옷을 입고 담요를 덮으세요. 서로 붙어 앉으면 더 따뜻해요", c.ink)
                GuideLine("3", "기어를 P에서 바꾸거나 ‘그만하기’를 누르면 원래 난방으로 돌아가요", c.ink)
                Spacer(Modifier.height(8.dp))
                Text(
                    "시간은 바깥 영하 ${(-CabinAssumptions().outsideC).toInt()}도를 기준으로 짧게 잡은 추정이에요. 이동할 때 쓸 전력은 남겨 두고 계산했어요.".keepWords(),
                    fontSize = Type.meta,
                    color = c.mute,
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Size.gap)) {
                if (warm.active) {
                    AppButton("닫기", onClose, Kind.SECONDARY, Modifier.weight(1f))
                    AppButton("그만하고 원래 난방으로", actions.stopKeepWarm, Kind.STRONG_NEUTRAL, Modifier.weight(1.6f))
                } else {
                    AppButton("취소", onClose, Kind.SECONDARY, Modifier.weight(1f))
                    AppButton(
                        "시작하기",
                        actions.startKeepWarm,
                        Kind.TONE,
                        Modifier.weight(1.6f),
                        tone = Tone.NEUTRAL,
                        enabled = estimate != null && state.gear == GearState.PARK,
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelCard(title: String, detail: String, hours: Double, selected: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(Size.radius)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 112.dp).clip(shape)
            .background(if (selected) c.accentSoft else c.canvas)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.line, shape)
            .clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).border(2.dp, if (selected) c.accent else c.mute, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(if (selected) c.accent else Color.Transparent))
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = Type.title, fontWeight = FontWeight.Bold, color = c.ink)
            Text(detail, fontSize = Type.meta, color = c.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Text(warmHoursWord(hours), fontSize = Type.display, fontWeight = FontWeight.Bold, color = c.ink)
    }
}
