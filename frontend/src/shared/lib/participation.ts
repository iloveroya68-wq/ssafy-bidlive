// BID·PRED 참여 역할 — 입찰과 예측 베팅은 한 방에서 상호 배타다.
//
// 근거
// - AGENTS.md 규칙 7 / docs/03-역할-경계-합의.md §4-1
// - DB: `auction_participant`의 UNIQUE(auction_room_id, member_id) + participation_type CHECK
// - 값 집합은 WS-03 스냅샷의 `myParticipationType`과 **같은 문자열**을 쓴다
//   (명세 「경매 상태 스냅샷」 GET /api/v1/auctions/{auctionId}/realtime-state).
//   서버 값을 그대로 들고 있어야 재접속 복원에서 변환 실수가 생기지 않는다.

/** `NONE`(구경 중) | `BID`(입찰자 확정) | `PREDICTION`(예측자 확정) */
export type ParticipationType = 'NONE' | 'BID' | 'PREDICTION'

/**
 * 잠긴 쪽에 남길 안내 문구 (디자인 단일 소스 `비드라이브 인터랙티브.html`의 `.locknote` 카피).
 *
 * 잠기는 것은 **제출뿐**이다 — 패널을 숨기거나 모드 전환을 막지 않는다.
 * 패널이 통째로 사라지면 레이아웃이 흔들리고 기능이 없어진 것으로 오해하기 때문이다.
 */
export const BID_LOCK_NOTE =
  '🔒 이 방에서 예측 베팅에 참여했어요. 입찰과 예측 베팅은 한쪽만 참여할 수 있어요 (상호 배타)'

export const PREDICTION_LOCK_NOTE =
  '🔒 이 방에 이미 입찰했어요. 입찰과 예측 베팅은 한쪽만 참여할 수 있어요 (상호 배타 — 2등가 조작 방지)'
