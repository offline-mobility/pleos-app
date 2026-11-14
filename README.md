# Offline Mobility

폭설이나 비상 대피길 정체로 밖에 나갈 수 없어 차 안에서 오래 기다려야 할 때, 차량 에너지를 아끼며 버티고, 도움을 요청하고, 가까운 대피소와 시설을 통신 없이도 찾게 해 주는 차량용 앱입니다. Pleos Connect용입니다.

## 주요 기능

- **저전력 모드:** 전기차에서 기다리는 동안 히터 설정 온도를 낮추고 시트·핸들 열선으로 보완해 배터리로 버틸 수 있는 시간을 늘립니다. 시작 전에 버틸 수 있는 시간을 추정해 보여 줍니다. P단에서 사용자가 시작했을 때만 제어하고, 멈추거나 P단을 벗어나면 원래 공조 설정으로 되돌립니다.
- **주변 시설:** 전국 민방위대피소 1.7만 곳, 약국 2.6만 곳, 종합병원 384곳, 편의점 5.6만 곳을 앱에 넣어 통신 없이 가까운 순으로 보여 줍니다. `길 안내`를 누르면 차량 내비게이션에 목적지로 넘깁니다(NaviHelper SDK).
- **도움 요청:** 7가지(연료·전력 부족, 고립됨, 추위·저체온, 응급 환자, 식수·식량 필요, 약 필요, 도움 필요) 중 하나를 탑승 인원, 위치(약 40m)와 함께 보냅니다. 통신이 되면 구조 서버로 보내고, 끊겨 있으면 기기 안에 암호화해 두었다가 통신이 돌아오면 보냅니다. QR 코드로 앱이 없는 사람에게 전달을 부탁할 수도 있습니다.
- **상황별 행동요령 9개:** 민방위 경보 4단계(피난 중·경계경보·공습경보·화생방경보)와 대설·한파, 호우·침수, 지진, 산불, 태풍·강풍의 차 안 행동요령(행정안전부 국민행동요령)을 오프라인으로 보여 줍니다.
- **경보에 맞춘 차량 조치:** 공습경보에는 차가 전조등·미등·실내등을 끄고(등화관제), 화생방경보와 산불에는 창문을 닫고 공조를 내기순환으로 바꿉니다(외기 차단). P단에서만 실행하고, 상황이 끝나면 조명과 공조를 되돌립니다.
- **차량 간 신호(통신 두절 시 보조 수단):** 같은 요청을 차량 실내 스피커의 소리(3.3초 프레임)와 외부 조명의 조난 점멸로 주변 차량에 보냅니다. 받은 차량은 "응급 환자 2명, 북동쪽 약 300m"처럼 표시하고 음성으로 읽어 주며, 최대 3대까지 이어서 전달합니다.
- **화면 밖 수신:** 포그라운드 서비스로 앱을 내려도 수신·중계를 계속합니다.
- **안전 잠금:** 송신과 차량 제어는 P단에서만 합니다. P단을 벗어나면 송신을 즉시 멈춥니다.

## 알려진 한계

- 차량 간 소리 신호는 실내 스피커로 냅니다(Pleos SDK에 외부 스피커·경적 기능이 없음). 창문을 닫은 실차 사이의 전달 거리는 측정하지 못했습니다.
- 저전력 모드가 보여 주는 시간은 바깥 영하 15도를 가정한 추정입니다. 실측값이 아닙니다.
- 시설 목록은 위치만 알려 줍니다. 영업 여부는 알 수 없습니다.
- 등화관제 중에 P단을 벗어나면 차량이 조명 제어를 거부해 앱이 불을 다시 켤 수 없습니다. 이때 음성으로 전조등을 직접 켜라고 알립니다.
- 통신이 끊겼을 때 차량 내비게이션이 경로를 계산하는지는 확인하지 못했습니다.
- 구조 서버는 이 프로젝트의 서버입니다. 실제 구조기관 접수와 연결되어 있지 않습니다.

## 실행 방법

### APK 설치

```bash
adb install apk/OfflineMobility_release.apk   # 제출용 release APK (제출 시 추가)
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 소스 빌드

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew testDebugUnitTest assembleDebug

# release (R8 축소·서명). 서명 키는 저장소에 없으며 keystore.properties로 지정합니다.
./gradlew assembleRelease -PserverUrl=https://offline-mobility.vercel.app   # 비우면 온라인 기능이 꺼집니다

# 구조 서버 (로컬)
cd server && cp .env.example .env && npm run dev   # http://localhost:8787
./gradlew assembleDebug -PserverUrl=http://10.0.2.2:8787   # 에뮬레이터에서 로컬 서버 사용
```

## 테스트 환경 및 필요한 설정

| 항목 | 값 |
|---|---|
| IDE | Android Studio 최신 버전, JDK 17 |
| SDK | compileSdk 35, minSdk 28 |
| 에뮬레이터 | Pleos Connect Emulator v2.1.0 (API 34, Cold boot, RAM 4GB). 설치 방법은 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) |
| 차량 SDK | Pleos Vehicle SDK 2.3.2 (`ai.pleos.playground:Vehicle`), NaviHelper SDK 3.0.5, TextToSpeech SDK 3.3.1 |
| 권한 | 최초 실행 시 마이크·위치·알림 허용. Pleos: `CAR_LIGHTS`, `CONTROL_CAR_LIGHTS`, `CAR_DRIVE`, `CAR_ENERGY`, `CAR_CLIMATE`, `CONTROL_CAR_CLIMATE`, `CAR_SEATS`, `CONTROL_CAR_SEATS`, `CAR_WINDOWS`, `CONTROL_CAR_WINDOWS`, `NAVI_ROUTE_SEARCH`, `TTS_SERVICE` |

**에뮬레이터에서 확인하는 방법**
1. 에뮬레이터를 실행하고 `adb forward tcp:8088 tcp:8088` 후 브라우저로 `http://127.0.0.1:8088/`의 Virtual Console을 엽니다. 콘솔은 브라우저가 열려 있는 동안만 차량 상태를 바꿀 수 있습니다.
2. Motion의 Gear Selection을 P로 둡니다. D로 바꾸면 송신과 차량 제어가 막히고, 송신 중이면 즉시 중단됩니다.
3. 저전력 모드: 첫 화면 가운데 `저전력 모드` > `시작하기`. Virtual Console의 Climate에서 설정 온도 16도, 시트 열선 2단, A/C 꺼짐으로 바뀌는지 확인합니다. `그만하기`를 누르거나 기어를 D로 바꾸면 시작 전 값으로 돌아옵니다.
4. 주변 시설: 첫 화면 `주변 시설`에서 종류(대피소·약국·종합병원·편의점)를 고르고 한 곳을 눌러 `길 안내`를 누릅니다. 차량 내비게이션에 경로가 뜹니다.
5. 공습경보 소등: Virtual Console의 Exterior Lights Mode를 Low beam, Cabin Lights Switch를 On으로 둔 뒤 `행동요령` > `공습경보`를 고릅니다. 둘 다 꺼지고 화면에 "차 불을 모두 껐어요"가 뜹니다. 다른 상황을 고르면 원래대로 켜집니다.
6. 화생방경보 외기 차단: Window Position으로 창문을 열고 HVAC Power를 켠 뒤 `행동요령` > `화생방경보`를 고릅니다. 창문이 닫히고 Air Recirculation이 켜지며 공조가 꺼집니다.
7. 도움 요청: 칸을 고르고 `보내기`를 누릅니다. Exterior Lights Mode에서 하향등 점멸을 확인합니다. 에뮬레이터는 상향등 스위치 명령만 받고 실제 상향등 상태는 바꾸지 않으므로, 화면 결과에 "점등 미확인 상향등"으로 표시됩니다. 서버 주소를 넣어 빌드한 경우(`-PserverUrl=`) 요청이 그 서버로도 전송됩니다.
8. 소리 수신: 에뮬레이터를 `-allow-host-audio`로 실행하고 `adb emu avd hostmicon`으로 호스트 마이크 입력을 켜면, 다른 기기에서 재생한 신호를 수신합니다. 테스트용 신호 WAV는 아래 명령으로 만듭니다.
   ```bash
   OM_WAV_DIR=/tmp/om ./gradlew testDebugUnitTest --tests '*WavFixtureExport*'
   afplay /tmp/om/frame_1_medical.wav
   ```
9. 두 번째 기기 없이 확인하려면 `설정 > 받은 신호 > 다른 차 신호 받아 보기 (시연)`를 누릅니다. 다른 차량의 신호를 합성하고 잡음과 반향을 더한 뒤, 실제 수신기로 해독합니다.
10. 서버 주소를 넣어 빌드한 경우, 보내는 중 화면의 `앱이 없는 사람에게 QR로 전달`에서 QR 코드를 확인합니다.

차량 서비스에 연결되지 않으면 연습 모드로 실행됩니다. 이때 `설정`의 `기어 P`, `기어 D` 버튼으로 기어를 바꿀 수 있습니다.

## 구조

```text
app/src/main/java/.../
  core/cabin/      난방 절약 계획: 버틸 수 있는 시간 추정, 히터·환기 주기 판단
  core/energy/     배터리·연료 기반 지속 시간 추정
  core/online/     앱에 든 대피소·시설 목록 읽기(가까운 순, 지역별), 서버 응답 해석
  core/position/   GPS 값 검증(불가능한 순간 이동 걸러 내기), 보낼 수 있는 위치 판단
  core/disaster/   상황 9개(민방위 경보 4단계, 자연재난 5종) 행동요령과 앱 동작
  core/protocol/   RescueFrame(72bit, CRC-8 포함), Reed-Solomon(15,9) over GF(16) 2개 교차 배치
  core/acoustic/   16-FSK 변조, 처프 동기(FFT 정합 필터), Goertzel 복조, GMD 복호
  core/light/      SOS·조난 비컨·응답 점멸 패턴, 조명 복구 순서
  core/relay/      중복 억제, 홉 제한, 무작위 백오프
  core/safety/     P단 송신 잠금
  core/gateway/    구조 서버 전송 형식, QR 전달 주소
  vehicle/         Pleos Vehicle SDK 연동(Light, Gear, EvBattery, Fuel, HVAC, Seat, SteeringWheel, Window)과 NaviHelper SDK(길 안내). 조명 패턴·난방 계획·등화관제·외기 차단 실행과 복원
  ui/              화면, 색·글자 기준, 문구
  gateway/         암호화 보관함, HTTPS 전송
  online/, net/    재난문자·대피소·주변 구조 요청 동기화, 서버 통신
  location/        GPS
  audio/           AudioTrack 송신(오디오 포커스), AudioRecord 수신, 음성 안내
server/               구조 서버와 구조 상황판 (Node, Vercel)
  data/               앱에 넣는 공공 데이터: 민방위대피소, facilities/(약국·종합병원·편의점). 빌드할 때 앱 assets로 복사
  scripts/            데이터 갱신 (`npm run update-civil-shelters`, `npm run update-facilities`)
docs/
  PROPOSAL.md         기획서 초안
  UI_DESIGN.md        화면 설계
  GATEWAY_API.md      구조 서버 API 명세
  SECURITY.md         보안·개인정보 점검
  WORKPLAN.md         작업 계획
  CONTEST_RESEARCH.md 공모전·Pleos 조사 자료
  DEVELOPMENT.md      개발 환경
```

## 데이터 출처

| 데이터 | 출처 |
|---|---|
| 민방위대피소 | 행정안전부 (재난안전데이터공유플랫폼) |
| 약국·종합병원 | 건강보험심사평가원 전국 병의원 및 약국 현황 (2026-09-30, 공공누리 1유형) |
| 편의점 | 소상공인시장진흥공단 상가(상권)정보 (2026-09-30) |
| 행동요령 | 행정안전부 국민행동요령 (국민재난안전포털) |

## 주의

Pleos SDK와 에뮬레이터는 현대자동차그룹의 별도 이용 조건을 따릅니다. 이 저장소의 MIT 라이선스는 이 프로젝트에서 작성한 코드에만 적용됩니다.
