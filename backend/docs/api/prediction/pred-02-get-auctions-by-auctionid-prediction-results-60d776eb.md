# 예측 결과 분포 조회

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [예측 결과 분포 조회](https://app.notion.com/60d776ebfd688205a7de01e430410094)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `PRED-02` |
| 저장소 도메인 | `prediction` |
| Notion 도메인 | 예측 |
| Method | `GET` |
| Path / Destination | `/api/v1/auctions/{auctionId}/prediction-results` |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | Public |
| 담당자 | 김택림 |
| 우선순위 | Medium |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, PostgreSQL, 경매 |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 요약

경매 종료 후에만 오차 밴드별 예측 인원과 지급 합계를 조회한다.

## 비고

실시간 분포 공개 금지

## Response
```json
{
  "auctionId": 100,
  "settlementStatus": "COMPLETED",
  "bands": [
    {"band":"WITHIN_5","participantCount":2,"multiplier":5,"totalPayoutAmount":2500},
    {"band":"WITHIN_10","participantCount":5,"multiplier":3,"totalPayoutAmount":4200},
    {"band":"WITHIN_20","participantCount":3,"multiplier":1.5,"totalPayoutAmount":900},
    {"band":"MISS","participantCount":8,"multiplier":0,"totalPayoutAmount":0}
  ]
}
```
## 규칙
- 방이 종료되고 예측 정산이 완료된 뒤에만 공개한다.
- 진행 중에는 `409 PREDICTION_RESULT_NOT_AVAILABLE`을 반환한다.
- 개별 사용자의 예측 금액과 식별 정보는 공개하지 않는다.
- 낙찰가가 없는 종료라면 밴드 대신 전액 환불 결과를 반환한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [예측 결과 분포 조회 원본](https://app.notion.com/60d776ebfd688205a7de01e430410094)

