# API Specification

이 디렉터리는 [Notion 최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)의 API 명세 테이블과 WebSocket
이벤트 명세를 현재 백엔드 도메인 경계에 맞춰 동기화한 로컬 구현 계약이다.

- 동기화 기준일: 2026-07-27
- 명세 문서: **98건**
  - Notion 유래 93건 — API·내부·WS 계약 81 + 별도 WebSocket 이벤트 계약 12
  - 로컬 추가 5건 — Notion에 대응 문서가 없다. 파일명에 해시 접미사가 없는 것으로 구분한다
    (`common/websocket-message-format.md`, `media/events/live-ended.md`,
    `member/mem-03-*` 2건, `report/admin-01-get-admin-reports.md`)

## 사용 원칙

- 에이전트는 작업 scope에 포함된 `docs/api/{domain}/**` 문서만 구현·수정한다.
- `확정` 상태의 계약을 구현 기준으로 사용한다.
- `작성 중`, `검토 중`, `예정`, 상태 미지정 항목은 확정 계약으로 간주하지 않는다.
- ⚠️ **`상태`와 `구현`은 다른 축이다.** `상태: 확정`은 계약이 확정됐다는 뜻이고, 실제로 서버에
  붙었는지는 메타데이터 표의 **`구현` 행**이 말한다 — 아래 「구현 현황」 참조.
- 코드와 로컬 명세를 같은 변경에서 함께 갱신한다.
- Notion 원본을 변경했다면 관련 로컬 문서도 동기화한다.
- Notion과 로컬 문서가 다르면 구현을 추측하지 않고 차이와 영향을 먼저 보고한다.

## Notion 도메인 재분류

| Notion 분류 | 저장소 분류 |
| --- | --- |
| 회원 | 인증·OAuth는 `auth`, 프로필·약관·마이페이지는 `member` |
| 경매 | 경매방·결과·강제 종료는 `auction`, 거래는 `deal`, 신고는 `report`, 마이페이지 조회는 `member` |
| 방송 | OpenVidu·방송 세션은 `media`, 입찰 피드는 `bid`, 타이머·연장은 `auction` |
| AI | 상품 분석·검열은 `product`, 중간 입장 요약은 `auction`, 채팅 문맥 검열은 `chat` |
| 포인트 | `wallet` |
| 입찰·예측·채팅·공통 | `bid`, `prediction`, `chat`, `common` |

## 구현 현황 — 명세 98건 전수

> **`상태: 확정`은 "명세가 확정됐다"는 뜻이지 "구현됐다"는 뜻이 아니다.** 그래서 이 표는
> **`구현`(백엔드)과 `연동`(프론트) 두 축**을 따로 적는다. 한 축만 보면 「서버에 붙었는데 화면이
> 안 쓴다」와 「화면이 부르는데 서버에 도달할 데가 없다」가 둘 다 안 보인다 — 지금
> 각각 2행·4행이다.
>
> 마지막 **전수** 대조: **2026-07-31** (`dev` `753a17d` 기준). 근거는 추측이 아니라 백엔드
> `@RestController`·`@MessageMapping` 매핑 전수와 프론트 호출부 전수 대조다.
>
> `BID-05`·`TIME_SYNC`과 폐기 이벤트 2행은 **2026-08-01**
> (`feat/S15P11A704-128`)에 관련 백엔드·프론트 코드를 다시 대조해 갱신했다.
>
> **2026-08-02 부분 재대조** (`dev` `0f8e039`, `S15P11A704-562`) — 아래 행만 코드로 다시 확인해
> 갱신했다. **나머지 행은 7/31 판정 그대로다 — 이미 낡았을 수 있다.**
> `ROOM-08` · `BID-03`·`AUCTION_EXTENDED` · `BID-05`·`TIME_SYNC` · `PRED-05` · `PRED-04` ·
> `CHAT-01` 2행 · `CHAT-02` 2행 · `CHAT-03` · `CHAT-04` ·
> `BID-07`·`AUCTION_ENDED`·`AUCTION_SETTLED`·`AUCTION_NO_SALE` · `LIVE_ENDED` ·
> `CHAT-06` WS `/app`(근거만).
> `BID-07`과 WS 3행은 `dev`에 머지된 `feat/vickrey_engine_call`(`9c17ee7`)이 마감 예약 트리거를
> 넣어 넷 다 뒤집혔고, `PRED-04`·`AI-04`는 !300·!297 머지로 연동이 닫혔다.
> `AUCTION_EXTENDED`·`CHAT-03`·`CHAT-04`는 지목받은 행은 아니지만 근거가 통째로 `BID-03`·`CHAT-01`에
> 매달려 있어 같이 뒤집혔다 — 안 고치면 표 안에서 앞뒤가 어긋난다.
>
> ⚠️ **이 표는 며칠이 아니라 몇 시간이면 낡는다.** 이 표를 만드는 두 시간 사이에 `dev`가 4커밋
> 나갔고 그중 `115`가 `AUCTION_EXTENDED` 행을 뒤집었다. 고칠 때 기억에 의존하지 말고 매핑을
> 다시 훑는다. 실제로 낡았던 적이 있다 — 2026-07-30 대조 뒤 개별 명세는 갱신됐는데 이 요약표만
> `DEAL-01`·`DEAL-06/08`을 ❌로 달고 있었다(`520`).
>
> ⚠️ **Jira 상태가 아니라 `dev`의 코드를 봤다.** `245`(AI-05 검열 경고·뮤트 이벤트)는
> **완료로 표시돼 있는데 `dev`에 코드가 없다** — `MODERATION_WARNING`·`CHAT_MUTED` 발행이 0건이다
> (`ChatSendErrorCode.CHAT_MUTED`는 SEND 실패 코드라 다른 것이다). 보드만 보면 끝난 일로 읽히는
> 자리다. 반면 `539`(CHAT-01 STOMP SEND 진입점)는 7/31엔 없었으나 지금은 `StompChatSendController`로
> 들어와 있다 — 어긋남은 방향이 둘 다 있다.

### 표기

| 기호 | `구현` (백엔드) | `연동` (프론트) |
| --- | --- | --- |
| ✅ | 진입점이 있고 호출 가능하다 | 화면이 실 API를 부른다 |
| 🟡 | 로직 또는 일부 트리거가 있으나 **계약 전체가 연결되지 않았다** | 계약 경로에 **목 분기**가 남아 있다 |
| ❌ | 없다 | 부르는 코드가 없다 |
| 🚫 | 폐기된 계약 | — |
| — | | 연동 대상이 아니다 (INTERNAL·서버 내부 처리) |

**🟡(구현)은 「미구현」이 아니라 「부분 구현 또는 연결 누락」이다.** 티켓 성격이 달라 구분한다.

### 집계

| | ✅ | 🟡 | ❌ | 🚫 | — |
| --- | ---: | ---: | ---: | ---: | ---: |
| **구현** | 79 | 4 | 12 | 3 | |
| **연동** | 65 | 7 | 10 | | 16 |

연동 `—` 16건(INTERNAL 13 + 폐기 3)을 빼면 **프론트가 붙여야 할 82행 중 65행이 붙었다.**

**세는 단위는 「행」이지 「기능」이 아니다.** 한 기능이 여러 행을 갖는다 — `CHAT-02`는 등록·해제
2행, `CHAT-01`은 송신·수신 2행이다. 기능 단위로 세면 같은 표에서 다른 숫자가 나온다.

---

### `auction` — 25건

[도메인 명세 목록](./auction/README.md)

| 구현 | 연동 | 기능 ID | Method | Path / Destination | 근거 |
| --- | --- | --- | --- | --- | --- |
| ✅ | ✅ | `AI-03` | `GET` | [/api/v1/auction-rooms/{auctionId}/catch-up-summary](./auction/ai-03-get-auction-rooms-by-auctionid-catch-up-summary-2ae776eb.md) | RoomEvent 폴백 + Redis 캐시 + 선택적 GMS STT·요약 (`S15P11A704-700`) |
| ✅ | ✅ | `BID-03` | `POST` | [/api/v1/auctions/{auctionId}/extensions](./auction/bid-03-post-auctions-by-auctionid-extensions-408776eb.md) | `AuctionExtensionController`의 `@PostMapping("/{auctionId}/extensions")` → `AuctionExtensionService`·`RedisAuctionExtensionProcessor`·`extend-auction.lua`·JPA 어댑터·WS 발행(`115`) / `bids.ts:119` `extendAuction` ← `ConsoleView.vue:167`, 목 분기 없음 (`548`) |
| ✅ | ✅ | `BID-05` | `WS` | [/topic/auctions/{auctionId}](./auction/bid-05-ws-topic-auctions-by-auctionid-4ef776eb.md) | `TIME_SYNC` 발생 조건 네 갈래가 모두 실재한다 — 구독 직후 `AuctionTimeSyncSubscriptionListener`, 30초 주기 `AuctionTimeSyncScheduler`(구독자 있는 LIVE 경매만), 연장 직후 `StompAuctionExtendedEventPublisher`, 시작 직후 `StompAuctionStartedEventPublisher:68` / `handlers.ts:55` 수신 구현 |
| ✅ | — | `BID-07` | `INTERNAL` | [내부 처리 (비크리 엔진)](./auction/bid-07-internal-internal-c29776eb.md) | 명세의 최종가·순위 규칙을 `VickreySettlementPolicy`가 그대로 구현하고(회원별 최종 입찰, 2등가, `priceDecisionType` 3종), 결과는 `JpaAuctionSettlementAdapter:128`이 `second_highest_bid_amount`까지 `auction_result`에 적재한다(강제 종료 경로는 `JpaAuctionForceEndAdapter:54`). 명세가 말하는 「경매 마감 스케줄러」 진입점도 `feat/vickrey_engine_call`로 실재하게 됐다 — `AuctionSettlementDeadlineScheduler:120` (`89`). 남은 건 `BID-10` 집계 진행 이벤트(`145`)뿐이다 |
| ✅ | 🟡 | `DEAL-06, DEAL-08` | `GET` | [/api/v1/auction-rooms/{auctionId}/result](./auction/deal-06-deal-08-get-auction-rooms-by-auctionid-result-800776eb.md) | `AuctionResultController` / `resultSource.ts:20` 목 — **의도된 데모 경로다**(`results.ts`에 폴백이 없어 지우면 목 모드가 실 서버를 때린다) |
| ✅ | ✅ | `DEAL-09` | `POST` | [/api/v1/admin/auctions/{auctionId}/force-end](./auction/deal-09-post-admin-auctions-by-auctionid-force-end-0c1776eb.md) | `AdminAuctionForceEndController` / `useAdminForceEnd.ts:112`. ⚠️ `ADMIN` 계정이 아직 없다 |
| ✅ | ✅ | `ROOM-01` | `POST` | [/api/v1/auction-rooms](./auction/room-01-post-auction-rooms-177776eb.md) | `AuctionRoomCreationController` / `stores/room.ts:336` |
| ✅ | ✅ | `ROOM-02` | `GET` | [/api/v1/auction-rooms](./auction/room-02-get-auction-rooms-98a776eb.md) | `AuctionRoomListController` / `stores/room.ts:257`, `AdminView.vue:110` |
| ✅ | ✅ | `ROOM-02` | `GET` | [/api/v1/auctions/categories](./auction/room-02-get-auctions-categories-a23776eb.md) | `AuctionCategoryController` / `stores/category.ts:61` |
| ✅ | ✅ | `ROOM-03` | `GET` | [/api/v1/auction-rooms/{auctionId}](./auction/room-03-get-auction-rooms-by-auctionid-323776eb.md) | `AuctionRoomDetailController` / `ConsoleView`·`WaitingRoomView`·`RoomEditView` |
| 🚫 | — | `—` | `—` | [(문서)](./auction/room-03-get-auctions-categoryid-1-status-live-size-20-cursor-by-cursor-293776eb.md) | 폐기 (2026-07-30) — ROOM-02가 완전히 포함한다. 새 엔드포인트를 만들지 않는다 |
| ✅ | ✅ | `ROOM-04` | `DELETE` | [/api/v1/auction-rooms/{auctionId}](./auction/room-04-delete-auction-rooms-by-auctionid-53b776eb.md) | `AuctionRoomDeleteController` / `useRoomDelete.ts:50` |
| ✅ | ✅ | `ROOM-04` | `PATCH` | [/api/v1/auction-rooms/{auctionId}](./auction/room-04-patch-auction-rooms-by-auctionid-a9b776eb.md) | `AuctionRoomUpdateController` / `RoomEditView.vue:287` |
| ✅ | ✅ | `ROOM-05, LIVE-02` | `POST` | [/api/v1/auction-rooms/{auctionId}/start](./auction/room-05-live-02-post-auction-rooms-by-auctionid-start-4bf776eb.md) | `AuctionRoomStartController` / `ConsoleView.vue:146` |
| ✅ | ✅ | `ROOM-07` | `POST` | [/api/v1/auction-rooms/{auctionId}/join](./auction/room-07-post-auction-rooms-by-auctionid-join-9e0776eb.md) | `AuctionRoomJoinController` / `WaitingRoomView.vue:222` |
| ✅ | ✅ | `ROOM-08` | `GET` | [/api/v1/auction-rooms/upcoming](./auction/room-08-get-auction-rooms-upcoming-d7d776eb.md) | `UpcomingAuctionController` / `stores/room.ts:326` `loadUpcoming` ← `RoomListView.vue:209` (`542`) |
| ✅ | ✅ | `AUCTION_CANCELED` | `WS` | [/topic/auctions/{auctionId}](./auction/events/auction-canceled.md) | `StompAuctionCanceledEventPublisher:41` / `handlers.ts:123` |
| ✅ | ✅ | `AUCTION_ENDED` | `WS` | [/topic/auctions/{auctionId}](./auction/events/auction-ended.md) | 발행 코드 실재 — `StompAuctionSettlementNotificationPublisher:27` ← `AuctionSettlementService:78`. **트리거도 실재한다**(`89`) — `AuctionRoomStartService:86`·`AuctionExtensionService:78`가 낸 시작·연장 이벤트를 `AuctionSettlementDeadlineEventListener:27`·`:33`이 커밋 후 받아 마감 시각을 예약하고, `AuctionSettlementDeadlineScheduler:120`이 그 시각에 `settle()`을 부른다(재기동 복구 `AuctionSettlementDeadlineRecovery:31`) / `handlers.ts:85` |
| ✅ | ✅ | `AUCTION_EXTENDED` | `WS` | [/topic/auctions/{auctionId}](./auction/events/auction-extended.md) | `StompAuctionExtendedEventPublisher:57`이 발행하고(`115`), 트리거 `AuctionExtensionService`도 `AuctionExtensionController`로 도달 가능해졌다 — 위 BID-03과 한 몸이다 / `handlers.ts:68` |
| ✅ | ✅ | `AUCTION_NO_SALE` | `WS` | [/topic/auctions/{auctionId}](./auction/events/auction-no-sale.md) | 발행 코드 실재 — 같은 퍼블리셔 `:55` ← `AuctionSettlementService:145`. 트리거는 위 `AUCTION_ENDED`와 같은 마감 스케줄러다 / `handlers.ts:95` |
| 🚫 | — | `AUCTION_PAUSED` | `WS` | [/topic/auctions/{auctionId}](./auction/events/auction-paused.md) | 폐기 (2026-08-01) — 공개 일시정지 상태를 사용하지 않는다 |
| 🚫 | — | `AUCTION_RESUMED` | `WS` | [/topic/auctions/{auctionId}](./auction/events/auction-resumed.md) | 폐기 (2026-08-01) — 재개 이벤트 없이 기존 `endAt`을 유지한다 |
| ✅ | ✅ | `AUCTION_SETTLED` | `WS` | [/topic/auctions/{auctionId}](./auction/events/auction-settled.md) | 발행 코드 실재 — 같은 퍼블리셔 `:38` ← `AuctionSettlementService:141`. 트리거는 위 `AUCTION_ENDED`와 같은 마감 스케줄러다 / `handlers.ts:90` |
| ✅ | ✅ | `AUCTION_STARTED` | `WS` | [/topic/auctions/{auctionId}](./auction/events/auction-started.md) | `StompAuctionStartedEventPublisher:52` / `handlers.ts:111` |
| ✅ | ✅ | `TIME_SYNC` | `WS` | [/topic/auctions/{auctionId}](./auction/events/time-sync.md) | 위 `BID-05`와 같은 행이다 — 구독 직후(`AuctionTimeSyncSubscriptionListener`)·30초 주기(`AuctionTimeSyncScheduler`)·연장 직후·시작 직후(`StompAuctionStartedEventPublisher:68`) 넷 다 발행한다. 재연결도 재구독이라 구독 직후 경로를 탄다 / `handlers.ts:55` |

### `bid` — 8건

[도메인 명세 목록](./bid/README.md)

| 구현 | 연동 | 기능 ID | Method | Path / Destination | 근거 |
| --- | --- | --- | --- | --- | --- |
| ✅ | — | `BID-01` | `INTERNAL` | [내부 처리 (원자적 입찰)](./bid/bid-01-internal-internal-0d0776eb.md) | `RedisBidProcessor` + `redis/lua/bid-process.lua`, `BidCommandService`가 호출 |
| ✅ | ✅ | `BID-02` | `WS` | [/topic/auctions/{auctionId}](./bid/bid-02-ws-topic-auctions-by-auctionid-0f1776eb.md) | `BidAcceptedWebSocketPublisher:34` / `handlers.ts:115` |
| ✅ | ✅ | `BID-04` | `WS` | [/topic/auctions/{auctionId} (Vibe 피드)](./bid/bid-04-ws-topic-auctions-by-auctionid-8a9776eb.md) | 같은 퍼블리셔의 `VIBE_MESSAGE` — 명세 예시와 같은 고정 문구다 / `handlers.ts:79` → `VibeOverlay.vue` |
| ✅ | ✅ | `BID-06` | `POST` | [/api/v1/auctions/{auctionId}/bids](./bid/bid-06-post-auctions-by-auctionid-bids-c39776eb.md) | `BidController` / `BidDock.vue:141` |
| ❌ | ❌ | `BID-08` | `GET` | [/api/v1/auctions/{auctionId}/bid-flow](./bid/bid-08-get-auctions-by-auctionid-bid-flow-f6e776eb.md) | `BidFlowResponse` DTO만 있고 매핑 0건 (`85`) / `bids.ts:134`에 정의·목 분기만 있고 호출부 0건 |
| ❌ | ❌ | `BID-10` | `WS` | [/topic/auctions/{auctionId} (집계 중)](./bid/bid-10-ws-topic-auctions-by-auctionid-09f776eb.md) | `SETTLEMENT_PROGRESS` 문자열 0건 (`86`) / 프론트 타입·핸들러 모두 없음 |
| ✅ | ✅ | `BID_ACCEPTED` | `WS` | [/topic/auctions/{auctionId}](./bid/events/bid-accepted.md) | `BidAcceptedWebSocketPublisher:34` (`AFTER_COMMIT`) / `handlers.ts:115` |
| ❌ | ❌ | `SETTLEMENT_PROGRESS` | `WS` | [/topic/auctions/{auctionId}](./bid/events/settlement-progress.md) | 문자열 0건 (`86`) / 프론트 타입·핸들러 없음 |

### `chat` — 12건

[도메인 명세 목록](./chat/README.md)

> **CHAT-01 전송 핸들러가 들어왔다** (2026-08-02 확인, `539`). `StompChatSendController:37`의
> `@MessageMapping("/auctions/{auctionId}/chat")` → `ChatSendService`(`ChatSendConfiguration`이
> `@Bean` 등록 — `@Service`는 없다) → `ChatMessageCreatedEvent` → `StompChatMessageEventPublisher`
> `AFTER_COMMIT` 송출까지 이어진다. 그 뒤에 매달려 있던 룰 필터·슬로우 모드도 이제 실행 경로가 있다.
>
> ⚠️ **남은 🟡은 질문(CHAT-06)이다.** `isQuestion`은 저장(`chat_message.type='QUESTION'`)까지
> 가지만 `QUESTION_ADDED`·`QUESTION_REMOVED` 발행이 0건이고 `questions` REST 컨트롤러도 없다.

| 구현 | 연동 | 기능 ID | Method | Path / Destination | 근거 |
| --- | --- | --- | --- | --- | --- |
| ❌ | — | `AI-05` | `INTERNAL` | [내부 처리 (LLM 문맥 판정)](./chat/ai-05-internal-internal-d3b776eb.md) | `chat`에 LLM 호출·큐·백오프·DLQ 코드 없음 (`250`) (`product/infrastructure/gms`는 상품 검열이라 별건) |
| ❌ | ✅ | `AI-05` | `WS` | [/user/queue/moderation](./chat/ai-05-ws-user-queue-moderation-5bb776eb.md) | `MODERATION_WARNING`·`CHAT_MUTED` 발행 없음 — ⚠️ `245`가 완료인데 `dev`에 코드가 없다 / `liveSocket.ts:200` 구독 + `handlers.ts:265` |
| ✅ | ✅ | `CHAT-01` | `WS` | [/app/auctions/{auctionId}/chat](./chat/chat-01-ws-app-auctions-by-auctionid-chat-1eb776eb.md) | `StompChatSendController:37` `@MessageMapping` → `ChatSendService`(`ChatSendConfiguration:21`이 `@Bean` 등록) (`539`) / `liveSocket.ts:351`이 SEND |
| ✅ | ✅ | `CHAT-01` | `WS` | [/topic/auctions/{auctionId}/chat](./chat/chat-01-ws-topic-auctions-by-auctionid-chat-533776eb.md) | `StompChatMessageEventPublisher`가 `ChatMessageCreatedEvent`를 `AFTER_COMMIT`으로 받아 `CHAT_MESSAGE`를 송출한다 — 위 SEND 진입점이 생겨 트리거까지 이어진다 / `handlers.ts:182` |
| ✅ | ✅ | `CHAT-02` | `DELETE` | [/api/v1/auctions/{auctionId}/chat/pin](./chat/chat-02-delete-auctions-by-auctionid-chat-pin-b6b776eb.md) | `PinnedNoticeController`의 `@DeleteMapping` / `shared/api/chat.ts:39` `unpinNotice` ← `ChatPanel.vue:468` (`559`) |
| ✅ | ✅ | `CHAT-02` | `POST` | [/api/v1/auctions/{auctionId}/chat/pin](./chat/chat-02-post-auctions-by-auctionid-chat-pin-516776eb.md) | `PinnedNoticeController`의 `@PostMapping` — `messageId`는 26자 정규 ULID만 통과한다(`ChatMessageId`, `613`) / `shared/api/chat.ts:34` `pinNotice` ← `ChatPanel.vue:456` (`559`) |
| ✅ | — | `CHAT-03` | `INTERNAL` | [내부 처리 (룰 기반 필터)](./chat/chat-03-internal-internal-127776eb.md) | `RuleBasedChatContentFilter`의 유일 소비자 `ChatSendService`에 SEND 진입점이 생겨 실행 경로가 열렸다(위 CHAT-01). 결과값 `maskedFields`는 `ChatPanel.vue:259`(`isMasked`)가 표시 판정에만 쓴다 |
| ✅ | — | `CHAT-04` | `INTERNAL` | [내부 처리 (슬로우 모드)](./chat/chat-04-internal-internal-b00776eb.md) | `RedisChatSlowModeChecker` ← `ChatSendService`(`ChatSendPolicy:71`이 결과 판정). CHAT-01과 같은 이유로 도달 가능해졌다 |
| ❌ | 🟡 | `CHAT-06` | `DELETE` | [/api/v1/auctions/{auctionId}/questions/{questionId}](./chat/chat-06-delete-auctions-by-auctionid-questions-by-questionid-12a776eb.md) | `questions` REST 컨트롤러 자체가 없다 / `questions.ts:84` + `ConsoleView.vue:274`, 목 분기 `questions.ts:85` |
| ❌ | 🟡 | `CHAT-06` | `GET` | [/api/v1/auctions/{auctionId}/questions](./chat/chat-06-get-auctions-by-auctionid-questions-e0c776eb.md) | 위와 동일 / `useQuestionQueue.ts:100,145`, 목 분기 `questions.ts:70` |
| 🟡 | ✅ | `CHAT-06` | `WS` | [/app/auctions/{auctionId}/chat](./chat/chat-06-ws-app-auctions-by-auctionid-chat-781776eb.md) | CHAT-01과 같은 목적지라 `@MessageMapping`은 생겼고 `isQuestion`도 `chat_message.type='QUESTION'`까지 저장된다. **다만 질문 큐 쪽 발행(`QUESTION_ADDED`)이 0건이라 계약이 끝까지 닫히지 않는다** / `liveSocket.ts:351` |
| 🟡 | ✅ | `CHAT-06` | `WS` | [/topic/auctions/{auctionId}/questions](./chat/chat-06-ws-topic-auctions-by-auctionid-questions-5ec776eb.md) | 구독 권한 검증만 구현(`StompSubscriptionDestinationResolver:17` 호스트 전용). `QUESTION_ADDED/REMOVED` 발행 없음 / `liveSocket.ts:196` |

### `common` — 6건

[도메인 명세 목록](./common/README.md)

| 구현 | 연동 | 기능 ID | Method | Path / Destination | 근거 |
| --- | --- | --- | --- | --- | --- |
| ✅ | ✅ | `—` | `—` | [WebSocket 공통 메시지 형식](./common/websocket-message-format.md) | `common/event/WsEvent`를 모든 STOMP 퍼블리셔가 봉투로 쓴다 / `socket/types.ts:27`이 같은 규격 |
| ✅ | ✅ | `WS-01` | `WS` | [/app/auth/renew](./common/ws-01-ws-app-auth-renew-e0d776eb.md) | `StompTokenRenewalController:48` / `liveSocket.ts:130` |
| ✅ | ✅ | `WS-01` | `WS` | [/ws](./common/ws-01-ws-ws-053776eb.md) | `WebSocketConfig:43` + `StompConnectAuthenticationInterceptor` / `liveSocket.ts:95` |
| ✅ | — | `WS-02` | `INTERNAL` | [내부 처리 (구독 권한 검증)](./common/ws-02-internal-internal-146776eb.md) | `StompSubscriptionAuthorizationInterceptor`, `WebSocketConfig:62-67` 등록 |
| ❌ | ✅ | `WS-03` | `GET` | [/api/v1/auctions/{auctionId}/realtime-state](./common/ws-03-get-auctions-by-auctionid-realtime-state-9b3776eb.md) | ⚠️ **`realtime-state` 문자열 0건** (`261`, 후속 `350`) / `realtimeState.ts:68`이 **목 분기 없이 실호출**한다 — 실서버에서 재접속 스냅샷 복원이 통째로 실패한다 |
| ✅ | ✅ | `WS-04` | `WS` | [/user/queue/errors](./common/ws-04-ws-user-queue-errors-498776eb.md) | `SimpUserWebSocketErrorPublisher:42` / `liveSocket.ts:204` → `AUTH_EXPIRED` 재인증 |

### `deal` — 6건

[도메인 명세 목록](./deal/README.md)

| 구현 | 연동 | 기능 ID | Method | Path / Destination | 근거 |
| --- | --- | --- | --- | --- | --- |
| ✅ | ❌ | `DEAL-01` | `GET` | [/api/v1/members/me/deals](./deal/deal-01-get-members-me-deals-4a7776eb.md) | `MyDealListController` / `shared/api/deals.ts`에는 `todos`뿐 — 호출 함수가 없다 |
| ✅ | — | `DEAL-05` | `INTERNAL` | [내부 처리 (7일 자동 확정)](./deal/deal-05-internal-internal-a43776eb.md) | `DealAutoConfirmService` + `DealAutoConfirmScheduler` (489) |
| ✅ | ✅ | `DEAL-05` | `PATCH` | [/api/v1/deals/{dealId}/shipping-address](./deal/deal-05-patch-deals-by-dealid-shipping-address-620776eb.md) | `DealEscrowController:30` / `useDealAction.ts:135` |
| ✅ | ✅ | `DEAL-05` | `POST` | [/api/v1/deals/{dealId}/receipt-confirmation](./deal/deal-05-post-deals-by-dealid-receipt-confirmation-f26776eb.md) | `DealEscrowController:57` / `useDealAction.ts:117` |
| ✅ | ✅ | `DEAL-05` | `POST` | [/api/v1/deals/{dealId}/shipment-confirmation](./deal/deal-05-post-deals-by-dealid-shipment-confirmation-255776eb.md) | `DealEscrowController:46` / `useDealAction.ts:104` |
| ✅ | ✅ | `MEM-02` | `GET` | [/api/v1/members/me/deals/todos](./deal/mem-02-get-members-me-deals-todos-27e776eb.md) | `DealTodoController` / `MyPageView.vue:102` |

### `media` — 7건

[도메인 명세 목록](./media/README.md)

| 구현 | 연동 | 기능 ID | Method | Path / Destination | 근거 |
| --- | --- | --- | --- | --- | --- |
| 🟡 | ❌ | `LIVE_ENDED` | `WS` | [/topic/rooms/{roomId}/live](./media/events/live-ended.md) | 발행은 `StompLiveBroadcastEventPublisher:36`이 하지만 **구독을 못 한다** — `StompSubscriptionDestinationResolver`가 `/topic/rooms/**`를 모르는 destination(`UNKNOWN`)으로 떨궈서 `StompSubscriptionAuthorizationInterceptor:53`이 SUBSCRIBE 프레임을 폐기하고 `SUBSCRIPTION_DENIED`를 회신한다. **BE 선행이 필요하다** / 프론트는 `/topic/auctions/*`와 `/user/queue/*`만 구독한다 |
| ✅ | 🟡 | `LIVE-01` | `POST` | [/api/v1/rooms/{roomId}/live/publisher](./media/live-01-post-rooms-by-roomid-live-publisher-404776eb.md) | `LiveTokenController:23` / `media/api.ts:88` + `PublisherPreview.vue:103`, 목 분기 `api.ts:89` |
| ✅ | 🟡 | `LIVE-01` | `POST` | [/api/v1/rooms/{roomId}/live/subscriber](./media/live-01-post-rooms-by-roomid-live-subscriber-310776eb.md) | `LiveTokenController:35` / `media/api.ts:98` + `LiveStage.vue:339`, 목 분기 `api.ts:99` |
| ✅ | 🟡 | `LIVE-02` | `POST` | [/api/v1/rooms/{roomId}/live/end](./media/live-02-post-rooms-by-roomid-live-end-502776eb.md) | `LiveBroadcastEndController:22` / `media/api.ts:108` + `ConsoleView.vue:203`, 목 분기 `api.ts:109` |
| ✅ | — | `LIVE-03` | `INTERNAL` | [내부 처리 (접속 세션 집계)](./media/live-03-internal-internal-3fa776eb.md) | `ViewerSessionStompEventListener` + `ViewerSessionTrackingService` + `RedisViewerSessionStore` |
| ✅ | ✅ | `LIVE-03` | `WS` | [/topic/auctions/{auctionId}/viewers](./media/live-03-ws-topic-auctions-by-auctionid-viewers-f7f776eb.md) | `StompViewerCountPublisher:32` / `liveSocket.ts:191` → `handlers.ts:197` |
| ✅ | ✅ | `LIVE-06` | `POST` | [/api/v1/rooms/{roomId}/live/snapshot](./media/live-06-post-rooms-by-roomid-live-snapshot-ef6776eb.md) | `LiveSnapshotController:40` / `media/api.ts:140` ← `PublisherPreview.vue:380` (`704`에서 A안 — 프론트가 캡처해 multipart 업로드 — 으로 정리됐다). 표시는 `705`: 결과 응답의 `snapshotUrl`을 `ResultView.vue`가, MEM-02의 같은 필드를 `MyAuctionsPanel.vue`가 그린다 |

### `member` — 10건

[도메인 명세 목록](./member/README.md)

| 구현 | 연동 | 기능 ID | Method | Path / Destination | 근거 |
| --- | --- | --- | --- | --- | --- |
| ✅ | ✅ | `MEM-02` | `GET` | [/api/v1/members/me](./member/mem-02-get-members-me-58a776eb.md) | `ProfileQueryController` / `MyPageView.vue:118`, `stores/auth.ts:448` |
| ✅ | ✅ | `MEM-02` | `GET` | [/api/v1/members/me/auctions/hosted](./member/mem-02-get-members-me-auctions-hosted-bdc776eb.md) | `HostedAuctionQueryController` / `MyAuctionsPanel.vue:66` |
| ✅ | ✅ | `MEM-02` | `GET` | [/api/v1/members/me/auctions/participated](./member/mem-02-get-members-me-auctions-participated-87d776eb.md) | `ParticipatedAuctionQueryController` / `MyAuctionsPanel.vue:67` |
| ✅ | ✅ | `MEM-02` | `GET` | [/api/v1/members/me/auctions/won](./member/mem-02-get-members-me-auctions-won-a2d776eb.md) | `WonAuctionQueryController` / `MyAuctionsPanel.vue:65` |
| ✅ | ✅ | `MEM-02` | `GET` | [/api/v1/members/nickname-availability](./member/mem-02-get-members-nickname-availability-0de776eb.md) | `NicknameAvailabilityController` / `OnboardingStep.vue:92` |
| ✅ | ✅ | `MEM-02` | `GET` | [/api/v1/terms?purpose=ONBOARDING](./member/mem-02-get-terms-purpose-onboarding-3af776eb.md) | `TermsController` / `stores/auth.ts:179` |
| ✅ | ✅ | `MEM-02` | `PATCH` | [/api/v1/members/me](./member/mem-02-patch-members-me-2fc776eb.md) | `ProfileUpdateController` / `stores/auth.ts:343` |
| ✅ | ✅ | `MEM-02` | `POST` | [/api/v1/members/me/terms-agreements](./member/mem-02-post-members-me-terms-agreements-662776eb.md) | `TermsAgreementController` / `OnboardingView.vue:37` |
| ✅ | ✅ | `MEM-03` | `GET` | [/api/v1/members/me/settlement-account](./member/mem-03-get-members-me-settlement-account.md) | `SettlementAccountQueryController` (490) / `WithdrawView.vue:97`. ⚠️ 이 문서는 메타데이터 표가 없어 경로를 본문 Request 블록에서 읽어야 한다 |
| ✅ | ✅ | `MEM-03` | `PUT` | [/api/v1/members/me/settlement-account](./member/mem-03-put-members-me-settlement-account.md) | `SettlementAccountUpdateController` (490) / `WithdrawView.vue:145`. 위와 같이 메타데이터 표 없음 |

### `prediction` — 6건

[도메인 명세 목록](./prediction/README.md)

| 구현 | 연동 | 기능 ID | Method | Path / Destination | 근거 |
| --- | --- | --- | --- | --- | --- |
| ✅ | ✅ | `PREDICTION_SETTLED` | `WS` | [/topic/auctions/{auctionId}](./prediction/events/prediction-settled.md) | `PredictionSettledWebSocketPublisher:28` / `handlers.ts:125` |
| ✅ | ✅ | `PRED-01` | `POST` | [/api/v1/auctions/{auctionId}/predictions](./prediction/pred-01-post-auctions-by-auctionid-predictions-a9e776eb.md) | `PredictionController` / `stores/prediction.ts:88` ← `PredictionDock.vue:104` |
| ✅ | 🟡 | `PRED-02` | `GET` | [/api/v1/auctions/{auctionId}/prediction-results](./prediction/pred-02-get-auctions-by-auctionid-prediction-results-60d776eb.md) | `PredictionResultController` / `resultSource.ts:28` 목 — DEAL-06/08과 같은 의도된 데모 경로다 |
| ✅ | — | `PRED-03` | `INTERNAL` | [내부 처리 (고정 배당 정산)](./prediction/pred-03-internal-internal-49b776eb.md) | `PredictionSettlementService` + `PredictionSettlementPolicy` |
| ✅ | ✅ | `PRED-04` | `GET` | [/api/v1/members/me/prediction-achievements](./prediction/pred-04-get-members-me-prediction-achievements-a61776eb.md) | `PredictionAchievementController` (`103` 완료) / `LeaderboardPanel.vue:81` `getMyPredictionAchievements` — MR **!300**(`561`)이 `dev`에 머지되면서 연동이 닫혔다 |
| ✅ | ✅ | `PRED-05` | `GET` | [/api/v1/predictions/leaderboards/daily](./prediction/pred-05-get-predictions-leaderboards-daily-0fc776eb.md) | `PredictionLeaderboardController`의 `@GetMapping("/daily")` → `PredictionLeaderboardQueryService`·JPA 어댑터·Redis Top10 캐시 (`104`) / `predictions.ts:166` `getDailyLeaderboard` ← `LeaderboardPanel.vue:36`·`HomeTicker.vue:51`, 목 분기 없음 (`547`) |

### `product` — 3건

[도메인 명세 목록](./product/README.md)

| 구현 | 연동 | 기능 ID | Method | Path / Destination | 근거 |
| --- | --- | --- | --- | --- | --- |
| ❌ | ❌ | `AI-01` | `POST` | [/api/v1/ai/products/analyze](./product/ai-01-post-ai-products-analyze-e36776eb.md) | 매핑 없음 (`97`, 프론트 `415`) / 프론트 호출 0건 |
| ❌ | ❌ | `AI-02` | `POST` | [/api/v1/ai/price-recommendations](./product/ai-02-post-ai-price-recommendations-12b776eb.md) | 매핑 없음 (`98`, 프론트 `415`) / 프론트 호출 0건 |
| ✅ | ✅ | `AI-04` | `POST` | [/api/v1/ai/products/inspect](./product/ai-04-post-ai-products-inspect-d62776eb.md) | `ProductInspectionController` (`SecurityConfig:117`) / `RoomCreateView.vue` 1단계 「다음」에서 호출 |

### `report` — 2건

[도메인 명세 목록](./report/README.md)

| 구현 | 연동 | 기능 ID | Method | Path / Destination | 근거 |
| --- | --- | --- | --- | --- | --- |
| ✅ | ✅ | `ADMIN-01` | `GET` | [/api/v1/admin/reports](./report/admin-01-get-admin-reports.md) | `AdminReportController` / `AdminView.vue:108`. Notion 원본이 없는 로컬 계약이다 (525) |
| ✅ | ✅ | `DEAL-04` | `POST` | [/api/v1/auctions/{auctionId}/reports](./report/deal-04-post-auctions-by-auctionid-reports-b7d776eb.md) | `AuctionReportController` / `useAuctionReport.ts:48` |

### `wallet` — 7건

[도메인 명세 목록](./wallet/README.md)

> 지갑 호출은 `shared/api/wallets.ts`가 아니라 **`stores/wallet.ts` 경유**다. API 파일만 보면
> 미연동으로 오판한다.

| 구현 | 연동 | 기능 ID | Method | Path / Destination | 근거 |
| --- | --- | --- | --- | --- | --- |
| ✅ | ✅ | `PNT-01` | `GET` | [/api/v1/members/me/wallets](./wallet/pnt-01-get-members-me-wallets-2e8776eb.md) | `WalletQueryController` / `stores/wallet.ts:174` ← `AppHeader.vue:40` 외 4개 화면 |
| ✅ | ✅ | `PNT-01` | `POST` | [/api/v1/members/me/wallets/trade/charge](./wallet/pnt-01-post-members-me-wallets-trade-charge-4b6776eb.md) | `TradePointChargeController` / `stores/wallet.ts:265` ← `ChargeView.vue:84` |
| ✅ | ✅ | `PNT-01` | `POST` | [/api/v1/members/me/wallets/trade/refunds](./wallet/pnt-01-post-members-me-wallets-trade-refunds-b98776eb.md) | `TradePointRefundController` / `stores/wallet.ts:296` ← `WithdrawView.vue:166` |
| ✅ | ✅ | `PNT-02` | `GET` | [/api/v1/members/me/point-ledgers](./wallet/pnt-02-get-members-me-point-ledgers-499776eb.md) | `PointLedgerQueryController` / `stores/wallet.ts:213,238` ← `WalletView.vue:314` |
| ✅ | — | `PNT-03` | `INTERNAL` | [내부 처리 (홀드·해제·정산)](./wallet/pnt-03-internal-internal-556776eb.md) | `TradePointOperationService` (498), 호출부 `BidCommandService:35`·`AuctionForceEndService:59` |
| ❌ | ❌ | `PNT-04` | `POST` | [/api/v1/payments/charge](./wallet/pnt-04-post-payments-charge-f44776eb.md) | 백엔드 전체에 `payments` 매핑 없음 / `ChargeView.vue:9` 주석에 「별도 흐름」으로만 언급 |
| 🟡 | — | `PNT-05` | `INTERNAL` | [내부 처리 (베팅 포인트 지급)](./wallet/pnt-05-internal-internal-fa7776eb.md) | `BettingPointGrantService` 본체는 원장·멱등까지 실구현이나 **자기 파일 밖 참조가 0건** — 가입·출석 어디서도 안 부른다 (505) |

### `auth` — 6건

[도메인 명세 목록](./auth/README.md)

| 구현 | 연동 | 기능 ID | Method | Path / Destination | 근거 |
| --- | --- | --- | --- | --- | --- |
| ✅ | — | `MEM-01` | `GET` | [/login/oauth2/code/kakao](./auth/mem-01-get-login-oauth2-code-kakao-aa6776eb.md) | Spring Security `oauth2Login` (`SecurityConfig:216-217`) — 프레임워크 제공 / 브라우저 리다이렉트라 프론트 호출 코드가 없다. 복귀 지점은 별개 경로인 `/oauth/callback`이다 |
| ✅ | ✅ | `MEM-01` | `GET` | [/oauth2/authorization/kakao](./auth/mem-01-get-oauth2-authorization-kakao-203776eb.md) | Spring Security `oauth2Login` (`SecurityConfig:216`) / `auth.ts:69` ← `useKakaoAuth.ts:59` |
| ✅ | — | `MEM-01` | `INTERNAL` | [내부 처리 (OAuth 자동 가입)](./auth/mem-01-internal-internal-951776eb.md) | `KakaoOAuth2AuthenticationSuccessHandler:24` → `OAuthMemberProvisioningService` |
| ✅ | ✅ | `MEM-01` | `POST` | [/api/v1/auth/logout](./auth/mem-01-post-auth-logout-fbb776eb.md) | `LogoutController` / `stores/auth.ts:409` ← `MyPageView.vue:181` |
| ✅ | ✅ | `MEM-01` | `POST` | [/api/v1/auth/oauth2/token](./auth/mem-01-post-auth-oauth2-token-769776eb.md) | `OAuthTokenController` / `OAuthCallbackView.vue:63` |
| ✅ | ✅ | `MEM-01` | `POST` | [/api/v1/auth/refresh](./auth/mem-01-post-auth-refresh-1a0776eb.md) | `AccessTokenRefreshController` / `shared/api/http.ts:23` 401 인터셉터가 자동 호출 |

---

## 읽는 법 — 남은 일의 성격이 네 가지다

숫자는 전부 **행 수**이고, 괄호 안은 표에서 그 묶음을 뽑는 조건이다.

**① 서버에 붙었는데 화면이 안 쓴다 — 1행** (구현 ✅ · 연동 ❌).
`DEAL-01`. **프론트 작업만 남았다.**
`LIVE-06`은 업로드(`704`)와 표시(`705`)가 붙어 이 묶음에서 빠졌다.
`PRED-04`(!300, `561`)와 `AI-04`(!297)는 그 사이 `dev`에 머지돼 이 묶음에서 빠졌다.

⚠️ **`LIVE_ENDED`는 여기 없다.** 예전에는 이 묶음이었지만 프론트 작업만으로 못 끝낸다 —
구독 인가가 `/topic/rooms/**`를 모르는 destination으로 떨궈서 FE가 구독해도 프레임이 폐기된다.
**BE 선행이 필요하다**(③으로 옮겼다, 후속 `563`).

**② 화면 계약에 비해 서버 구현이 부족하다 — 4행.**

- 서버에 아예 없다 — 2행 (구현 ❌ · 연동 ✅): `WS-03`(`261`) · `AI-05` WS(`250`)
- 코드가 일부 있거나 도달하지 못한다 — 2행 (구현 🟡 · 연동 ✅): `CHAT-06` WS 2행

`WS-03 realtime-state`는 프론트에 목 분기가 없어 실서버 재접속 스냅샷 복원이 실패한다.

**③ 로직 또는 일부 구현은 있지만 계약 전체가 연결되지 않았다 — 4행** (구현 🟡).
`CHAT-06` WS 2행 · `PNT-05` 베팅 포인트 지급(`505`) ·
`LIVE_ENDED`(발행은 되나 구독 인가가 destination을 모른다).
빠진 연결 지점은 컨트롤러가 아니라 이제 **발행부와 구독 인가**다.
**미구현과 부분 구현·연결 누락은 티켓 성격이 다르다.**

**④ 양쪽 다 없다 — 나머지.** `AI-01`(`97`) · `AI-02`(`98`) · `AI-03`(`75`) ·
`PNT-04` 결제 · `BID-08`(`85`) · `BID-10`(`86`) ·
`SETTLEMENT_PROGRESS` · `AI-05` LLM 판정(`250`).
폐기된 `AUCTION_PAUSED`·`AUCTION_RESUMED`는 구현 대상에서 제외한다.

**`BID-07` 정산 진입점이 열렸다 — 더 이상 병목이 아니다.** `feat/vickrey_engine_call`(`9c17ee7`)이
마감 예약을 붙이면서 조립돼 있던 흐름(`AuctionSettlementService` → 집계·포인트·결과 저장 →
`AUCTION_ENDED`·`SETTLED`·`NO_SALE` 발행)이 통째로 도달 가능해졌다 —
`AuctionRoomStartService:86`·`AuctionExtensionService:78`의 시작·연장 이벤트를
`AuctionSettlementDeadlineEventListener`가 커밋 후 받아 예약하고,
`AuctionSettlementDeadlineScheduler:120`이 마감 시각에 `settle()`을 부른다(실패 시 지연 재시도,
재기동 복구는 `AuctionSettlementDeadlineRecovery:31`). **경매가 스스로 끝난다.**
그래서 `BID-07`과 WS 3행이 이번에 같이 열렸다(`89`).
`BID-10` 집계 진행 이벤트(`SETTLEMENT_PROGRESS`)는 아직 발행부가 없어 ④에 남는다 (`86` · 연동 `145`).
`auction_result` INSERT도 `JpaAuctionSettlementAdapter:128`·`JpaAuctionForceEndAdapter:54`
두 곳에 실재하고 `JpaAuctionResultQueryAdapter:21` 주석도 같은 사실을 적고 있다.

## 공통 WebSocket 형식

- [WebSocket 공통 메시지 형식](./common/websocket-message-format.md)

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [API 명세 테이블](https://app.notion.com/p/53e776ebfd6882529d6381be639c7de9)
- [WebSocket 이벤트 명세](https://app.notion.com/p/728776ebfd6883f89ae201878be07850)
