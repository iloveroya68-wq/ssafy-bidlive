# 대기방 입장 및 상태 조회

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [대기방 입장 및 상태 조회](https://app.notion.com/5f7e6b785ae383539152010222f18275)
>
> **2026-07-30 재동기화.** 직전 사본은 2026-07-27 기준이라 `serverTime`과 `destinations.viewers`가
> 빠져 있었다. 원본 Notion 페이지가 새로 만들어져 페이지 ID도 바뀌었다
> (구 `9e0776eb...` → 신 `5f7e6b78...`). 파일명은 그대로 둔다.

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `ROOM-07` |
| 저장소 도메인 | `auction` |
| Notion 도메인 | 경매 |
| Method | `POST` |
| Path / Destination | `/api/v1/auction-rooms/{auctionId}/join` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `AuctionRoomJoinController` (`2026-07-30` 확인) |
| 우선순위 | Medium |
| 연결 구분 | BE↔Redis, Front↔BE |
| 의존 대상 | Front, PostgreSQL, Redis, 경매 |
| 마지막 수정 | 2026-07-29 05:55:00Z |

## 개요

사용자의 경매방 입장 가능 여부를 확인하고 현재 상태에 따라 대기방 또는 진행 중 라이브 진입 정보를 반환한다.

프론트가 사용자 기기 시각이 아닌 서버 시각을 기준으로 대기 및 마감 카운트다운을 계산할 수 있도록 응답 생성 시점의 `serverTime`을 함께 반환한다.

## Request

```
POST /api/v1/auction-rooms/{auctionId}/join
Authorization: Bearer {accessToken}
```

### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `auctionId` | Long | 필수 | 입장할 경매방 ID |

### Request Body

요청 본문 없음.

## Response

### 200 OK — 대기방

```
{
  "serverTime": "2026-07-20T20:59:30+09:00",
  "auctionId": 100,
  "roomStatus": "WAITING",
  "scheduledAt": "2026-07-20T21:00:00+09:00",
  "permissions": {
    "chat": true,
    "bid": false,
    "prediction": false
  },
  "destinations": {
    "room": "/topic/auctions/100",
    "chat": "/topic/auctions/100/chat",
    "viewers": "/topic/auctions/100/viewers"
  }
}
```

### 200 OK — 진행 중 입장

```
{
  "serverTime": "2026-07-20T21:05:00+09:00",
  "auctionId": 100,
  "roomStatus": "LIVE",
  "permissions": {
    "chat": true,
    "bid": true,
    "prediction": true
  },
  "catchUpSummaryAvailable": true
}
```

### 응답 필드

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `serverTime` | DateTime | 입장 처리 후 응답을 생성한 서버 시각. UTC 오프셋을 포함한 ISO 8601 문자열 |
| `auctionId` | Long | 입장한 경매방 ID |
| `roomStatus` | Enum | 현재 경매방 상태. `WAITING` 또는 `LIVE` |
| `scheduledAt` | DateTime / null | 판매자가 안내한 시작 예정 시각. 대기방 응답에서 반환 |
| `permissions` | Object | 현재 상태에서 사용할 수 있는 기능별 권한 |
| `permissions.chat` | Boolean | 채팅 사용 가능 여부 |
| `permissions.bid` | Boolean | 입찰 가능 여부 |
| `permissions.prediction` | Boolean | 예측 베팅 가능 여부 |
| `destinations` | Object / null | 대기방에서 사용할 WebSocket 구독 목적지 |
| `catchUpSummaryAvailable` | Boolean / null | 라이브 중간 입장 시 AI 요약 조회 가능 여부 |

## 검증 규칙

- 회원의 정지·차단 상태와 경매방 접근 제한을 확인한다.
- 방송 시작 전에는 `WAITING`, 판매자가 시작한 뒤에는 `LIVE` 상태로 반환한다.
- `scheduledAt`은 안내용이며 해당 시각 도달만으로 상태가 변경되지 않는다.
- 대기방에서는 입찰과 예측 베팅을 비활성화한다.
- 종료된 경매방에는 새로 입장할 수 없다.
- 동일 사용자의 중복 입장 요청은 접속자 수를 중복 증가시키지 않는다.
- `serverTime`은 입장 권한과 방 상태 판정이 끝난 뒤 응답을 생성하는 시점의 서버 시각이다.
- `serverTime`은 UTC 오프셋을 포함한 ISO 8601 문자열로 반환한다.
- `WAITING` 상태에서는 `scheduledAt - serverTime`을 기준으로 시작 예정 카운트다운을 계산한다.
- 프론트는 경매방에서 제공되는 예정·종료 시각과 `serverTime`을 비교해 카운트다운을 계산한다.
- 사용자 기기의 로컬 시각은 카운트다운의 기준으로 사용하지 않는다.
- 각 요청의 `serverTime`은 독립적으로 생성하므로 이전 목록 또는 상세 조회 응답의 값과 동일할 필요는 없다.

## 오류 응답

| HTTP | 코드 | 설명 |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 403 | `ROOM_ACCESS_DENIED` | 회원 또는 방 정책상 입장할 수 없음 |
| 404 | `AUCTION_ROOM_NOT_FOUND` | 경매방이 존재하지 않음 |
| 409 | `AUCTION_ALREADY_ENDED` | 이미 종료된 경매방 |

## 처리 흐름

```
인증 사용자 확인
→ 경매방과 접근 권한 조회
→ 현재 방 상태 판정
→ Redis 접속 상태를 멱등하게 등록
→ 상태별 권한과 구독 Destination 구성
→ 응답 생성 시점의 serverTime 계산
→ serverTime을 포함한 입장 정보 반환
```

## 후속 처리

- 판매자가 ROOM-05 방송 시작 API를 호출하면 `AUCTION_STARTED` 이벤트를 받아 전원 라이브 뷰로 전환한다.
- 진행 중 중간 입장자는 AI-03 요약을 별도로 조회할 수 있다.
- 대기방 채팅도 채팅 검열 정책을 적용한다.

## 구현 메모 (2026-07-30, S15P11A704-471)

- **"Redis 접속 상태를 멱등하게 등록"은 이 엔드포인트가 하지 않는다.** 접속자 집계는 STOMP 세션
  단위이고(`ViewerSessionStompEventListener.onSubscribe` → `ViewerSessionTrackingService`, 249),
  join은 REST라 `stompSessionId`가 없어 등록할 수단이 없다. 클라이언트가 join 응답의
  `destinations`로 구독하는 순간 그 경로가 멱등하게 등록한다. 그래서 "중복 입장 요청이 접속자 수를
  중복 증가시키지 않는다"도 자연히 지켜진다 — REST 재호출은 집계를 건드리지 않는다.
- **비공개 방을 남이 입장하려 하면 404다**(403 아님). 목록·상세·수정·삭제가 모두 비공개 방을
  "없는 것"으로 다루는데 입장만 403을 주면 그 방의 존재가 드러난다(482).
  `ROOM_ACCESS_DENIED`(403)는 **회원 상태**(정지·탈퇴)로 막는 경우에 쓴다 — 방의 존재와 무관한
  거절이라 두 코드의 뜻이 갈린다.
- `catchUpSummaryAvailable`은 **항상 `false`다.** AI-03(중간 입장 요약 조회)이 아직 구현되지
  않았다(전수 grep). 그 API가 생기면 조건을 채워야 한다.
- `destinations.viewers`(`/topic/auctions/{auctionId}/viewers`)는 실재한다 —
  `StompViewerCountPublisher`가 그 destination으로 접속자 수를 발행한다.
