# 공모전·Pleos 조사 자료

조사일: 2026-11-08
대상: 공모전 사이트 전 페이지(공지 3건, FAQ, 약관, 개인정보 처리방침, 접수양식 DOCX 포함), Pleos 개발 문서(기획·심사·개발·SDK·테스트·디자인 가이드), Vehicle SDK 2.3.2 AAR 바이너리

## 1. 공모전 기본 사항

| 항목 | 내용 |
|---|---|
| 명칭 | 현대자동차그룹 차량용 앱 개발 공모전 (Code your App into Pleos App Market) |
| 주최 / 운영 | 현대자동차그룹 / 주식회사 스터닝(운영사무국) |
| 참가 대상 | 대학(원)생, 일반인, 스타트업. 개인 또는 팀(대표자 포함 최대 5인) |
| 시상 부문 | 대학생 / 일반(스타트업 포함) |
| 접수 | 접수양식(기획서) 이메일 제출. appdevchallenge@hyundai-partners.com |
| 메일 제목 | `[차량용 앱 개발 공모전] 팀명(개인은 성명)_대표자명` |
| 수정 제출 제목 | `[수정제출] [차량용 앱 개발 공모전] 팀명(개인은 성명)_대표자명` |
| 문의 | appdevchallenge@hyundai-partners.com (전화 없음) |

### 일정 (KST)

| 날짜 | 내용 |
|---|---|
| 2026.10.21 | 공고·접수 시작, 숏폼 챌린지 시작 |
| 2026.10.28 14:00 | 온라인 설명회 (다시보기 URL 미확인) |
| 2026.11.15 23:59 | 접수 마감 |
| 2026.11.23 | 1차 서류심사 결과 발표 (합격 20팀, 공지 000006) |
| 2026.12.03–04 | 2차 최종 PT 심사(오프라인). 현대자동차 강남대로사옥 1층 UX스튜디오 서울 |
| 2026.12.18 | 최종 선정 결과 발표 (10팀) |
| 2026.12.19–2027.01 | 수상팀 전시 준비 기간 (추가 개발 기간 아님) |
| 2027.02 | 현대자동차그룹 행사 전시·시상식, 최종 순위 발표 |

### 시상

| 상 | 상금 | 팀 수 |
|---|---|---|
| 대상 | 1,000만원 | 대학생 1 / 일반 1 |
| 최우수상 | 600만원 | 대학생 1 / 일반 1 |
| 우수상 | 400만원 | 대학생 2 / 일반 2 |
| 아이디어 특별상 | 150만원 | 부문 통합 2 |
| 숏폼 챌린지 어워드 | 50만원 | 1 (본상과 별도) |

- 수상팀: Pleos App Market 탑재 준비 지원(입점·배포 보장 아님), 2027.02 행사 전시·시상.
- 상금은 대표 신청자에게 일괄 지급, 기타소득세 등 원천징수.

## 2. 접수양식(기획서) 구성

출처: `/downloads/contest-application-consent.docx` (동의서 버전 1020 ver.)

**01 참가신청서**: 참가 단위, 부문, 팀명, 앱명, 대표자 정보, 참가 인원, 구성원 표(성명·소속·휴대폰·이메일·담당 역할).

**02 앱 프로젝트 소개 / 심사 자료** — 성명·연락처·소속 기재 금지(블라인드). 분량 제한 없음. 별지 추가 가능.

| 번호 | 항목 | 작성 지시 |
|---|---|---|
| 1 | 앱 한 줄 소개 | 한 문장 |
| 2 | 기획 배경 | 어떤 고객의 니즈·불편한 순간에 주목했고 왜 해결이 필요한지 |
| 3 | 앱 주요 기능 및 차량 특화 요소 | 핵심 기능, 해결하는 문제, 차량에서의 새로운 경험 |
| 4 | 주요 사용 시나리오 | 누가, 어떤 상황에서, 어떻게 사용하는지 이용 흐름 |
| 5 | 개발 계획 | Pleos Connect 환경 구현 방식, 주요 개발 범위, SDK/API 사용계획 |
| 6 | 기대효과 및 발전 가능성 | 타깃 고객 분석, 예상 MAU, Retention Rate, 서비스 확대 계획 |
| 7 | 참고자료 (선택, 가산점) | 화면 시안, 시연 영상, GitHub 저장소 |
| 8 | 숏폼 링크 (선택, 가산점 없음) | 세로 영상 링크 |

- 공유 링크·첨부자료에도 개인정보·계정정보·개발자 실명 노출 금지. GitHub 저장소 이름·커밋 작성자 노출 여부 확인 필요.

**03 개인정보 수집·이용 동의서**, **04 대표자·팀원별 동의 및 서명**: 팀원 전원 개별 서명. CC 수신만으로 동의를 대체하지 않음.

## 3. 심사 기준

1차 서류심사 (세부 배점 비공개):

| 항목 | 기준 |
|---|---|
| 차량특화성 | 모바일 대비 차량 환경만의 독자적 가치. 주행·정차 맥락 및 차량 기능과의 유기적 결합 |
| 창의성 | 독창적 아이디어. 운전자·동승자 Pain Point와 해결 방식이 명확 |
| 기술 활용성 | Pleos Playground SDK·API를 핵심 기능과 사용 경험에 적절히 활용 |
| 사업성 | 타깃 고객, 사용자 확보·지속 이용·확대 가능성 (예상 MAU, Retention Rate 등) |
| [가산점] 개발 산출물 | 시연 영상(화면 녹화 포함), GitHub 저장소(소스+APK). 1차 접수 시 제출하면 단계별 가산점 |

2차 PT:
- 1차 합격팀은 실제 동작 가능한 앱 시연 필수 (에뮬레이터 구동 화면 또는 기기 시연).
- 2차 PT에서 제출·시연한 결과물 기준으로 수상작·순위 확정. 이후 추가 개발은 순위에 영향 없음.
- 2차 PT 세부 기준·발표 시간·형식: 추후 공지 (미확인).

## 4. 제출 규칙

- 필수: 접수양식(기획서). 선택: 시연 영상(목업·프로토타입·화면 녹화 인정), GitHub 저장소, 숏폼 링크.
- APK 단독 제출 금지. GitHub 저장소에 소스와 APK를 함께 업로드.
- APK 위치: 저장소 `apk/` 폴더. 파일명 `팀명_앱이름_release.apk`. 실행 가능한 release 버전.
- 용량 초과 시 GitHub Releases 등록 후 Release 주소 제출.
- 비공개 저장소 가능. 공지된 심사용 GitHub 계정을 Collaborator로 초대하고 마감 전 접근 확인. 심사용 계정명은 현재 공지에 없음.
- README 필수 항목: 앱 이름·한 줄 소개, 실행 방법, 테스트 환경 및 필요한 설정, APK 위치 또는 Release 링크.
- 여러 번 제출 시 마감 전 마지막 제출본이 최종본. 수정 시 전체 자료와 서명 동의서 재첨부, 팀원 전원 CC.

## 5. 출품 유의사항 (공지 000006, 약관)

- 순수 창작물이어야 함. 표절, 타 공모전 수상작은 심사 제외.
- 현대자동차그룹 임직원은 참가 가능하나 수상 제외.
- 지식재산권은 응모자 귀속. 수상작은 주최측 통상실시권 보유 가능, 결과 발표일부터 4개월 우선협상권 보유 가능.
- 오픈소스 사용 시 라이선스 준수·출처 표기 (약관 제7조).
- 제3자 권리 침해 시 수상 취소·상금 환수.
- AI 사용 규정: 명시 없음.

## 6. 개발 환경

| 항목 | 공식 기준 | 프로젝트 설정 |
|---|---|---|
| minSdk | API 28 이상 (차량 사이버 보안 요건) | 28 |
| Gradle | 8.0 이상 | 8.10.2 |
| 언어 | Java / Kotlin | Kotlin 2.0.0 |
| AGP / JDK | 명시 없음. 샘플 앱 기준 AGP 8.8.2, JDK 17 | AGP 8.8.2, JDK 17 |
| Vehicle SDK | v2.3.2 (2026.10.19 릴리스) | 2.3.2 |
| 에뮬레이터 | Pleos Connect Emulator v2.1.0, API 34, Cold boot, RAM 4GB | 동일 |

- 에뮬레이터 권장 GPU: RTX 4060 Laptop 8GB급 이상. Quick boot는 VHAL 미동작.
- Virtual Console: `adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/`.
- 주행 규제 테스트: `adb shell cmd hmg_car_service --service drivingstate driving_regulation on|off`.
- Pleos Playground(developers.pleos.ai) 가입 후 My Project 생성 필요. 프로젝트 ID는 앱 심사 요청 시 사용.
- SDK 사용 신청 상태(create-my-project 문서 기준): Vehicle State·NaviHelper 사용 가능. Vehicle Control SDK·FusedLocation·TextToSpeech·SpeechToText는 "준비 중"(신청 불가).

## 7. Vehicle SDK 2.3.2 확인 결과 (AAR 바이너리 직접 확인)

퍼미션 형식: `pleos.car.permission.{상수}`. `CAR_*` 조회, `CONTROL_CAR_*` 제어. 런타임 승인 창 없음, 매니페스트 선언 방식.

| 용도 | API | 퍼미션 | 비고 |
|---|---|---|---|
| 상향등 제어 | `Light.setHighBeamLightSwitch(LightSwitch, onComplete, onFailed)` | `CONTROL_CAR_LIGHTS` | Virtual Console High Beam 카드 있음 |
| 하향등·후미등·전/후방 안개등·실내등·독서등 제어 | `setLowBeamLightSwitch` 등 | `CONTROL_CAR_LIGHTS` | 안개등은 콘솔 카드 없음 |
| 앰비언트 조명 | `setAmbientLightSwitch`, `setAmbientLightBrightness`, `setAmbientLightBrightnessColor` | `CONTROL_CAR_LIGHTS` | 콘솔 밝기 0–100%, 색상 4종 |
| 조명 상태·지원 여부 조회 | `getCurrent*State`, `check*Capabilities`, `register*` | `CAR_LIGHTS` | |
| 방향지시등 | 조회·리스너만 | `CAR_LIGHTS` | 제어 불가 |
| 비상등·제동등·경적 | 없음 | — | 제어 불가 |
| 기어 | `Gear.getCurrentGearSelectionState`, `registerGearSelection` | `CAR_DRIVE` | 문서 도메인 목록에는 없으나 바이너리에 존재 |
| 브레이크 | `Brake.getCurrentBrakePedalStatus`, `registerBrakePedalStatus` | `CAR_DRIVE` | |
| 창문 | `Window.setWindowPosition` 등 | `CAR_WINDOWS` / 제어 퍼미션 | |
| 카메라·블랙박스·라디오 튜너 | 없음 | — | |

- Vehicle SDK 퍼미션은 Pleos App Market과 사전 협의·승인된 범위만 선언 가능. 미승인 퍼미션 선언 시 앱 심사 불통과.
- 차량 상태를 변경하거나 운전 안전에 영향을 주는 API는 별도 심사 기준 적용(세부 미공개).
- 모델별 지원 여부가 다르므로 `check*Capabilities`로 확인 후 사용. 에러 코드에 `ERROR_DRIVING_RESTRICTED`(주행 중 제한) 존재.
- 에뮬레이터에서 앱의 Light set 호출이 실제로 반영되는지는 문서에 없음. 직접 검증 필요.

Vehicle SDK 2.3.2 전체 영역 20개와 사용 여부 (공개 인터페이스 이름 기준):

| 영역 | 앱 사용 | 용도 또는 쓰지 않는 이유 |
|---|---|---|
| Light | 사용 | 불빛 신호, 등화관제 소등(외부등·실내등·독서등·앰비언트) |
| Gear | 사용 | P단 확인 |
| EvBattery, Fuel | 사용 | 잔량, 버틸 시간 추정 |
| HVAC | 사용 | 난방 아끼기, 화생방경보 내기순환·공조 끄기 |
| Seat | 사용 | 열선, 착좌 감지(인원 기본값) |
| SteeringWheel | 사용 | 핸들 열선 |
| Window | 사용 | 화생방경보 창문 닫기 |
| Odometer | 미사용 | `getRangeRemainingDistance`로 갈 수 있는 주유소·충전소 구분 가능(시설 데이터 단계 후보) |
| Safety | 미사용 | 에어백 전개·안전벨트 조회. 정차 상황과 접점 약함 |
| Door, SideMirror, Wiper, Tire, Toll, Display, CarInfo, DrivingMode, Brake, TurnSignal | 미사용 | 피난 정체 상황과 접점 없음. CarInfo(차대번호)는 개인정보 |

에뮬레이터 확인 결과(2026-11-14, Virtual Console 상태값으로 대조):
- 창문 위치 범위는 0..10, 0이 닫힘. `setWindowPosition(area, 0)`으로 닫힘.
- 전조등 스위치는 Off / Position / LowBeam 하나라, 미등과 하향등을 따로 쓰면 마지막에 쓴 값이 남는다. 복구는 꺼짐 → 미등 → 하향등 순서로 해야 한다.
- P단이 아니면 조명 쓰기가 거부된다(오류 50001). 등화관제 중 출발하면 앱이 조명을 복구할 수 없다.
- 실내등·독서등 끄기와 복구는 반영된다. 앰비언트 조명은 콘솔이 켜짐이어도 SDK 조회가 꺼짐으로 나와 확인하지 못했다.
- 콘솔 쓰기 API는 브라우저 연결(`POST /browser/session?client=…`, 1초 간격)이 있어야 동작한다.

앱에 넣은 시설 데이터 (공공데이터포털 파일, 키 불필요, `server/scripts/update-facilities.mjs`):

| 종류 | 출처 | 기준일 | 건수 | 이용 조건 |
|---|---|---|---|---|
| 약국 | 건강보험심사평가원 전국 병의원 및 약국 현황 (15051059) | 2026-09-30 | 25,829 | 공공누리 1유형(출처 표시) |
| 종합병원 | 같은 파일의 병원정보 중 종합병원·상급종합 | 2026-09-30 | 384 | 공공누리 1유형 |
| 편의점 | 소상공인시장진흥공단 상가(상권)정보 (15083033) 중 소분류 편의점 | 2026-09-30 | 55,970 | 제한 없음 |

- 시·도 이름이 파일마다 다르다(강원도/강원특별자치도, 전라북도/전북특별자치도, 광주광역시·전라남도/전남광주통합특별시). 지역으로 찾을 때 같은 곳으로 맞춘다(`provinceKey`).
- 영업 여부·영업시간은 들어 있지 않다. 종합병원은 응급실 운영 여부가 아니라 종별 기준이다.

기타 SDK:
- NaviHelper 3.0.5: `requestRoute`, `requestRouteWithWaypoints`, `addWaypoint`, `getCurrentLocationInfo`, `getDestinationInfo`, `getTBTInfo`, `requestNavigationScreen` 등. 신청 없이 사용 가능. 대피소 상세의 `길 안내`에 적용. `requestRoute`는 `pleos.car.permission.NAVI_ROUTE_SEARCH`가 필요하다(없으면 예외). 에뮬레이터(내비 서비스 3.0.4)에서 좌표·이름만 넘겨 경로 안내가 시작되는 것을 확인했다(2026-11-14). 통신이 끊겼을 때 경로 계산이 되는지는 확인하지 못했다.
- SpeechToText 3.3.1: 음성 인식. 신호를 듣는 마이크와 겹쳐 쓰지 않음.
- TextToSpeech / SpeechToText: `ON_DEVICE` 모드는 네트워크·앱 등록 불필요. 단, SDK 신청 상태는 "준비 중".
- FusedLocation: `pleos.car.permission.FUSED_LOCATION`, `isSupported()` 확인.

## 8. 앱 정책 중 우리 앱에 영향이 있는 항목

- **주행 규제**: DO(Distraction Optimization) 미선언 앱은 Parking에서만 사용 가능, Drive에서 사용 불가. DO 선언 방법은 공개 문서에 없음. 주행 상태는 `CarUxRestrictionsManager`로 확인.
- **하드웨어 접근**: 차량 물리 제어·센서 직접 접근 금지. SDK·플랫폼 API로만 접근.
- **오디오**: 재생 전 오디오 포커스 요청 필수, 포커스 변경 처리 필수. Suspend 모드 예외는 "긴급 통신, 안전 관련 경고음"으로 최소화. 음성 안내 전 선행 묵음 150–200ms 권장. 최대 볼륨·지속 비프음 규정은 없음.
- **개인정보**: 마이크 등 접근 권한 최소화, 등록 시 정직하게 신고, 명시적 동의 절차.
- **콘텐츠 정책**: "재난, 사고, 전쟁, 갈등, 전염병 등 특정 대상에게 민감한 내용이나 사건을 시사하거나, 이러한 문제를 통해 이익을 얻는 등 유해한 내용" 금지. 앱 문구·메타데이터 작성 시 유의.
- **오프라인**: 오프라인 시 핵심 기능 유지, 오프라인 상태 안내 UI는 권장 사항.
- **성능**: 입력 응답 0.25초 이내, 앱 실행 10초 이내, 2초 초과 작업은 진행 표시. 정차 중 CPU 25% 이하, RAM 400MB 이하.
- **UI**: Landscape·Portrait 모두 대응, 레터박스 최소화, 호환성 모드 금지, 대비 4.5:1 이상, 최소 12sp, 한 손 조작.
- **카테고리**: Maps/Navigation, Mobility Services, Lifestyle, Entertainment, Business, Music/Audio, Kids, Social/Communication 등. 비상·안전 카테고리 명시 없음.

## 9. 미확인 항목

- 심사용 GitHub 계정명 (공지 예정)
- 2차 PT 세부 기준·형식
- 설명회 다시보기 영상 URL
- DO 선언 절차
- Vehicle Control SDK 공개 시점
- 에뮬레이터에서 Light set API 반영 여부
- 오디오 포커스 상호작용 표 셀 값, EMERGENCY 컨텍스트 사용 자격

## 10. 출처

공모전: `/`, `/guide`, `/guide/overview`, `/guide/schedule`, `/guide/shortform`, `/guide/submission`, `/guide/development`, `/guide/judging-awards`, `/guide/rules`, `/guide/help`, `/faq`, `/notice` 및 공지 000001·000005·000006, `/qna`, `/terms`, `/privacy`, `/downloads/contest-application-consent.docx` (https://appdevchallenge.auto-hmg.io)

Pleos 문서 (https://document.pleos.ai/docs/pleos-only/ 하위):
- 시작·프로젝트: `getting-started`, `create-my-project`
- 기획 가이드: `vehicle-app-planning-guide/` (intro, driver-distraction-guideline, driving-restiction-policy, app-category, app-structure-screen-model, platform-api-architecture, pleos-connect-sdk-api, api-compatibility-availability, security-privacy, performance-resource-mgmt)
- 심사·제출: `distribute/app-review-guide/` (content-safety, functionality-performance-stability, driver-distraction-elements, app-registration-info, user-privacy-security), `distribute/app-submission-guide`
- 개발 가이드: `vehicle-app-developer-guide/` (audio-focus, suspend-mode, driving-restriction-development-guide, ccp-rotary-controller-development, vehicle-app-faq, adb-command-reference-guide 등)
- SDK: `connect-sdk-pleos/` (Vehicle intro, project-setup, check-capability, vehicle-permission, set-property, callback, vehicle-error-code, Light·Brake·TurnSignal·DrivingMode·Window, NaviHelper·TextToSpeech·SpeechToText·FusedLocation intro)
- 테스트·릴리스: `app-test-guide/` (virtual-console-guide, form-factor-test-guide), `release-note/` (sdk, emulator), `tutorial/vehicle-sdk-sample-app`
- 등록: `app-registration-guide/` (tagging, update, version-management, security)

에뮬레이터: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/ (setup-connect-sdk-emulator, configure-pleos-connect-emulator, use-vehicle-emulator)

GitHub: https://github.com/Pleos-Playground (pleos-sample-vehicle-app, pleos-sample-navi-app, pleos-sample-fusedlocation-app, pleos-guide)
