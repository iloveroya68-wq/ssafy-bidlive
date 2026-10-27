# 비크리 엔진

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [비크리 엔진](https://app.notion.com/c29776ebfd6882d481c7817bdc017e77)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `BID-07` |
| 저장소 도메인 | `auction` |
| Notion 도메인 | 경매 |
| Method | `INTERNAL` |
| Path / Destination | 내부 처리 |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | Internal |
| 담당자 | 김택림 |
| 우선순위 | High |
| 연결 구분 | BE↔Redis |
| 의존 대상 | 경매, Redis, PostgreSQL |
| 마지막 수정 | 2026-07-27 02:08:10Z |

> **내부 정산 명세** — 일반 사용자가 직접 호출하는 API가 아니라 경매 마감 스케줄러 또는 내부 종료 처리에서 호출한다.
## 목적
경매 종료 후 회원별 최종 입찰가를 기준으로 낙찰자와 최종 지불가를 결정하고 거래 포인트 홀드를 정산한다.
## 호출 위치
```java
AuctionSettlementResult settle(Long auctionId)
```
## 최종가 결정 규칙
```plain text
유효 입찰 0건
→ 유찰

유효 입찰 1건
→ 최고 입찰자 낙찰
→ 최종가 = 하한가

유효 입찰 2건 이상
→ 최고 입찰자 낙찰
→ 최종가 = max(하한가, 2등 입찰가)
```
## 순위 판정
1. 입찰 금액 내림차순
2. 최종 입찰 접수 시각 오름차순
3. 내부 입찰 ID 오름차순
동일 회원이 여러 번 상향 입찰했다면 회원별 최종 입찰가만 낙찰 순위 계산에 사용한다.
## 결과 예시
```json
{
  "auctionId": 100,
  "status": "SETTLED",
  "winnerId": 25,
  "highestBidAmount": 82000,
  "secondHighestBidAmount": 74000,
  "reservePrice": 50000,
  "finalPrice": 74000,
  "priceDecisionType": "SECOND_PRICE",
  "settledAt": "2026-07-20T21:20:00+09:00"
}
```
### priceDecisionType
- `SECOND_PRICE`: 2등 입찰가를 최종가로 사용
- `RESERVE_PRICE`: 단독 입찰 또는 2등가가 하한가보다 낮아 하한가를 사용
- `NO_SALE`: 유효 입찰이 없어 유찰
## 포인트 처리
- 낙찰자: 최종가만 실제 차감
- 낙찰자: 본인 최고 입찰가와 최종가의 차액을 홀드 해제
- 미낙찰자: 홀드 전액 해제
- 모든 차감·해제·복구 내역을 포인트 원장에 기록
## 중복 정산 방지
```plain text
Redis 상태 LIVE
→ 마감 시 `AUCTION_ENDED`를 발행하고 BID-10 집계 중 화면 진입
→ Lua로 SETTLING 선점
→ 한 실행만 정산 진행
→ 성공 시 SETTLED 또는 NO_SALE
```
이미 `SETTLING` 또는 `ENDED` 상태라면 중복 차감하지 않는다. 유찰 여부는 방 상태가 아니라 `auction_result.resultType=NO_BID | HOST_LEFT | FORCED`로 구분한다.
## 처리 흐름
```plain text
서버 마감 시각 도달
→ AUCTION_ENDED 브로드캐스트 및 BID-10 집계 중 화면 전환
→ Redis LIVE → SETTLING 원자 전환
→ 회원별 최종 입찰 조회 및 PostgreSQL 이중 검증
→ 비크리 순수 계산 함수 실행
→ 포인트 차감·홀드 해제
→ Deal 및 정산 결과 저장
→ auction_result와 Deal 저장
→ Redis 및 방 상태를 ENDED로 전환
→ SOLD이면 AUCTION_SETTLED, 유찰이면 AUCTION_NO_SALE 발행
→ PRED-03 예측 베팅 정산 비동기 실행
```
## 실패 처리
- 포인트 또는 DB 정산 실패 시 완료 상태로 변경하지 않는다.
- 재시도 시 포인트 원장의 정산 키로 중복 차감을 방지한다.
- `NO_BID`는 해제할 입찰 홀드가 없다.
- `HOST_LEFT` 또는 `FORCED`처럼 입찰 후 강제 종료된 경우에는 모든 입찰 홀드를 전액 해제한다.
## 필수 테스트
- 입찰 0건 유찰
- 단독 입찰 하한가 낙찰
- 2등가 낙찰
- 동액 최고가 선입찰 우선
- 상향 입찰 시 최종 접수 시각 기준
- 동시 정산 요청 중 한 번만 성공
- 낙찰자 차액 및 미낙찰자 전액 복구

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [비크리 엔진 원본](https://app.notion.com/c29776ebfd6882d481c7817bdc017e77)

