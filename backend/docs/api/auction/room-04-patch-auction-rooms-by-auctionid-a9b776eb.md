# 경매방 수정

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [경매방 수정](https://app.notion.com/a9b776ebfd68830982d301103fc577a5)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `ROOM-04` |
| 저장소 도메인 | `auction` |
| Notion 도메인 | 경매 |
| Method | `PATCH` |
| Path / Destination | `/api/v1/auction-rooms/{auctionId}` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | Seller |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `AuctionRoomUpdateController` (`2026-07-30` 확인) |
| 우선순위 | Medium |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, PostgreSQL, 경매 |
| 마지막 수정 | 2026-07-27 02:08:11Z |

## 요약

판매자가 시작 전 경매방의 설명·하한가·예약 시간을 수정한다.

## 개요
판매자가 방송 시작 전 `WAITING` 상태의 상품 정보와 경매 조건을 수정한다. 변경하지 않는 객체 또는 필드는 생략할 수 있다.
## Request
```javascript
PATCH /api/v1/auction-rooms/{auctionId}
Authorization: Bearer {accessToken}
Content-Type: application/json
```
```json
{
  "product": {
    "title": "키크론 Q1 Pro 풀박스",
    "description": "구성품 설명 수정",
    "category": "ELECTRONICS",
    "imageUrl": "https://cdn.example.com/products/changed.jpg"
  },
  "auction": {
    "reservePrice": 55000,
    "durationMinutes": 30,
    "startType": "SCHEDULED",
    "scheduledAt": "2026-07-21T21:00:00+09:00"
  }
}
```
### Path Variable
<table>
<tr>
<td>이름</td>
<td>타입</td>
<td>필수</td>
<td>설명</td>
</tr>
<tr>
<td>`auctionId`</td>
<td>Long</td>
<td>필수</td>
<td>수정할 경매방 ID</td>
</tr>
</table>
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
<td>선택</td>
<td>변경할 상품명</td>
</tr>
<tr>
<td>`product.description`</td>
<td>String</td>
<td>선택</td>
<td>변경할 상품 설명</td>
</tr>
<tr>
<td>`product.category`</td>
<td>String</td>
<td>선택</td>
<td>변경할 카테고리 코드</td>
</tr>
<tr>
<td>`product.imageUrl`</td>
<td>String</td>
<td>선택</td>
<td>변경할 단일 상품 이미지 URL</td>
</tr>
<tr>
<td>`auction.reservePrice`</td>
<td>Long</td>
<td>선택</td>
<td>변경할 공개 하한가</td>
</tr>
<tr>
<td>`auction.durationMinutes`</td>
<td>Integer</td>
<td>선택</td>
<td>변경할 기본 진행 시간</td>
</tr>
<tr>
<td>`auction.startType`</td>
<td>Enum</td>
<td>선택</td>
<td>`IMMEDIATE` 또는 `SCHEDULED`</td>
</tr>
<tr>
<td>`auction.scheduledAt`</td>
<td>DateTime</td>
<td>조건부</td>
<td>`SCHEDULED`로 변경할 때 필수. 안내용이며 자동 시작하지 않음</td>
</tr>
</table>
## Response
### 200 OK
```json
{
  "auctionId": 100,
  "productId": 501,
  "status": "WAITING",
  "product": {
    "title": "키크론 Q1 Pro 풀박스",
    "description": "구성품 설명 수정",
    "category": "ELECTRONICS",
    "imageUrl": "https://cdn.example.com/products/changed.jpg"
  },
  "auction": {
    "reservePrice": 55000,
    "durationMinutes": 30,
    "startType": "SCHEDULED",
    "scheduledAt": "2026-07-21T21:00:00+09:00"
  },
  "updatedAt": "2026-07-21T19:00:00+09:00"
}
```
## 검증 규칙
- 요청자가 해당 경매방의 판매자여야 한다.
- 방 상태가 `WAITING`일 때만 수정할 수 있다.
- 요청에는 최소 한 개 이상의 수정 필드가 있어야 한다.
- `IMMEDIATE`로 변경하면 `scheduledAt`은 제거한다.
- `SCHEDULED`이면 미래의 `scheduledAt`이 필요하다.
- 예약 시각 도달만으로 방을 시작하지 않는다.
## 처리 흐름
```plain text
판매자 인증
→ 경매방 소유권과 WAITING 상태 확인
→ 수정 필드 검증
→ Product·AuctionRoom 갱신
→ 수정 결과 반환
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
<td>`EMPTY_UPDATE_REQUEST`</td>
<td>수정할 필드가 없음</td>
</tr>
<tr>
<td>400</td>
<td>`INVALID_AUCTION_REQUEST`</td>
<td>수정값 형식 또는 범위 오류</td>
</tr>
<tr>
<td>401</td>
<td>`UNAUTHORIZED`</td>
<td>인증 실패</td>
</tr>
<tr>
<td>403</td>
<td>`NOT_AUCTION_SELLER`</td>
<td>해당 경매방의 판매자가 아님</td>
</tr>
<tr>
<td>404</td>
<td>`AUCTION_NOT_FOUND`</td>
<td>경매방이 존재하지 않음</td>
</tr>
<tr>
<td>409</td>
<td>`AUCTION_UPDATE_NOT_ALLOWED`</td>
<td>방송 시작 후이거나 수정 불가 상태</td>
</tr>
</table>

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [경매방 수정 원본](https://app.notion.com/a9b776ebfd68830982d301103fc577a5)

