# PREDICTION_ACCEPTED

> PRED-01 예측 접수의 공개 실시간 집계 계약이다.

## 메타데이터

| 항목 | 값 |
| --- | --- |
| Event Type | `PREDICTION_ACCEPTED` |
| 저장소 도메인 | `prediction` |
| 연관 기능 ID | `PRED-01` |
| Destination | `/topic/auctions/{auctionId}` |
| 수신 대상 | 경매방 전체 |
| 상태 | 확정 |
| 발생 시점 | 신규 예측 접수 트랜잭션 커밋 후 |
| data 필드 | participantCount, vibeMessage |

## 설명

새로운 예측 참여가 확정됐음을 경매방 전체에 알린다. 비공개 예측 정책을 유지하기 위해 참여자의
식별 정보, 예측 금액과 스테이크는 보내지 않고 입찰·예측 전체 고유 참여자 수와 고정 안내 문구만
공개한다.

## Payload

```json
{
  "type": "PREDICTION_ACCEPTED",
  "data": {
    "participantCount": 9,
    "vibeMessage": "누군가 예측에 참여했습니다!"
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `participantCount` | number | 입찰 또는 예측에 참여한 해당 경매의 누적 고유 사용자 수 |
| `vibeMessage` | String | `누군가 예측에 참여했습니다!` 고정 문구 |

## 처리 규칙

- PostgreSQL 저장과 베팅 포인트 차감이 포함된 접수 트랜잭션이 커밋된 뒤 발행한다.
- 최초로 승인된 신규 예측에만 한 번 발행한다.
- 동일한 `clientRequestId`의 멱등 재시도에는 다시 발행하지 않는다.
- 접수 트랜잭션이 롤백되거나 요청이 거절되면 발행하지 않는다.
- `auctionId`는 Destination에 포함되므로 메시지 본문에서 제외한다.
- 회원 ID·닉네임, `predictedPrice`, `stakeAmount`와 실시간 분포는 포함하지 않는다.

## 출처

- 로컬 확정 계약 — `PRED-01` 예측 접수의 실시간 집계 확장
