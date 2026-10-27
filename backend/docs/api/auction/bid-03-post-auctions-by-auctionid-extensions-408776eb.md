# 판매자 방송 연장

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [판매자 방송 연장](https://app.notion.com/408776ebfd6882e4971f815e0117ca96)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `BID-03` |
| 저장소 도메인 | `auction` |
| Notion 도메인 | 방송 |
| Method | `POST` |
| Path / Destination | `/api/v1/auctions/{auctionId}/extensions` |
| 상태 | 확정 |
| 구현 | 구현 완료 — `AuctionExtensionController` |
| 인증 | 필요 |
| 공개 범위 | Seller |
| 담당자 | 김택림 |
| 우선순위 | High |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, 경매, Redis, PostgreSQL |
| 마지막 수정 | 2026-07-31 |

## 개요
판매자가 진행 중인 경매의 마감 시각을 5분 연장한다. 서버 시각을 기준으로 연장 가능 여부를 판정한다.
## Request
```javascript
POST /api/v1/auctions/{auctionId}/extensions
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
<td>연장할 경매 ID</td>
</tr>
</table>
### Request Body
요청 본문 없음. 연장 시간은 서버 정책상 항상 5분이다.
## Response
### 200 OK
```json
{
  "auctionId": 100,
  "previousEndAt": "2026-07-20T21:20:00+09:00",
  "endAt": "2026-07-20T21:25:00+09:00",
  "extensionCount": 2,
  "remainingExtensions": 1
}
```
## 검증 규칙
- 요청자가 해당 경매의 판매자인지 확인
- 경매 상태가 `LIVE`인지 확인
- 연장 횟수가 3회 미만인지 확인
- 서버 기준 남은 시간이 1분 이상인지 확인
- 동일 요청의 중복 처리를 방지

남은 시간이 정확히 1분이면 연장할 수 있고, 1분 미만이면 `EXTENSION_TOO_LATE`로
거절한다.

## 상태 저장

- Redis 경매 상태의 `endAt`, `extensionCount`, `version`을 하나의 원자 연산으로
  갱신한다.
- PostgreSQL `auction_room`의 `ends_at`, `extension_count`에는 Redis 연장 결과의
  최종 상태를 반영한다.
- 개별 연장 시각이나 이전 종료 시각을 별도 RoomEvent 테이블에 이력으로 저장하지
  않는다.
- BID-03을 위해 별도 RoomEvent 테이블이나 DB migration을 추가하지 않는다.

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
<td>경매가 존재하지 않음</td>
</tr>
<tr>
<td>409</td>
<td>`AUCTION_NOT_LIVE`</td>
<td>진행 중인 LIVE 경매가 아님</td>
</tr>
<tr>
<td>409</td>
<td>`EXTENSION_LIMIT_EXCEEDED`</td>
<td>최대 연장 횟수 초과</td>
</tr>
<tr>
<td>409</td>
<td>`EXTENSION_TOO_LATE`</td>
<td>남은 시간이 1분 미만</td>
</tr>
</table>
## 후속 WebSocket 이벤트

연장 상태 반영이 완료되면 `/topic/auctions/{auctionId}`에
`AUCTION_EXTENDED`를 먼저 발행한다.

```json
{
  "type": "AUCTION_EXTENDED",
  "data": {
    "endAt": "2026-07-20T21:25:00+09:00",
    "extensionCount": 2,
    "remainingExtensions": 1
  }
}
```

이어서 같은 destination에 `TIME_SYNC`를 발행한다.

```json
{
  "type": "TIME_SYNC",
  "data": {
    "serverTime": "2026-07-20T21:20:03+09:00",
    "endAt": "2026-07-20T21:25:00+09:00"
  }
}
```

- `serverTime`은 `TIME_SYNC` 이벤트를 생성하는 시점의 서버 시각이다.
- 클라이언트는 `serverTime`으로 서버와 클라이언트의 시각 차이를 다시 계산하고,
  `endAt`을 기준으로 카운트다운을 보정한다.
- BID-03은 연장 직후의 `TIME_SYNC` 발행만 담당한다. 최초 구독, 재연결 및 주기적
  오차 보정은 BID-05의 범위다.

## 처리 흐름
```plain text
판매자 연장 요청
→ 판매자 권한 및 서버 시각 검증
→ Redis의 endAt, extensionCount, version 원자적 갱신
→ auction_room의 ends_at, extension_count에 최종 상태 반영
→ AUCTION_EXTENDED 브로드캐스트
→ TIME_SYNC 브로드캐스트로 모든 클라이언트의 서버 시각 기준 카운트다운 보정
```

## 미확정 사항

- 현재 Request 계약에는 동일 요청을 식별할 멱등 키가 정의되어 있지 않다.
- `Idempotency-Key` 헤더 등 식별자 전달 방식을 확정하기 전에는 중복 처리 방식을
  임의로 구현하지 않는다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [판매자 방송 연장 원본](https://app.notion.com/408776ebfd6882e4971f815e0117ca96)
