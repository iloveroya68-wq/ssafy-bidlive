# 고정 공지 등록·교체

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [고정 공지 등록·교체](https://app.notion.com/516776ebfd688271b70801cb277192b4)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `CHAT-02` |
| 저장소 도메인 | `chat` |
| Notion 도메인 | 채팅 |
| Method | `POST` |
| Path / Destination | `/api/v1/auctions/{auctionId}/chat/pin` |
| 상태 | 확정 |
| 인증 | 필요 — Access Token |
| 공개 범위 | 미지정 |
| 담당자 | 김경민 |
| 우선순위 | Medium |
| 연결 구분 | 미지정 |
| 의존 대상 | 미지정 |
| 마지막 수정 | 2026-07-27 02:08:13Z |

## 비고

기존 공지를 교체하고 WS-03 스냅샷에 포함

> **REST 명세** — 호스트가 같은 경매의 채팅 한 건을 상단 공지로 등록하거나 교체한다.
## 호출 위치
```plain text
POST /api/v1/auctions/{auctionId}/chat/pin
```
## 입력
```json
{ "messageId": "01J8W3P2N9KQ4T" }
```
## 처리 규칙
- 요청자와 경매 `hostId` 일치
- 메시지가 같은 경매에 속하고 삭제되지 않았는지 확인
- 현재 공지와 같으면 멱등 성공
- `chat_message.is_pinned`를 고정 공지 source of truth로 사용
- 경매별 PostgreSQL transaction advisory lock으로 등록·교체를 직렬화해 방당 최대 한 건을 보장
- 대상 메시지를 고정하면서 기존 고정 메시지를 같은 트랜잭션에서 해제
- 커밋 후 `NOTICE_PINNED`를 채팅 토픽에 송출
## 결과
```json
{ "messageId": "01J8W3P2N9KQ4T", "content": "택배비 포함가입니다" }
```
## 실패 코드
<table>
<tr>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>`NOT_AUCTION_HOST`</td>
<td>호스트가 아님</td>
</tr>
<tr>
<td>`MESSAGE_NOT_FOUND`</td>
<td>없거나 다른 경매의 메시지</td>
</tr>
</table>
## 필수 테스트
- 최초 등록·다른 메시지로 교체
- 같은 메시지 중복 등록 멱등성
- 비호스트와 타 경매 메시지 차단
- WS-03 pinnedNotice 반영

## 구현 메모 (2026-08-02, S15P11A704-613)

- **`messageId`는 26자 정규 ULID만 통과한다.** `PinnedNoticeController.parseMessageId`가
  `ChatMessageId`로 감싸고, 그 record가 `[0-7][0-9A-HJKMNP-TV-Z]{25}`(총 26자, Crockford Base32)로
  검증한다. 어긋나면 400 `INVALID_MESSAGE_ID`다 — **현실의 숫자 id는 여기서 막힌다.** 패턴 자체는
  숫자를 배제하지 않아 `0`~`7`로 시작하는 26자리 숫자열은 통과하지만, 이전 구현이 쓰던 `Long` id는
  전부 26자 미만이라 길이에서 걸린다. 그 이전 구현은 `Long.parseLong`이라 반대로 ULID가
  막혔다(`613`에서 뒤집었다).
- ⚠️ **위 「입력」·「결과」 블록의 `01J8W3P2N9KQ4T`는 14자라 그대로 보내면 400이다.** 명세를 쓸 때
  줄인 생략 표기이지 계약값이 아니다 — 실제 값은 서버가 발급한 26자 ULID
  (`01ARZ3NDEKTSV4RRFFQ69G5FAV` 꼴)다.
- 고정 공지 조회 정렬은 `ORDER BY created_at DESC, message_id DESC`다. `613`이 바꾼 것은
  tie-breaker를 **내부 숫자 PK에서 외부 식별자로 옮긴 것**이다 — `chat_message_id`는 V1부터
  실재하는 BIGINT PK(`V1__init.sql:374`)이고, `message_id`는 V19가 새로 추가한 ULID 외부 키
  (`VARCHAR(26)`, `V19__add_chat_message_external_ids.sql`)다. ULID는 사전순이 생성 순서와
  일치하므로 tie-breaker로서의 의미는 그대로다.
- 대신 새 정렬은 V1이 만든 인덱스 `(auction_room_id, created_at DESC, chat_message_id DESC)`
  (`V1__init.sql:410`)의 정렬을 더 이상 타지 못한다 — tie-breaker 컬럼이 다르다. 다만 고정 공지
  조회는 `LIMIT 1`이고 `is_pinned` 행이 방당 최대 한 건이라 실비용은 무시할 수 있다.
- 「경매별 advisory lock」은 `pg_advisory_xact_lock(hashtextextended('bidlive:chat:{auctionId}:pinned-notice', 0))`으로
  구현돼 있다. 멱등 경로(같은 메시지 재등록)는 lock 획득 후 `findPinned` 비교로 판정하고
  `NOTICE_PINNED`를 다시 쏘지 않는다.
- ⚠️ **「필수 테스트」의 `WS-03 pinnedNotice 반영`은 아직 못 지킨다.** `PinnedNoticeQueryService`를
  부르는 운영 코드가 0건이고 `realtime-state` 매핑 자체가 백엔드에 없다. 배선하는 시점에
  **ULID 문자열이 그대로 스냅샷에 실린다** — `JpaPinnedNoticePersistenceAdapter`가 `message_id`를
  변환 없이 담는다(`613` 이전엔 `String.valueOf(long)`이었다).
- 프론트(`shared/api/chat.ts`, `559`)는 200 응답 본문을 버린다. 화면 갱신은 커밋 후 채팅 토픽으로
  나가는 `NOTICE_PINNED` 에코 한 경로로만 한다 — 응답과 소켓 둘 다 쓰면 같은 상태에 소스가 둘이 된다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [고정 공지 등록·교체 원본](https://app.notion.com/516776ebfd688271b70801cb277192b4)

