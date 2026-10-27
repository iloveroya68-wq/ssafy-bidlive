// ROOM 경매방 API의 **테스트 전용 가짜 서버**.
//
// 실 연동으로 넘어가면서 `shared/api/rooms.ts`의 목 분기를 전부 지웠다(S15P11A704-504).
// 그런데 `stores/room.spec.ts`가 잡는 것들 — 커서 페이지 이어 붙이기, 탭 전환 경합,
// 상태별 정렬, `counts` 집계 — 은 **응답이 여러 장 오고 조건마다 달라야** 검사가 성립한다.
// 응답 한 개를 고정해 두면 그 테스트들이 전부 공허해지므로, 목 서버 쪽 로직만 여기로 옮겼다.
//
// ⚠️ **화면 코드는 이 파일을 임포트하지 않는다.** 스펙에서 `http.get`을 스텁할 때만 쓴다.
//    API 함수가 아니라 `http`를 스텁하는 이유는, 스펙이 `getAuctionRooms` 호출 횟수를 직접
//    세고 있어서다 — API 함수를 미리 스파이해 두면 그 카운트가 오염된다.

import { mockCursorPage } from '@/shared/api/mock'
import { mockRooms } from '@/mocks/rooms'
import { mockCategories } from '@/mocks/categories'
import { toIsoDateTime } from '@/shared/api/types'
import type {
  AuctionRoomDetail,
  AuctionRoomListQuery,
  AuctionRoomPage,
  AuctionRoomSummary,
  AuctionStatus,
} from '@/shared/api/rooms'
import type { Id, IsoDateTime } from '@/shared/api/types'

const AUCTION_STATUS_BY_ROOM_STATE = {
  live: 'LIVE',
  waiting: 'WAITING',
  ended: 'ENDED',
} as const

/** 방 픽스처는 카테고리를 **한글 표시명**으로 들고 있다. 응답은 코드라 역방향이 필요하다 */
const codeByName = (name: string): string =>
  mockCategories.find((c) => c.name === name)?.code ?? name

const codeById = (categoryId: number): string | undefined =>
  mockCategories.find((c) => c.categoryId === categoryId)?.code

/** epoch ms로. 값이 없으면 뒤로 밀리도록 `fallback`을 준다 */
const msOf = (value: IsoDateTime | null, fallback: number): number => {
  if (value === null) return fallback
  const ms = Date.parse(value)
  return Number.isNaN(ms) ? fallback : ms
}

/**
 * 명세의 상태별 정렬 정책: `LIVE`는 `endAt` 오름차순, `WAITING`은 `scheduledAt` 오름차순,
 * `ENDED`는 `endedAt` 내림차순. 화면은 "서버가 정렬해 준다"는 전제로 만들어져 있어서,
 * 가짜 서버가 순서를 안 지키면 화면에 클라이언트 정렬을 되살리게 된다 —
 * 그러면 커서 페이지 경계에서 순서가 어긋난다(서버는 전체 기준, FE는 받은 것 기준이므로).
 */
function sortByStatusPolicy(
  rooms: AuctionRoomSummary[],
  status: AuctionStatus | undefined,
): AuctionRoomSummary[] {
  const FAR = Number.POSITIVE_INFINITY
  if (status === 'LIVE') return [...rooms].sort((a, b) => msOf(a.endAt, FAR) - msOf(b.endAt, FAR))
  if (status === 'WAITING')
    return [...rooms].sort((a, b) => msOf(a.scheduledAt, FAR) - msOf(b.scheduledAt, FAR))
  if (status === 'ENDED') return [...rooms].sort((a, b) => msOf(b.endedAt, 0) - msOf(a.endedAt, 0))
  // 상태 필터가 없으면 명세가 정렬을 정하지 않았다 — 픽스처 순서 그대로
  return rooms
}

function summaries(): AuctionRoomSummary[] {
  return mockRooms.map((room) => {
    // 픽스처의 `participantCount`는 상태에 따라 뜻이 다르다(live는 입찰 참여자, waiting은 대기 인원).
    // 명세 응답에는 `viewerCount`와 `predictionParticipantCount`뿐이라 여기서 갈라 넣는다.
    const waiting = room.state === 'waiting'
    return {
      auctionId: room.id,
      title: room.title,
      thumbnailUrl: null,
      categoryCode: codeByName(room.category),
      sellerNickname: '판**',
      reservePrice: room.startPrice,
      bidCount: room.bidCount,
      // 대기방은 아직 베팅을 못 받는다 (입찰·예측 모두 시작 후에 열린다)
      predictionParticipantCount: waiting ? 0 : room.participantCount,
      scheduledAt: room.startsAt === undefined ? null : toIsoDateTime(room.startsAt),
      endAt: room.endsAt === undefined ? null : toIsoDateTime(room.endsAt),
      soldPrice: room.soldPrice ?? null,
      endedAt: room.endedAt === undefined ? null : toIsoDateTime(room.endedAt),
      status: AUCTION_STATUS_BY_ROOM_STATE[room.state],
      viewerCount: waiting ? room.participantCount : room.viewerCount,
    }
  })
}

/** ROOM-02 목록. 카테고리·검색어는 목록과 집계 **양쪽**에, 상태는 **목록에만** 적용된다 */
export function roomListResponse(query: AuctionRoomListQuery = {}): AuctionRoomPage {
  const scoped = summaries().filter(
    (room) =>
      (query.categoryId === undefined || room.categoryCode === codeById(query.categoryId)) &&
      (query.keyword === undefined || room.title.includes(query.keyword)),
  )
  const items = scoped.filter((room) => query.status === undefined || room.status === query.status)
  return {
    ...mockCursorPage(sortByStatusPolicy(items, query.status), query, 8),
    // 가짜 서버라 오프셋 0(단말 시계와 동일)이 되도록 지금 시각을 준다
    serverTime: toIsoDateTime(Date.now()),
    counts: {
      LIVE: scoped.filter((r) => r.status === 'LIVE').length,
      WAITING: scoped.filter((r) => r.status === 'WAITING').length,
    },
  }
}

/** ROOM-03 상세. 목록에 없는 id도 딥링크로 열리므로 첫 방으로 떨어뜨린다 */
export function roomDetailResponse(auctionId: Id): AuctionRoomDetail {
  const all = summaries()
  const summary = all.find((room) => room.auctionId === auctionId) ?? all[0]
  return {
    auctionId,
    title: summary?.title ?? '목 경매방',
    status: summary?.status ?? 'WAITING',
    reservePrice: summary?.reservePrice ?? 10_000,
    scheduledAt: summary?.scheduledAt ?? null,
    durationMinutes: 15,
    viewerCount: summary?.viewerCount ?? 0,
    isSeller: true,
    notificationCount: 12,
    product: {
      productId: auctionId,
      title: summary?.title ?? '목 상품',
      description: '목 데이터 상품 설명입니다.',
      imageUrl: summary?.thumbnailUrl ?? '',
    },
    seller: { nickname: '판**', rating: 4.8 },
    canEnter: true,
    serverTime: toIsoDateTime(Date.now()),
  }
}
