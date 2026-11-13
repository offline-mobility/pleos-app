# 구조 서버 API 명세

구현: `server/` (Node 20 이상, 외부 패키지 없음). 로컬 실행 `cd server && cp .env.example .env && npm run dev`, 테스트 `npm test`.

통신이 끊긴 구역에서 차량끼리 소리로 주고받은 구조 요청을, 통신이 살아난 차량이 구조 서버로 올린다. 서버는 받은 요청을 모아 구조기관 화면이나 재난방송 연계 시스템에 넘긴다.

## 앱 동작

- 차량은 받은 구조 요청과 자기가 보낸 요청을 기기 안에 암호화해 보관한다(최대 7일, 500건).
- 인터넷 연결이 확인되면(`NET_CAPABILITY_VALIDATED`) 50건씩 POST한다.
- 성공(2xx 또는 409)하면 해당 건을 보관함에서 지운다. 실패하면 5초부터 최대 5분까지 간격을 두 배씩 늘려 다시 보낸다.
- 서버 주소는 빌드 설정 `SERVER_URL`로 넣는다(`./gradlew assembleRelease -PserverUrl=https://...`). 비어 있으면 온라인 기능은 모두 꺼진다. 디버그 빌드만 로컬 서버(`http://10.0.2.2:포트`)를 허용한다.
- 앱에는 비밀 키가 없다. 서버는 모든 요청을 신뢰할 수 없는 입력으로 다룬다.

## 요청

```
POST {SERVER_URL}/api/reports
Content-Type: application/json; charset=utf-8
```

```json
{
  "version": 1,
  "reporter": "123e4567-e89b-12d3-a456-426614174000",
  "reports": [
    {
      "id": "a1-3",
      "message": "MEDICAL",
      "people": 2,
      "hop": 1,
      "own": false,
      "heardAt": 1794000000000,
      "lat": 37.49787,
      "lon": 127.02759
    }
  ]
}
```

| 필드 | 형식 | 설명 |
|---|---|---|
| `version` | 정수 | 현재 1 |
| `reporter` | UUID 문자열 | 올린 차량의 앱 설치마다 무작위로 만든 값. 차량·사람과 연결되지 않는다 |
| `reports[].id` | 문자열 `"{senderId 2자리 16진수}-{sequence 16진수}"` | 같은 원래 메시지를 가리킨다. 여러 차량이 같은 메시지를 올리면 `id`가 같다 |
| `reports[].message` | 문자열 | 아래 메시지 코드 |
| `reports[].people` | 정수 0–15 | 0 모름, 15는 15명 이상 |
| `reports[].hop` | 정수 0–3 | 0이면 올린 차량이 원래 보낸 차량에게 직접 받음 |
| `reports[].own` | 불리언 | 올린 차량이 직접 보낸 메시지 |
| `reports[].heardAt` | 정수 (Unix ms) | 올린 차량이 처음 받은 시각 |
| `reports[].lat`, `lon` | 실수, 소수 5자리 | 보낸 차량 위치. 약 40m 격자. 위치가 없으면 필드가 없다 |

메시지 코드: `SOS`, `MEDICAL`, `TRAPPED`, `FIRE`, `HAZARD`, `FLOOD`, `VULNERABLE`, `WATER`, `POWER`, `ROAD_BLOCKED`, `ROUTE_CLEAR`, `SHELTER`, `RESCUE_TEAM`, `ALL_SAFE`, `COLD`, `COLLAPSE`, `CBRN`, `FOOD`, `MEDICINE`

## 응답

| 코드 | 의미 | 앱 동작 |
|---|---|---|
| 200, 201, 202, 204 | 받음 | 보관함에서 삭제 |
| 409 | 이미 받은 요청 | 보관함에서 삭제 |
| 그 밖 | 실패 | 나중에 다시 보냄 |

응답 본문은 읽지 않는다. 앱은 리다이렉트를 따라가지 않는다.

## 서버 요구사항

- HTTPS만 받는다(TLS 1.2 이상). 앱은 평문 HTTP를 보내지 않는다.
- 같은 `id`는 하나의 사건으로 합친다. `hop`이 가장 작은 보고의 위치와 인원을 대표값으로 쓴다.
- 입력 검증: 필드 범위, 메시지 코드 목록, 위도 -90–90·경도 -180–180, `heardAt`이 현재 시각보다 미래가 아닐 것. 어긋나는 건은 버린다.
- 요청 크기는 64KB, `reports`는 50건 이하로 제한한다.
- `reporter`별, IP별 요청 횟수를 제한한다. 소리 신호는 누구나 보낼 수 있으므로, 같은 내용을 여러 `reporter`가 올렸는지를 신뢰도 판단에 쓴다.
- 위치는 민감정보다. 구조 목적 외 접근을 막고, 보관 기간을 정해 지운다.

## 그 밖의 API

앱은 위치를 소수 둘째 자리(약 1km)로 줄여 보낸다.

| 요청 | 설명 | 응답 |
|---|---|---|
| `GET /api/alerts?lat&lon` | 그 지역의 최근 24시간 긴급재난문자. 지역은 가장 가까운 대피소 주소로 판단 | `{source, region, alerts:[{id, createdAt, text, level, type}]}`. `type`은 앱의 재난 모드 이름 또는 null |
| `GET /api/shelters?lat&lon&km` | 주변 대피소(기본 5km, 최대 30km), 가까운 순 | `{source, shelters:[{id, name, kind, lat, lon, address, capacity, distanceM}]}` |
| `GET /api/reports/nearby?lat&lon&km` | 주변 구조 요청(기본 5km, 최대 10km) | `{incidents:[…]}` |
| `GET /api/incidents` | 전체 구조 요청. `Authorization: Bearer {DASHBOARD_TOKEN}` 필요. 토큰이 없으면 꺼짐(503) | `{incidents:[{uid, id, message, people, hop, lat, lon, firstHeardAt, lastHeardAt, fromSender, confirmations}]}` |
| `GET /api/health` | 설정 상태 | `{ok, alerts, shelters, dashboard}` |
| `GET /` | 구조 상황판(지도) | HTML |
| `GET /r?…` | QR 전달 페이지. 아래 "QR 전달" 참고 | HTML |

`source`: `live`(공공 API), `sample`(`SAMPLE_DATA=1`인 개발 서버의 견본), `unconfigured`(키 없음, 빈 목록).

## QR 전달

앱이 없는 사람의 휴대폰이 구조 요청을 대신 올린다. 차량은 보낸 요청을 아래 주소의 QR 코드로 띄운다.

```
{SERVER_URL}/r?m=MEDICAL&p=2&i=a1-3&h=0&t=1794000000&la=37.49787&lo=127.02759
```

| 값 | 설명 |
|---|---|
| `m` | 메시지 코드 |
| `p` | 인원 0–15 |
| `i` | `reports[].id`와 같은 형식 |
| `h` | 홉 0–3 |
| `t` | 차량이 보낸 시각 (Unix 초) |
| `la`, `lo` | 위치. 위치를 보내지 않으면 둘 다 없다 |

- 페이지(`public/r.html`, `r.js`, `relay.js`)는 값을 검증해 요약을 보여 주고, 사람이 `구조 서버로 전달하기`를 누르면 `POST /api/reports`로 보낸다. `own`은 false, `reporter`는 휴대폰 브라우저에 저장한 무작위 UUID다.
- 값이 하나라도 형식에 맞지 않으면 전송 버튼을 보여 주지 않는다.
- 휴대폰이 통신이 없는 곳에서 찍었으면 페이지가 열리지 않는다. 통신이 되는 곳에서 주소를 다시 열어야 한다.

## 공공 데이터

| 데이터 | API | 환경변수 |
|---|---|---|
| 행정안전부 긴급재난문자 | safetydata.go.kr `DSSP-IF-00247` | `SAFETYDATA_ALERT_KEY` |
| 통합대피소 (한파쉼터·무더위쉼터·지진옥외대피장소·지진해일긴급대피장소) | `DSSP-IF-10941` | `SAFETYDATA_SHELTER_KEY` |

- 재난문자는 2분, 대피소는 약 1km 칸마다 24시간 캐시한다.
- 서비스 키는 서버에만 둔다. 오류 응답에 키나 외부 주소를 싣지 않는다.
- 통합대피소에는 민방위 대피소와 수용 인원이 없다. 민방위 대피소(`DSSP-IF-10166`)는 키 발급 후 추가한다.

## 배포 (Vercel)

1. `cd server && vercel login && vercel link` (프로젝트 이름에 개인 정보가 들어가지 않게 한다)
2. 환경변수 등록: `SAFETYDATA_ALERT_KEY`, `SAFETYDATA_SHELTER_KEY`, `DASHBOARD_TOKEN`(`openssl rand -hex 32`), Upstash Redis의 `UPSTASH_REDIS_REST_URL`·`UPSTASH_REDIS_REST_TOKEN`. `SAMPLE_DATA`는 넣지 않는다.
3. `vercel deploy --prod` 후 `https://…/api/health` 확인
4. 앱 빌드: `./gradlew assembleRelease -PserverUrl=https://배포주소`
