// ROOM-02 방 목록 목 데이터 — 디자인 단일 소스(비드라이브 인터랙티브.html)의 ROOMS 배열 기반
// 비크리 비공개 원칙: 진행 중 방에는 현재 최고가·타인 입찰 금액을 두지 않는다.
// 공개 가능: 시작가·입찰 건수·참여/시청자 수. 낙찰가(soldPrice)는 종료된 방에서만 공개.

export type RoomState = 'live' | 'waiting' | 'ended'

export interface Room {
  id: number
  title: string
  category: string
  state: RoomState
  /** 시작가(하한가), 포인트 단위 */
  startPrice: number
  /** 입찰 건수 (live) */
  bidCount: number
  /** 참여 인원 — live는 입찰 참여자, waiting은 대기 인원 */
  participantCount: number
  /** 시청자 수 (live) */
  viewerCount: number
  /** live: 서버 권위 마감 시각 (epoch ms) — 남은 시간은 이 절대 시각 기준으로 표시만 한다 (BID-05) */
  endsAt?: number
  /** waiting: 시작 예정 시각 표시 문자열 */
  startsAtLabel?: string
  /**
   * waiting: 시작 예정 시각 (epoch ms).
   * 라벨만으로는 카운트다운을 만들 수 없어 절대 시각을 같이 둔다 — 서버는 `scheduledAt`으로 시각을 준다.
   */
  startsAt?: number
  /** ended: 종료 시점 표시 문자열 */
  endedAtLabel?: string
  /**
   * ended: **실제** 종료 시각 (epoch ms). 라벨만으로는 절대 시각을 만들 수 없어 같이 둔다 —
   * 서버는 `endedAt`으로 주고, 예정 마감 시각(`endAt`)과 구분되는 값이다.
   */
  endedAt?: number
  /** ended: 낙찰가 — 종료 후에만 공개 */
  soldPrice?: number
}

// 목 데이터는 모듈 로드 시각 기준으로 마감 시각을 만든다 (고정 epoch를 박으면 나중에 열었을 때 전부 만료).
// 실 연동 뒤에는 **테스트 픽스처 전용**이다(S15P11A704-504) — `mocks/roomServer.ts`가 상대 시각을
// 명세의 절대 시각(`endAt`)으로 바꿔 응답을 만든다. 화면이 읽는 것은 그 응답이다.
const now = Date.now()

export const mockRooms: Room[] = [
  // ── 진행중 (live) ──
  {
    id: 1,
    title: '기계식 키보드 커스텀 풀빌드 (저소음 적축)',
    category: '전자기기',
    state: 'live',
    startPrice: 30000,
    bidCount: 12,
    participantCount: 9,
    viewerCount: 24,
    endsAt: now + 252_000,
  },
  {
    id: 2,
    title: '한정판 피규어 미개봉',
    category: '취미·수집',
    state: 'live',
    startPrice: 50000,
    bidCount: 7,
    participantCount: 15,
    viewerCount: 41,
    endsAt: now + 748_000,
  },
  {
    id: 3,
    title: '아트토이 굿즈 컬렉션 (3종)',
    category: '취미·수집',
    state: 'live',
    startPrice: 25000,
    bidCount: 9,
    participantCount: 20,
    viewerCount: 33,
    endsAt: now + 119_000,
  },
  {
    id: 4,
    title: '스니커즈 한정 컬래버',
    category: '패션·잡화',
    state: 'live',
    startPrice: 90000,
    bidCount: 15,
    participantCount: 29,
    viewerCount: 52,
    endsAt: now + 187_000,
  },
  // ── 대기 (waiting) — 채팅만 활성, 입찰·베팅 잠금 (ROOM-07) ──
  {
    id: 5,
    title: '라이카 M6 필름 바디',
    category: '전자기기',
    state: 'waiting',
    startPrice: 450000,
    bidCount: 0,
    participantCount: 12,
    viewerCount: 0,
    startsAtLabel: '오늘 20:20',
    startsAt: now + 12 * 60_000,
  },
  {
    id: 6,
    title: '한정판 스니커즈 US 270',
    category: '패션·잡화',
    state: 'waiting',
    startPrice: 220000,
    bidCount: 0,
    participantCount: 31,
    viewerCount: 0,
    startsAtLabel: '오늘 21:00',
    startsAt: now + 52 * 60_000,
  },
  {
    id: 7,
    title: '수제 도자기 다기 세트',
    category: '생활용품',
    state: 'waiting',
    startPrice: 40000,
    bidCount: 0,
    participantCount: 2,
    viewerCount: 0,
    startsAtLabel: '토요일 19:00',
    startsAt: now + 2 * 60 * 60_000,
  },
  // ── 종료 (ended) — 낙찰가는 종료 후에만 공개 ──
  {
    id: 8,
    title: '스니커즈 한정 컬래버 (2차)',
    category: '패션·잡화',
    state: 'ended',
    startPrice: 70000,
    bidCount: 9,
    participantCount: 14,
    viewerCount: 0,
    endedAtLabel: '어제',
    endedAt: now - 24 * 60 * 60_000,
    soldPrice: 90000,
  },
  {
    id: 9,
    title: '미니 턴테이블 + LP 5장',
    category: '도서·음반',
    state: 'ended',
    startPrice: 80000,
    bidCount: 6,
    participantCount: 11,
    viewerCount: 0,
    endedAtLabel: '2일 전',
    endedAt: now - 2 * 24 * 60 * 60_000,
    soldPrice: 128000,
  },
  // 아래 7개는 **무한 스크롤 확인용**이다 (S15P11A704-396).
  // 상태별로 서버가 거르게 되면서 한 탭이 8건 페이지를 넘겨야 다음 페이지가 생기는데,
  // 종료 방이 2개뿐이라 스크롤이 아예 안 나왔다. 종료를 늘린 이유는 홈이 종료를 안 쓰기 때문이다
  // (히어로는 id 1~4, 마감 임박은 live만) — 진행중을 늘리면 홈 화면이 같이 바뀐다.
  {
    id: 10,
    title: '빈티지 데님 자켓 (90s)',
    category: '패션·잡화',
    state: 'ended',
    startPrice: 40000,
    bidCount: 8,
    participantCount: 9,
    viewerCount: 0,
    endedAtLabel: '3일 전',
    endedAt: now - 3 * 24 * 60 * 60_000,
    soldPrice: 62000,
  },
  {
    id: 11,
    title: '캠핑 랜턴 세트 (3종)',
    category: '스포츠·레저',
    state: 'ended',
    startPrice: 35000,
    bidCount: 5,
    participantCount: 7,
    viewerCount: 0,
    endedAtLabel: '3일 전',
    endedAt: now - 3 * 24 * 60 * 60_000 - 6 * 60 * 60_000,
    soldPrice: 51000,
  },
  {
    id: 12,
    title: '무선 이어버드 (미개봉)',
    category: '전자기기',
    state: 'ended',
    startPrice: 70000,
    bidCount: 14,
    participantCount: 16,
    viewerCount: 0,
    endedAtLabel: '4일 전',
    endedAt: now - 4 * 24 * 60 * 60_000,
    soldPrice: 112000,
  },
  {
    id: 13,
    title: '보드게임 대형 3종 묶음',
    category: '취미·수집',
    state: 'ended',
    startPrice: 45000,
    bidCount: 4,
    participantCount: 6,
    viewerCount: 0,
    endedAtLabel: '5일 전',
    endedAt: now - 5 * 24 * 60 * 60_000,
    // 유찰 — 낙찰가가 없는 종료 방도 목록에 섞여 있어야 카드가 그 경우를 그리는지 확인된다
  },
  {
    id: 14,
    title: '원목 협탁 (조립 완료)',
    category: '생활용품',
    state: 'ended',
    startPrice: 30000,
    bidCount: 3,
    participantCount: 5,
    viewerCount: 0,
    endedAtLabel: '6일 전',
    endedAt: now - 6 * 24 * 60 * 60_000,
    soldPrice: 38000,
  },
  {
    id: 15,
    title: '만화 전집 (완결 24권)',
    category: '도서·음반',
    state: 'ended',
    startPrice: 55000,
    bidCount: 9,
    participantCount: 10,
    viewerCount: 0,
    endedAtLabel: '지난주',
    endedAt: now - 8 * 24 * 60 * 60_000,
    soldPrice: 74000,
  },
  {
    id: 16,
    title: '러닝화 275 (거의 새것)',
    category: '스포츠·레저',
    state: 'ended',
    startPrice: 60000,
    bidCount: 7,
    participantCount: 8,
    viewerCount: 0,
    endedAtLabel: '지난주',
    endedAt: now - 9 * 24 * 60 * 60_000,
    soldPrice: 81000,
  },
]
