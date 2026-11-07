package io.github.offlinemobility.app

enum class SignalChannel(val label: String) {
    LIGHT("광학 신호"),
    SOUND("음향 신호"),
}

enum class MessagePriority {
    CRITICAL,
    HIGH,
    NORMAL,
}

data class EmergencyMessage(
    val id: String,
    val title: String,
    val description: String,
    val morsePattern: String,
    val priority: MessagePriority,
)

enum class PulseType {
    SIGNAL,
    GAP,
}

data class SignalPulse(
    val type: PulseType,
    val durationUnits: Int,
)

object EmergencyProtocol {
    val presets = listOf(
        EmergencyMessage(
            id = "SOS",
            title = "긴급 구조 요청",
            description = "즉각적인 구조가 필요합니다.",
            morsePattern = "... --- ...",
            priority = MessagePriority.CRITICAL,
        ),
        EmergencyMessage(
            id = "IMMOBILE",
            title = "차량 이동 불가",
            description = "차량이 고립되어 움직일 수 없습니다.",
            morsePattern = ".... . .-.. .--.",
            priority = MessagePriority.HIGH,
        ),
        EmergencyMessage(
            id = "MEDICAL",
            title = "응급 환자 발생",
            description = "의료 지원이 필요합니다.",
            morsePattern = "-- . -..",
            priority = MessagePriority.CRITICAL,
        ),
        EmergencyMessage(
            id = "WATER",
            title = "식수 지원 요청",
            description = "식수가 부족합니다.",
            morsePattern = ".-- .- - . .-.",
            priority = MessagePriority.NORMAL,
        ),
        EmergencyMessage(
            id = "BLOCKED",
            title = "전방 도로 차단",
            description = "진행 방향의 도로가 통제되었습니다.",
            morsePattern = "-... .-.. --- -.-. -.-",
            priority = MessagePriority.HIGH,
        ),
        EmergencyMessage(
            id = "SAFE",
            title = "안전 경로 확인",
            description = "현재 진행 방향으로 이동 가능합니다.",
            morsePattern = "... .- ..-. .",
            priority = MessagePriority.NORMAL,
        ),
    )

    fun findMessage(id: String): EmergencyMessage? = presets.firstOrNull { it.id == id }

    fun toPulses(pattern: String): List<SignalPulse> {
        if (pattern.isBlank()) return emptyList()

        val pulses = mutableListOf<SignalPulse>()
        pattern.trim().forEachIndexed { index, symbol ->
            when (symbol) {
                '.' -> pulses += SignalPulse(PulseType.SIGNAL, durationUnits = 1)
                '-' -> pulses += SignalPulse(PulseType.SIGNAL, durationUnits = 3)
                ' ' -> {
                    if (pulses.lastOrNull()?.type == PulseType.GAP) {
                        pulses[pulses.lastIndex] = SignalPulse(PulseType.GAP, durationUnits = 3)
                    } else if (pulses.isNotEmpty()) {
                        pulses += SignalPulse(PulseType.GAP, durationUnits = 3)
                    }
                    return@forEachIndexed
                }
                else -> return@forEachIndexed
            }

            val next = pattern.getOrNull(index + 1)
            if (next == '.' || next == '-') {
                pulses += SignalPulse(PulseType.GAP, durationUnits = 1)
            }
        }
        return pulses
    }
}
