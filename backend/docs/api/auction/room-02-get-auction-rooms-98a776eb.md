# 경매방 목록 조회

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [경매방 목록 조회](https://app.notion.com/184e6b785ae3836498ad81ca94dd2923)
>
> **2026-07-30 재동기화.** 직전 사본은 2026-07-27 기준이라 `serverTime`·`counts`·`categoryCode`·
> `soldPrice`·`endedAt`이 모두 빠져 있었다. Notion 페이지가 새로 만들어져 원본 링크의 페이지 ID도
> 바뀌었다(구 `98a776eb...` → 신 `184e6b78...`). 파일명은 그대로 둔다.

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `ROOM-02` |
| 저장소 도메인 | `auction` |
| Notion 도메인 | 경매 |
| Method | `GET` |
| Path / Destination | `/api/v1/auction-rooms` |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | Public |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `AuctionRoomListController` (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, PostgreSQL, 경매 |
| 마지막 수정 | 2026-07-29 05:54:00Z |

## 요약

진행 중·대기·종료 탭과 카테고리·검색 조건으로 경매방을 무한 스크롤 조회하며 상태별 정렬 정책을 적용한다.

## 비고

기본 8건, LIVE는 마감 임박순, WAITING은 예정 시각순, ENDED는 종료 최신순

## 개요

진행 중·예정·종료 상태와 카테고리·검색어 조건으로 경매방 목록을 조회한다. 각 방 카드에 카테고리 코드, 공개 하한가, 누적 입찰 건수, 입찰 참여 인원, 예측 베팅 참여 인원과 종료 결과를 포함하며 커서 기반 페이징을 사용한다.

프론트의 진행 중·대기 탭에 표시할 방 개수를 위해 `LIVE`, `WAITING` 상태별 전체 개수를 함께 반환한다. 상태별 개수에는 요청의 `categoryId`와 `keyword` 조건을 적용하지만 `status`, `cursor`, `size`는 적용하지 않는다.

프론트가 사용자 기기 시각이 아닌 서버 시각을 기준으로 대기 및 마감 카운트다운을 계산할 수 있도록 응답 생성 시점의 `serverTime`을 함께 반환한다.

기존에 별도 명세로 관리하던 카테고리별 경매방 목록 조회
`GET /api/v1/auctions`는 이 API의 `categoryId` 필터로 통합한다. 별도 엔드포인트는
제공하지 않는다.

## Request

```javascript
GET /api/v1/auction-rooms?status=LIVE&categoryId=12&keyword=키보드&cursor={cursor}&size=8
```

카테고리를 선택하지 않으면 `categoryId`를 생략한다.

```javascript
GET /api/v1/auction-rooms?status=LIVE&size=20
```

### Query Parameter

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `status` | Enum | 선택 | `WAITING`, `LIVE`, `ENDED` 경매 상태. 목록 조회에만 적용하며 `counts` 집계에는 적용하지 않음 |
| `categoryId` | Long | 선택 | 상품 카테고리 ID. 목록과 `counts` 집계에 모두 적용 |
| `keyword` | String | 선택 | 경매방 제목 또는 상품명 검색어. 목록과 `counts` 집계에 모두 적용 |
| `cursor` | String | 선택 | 이전 응답의 `nextCursor`. 최초 조회 시 생략 |
| `size` | Integer | 선택 | 무한 스크롤 1회 조회 개수. 기본 8, 최대 20 |

## Response

### 200 OK

```json
{
  "serverTime": "2026-07-21T19:59:30+09:00",
  "items": [
    {
      "auctionId": 100,
      "title": "키크론 Q1 Pro 경매",
      "thumbnailUrl": "https://cdn.example.com/products/501.png",
      "categoryCode": "ELECTRONICS",
      "sellerNickname": "서연***",
      "reservePrice": 50000,
      "bidCount": 7,
      "bidParticipantCount": 5,
      "predictionParticipantCount": 15,
      "scheduledAt": "2026-07-21T20:00:00+09:00",
      "endAt": "2026-07-21T20:15:00+09:00",
      "soldPrice": null,
      "endedAt": null,
      "status": "LIVE",
      "viewerCount": 18
    }
  ],
  "nextCursor": "opaque-cursor",
  "hasNext": true,
  "counts": {
    "LIVE": 3,
    "WAITING": 2
  }
}
```

### 추가 응답 필드

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `serverTime` | DateTime | 응답 생성 시점의 서버 시각. UTC 오프셋을 포함한 ISO 8601 문자열 |
| `categoryCode` | String | 프론트 카테고리 배지에 사용하는 표준 카테고리 코드 |
| `bidCount` | Long | 누적 입찰 **건수**. 같은 회원의 다중·상향 입찰을 모두 센다 |
| `bidParticipantCount` | Long | 입찰한 고유 **회원 수**. 다중·상향 입찰은 한 명으로 집계한다. WS-03의 동일 필드와 같은 정의다 |
| `predictionParticipantCount` | Long | 예측 베팅 참여 인원 |
| `soldPrice` | Long / null | 낙찰된 종료 경매의 최종 낙찰가. 진행 중·대기·유찰 경매는 `null` |
| `endedAt` | DateTime / null | 경매가 실제 종료된 시각. 종료 전에는 `null` |
| `counts` | Object | 현재 카테고리·검색어 조건에 해당하는 상태별 방 개수 |
| `counts.LIVE` | Long | 조건에 해당하는 진행 중 경매방 개수 |
| `counts.WAITING` | Long | 조건에 해당하는 대기 중 경매방 개수 |

## 목록 조회 규칙

- `items`에는 요청의 `status`, `categoryId`, `keyword`, `cursor`, `size` 조건을 모두 적용한다.
- `status`가 지정되면 해당 상태의 경매방만 `items`에 반환한다.
- `cursor`와 `size`는 `items`의 커서 페이징에만 사용한다.
- 지원하는 상태값과 존재하는 카테고리만 조회할 수 있다.
- `size`는 서버가 허용한 범위로 제한한다.
- 비로그인 사용자도 공개 경매방 목록을 조회할 수 있다.
- 진행 중에도 입찰 금액, 최고가, 2등가와 입찰자 정보는 반환하지 않는다.
- `categoryCode`는 서버의 표준 카테고리 enum 문자열을 반환한다.
- 카드의 「참여 N명」은 `bidParticipantCount`로 그린다. `bidCount`는 건수이고 `predictionParticipantCount`는 예측 참여 인원이므로 이 자리에 대신 쓰지 않는다.
- `bidParticipantCount`는 진행 중에도 공개한다. 인원 집계이며 개별 입찰 금액·순위·신원을 드러내지 않는다.
- 카테고리 ID·코드·이름은 `GET /api/v1/auctions/categories` 응답으로 매핑하며,
  목록 항목에 별도 `category` 객체를 중복해서 반환하지 않는다.
- `soldPrice`는 `status=ENDED`이면서 낙찰된 경매에만 값을 반환하고, 유찰 또는 종료 전에는 `null`이다.
- `endedAt`은 실제 종료 시각이며, 예정 종료 시각인 `endAt`과 구분한다.
- `LIVE`는 `endAt` 오름차순, `WAITING`은 `scheduledAt` 오름차순, `ENDED`는 `endedAt` 내림차순으로 정렬한다.
- `serverTime`은 목록과 집계 처리가 완료된 뒤 응답을 생성하는 시점의 서버 시각이다.
- `serverTime`은 UTC 오프셋을 포함한 ISO 8601 문자열로 반환한다.
- `LIVE`의 마감 카운트다운은 `endAt - serverTime`을 기준으로 계산한다.
- `WAITING`의 시작 예정 카운트다운은 `scheduledAt - serverTime`을 기준으로 계산한다.
- 남은 시간 3분 이내 강조 표시는 `endAt`과 `serverTime`을 기준으로 프론트가 처리한다.
- 사용자 기기의 로컬 시각은 카운트다운의 기준으로 사용하지 않는다.

## 상태별 개수 집계 규칙

- `counts`에는 `LIVE`, `WAITING`을 항상 모두 포함한다.
- 조건에 해당하는 방이 없으면 해당 상태의 개수를 `0`으로 반환한다.
- `ENDED` 개수는 `counts`에 포함하지 않는다.
- 요청의 `status`는 `counts` 집계에서 무시한다.
- `status=LIVE`로 조회하더라도 `LIVE`, `WAITING` 개수를 모두 반환한다.
- `status=WAITING` 또는 `status=ENDED`로 조회하더라도 `LIVE`, `WAITING` 개수를 모두 반환한다.
- 요청의 `categoryId`와 `keyword`는 `counts` 집계에도 적용한다.
- `cursor`와 `size`는 `counts` 집계에 영향을 주지 않는다.
- `counts`는 현재 페이지의 `items` 개수가 아니라 필터 조건에 해당하는 전체 방 개수다.

예를 들어 `categoryId=12`, `keyword=키보드`, `status=LIVE`로 요청하면 다음과 같이 처리한다.

- `items`: 카테고리 12이면서 검색어가 일치하는 `LIVE` 경매방 목록
- `counts.LIVE`: 카테고리 12이면서 검색어가 일치하는 전체 `LIVE` 경매방 개수
- `counts.WAITING`: 카테고리 12이면서 검색어가 일치하는 전체 `WAITING` 경매방 개수

## 오류 응답

| HTTP | 코드 | 설명 |
| --- | --- | --- |
| 400 | `INVALID_AUCTION_STATUS` | 지원하지 않는 상태 필터 |
| 400 | `INVALID_CURSOR` | 커서 형식 오류 |
| 404 | `CATEGORY_NOT_FOUND` | 카테고리가 존재하지 않음 |

## 처리 흐름

```plain text
조회 조건 검증
→ cursor 디코딩
→ status·categoryId·keyword 조건으로 경매방 size+1건 조회
→ categoryId·keyword 조건만 적용하여 LIVE·WAITING 전체 개수 집계
→ Redis 또는 집계 저장소에서 입찰 건수·입찰 참여 인원·예측 참여 인원·시청자 수 결합
→ nextCursor와 hasNext 계산
→ 응답 생성 시점의 serverTime 계산
→ serverTime, items, counts를 포함한 공개 목록 정보 반환
```

## 공개 원칙

경매 진행 중에는 공개 하한가, 누적 입찰 건수, 입찰 참여 인원, 예측 베팅 참여 인원, 시청자 수와 같은 집계 정보만 공개할 수 있다. 개별 입찰 금액, 최고가, 2등가, 예측 금액과 참여자 식별 정보는 종료 전까지 공개하지 않는다.

## 구현 메모 (2026-07-30, S15P11A704-457)

- 커서는 `(status, 정렬 값, auctionId)`를 base64url로 감싼 불투명 문자열이다. 정렬 기준 시각이 없는 방은
  `infinity`·`-infinity` 센티넬을 담아 항상 뒤로 밀린다. **정렬 기준이 상태별로 다르므로 커서는 발급
  당시와 같은 `status` 조건에서만 유효하고**, 어긋나면 `INVALID_CURSOR`다.
  `status`를 커서에 담는 이유가 이것이다 — 담지 않으면 `LIVE`에서 받은 커서를 `ENDED`로 보냈을 때
  `ends_at` 값이 `ended_at` 내림차순 비교에 쓰여 **방이 조용히 누락된다**(MR !181 리뷰에서 발견).
- **검열 차단 상품은 목록과 집계에서 제외한다** (`product.moderation_status <> 'BLOCKED'`).
  `PASSED`만 통과시키지 않는 이유는 등록 직후가 `PENDING`이라 그러면 검수 전 방이 통째로 사라지기
  때문이고, `FLAGGED`는 AI 미확정이라 오탐인 정상 방을 지우지 않는다. 썸네일 선택에서도 차단된
  이미지(`product_image.moderation_status`)는 건너뛴다. `frontend/docs/04` §6.5가 미해결로 남겨 둔
  질문의 답이다.
- `title`은 `auction_room`에 제목 컬럼이 없어 `product.title`을 반환한다. 명세 예시는 방 제목
  (`키크론 Q1 Pro 경매`)과 상품명(`키크론 Q1 Pro`)을 다르게 적었지만 스키마에는 하나뿐이다.
- `keyword`는 상품명(`product.title`) ILIKE 검색이며 `%`·`_`는 리터럴로 이스케이프한다.
- **`viewerCount`는 아직 항상 `0`이다.** 접속자 수는 STOMP 세션 기반 실시간 값이라 DB에 없고,
  ROOM-07 입장이 Redis에 접속을 등록하기 전까지는 결합할 소스가 없다.
- `auction_room.status`에는 DB CHECK 제약이 없어 조회 시 `WAITING`·`LIVE`·`ENDED`만 받는다.
- `bidParticipantCount`는 `COUNT(DISTINCT auction_participant.member_id)`다. `auction_participant`가 `(auction_room_id, member_id)` 유일 제약을 가지므로 이 값이 곧 참여 인원이며,
  같은 회원이 여러 번 입찰해도 1로 집계된다. `bidCount`(행 수)와 축이 달라 하나로 합치지 않는다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [경매방 목록 조회 원본](https://app.notion.com/184e6b785ae3836498ad81ca94dd2923)
