# 경매방 생성

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [경매방 생성](https://app.notion.com/177776ebfd68839c9cd381a5ae5383ca)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `ROOM-01` |
| 저장소 도메인 | `auction` |
| Notion 도메인 | 경매 |
| Method | `POST` |
| Path / Destination | `/api/v1/auction-rooms` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | Seller |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `AuctionRoomCreationController` (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, PostgreSQL, 경매 |
| 마지막 수정 | 2026-07-27 02:08:14Z |

## 요약

판매자가 상품 정보와 경매 조건을 함께 입력해 상품과 대기 상태의 경매방을 한 트랜잭션으로 생성한다.

## 비고

상품·경매방 동시 생성, 예약 시각은 안내용, 실제 시작은 판매자 수동

## 개요
판매자가 상품 정보와 경매 조건을 한 번에 입력하면 서버가 `Product`와 `AuctionRoom`을 하나의 트랜잭션으로 생성한다. 예약 시각은 안내용이며, 실제 방송 시작은 판매자가 직접 수행한다.
## Request
```javascript
POST /api/v1/auction-rooms
Authorization: Bearer {accessToken}
Content-Type: application/json
```
```json
{
  "product": {
    "title": "키크론 Q1 Pro",
    "description": "사용 기간 6개월, 구성품 포함",
    "category": "ELECTRONICS",
    "imageUrl": "https://cdn.example.com/products/abc.jpg"
  },
  "auction": {
    "reservePrice": 50000,
    "durationMinutes": 15,
    "startType": "SCHEDULED",
    "scheduledAt": "2026-07-21T20:00:00+09:00"
  }
}
```
### Request Body
<table>
<tr>
<td>필드</td>
<td>타입</td>
<td>필수</td>
<td>설명</td>
</tr>
<tr>
<td>`product.title`</td>
<td>String</td>
<td>필수</td>
<td>경매에 등록할 상품명</td>
</tr>
<tr>
<td>`product.description`</td>
<td>String</td>
<td>필수</td>
<td>상품 상태·사용 기간·구성품 설명</td>
</tr>
<tr>
<td>`product.category`</td>
<td>String</td>
<td>필수</td>
<td>상품 카테고리 코드</td>
</tr>
<tr>
<td>`product.imageUrl`</td>
<td>String</td>
<td>필수</td>
<td>`POST /api/v1/product-images`가 반환한 단일 상품 이미지 URL</td>
</tr>
<tr>
<td>`auction.reservePrice`</td>
<td>Long</td>
<td>필수</td>
<td>공개되는 필수 하한가</td>
</tr>
<tr>
<td>`auction.durationMinutes`</td>
<td>Integer</td>
<td>필수</td>
<td>방송 시작 후 경매 기본 진행 시간</td>
</tr>
<tr>
<td>`auction.startType`</td>
<td>Enum</td>
<td>필수</td>
<td>`IMMEDIATE` 또는 `SCHEDULED`</td>
</tr>
<tr>
<td>`auction.scheduledAt`</td>
<td>DateTime</td>
<td>조건부 필수</td>
<td>`SCHEDULED`일 때 필수. 안내용 시각이며 자동 시작하지 않음</td>
</tr>
</table>

## 상품 이미지 준비

경매방 생성 전에 `POST /api/v1/product-images`로 상품 이미지를 업로드하고 응답의 `imageUrl`을 `product.imageUrl`에 전달한다.

AI 상품 자동 완성을 사용하는 경우에도 최종적으로 서버 저장소에 저장된 이미지 URL을 받아 동일한 필드에 전달해야 한다. AI API 담당 명세에 이미지 URL이 추가되기 전까지는 상품 이미지 업로드 API를 사용한다.

## Response
### 201 Created
```json
{
  "auctionId": 100,
  "productId": 501,
  "sellerId": 25,
  "status": "WAITING",
  "reservePrice": 50000,
  "durationMinutes": 15,
  "startType": "SCHEDULED",
  "scheduledAt": "2026-07-21T20:00:00+09:00",
  "createdAt": "2026-07-21T18:00:00+09:00"
}
```
## 검증 규칙
- 상품명·설명·카테고리·이미지 URL은 필수다.
- `product.imageUrl`은 현재 판매자가 상품 이미지 업로드 API로 발급받은 URL이어야 하며, 임의의 외부 URL은 허용하지 않는다.
- 하한가는 0보다 큰 정수여야 한다.
- 진행 시간은 서버가 허용한 범위여야 한다.
- `SCHEDULED`이면 `scheduledAt`은 현재 서버 시각 이후여야 한다.
- `IMMEDIATE`이면 `scheduledAt`을 생략한다.
- 어떤 시작 방식이든 생성 직후 상태는 `WAITING`이다.
- 판매자가 방송 시작 API를 호출하기 전에는 입찰·예측을 받을 수 없다.
## 처리 흐름
```plain text
판매자 인증
→ 상품 정보와 경매 조건 검증
→ Product 생성
→ AuctionRoom 생성 및 productId 연결
→ WAITING 상태 저장
→ 트랜잭션 커밋
→ 생성 결과 반환
```
## 오류 응답
<table>
<tr>
<td>HTTP</td>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>400</td>
<td>`INVALID_PRODUCT_REQUEST`</td>
<td>상품 정보 형식 오류</td>
</tr>
<tr>
<td>400</td>
<td>`INVALID_AUCTION_REQUEST`</td>
<td>하한가·진행 시간·시작 방식 오류</td>
</tr>
<tr>
<td>401</td>
<td>`UNAUTHORIZED`</td>
<td>인증 실패</td>
</tr>
<tr>
<td>422</td>
<td>`INVALID_SCHEDULE_TIME`</td>
<td>예약 시각이 유효하지 않음</td>
</tr>
</table>

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [경매방 생성 원본](https://app.notion.com/177776ebfd68839c9cd381a5ae5383ca)

