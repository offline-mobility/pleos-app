# Offline Mobility

기지국과 인터넷이 끊긴 재난·비상 상황에서, 정차한 차량끼리 소리와 빛으로 구조 메시지를 주고받고 다음 차량으로 이어 전달하는 차량용 오프라인 비상 신호 앱입니다.

## 주요 기능

- **음향 데이터 통신:** 19종 구조 메시지, 탑승 인원, GPS 위치(약 40m)를 차량 스피커로 3.3초 프레임에 실어 보냅니다. 수신 차량은 마이크로 받아 "응급 환자 · 2명 · 북동쪽 약 300m"처럼 표시합니다.
- **조난 점멸:** 차량이 지원하는 외부 조명을 골라 SOS(USCG 46 CFR 161.013-7 타이밍), 국제 산악 조난 신호(분당 6회), 응답 신호(분당 3회)를 보냅니다. 후방 안개등이 없는 차량은 하향등과 상향등을 함께 점멸합니다.
- **차량 간 중계:** 최대 3홉까지 중계하고, 같은 메시지는 한 번만 보냅니다. 분당 6회로 제한해 가짜 신호가 증폭되지 않게 합니다.
- **재난 유형 모드 13종:** 공식 국민행동요령 기반의 차 안 행동요령을 오프라인으로 보여 주고, 상황에 맞는 메시지와 앱 동작(공습 시 등화관제, 화학사고 시 창문 닫기 안내)을 바꿉니다.
- **사람에게 직접 알리기:** 위급 메시지 뒤에 사람이 알아듣는 SOS 소리를 붙이고, 받은 신호를 음성으로 읽어 줍니다.
- **차량 에너지:** 배터리·연료 잔량으로 신호 지속 시간을 추정하고, 부족하면 불빛을 끄도록 안내합니다.
- **난방 아끼며 버티기:** 전기차에서 구조를 기다리는 동안 히터를 낮은 온도로 돌리고 시트·핸들 열선으로 보완해 배터리 사용 시간을 늘립니다. P단에서 사용자가 시작했을 때만 제어하고, 멈추거나 P단을 벗어나면 원래 공조 설정으로 되돌립니다.
- **QR 전달:** 보낸 구조 요청을 QR 코드로 띄웁니다. 앱이 없는 사람이 휴대폰으로 찍어 통신이 되는 곳에서 열면 구조 서버로 전달됩니다.
- **통신이 살아 있을 때:** 긴급재난문자 중 진행 중인 재난만 골라 맞는 재난 모드를 제안하고, 주변 대피소(통합대피소·민방위대피소)를 기기에 받아 둡니다. 공습은 민방위대피소, 지진은 옥외대피장소처럼 상황에 맞는 대피소를 먼저 보여 줍니다. 구조 요청은 구조 서버로도 보내고, 서버의 구조 상황판(지도)에 표시됩니다.
- **통신이 돌아왔을 때:** 끊긴 동안 받은 구조 요청을 기기 안에 암호화해 두었다가 구조 서버로 HTTPS 전송합니다.
- **화면 밖 수신:** 포그라운드 서비스로 앱을 내려도 수신·중계를 계속합니다.
- **안전 잠금:** P단에서만 송신하고, P단을 벗어나면 즉시 중단하고 조명을 원래대로 되돌립니다.

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
| 차량 SDK | Pleos Vehicle SDK 2.3.2 (`ai.pleos.playground:Vehicle`) |
| 권한 | 최초 실행 시 마이크·위치·알림 허용. Pleos: `CAR_LIGHTS`, `CONTROL_CAR_LIGHTS`, `CAR_DRIVE`, `CAR_ENERGY`, `CAR_CLIMATE`, `CONTROL_CAR_CLIMATE`, `CAR_SEATS`, `CONTROL_CAR_SEATS`, `TTS_SERVICE` |

**에뮬레이터에서 확인하는 방법**
1. 에뮬레이터를 실행하고 `adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/`에서 Virtual Console을 엽니다.
2. Motion에서 Gear Selection을 P로 둡니다. D로 바꾸면 송신이 막히고, 송신 중이면 즉시 중단됩니다.
3. 메시지를 고르고 `보내기`를 누릅니다. Virtual Console의 Exterior Lights Mode 카드에서 하향등 점멸을 확인합니다. 에뮬레이터는 상향등 스위치 명령만 받고 실제 상향등 상태는 바꾸지 않으므로, 화면 결과에 "점등 미확인 상향등"으로 표시됩니다.
4. 에뮬레이터를 `-allow-host-audio`로 실행하고 `adb emu avd hostmicon`으로 호스트 마이크 입력을 켜면, 다른 기기에서 재생한 신호를 수신합니다. 테스트용 신호 WAV는 아래 명령으로 만듭니다.
   ```bash
   OM_WAV_DIR=/tmp/om ./gradlew testDebugUnitTest --tests '*WavFixtureExport*'
   afplay /tmp/om/frame_1_medical.wav
   ```
5. 두 번째 기기 없이 확인하려면 `설정 > 다른 차 신호 받아 보기`를 누릅니다. 다른 차량의 신호를 합성하고 잡음과 반향을 더한 뒤, 실제 수신기로 해독합니다.
6. 첫 화면 아래 `난방 아끼며 오래 버티기`를 눌러 시작합니다. Virtual Console의 HVAC 카드에서 설정 온도 16도, 시트 열선 2단, A/C 꺼짐으로 바뀌는지 확인합니다. 기어를 D로 바꾸면 시작 전 값으로 돌아옵니다.
7. 서버 주소를 넣어 빌드한 경우(`-PserverUrl=`), 보내는 중 화면의 `앱이 없는 사람에게 QR로 전달`에서 QR 코드를 확인합니다.

차량 서비스에 연결되지 않으면 연습 모드로 실행됩니다. 이때 `설정`의 `기어 P`, `기어 D` 버튼으로 기어를 바꿀 수 있습니다.

## 구조

```text
app/src/main/java/.../
  core/protocol/   RescueFrame(72bit, CRC-8 포함), Reed-Solomon(15,9) over GF(16) 2개 교차 배치
  core/acoustic/   16-FSK 변조, 처프 동기(FFT 정합 필터), Goertzel 복조, GMD 복호
  core/light/      SOS·조난 비컨·응답·위치 유도 점멸 패턴
  core/relay/      중복 억제, 홉 제한, 무작위 백오프
  core/safety/     P단 송신 잠금
  vehicle/         Pleos Vehicle SDK 연동(Light, Gear, EvBattery, HVAC, Seat, SteeringWheel), 조명 패턴·난방 계획 실행과 복원
  ui/              화면, 색·글자 기준, 메시지·재난 문구
  core/disaster/   재난 유형 13종 행동요령과 앱 동작
  core/energy/     배터리·연료 기반 지속 시간 추정
  core/cabin/      난방 절약 계획: 버틸 수 있는 시간 추정, 히터·환기 주기 판단
  core/gateway/    구조 서버 전송 형식, QR 전달 주소
  gateway/         암호화 보관함, HTTPS 전송
  online/, net/    재난문자·대피소·주변 구조 요청 동기화, 서버 통신
  location/        GPS
  audio/           AudioTrack 송신(오디오 포커스), AudioRecord 수신
server/               구조 서버와 구조 상황판 (Node, Vercel). data/에 민방위대피소 전국 데이터(행정안전부, 재난안전데이터공유플랫폼)
docs/
  PROPOSAL.md         기획서 초안
  UI_DESIGN.md        화면 설계
  GATEWAY_API.md      구조 서버 API 명세
  SECURITY.md         보안·개인정보 점검
  WORKPLAN.md         작업 계획
  CONTEST_RESEARCH.md 공모전·Pleos 조사 자료
  DEVELOPMENT.md      개발 환경
```

## 주의

Pleos SDK와 에뮬레이터는 현대자동차그룹의 별도 이용 조건을 따릅니다. 이 저장소의 MIT 라이선스는 이 프로젝트에서 작성한 코드에만 적용됩니다.
