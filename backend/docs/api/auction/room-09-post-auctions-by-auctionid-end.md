# 판매자 경매 종료

> **로컬에서 작성한 계약이다.** Notion 최종 종합 명세에 대응 항목이 없다 — 판매자가 진행 콘솔에서
> 방송을 끝냈을 때 경매를 함께 닫는 경로가 명세에 비어 있었고, 그 공백이 실제 사용에서 드러나
> (S15P11A704-687) 여기서 정의한다. 사용하는 값(`HOST_LEFT`)은 새로 만든 것이 아니라
> BID-07 비크리 엔진이 이미 정의한 `auction_result.termination_reason`의 한 값이다.

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `ROOM-09` |
| 저장소 도메인 | `auction` |
| Method | `POST` |
| Path | `/api/v1/auctions/{auctionId}/end` |
| 상태 | 확정 |
| 인증 | 필요 (판매자 본인) |
| 공개 범위 | Private |
| 우선순위 | High |
| 의존 대상 | 경매, 지갑, 예측, Redis, PostgreSQL |

## 목적

판매자가 진행 콘솔에서 방송을 끝낼 때 경매도 함께 닫는다.

방송 송출 종료(`LIVE-02` `POST /api/v1/rooms/{roomId}/live/end`)는 `live_broadcast` 행과 OpenVidu
세션만 정리하고 `auction_room.status`를 건드리지 않는다. 그래서 그것만 부르면 **방송은 끊겼는데
경매 타이머는 계속 돌고 입찰도 계속 접수된다.** 이 API가 그 나머지를 맡는다.

## 낙찰이 나지 않는다

마감 시각까지 간 정상 종료가 아니므로 **유찰(`HOST_LEFT`)로 닫고 입찰 홀드를 전액 해제한다.
입찰이 있었더라도 그렇다.**

근거는 BID-07이 정한 `auction_result.termination_reason = NO_BID | HOST_LEFT | FORCED`다.
`HOST_LEFT`는 유찰 사유이고, 결과 조회(`DEAL-06`)가 이미 「판매자 이탈로 종료됐어요」로 문구를
만들고 있었다. 낙찰까지 내리는 동작이 필요하면 그것은 종료가 아니라 **마감을 당기는** 별개
기능이고 이 계약이 할 일이 아니다.

## 요청

본문이 없다. 사유는 서버가 `HOST_LEFT`로 고정한다 — 「왜 끝냈는가」는 판매자의 사정이고
참여자에게 전할 계약이 없다. 운영 강제 종료(`DEAL-09`)가 본문으로 사유를 받는 것과 다른 점이다.

```http
POST /api/v1/auctions/{auctionId}/end
```

## 성공 응답 `200`

```json
{
  "auctionId": 100,
  "status": "ENDED",
  "resultType": "HOST_LEFT",
  "endedAt": "2026-08-03T15:04:05+09:00",
  "releasedBidCount": 2,
  "releasedBidAmount": 12000,
  "refundedPredictionCount": 3
}
```

해제 건수·금액을 돌려주는 이유: 판매자에게 「몇 명의 입찰이 풀렸는지」는 종료의 결과 그 자체다.

## 오류

| 상태 | 코드 | 조건 |
| --- | --- | --- |
| `400` | `INVALID_AUCTION_ID` | 경로 변수가 양의 정수가 아니다 |
| `401` | `UNAUTHORIZED` | 인증이 없다 |
| `404` | `AUCTION_NOT_FOUND` | 경매가 없거나 삭제됐다 |
| `403` | `NOT_AUCTION_SELLER` | 내 방이 아니다 |
| `409` | `AUCTION_NOT_ENDABLE` | 이미 종료됐다 (`WAITING`·`LIVE`만 종료할 수 있다) |

판정 순서가 계약이다: 인증 → 없음·삭제됨 → 판매자 아님 → 종료 불가.

**삭제된 방을 `404`로 감춘다.** 운영 강제 종료는 같은 경우를 `409`로 알려 주는데, 그쪽은 경로가
`hasRole("ADMIN")`이라 존재를 숨길 이유가 없다. 이 경로는 아무 회원이나 부를 수 있다.

## 처리 흐름

```plain text
판매자 판정
→ auction_room WAITING|LIVE → ENDED 조건부 UPDATE
→ auction_result 생성 (result_status=NO_SALE, termination_reason=HOST_LEFT)
→ 입찰 홀드 전액 해제
→ 예측 베팅 전액 환급
→ AUCTION_NO_SALE 브로드캐스트 (reason=HOST_LEFT)
```

운영 강제 종료(`DEAL-09`)와 **같은 절차**이고 대상 판정과 결과 행의 사유만 다르다. 구현도 같은
서비스의 진입 메서드 둘로 나뉘어 있다.

## 멱등성

상태 전이가 조건부 UPDATE라 두 번째 호출은 0행이 되고 `409`로 끊긴다 — 환급 단계에 도달하지
않으므로 중복 환급이 없다.

## 입력 차단은 Redis가 맡는다

입찰·예측 접수는 PostgreSQL을 보지 않고 Redis 상태 Hash의 `status`만 본다
(`bid-process.lua`·`prediction-participation-process.lua`). 그래서 `ENDED` 전이만으로는 막히지
않고, 발행된 종료 사실을 받은 `RedisAuctionStateCloser`가 그 Hash를 닫아야 실제로 차단된다.
그 리스너는 `BEFORE_COMMIT`이라 실패하면 이 요청 전체가 롤백된다(S15P11A704-537).

## 관련 이벤트

| Event Type | Destination | data |
| --- | --- | --- |
| [`AUCTION_NO_SALE`](./events/auction-no-sale.md) | `/topic/auctions/{auctionId}` | `reason` (`HOST_LEFT`), `endedAt` |

그 이벤트 명세의 「발생 시점」이 *"유효 입찰이 없거나 낙찰 없이 경매가 종료되었을 때"*라 이 경우를
이미 포함한다. 새 이벤트를 만들지 않는다.

`AUCTION_CANCELED`를 쓰지 않는 이유: 프론트가 그 이벤트를 「운영팀이 이 방송을 종료했어요」로
그린다. 판매자가 끝낸 방에 그것이 뜨면 화면이 거짓말을 한다.
