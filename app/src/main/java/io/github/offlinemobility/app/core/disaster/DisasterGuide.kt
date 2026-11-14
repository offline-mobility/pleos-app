package io.github.offlinemobility.app.core.disaster

/**
 * The nine situations with in-car guidance: the 민방위 경보 in force during a war evacuation (or
 * none, while simply evacuating), and five natural disasters people meet on the road.
 */
enum class DisasterType(val label: String) {
    /** No alert in force: on the road, evacuating. */
    EVACUATION("피난 중"),
    ALERT("경계경보"),
    AIR_RAID("공습경보"),
    CBRN("화생방경보"),
    SNOW_COLD("대설·한파"),
    FLOOD("호우·침수"),
    EARTHQUAKE("지진"),
    WILDFIRE("산불"),
    TYPHOON("태풍·강풍"),
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
 * In-car guidance per situation, offline. Wording follows Korean official guidance (행정안전부
 * 국민행동요령, checked 2026-11-11). The few lines taken from foreign official sources are marked
 * [Step.foreign]; nothing is added that the sources do not say.
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
        DisasterGuide(
            type = DisasterType.SNOW_COLD,
            actions = listOf(
                Step("고립되면 되도록 차 안에서 구조를 기다립니다"),
                Step("함께 있는 사람과 체온을 나누고, 한 명은 꼭 깨어 있습니다"),
                Step("손뼉을 치고 팔다리를 움직여 체온을 유지합니다"),
                Step("밤에는 실내등을 켜거나 색깔 있는 옷을 눈 위에 펼쳐 둡니다"),
                Step("차 주변 눈을 치워 배기구가 막히지 않게 합니다"),
                Step("히터를 켤 때는 창문을 조금 열어 둡니다"),
            ),
            avoid = listOf(
                Step("고립된 곳에서 무리하게 운전해 빠져나가지 않습니다"),
                Step("모두 함께 잠들지 않습니다"),
            ),
            carRisk = "배기구가 눈에 막힌 채 시동을 켜 두면 일산화탄소 중독 위험이 있어요",
            window = WindowAdvice.OPEN_A_LITTLE,
            blackout = false,
            sources = listOf(
                official("국민행동요령 · 대설", "heavySnow"),
                official("국민행동요령 · 한파", "coldWave"),
                Source("서울시 대설 행동요령", "https://news.seoul.go.kr/safe/?p=21201", SourceKind.KOREA_LOCAL, "서울시"),
                Source("미국 기상청(NWS) 겨울 폭풍 중", "https://www.weather.gov/safety/winter-during", SourceKind.FOREIGN_OFFICIAL, "미국 기상청"),
            ),
        ),
        DisasterGuide(
            type = DisasterType.FLOOD,
            actions = listOf(
                Step("침수된 지하차도에 들어섰다면 차를 두고 바로 밖으로 나갑니다"),
                Step("타이어 3분의 2가 잠기기 전에 저단 기어로 빠져나갑니다"),
                Step("움직일 수 없으면 시동이 꺼지기 전에 창문·썬루프를 엽니다"),
                Step("문이 안 열리면 목받침 아래 철제 봉으로 유리를 깹니다"),
                Step("안팎 물 높이 차이가 30cm 이하일 때 문을 열고 나옵니다"),
                Step("나온 뒤에는 높은 곳이나 차 지붕 위에서 구조를 기다립니다"),
            ),
            avoid = listOf(
                Step("조금이라도 물에 잠긴 지하차도와 도로를 지나가지 않습니다"),
                Step("차를 빼려고 지하주차장에 들어가지 않습니다"),
            ),
            carRisk = "물살이 30cm만 돼도 승용차가 떠내려갈 수 있어요",
            window = WindowAdvice.NO_ADVICE,
            blackout = false,
            sources = listOf(
                official("국민행동요령 · 침수", "inundation"),
                official("국민행동요령 · 호우", "heavyRain"),
                Source("미국 기상청(NWS) Turn Around Don't Drown", "https://www.weather.gov/safety/flood-turn-around-dont-drown", SourceKind.FOREIGN_OFFICIAL, "미국 기상청"),
            ),
        ),
        DisasterGuide(
            type = DisasterType.EARTHQUAKE,
            actions = listOf(
                Step("비상등을 켜고 서서히 속도를 줄입니다"),
                Step("도로 오른쪽에 차를 세웁니다"),
                Step("도로 가운데는 긴급차량을 위해 비워 둡니다"),
                Step("라디오로 정보를 확인합니다"),
                Step("키를 꽂아 두고 문을 잠그지 않은 채 대피합니다"),
                Step("운동장·공원 같은 넓은 곳까지 걸어서 갑니다"),
            ),
            avoid = listOf(Step("대피할 때 차를 이용하지 않습니다")),
            carRisk = "산 주변 도로라면 산사태가 함께 올 수 있어요",
            window = WindowAdvice.OPEN_A_LITTLE,
            blackout = false,
            sources = listOf(
                official("국민행동요령 · 지진", "earthQuake"),
                Source("광명시 지진 행동요령", "https://www.gm.go.kr/pt/partInfo/sf/emergencyact/naturedisaster/PTMN753.jsp", SourceKind.KOREA_LOCAL, "광명시"),
            ),
        ),
        DisasterGuide(
            type = DisasterType.WILDFIRE,
            actions = listOf(
                Step("산과 멀리 떨어진 도로로 이동합니다"),
                Step("산불이 번지는 경로에서 벗어납니다"),
                Step("연기 냄새가 나면 안내가 없어도 바로 대피합니다"),
                Step("재난문자와 현장 관계자 안내를 따릅니다"),
                Step("불길에 갇히면 수풀에서 먼 빈터에 세우고 비상등을 켭니다", foreign = true),
                Step("창문과 문을 꼭 닫고, 창문보다 낮게 몸을 숙이고 담요를 덮습니다", foreign = true),
            ),
            avoid = listOf(
                Step("불길이 지나가기 전에 차 밖으로 나가지 않습니다", foreign = true),
                Step("길을 막고 세우지 않습니다", foreign = true),
            ),
            carRisk = "연기로 앞이 안 보여 추돌 사고가 잦아요",
            window = WindowAdvice.KEEP_CLOSED,
            blackout = false,
            sources = listOf(
                official("국민행동요령 · 산불", "forestFires"),
                Source("호주 빅토리아주 소방청(CFA) 차 안에서 안전하게", "https://www.cfa.vic.gov.au/plan-prepare/staying-safe-in-the-car", SourceKind.FOREIGN_OFFICIAL, "호주 빅토리아주 소방청"),
            ),
        ),
        DisasterGuide(
            type = DisasterType.TYPHOON,
            actions = listOf(
                Step("속도를 줄이고 방어운전을 합니다"),
                Step("돌풍에 밀릴 수 있으니 옆 차로의 차와 거리를 둡니다"),
                Step("전깃줄이 차에 닿으면 차 안에 머물고 금속 부분을 만지지 않습니다"),
            ),
            avoid = listOf(
                Step("침수된 도로·지하차도·교량에 들어가지 않습니다"),
                Step("가로등·신호등·전신주·공사장 가까이 가지 않습니다"),
            ),
            carRisk = "간판·가로수가 떨어지거나 돌풍에 차선을 벗어날 수 있어요",
            window = WindowAdvice.NO_ADVICE,
            blackout = false,
            sources = listOf(official("국민행동요령 · 태풍", "typhoon"), official("국민행동요령 · 강풍", "strongWind")),
        ),
    )

    fun of(type: DisasterType): DisasterGuide = all.first { it.type == type }

    /**
     * The situation an official message points to. The server classifies messages by disaster name
     * (AIR_RAID, CHEMICAL, SNOW_COLD, ...); for a 민방위 경보 the words decide the stage. Drill
     * messages (훈련) suggest nothing.
     */
    fun stageOf(serverType: String?, text: String): DisasterType? = when {
        text.contains("훈련") -> null
        text.contains("화생방") || serverType == "CHEMICAL" || serverType == "RADIATION" -> DisasterType.CBRN
        text.contains("공습") || text.contains("미사일") -> DisasterType.AIR_RAID
        text.contains("경계경보") -> DisasterType.ALERT
        serverType == "AIR_RAID" -> DisasterType.ALERT
        else -> NATURAL.firstOrNull { it.name == serverType }
    }

    private val NATURAL = listOf(
        DisasterType.SNOW_COLD, DisasterType.FLOOD, DisasterType.EARTHQUAKE, DisasterType.WILDFIRE, DisasterType.TYPHOON,
    )
}
