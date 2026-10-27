# 운영 강제 종료

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [운영 강제 종료](https://app.notion.com/0c1776ebfd6882578c4a01f5efe90445)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `DEAL-09` |
| 저장소 도메인 | `auction` |
| Notion 도메인 | 경매 |
| Method | `POST` |
| Path / Destination | `/api/v1/admin/auctions/{auctionId}/force-end` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | Admin |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `AdminAuctionForceEndController` (S15P11A704-522). 아래 「구현 메모」 참조 (`2026-07-31` 확인) |
| 우선순위 | Medium |
| 연결 구분 | Front↔BE, BE↔BE, BE↔Redis, BE 내부 |
| 의존 대상 | 경매, 포인트, Redis, PostgreSQL |
| 마지막 수정 | 2026-07-27 02:08:13Z |

## 요약

운영자가 대기방 또는 라이브 경매를 강제 종료하고 입력 차단·홀드 해제·예측 포인트 환급을 처리한다.

```plain text
POST /api/v1/admin/auctions/{auctionId}/force-end
Authorization: Bearer {adminAccessToken}
```
<table>
<tr>
<td>속성</td>
<td>권장 내용</td>
</tr>
<tr>
<td>도메인</td>
<td>경매</td>
</tr>
<tr>
<td>권한 범위</td>
<td>Admin</td>
</tr>
<tr>
<td>연결 구분</td>
<td>Front↔BE, BE↔BE, BE↔Redis, BE 내부</td>
</tr>
<tr>
<td>우선순위</td>
<td>Medium</td>
</tr>
<tr>
<td>상태</td>
<td>검토 중</td>
</tr>
</table>
요청 예시:
```plain text
{
  "reason": "POLICY_VIOLATION",
  "adminNote": "금지 품목 신고 확인"
}
```
성공 응답 예시:
```plain text
{
  "auctionId": 100,
  "status": "ENDED",
  "resultType": "FORCED",
  "endedAt": "2026-07-22T15:00:00+09:00"
}
```
처리 규칙:
- `WAITING`, `LIVE` 상태의 경매만 강제 종료할 수 있다.
- 입찰·예측·채팅 입력을 차단한다.
- 기존 입찰 보류금과 예측 포인트를 환급한다.
- 방송 및 경매 타이머를 종료한다.
- 일반 낙찰 처리와 강제 종료가 동시에 진행되지 않게 한다.
- 동일 요청이 반복돼도 중복 환급하지 않는다.
---
- `auction_room.status`: 최종적으로 `ENDED`
- `auction_result.result_status`: `CANCELLED`
- `auction_result.termination_reason`: `FORCED`

## 구현 메모

**「입력 차단」에 별도 플래그를 두지 않았다. 다만 `ENDED` 전이만으로는 차단되지 않는다.**
입찰·예측 접수는 Postgres를 보지 않고 Redis 실시간 상태 Hash(`bid:{bid}:auction:{id}:state`)의
`status`가 `LIVE`인지만 본다(`bid-process.lua`·`prediction-participation-process.lua`). 그래서
`AUCTION_CANCELED`를 받은 `RedisAuctionStateCloser`가 그 `status`를 `ENDED`로 바꿔야 실제로 막힌다
— ⚠️ **2026-07-31 정정(S15P11A704-537).** 원래 여기에 「입찰·예측·채팅은 방이 `LIVE`일 때만 받으므로
`ENDED` 전이가 곧 차단이다」라고 적혀 있었으나 **사실이 아니었다.** Redis가 `LIVE`로 남아 원래
`endAt`까지 입찰이 계속 접수됐고, 그 입찰이 새로 잡은 홀드는 홀드 해제가 이미 끝난 뒤라 아무도
풀지 않아 **회원 잔액이 묶였다.** 플래그를 더하지 않는다는 판단 자체는 유지한다 — 차단의 근거는
여전히 상태 하나이고, 다만 그 상태가 Postgres와 Redis 두 곳에 있어 둘 다 닫아야 한다.

- 값은 `SETTLING`이 아니라 `ENDED`다. BID-07 비크리 엔진이 `SETTLING`을 「정산 선점」으로 `LIVE`에서만
  전환해 쓰고, 「이미 `SETTLING` 또는 `ENDED`면 중복 차감하지 않는다」로 정해 뒀다. 강제 종료는
  정산이 아니라 취소이고 환급도 이미 끝냈으므로 종착 값을 쓴다
- `endAt`은 안 건드린다. 두 스크립트가 `status`를 먼저 보므로 당겨도 도달하지 않는 죽은 수정이고,
  `endAt`은 **예정** 마감이라 실시간 화면·집계가 그대로 읽어야 한다
- 차단 리스너는 **`BEFORE_COMMIT`**이다. Redis 쓰기가 실패하면 트랜잭션이 통째로 롤백되고 운영자에게
  500이 간다 — 상태 전이·홀드 해제·예측 환급이 다 되돌아간다(둘 다 `REQUIRED`라 같은 트랜잭션).
  남는 상태는 「DB는 아직 `LIVE`, Redis만 `ENDED`」로 **안전한 방향**이고, 방이 아직 종료 가능해
  **force-end를 그대로 다시 부르면 복구된다**(409가 아니다)
  - ⚠️ **`AFTER_COMMIT`이면 안 된다(2026-07-31 재정정, S15P11A704-537).** 이 자리에 한때
    「`AFTER_COMMIT`이라 DB만 `ENDED`로 남고 브로드캐스트도 안 나가며 500이 간다」고 적었으나
    **셋 다 틀렸다.** 스프링 6.2.19는 `AFTER_COMMIT`을 `afterCompletion`에서 처리하고
    `TransactionSynchronizationUtils.invokeAfterCompletion`이 동기화마다 Throwable을 잡아 로그만
    남긴다 — 예외가 삼켜져 **운영자는 200을 받고, 브로드캐스트는 그대로 나가고**, Redis만 `LIVE`로
    남아 이 버그가 **조용히** 재현된다. `BEFORE_COMMIT`은 `triggerBeforeCommit`이 감싸지 않아
    `processCommit`이 롤백 후 다시 던진다
  - 시작(ROOM-05)의 초기화가 `AFTER_COMMIT`인 것과 비대칭인데 근거가 있다: **시작은 「안 열리는 쪽」이,
    종료는 「닫히는 쪽」이 안전하다.** 시작을 `BEFORE_COMMIT`으로 하면 커밋 실패 시 시작되지도 않은
    방이 Redis에서 `LIVE`가 돼 입찰과 홀드가 쌓인다
  - 페이즈가 다르므로 브로드캐스트(`AFTER_COMMIT`)와의 순서는 스프링이 보장한다. `@Order`는 쓰지
    않는다 — 애초에 **클래스에 붙인 `@Order`는 이벤트 리스너에 안 먹는다**
    (`ApplicationListenerMethodAdapter.resolveOrder(Method)`가 메서드만 읽는다. 같은 결함이
    `AUCTION_STARTED` 쌍에도 있어 메서드로 옮겼다)

**⚠️ 「채팅 입력을 차단한다」는 아직 미이행이다.** 채팅 전송(CHAT-01 `/app/auctions/{id}/chat`)에
해당하는 `@MessageMapping` 핸들러가 레포에 없다 — 차단할 입력 경로 자체가 아직 없어서 게이트를
붙일 자리가 없다. CHAT-01을 구현할 때 같은 Redis `status` 게이트를 지나게 해야 한다.

**멱등성은 「두 번째 호출이 아무것도 안 한다」로 만들었다.** 상태 전이가
`status IN ('WAITING','LIVE')` 조건부 UPDATE라 재실행·동시 요청이면 0행이 되고 곧바로 409로 끊겨
**환급 단계에 도달하지 못한다.** 방송 시작(ROOM-05)이 쓰는 방식과 같다.

**`ended_at`을 세운다. `ends_at`이 아니다.** 둘은 다른 컬럼이다 — `ends_at`은 **예정** 마감이고
`ended_at`이 **실제** 종료 시각이다. 바꿔 쓰면 참여·주최·낙찰 이력 어댑터의
`COALESCE(ar.ended_at, '9999-12-31')` 정렬에서 센티넬 자리로 올라가 목록 맨 위에 영구 고정되고,
커서 페이징도 함께 어긋난다. 실제로 한 번 밟았다(MR !246 리뷰).

**⚠️ `ADMIN` 계정이 아직 하나도 없다.** 카카오 가입은 전부 기본값 `USER`로 들어가고 role을
올리는 코드가 없다. 그래서 **실서버에서 호출해 본 적이 없고**, 권한 게이트는
`AdminAuctionForceEndSecurityIntegrationTest`(ADMIN 200 / USER 403 / 비로그인 401)로만 담보된다.
확보 방법은 별도 티켓(`525`)에서 정한다.

**응답의 `resultType`은 `FORCED` 고정이다.** 요청 본문의 `adminNote`는 받되 쓰지 않는다 —
명세 예시에만 있고 처리 규칙이 없다. 감사 로그 자리가 정해지면 그때 소비한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [운영 강제 종료 원본](https://app.notion.com/0c1776ebfd6882578c4a01f5efe90445)

