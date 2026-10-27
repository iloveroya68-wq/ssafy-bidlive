import { defineStore } from 'pinia'
import type { RoomState } from '@/mocks/rooms'
import {
  ROOM_STATE_BY_AUCTION_STATUS,
  createAuctionRoom,
  getAuctionRoom,
  getAuctionRooms,
  getUpcomingAuctionRooms,
  type AuctionRoomCounts,
  type AuctionRoomDetail,
  type AuctionRoomSummary,
  type AuctionStatus,
  type UpcomingAuctionRoom,
} from '@/shared/api/rooms'
import { toApiError } from '@/shared/api/errors'

export type { RoomState }
export type { AuctionRoomSummary }

/**
 * 화면이 쓰는 방 상태는 소문자다(`?state=live` 딥링크가 정본이라 바꿀 수 없다).
 * 서버는 대문자 `AuctionStatus`를 주므로 경계에서만 옮긴다.
 */
export const roomStateOf = (room: AuctionRoomSummary): RoomState =>
  ROOM_STATE_BY_AUCTION_STATUS[room.status]

/** 카운트다운·정렬용 epoch ms. 서버는 ISO를 주고 시각 계산은 절대 시각으로만 한다 (BID-05) */
export function endAtMs(room: AuctionRoomSummary): number | undefined {
  if (room.endAt === null) return undefined
  const ms = Date.parse(room.endAt)
  return Number.isNaN(ms) ? undefined : ms
}

/** product.condition_status — nullable + CHECK IN ('NEW','LIKE_NEW','USED','DAMAGED') */
export type ConditionStatus = 'NEW' | 'LIKE_NEW' | 'USED' | 'DAMAGED'

/** auction_room.start_type — CHECK IN ('IMMEDIATE','SCHEDULED') */
export type StartType = 'IMMEDIATE' | 'SCHEDULED'

/**
 * product_image 한 장.
 * - `sortOrder`는 1부터 연속 (`sort_order > 0` + `UNIQUE(product_id, sort_order)`)
 * - `isThumbnail`은 전체에서 최대 1장만 true (`uq_product_single_thumbnail`, 0장도 허용)
 */
export interface ProductImageDraft {
  /** 로컬 미리보기 식별자 (서버 전송 대상 아님) */
  id: string
  imageUrl: string
  sortOrder: number
  isThumbnail: boolean
}

/**
 * ROOM-01 방 개설 요청 페이로드.
 * `product` + `product_image[]` + `auction_room` 3개 테이블을 한 번에 만드는 형태로 가정한다
 * (단일 요청인지 단계별인지는 백엔드 확인 필요 — docs/04 §6.4).
 * `moderation_status`는 서버가 정하므로 페이로드에 없다 (등록 직후 PENDING).
 */
export interface CreateRoomPayload {
  product: {
    /** product.title VARCHAR(100) NOT NULL */
    title: string
    /** product.description TEXT NOT NULL — 빈 문자열 불가 */
    description: string
    /** product_category.code */
    categoryCode: string
    /** nullable — "선택 안 함"이 유효하다 */
    conditionStatus: ConditionStatus | null
    images: ProductImageDraft[]
  }
  room: {
    /** auction_room.reserve_price > 0 */
    reservePrice: number
    /** auction_room.duration_minutes > 0 */
    durationMinutes: number
    startType: StartType
    /** ck_auction_room_schedule: IMMEDIATE면 반드시 null, SCHEDULED면 반드시 값 (ISO 문자열) */
    scheduledAt: string | null
  }
}

/**
 * 개설 결과는 **서버가 준 id뿐**이다.
 *
 * 예전에는 `startType`도 함께 돌려줬는데, 화면이 그걸로 이동처를 갈랐기 때문이다
 * (`IMMEDIATE`면 `/live`). 개설 직후 상태는 시작 방식과 무관하게 항상 `WAITING`이라
 * 그 분기 자체가 사라졌고(무조건 콘솔), 남은 필드는 아무도 읽지 않는 값이 됐다.
 * 되살릴 일이 있으면 그때 `getAuctionRoom` 상세에서 읽는 게 맞다 — 개설 응답을
 * 들고 다니면 그 값이 언제 찍힌 것인지 화면에서 알 수 없다.
 */
export interface CreateRoomResult {
  roomId: number
}

/** 한 번에 받을 방 수. 명세 기본값 8을 그대로 쓴다 (최대 20) */
const ROOM_PAGE_SIZE = 8

/**
 * 요청 일련번호. **탭·칩을 빠르게 바꾸면 먼저 보낸 조회가 나중에 도착한다** —
 * 그대로 쓰면 방금 고른 탭 자리에 이전 탭 결과가 그려진다. 응답이 오면 자기 번호가
 * 아직 최신인지 보고, 아니면 조용히 버린다.
 *
 * `loadMoreRooms`에도 같은 번호를 쓴다 — 다음 페이지를 기다리는 사이 탭이 바뀌면
 * **다른 질의의 결과가 이어 붙는다.** 그게 제일 알아채기 어려운 형태의 오염이다.
 */
let requestSeq = 0

/**
 * 대표 이미지 한 장을 고른다. 명세의 `product.imageUrl`은 **단일 값**이라
 * 여러 장을 올려도 하나만 나간다 — 대표로 지정한 것, 없으면 첫 장.
 *
 * Step1이 넣는 `blob:` URL은 화면이 `useProductImageUpload`로 서버 URL로 바꿔치기하고,
 * "최소 1장"도 화면 검증이 막는다(S15P11A704-443) — 그래서 여기 도달하는 값은 서버 URL이다.
 * 그래도 폴백 `''`는 남긴다: 검증을 우회한 경로가 생기면 **서버가 400으로 거절해 드러나야 한다.**
 */
function thumbnailUrlOf(images: ProductImageDraft[]): string {
  return images.find((image) => image.isThumbnail)?.imageUrl ?? images[0]?.imageUrl ?? ''
}

/** 목록 조회 조건. 서버가 거른다 — 클라이언트에서 다시 거르지 않는다 */
export interface RoomListQuery {
  status?: AuctionStatus
  /** 「경매방 카테고리 목록 조회」가 주는 `categoryId` (S15P11A704-397의 `idByCode`) */
  categoryId?: number
  keyword?: string
}

/** 같은 목록을 가리키는 조건인가 — "지금 목록을 비울지" 판단에 쓴다 */
const isSameQuery = (a: RoomListQuery, b: RoomListQuery): boolean =>
  a.status === b.status && a.categoryId === b.categoryId && a.keyword === b.keyword

/**
 * 같은 **집계** 조건인가 — "지금 카운트를 비울지" 판단에 쓴다.
 *
 * 목록보다 **좁다**: `status`를 안 본다. 명세가 "요청의 `status`는 `counts` 집계에서
 * 무시한다"고 못 박았으므로, 진행중 → 종료처럼 상태만 바꾸면 **받아올 숫자가 직전과 같다.**
 * 그걸 비우면 같은 값이 사라졌다 돌아오는 깜빡임만 생긴다 (카드는 실제로 바뀌니 비우는 게 맞다).
 */
const isSameCountScope = (a: RoomListQuery, b: RoomListQuery): boolean =>
  a.categoryId === b.categoryId && a.keyword === b.keyword

/**
 * 목록 응답의 `serverTime`으로 서버-단말 시각 차이를 구한다.
 * **로컬 시계를 여기서 딱 한 번 쓴다** — 이 지점이 동기화 지점이고, 이후 남은 시간은
 * `endAt + serverOffset`으로만 계산한다 (규칙 5, live 스토어의 TIME_SYNC와 같은 방식).
 *
 * 파싱이 안 되면 `null`이다. 0을 반환하면 "서버와 시각이 같다"는 사실을 지어내게 된다.
 */
export function offsetFrom(serverTime: string | undefined): number | null {
  if (serverTime === undefined) return null
  const serverMs = Date.parse(serverTime)
  return Number.isNaN(serverMs) ? null : serverMs - Date.now()
}

// 오너: FE1 (ROOM-01~07)
export const useRoomStore = defineStore('room', {
  state: () => ({
    /**
     * **현재 질의의 누적 페이지**다. 예전에는 "모든 방"이었는데(390),
     * 그때는 탭 카운트를 세려고 커서를 끝까지 따라갔다 — 방이 늘면 무너지는 방식이었다.
     * 이제 상태·카테고리·검색어를 서버가 거르고 여기에는 그 결과만 쌓인다.
     */
    rooms: [] as AuctionRoomSummary[],
    /** 서버가 준 불투명 커서. FE는 내부를 해석하지 않고 그대로 되돌려준다 */
    nextCursor: null as string | null,
    hasNext: false,
    loading: false,
    loaded: false,
    loadError: null as string | null,
    /** 지금 `rooms`가 대응하는 조건. `loadMoreRooms`가 같은 조건으로 이어 받는다 */
    activeQuery: {} as RoomListQuery,
    /**
     * 탭에 붙는 상태별 **전체** 개수 (S15P11A704-417). 현재 페이지 개수가 아니다 —
     * 스크롤로 다음 장을 받아도 이 값은 변하지 않는다.
     *
     * **아직 못 받았으면 null이다.** 0으로 초기화하면 조회 중에 "방이 없다"로 읽힌다.
     * 서버 구현이 아직이라 실 응답에 `counts`가 없을 수도 있고, 그때도 null로 남는다.
     */
    counts: null as AuctionRoomCounts | null,
    /**
     * 서버-단말 시각 차이(ms). 목록 응답의 `serverTime`에서 계산한다 (명세 2026-07-29 개정).
     *
     * 목록·홈 화면은 소켓을 붙이지 않아 `live.serverOffset`(TIME_SYNC)을 쓸 수 없었고,
     * 그래서 카드 카운트다운이 단말 시계로 돌고 있었다. 이 값이 그 유일한 서버 시각 출처다.
     * **아직 못 받았으면 null**이고, 그때 `useCountdown`은 남은 시간을 지어내지 않는다 (규칙 5).
     */
    serverOffset: null as number | null,
    /**
     * 방 상세 캐시(id → 상세). **목록이 "현재 페이지"가 되면서 필요해졌다** —
     * 라이브·결과·유찰 화면은 딥링크로 바로 들어올 수 있고, 그 방이 목록 페이지에
     * 없으면 `rooms.find`가 못 찾는다. 예전에는 목록이 전부라서 우연히 맞았을 뿐이다.
     */
    /**
     * ROOM-08 「곧 시작해요」 스트립 항목. 1시간 이내 시작 예정인 대기방을
     * `scheduledAt` 오름차순 최대 5개로 **서버가 잘라서** 준다 — FE는 다시 자르지도 세우지도 않는다.
     */
    upcoming: [] as UpcomingAuctionRoom[],
    /** 5개를 넘겨 잘린 개수. 스트립 끝 「더보기 +N개」 카드의 N이다 */
    upcomingHidden: 0,
    details: {} as Record<number, AuctionRoomDetail>,
    detailError: null as string | null,
    creating: false,
  }),
  getters: {
    /** 딥링크로 들어온 방의 상세. 아직 못 받았으면 undefined */
    detailOf: (state) => (auctionId: number) => state.details[auctionId],
    /**
     * 목록에서 받아둔 그 방의 **예정 마감 시각**(epoch ms). 모르면 null (S15P11A704-523).
     *
     * 소켓 `TIME_SYNC` 전 구간에 라이브·콘솔 타이머가 쓸 폴백이다. **상세(ROOM-03)에는 `endAt`이
     * 없어서** 목록이 유일한 REST 출처다 — 목록을 거쳐 들어온 방만 값이 있고, 딥링크로 바로
     * 들어오면 비어 있다. 그때는 `--:--`가 맞다(규칙 5 — 마감 시각을 지어내지 않는다).
     */
    deadlineOf:
      (state) =>
      (auctionId: number | null): number | null => {
        if (auctionId === null) return null
        const summary = state.rooms.find((r) => r.auctionId === auctionId)
        return summary === undefined ? null : (endAtMs(summary) ?? null)
      },
  },
  actions: {
    /**
     * 서버 시각을 받았다고 알린다 — `serverOffset`을 쓰는 **유일한** 자리다 (S15P11A704-265).
     *
     * `serverTime`은 목록·상세 응답 모두에서 **선택 필드**다(배포 시점이 엇갈리면 빠질 수 있다).
     * 없을 때 `null`로 덮으면 이미 갖고 있던 멀쩡한 오프셋이 지워져 화면 전체가 `--:--`가 된다 —
     * 30초 전 값이 부정확한 것보다 나쁘다. 그래서 **없으면 유지**한다.
     *
     * 상세를 스토어 밖에서 받는 화면(콘솔의 `getAuctionRoom` 직접 호출)도 이 액션으로 들어온다.
     * 판정식을 호출부마다 다시 적으면 「없으면 유지」가 한쪽에서만 바뀐다.
     */
    noteServerTime(serverTime: string | undefined): void {
      this.serverOffset = offsetFrom(serverTime) ?? this.serverOffset
    },

    /**
     * ROOM-02 방 목록 **첫 페이지** (`GET /auction-rooms`).
     * 조건이 바뀔 때마다(탭·칩·검색어) 부른다 — 목록을 갈아엎는 조회다.
     *
     * 정렬은 서버가 한다: `LIVE`는 `endAt` 오름차순, `WAITING`은 `scheduledAt` 오름차순,
     * `ENDED`는 `endedAt` 내림차순. **클라이언트에서 다시 정렬하지 않는다** — 겹치면
     * 페이지 경계에서 순서가 어긋난다(서버는 전체 기준, FE는 받은 것 기준으로 세우므로).
     */
    async loadRooms(query: RoomListQuery = {}): Promise<void> {
      const token = (requestSeq += 1)
      // **조건이 바뀌면 지금 목록을 먼저 비운다.** 응답이 올 때까지 두면 종료 탭을 눌러도
      // 진행중 카드가 그대로 보인다 — 사용자에겐 "탭이 안 먹었다"가 아니라 **잘못된 탭 내용**이다.
      // 같은 조건을 다시 받는 경우(홈 재진입 등)는 비우지 않는다. 멀쩡한 화면을 깜빡이게 할 뿐이다.
      if (!isSameQuery(this.activeQuery, query)) {
        this.rooms = []
        this.nextCursor = null
        this.hasNext = false
        this.loaded = false
      }
      // 카운트는 **집계 조건**이 바뀔 때만 비운다. 남겨 두면 전자기기 칩을 켠 순간
      // 전체 기준 숫자가 잠깐 보이고 그건 카드 개수와 안 맞는다 — 반대로 상태만 바뀌면
      // 숫자는 그대로여야 한다 (위 `isSameCountScope` 주석).
      if (!isSameCountScope(this.activeQuery, query)) {
        this.counts = null
      }
      this.activeQuery = { ...query }
      this.loading = true
      this.loadError = null
      try {
        const page = await getAuctionRooms({ ...query, size: ROOM_PAGE_SIZE })
        // 그 사이 탭이 바뀌었으면 이 결과는 남의 것이다
        if (token !== requestSeq) return
        this.rooms = page.items
        this.nextCursor = page.nextCursor
        this.hasNext = page.hasNext
        // 서버가 안 주면 null로 남긴다 — 화면은 숫자를 안 그린다
        this.counts = page.counts ?? null
        this.noteServerTime(page.serverTime)
        this.loaded = true
      } catch (error) {
        if (token !== requestSeq) return
        this.loadError = toApiError(error).message
      } finally {
        if (token === requestSeq) this.loading = false
      }
    },

    /**
     * 다음 페이지를 **이어 붙인다** (무한 스크롤).
     * 실패해도 이미 보이는 목록은 지우지 않는다 — 다시 스크롤하면 재시도된다.
     */
    async loadMoreRooms(): Promise<void> {
      if (this.loading || !this.hasNext || this.nextCursor === null) return
      const token = requestSeq
      this.loading = true
      this.loadError = null
      try {
        const page = await getAuctionRooms({
          ...this.activeQuery,
          cursor: this.nextCursor,
          size: ROOM_PAGE_SIZE,
        })
        // 기다리는 사이 조건이 바뀌었다면 **이어 붙이면 안 된다** (다른 탭의 방이 섞인다)
        if (token !== requestSeq) return
        this.rooms = [...this.rooms, ...page.items]
        this.nextCursor = page.nextCursor
        this.hasNext = page.hasNext
        // 매 응답이 자기 시점의 `serverTime`을 주므로 최신 값으로 갱신한다 (오프셋 드리프트 보정)
        this.noteServerTime(page.serverTime)
        // `counts`는 **전체** 개수라 페이지를 더 받아도 그대로다 — 덮어쓰지 않는다
      } catch (error) {
        if (token !== requestSeq) return
        this.loadError = toApiError(error).message
      } finally {
        if (token === requestSeq) this.loading = false
      }
    },

    /**
     * ROOM-08 곧 시작 경매 (`GET /auction-rooms/upcoming`).
     *
     * **부르는 쪽은 스트립을 그리는 화면이다**(`RoomListView`). 예전에는 `loadRooms`가 `status=LIVE`일 때
     * 같이 걸었는데, 그러면 스트립이 없는 홈(`MainView`)에서도 요청이 나가고 칩·검색어를 만질 때마다
     * 같은 내용을 다시 받았다 — 응답이 질의 조건과 무관하다는 점에서 둘 다 낭비다.
     *
     * **실패하면 조용히 비운다.** 스트립은 목록 위에 얹힌 보조 정보라, 여기서 `loadError`를 세우면
     * 멀쩡한 카드 그리드 위에 빨간 문구가 뜨고 사용자는 목록이 깨진 줄 안다.
     * 순서 경쟁도 다루지 않는다 — 응답이 질의 조건과 무관해서 늦게 온 결과도 같은 내용이다.
     */
    async loadUpcoming(): Promise<void> {
      try {
        const { items, hiddenCount } = await getUpcomingAuctionRooms()
        this.upcoming = items
        this.upcomingHidden = hiddenCount
      } catch {
        this.upcoming = []
        this.upcomingHidden = 0
      }
    },

    /**
     * ROOM-03 방 상세 — 딥링크로 들어온 화면이 제목·하한가를 얻는 경로다.
     * 한 번 받으면 캐시한다(같은 화면 안에서 여러 컴포넌트가 물어본다).
     */
    async loadRoomDetail(auctionId: number): Promise<void> {
      if (this.details[auctionId] !== undefined) return
      this.detailError = null
      try {
        const detail = await getAuctionRoom(auctionId)
        this.details[auctionId] = detail
        // 딥링크로 상세만 받은 화면(라이브·결과·유찰)은 목록을 거치지 않아 오프셋이 비어 있다.
        // 그대로 두면 카운트다운이 `--:--`가 된다 — 상세 응답의 `serverTime`이 그 자리를 채운다.
        this.noteServerTime(detail.serverTime)
      } catch (error) {
        this.detailError = toApiError(error).message
      }
    },

    /**
     * ROOM-01 방 개설 (상품 등록 + 경매 조건). 서버 한 트랜잭션이다.
     *
     * **생성 직후 상태는 시작 방식과 무관하게 항상 `WAITING`이다** (명세 §검증 규칙).
     * 예전에는 `IMMEDIATE`면 `LIVE`를 지어냈는데, 명세는 "판매자가 방송 시작 API를 호출하기
     * 전에는 입찰·예측을 받을 수 없다"고 못 박았다 — 개설과 방송 시작은 별개 호출이다(ROOM-05).
     *
     * `auctionId`도 서버가 준다. 목 시절의 로컬 시퀀스는 사라졌다.
     */
    async createRoom(payload: CreateRoomPayload): Promise<CreateRoomResult> {
      this.creating = true
      try {
        const created = await createAuctionRoom({
          product: {
            title: payload.product.title,
            description: payload.product.description,
            // 요청 필드명은 `category`이고 값은 **코드**다 (목록 응답의 `categoryCode`와 같은 값)
            category: payload.product.categoryCode,
            // 명세상 단일 이미지다 — 대표로 지정한 것, 없으면 첫 장
            imageUrl: thumbnailUrlOf(payload.product.images),
          },
          auction: {
            reservePrice: payload.room.reservePrice,
            durationMinutes: payload.room.durationMinutes,
            startType: payload.room.startType,
            // `IMMEDIATE`면 생략한다 (명세 §검증 규칙)
            ...(payload.room.startType === 'SCHEDULED' && payload.room.scheduledAt !== null
              ? { scheduledAt: payload.room.scheduledAt }
              : {}),
          },
        })

        // **목록에는 끼워 넣지 않는다.** 예전에는 지어낸 방을 앞에 붙여 개설 직후 보이게 했는데,
        // 396 이후로는 `rooms`가 "현재 질의의 서버 결과"라 두 가지가 어긋난다 —
        // 목록 화면은 진입할 때마다 다시 조회해서 그 항목을 곧바로 덮어쓰고, 새 방은 `WAITING`이라
        // 진행중 탭을 보고 있으면 **서버 필터에 없는 방이 한 장 껴 있는** 상태가 된다.
        return { roomId: created.auctionId }
      } finally {
        this.creating = false
      }
    },
  },
})
