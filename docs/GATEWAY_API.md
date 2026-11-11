# 구조 서버 API 명세

통신이 끊긴 구역에서 차량끼리 소리로 주고받은 구조 요청을, 통신이 살아난 차량이 구조 서버로 올린다. 서버는 받은 요청을 모아 구조기관 화면이나 재난방송 연계 시스템에 넘긴다.

## 앱 동작

- 차량은 받은 구조 요청과 자기가 보낸 요청을 기기 안에 암호화해 보관한다(최대 7일, 500건).
- 인터넷 연결이 확인되면(`NET_CAPABILITY_VALIDATED`) 50건씩 POST한다.
- 성공(2xx 또는 409)하면 해당 건을 보관함에서 지운다. 실패하면 5초부터 최대 5분까지 간격을 두 배씩 늘려 다시 보낸다.
- 서버 주소는 빌드 설정 `GATEWAY_URL`로 넣는다(`./gradlew assembleRelease -PgatewayUrl=https://...`). 비어 있으면 전송 기능은 꺼진다.
- 앱에는 비밀 키가 없다. 서버는 모든 요청을 신뢰할 수 없는 입력으로 다룬다.

## 요청

```
POST {GATEWAY_URL}
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
