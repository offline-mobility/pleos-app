# Pleos Connect 개발 환경 설정

## 필수 도구

- Android Studio 최신 버전
- JDK 17
- Android SDK Platform 35
- Pleos Connect Emulator 2.1.0

제품 앱의 `minSdk`는 차량 사이버 보안 요구사항에 따라 API 28 이상으로 유지합니다.

## Pleos 에뮬레이터 설치

Android Studio의 `Tools > SDK Manager > SDK Update Sites`에서 다음 저장소를 추가합니다.

- 이름: `Pleos Connect System Image`
- macOS: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
- Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`

API 34의 Pleos Connect 이미지를 설치한 다음 Automotive AVD를 생성합니다.

- Device: Pleos Connect v2.1.0
- System image: API 34
- RAM: 4GB
- Boot option: Cold boot

Quick boot에서는 VHAL이 동작하지 않으므로 반드시 Cold boot를 사용합니다.

## 빌드

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew testDebugUnitTest assembleDebug
```

APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다.

## Virtual Console

에뮬레이터를 실행한 후 다음 명령으로 차량 상태 테스트 콘솔을 연결합니다.

```bash
adb forward tcp:8088 tcp:8088
```

브라우저에서 `http://127.0.0.1:8088/`을 엽니다.

## 개발 원칙

1. Vehicle API 호출 전에 Capability API로 차량별 지원 여부를 확인합니다.
2. 실제 사용하는 API의 최소 권한만 `AndroidManifest.xml`에 선언합니다.
3. SDK 콜백 실패와 미지원 상태를 사용자에게 구분해 표시합니다.
4. Listener는 등록한 동일 인스턴스와 Area로 해제합니다.
5. SDK 사용이 끝나면 `Vehicle.release()`를 호출합니다.
6. 주행 중에는 운전자 주의 분산을 줄이는 UI와 기능 제한을 적용합니다.

## 공식 자료

- 개발 시작: https://document.pleos.ai/docs/pleos-only/getting-started
- 에뮬레이터: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- SDK 릴리스: https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note
- Vehicle 샘플: https://github.com/Pleos-Playground/pleos-sample-vehicle-app

