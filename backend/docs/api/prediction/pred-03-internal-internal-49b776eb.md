# 예측 고정 배당 정산

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [예측 고정 배당 정산](https://app.notion.com/49b776ebfd688312a6e481909c299229)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `PRED-03` |
| 저장소 도메인 | `prediction` |
| Notion 도메인 | 예측 |
| Method | `INTERNAL` |
| Path / Destination | 내부 처리 |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | Internal |
| 담당자 | 김택림 |
| 우선순위 | Medium |
| 연결 구분 | BE↔BE, BE 내부 |
| 의존 대상 | PostgreSQL, 포인트, 경매 |
| 마지막 수정 | 2026-07-27 02:08:11Z |

## 요약

경매 결과 확정 후 예측 오차율에 따른 고정 배당을 비동기로 계산하고 베팅 포인트 원장에 반영한다.

## 비고

5% 이내 ×5, 10% 이내 ×3, 20% 이내 ×1.5, 그 외 0

## 정산 규칙
```plain text
errorRate = abs(predictedPrice - finalPrice) / finalPrice × 100
5% 이내  → stakeAmount × 5
10% 이내 → stakeAmount × 3
20% 이내 → floor(stakeAmount × 1.5)
그 외    → 0
```

## 명예 점수·등급 정책

점수는 베팅 금액과 무관하게 정산 밴드에 따라 누적한다.

| 정산 밴드 | 획득 점수 |
| --- | ---: |
| `WITHIN_5` | 100 |
| `WITHIN_10` | 50 |
| `WITHIN_20` | 20 |
| `MISS` | 0 |
| `REFUNDED` | 집계 제외 |

| 등급 코드 | 누적 점수 |
| --- | ---: |
| `ORACLE_1` | 0~299 |
| `ORACLE_2` | 300~999 |
| `ORACLE_3` | 1,000~2,499 |
| `ORACLE_4` | 2,500~4,999 |
| `ORACLE_5` | 5,000 이상 |

## 뱃지 정책

- `FIRST_HIT`: 최초 적중
- `PINPOINT`: 최초 `WITHIN_5` 적중
- `HIT_10`: 누적 적중 10회
- `PREDICTION_50`: 환불을 제외한 정산 50회
- `PINPOINT_10`: `WITHIN_5` 누적 적중 10회
- `ORACLE_MASTER`: 최초 `ORACLE_5` 달성

## 처리 흐름
```plain text
auction_result 확정
→ 정산 대상 prediction 조회
→ 오차율·밴드·배수 계산
→ payoutAmount 계산
→ BETTING 지갑 반영
→ point_ledger 배당 기록
→ prediction_result HIT/MISS 생성
→ prediction_achievement 등급 갱신
→ 새로 획득한 prediction_badge 기록
```
- 입찰·낙찰 정산 트랜잭션과 분리한다.
- 동일 예측은 한 번만 정산한다.
- `NO_BID`, `HOST_LEFT`, `FORCED`이면 전액 환불하고 `REFUNDED` 처리한다.
- `prediction_result` 행의 생성은 해당 예측의 정산 완료를 의미한다.
- `PAYOUT`·`REFUND` 원장과 BETTING 지갑 변경이 커밋되면 지급액만큼 Redis의
  BETTING 가용 잔액을 증가시킨다.
- 등급 점수와 뱃지는 최초 `prediction_result` 생성에 성공한 경우에만 반영한다.
- 회원별 현재 등급은 `prediction_achievement`에 갱신한다.
- 획득한 뱃지는 `prediction_badge`에 기록하며 같은 회원에게 같은 `badgeCode`를 중복
  부여하지 않는다.
- 재처리 요청은 이미 존재하는 `prediction_result`를 기준으로 등급 점수와 뱃지
  갱신까지 함께 건너뛴다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [예측 고정 배당 정산 원본](https://app.notion.com/49b776ebfd688312a6e481909c299229)

