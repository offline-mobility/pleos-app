# Offline Mobility

기지국과 인터넷이 끊긴 재난·비상 상황에서, 정차한 차량끼리 소리와 빛으로 구조 메시지를 주고받고 다음 차량으로 이어 전달하는 차량용 오프라인 비상 신호 앱입니다.

## 주요 기능

- **음향 데이터 통신:** 15종 구조 메시지와 탑승 인원을 차량 스피커로 1.7초 프레임에 실어 보냅니다. 수신 차량은 마이크로 받아 문장으로 표시합니다.
- **조난 점멸:** 상향등·후방 안개등으로 SOS(USCG 46 CFR 161.013-7 타이밍), 국제 산악 조난 신호(분당 6회), 응답 신호(분당 3회)를 보냅니다.
- **차량 간 중계:** 최대 3홉까지 중계하고, 같은 메시지는 한 번만 보냅니다. 내 신호가 중계되면 확인 표시가 뜹니다.
- **안전 잠금:** P단에서만 송신합니다. P단을 벗어나면 즉시 중단하고 조명을 원래대로 되돌립니다.
- **등화관제 모드:** 외부 조명을 쓰지 않고 소리로만 통신합니다.

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
```

## 테스트 환경 및 필요한 설정

| 항목 | 값 |
|---|---|
| IDE | Android Studio 최신 버전, JDK 17 |
| SDK | compileSdk 35, minSdk 28 |
| 에뮬레이터 | Pleos Connect Emulator v2.1.0 (API 34, Cold boot, RAM 4GB). 설치 방법은 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) |
| 차량 SDK | Pleos Vehicle SDK 2.3.2 (`ai.pleos.playground:Vehicle`) |
| 권한 | `RECORD_AUDIO`(최초 실행 시 허용), `pleos.car.permission.CAR_LIGHTS`, `CONTROL_CAR_LIGHTS`, `CAR_DRIVE` |

**에뮬레이터에서 확인하는 방법**
1. 에뮬레이터를 실행하고 `adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/`에서 Virtual Console을 엽니다.
2. Motion에서 Gear Selection을 P로 둡니다. D로 바꾸면 송신이 막히고, 송신 중이면 즉시 중단됩니다.
3. 메시지를 고르고 `보내기`를 누릅니다. Virtual Console의 High Beam 카드에서 점멸 상태를 확인합니다.
4. 에뮬레이터 Extended Controls > Microphone에서 호스트 마이크 입력을 켜면, 다른 기기에서 재생한 신호를 수신합니다.
5. 두 번째 기기 없이 확인하려면 `가상 수신`을 누릅니다. 다른 차량의 신호를 합성하고 잡음과 반향을 더한 뒤, 실제 수신기로 해독합니다.

차량 서비스에 연결되지 않으면 시뮬레이션 모드로 실행됩니다. 이때 상단의 `P로`, `D로` 버튼으로 기어를 바꿀 수 있습니다.

## 구조

```text
app/src/main/java/.../
  core/protocol/   RescueFrame(24bit + CRC-8), Reed-Solomon(14,8) over GF(16)
  core/acoustic/   16-FSK 변조, 처프 동기(FFT 정합 필터), Goertzel 복조, GMD 복호
  core/light/      SOS·조난 비컨·응답·위치 유도 점멸 패턴
  core/relay/      중복 억제, 홉 제한, 무작위 백오프
  core/safety/     P단 송신 잠금
  vehicle/         Pleos Vehicle SDK 연동(Light, Gear), 조명 패턴 실행·복원
  audio/           AudioTrack 송신(오디오 포커스), AudioRecord 수신
docs/
  PROPOSAL.md         기획서 초안
  CONTEST_RESEARCH.md 공모전·Pleos 조사 자료
  DEVELOPMENT.md      개발 환경
```

## 주의

Pleos SDK와 에뮬레이터는 현대자동차그룹의 별도 이용 조건을 따릅니다. 이 저장소의 MIT 라이선스는 이 프로젝트에서 작성한 코드에만 적용됩니다.
