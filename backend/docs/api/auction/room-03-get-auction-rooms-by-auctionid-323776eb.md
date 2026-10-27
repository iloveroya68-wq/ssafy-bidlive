# 경매방 상세 조회

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [경매방 상세 조회](https://app.notion.com/323776ebfd6883aa9f2101c95331bc48)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `ROOM-03` |
| 저장소 도메인 | `auction` |
| Method | `GET` |
| Path | `/api/v1/auction-rooms/{auctionId}` |
| 상태 | 확정 |
| 인증 | 선택 |
| 공개 범위 | Public |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `AuctionRoomDetailController` (`2026-07-30` 확인) |

## 개요

경매방 입장 전 상품·판매자·경매 조건과 현재 방 상태를 조회한다.

프론트가 사용자 기기 시각이 아닌 서버 시각을 기준으로 대기 및 마감 카운트다운을
계산할 수 있도록 응답 생성 시점의 `serverTime`을 함께 반환한다.

## Request

```http
GET /api/v1/auction-rooms/{auctionId}
Authorization: Bearer {accessToken}
```

`Authorization` 헤더는 선택이다.

### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `auctionId` | Long | 필수 | 조회할 경매방 ID |

## Response

### 200 OK

```json
{
  "serverTime": "2026-07-21T19:59:30+09:00",
  "auctionId": 100,
  "title": "키크론 Q1 Pro 경매",
  "status": "WAITING",
  "reservePrice": 50000,
  "scheduledAt": "2026-07-21T20:00:00+09:00",
  "durationMinutes": 20,
  "viewerCount": 18,
  "isSeller": true,
  "notificationCount": 12,
  "product": {
    "productId": 501,
    "title": "키크론 Q1 Pro",
    "description": "상품 설명",
    "categoryCode": "ELECTRONICS",
    "imageUrl": "https://cdn.example.com/products/501.png"
  },
  "seller": {
    "nickname": "판**",
    "rating": null
  },
  "canEnter": true
}
```

### 추가 응답 필드

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `serverTime` | DateTime | 응답 생성 시점의 서버 시각. UTC 오프셋을 포함한 ISO 8601 문자열 |
| `isSeller` | Boolean | 현재 인증 회원이 이 경매방의 판매자인지 여부. 비로그인 또는 다른 회원은 `false` |
| `notificationCount` | Integer / null | 중복 제거된 활성 알림 신청자 수. 판매자 또는 관리자에게만 반환하고 그 외에는 `null` |
| `product.categoryCode` | String | 상품 카테고리의 표준 코드. ROOM-02 목록의 `categoryCode`와 같은 값 |
| `seller.rating` | Double / null | 판매자 평점. 평점 산정 데이터가 제공되기 전까지 `null` |

## 검증 규칙

- 존재하고 공개 가능한 경매방만 조회한다.
- 삭제된 경매방은 `AUCTION_DELETED`, 비공개 또는 검열 차단 상품은
  `AUCTION_NOT_FOUND`로 처리한다.
- Access Token은 선택이며, 유효한 토큰이 있으면 회원 ID와 판매자 ID를 비교해
  `isSeller`를 계산한다.
- 비로그인 사용자 또는 다른 회원에게는 `isSeller=false`를 반환한다.
- `notificationCount`는 중복을 제거한 활성 알림 신청자 수이며 판매자 또는
  관리자에게만 반환한다.
- `viewerCount`는 경매 공개 STOMP 토픽을 구독 중인 세션 수를 반환한다.
- Redis에서 접속자 수를 읽을 수 없으면 상세 조회 자체를 실패시키지 않고
  `viewerCount=0`으로 반환한다.
- `product.categoryCode`는 ROOM-02 목록과 같은 `product_category.code` 값을 반환한다. 방 수정
  화면이 딥링크·새로고침으로 열려도 현재 카테고리를 채울 수 있어야 하므로 목록 경유 여부와
  무관하게 항상 포함한다. 카테고리 ID·이름 매핑은 `GET /api/v1/auctions/categories`를 사용하고
  상세 응답에 `category` 객체를 중복해서 반환하지 않는다.
- `WAITING`, `LIVE` 상태는 `canEnter=true`, `ENDED`는 `canEnter=false`다.
- 진행 중에도 개별 입찰 금액·순위·입찰자 식별 정보는 반환하지 않는다.
- `serverTime`은 상세 조회 처리가 완료된 뒤 응답을 생성하는 시점의 서버 시각이다.
- `serverTime`은 UTC 오프셋을 포함한 ISO 8601 문자열로 반환한다.
- 프론트는 경매방에 포함된 예정·종료 시각과 `serverTime`을 비교해 카운트다운을
  계산한다.
- 사용자 기기의 로컬 시각은 카운트다운의 기준으로 사용하지 않는다.

## 오류 응답

| HTTP | 코드 | 설명 |
| --- | --- | --- |
| 400 | `INVALID_AUCTION_ID` | 경매 ID 형식 오류 |
| 404 | `AUCTION_NOT_FOUND` | 경매방이 존재하지 않거나 공개할 수 없음 |
| 410 | `AUCTION_DELETED` | 삭제된 경매방 |

## 처리 흐름

```text
auctionId 검증
→ 선택적 Access Token 검증 및 현재 memberId 확인
→ AuctionRoom과 Product·카테고리 조회
→ 판매자 공개 정보 조회
→ isSeller와 notificationCount 계산
→ Redis 활성 접속 세션 수 조회
→ 현재 방 상태와 입장 가능 여부 계산
→ 응답 생성 시점의 serverTime 계산
→ serverTime을 포함한 공개 상세 정보 반환
```

## 공개 원칙

비공개 비크리 방식에 따라 경매 종료 전에는 다른 사용자의 입찰 금액, 최고가,
2등가, 순위와 식별 정보를 노출하지 않는다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [경매방 상세 조회 원본](https://app.notion.com/323776ebfd6883aa9f2101c95331bc48)
