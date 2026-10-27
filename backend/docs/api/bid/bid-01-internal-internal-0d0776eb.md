# 원자적 입찰 처리

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [원자적 입찰 처리](https://app.notion.com/0d0776ebfd6883f78c1b816f192afa69)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `BID-01` |
| 저장소 도메인 | `bid` |
| Notion 도메인 | 입찰 |
| Method | `INTERNAL` |
| Path / Destination | 내부 처리 |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | Internal |
| 담당자 | 김택림 |
| 우선순위 | High |
| 연결 구분 | BE↔Redis |
| 의존 대상 | Redis, 경매, PostgreSQL |
| 마지막 수정 | 2026-07-27 02:08:14Z |

> **내부 처리 명세** — 프론트가 직접 호출하는 API가 아니라 `BID-06`: `POST /api/v1/auctions/{auctionId}/bids` 내부에서 실행되는 Redis Lua 기반 입찰 처리 로직이다.
## 목적
동시에 여러 입찰 요청이 들어와도 입찰 검증, 본인 최고 입찰가·최종 접수 시각 갱신, 참여 상태 변경과 회원 전역 거래 포인트 지갑의 차액 홀드를 하나의 Redis Lua 원자 작업으로 처리한다.
## 호출 위치
```java
BidProcessingResult process(BidCommand command)
```
## 입력
```json
{
  "auctionId": 100,
  "memberId": 25,
  "amount": 82000,
  "requestId": "9a61c1e4-7ad4-4e7c-83b5-a63edc00ac20",
  "requestedAt": "2026-07-20T21:12:44+09:00"
}
```
<table>
<tr>
<td>필드</td>
<td>타입</td>
<td>설명</td>
</tr>
<tr>
<td>`auctionId`</td>
<td>Long</td>
<td>입찰 대상 경매 ID</td>
</tr>
<tr>
<td>`memberId`</td>
<td>Long</td>
<td>인증된 입찰자 ID</td>
</tr>
<tr>
<td>`amount`</td>
<td>Long</td>
<td>새로 제출한 최종 입찰 금액</td>
</tr>
<tr>
<td>`requestId`</td>
<td>String</td>
<td>중복 요청 방지용 고유 ID</td>
</tr>
<tr>
<td>`requestedAt`</td>
<td>DateTime</td>
<td>서버가 접수한 요청 시각</td>
</tr>
</table>
## Redis 검증 규칙
- 경매 상태가 `LIVE`인지 확인
- 서버 기준 현재 시각이 `endAt` 이전인지 확인
- 입찰 금액이 하한가 이상인지 확인
- 본인의 기존 입찰가보다 높은지 확인
- 판매자가 자기 경매에 입찰하는지 확인
- 해당 사용자가 예측 베팅에 먼저 참여했는지 확인
- 동일 `requestId`가 이미 처리되었는지 확인

## Redis 지갑 범위

- 거래 포인트 가용액과 홀드액은 경매별로 복제하지 않고 회원 전역 Redis 지갑에서 관리한다.
- 회원 전역 가용액은 해당 회원이 참여한 모든 경매가 공유하며, 한 경매에서 홀드한 금액을 다른 경매에서 다시 사용할 수 없다.
- 회원 전역 홀드액은 진행 중인 모든 경매의 입찰 홀드 합계다.
- 본인 기존 입찰가는 경매별로 관리한다.
- 최초 입찰은 입찰액 전액, 상향 입찰은 `새 입찰액 - 같은 경매의 기존 본인 입찰액`만 회원 전역 가용액에서 추가 홀드한다.
- 같은 회원의 서로 다른 경매 입찰이 동시에 요청되어도 가용액 검증과 차액 홀드는 원자적으로 직렬화되어야 한다.
- Lua가 접근하는 경매 상태와 회원 전역 지갑 Key는 하나의 원자 실행 단위에서 함께 처리할 수 있어야 한다.

## 원자 갱신 항목
- 경매별 회원 최종 입찰 금액
- 경매별 최종 접수 시각
- 경매별 누적 입찰 건수
- 경매별 고유 입찰 참여자 수
- 경매별 참여 유형 `NONE → BID`
- 회원 전역 거래 포인트 가용액 감소 및 홀드액 증가
- 경매방 상태 버전 또는 갱신 시각
- 경매별 중복 요청 처리 기록
## 결과 예시
```json
{
  "accepted": true,
  "previousBidAmount": 70000,
  "currentBidAmount": 82000,
  "additionalHoldAmount": 12000,
  "submittedAt": "2026-07-20T21:12:44+09:00",
  "bidCount": 13,
  "participantCount": 9
}
```
## 실패 코드
<table>
<tr>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>`AUCTION_NOT_LIVE`</td>
<td>입찰 가능한 LIVE 상태가 아님</td>
</tr>
<tr>
<td>`AUCTION_ALREADY_ENDED`</td>
<td>서버 기준 마감 시각 이후 요청</td>
</tr>
<tr>
<td>`BID_BELOW_RESERVE_PRICE`</td>
<td>하한가 미만 입찰</td>
</tr>
<tr>
<td>`BID_NOT_HIGHER_THAN_PREVIOUS`</td>
<td>본인 기존 입찰가 이하</td>
</tr>
<tr>
<td>`SELLER_CANNOT_BID`</td>
<td>판매자 자기 방 입찰</td>
</tr>
<tr>
<td>`PREDICTION_ALREADY_JOINED`</td>
<td>예측 베팅 참여 후 입찰 시도</td>
</tr>
<tr>
<td>`INSUFFICIENT_AVAILABLE_BALANCE`</td>
<td>가용 거래 포인트 부족</td>
</tr>
<tr>
<td>`DUPLICATE_REQUEST`</td>
<td>이미 처리한 요청</td>
</tr>
</table>
## 후속 처리
```plain text
Redis 회원 전역 TRADE 지갑이 없으면 PostgreSQL 스냅샷 최초 적재
→ Redis Lua 성공(입찰 상태 갱신 + 차액 홀드)
→ 입찰 이력과 포인트 원장 PostgreSQL 영속화
→ 요청자에게 REST 개인 응답
→ BID_ACCEPTED WebSocket 브로드캐스트
```

## 현재 구현 범위

- 경매별 잔액 Key를 회원 전역 Redis 지갑으로 분리하고, Redis 내부에서 여러 경매의 잔액 중복 사용을 방지한다.
- 신규 입찰 전에 PostgreSQL TRADE 지갑 행을 잠그고, Redis 가용액과 홀드액이 모두 없으면
  잠근 지갑 스냅샷을 Lua로 한 번만 함께 적재한다.
- Redis의 두 잔액이 이미 존재하면 DB 스냅샷으로 덮어쓰지 않으며, 둘 중 하나만 존재하는
  부분 초기화 상태는 입찰 전에 오류로 중단한다.
- Redis 승인 후 입찰 이력과 포인트 원장을 같은 PostgreSQL 트랜잭션에 영속화한다.
- 충전·출금·환불·정산에 따른 DB와 Redis 동기화는 이번 단계에 포함하지 않는다.
- Redis Stream과 비동기 Consumer 기반 재처리는 후속 범위다.

## 필수 테스트
- 동일 사용자의 상향 입찰
- 동일 회원이 여러 경매에 입찰할 때 회원 전역 가용액을 공유함
- 동일 회원의 여러 경매 동시 입찰에서 가용액을 중복 사용하지 못함
- 기존 금액 이하 거부
- 동시 다중 사용자 입찰
- 마감 경계 시각 입찰
- 동일 requestId 중복 요청
- 입찰·예측 상호 배타
- 판매자 자기 방 입찰 차단

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [원자적 입찰 처리 원본](https://app.notion.com/0d0776ebfd6883f78c1b816f192afa69)
