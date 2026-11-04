# HMG Pleos App Challenge

현대자동차그룹 차량용 앱 개발 공모전을 위한 Android Automotive 기반 Pleos Connect 스타터 프로젝트입니다.

현재 단계에서는 Pleos Vehicle SDK 연결과 개발환경 검증에 집중합니다. 앱 아이디어가 확정되면 필요한 차량 데이터, NaviHelper, 위치·음성 기능을 최소 권한 원칙으로 추가합니다.

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
    java/.../MainActivity.kt       Compose 진입 화면
    java/.../MainViewModel.kt      SDK 연결 상태 관리
    java/.../PleosVehicleClient.kt Vehicle SDK 생명주기
docs/
  DEVELOPMENT.md                   에뮬레이터·빌드·테스트 안내
  PRODUCT_BRIEF.md                 아이디어와 SDK 매핑 템플릿
```

## 다음 작업

- 공모전 아이디어와 핵심 사용자 시나리오 확정
- Vehicle API와 최소 Manifest 권한 매핑
- Capability 확인 → 조회 → 구독 흐름 구현
- Virtual Console 기반 데모 시나리오 작성
- APK와 시연 영상 생성

## 주의

Pleos SDK와 에뮬레이터는 현대자동차그룹의 별도 이용 조건을 따릅니다. 이 저장소의 MIT 라이선스는 이 프로젝트에서 작성한 코드에만 적용됩니다.

