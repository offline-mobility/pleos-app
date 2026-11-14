package io.github.offlinemobility.app.core.disaster

/**
 * The app is for one situation: a war, people evacuating by car, roads jammed, networks down.
 * The "disaster type" is therefore the 민방위 경보 in force (or none, while simply evacuating).
 */
enum class DisasterType(val label: String) {
    /** No alert in force: on the road, evacuating. */
    EVACUATION("피난 중"),
    ALERT("경계경보"),
    AIR_RAID("공습경보"),
    CBRN("화생방경보"),
}

/** What the app tells people about the windows, which also decides how far sound reception reaches. */
enum class WindowAdvice { OPEN_A_LITTLE, KEEP_CLOSED, NO_ADVICE }

enum class SourceKind(val label: String) {
    KOREA_OFFICIAL("정부"),
    KOREA_LOCAL("지자체"),
    LAW("법령"),
    FOREIGN_OFFICIAL("해외 공식"),
    NEWS("언론 보도"),
}

/** [org] is the short publisher name shown on screen; [name] and [url] stay for the documents. */
data class Source(val name: String, val url: String, val kind: SourceKind, val org: String)

/** One action line; [foreign] marks guidance taken from a foreign official source. */
data class Step(val text: String, val foreign: Boolean = false)

data class DisasterGuide(
    val type: DisasterType,
    val actions: List<Step>,
    val avoid: List<Step>,
    val carRisk: String?,
    val window: WindowAdvice,
    /** Switch on blackout (no exterior lights) when this stage is chosen. */
    val blackout: Boolean,
    val sources: List<Source>,
)

/**
 * In-car guidance per alert stage, offline. Every line is taken from Korean official guidance
 * (행정안전부 국민행동요령: 민방공 경보, 비상사태, 통신 장애, 화학사고; checked 2026-11-11), split by
 * stage; nothing is added that the sources do not say.
 */
object DisasterGuides {
    private fun sk(category: String) =
        "https://www.safekorea.go.kr/safekorea-kor/acts/nacts/action-guide.do?category=$category&menuSn=4"

    private fun official(name: String, category: String) = Source(name, sk(category), SourceKind.KOREA_OFFICIAL, "행정안전부")

    private val airDefense = official("국민행동요령 · 민방공 경보", "civlAirDefenseWarning")
    private val emergency = official("국민행동요령 · 비상사태", "stateOfEmergency")
    private val telecom = official("국민행동요령 · 통신 장애", "infocommAccident")
    private val chemical = official("국민행동요령 · 화학사고", "chemicalAccidentDisaster")

    private const val TRAFFIC_LIMIT = "비상사태가 선포되면 일반 차량 운행이 제한될 수 있어요"

    val all: List<DisasterGuide> = listOf(
        DisasterGuide(
            type = DisasterType.EVACUATION,
            actions = listOf(
                Step("도로 오른쪽이나 빈터에 차를 세웁니다"),
                Step("재난방송·라디오로 상황을 확인합니다"),
                Step("통신이 안 돼도 119·112 긴급전화는 시도해 봅니다"),
                Step("긴급전화가 안 되면 유심을 빼고 다시 걸어 봅니다"),
            ),
            avoid = listOf(Step("상황을 모른 채 무리하게 이동하지 않습니다")),
            carRisk = TRAFFIC_LIMIT,
            window = WindowAdvice.OPEN_A_LITTLE,
            blackout = false,
            sources = listOf(telecom, emergency),
        ),
        DisasterGuide(
            type = DisasterType.ALERT,
            actions = listOf(
                Step("천천히 운행하고 고가도로·도심 진입을 피합니다"),
                Step("재난방송·라디오로 상황을 확인합니다"),
            ),
            avoid = listOf(Step("고가도로·도심에 들어가지 않습니다")),
            carRisk = TRAFFIC_LIMIT,
            window = WindowAdvice.NO_ADVICE,
            blackout = false,
            sources = listOf(airDefense, telecom, emergency),
        ),
        DisasterGuide(
            type = DisasterType.AIR_RAID,
            actions = listOf(
                Step("빈터나 도로 오른쪽에 세우고 승객을 대피시킵니다"),
                Step("키는 차에 둔 채로 대피합니다"),
                Step("밤에는 전조등·미등·실내등을 끈 뒤 대피합니다"),
            ),
            avoid = listOf(Step("밤에 차 불빛을 켜 두지 않습니다")),
            carRisk = null,
            window = WindowAdvice.NO_ADVICE,
            blackout = true,
            sources = listOf(airDefense),
        ),
        DisasterGuide(
            type = DisasterType.CBRN,
            actions = listOf(
                Step("오염 지역을 벗어나거나 오른쪽에 세우고 보호장비를 쓴 뒤 건물 안으로 대피합니다"),
                Step("창문을 닫고 에어컨·히터를 끕니다"),
                Step("공조를 내기순환으로 바꿔 바깥 공기를 막습니다"),
                Step("바람이 오염 지점에서 내 쪽으로 불면 바람과 직각 방향으로 피합니다"),
                Step("노출됐다면 입과 코를 막고 멀리 떨어진 뒤 신고합니다"),
            ),
            avoid = listOf(
                Step("바깥 공기를 들인 채 오염 지역을 지나가지 않습니다"),
                Step("오염된 물건을 맨손으로 만지지 않습니다"),
            ),
            carRisk = "공조장치로 바깥 공기가 들어올 수 있어요. 창문을 닫으면 소리 신호가 닿는 거리가 줄어요",
            window = WindowAdvice.KEEP_CLOSED,
            blackout = false,
            sources = listOf(airDefense, chemical),
        ),
    )

    fun of(type: DisasterType): DisasterGuide = all.first { it.type == type }

    /**
     * The alert stage an official message points to. The server still classifies messages by the
     * older disaster names (AIR_RAID, CHEMICAL, RADIATION, ...); the words decide the stage here.
     * Drill messages (훈련) suggest nothing.
     */
    fun stageOf(serverType: String?, text: String): DisasterType? = when {
        text.contains("훈련") -> null
        text.contains("화생방") || serverType == "CHEMICAL" || serverType == "RADIATION" -> DisasterType.CBRN
        text.contains("공습") || text.contains("미사일") -> DisasterType.AIR_RAID
        text.contains("경계경보") -> DisasterType.ALERT
        serverType == "AIR_RAID" -> DisasterType.ALERT
        else -> null
    }
}
