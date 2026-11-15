# 폴라리스

> 지도도 통신도 없던 시절, 사람들은 북극성(폴라리스)을 보고 길을 찾았습니다.
>
> 통신이 끊긴 재난상황 속에서, 북극성의 역할이 되어줍니다.

차 밖으로 나갈 수 없어 차 안에서 오래 기다려야 할 때 쓰는 Pleos Connect 차량용 앱입니다.
차량 에너지를 아껴 더 오래 버티게 하고, 통신이 끊겨도 도움 요청을 차에서 차로 이어 전달합니다.

| 항목 | 내용 |
|---|---|
| 제출용 APK | [`apk/패트와매트_폴라리스_release.apk`](apk/패트와매트_폴라리스_release.apk) |
| 에뮬레이터 | Pleos Connect Emulator v2.1.0 (API 34) |
| 화면 규격 | 2560×1440, 160dpi, 가로 |

## 주요 기능

### 1. 저전력 모드

전기차의 히터 설정 온도를 낮추고 시트와 핸들 열선으로 보완해 배터리로 버티는 시간을 늘립니다.

- 시작하기 전에 버틸 수 있는 시간을 추정해 보여 줍니다.
- P단에서 사용자가 시작했을 때만 차량을 제어합니다.
- 멈추거나 P단을 벗어나면 공조를 시작 전 설정으로 되돌립니다.

### 2. 차에서 차로 전달

도움 요청을 실내 스피커의 소리와 외부 조명의 점멸로 주변 차량에 보냅니다.

- **요청 7가지:** 연료나 전력 부족, 고립, 추위와 저체온, 응급 환자, 식수와 식량, 약, 그 밖의 도움 요청
- **함께 보내는 정보:** 탑승 인원, 위치(약 40m 단위)
- **받은 차량:** "응급 환자 2명, 북동쪽 약 300m"처럼 표시하고 음성으로 읽어 준 뒤, 다음 차량으로 다시 보냅니다.
- **중계 제한:** 최대 3대까지 이어지고, 같은 요청은 한 번만 중계합니다.
- **화면 밖 동작:** 앱을 화면에서 내려도 수신과 중계를 계속합니다.

소리 신호 하나의 길이는 3.3초입니다.

### 3. 함께 제공하는 기능

| 기능 | 내용 |
|---|---|
| 서버 전송과 보관 | 통신이 되면 같은 요청을 구조 서버로도 보냅니다. 끊겨 있으면 보낸 요청과 받은 요청을 기기에 암호화해 두었다가 통신이 돌아오면 보냅니다. |
| QR 전달 | 앱이 없는 사람이 QR 코드를 찍어 요청을 대신 전달할 수 있습니다. |
| 주변 시설 | 민방위대피소 1.7만 곳, 약국 2.6만 곳, 종합병원 384곳, 편의점 5.6만 곳을 앱에 담아 통신 없이 가까운 순으로 보여 줍니다. `길 안내`를 누르면 차량 내비게이션에 목적지를 넘깁니다. |
| 행동요령 | 9개 상황의 차 안 행동요령을 통신 없이 보여 줍니다. 민방위 경보 4단계(피난 중, 경계경보, 공습경보, 화생방경보)와 자연재난 5종(대설과 한파, 호우와 침수, 지진, 산불, 태풍과 강풍)입니다. |
| 경보에 맞춘 차량 조치 | 공습경보에는 전조등, 미등, 실내등을 끕니다. 화생방경보와 산불에는 창문을 닫고 공조를 내기순환으로 바꿉니다. 상황이 끝나면 되돌립니다. |
| 안전 잠금 | 송신과 차량 제어는 P단에서만 합니다. P단을 벗어나면 송신을 즉시 멈춥니다. |

## 실행 방법

### APK 설치

```bash
adb install "apk/패트와매트_폴라리스_release.apk"
```

처음 실행할 때 마이크, 위치, 알림 권한을 허용합니다.

### 소스 빌드

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# 단위 테스트 + debug APK (app/build/outputs/apk/debug/app-debug.apk)
./gradlew testDebugUnitTest assembleDebug

# release APK (R8 축소)
./gradlew assembleRelease -PserverUrl=https://offline-mobility.vercel.app
```

- `-PserverUrl`을 비우면 서버 전송과 QR 전달이 꺼집니다.
- release 서명 키는 저장소에 없습니다. 직접 서명하려면 `keystore.properties`에 키 정보를 넣습니다.

### 구조 서버를 로컬에서 실행 (선택)

```bash
cd server && cp .env.example .env && npm run dev            # http://localhost:8787
./gradlew assembleDebug -PserverUrl=http://10.0.2.2:8787    # 에뮬레이터에서 로컬 서버 사용
```

## 테스트 환경 및 필요한 설정

| 항목 | 값 |
|---|---|
| IDE | Android Studio 최신 버전, JDK 17 |
| SDK | compileSdk 35, minSdk 28 |
| 에뮬레이터 | Pleos Connect Emulator v2.1.0 (API 34, Cold boot, RAM 4GB). 설치 방법은 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) |
| 화면 규격 | 2560×1440, 160dpi, 가로 (에뮬레이터 기본 화면) |
| 차량 SDK | Pleos Vehicle SDK 2.3.2, NaviHelper SDK 3.0.5, TextToSpeech SDK 3.3.1 |
| 안드로이드 권한 | 마이크, 위치, 알림 (처음 실행할 때 허용) |
| Pleos 권한 | `CAR_LIGHTS`, `CONTROL_CAR_LIGHTS`, `CAR_DRIVE`, `CAR_ENERGY`, `CAR_CLIMATE`, `CONTROL_CAR_CLIMATE`, `CAR_SEATS`, `CONTROL_CAR_SEATS`, `CAR_WINDOWS`, `CONTROL_CAR_WINDOWS`, `NAVI_ROUTE_SEARCH`, `TTS_SERVICE` |

## 에뮬레이터에서 확인하는 방법

### 준비

1. 에뮬레이터를 Cold boot로 실행합니다.
2. `adb forward tcp:8088 tcp:8088`을 실행하고 브라우저에서 `http://127.0.0.1:8088/`(Virtual Console)을 엽니다. 콘솔은 브라우저가 열려 있는 동안만 차량 상태를 바꿉니다.
3. 콘솔의 Motion에서 Gear Selection을 P로 둡니다.

### 기능별 확인

| 기능 | 조작 | 결과 |
|---|---|---|
| 저전력 모드 | 첫 화면 `저전력 모드` > `시작하기` | 콘솔의 Climate에서 설정 온도 16도, 시트 열선 2단, A/C 꺼짐. `그만하기`를 누르거나 기어를 D로 바꾸면 시작 전 값으로 돌아옴 |
| 주변 시설 | 첫 화면 `주변 시설` > 종류 선택 > 한 곳 선택 > `길 안내` | 차량 내비게이션에 경로 표시 |
| 공습경보 소등 | 콘솔에서 Exterior Lights Mode를 Low beam, Cabin Lights Switch를 On으로 둔 뒤 `행동요령` > `공습경보` | 둘 다 꺼지고 "차 불을 모두 껐어요" 표시. 다른 상황을 고르면 다시 켜짐 |
| 화생방경보 외기 차단 | 콘솔에서 Window Position으로 창문을 열고 HVAC Power를 켠 뒤 `행동요령` > `화생방경보` | 창문이 닫히고 Air Recirculation이 켜지며 공조가 꺼짐 |
| 도움 요청 보내기 | 첫 화면에서 요청 칸 선택 > `보내기` | 콘솔의 Exterior Lights Mode에서 하향등 점멸. 제출용 APK는 구조 서버로도 전송 |
| QR 전달 | 보내는 중 화면의 `앱이 없는 사람에게 QR로 전달` | QR 코드 표시 |
| 신호 받기 | `설정` > `받은 신호` > `다른 차 신호 받아 보기 (시연)` | 다른 차량의 신호를 합성해 잡음과 반향을 더한 뒤 실제 수신기로 해독하고, 받은 신호로 표시 |
| P단 잠금 | 보내는 중에 콘솔의 Gear Selection을 D로 변경 | 송신 즉시 중단, 차량 제어 차단 |

- 에뮬레이터는 상향등 스위치 명령만 받고 실제 상향등 상태는 바꾸지 않습니다. 그래서 보내기 결과에 "점등 미확인 상향등"이 표시됩니다.
- 차량 서비스에 연결되지 않으면 연습 모드로 실행됩니다. 이때는 `설정`의 `기어 P`, `기어 D` 버튼으로 기어를 바꿉니다.

### 실제 소리로 수신 확인 (선택)

에뮬레이터를 `-allow-host-audio` 옵션으로 실행하고 호스트 마이크 입력을 켠 뒤, 다른 기기나 스피커로 신호를 재생합니다.

```bash
adb emu avd hostmicon
OM_WAV_DIR=/tmp/om ./gradlew testDebugUnitTest --tests '*WavFixtureExport*'   # 테스트용 신호 WAV 생성
afplay /tmp/om/frame_1_medical.wav
```

## 참고 사항

- 차량 간 소리 신호는 차량 실내 스피커로 보냅니다.
- 저전력 모드가 보여 주는 시간은 바깥 기온 영하 15도를 기준으로 계산한 예상값입니다.
- 구조 요청은 이 프로젝트의 구조 서버로 전송됩니다.

## 폴더 구조

```text
apk/                  제출용 release APK
app/src/main/java/.../
  core/               차량, 화면과 분리한 순수 로직 (단위 테스트 대상)
    cabin/            저전력 모드 계획: 버틸 수 있는 시간 추정, 히터와 환기 주기
    energy/           배터리와 연료 기반 지속 시간 추정
    protocol/         신호 형식: RescueFrame(72bit, CRC-8), Reed-Solomon(15,9) 2개 교차 배치
    acoustic/         소리 신호: 16-FSK 변조, 처프 동기, Goertzel 복조
    light/            조명 신호: SOS와 조난 점멸 패턴, 조명 복구 순서
    relay/            중계: 중복 억제, 홉 제한, 무작위 지연
    safety/           P단 송신 잠금
    position/         GPS 값 검증, 보낼 수 있는 위치 판단
    disaster/         상황 9개의 행동요령과 앱 동작
    online/           앱에 담은 대피소와 시설 목록(가까운 순), 서버 응답 해석
    gateway/          구조 서버 전송 형식, QR 전달 주소
  vehicle/            Pleos Vehicle SDK와 NaviHelper SDK 연동, 차량 제어 실행과 복원
  audio/              스피커 송신, 마이크 수신, 음성 안내
  gateway/            암호화 보관함, HTTPS 전송
  online/, net/       재난문자, 대피소, 주변 구조 요청 동기화, 서버 통신
  location/           GPS
  ui/                 화면, 색과 글자 기준, 문구
server/               구조 서버와 구조 상황판 (Node, Vercel)
  data/               앱에 담는 공공 데이터. 빌드할 때 앱 assets로 복사
  scripts/            데이터 갱신 (npm run update-civil-shelters, npm run update-facilities)
docs/                 기획서 초안, 화면 설계, 서버 API 명세, 보안 점검, 개발 환경
```

## 데이터 출처

| 데이터 | 출처 |
|---|---|
| 민방위대피소 | 행정안전부 (재난안전데이터공유플랫폼) |
| 약국, 종합병원 | 건강보험심사평가원 전국 병의원 및 약국 현황 (2026-09-30, 공공누리 1유형) |
| 편의점 | 소상공인시장진흥공단 상가(상권)정보 (2026-09-30) |
| 행동요령 | 행정안전부 국민행동요령 (국민재난안전포털) |

## 라이선스

이 프로젝트에서 작성한 코드는 MIT 라이선스입니다. Pleos SDK와 에뮬레이터는 현대자동차그룹의 별도 이용 조건을 따릅니다.
