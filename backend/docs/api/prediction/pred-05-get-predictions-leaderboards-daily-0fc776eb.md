# 일일 낙찰가 예측 리더보드 조회

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [일일 낙찰가 예측 리더보드 조회](https://app.notion.com/0fc776ebfd68824a851a014f870dfe96)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `PRED-05` |
| 저장소 도메인 | `prediction` |
| Notion 도메인 | 예측 |
| Method | `GET` |
| Path / Destination | `/api/v1/predictions/leaderboards/daily` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 김택림 |
| 우선순위 | Low |
| 연결 구분 | Front↔BE, BE↔Redis |
| 의존 대상 | Front, PostgreSQL, Redis |
| 마지막 수정 | 2026-08-01 |

## 요약

Asia/Seoul 기준으로 완료된 전날의 예측 성과를 일일 배치로 확정한다. 공통 조회가
집중되는 기본 상위 10명은 Redis에 캐시하고, 요청 페이지와 로그인 사용자의 순위는
PostgreSQL의 동일한 날짜별 읽기 모델을 기준으로 조회한다.

## 비고

홈은 `size=10`, 마이페이지는 커서 페이지 조회를 사용한다. 이 API의 `date`는 화면
조회일이 아니라 실제 집계 대상인 `rankingDate`다.

### 요청

```plain text
GET /api/v1/predictions/leaderboards/daily?date=2026-07-22&size=20&cursor={cursor}
Authorization: Bearer {accessToken}
```

<table>
<tr>
<td>파라미터</td>
<td>필수</td>
<td>설명</td>
</tr>
<tr>
<td>`date`</td>
<td>선택</td>
<td>집계 대상 날짜. 기본값은 Asia/Seoul 기준 어제이며 오늘과 미래 날짜는 조회할 수 없다.</td>
</tr>
<tr>
<td>`size`</td>
<td>선택</td>
<td>기본 10, 최대 100</td>
</tr>
<tr>
<td>`cursor`</td>
<td>선택</td>
<td>다음 페이지용 불투명 커서</td>
</tr>
</table>

### 응답

```plain text
{
  "rankingDate": "2026-07-22",
  "calculatedAt": "2026-07-23T00:05:03+09:00",
  "items": [
    {
      "rank": 1,
      "nickname": "시세예언자",
      "settledPredictionCount": 4,
      "totalStakedPoints": 2000,
      "totalPayoutPoints": 8000,
      "payoutRate": 400.0
    }
  ],
  "myRank": {
    "rank": 18,
    "nickname": "택림",
    "settledPredictionCount": 3,
    "totalStakedPoints": 1500,
    "totalPayoutPoints": 3000,
    "payoutRate": 200.0
  },
  "nextCursor": "opaque-cursor",
  "hasNext": true
}
```

`calculatedAt`은 API 요청 시각이나 Redis 저장 시각이 아니라 해당 `rankingDate`의
DB 집계가 성공한 시각이다. Redis cache miss로 기본 상위 10명 캐시를 DB 읽기
모델에서 다시 채워도 값이 바뀌지 않으며, 동일 날짜를 다시 집계했을 때만 갱신된다.
해당 날짜의 집계 행이 없으면 `calculatedAt=null`을 반환한다.

### 순위 규칙

- 집계일은 `prediction_result.created_at`을 Asia/Seoul로 변환한 날짜로 판정한다.
- 집계 범위는 `rankingDate 00:00:00+09:00` 이상, 다음 날
  `00:00:00+09:00` 미만이다.
- `prediction_result.result_status = 'REFUNDED'`인 취소·유찰 환불 건과 아직 정산
  결과가 없는 예측은 제외한다.
- `payoutRate = totalPayoutPoints ÷ totalStakedPoints × 100`
- 스테이크가 0인 사용자는 제외한다.
- 응답의 `payoutRate`는 소수점 한 자리에서 `HALF_UP`으로 반올림한다.
- 순위 비교에는 응답용 반올림 전의 정확한 배당률을 사용한다.
- 정렬 기준:
  1. `payoutRate` 내림차순
  2. 정산된 예측 건수 내림차순
  3. 완전 동률이면 내부 회원 ID 오름차순으로 순서를 고정한다.
- 공동 순위를 사용하지 않고 `ROW_NUMBER` 방식의 연속되고 유일한 순위를 부여한다.
- 총 배당 포인트는 응답 정보로 제공하지만 동점 판정 조건에는 사용하지 않는다.

### 일일 집계

- 매일 `00:05 Asia/Seoul`에 전날 하루의 결과를 한 번 집계한다.
- 원본 `prediction_result`는 회원 ID로 한 번만 그룹화하여 모든 회원의 성과와 순위를
  계산한다.
- 집계 결과는 `prediction_leaderboard`에 `rankingDate`별 읽기 모델로 보존한다.
- 정산 완료 이벤트마다 리더보드를 다시 집계하지 않는다. PRED-03 정산 완료 이벤트는
  WebSocket 알림 용도로만 유지한다.
- API 요청 중 원본 `prediction_result`를 실시간으로 다시 집계하지 않는다.
- 여러 애플리케이션 인스턴스의 중복 실행은 날짜별 DB lock으로 방지한다.
- 동일 날짜 집계는 멱등해야 하며, 재실행 시 기존 날짜 스냅샷을 하나의 DB
  트랜잭션에서 교체한다.
- DB 집계 커밋에 성공한 뒤 Redis의 기본 상위 10명 캐시를 교체한다. DB 집계가
  실패하면 기존 Redis 캐시를 덮어쓰지 않는다.
- Redis 저장 실패는 성공한 DB 집계를 롤백하지 않는다.
- 집계 대상 회원이 0명이면 해당 날짜의 `prediction_leaderboard` 행을 생성하지 않는다.
- 데이터 0건과 배치 미실행은 별도 메타데이터로 구분하지 않으며 조회 시 둘 다 빈
  리더보드로 처리한다.
- 일일 집계 이후 늦게 생성된 정산 결과는 자동 반영하지 않는다. 운영상 필요한 경우
  동일 날짜 집계를 멱등하게 재실행하고 DB 읽기 모델과 Redis 상위 10명 캐시를 함께
  교체한다.

### 조회와 커서

- 홈 티커는 `size=10`, 마이페이지는 커서 기반 전체 순위를 사용한다.
- `items`는 `cursor`와 `size`에 해당하는 페이지이며 첫 요청의 기본 페이지는 상위
  10명이다.
- `myRank`는 현재 페이지에 포함되지 않아도 별도로 반환한다.
- 로그인 사용자가 집계 대상이 아니면 `myRank=null`이다.
- `myRank`는 Redis 캐시 여부와 관계없이 `prediction_leaderboard`에서
  `rankingDate`와 로그인 회원 ID로 단건 조회한다.
- `items`와 `myRank`를 위해 원본 정산 결과를 각각 집계하지 않는다. 둘 다 배치가
  생성한 동일 날짜와 동일 `calculatedAt`의 DB 읽기 모델을 기준으로 한다.
- 데이터가 없으면 `calculatedAt=null`, `items=[]`, `myRank=null`,
  `nextCursor=null`, `hasNext=false`를 반환한다.
- 커서는 집계 날짜, 스냅샷 계산 시점과 마지막 순위 항목을 식별한다.
- 형식이 잘못되었거나 요청 날짜와 다른 커서는 `INVALID_CURSOR`로 거부한다.
- 커서 페이지는 커서의 `calculatedAt`과 같은 읽기 모델에서 마지막 `rank` 이후를
  `size + 1`건 조회해 `hasNext`와 다음 커서를 계산한다.
- 동일 날짜를 재집계해 이전 읽기 모델이 교체된 뒤 기존 커서를 사용하면 빈 페이지를
  반환할 수 있다.
- Redis TTL 만료만으로 커서를 만료시키지 않는다. DB에 같은 스냅샷 행이 남아 있으면
  기존 커서를 계속 사용할 수 있다.

### Redis 상위 순위 캐시

- Redis는 원본 데이터를 집계하거나 전체 회원 순위를 저장하지 않는다.
- Redis 캐시 대상은 `cursor`가 없고 `size=10`인 기본 첫 페이지뿐이다. 다른
  `size` 또는 커서 페이지는 `prediction_leaderboard`에서 조회한다.
- Key는 `prediction:leaderboard:daily:{rankingDate}:top10` 형식을 사용한다.
- Value에는 `rankingDate`, `calculatedAt`, 상위 10개 `items`, `nextCursor`,
  `hasNext`를 저장하며 로그인 회원별 `myRank`는 저장하지 않는다.
- TTL은 저장 시점부터 48시간이다. TTL은 최신성 기준이 아니라 캐시 메모리 정리
  정책이다.
- 기본 첫 페이지는 `rankingDate`에 해당하는 Redis Value가 있으면 cache hit로 사용한다.
- cache miss이면 `prediction_leaderboard`에서 해당 날짜의 상위 10명만 조회하고
  Redis에 48시간 동안 다시 저장한다.
- 동일 날짜 재집계가 커밋되면 상위 10명 캐시를 새 값으로 교체한다.
- cache miss 시 원본 `prediction_result`를 재집계하지 않는다.
- `myRank`는 `prediction_leaderboard`의 `(rankingDate, memberId)` 조건으로
  독립적인 단건 DB 조회를 수행한다.
- Redis 장애 시 DB 읽기 모델로 정상 응답한다.
- TTL이 만료된 과거 날짜도 DB 읽기 모델을 통해 다시 조회할 수 있다.

### 빈 날짜 처리

- 기본 조회 날짜는 항상 Asia/Seoul 기준 어제다. 더 오래된 최신 스냅샷으로 날짜를
  임의 대체하지 않는다.
- 해당 날짜의 읽기 모델이 없더라도 원본 `prediction_result`를 요청 시점에 집계하지
  않고 `calculatedAt=null`인 빈 응답을 반환한다.
- 집계 대상 0명과 배치 미실행은 API 응답에서 구분하지 않는다.

### 오류

<table>
<tr>
<td>HTTP</td>
<td>코드</td>
<td>조건</td>
</tr>
<tr>
<td>400</td>
<td>`INVALID_RANKING_DATE`</td>
<td>날짜 형식 오류 또는 Asia/Seoul 기준 오늘·미래 날짜</td>
</tr>
<tr>
<td>400</td>
<td>`INVALID_PAGE_SIZE`</td>
<td>조회 크기 범위 초과</td>
</tr>
<tr>
<td>400</td>
<td>`INVALID_CURSOR`</td>
<td>형식이 잘못되었거나 요청 날짜와 다른 커서</td>
</tr>
<tr>
<td>401</td>
<td>`UNAUTHORIZED`</td>
<td>인증 실패</td>
</tr>
</table>

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [일일 낙찰가 예측 리더보드 조회 원본](https://app.notion.com/0fc776ebfd68824a851a014f870dfe96)
