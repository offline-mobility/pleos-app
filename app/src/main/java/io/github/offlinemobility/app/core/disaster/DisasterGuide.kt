package io.github.offlinemobility.app.core.disaster

import io.github.offlinemobility.app.core.protocol.RescueMessage
import io.github.offlinemobility.app.core.protocol.RescueMessage.CBRN
import io.github.offlinemobility.app.core.protocol.RescueMessage.COLD
import io.github.offlinemobility.app.core.protocol.RescueMessage.COLLAPSE
import io.github.offlinemobility.app.core.protocol.RescueMessage.FIRE
import io.github.offlinemobility.app.core.protocol.RescueMessage.FLOOD
import io.github.offlinemobility.app.core.protocol.RescueMessage.FOOD
import io.github.offlinemobility.app.core.protocol.RescueMessage.MEDICAL
import io.github.offlinemobility.app.core.protocol.RescueMessage.MEDICINE
import io.github.offlinemobility.app.core.protocol.RescueMessage.POWER
import io.github.offlinemobility.app.core.protocol.RescueMessage.ROAD_BLOCKED
import io.github.offlinemobility.app.core.protocol.RescueMessage.ROUTE_CLEAR
import io.github.offlinemobility.app.core.protocol.RescueMessage.SHELTER
import io.github.offlinemobility.app.core.protocol.RescueMessage.TRAPPED
import io.github.offlinemobility.app.core.protocol.RescueMessage.VULNERABLE
import io.github.offlinemobility.app.core.protocol.RescueMessage.WATER

enum class DisasterType(val label: String) {
    GENERAL("일반 재난"),
    EARTHQUAKE("지진"),
    WILDFIRE("산불"),
    FLOOD("호우·침수"),
    TYPHOON("태풍·강풍"),
    SNOW_COLD("대설·한파"),
    HEAT("폭염"),
    CHEMICAL("화학사고"),
    RADIATION("방사능"),
    AIR_RAID("공습·민방공"),
    TUNNEL("터널 사고"),
    LANDSLIDE("산사태·낙석"),
    BLACKOUT("정전·통신 장애"),
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
    /** The five messages that matter most in this situation, in order. */
    val primary: List<RescueMessage>,
    val window: WindowAdvice,
    /** Switch on blackout (no exterior lights) when this mode is chosen. */
    val blackout: Boolean,
    val sources: List<Source>,
) {
    /**
     * The eight messages on the first screen (the ninth cell opens the rest): [FIRST_SCREEN_FIXED] in
     * the same places whatever the situation, then one cell for this situation — its first message
     * that is not already there, or [FIRST_SCREEN_SPARE] when it has none.
     */
    val firstScreen: List<RescueMessage>
        get() = FIRST_SCREEN_FIXED +
            (primary.firstOrNull { it !in FIRST_SCREEN_FIXED && it !in RescueMessage.RETIRED } ?: FIRST_SCREEN_SPARE)

    companion object {
        /**
         * Always on the first screen, in this order. SOS comes first: it is the one to press when
         * nothing else fits or there is no time to choose.
         */
        val FIRST_SCREEN_FIXED = listOf(RescueMessage.SOS, TRAPPED, POWER, FLOOD, FIRE, WATER, MEDICAL)

        /** The situation cell when the situation has nothing beyond [FIRST_SCREEN_FIXED] (일반 재난). */
        val FIRST_SCREEN_SPARE = COLD
    }
}

/**
 * Offline in-car guidance per disaster. Wording follows Korean official guidance (행정안전부
 * 국민안전24 국민행동요령 and related ministries) checked 2026-11-11. Items from foreign official
 * sources are marked; points where sources conflict or no official guidance exists are left out.
 */
object DisasterGuides {
    private fun sk(category: String) =
        "https://www.safekorea.go.kr/safekorea-kor/acts/nacts/action-guide.do?category=$category&menuSn=4"

    private fun official(name: String, category: String) = Source(name, sk(category), SourceKind.KOREA_OFFICIAL, "행정안전부")

    val all: List<DisasterGuide> = listOf(
        DisasterGuide(
            type = DisasterType.GENERAL,
            actions = listOf(
                Step("도로 오른쪽이나 빈터에 차를 세웁니다"),
                Step("재난방송·라디오로 상황을 확인합니다"),
                Step("통신이 안 돼도 119·112 긴급전화는 시도해 봅니다"),
                Step("긴급전화가 안 되면 유심을 빼고 다시 걸어 봅니다"),
            ),
            avoid = listOf(Step("상황을 모른 채 무리하게 이동하지 않습니다")),
            carRisk = null,
            primary = listOf(MEDICAL, TRAPPED, FIRE, FLOOD, WATER),
            window = WindowAdvice.OPEN_A_LITTLE,
            blackout = false,
            sources = listOf(official("국민행동요령 · 통신 장애", "infocommAccident")),
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
            primary = listOf(MEDICAL, TRAPPED, COLLAPSE, ROAD_BLOCKED, SHELTER),
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
            primary = listOf(FIRE, MEDICAL, TRAPPED, ROAD_BLOCKED, ROUTE_CLEAR),
            window = WindowAdvice.KEEP_CLOSED,
            blackout = false,
            sources = listOf(
                official("국민행동요령 · 산불", "forestFires"),
                Source("호주 빅토리아주 소방청(CFA) 차 안에서 안전하게", "https://www.cfa.vic.gov.au/plan-prepare/staying-safe-in-the-car", SourceKind.FOREIGN_OFFICIAL, "호주 빅토리아주 소방청"),
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
            primary = listOf(FLOOD, TRAPPED, MEDICAL, ROAD_BLOCKED, ROUTE_CLEAR),
            window = WindowAdvice.NO_ADVICE,
            blackout = false,
            sources = listOf(
                official("국민행동요령 · 침수", "inundation"),
                official("국민행동요령 · 호우", "heavyRain"),
                Source("미국 기상청(NWS) Turn Around Don't Drown", "https://www.weather.gov/safety/flood-turn-around-dont-drown", SourceKind.FOREIGN_OFFICIAL, "미국 기상청"),
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
            primary = listOf(TRAPPED, MEDICAL, ROAD_BLOCKED, FLOOD, SHELTER),
            window = WindowAdvice.NO_ADVICE,
            blackout = false,
            sources = listOf(official("국민행동요령 · 태풍", "typhoon"), official("국민행동요령 · 강풍", "strongWind")),
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
                Step("앞유리에 뜨거운 물을 붓지 않습니다"),
            ),
            carRisk = "배기구가 눈에 막힌 채 시동을 켜 두면 일산화탄소 중독 위험이 있어요",
            primary = listOf(COLD, TRAPPED, POWER, FOOD, MEDICAL),
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
            type = DisasterType.HEAT,
            actions = listOf(
                Step("물을 자주 마시고 카페인·술은 피합니다"),
                Step("어지럽거나 두통·근육경련이 있으면 시원한 곳에서 쉽니다"),
                Step("시원한 음료를 천천히 마십니다"),
            ),
            avoid = listOf(Step("창문이 닫힌 차 안에 어린이·어르신을 혼자 두지 않습니다")),
            carRisk = "여름 한낮에 세워 둔 차 안은 60℃를 넘을 수 있어요",
            primary = listOf(MEDICAL, WATER, VULNERABLE, POWER, TRAPPED),
            window = WindowAdvice.NO_ADVICE,
            blackout = false,
            sources = listOf(
                official("국민행동요령 · 폭염", "heatWave"),
                Source("세계일보, 도로교통공단 차 실내 온도 측정 (2026.8.6)", "https://www.segye.com/newsView/20260806509056", SourceKind.NEWS, "도로교통공단"),
            ),
        ),
        DisasterGuide(
            type = DisasterType.CHEMICAL,
            actions = listOf(
                Step("창문을 닫고 에어컨·히터를 끕니다"),
                Step("공조를 내기순환으로 바꿔 바깥 공기를 막습니다"),
                Step("바람이 사고 지점에서 내 쪽으로 불면 바람과 직각 방향으로 피합니다"),
                Step("재난문자와 경찰·공무원의 대피 안내를 따릅니다"),
                Step("노출됐다면 입과 코를 막고 멀리 떨어진 뒤 신고합니다"),
            ),
            avoid = listOf(
                Step("바깥 공기를 들인 채 사고 지역을 지나가지 않습니다"),
                Step("오염된 물건을 맨손으로 만지지 않습니다"),
            ),
            carRisk = "공조장치로 바깥 공기가 들어올 수 있어요. 창문을 닫으면 소리 신호가 닿는 거리가 줄어요",
            primary = listOf(CBRN, MEDICAL, TRAPPED, ROAD_BLOCKED, ROUTE_CLEAR),
            window = WindowAdvice.KEEP_CLOSED,
            blackout = false,
            sources = listOf(
                official("국민행동요령 · 화학사고", "chemicalAccidentDisaster"),
                Source("도봉구 화학사고 행동요령", "https://www.dobong.go.kr/Contents.asp?code=10010083&tab=20", SourceKind.KOREA_LOCAL, "도봉구"),
            ),
        ),
        DisasterGuide(
            type = DisasterType.RADIATION,
            actions = listOf(
                Step("실내 대피 안내를 받으면 바로 집이나 가까운 콘크리트 건물로 갑니다"),
                Step("건물 안에서는 문·창문을 닫고 에어컨·환풍기를 끕니다"),
                Step("대피 안내를 받으면 안내에 따라 구호소로 이동합니다"),
                Step("경찰 등 안전요원의 통제에 따릅니다"),
            ),
            avoid = listOf(
                Step("가족을 데리러 학교·병원으로 가지 않습니다"),
                Step("차 안을 대피 장소로 쓰지 않습니다", foreign = true),
            ),
            carRisk = null,
            primary = listOf(CBRN, MEDICAL, SHELTER, ROUTE_CLEAR, VULNERABLE),
            window = WindowAdvice.KEEP_CLOSED,
            blackout = false,
            sources = listOf(
                official("국민행동요령 · 원전사고", "nuclearAccident"),
                Source("경주시 방사능 방재", "https://www.gyeongju.go.kr/gersi/page.do?mnu_uid=4355", SourceKind.KOREA_LOCAL, "경주시"),
                Source("미국 질병통제예방센터(CDC) 방사능 비상", "https://www.cdc.gov/radiation-emergencies/faq/index.html", SourceKind.FOREIGN_OFFICIAL, "미국 질병통제예방센터"),
            ),
        ),
        DisasterGuide(
            type = DisasterType.AIR_RAID,
            actions = listOf(
                Step("경계경보: 천천히 운행하고 고가도로·도심 진입을 피합니다"),
                Step("공습경보: 빈터나 도로 오른쪽에 세우고 승객을 대피시킵니다"),
                Step("키는 차에 둔 채로 대피합니다"),
                Step("밤에는 전조등·미등·실내등을 끈 뒤 대피합니다"),
                Step("화생방경보: 오염 지역을 벗어나거나 오른쪽에 세우고 보호장비를 쓴 뒤 건물 안으로 대피합니다"),
            ),
            avoid = listOf(
                Step("경계경보 때 고가도로·도심에 들어가지 않습니다"),
                Step("밤 공습경보 때 차 불빛을 켜 두지 않습니다"),
            ),
            carRisk = "비상사태가 선포되면 일반 차량 운행이 제한될 수 있어요",
            primary = listOf(MEDICAL, TRAPPED, SHELTER, CBRN, ROAD_BLOCKED),
            window = WindowAdvice.NO_ADVICE,
            blackout = true,
            sources = listOf(
                official("국민행동요령 · 민방공 경보", "civlAirDefenseWarning"),
                official("국민행동요령 · 비상사태", "stateOfEmergency"),
            ),
        ),
        DisasterGuide(
            type = DisasterType.TUNNEL,
            actions = listOf(
                Step("갓길이나 비상주차대 쪽에 차를 세웁니다"),
                Step("시동을 끄고 키를 꽂아 둔 채 내립니다"),
                Step("휴대전화나 터널 안 긴급전화로 신고합니다"),
                Step("불이 작으면 소화기·소화전으로 끕니다"),
                Step("불이 커지면 연기 반대쪽으로, 유도등을 따라 밖이나 피난통로로 갑니다"),
            ),
            avoid = emptyList(),
            carRisk = "연기로 앞이 안 보이면 차를 세우고 걸어서 대피해야 해요",
            primary = listOf(FIRE, MEDICAL, TRAPPED, ROAD_BLOCKED, ROUTE_CLEAR),
            window = WindowAdvice.NO_ADVICE,
            blackout = false,
            sources = listOf(official("국민행동요령 · 도로터널사고", "roadTrafficAccident")),
        ),
        DisasterGuide(
            type = DisasterType.LANDSLIDE,
            actions = listOf(
                Step("천천히 달리고 앞차와 거리를 둡니다"),
                Step("산사태 위험 구간은 돌아가서 빨리 벗어납니다"),
                Step("고립되면 119에 신고하고, 통신이 안 되면 소리 지르기·호루라기·물건 두드리기로 알립니다"),
                Step("땅이 꺼져 차가 빠지면 시동을 끄고 경적과 비상등으로 구조를 요청합니다"),
            ),
            avoid = listOf(Step("물이 불어난 하천·계곡을 무리하게 건너지 않습니다")),
            carRisk = null,
            primary = listOf(COLLAPSE, TRAPPED, MEDICAL, ROAD_BLOCKED, ROUTE_CLEAR),
            window = WindowAdvice.OPEN_A_LITTLE,
            blackout = false,
            sources = listOf(official("국민행동요령 · 산사태", "landSlide"), official("국민행동요령 · 지반침하", "groundSubsidence")),
        ),
        DisasterGuide(
            type = DisasterType.BLACKOUT,
            actions = listOf(
                Step("신호등이 꺼지면 경찰 등 안내자의 통제에 따라 침착하게 움직입니다"),
                Step("재난방송을 듣습니다"),
                Step("통신이 안 돼도 119·112 긴급전화는 연결될 수 있습니다"),
                Step("긴급전화가 안 되면 유심을 빼고 다시 걸어 봅니다"),
                Step("재난 와이파이 'Public WiFi Emergency'를 찾아 연결합니다"),
            ),
            avoid = emptyList(),
            carRisk = null,
            primary = listOf(MEDICAL, POWER, WATER, MEDICINE, SHELTER),
            window = WindowAdvice.OPEN_A_LITTLE,
            blackout = false,
            sources = listOf(
                official("국민행동요령 · 정전", "powerOutagesandShortages"),
                official("국민행동요령 · 통신 장애", "infocommAccident"),
            ),
        ),
    )

    fun of(type: DisasterType): DisasterGuide = all.first { it.type == type }
}
