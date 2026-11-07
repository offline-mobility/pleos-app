# Offline Mobility

현대자동차그룹 차량용 앱 개발 공모전을 위한 Android Automotive 기반 재난 통신 프로토타입입니다.

기지국과 인터넷이 끊긴 상황에서 정차한 차량끼리 광학·음향 패턴으로 구조 요청과 도로 정보를 주고받는 경험을 검증합니다. 현재 버전은 메시지 프리셋, 신호 인코딩, 송수신 데모와 안전 잠금을 구현하며 실제 차량 제어는 검증된 SDK 범위에서 단계적으로 연결합니다.

## 기술 스택

- Kotlin 2.0
- Jetpack Compose
- Android SDK 35, minSdk 28
- JDK 17
- Gradle 8.10.2
- Pleos Vehicle SDK 2.3.2
- Pleos Connect Emulator 2.1.0

## 시작하기

1. 최신 Android Studio와 Android SDK Platform 35를 설치합니다.
2. [개발 환경 설정](docs/DEVELOPMENT.md)에 따라 Pleos Connect Emulator를 추가합니다.
3. JDK 17로 프로젝트를 빌드합니다.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew testDebugUnitTest assembleDebug
```

4. Android Studio에서 Pleos Connect v2.1.0 AVD를 선택해 실행합니다.

## 프로젝트 구조

```text
app/
  src/main/
    java/.../MainActivity.kt       차량용 Compose 송수신 화면
    java/.../MainViewModel.kt      SDK 연결·송수신 상태 관리
    java/.../EmergencyProtocol.kt  메시지 프리셋과 펄스 인코더
    java/.../PleosVehicleClient.kt Vehicle SDK 생명주기
docs/
  DEVELOPMENT.md                   에뮬레이터·빌드·테스트 안내
  PRODUCT_BRIEF.md                 아이디어와 SDK 매핑 템플릿
```

## 구현 현황

- [x] Offline Mobility 핵심 사용자 시나리오
- [x] 재난 메시지 프리셋과 모스 펄스 인코더
- [x] 광학·음향 송신 UI와 수신 시뮬레이션
- [x] 주행 중 송신 차단 데모
- [ ] Gear/Speed Capability 조회와 안전 상태 연동
- [ ] 검증된 Vehicle API 기반 광학 신호 출력
- [ ] 음향 신호 출력·수신 프로토타입
- [ ] Virtual Console 시연 시나리오와 영상

## 주의

Pleos SDK와 에뮬레이터는 현대자동차그룹의 별도 이용 조건을 따릅니다. 이 저장소의 MIT 라이선스는 이 프로젝트에서 작성한 코드에만 적용됩니다.

