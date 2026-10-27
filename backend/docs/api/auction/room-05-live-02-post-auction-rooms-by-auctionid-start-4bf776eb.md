# 경매 방송 시작

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [경매 방송 시작](https://app.notion.com/4bf776ebfd68835885dd8115af832c89)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `ROOM-05, LIVE-02` |
| 저장소 도메인 | `auction` |
| Notion 도메인 | 경매 |
| Method | `POST` |
| Path / Destination | `/api/v1/auction-rooms/{auctionId}/start` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | Seller |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `AuctionRoomStartController` (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | Front↔BE, BE↔Redis, BE 내부 |
| 의존 대상 | Front, Redis, PostgreSQL, 경매 |
| 마지막 수정 | 2026-07-27 02:08:13Z |

## 요약

판매자가 대기 중인 경매방의 방송을 직접 시작하고 라이브 상태와 서버 기준 종료 시각을 확정한다.

## 비고

기존 예약 시각 설정 API를 수동 방송 시작 API로 변경. scheduledAt은 안내용

## 개요
예약 방송 시각은 대기방 안내에만 사용한다. 실제 방송과 경매는 판매자가 시작 버튼을 눌렀을 때 시작하며, 서버가 그 시각을 기준으로 종료 시각을 계산한다.
## Request
```javascript
POST /api/v1/auction-rooms/{auctionId}/start
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
<td>시작할 경매방 ID</td>
</tr>
</table>
### Request Body
요청 본문 없음.
## Response
### 200 OK
```json
{
  "auctionId": 100,
  "status": "LIVE",
  "startedAt": "2026-07-21T20:02:13+09:00",
  "endAt": "2026-07-21T20:17:13+09:00",
  "durationMinutes": 15,
  "serverTime": "2026-07-21T20:02:13+09:00"
}
```
## 검증 규칙
- 요청자가 해당 경매방의 판매자여야 한다.
- 현재 상태가 `WAITING`이어야 한다.
- 예약 시각 전후와 관계없이 판매자가 직접 시작할 수 있다.
- 상품 검열 결과가 방송 시작을 차단하는 상태라면 시작할 수 없다.
- 중복 시작 요청은 두 번 처리하지 않는다.
- 종료 시각은 `startedAt + durationMinutes`로 서버가 계산한다.
## 처리 흐름
```plain text
판매자 인증
→ 경매방 소유권·WAITING 상태 확인
→ 방송 세션 준비 상태 확인
→ startedAt과 endAt 확정
→ Redis 실시간 경매 상태 초기화
→ 방 상태를 LIVE로 변경
→ AUCTION_STARTED 이벤트 발행
→ 시작 정보 반환
```
## WebSocket Event
```json
{
  "type": "AUCTION_STARTED",
  "data": {
    "startedAt": "2026-07-21T20:02:13+09:00",
    "endAt": "2026-07-21T20:17:13+09:00"
  }
}
```
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
<td>해당 경매방의 판매자가 아님</td>
</tr>
<tr>
<td>404</td>
<td>`AUCTION_NOT_FOUND`</td>
<td>경매방이 존재하지 않음</td>
</tr>
<tr>
<td>409</td>
<td>`AUCTION_START_NOT_ALLOWED`</td>
<td>현재 상태에서 시작할 수 없음</td>
</tr>
<tr>
<td>409</td>
<td>`AUCTION_ALREADY_STARTED`</td>
<td>이미 시작된 경매방</td>
</tr>
<tr>
<td>422</td>
<td>`AUCTION_MODERATION_BLOCKED`</td>
<td>검열 결과로 방송 시작이 차단됨</td>
</tr>
</table>

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [경매 방송 시작 원본](https://app.notion.com/4bf776ebfd68835885dd8115af832c89)

