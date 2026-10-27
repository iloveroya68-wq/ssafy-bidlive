// 목 라이브 피드 드라이버 (S15P11A704-203) — docs/03-역할-경계-합의.md §7.
//
// 가짜 STOMP 클라이언트는 만들지 않는다. 이 파일은 **실 핸들러(features/live/socket/handlers.ts)를
// 그대로 두드리는 setInterval 드라이버**다 — 실소켓 전환 시 이 파일만 지우면 되고 핸들러는 재사용된다.
// 데모(중간 발표)용이며, FE1 화면 개발에는 스토어 fixture로 충분하다(§7).
//
// ⚠️ 타이머 틱 이벤트는 없다 — 카운트다운은 endsAt 기준 렌더러(useCountdown)가 그린다.
//    여기서는 서버가 보낼 법한 {type,data} 이벤트만 흘린다.
// ⚠️ 이벤트 이름·필드는 socket/types.ts(백엔드 명세 사본)를 따른다 — 목에서만 통하는 이름을 만들지 않는다(§6-6).
//    (구 mocks/bidFeed.ts는 SocketEvent·핸들러 로직을 자체 복사했던 FE1 임시 파일이라, 이 드라이버가 그 자리를 대체한다.)

import { MAX_EXTENSIONS, type useLiveStore } from '@/stores/live'
import {
  dispatchAuctionTopic,
  dispatchChatTopic,
  handleViewerCount,
} from '@/features/live/socket/handlers'
import { toIsoDateTime } from '@/shared/api/types'

type LiveStore = ReturnType<typeof useLiveStore>

const VIBE_MESSAGES = [
  '🔥 누군가 베팅을 진행 중입니다!',
  '⚡ 새 입찰이 들어왔어요',
  '👀 방금 3명 입장',
  '🔥 베팅 열기 상승 중',
]

// [닉네임, 내용, 질문 여부]
const CHAT_LINES: Array<[string, string, boolean]> = [
  ['민수', '타건음 좋네요', false],
  ['서연', '스위치 윤활 되어 있나요?', true],
  ['준호', '뒷면도 보여주세요~', true],
  ['하늘', '🔥🔥', false],
  ['도윤', '이거 얼마까지 갈까요', false],
  ['지아', '이 가격이면 사야죠', false],
]

const iso = () => toIsoDateTime(Date.now())

export interface MockLiveFeedOptions {
  /** 초기 마감까지 시간(초). 기본 252 */
  durationSec?: number
  /** 초기 집계 시작값 */
  bidCount?: number
  participantCount?: number
  viewerCount?: number
}

/** 목 라이브 피드 핸들. `stop`은 언마운트 시 타이머 정리, `bump`은 내 입찰을 즉시 집계에 반영(데모). */
export interface MockLiveFeedHandle {
  stop: () => void
  bump: () => void
}

/**
 * 목 라이브 피드를 시작한다. 반환값은 핸들({@link MockLiveFeedHandle}) — `stop`을 언마운트 시 반드시 호출해야 타이머가 남지 않는다.
 *
 * 흐름: TIME_SYNC로 serverOffset·endsAt를 세팅(타이머가 서버 권위로 뜨게) → 입찰·채팅·시청자 이벤트를 주기적으로 흘린다.
 * 마감/연장/낙찰 같은 일회성 전이는 아래 emit* 함수로 데모 컨트롤에서 직접 쏜다.
 */
export function startMockLiveFeed(
  store: LiveStore,
  options: MockLiveFeedOptions = {},
): MockLiveFeedHandle {
  const { durationSec = 252, bidCount = 12, participantCount = 9, viewerCount = 24 } = options

  let bids = bidCount
  let participants = participantCount
  let viewers = viewerCount
  let msgIndex = 0
  let vibeIndex = 0

  // 시작부에서 라이브로 전환 — 이게 없으면 스토어 기본값 'WAITING'이 남아 라이브 화면이 대기 상태로 뜬다.
  const endAtMs = Date.now() + durationSec * 1000
  dispatchAuctionTopic(store, {
    type: 'AUCTION_STARTED',
    data: { startedAt: iso(), endAt: toIsoDateTime(endAtMs) },
  })
  // TIME_SYNC — 라이브에서 serverOffset을 세우는 건 이 이벤트다. 목은 서버 시각이 곧 로컬 시각이라 offset ≈ 0(데모상 무방).
  dispatchAuctionTopic(store, {
    type: 'TIME_SYNC',
    data: { serverTime: iso(), endAt: toIsoDateTime(endAtMs) },
  })

  const timers: ReturnType<typeof setInterval>[] = []

  // 현재 카운터로 BID_ACCEPTED를 방출한다(공개 범위는 건수·참여자·Vibe뿐 — 규칙 6)
  const emitBid = (vibe: string) => {
    dispatchAuctionTopic(store, {
      type: 'BID_ACCEPTED',
      data: { bidCount: bids, participantCount: participants, vibeMessage: vibe },
    })
  }

  // 입찰 접수 — 건수 증가, 가끔 참여자 증가, Vibe 문구
  timers.push(
    setInterval(() => {
      // 마감 후엔 입찰 브로드캐스트가 멈춘다(실서버 동작) — 집계중 오버레이 뒤로 건수가 계속 오르지 않게
      if (store.auctionStatus !== 'LIVE') return
      bids += 1
      if (Math.random() < 0.35) participants += 1
      emitBid(VIBE_MESSAGES[vibeIndex++ % VIBE_MESSAGES.length])
    }, 5200),
  )

  // 내 입찰 즉시 반영(데모) — 목 피드는 자체 카운터라 내 행동을 모르므로, 이 진입점으로 건수를 올린다.
  // 실 모드는 서버 BID_ACCEPTED가 담당하므로 store에서 이 함수를 노출하지 않는다(S15P11A704-355 리뷰 3).
  const bumpBid = () => {
    if (store.auctionStatus !== 'LIVE') return
    bids += 1
    emitBid('내 입찰이 반영됐어요')
  }

  // 채팅 — 순서대로 흘린다(발신자·질문 칩 구분은 핸들러/ChatPanel이 처리)
  timers.push(
    setInterval(() => {
      const [nickname, content, isQuestion] = CHAT_LINES[msgIndex++ % CHAT_LINES.length]
      dispatchChatTopic(store, {
        type: 'CHAT_MESSAGE',
        data: {
          messageId: `mock-live-${Date.now()}-${msgIndex}`,
          memberId: null,
          nickname,
          content,
          isQuestion,
          clientMessageId: null,
          maskedFields: [],
          createdAt: iso(),
        },
      })
    }, 3400),
  )

  // 시청자 수 — 소폭 변동(활성 세션 수, LIVE-03)
  timers.push(
    setInterval(() => {
      viewers = Math.max(0, viewers + (Math.random() < 0.5 ? 1 : -1))
      handleViewerCount(store, { viewerCount: viewers })
    }, 6000),
  )

  return {
    stop: () => {
      timers.forEach(clearInterval)
      timers.length = 0
    },
    bump: bumpBid,
  }
}

// ── 데모 컨트롤용 일회성 이벤트 (와이어프레임 [시연] 바: 연장 · 마감 · 유찰 · 낙찰 공개) ──

/** +5분 연장 (BID-03). 다음 마감·연장 횟수는 스토어에서 유도한다 — 데모 컨트롤은 인자 없이 호출한다. */
export function emitAuctionExtended(store: LiveStore): void {
  const extensionCount = store.extensionCount + 1
  const endAtMs = (store.endsAt ?? Date.now()) + 5 * 60 * 1000
  dispatchAuctionTopic(store, {
    type: 'AUCTION_EXTENDED',
    data: {
      endAt: toIsoDateTime(endAtMs),
      extensionCount,
      remainingExtensions: Math.max(0, MAX_EXTENSIONS - extensionCount),
    },
  })
}

/** 마감 → 집계 시작 (BID-07) */
export function emitAuctionEnded(store: LiveStore): void {
  dispatchAuctionTopic(store, {
    type: 'AUCTION_ENDED',
    data: { endedAt: iso(), status: 'SETTLING' },
  })
}

/** 낙찰·정산 완료 (BID-07) — 결과값은 종료 후 REST 조회(DEAL-06)라 여기선 상태 전이만 유효 */
export function emitAuctionSettled(store: LiveStore): void {
  dispatchAuctionTopic(store, {
    type: 'AUCTION_SETTLED',
    data: {
      winner: '●●3',
      highestBidAmount: 82_000,
      secondHighestBidAmount: 74_000,
      finalPrice: 74_000,
      resultType: 'SECOND_PRICE',
    },
  })
}

/** 유찰 (BID-07) */
export function emitAuctionNoSale(store: LiveStore): void {
  dispatchAuctionTopic(store, {
    type: 'AUCTION_NO_SALE',
    data: { reason: 'NO_BID', endedAt: iso() },
  })
}
