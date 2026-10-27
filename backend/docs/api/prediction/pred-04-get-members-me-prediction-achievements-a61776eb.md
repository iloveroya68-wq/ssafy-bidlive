# 명예 뱃지/등급 점수

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [명예 뱃지/등급 점수](https://app.notion.com/a61776ebfd68838ab870811edfc3f173)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `PRED-04` |
| 저장소 도메인 | `prediction` |
| Notion 도메인 | 예측 |
| Method | `GET` |
| Path / Destination | `/api/v1/members/me/prediction-achievements` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 김택림 |
| 우선순위 | Low |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front |
| 마지막 수정 | 2026-07-27 02:08:11Z |

## 개요
로그인 사용자의 예측 베팅 적중 이력과 명예 뱃지·등급 점수를 조회한다.
## Request
```javascript
GET /api/v1/members/me/prediction-achievements
Authorization: Bearer {accessToken}
```
## Response
### 200 OK
```json
{
  "memberId": 25,
  "predictionCount": 18,
  "hitCount": 7,
  "hitRate": 38.9,
  "totalStakedPoints": 9200,
  "totalRewardPoints": 13400,
  "grade": {
    "code": "ORACLE_2",
    "name": "시세 예언자 II",
    "score": 720
  },
  "badges": [
    {
      "badgeCode": "FIRST_HIT",
      "name": "첫 적중",
      "earnedAt": "2026-07-20T21:30:00+09:00"
    }
  ]
}
```
## 응답 필드
- `hitRate`: `hitCount / predictionCount × 100`, 소수점 한 자리
- `totalStakedPoints`: 누적 사용 베팅 포인트
- `totalRewardPoints`: 누적 배당 포인트
- `grade`: 적중 이력 기반 명예 등급
- `badges`: 획득한 명예 뱃지 목록

## 집계 규칙

- `prediction_result` 행이 존재하면 해당 예측은 정산 완료된 것으로 간주한다.
- `predictionCount`는 결과 상태가 `HIT` 또는 `MISS`인 정산 건수다.
- `hitCount`는 결과 상태가 `HIT`인 정산 건수다.
- `hitRate`는 `hitCount / predictionCount × 100`으로 계산하고 소수점 한 자리로
  반환한다.
- `totalStakedPoints`는 집계 대상과 연결된 `prediction.stake_amount`의 합계다.
- `totalRewardPoints`는 집계 대상 `prediction_result.payout_amount`의 합계다.
- `REFUNDED` 결과는 건수, 적중률, 누적 사용 베팅 포인트 및 누적 배당 포인트
  집계에서 제외한다.
- 통계 값은 예측 원본과 정산 결과를 기준으로 계산하며 별도의 누적 통계로 저장하지
  않는다.

## 등급·뱃지 표시 이름

- 등급은 `ORACLE_1`부터 `ORACLE_5`까지 각각 `시세 예언자 I`부터
  `시세 예언자 V`로 표시한다.
- 뱃지는 `FIRST_HIT`(첫 적중), `PINPOINT`(정밀 예측), `HIT_10`(적중 10회),
  `PREDICTION_50`(예측 정산 50회), `PINPOINT_10`(5% 이내 적중 10회),
  `ORACLE_MASTER`(최고 등급 달성)를 지원한다.

## 정산 이력이 없는 회원

- `predictionCount`, `hitCount`, `totalStakedPoints`, `totalRewardPoints`는 `0`이다.
- `hitRate`는 `0.0`이다.
- `grade`는 점수 `0`에 해당하는 초기 등급이다.
- `badges`는 빈 배열이다.

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
<td>404</td>
<td>`MEMBER_NOT_FOUND`</td>
<td>회원이 존재하지 않음</td>
</tr>
</table>
## 구현 메모
P2 기능이다. 등급·뱃지 계산은 PRED-03 정산 완료 시 갱신하고 이 API는 조회만 담당하도록 분리한다.

- 현재 등급 코드와 점수는 `prediction_achievement`에서 조회한다.
- 획득한 뱃지 코드와 획득 시각은 `prediction_badge`에서 조회한다.
- 등급과 뱃지의 표시 이름은 저장된 코드를 기준으로 애플리케이션에서 변환한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [명예 뱃지/등급 점수 원본](https://app.notion.com/a61776ebfd68838ab870811edfc3f173)

