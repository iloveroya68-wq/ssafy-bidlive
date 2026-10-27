// 시연용 더미 방 카드 (프론트 전용).
//
// ⚠️ **이것은 `shared/api/mock.ts` 스위치 밖의 가짜다.** 그 파일이 "스위치 밖 가짜는 목록만 보고
// 나머지는 진짜로 읽는 사람을 속인다"며 없앤 것과 같은 종류이고, 실제로 홈 히어로의 방 id 하드코딩을
// 걷어낸 적이 있다(S15P11A704-507). 그럼에도 다시 두는 이유는 하나다 — **시연 때 목록이 비면
// 화면 자체를 보여줄 수 없어서**다. 목 모드와 무관하게(실 API로 붙어도) 뜬다.
//
// 속이지 않기 위한 장치 셋을 반드시 함께 유지한다.
//   ① 진짜 방이 **항상 앞**이다. 더미는 뒤에만 붙는다 (`withDemoRooms`).
//   ② 누르면 방으로 들어가지 않고 「시연용 더미 데이터입니다」 안내만 뜬다 (`DemoRoomNotice.vue`).
//   ③ `auctionId`가 음수라 실 데이터와 절대 겹치지 않고, 라우팅에 쓰이면 즉시 티가 난다.
//
// **끄는 법**: 아래 `DEMO_ROOMS_ENABLED`를 false로 두면 전부 사라진다. 시연이 끝나면 이 파일째
// 지우는 것이 맞다 — 남겨 두면 위 ①~③이 언젠가 조용히 무너진다.

import type { AuctionRoomSummary } from '@/stores/room'

/** 시연 더미 노출 스위치. 끄면 목록·홈이 실 데이터만 보여준다 */
export const DEMO_ROOMS_ENABLED = true

/**
 * 더미 판별. 실 서버 `auction_room_id`는 항상 양수라 음수면 더미다.
 * 카드 클릭 핸들러가 라우팅 전에 이걸로 갈라낸다.
 */
export function isDemoRoom(room: AuctionRoomSummary): boolean {
  return room.auctionId < 0
}

/** 지금부터 n분 뒤 ISO. 더미 카드의 남은 시간이 매번 그럴듯하게 보이도록 조회 시점 기준으로 만든다 */
function inMinutes(minutes: number): string {
  return new Date(Date.now() + minutes * 60_000).toISOString()
}

/**
 * 시연용 상품 이미지는 `public/demo/rooms`에서 정적으로 제공한다.
 * 음수 ID를 키로 고정해 제목과 사진이 어긋나지 않게 하고, 외부 이미지 호스트에도 의존하지 않는다.
 */
const DEMO_THUMBNAIL_BY_ID: Record<number, string> = {
  [-21]: '/demo/rooms/rolex-submariner-126610ln.webp',
  [-22]: '/demo/rooms/nintendo-switch-2.webp',
  [-23]: '/demo/rooms/asics-gel-kayano-31.webp',
  [-1]: '/demo/rooms/keychron-q1-pro.webp',
  [-2]: '/demo/rooms/leica-m6.webp',
  [-3]: '/demo/rooms/nike-sacai-ldwaffle.webp',
  [-4]: '/demo/rooms/herman-miller-aeron.webp',
  [-5]: '/demo/rooms/toji-book-collection.webp',
  [-6]: '/demo/rooms/scott-addict-rc.webp',
  [-7]: '/demo/rooms/sony-wh1000xm5.webp',
  [-8]: '/demo/rooms/lego-10307-eiffel.webp',
  [-24]: '/demo/rooms/dyson-airwrap-long.webp',
  [-25]: '/demo/rooms/fender-american-pro-ii.webp',
  [-26]: '/demo/rooms/murakami-haruki-books.webp',
  [-27]: '/demo/rooms/balmuda-toaster-k05a.webp',
  [-28]: '/demo/rooms/callaway-paradym-irons.webp',
  [-11]: '/demo/rooms/ipad-pro-11-m4.webp',
  [-12]: '/demo/rooms/takashi-murakami-print.webp',
  [-13]: '/demo/rooms/margiela-german-trainer.webp',
  [-14]: '/demo/rooms/wilson-pro-staff-97-v14.webp',
}

function demoThumbnail(auctionId: number): string | null {
  return DEMO_THUMBNAIL_BY_ID[auctionId] ?? null
}

/**
 * 카테고리 코드는 **실제 시드에 있는 6종만** 쓴다
 * (`V1__init.sql`의 `product_category`: ELECTRONICS · HOBBY · FASHION · LIVING · MEDIA · SPORTS).
 * 없는 코드를 쓰면 배지에 표시명 대신 코드가 그대로 나오고 카드 썸네일 그라데이션도 폴백으로 떨어진다 —
 * 실제로 `FURNITURE`를 넣었다가 그렇게 보였다.
 */

/**
 * 진행 중(LIVE) 더미.
 *
 * 마감이 가까운 순으로 둔다 — 홈 히어로·마감 임박이 **앞에서 자르므로** 그 순서가 곧 노출 우선순위다.
 * 목록 기본 페이지가 8건이라 그리드 한 판을 채울 만큼 담았다.
 *
 * `endAt`을 조회 시점 기준으로 만드는 이유: 고정 시각을 박아 두면 시연 중에 「마감 -3:21」 같은
 * 음수가 뜬다. 카운트다운은 서버 권위 값을 표시만 하는 규칙(규칙 5)이라 화면 쪽에 예외를 둘 수 없어,
 * 값 자체를 미래로 만든다.
 */
export function demoLiveRooms(): AuctionRoomSummary[] {
  return [
    // ── 마감 직전 3건 ──
    // 카드·컬럼의 긴급 강조는 **남은 3분 이하**에서 켜진다(`RoomCard`·`EndingSoonCard`의 `urgentMs`).
    // 그 안에 드는 값을 넣어야 빨간 타이머가 실제로 보인다.
    //
    // ⚠️ 이 값들은 조회 시점 기준이고 다시 계산되지 않는다(스토어가 갱신될 때만 재파생). 시연이
    // 3분을 넘기면 「00:00」으로 굳는다 — 새로고침하면 되돌아온다. 타이머로 계속 밀어 주는 장치는
    // 두지 않았다: 시연용 가짜에 그만한 기계를 붙이면 지울 때 딸려 오는 것이 늘어난다.
    live(-21, '롤렉스 서브마리너 데이트 126610LN (2023년 보증서)', 'FASHION', '지훈***', {
      reservePrice: 14_500_000,
      minutes: 1,
      bidCount: 63,
      predictionParticipantCount: 210,
      viewerCount: 487,
    }),
    live(-22, '닌텐도 스위치 2 본체 (예약 물량, 미개봉)', 'ELECTRONICS', '소율***', {
      reservePrice: 640_000,
      minutes: 2,
      bidCount: 38,
      predictionParticipantCount: 126,
      viewerCount: 251,
    }),
    live(-23, '아식스 젤카야노 31 275mm (한정 컬러)', 'SPORTS', '준서***', {
      reservePrice: 160_000,
      minutes: 3,
      bidCount: 15,
      predictionParticipantCount: 44,
      viewerCount: 88,
    }),

    live(-1, '키크론 Q1 Pro 커스텀 (게이트론 저소음 적축)', 'ELECTRONICS', '서연***', {
      reservePrice: 180_000,
      minutes: 4,
      bidCount: 12,
      predictionParticipantCount: 31,
      viewerCount: 48,
    }),
    live(-2, '라이카 M6 필름 카메라 (1984년식, 정품 박스)', 'HOBBY', '도현***', {
      reservePrice: 2_400_000,
      minutes: 9,
      bidCount: 27,
      predictionParticipantCount: 88,
      viewerCount: 132,
    }),
    live(-3, '나이키 x 사카이 LD 와플 270mm 미착용', 'FASHION', '지우***', {
      reservePrice: 210_000,
      minutes: 15,
      bidCount: 6,
      predictionParticipantCount: 19,
      viewerCount: 21,
    }),
    live(-4, '허먼밀러 에어론 리마스터드 B사이즈', 'LIVING', '민준***', {
      reservePrice: 950_000,
      minutes: 21,
      bidCount: 41,
      predictionParticipantCount: 64,
      viewerCount: 76,
    }),
    live(-5, '초판본 토지 전집 21권 (박경리, 1994)', 'MEDIA', '예린***', {
      reservePrice: 320_000,
      minutes: 27,
      bidCount: 9,
      predictionParticipantCount: 24,
      viewerCount: 33,
    }),
    live(-6, '스캇 애딕트 RC 울테그라 Di2 (54사이즈)', 'SPORTS', '태윤***', {
      reservePrice: 4_100_000,
      minutes: 34,
      bidCount: 18,
      predictionParticipantCount: 52,
      viewerCount: 94,
    }),
    live(-7, '소니 WH-1000XM5 (구성품 풀박스)', 'ELECTRONICS', '나윤***', {
      reservePrice: 240_000,
      minutes: 41,
      bidCount: 3,
      predictionParticipantCount: 11,
      viewerCount: 17,
    }),
    live(-8, '레고 10307 에펠탑 (미개봉 실링)', 'HOBBY', '건우***', {
      reservePrice: 780_000,
      minutes: 52,
      bidCount: 22,
      predictionParticipantCount: 47,
      viewerCount: 61,
    }),
    live(-24, '다이슨 에어랩 컴플리트 롱 (정품 케이스)', 'LIVING', '아린***', {
      reservePrice: 520_000,
      minutes: 63,
      bidCount: 11,
      predictionParticipantCount: 29,
      viewerCount: 40,
    }),
    live(-25, '펜더 아메리칸 프로페셔널 II 스트라토캐스터', 'HOBBY', '재민***', {
      reservePrice: 2_150_000,
      minutes: 71,
      bidCount: 7,
      predictionParticipantCount: 16,
      viewerCount: 28,
    }),
    live(-26, '무라카미 하루키 초판 세트 (양장, 상태 A)', 'MEDIA', '서윤***', {
      reservePrice: 290_000,
      minutes: 84,
      bidCount: 4,
      predictionParticipantCount: 13,
      viewerCount: 15,
    }),
    live(-27, '발뮤다 더 토스터 K05A (박스 보관)', 'LIVING', '민서***', {
      reservePrice: 195_000,
      minutes: 96,
      bidCount: 2,
      predictionParticipantCount: 8,
      viewerCount: 12,
    }),
    live(-28, '캘러웨이 패러다임 아이언 세트 (7i, 다이나믹골드)', 'SPORTS', '도윤***', {
      reservePrice: 1_380_000,
      minutes: 112,
      bidCount: 5,
      predictionParticipantCount: 21,
      viewerCount: 35,
    }),
  ]
}

/** 시작 예정(WAITING) 더미. 시작이 가까운 순 — 「곧 시작해요」 스트립과 같은 감각이다 */
export function demoWaitingRooms(): AuctionRoomSummary[] {
  return [
    waiting(-11, '아이패드 프로 11 M4 256GB (애플케어 잔여)', 'ELECTRONICS', '하은***', {
      reservePrice: 1_150_000,
      minutes: 25,
      viewerCount: 9,
    }),
    waiting(-12, '무라카미 다카시 판화 (에디션 넘버 있음)', 'HOBBY', '수아***', {
      reservePrice: 3_800_000,
      minutes: 48,
      viewerCount: 4,
    }),
    waiting(-13, '메종 마르지엘라 저먼 트레이너 41', 'FASHION', '유진***', {
      reservePrice: 420_000,
      minutes: 95,
      viewerCount: 6,
    }),
    waiting(-14, '윌슨 프로 스태프 97 v14 (그립 교체됨)', 'SPORTS', '현우***', {
      reservePrice: 180_000,
      minutes: 160,
      viewerCount: 2,
    }),
  ]
}

interface LiveOverrides {
  reservePrice: number
  /** 지금부터 마감까지 남은 분 */
  minutes: number
  bidCount: number
  predictionParticipantCount: number
  viewerCount: number
}

function live(
  auctionId: number,
  title: string,
  categoryCode: string,
  sellerNickname: string,
  over: LiveOverrides,
): AuctionRoomSummary {
  return {
    auctionId,
    title,
    thumbnailUrl: demoThumbnail(auctionId),
    categoryCode,
    sellerNickname,
    reservePrice: over.reservePrice,
    bidCount: over.bidCount,
    predictionParticipantCount: over.predictionParticipantCount,
    scheduledAt: null,
    endAt: inMinutes(over.minutes),
    soldPrice: null,
    endedAt: null,
    status: 'LIVE',
    viewerCount: over.viewerCount,
  }
}

function waiting(
  auctionId: number,
  title: string,
  categoryCode: string,
  sellerNickname: string,
  over: { reservePrice: number; minutes: number; viewerCount: number },
): AuctionRoomSummary {
  return {
    auctionId,
    title,
    thumbnailUrl: demoThumbnail(auctionId),
    categoryCode,
    sellerNickname,
    reservePrice: over.reservePrice,
    // 시작 전에는 입찰·예측이 있을 수 없다 — 0이 아니면 화면이 거짓말을 한다
    bidCount: 0,
    predictionParticipantCount: 0,
    scheduledAt: inMinutes(over.minutes),
    endAt: null,
    soldPrice: null,
    endedAt: null,
    status: 'WAITING',
    viewerCount: over.viewerCount,
  }
}

/**
 * 실 목록 **뒤에** 같은 상태의 더미를 붙인다.
 *
 * **진짜 방이 언제나 앞이다** — 이 순서가 이 기능의 유일한 안전장치다. 앞에 끼우면 새로 만든
 * 방이 더미에 밀려 안 보이고, 그건 시연 중에 가장 나쁘게 실패하는 방식이다.
 *
 * `status`가 없으면(전체 탭) LIVE·WAITING 둘 다 붙인다. `ENDED`에는 붙이지 않는다 — 끝난 방
 * 카드는 낙찰가·종료 시각을 말하는 자리라 가짜를 두면 결과 화면과 이어지지 않는다.
 */
export function withDemoRooms(
  rooms: readonly AuctionRoomSummary[],
  status?: 'LIVE' | 'WAITING' | 'ENDED',
): AuctionRoomSummary[] {
  if (!DEMO_ROOMS_ENABLED) return [...rooms]
  if (status === 'ENDED') return [...rooms]
  const demo =
    status === 'LIVE'
      ? demoLiveRooms()
      : status === 'WAITING'
        ? demoWaitingRooms()
        : [...demoLiveRooms(), ...demoWaitingRooms()]
  return [...rooms, ...demo]
}
