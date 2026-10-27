// 라이브 화면 하단 「다른 라이브」 파생 (와이어프레임 `data-screen="live"`의 `.live-below`).
//
// 와이어프레임이 이 블록에 남긴 결정 둘을 그대로 따른다.
//   · **방 목록 카드를 그대로 재사용한다** — 전용 카드를 새로 만들지 않는다
//   · **무한 스크롤 없이 8개까지만.** 원문 주석: "여긴 갈아타기용 추천 8개까지만.
//     본격 탐색은 방 목록(무한 스크롤)의 역할"
//
// `homeFeed.ts`와 같은 자리다 — 스토어를 직접 읽지 않고 호출부가 목록을 인자로 넘긴다.
// 그래야 화면이 데이터를 두 곳에서 가져오지 않는다.

import type { AuctionRoomSummary } from '@/stores/room'

/** 와이어프레임이 못 박은 상한. 갈아타기용 추천이라 더 늘리지 않는다 */
export const OTHER_LIVE_LIMIT = 8

/**
 * 지금 보고 있는 방을 뺀 진행 중 방들.
 *
 * **현재 방을 반드시 뺀다** — 보고 있는 방송이 "다른 라이브"에 또 뜨면 눌러도 아무 일이
 * 없고(같은 라우트), 8칸 중 한 칸을 낭비한다.
 *
 * 상태로도 한 번 거른다. 호출부가 `status: 'LIVE'`로 조회하지만 이 스토어는 목록 화면과
 * 공유라, 목록에서 「종료」 탭을 보고 넘어오면 응답이 도착하기 전까지 끝난 방이 들어 있다.
 */
export function otherLiveRooms(
  rooms: readonly AuctionRoomSummary[],
  currentAuctionId: number,
  limit = OTHER_LIVE_LIMIT,
): AuctionRoomSummary[] {
  return rooms
    .filter((room) => room.status === 'LIVE' && room.auctionId !== currentAuctionId)
    .slice(0, limit)
}
