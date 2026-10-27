// ROOM-08 홈(main)의 두 블록 파생 (S15P11A704-309, 507).
//
// **더 이상 목이 아니다.** 2026-07-31까지 `mocks/homeFeed.ts`였는데, 하는 일은 `room` 스토어가
// 실 API에서 받아온 방 목록을 자르는 것뿐이라 이름만 목이었다. 히어로의 편집 카피(태그·판매자명)만
// 진짜 목이었고 그게 실 서버에서 배너를 비게 만들어(507) 걷어냈다 — 남은 것은 순수 파생이다.

import { endAtMs, roomStateOf, type AuctionRoomSummary } from '@/stores/room'

/** 히어로 배너 한 장 */
export interface HeroSlide {
  room: AuctionRoomSummary
  /**
   * 슬라이드 안 칩. 카테고리 표시명이다.
   *
   * 정본(`비드라이브 인터랙티브.html`)은 여기에 큐레이션 문구를 넣었다 —
   * 「🔥 지금 가장 뜨거운 라이브」처럼 사람이 방 넷을 골라 붙인 카피다. 실 서버에는 대응 필드가
   * 없고 **아무 방에나 붙이면 근거 없는 주장**이 된다(첫 칸이 「가장 뜨거운」이라는 근거가 없다).
   * 서버가 큐레이션을 주게 되면 그때 이 자리를 그 값으로 바꾼다.
   */
  tag: string
}

/**
 * 히어로 — 목록 **앞에서** 자른다.
 *
 * `LIVE`는 서버가 `endAt` 오름차순으로 주므로(ROOM-02 정렬 정책) 「마감이 가까운 순」이 유지되고,
 * 마감 임박 컬럼과 같은 방을 같은 순서로 집는다. 예전에는 방 id 1~4를 하드코딩해 대조했는데
 * 실 서버에는 그 id가 없어 **배너가 조용히 비었다**(507).
 *
 * `nameByCode`를 인자로 받는 이유는 방 목록을 인자로 받는 것과 같다 — 이 파일이 스토어를 직접
 * 읽으면 화면이 데이터를 두 곳에서 가져오게 된다. 호출부(`MainView`)가 스토어를 단일 소스로 쥔다.
 */
export function heroSlides(
  rooms: readonly AuctionRoomSummary[],
  nameByCode: (code: string) => string,
  limit = 4,
): HeroSlide[] {
  return endingSoon(rooms, limit).map((room) => ({ room, tag: nameByCode(room.categoryCode) }))
}

/**
 * 마감 임박 — 진행 중 방 앞에서 몇 개.
 *
 * **정렬하지 않는다.** 「경매방 목록 조회」(ROOM-02, 확정)가 "`LIVE`는 `endAt` 오름차순"으로
 * 정렬 정책을 서버 몫으로 못 박았고, `MainView`가 `status=LIVE`로 받으므로 이미 그 순서다
 * (테스트용 가짜 서버도 같은 정책을 재현한다 — `mocks/roomServer.ts`의 `sortByStatusPolicy`).
 * 여기서 다시 세우면 커서 페이지 경계에서 서버 순서와 어긋난다 (S15P11A704-396).
 */
export function endingSoon(rooms: readonly AuctionRoomSummary[], limit = 4): AuctionRoomSummary[] {
  // 마감 시각이 없는 방은 카운트다운을 못 그리므로 제외한다 (상태 필터는 서버가 이미 걸었다)
  return rooms
    .filter((room) => roomStateOf(room) === 'live' && endAtMs(room) !== undefined)
    .slice(0, limit)
}
