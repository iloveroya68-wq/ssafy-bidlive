// 목 어댑터 스위치 (S15P11A704-59)
//
// ⚠️ **이 스위치가 덮는 범위는 이제 "아직 서버에 없는 것"뿐이다** (S15P11A704-504).
// 백엔드가 구현한 엔드포인트의 목 분기는 전부 걷어냈다 — 목이 남아 있으면 연동이 깨져도
// 화면이 멀쩡해서 **어긋난 걸 아무도 못 잡는다.**
//
// 아직 목으로 남은 것 (2026-08-01 기준). **이유가 둘로 갈린다 — 섞어 적으면 또 낡는다.**
//
// (가) **서버에 해당 컨트롤러가 없다:**
//   실시간 상태 스냅샷(`/auctions/{id}/realtime-state`) · 콘솔 시연 상태 ·
//   경매 결과 조회·예측 결과 분포 — **API 목이 아니라 화면 데모다**(아래 ※) ·
//   **라이브·대기방 소켓** — 목 모드에서는 STOMP 대신 `mocks/liveFeed`·`mocks/waitingRoom`이
//   스토어를 구동한다(`stores/live.ts` `connect()`). REST가 전부 실 호출이 된 지금, 목 스위치가
//   바꾸는 것 중 **가장 큰 덩어리가 이것**이다.
//
// (나) **서버 컨트롤러는 이미 생겼고 FE 목 분기만 남았다** — 걷어내는 게 후속 작업이다(549):
//   WebRTC 미디어 — `LiveTokenController`, `POST /rooms/{roomId}/live/publisher`·`/subscriber`
//   (`features/live/media/api.ts`). 여기 있는 동안은 연동이 깨져도 화면이 멀쩡하다.
//
// 그 외 API 모듈에는 목 분기가 없다 — 스위치를 켜도 실 호출로 나간다.
//
// PRED-05 일일 리더보드는 2026-08-01에 실 호출로 바뀌었다 (S15P11A704-547) —
// `getDailyLeaderboard`에 목 분기가 없고 `mocks/leaderboard.ts`도 지웠다.
// 인증이 필요해서 비로그인 홈 티커는 401을 받고 조용히 접힌다(목 시절엔 항상 채워져 보였다).
//
// BID-03 입찰 연장도 같은 날 실 호출로 바뀌었다 (S15P11A704-548) — `extendAuction`에 목 분기가 없다.
// 목 모드 콘솔의 「＋ 방송 5분 연장」도 이제 실 서버를 때린다. 판매자 판정·시작·삭제가 이미 실 호출이라
// 콘솔은 원래 실 방이 있어야 열리는 화면이고, 연장만 목이었을 땐 잔여가 늘 「2/3회」로 보였다.
// ⚠️ 다만 목 모드에는 AUCTION_EXTENDED를 보낼 소켓이 없어 **타이머는 그 자리에 머문다**
//    (목 피드가 endsAt을 쥐고 있다). 목 분기 시절에도 안 움직였으니 새로 깨진 건 없지만,
//    +5분이 눈에 보이는 시연이 필요하면 목 모드가 아니라 실 모드로 돌린다.
// ⚠️ 같은 이유로 목 피드가 `AUCTION_STARTED`·`TIME_SYNC`로 `LIVE`와 `endsAt`을 세워 버려서 **실 서버의 방이
//    아직 WAITING이어도 연장 버튼이 활성화**되고, 누르면 409 `AUCTION_NOT_LIVE`가 화면에 뜬다.
//    버그가 아니라 목 분기를 걷어낸 결과다 — 화면 판정이 아니라 **서버 판정이 그대로 보이는 것**이다.
//
// CHAT 채팅 이력(`/auctions/{id}/chats`)도 2026-08-02에 실 호출로 바뀌었다 (S15P11A704-679) —
// `features/chat/history.ts`에 목 분기가 없다. 서버에 `ChatHistoryController`가 있어 목 모드에서도
// 실제 DB의 이력이 온다(목 시절엔 「지난 대화 메시지 #N」이 무한히 나왔다).
// ⚠️ 다만 목 모드에서 실 이력이 오는 것은 **방 입장 시드(첫 페이지)뿐이다.** 목 피드가 흘리는 채팅의
//    id는 `mock-*`이라 커서로 쓸 수 없고(서버가 26자 ULID를 요구해 400 INVALID_CURSOR), 그다음
//    위로 스크롤은 시드가 실어 온 실 id부터만 이어진다.
//
// CHAT-06 질문 큐는 2026-08-02에 실 호출로 바뀌었다 (S15P11A704-680) — `QuestionQueueController`가
// 조회·제거 둘 다 들고 있다. 목 질문 3건이 사라져서 **목 모드에서도 큐는 실제 대기 질문만 보인다**
// (판매자가 아니면 403 `NOT_AUCTION_HOST`로 안내 카드가 뜬다). `QUESTION_REMOVED`도 실발행으로
// 바뀌었다(해결·검열 2·삭제 4곳).
//
// ※ 결과 화면 둘: `shared/api/results.ts`(S15P11A704-518)도 `predictions.ts`의
//   `getPredictionResults`(S15P11A704-520)도 실 호출만 한다 — 서버에 `AuctionResultController`·
//   `PredictionResultController`가 있다. 목 모드에서 fixture를 돌려주는 건
//   `features/result/resultSource.ts` 한 곳뿐이고, 역할 4분기·유찰 3분기를 실제 경매를 끝내지 않고
//   보기 위한 데모다. **그 단락을 지우면 목 모드가 실 서버를 때린다** —
//   `features/result/resultSource.spec.ts`가 잠가 두었다.
//
// ✅ **이 스위치 밖에 남은 가짜는 이제 없다.** 둘 있었고 둘 다 없앴다:
//   · 목 카카오 로그인 — 토큰 없는 세션을 만들어 「로그인된 것처럼 보이지만 전부 401」이었다.
//     되살리지 않고 지웠다. 로그인은 실 카카오 OAuth 하나뿐이다 (S15P11A704-506).
//   · 홈 히어로 배너 — 방 id 1~4 하드코딩을 걷어내고 실 목록 앞에서 자르게 바꾸면서
//     `mocks/homeFeed.ts`가 `features/home/homeFeed.ts`로 옮겨졌다 (S15P11A704-507).
//
// 새로 추가한다면 여기 적는다. 스위치 밖 가짜는 목록만 보고 "나머지는 진짜"로 읽는 사람을 속인다.
//
// ⚠️ **이 목록은 며칠이면 낡는다.** 고칠 때 기억에 의존하지 말고 백엔드의 `@RestController`
//    매핑을 다시 훑어서 맞춘다. 실제로 이 파일이 브랜치를 딴 시점 기준으로 적혀 있어서,
//    머지 대기 중에 지갑·원장·환불·신고 넷이 dev에 들어오며 **머리말이 거짓말이 됐다**(MR !219 리뷰).
//    서버에 생기는 대로 지운다: 분기 삭제 → 스펙은 `http`를 스텁(`mocks/*Server.ts` 참조).
//
// 켜고 끄기: `VITE_API_MOCK=false`를 넣으면 남은 목까지 전부 실 호출이 된다(`.env.example` 참조).
// 기본값이 "켬"인 이유는 위 목록이 아직 서버에 없어서다 — 끄면 그 화면들이 곧바로 죽는다.
// **반대로 켜 두어도 로컬 백엔드는 필요하다** — 홈·목록·대기방 입장이 실 호출이라
// 서버가 없으면 첫 화면부터 오류다. 시연을 목으로 돌리던 전제는 여기서 끝났다.
// ⚠️ `VITE_API_BASE_URL`은 이 스위치가 아니다. 베이스 URL이 채워져 있어도 목은 계속 목이다 —
//    둘을 섞어 판정하면 한 화면에서 목과 실 응답이 섞인다(S15P11A704-394).

import type { CursorPage, CursorPageQuery } from '@/shared/api/types'

export function isMockApi(): boolean {
  const flag = import.meta.env.VITE_API_MOCK
  return String(flag ?? '').toLowerCase() !== 'false'
}

/** 목 응답에도 네트워크 지연을 흉내 낸다 — 로딩 상태 UI가 실제로 보여야 하기 때문 */
export function mockDelay(ms = 300): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/**
 * 배열을 커서 페이지 응답으로 감싼다.
 * 목 커서는 "다음 시작 인덱스"를 문자열로 담은 것이고, 실제 서버 커서는 불투명 값이다 —
 * 프론트는 어느 쪽이든 해석하지 않고 그대로 되돌려 보낸다.
 */
export function mockCursorPage<T>(
  all: T[],
  query: CursorPageQuery = {},
  defaultSize = 20,
): CursorPage<T> {
  const size = query.size ?? defaultSize
  const start = Number(query.cursor ?? '0')
  const offset = Number.isFinite(start) && start > 0 ? start : 0
  const items = all.slice(offset, offset + size)
  const nextOffset = offset + items.length
  const hasNext = nextOffset < all.length
  return { items, nextCursor: hasNext ? String(nextOffset) : null, hasNext }
}
