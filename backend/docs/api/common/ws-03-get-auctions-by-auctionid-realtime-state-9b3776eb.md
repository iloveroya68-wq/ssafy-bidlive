# 경매 상태 스냅샷

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [경매 상태 스냅샷](https://app.notion.com/9b3776ebfd68824b839901c92d48ccda)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `WS-03` |
| 저장소 도메인 | `common` |
| Notion 도메인 | 공통 |
| Method | `GET` |
| Path / Destination | `/api/v1/auctions/{auctionId}/realtime-state` |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | 미지정 |
| 담당자 | 김경민 |
| 우선순위 | High |
| 연결 구분 | 미지정 |
| 의존 대상 | `S15P11A704-253`, `S15P11A704-500`, `S15P11A704-503` |
| 마지막 수정 | 2026-07-30 |

## 비고

서버 권위 스냅샷과 Redis `stateVersion`으로 재접속 중복·역전 방지

> **REST 스냅샷 명세** — 재접속·최초 입장 시 서버 권위 상태와 경매별 단조 증가 `stateVersion`을 반환한다.

## 호출 위치

```plain text
GET /api/v1/auctions/{auctionId}/realtime-state
```

## 처리 규칙

- `stateVersion`의 source of truth는 경매별 Redis 실시간 상태 Hash의 `version` 필드
- `auctionStatus`, `endAt`, `extendCount`, `bidCount`, `bidParticipantCount`, `stateVersion`은 동일 Redis 상태 스냅샷을 기준으로 조회
- Redis Hash의 버전은 입찰·연장·종료 원자 처리에서만 증가하며 PostgreSQL `auction_room.state_version`은 장애 복구용 워터마크로 사용
- `remainingSeconds`는 Redis `endAt`과 응답 시점의 서버 시각으로 계산
- `myBidAmount`, `myParticipationType`은 BE3 입찰·예측 참여 조회 포트에 위임
- `viewerCount`는 LIVE-03에서 만료 세션 정리 후 `ZCARD`
- `pinnedNotice`는 현재 고정 메시지 조회
- `amIMuted`는 Redis mute TTL 확인
- `myBidAmount`는 본인 값만 포함하며 미입찰이면 `null`
- `bidParticipantCount`는 입찰을 한 고유 회원 수이며 다중 입찰·상향 입찰은 한 명으로 집계
- `myParticipationType`은 `NONE | BID | PREDICTION` 중 하나이며, 입찰·예측 베팅 상호 배타 상태를 재접속 후 복원하는 데 사용
- 실시간 예측 분포와 타인 입찰 금액은 포함하지 않음
- Redis 상태 Hash가 없거나 필수 필드가 불완전하면 `version=0`인 빈 상태를 임의 생성하지 않고 `AUCTION_STATE_UNAVAILABLE`로 실패
- Redis 상태를 재구성할 때는 PostgreSQL 워터마크를 `version` 초기값으로 사용하며, 입찰·참여 상태까지 안전하게 복구할 수 있을 때만 Hash를 생성

## 결과 예시

```json
{
  "stateVersion": 184,
  "auctionStatus": "LIVE",
  "endAt": "2026-07-20T21:15:00+09:00",
  "remainingSeconds": 431,
  "extendCount": 1,
  "remainingExtensions": 2,
  "bidCount": 7,
  "bidParticipantCount": 5,
  "viewerCount": 23,
  "myBidAmount": 52000,
  "myPredictedPrice": null,
  "myStakeAmount": null,
  "myParticipationType": "BID",
  "pinnedNotice": { "messageId": "01J8...", "content": "택배비 포함가입니다" },
  "amIMuted": false,
  "mutedUntil": null
}
```

## 재접속 연장·뮤트 복원 계약

- `remainingExtensions`는 경매 연장 정책의 최대 횟수와 현재 `extendCount`를 기준으로 서버가 계산한 0 이상의 값이다.
- `mutedUntil`은 현재 회원의 채팅 제한 만료 시각이며 ISO 8601 형식으로 반환한다.
- `amIMuted=false`이면 `mutedUntil=null`이다.
- `amIMuted=true`이면 Redis mute TTL로 복원한 `mutedUntil`을 반드시 반환한다.
- 클라이언트는 두 값을 추정하지 않고 스냅샷 응답을 그대로 사용한다.

## 재접속 순서

1. STOMP SUBSCRIBE에 `receipt`를 넣고 RECEIPT 수신 전 이벤트를 버퍼링한다.
2. 스냅샷을 조회한다.
3. 버전 대상 이벤트 중 `event.data.stateVersion <= snapshot.stateVersion`인 이벤트는 폐기한다.
4. 더 큰 버전만 오름차순 적용하고 이후 실시간 이벤트를 처리한다.
5. 버전 번호의 공백은 Redis 성공 후 DB 실패 등으로 발생할 수 있으므로 누락으로 판단하지 않는다.
6. 이미 적용한 버전 이하의 중복·역전 이벤트는 폐기한다.
7. 버전 비대상 이벤트는 `stateVersion` 비교에 섞지 않고 각 이벤트의 ID 또는 최신 값 덮어쓰기 규칙으로 처리한다.

> 버전 대상 WebSocket 이벤트는 공통 `{type, data}` envelope을 유지하고 `data.stateVersion`에 경매별 버전을 포함한다. 버전 증가 대상과 장애 정책은 `S15P11A704-253` ADR을 따른다.

## 실패 코드

<table>
<tr>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>`AUCTION_NOT_FOUND`</td>
<td>경매 없음</td>
</tr>
<tr>
<td>`AUCTION_STATE_UNAVAILABLE`</td>
<td>Redis 실시간 상태 Hash 누락·필수 필드 불완전 또는 필수 상태 저장소 조회 실패</td>
</tr>
</table>

## 필수 테스트

- 스냅샷과 구독 사이 이벤트 누락 방지
- 스냅샷과 버전 대상 이벤트가 동일 Redis Hash의 `version`을 사용
- 스냅샷 이하 중복·역전 버전 폐기
- 버전 번호가 건너뛰어도 더 큰 버전을 오름차순으로 적용
- 버전 비대상 이벤트가 경매 버전 비교에 포함되지 않음
- Redis 상태 Hash 누락 또는 필수 필드 불완전 시 `AUCTION_STATE_UNAVAILABLE`
- Redis 상태 재구성 시 PostgreSQL 워터마크보다 작은 버전을 만들지 않음
- 미입찰 `myBidAmount=null`
- 입찰·예측 미참여 `myParticipationType=NONE`
- 입찰 참여자 수의 고유 회원 집계와 상향 입찰 중복 제거
- 타인 입찰가와 `prediction.buckets` 미노출
- 만료된 접속 세션 제외

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [경매 상태 스냅샷 원본](https://app.notion.com/9b3776ebfd68824b839901c92d48ccda)

