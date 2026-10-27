# 경매방 삭제

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [경매방 삭제](https://app.notion.com/53b776ebfd68824b836a81f31ad0a829)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `ROOM-04` |
| 저장소 도메인 | `auction` |
| Notion 도메인 | 경매 |
| Method | `DELETE` |
| Path / Destination | `/api/v1/auction-rooms/{auctionId}` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | Seller |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `AuctionRoomDeleteController` (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, PostgreSQL, 경매 |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 요약

판매자가 방송 시작 전 경매방을 삭제한다.

## 비고

시작 후 삭제 불가

## 개요
판매자가 방송 시작 전 상태의 경매방을 삭제한다. 실제 데이터는 정책에 따라 논리 삭제할 수 있다.
## Request
```javascript
DELETE /api/v1/auction-rooms/{auctionId}
Authorization: Bearer {accessToken}
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
<td>삭제할 경매방 ID</td>
</tr>
</table>
### Request Body
요청 본문 없음.
## Response
### 204 No Content
응답 본문 없음.
## 검증 규칙
- 요청자가 해당 경매방의 판매자여야 한다.
- 상태가 `DRAFT`, `SCHEDULED`, `WAITING` 중 하나여야 한다.
- 입찰·예측·거래 이력이 생성된 방은 삭제할 수 없다.
- 이미 삭제된 방에 대한 요청은 멱등하게 처리하거나 `404`로 통일한다.
## 오류 응답
<table>
<tr>
<td>HTTP</td>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>401</td>
<td>`UNAUTHORIZED`</td>
<td>인증 실패</td>
</tr>
<tr>
<td>403</td>
<td>`NOT_AUCTION_SELLER`</td>
<td>해당 경매의 판매자가 아님</td>
</tr>
<tr>
<td>404</td>
<td>`AUCTION_NOT_FOUND`</td>
<td>경매방이 존재하지 않음</td>
</tr>
<tr>
<td>409</td>
<td>`AUCTION_ALREADY_STARTED`</td>
<td>방송 또는 경매가 이미 시작됨</td>
</tr>
<tr>
<td>409</td>
<td>`AUCTION_HAS_PARTICIPATION`</td>
<td>입찰·예측 등 참여 이력이 존재함</td>
</tr>
</table>
## 처리 흐름
```plain text
판매자 인증
→ auctionId와 소유권 확인
→ 방 상태와 참여 이력 확인
→ AuctionRoom 논리 삭제
→ 상품 판매 가능 상태 복구
→ 204 No Content 반환
```

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [경매방 삭제 원본](https://app.notion.com/53b776ebfd68824b836a81f31ad0a829)

