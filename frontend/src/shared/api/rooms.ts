// ROOM 경매방 API (S15P11A704-59)
//
// 명세 근거 (경로가 `auction-rooms`인 것과 `auctions`인 것이 갈린다 — 명세 그대로 따른다)
// - 목록 조회:   GET    /api/v1/auction-rooms
// - 곧 시작:     GET    /api/v1/auction-rooms/upcoming
// - 상세 조회:   GET    /api/v1/auction-rooms/{auctionId}
// - 생성:        POST   /api/v1/auction-rooms
// - 수정:        PATCH  /api/v1/auction-rooms/{auctionId}
// - 삭제:        DELETE /api/v1/auction-rooms/{auctionId}      (204)
// - 방송 시작:   POST   /api/v1/auction-rooms/{auctionId}/start
// - 입장:        POST   /api/v1/auction-rooms/{auctionId}/join
// - 카테고리:    GET    /api/v1/auctions/categories        ← `auctions` 쪽이다
//
// 반면 입찰·연장·예측·스냅샷은 `/api/v1/auctions/{auctionId}/...`다 (`bids.ts`·`predictions.ts`).
//
// ⚠️ **`GET /api/v1/auctions?categoryId=...`(「카테고리별 경매방 목록 조회」)는 폐기됐다** —
// 명세에 남아 있지만 BE가 개발하지 않기로 확인했다(2026-07-30). 목록 조회 하나가 카테고리·상태·
// 검색어·커서·`serverTime`·`counts`를 전부 지원하므로 중복이었다. 카테고리별 조회는
// `getAuctionRooms({ categoryId })`로 하고, 표시명은 카테고리 목록의 `code`를 응답의
// `categoryCode`와 이어 쓴다(`stores/category.ts`의 `idByCode`가 그 역방향이다).

import { http } from '@/shared/api/http'
import type { CursorPage, CursorPageQuery, Id, IsoDateTime } from '@/shared/api/types'

export const AUCTION_ROOMS_PATH = '/auction-rooms'

/** 카테고리만 `auctions` 아래다 (명세 「경매방 카테고리 목록 조회」 경로 그대로) */
export const AUCTION_CATEGORIES_PATH = '/auctions/categories'

/**
 * 상품 카테고리 1건. **`categoryId`와 `code`가 둘 다 필요하다** —
 * 목록 필터 쿼리는 `categoryId`(Long)인데 목록 응답·생성·수정 요청은 `code` 문자열이라,
 * 둘을 잇는 유일한 곳이 이 응답이다.
 */
export interface AuctionCategory {
  categoryId: number
  /** `product_category.code` — UNIQUE. 전송·매칭 키 */
  code: string
  /** 화면 표시용 (VARCHAR(50)) */
  name: string
  /** 정렬 순서. 서버가 오름차순으로 주지만 FE도 한 번 더 정렬한다 */
  displayOrder: number
}

/**
 * 경매방 상태. **명세 「경매방 목록 조회」의 `status` 필터가 정의한 3값이 단일 소스**다.
 * (`docs/04` §6.1이 "값 목록 미확인 · 최우선 확인 대상"으로 남겨 둔 항목의 답이다.)
 *
 * ⚠️ 단 「경매 상태 스냅샷」(WS-03) 예시만 `"auctionStatus": "OPEN"`을 쓴다 —
 *    명세 내부 불일치이므로 `bids.ts`의 스냅샷 타입은 좁히지 않고 string으로 둔다.
 */
export type AuctionStatus = 'WAITING' | 'LIVE' | 'ENDED'

/** `auction_room.start_type` — 예약 시각은 **안내용**이고 실제 시작은 판매자 수동이다 */
export type StartType = 'IMMEDIATE' | 'SCHEDULED'

/** 화면(`mocks/rooms.ts`의 `RoomState`)에서 쓰는 소문자 상태로의 매핑 */
export const ROOM_STATE_BY_AUCTION_STATUS: Record<AuctionStatus, 'waiting' | 'live' | 'ended'> = {
  WAITING: 'waiting',
  LIVE: 'live',
  ENDED: 'ended',
}

// ── 목록 (ROOM-02) ──

export interface AuctionRoomListQuery extends CursorPageQuery {
  status?: AuctionStatus
  /**
   * 목록 **필터**는 `categoryId`(Long)인데 목록 **응답**과 생성·수정 요청은 코드 문자열이다.
   * 식별자가 두 종류인 건 그대로지만, 「경매방 카테고리 목록 조회」(`GET /auctions/categories`)가
   * `categoryId`·`code`를 한 응답에 주므로 대응이 가능하다.
   */
  categoryId?: number
  /** 경매방 제목 또는 상품명 검색어 */
  keyword?: string
}

/**
 * 탭에 붙는 상태별 **전체** 방 개수 (현재 페이지 개수가 아니다).
 *
 * 명세 「상태별 개수 집계 규칙」:
 * - `LIVE`·`WAITING`을 **항상 둘 다** 준다. 조건에 맞는 방이 없으면 `0`
 * - **`ENDED`는 없다** — 와이어프레임 종료 탭에 숫자가 없어서 요청에서 뺐다
 * - 요청의 `status`는 **집계에서 무시**한다 (`status=LIVE`로 조회해도 둘 다 온다)
 * - 요청의 `categoryId`·`keyword`는 **집계에도 적용**한다
 * - `cursor`·`size`는 집계에 영향을 주지 않는다
 */
export interface AuctionRoomCounts {
  LIVE: number
  WAITING: number
}

/**
 * 목록 응답. `CursorPage`에 `serverTime`·`counts`가 붙은 모양이라 전용 타입을 둔다.
 *
 * ⚠️ `counts`는 **선택으로 남긴다.** 서버는 구현됐지만(ROOM-02, S15P11A704-457) 배포 시점이
 *    엇갈리면 없는 응답이 올 수 있다 — 없으면 탭 숫자를 안 그리면 되고, 화면이 깨지면 안 된다.
 */
export interface AuctionRoomPage extends CursorPage<AuctionRoomSummary> {
  /**
   * 응답 생성 시점의 **서버** 시각 (명세 2026-07-29 개정으로 추가).
   * 목록 카드의 카운트다운을 단말 시계가 아니라 이 값 기준으로 계산한다 — AGENTS.md 규칙 5.
   * 홈·목록에는 TIME_SYNC 소켓 채널이 없어서 **여기가 유일한 서버 시각 출처**다.
   *
   * ⚠️ `counts`와 같은 이유로 **선택**이다. 명세는 항상 준다고 하지만 배포 시점이 엇갈리면
   * 없는 응답이 올 수 있고, 그때 실패 모드가 `counts`보다 나쁘다 — 모든 카드의 남은 시간이
   * `--:--`가 된다. 타입을 필수로 두면 `offsetFrom`의 undefined 처리가 죽은 코드처럼 보인다.
   */
  serverTime?: IsoDateTime
  counts?: AuctionRoomCounts
}

/** 목록 카드 1건. 비크리 원칙상 금액은 하한가만, 나머지는 집계값뿐이다 */
export interface AuctionRoomSummary {
  auctionId: Id
  title: string
  thumbnailUrl: string | null
  /** 카드 배지·색감용 표준 카테고리 코드 (예: `ELECTRONICS`). 표시명은 카테고리 목록 API가 준다 */
  categoryCode: string
  /** 서버가 마스킹한 판매자 닉네임 (예: "서연***") */
  sellerNickname: string
  reservePrice: number
  bidCount: number
  /**
   * 예측 베팅 참여 인원 (입찰 참여자 수와 다르다).
   * ⚠️ 목록에 **입찰 참여자 수는 없다** — 카드의 "참여 N명"이 어느 쪽인지는 미확정(S15P11A704-385).
   */
  predictionParticipantCount: number
  scheduledAt: IsoDateTime | null
  /** 서버 권위 **예정** 마감 시각. 시작 전에는 null이다 (BID-05 — 남은 시간은 이 값 기준으로 표시만 한다) */
  endAt: IsoDateTime | null
  /** 낙찰된 종료 경매의 최종 낙찰가. 진행 중·대기·유찰은 null */
  soldPrice: number | null
  /** **실제** 종료 시각. 예정 마감 시각(`endAt`)과 다른 값이다. 종료 전에는 null */
  endedAt: IsoDateTime | null
  status: AuctionStatus
  viewerCount: number
}

// ── 상세 (ROOM-03) ──

export interface AuctionRoomProduct {
  productId: Id
  title: string
  description: string
  /** ⚠️ 단일 이미지다. DB의 `product_image`(다중·정렬·대표 지정)와 어긋난다 */
  imageUrl: string
}

export interface AuctionRoomSeller {
  /** 마스킹된 닉네임 */
  nickname: string
  /** ⚠️ DB 스키마에 판매자 평점 컬럼이 없다 — 출처 확인 필요 */
  rating: number
}

export interface AuctionRoomDetail {
  /**
   * 응답 생성 시점의 **서버** 시각 (명세 2026-07-29 개정으로 추가, 구현 S15P11A704-458).
   * 딥링크로 상세만 받은 화면도 이 값으로 카운트다운 기준을 세운다 — 목록을 거치지 않으면
   * `room.serverOffset`이 비어 있어 남은 시간이 `--:--`가 된다 (규칙 5).
   */
  serverTime?: IsoDateTime
  auctionId: Id
  title: string
  status: AuctionStatus
  reservePrice: number
  scheduledAt: IsoDateTime | null
  durationMinutes: number
  viewerCount: number
  /**
   * 현재 인증 회원이 이 방의 판매자인가. 서버가 회원 ID와 판매자 ID를 비교해 계산한다.
   * 비로그인·타인은 `false`. `canEnter`와 같은 성격의 서버 계산 플래그다.
   * (`seller`는 마스킹 닉네임뿐이라 FE가 직접 비교할 수 없어서 이 값이 유일한 판정 근거다.)
   */
  isSeller: boolean
  /** 대기방 알림 신청자 수(중복 제거). **판매자·관리자에게만** 오고 그 외에는 null */
  notificationCount: number | null
  product: AuctionRoomProduct
  seller: AuctionRoomSeller
  /** 서버가 상태·접근 정책으로 계산한 입장 가능 여부 */
  canEnter: boolean
}

// ── 생성·수정 (ROOM-01·ROOM-04) ──

export interface AuctionProductInput {
  title: string
  description: string
  /** 카테고리 **코드** 문자열 (예: `ELECTRONICS`) */
  category: string
  /** 미리 업로드한 단일 이미지 URL — `POST /product-images`가 발급한 값이어야 한다(서버가 소유자까지 검증한다) */
  imageUrl: string
}

export interface AuctionConditionInput {
  reservePrice: number
  durationMinutes: number
  startType: StartType
  /** `SCHEDULED`일 때만 필수. `IMMEDIATE`면 생략한다 */
  scheduledAt?: IsoDateTime
}

export interface CreateAuctionRoomRequest {
  product: AuctionProductInput
  auction: AuctionConditionInput
}

export interface CreateAuctionRoomResponse {
  auctionId: Id
  productId: Id
  sellerId: Id
  /** 시작 방식과 무관하게 생성 직후는 항상 `WAITING`이다 */
  status: AuctionStatus
  reservePrice: number
  durationMinutes: number
  startType: StartType
  scheduledAt: IsoDateTime | null
  createdAt: IsoDateTime
}

export interface UpdateAuctionRoomRequest {
  product?: Partial<AuctionProductInput>
  auction?: Partial<AuctionConditionInput>
}

export interface UpdateAuctionRoomResponse {
  auctionId: Id
  productId: Id
  status: AuctionStatus
  product: AuctionProductInput
  auction: AuctionConditionInput
  updatedAt: IsoDateTime
}

// ── 방송 시작 (ROOM-05 / LIVE-02) ──

export interface StartAuctionRoomResponse {
  auctionId: Id
  status: AuctionStatus
  startedAt: IsoDateTime
  /** `startedAt + durationMinutes`를 **서버가** 계산한 값 */
  endAt: IsoDateTime
  durationMinutes: number
  /** 응답 시점의 서버 시각 — `serverOffset` 초기값으로 쓸 수 있다 (BID-05) */
  serverTime: IsoDateTime
}

// ── 판매자 경매 종료 (ROOM-09) ──

export interface SellerEndAuctionResponse {
  auctionId: Id
  status: AuctionStatus
  /** 항상 `HOST_LEFT` — 판매자 종료는 유찰이다 */
  resultType: string
  endedAt: IsoDateTime
  /** 해제된 입찰 홀드 건수·합계. 판매자에게 「몇 명의 입찰이 풀렸는지」가 종료의 결과다 */
  releasedBidCount: number
  releasedBidAmount: number
  refundedPredictionCount: number
}

// ── 입장 (ROOM-07) ──

export interface AuctionRoomPermissions {
  chat: boolean
  bid: boolean
  prediction: boolean
}

export interface AuctionRoomDestinations {
  room: string
  chat: string
  /**
   * 접속자 수 구독 (명세 2026-07-29 개정으로 추가). `StompViewerCountPublisher`가 이 destination으로
   * 활성 STOMP 세션 수를 발행한다 — 구독 자체가 시청자 등록이기도 하다(S15P11A704-249).
   *
   * **선택이다.** 471 이전 배포의 서버는 이 필드를 주지 않으므로, 받는 쪽은
   * `destinations.room + '/viewers'` 폴백을 유지해야 한다 — 필수로 두면 그 폴백이
   * 도달 불가 코드로 보여 지워지고, 그때 `undefined`가 구독 인자로 들어가
   * 시청자 수 구독이 조용히 죽는다.
   */
  viewers?: string
}

export interface JoinAuctionRoomResponse {
  /**
   * 입장 판정 후 응답을 만든 **서버** 시각 (명세 2026-07-29 개정으로 추가).
   * 대기방 카운트다운의 기준이다 — 소켓 `TIME_SYNC`가 오기 전 가장 정확한 서버 시각 출처다
   * (없으면 목록·상세에서 받아둔 `room.serverOffset`이 대신 선다, S15P11A704-265).
   */
  serverTime?: IsoDateTime
  auctionId: Id
  roomStatus: AuctionStatus
  /** 대기방 응답에만 있는 안내용 시각 */
  scheduledAt?: IsoDateTime
  permissions: AuctionRoomPermissions
  /** 대기방 응답에만 있는 구독 destination */
  destinations?: AuctionRoomDestinations
  /** 진행 중 입장 응답에만 있는 AI-03 요약 제공 여부 */
  catchUpSummaryAvailable?: boolean
}

// ── 중간 입장 요약 (AI-03) ──

export interface CatchUpSummary {
  auctionId: Id
  generatedAt: IsoDateTime
  expiresAt: IsoDateTime
  bidSummary: {
    bidCount: number
    participantCount: number
  }
  extensionSummary: {
    extensionCount: number
    extendedMinutes: number
  }
  /** STT가 확보됐을 때만 채워지는 판매자 발화 요약. RoomEvent 폴백이면 빈 배열이다. */
  sellerMentions: string[]
  serverTime: IsoDateTime
  endAt: IsoDateTime | null
  sourceType: 'ROOM_EVENT_ONLY' | 'STT_AND_ROOM_EVENT'
}

// ── 곧 시작 (ROOM-08) ──

export interface UpcomingAuctionRoom {
  auctionId: Id
  productTitle: string
  imageUrl: string | null
  scheduledAt: IsoDateTime
  waitingCount: number
  status: AuctionStatus
}

export interface UpcomingAuctionRoomsResponse {
  items: UpcomingAuctionRoom[]
  totalCount: number
  /** 최대 5개만 반환하고 초과분은 이 값으로 알려준다 */
  hiddenCount: number
}

// ── 엔드포인트 ──

/** 화면의 소문자 상태 → 명세 `status` 필터 값. 목록 화면의 탭이 이걸로 질의·집계를 고른다 */
export const AUCTION_STATUS_BY_ROOM_STATE = {
  live: 'LIVE',
  waiting: 'WAITING',
  ended: 'ENDED',
} as const

/** 응답이 `items`로 감싸져 온다 (목록 API들과 달리 커서가 없다) */
interface AuctionCategoriesEnvelope {
  items: AuctionCategory[]
}

/**
 * ROOM-02 경매방 카테고리 목록 (`GET /auctions/categories`).
 * 변경이 잦지 않아 명세도 "프론트에서 캐시할 수 있다"고 적었다 — 캐시는 `stores/category.ts`가 한다.
 */
export async function getAuctionCategories(): Promise<AuctionCategory[]> {
  const { data } = await http.get<AuctionCategoriesEnvelope>(AUCTION_CATEGORIES_PATH)
  // 명세는 displayOrder 오름차순을 보장하지만, 어긋나도 화면 순서가 흔들리지 않게 한 번 더 세운다
  return [...data.items].sort((a, b) => a.displayOrder - b.displayOrder)
}

/** ROOM-02 경매방 목록. 기본 8건, LIVE는 마감 임박순 / WAITING은 예정 시각순 / ENDED는 종료 최신순 */
export async function getAuctionRooms(query: AuctionRoomListQuery = {}): Promise<AuctionRoomPage> {
  const { data } = await http.get<AuctionRoomPage>(AUCTION_ROOMS_PATH, { params: query })
  return data
}

/** ROOM-08 지금부터 1시간 이내 시작 예정인 대기방 (최대 5개) */
export async function getUpcomingAuctionRooms(limit = 5): Promise<UpcomingAuctionRoomsResponse> {
  const { data } = await http.get<UpcomingAuctionRoomsResponse>(`${AUCTION_ROOMS_PATH}/upcoming`, {
    params: { limit },
  })
  return data
}

/** ROOM-03 경매방 상세 */
export async function getAuctionRoom(auctionId: Id): Promise<AuctionRoomDetail> {
  const { data } = await http.get<AuctionRoomDetail>(`${AUCTION_ROOMS_PATH}/${auctionId}`)
  return data
}

/** ROOM-01 상품 + 경매방 동시 생성 (서버 한 트랜잭션). 생성 직후 상태는 항상 `WAITING` */
export async function createAuctionRoom(
  request: CreateAuctionRoomRequest,
): Promise<CreateAuctionRoomResponse> {
  const { data } = await http.post<CreateAuctionRoomResponse>(AUCTION_ROOMS_PATH, request)
  return data
}

/** ROOM-04 수정 — `WAITING` 상태에서만 가능하다 */
export async function updateAuctionRoom(
  auctionId: Id,
  request: UpdateAuctionRoomRequest,
): Promise<UpdateAuctionRoomResponse> {
  const { data } = await http.patch<UpdateAuctionRoomResponse>(
    `${AUCTION_ROOMS_PATH}/${auctionId}`,
    request,
  )
  return data
}

/** ROOM-04 삭제 — 시작 후·참여 이력이 있으면 409 */
export async function deleteAuctionRoom(auctionId: Id): Promise<void> {
  await http.delete<void>(`${AUCTION_ROOMS_PATH}/${auctionId}`)
}

/**
 * ROOM-05 방송 시작 (판매자 수동).
 * 예약 시각(`scheduledAt`)은 안내용이라 그 시각이 되어도 자동으로 시작하지 않는다.
 */
export async function startAuctionRoom(auctionId: Id): Promise<StartAuctionRoomResponse> {
  const { data } = await http.post<StartAuctionRoomResponse>(
    `${AUCTION_ROOMS_PATH}/${auctionId}/start`,
  )
  return data
}

/**
 * ROOM-09 판매자 경매 종료 (S15P11A704-687).
 *
 * **방송 송출 종료(LIVE-02 `endLiveStream`)와 다른 API다.** 그쪽은 `live_broadcast`와 OpenVidu
 * 세션만 닫아서, 그것만 부르면 방송은 끊겼는데 경매 타이머가 계속 돌고 입찰도 계속 접수된다.
 * 둘 다 불러야 방송과 경매가 함께 끝난다.
 *
 * 낙찰은 나지 않는다 — 마감까지 간 정상 종료가 아니라 `HOST_LEFT` 유찰이고 입찰 홀드는
 * 전액 해제된다. 참여자에게는 `AUCTION_NO_SALE`(reason `HOST_LEFT`)이 나가고, 그 화면은
 * 이미 「판매자 이탈로 종료됐어요」로 그려진다(`RevealOverlay`).
 *
 * 경로가 `auction-rooms`가 아니라 `auctions`다 — 명세가 그렇다(연장·입찰과 같은 묶음).
 */
export async function endAuctionAsSeller(auctionId: Id): Promise<SellerEndAuctionResponse> {
  const { data } = await http.post<SellerEndAuctionResponse>(`/auctions/${auctionId}/end`)
  return data
}

/** ROOM-07 입장 — 권한과 구독 destination을 받는다 (대기방/라이브 응답 모양이 다르다) */
export async function joinAuctionRoom(auctionId: Id): Promise<JoinAuctionRoomResponse> {
  const { data } = await http.post<JoinAuctionRoomResponse>(
    `${AUCTION_ROOMS_PATH}/${auctionId}/join`,
  )
  return data
}

/**
 * AI-03 방송 중간 입장 요약. 생성 가능한 이벤트·발화가 없을 때 서버는 204를 반환하며,
 * 핵심 시청 흐름은 그대로 계속된다.
 */
export async function getCatchUpSummary(auctionId: Id): Promise<CatchUpSummary | null> {
  const response = await http.get<CatchUpSummary>(
    `${AUCTION_ROOMS_PATH}/${auctionId}/catch-up-summary`,
  )
  return response.status === 204 ? null : response.data
}
