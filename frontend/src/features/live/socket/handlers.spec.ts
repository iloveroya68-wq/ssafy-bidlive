import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { toEpochMs } from '@/shared/api/types'
import { useLiveStore, type ChatMessage } from '@/stores/live'
import {
  applySnapshot,
  dispatchAuctionTopic,
  dispatchChatTopic,
  dispatchModeration,
  dispatchQuestionTopic,
} from './handlers'
import type { RealtimeState } from './realtimeState'

// 소켓 핸들러의 경계 로직(S15P11A704-374). 핸들러는 스토어 필드를 직접 갱신하므로 실제 Pinia 스토어로 검증한다.
beforeEach(() => setActivePinia(createPinia()))

function chatData(over: Record<string, unknown> = {}) {
  return {
    messageId: 'srv-100',
    memberId: 7,
    nickname: '나',
    content: '안녕',
    isQuestion: false,
    clientMessageId: null,
    maskedFields: [],
    createdAt: '2026-01-01T00:00:01.000Z',
    ...over,
  }
}

describe('dispatchChatTopic — CHAT_MESSAGE', () => {
  it('내 낙관 행의 에코(같은 clientMessageId)는 중복 추가하지 않고 서버 값으로 교체한다', () => {
    const store = useLiveStore()
    const optimistic: ChatMessage = {
      id: 'opt-1',
      nickname: '나',
      text: '안녕',
      isQuestion: false,
      clientMessageId: 'c1',
      maskedFields: [],
      createdAt: '2026-01-01T00:00:00.000Z',
      pending: true,
    }
    store.chat = [optimistic]

    dispatchChatTopic(store, {
      type: 'CHAT_MESSAGE',
      data: chatData({ clientMessageId: 'c1', content: '안녕**', maskedFields: ['content'] }),
    })

    expect(store.chat).toHaveLength(1) // 중복 추가 안 됨
    expect(store.chat[0].id).toBe('srv-100') // 서버 messageId(ULID)로 승격
    expect(store.chat[0].text).toBe('안녕**') // 서버가 마스킹한 content로 교체
    expect(store.chat[0].maskedFields).toEqual(['content']) // 가려진 필드 반영
    expect(store.chat[0].pending).toBeUndefined() // 통째 교체라 pending 해제
  })

  it('남이 보낸 메시지(내 clientMessageId 아님)는 목록에 추가된다', () => {
    const store = useLiveStore()
    store.chat = []

    dispatchChatTopic(store, {
      type: 'CHAT_MESSAGE',
      data: chatData({ messageId: 'srv-200', nickname: '남', clientMessageId: null }),
    })

    expect(store.chat).toHaveLength(1)
    expect(store.chat[0].id).toBe('srv-200')
    expect(store.chat[0].nickname).toBe('남')
  })

  it('같은 messageId 서버 메시지 재수신(재브로드캐스트)은 중복 추가하지 않는다(416)', () => {
    const store = useLiveStore()
    store.chat = []
    const data = chatData({ messageId: 'srv-500', clientMessageId: null })

    dispatchChatTopic(store, { type: 'CHAT_MESSAGE', data })
    dispatchChatTopic(store, { type: 'CHAT_MESSAGE', data })

    expect(store.chat).toHaveLength(1) // messageId(ULID) dedup
  })
})

// CHAT_DELETED (S15P11A704-681) — 판매자·운영자 삭제와 AI 사후 차단이 같은 이벤트로 온다.
// 명세가 「화면·로컬 버퍼에서 제거」로 확정했다(tombstone 아님).
describe('dispatchChatTopic — CHAT_DELETED', () => {
  it('같은 messageId의 행만 목록에서 제거한다', () => {
    const store = useLiveStore()
    store.chat = []
    dispatchChatTopic(store, { type: 'CHAT_MESSAGE', data: chatData({ messageId: 'srv-1' }) })
    dispatchChatTopic(store, { type: 'CHAT_MESSAGE', data: chatData({ messageId: 'srv-2' }) })

    dispatchChatTopic(store, { type: 'CHAT_DELETED', data: { messageId: 'srv-1' } })

    expect(store.chat.map((m) => m.id)).toEqual(['srv-2'])
  })

  // 상한(capChat)에 밀려났거나 애초에 못 받은 메시지의 삭제 이벤트는 정상 경로다 — 크래시도 부작용도 없어야 한다.
  it('가지고 있지 않은 id면 아무것도 바꾸지 않는다', () => {
    const store = useLiveStore()
    store.chat = []
    dispatchChatTopic(store, { type: 'CHAT_MESSAGE', data: chatData({ messageId: 'srv-1' }) })

    dispatchChatTopic(store, { type: 'CHAT_DELETED', data: { messageId: 'srv-없음' } })

    expect(store.chat.map((m) => m.id)).toEqual(['srv-1'])
  })
})

describe('handleNotice — pinnedNotice messageId ULID 보존 (S15P11A704-416)', () => {
  it('NOTICE_PINNED의 messageId(ULID)를 Number 코어싱 없이 문자열 그대로 담는다', () => {
    const store = useLiveStore()
    dispatchChatTopic(store, {
      type: 'NOTICE_PINNED',
      data: { messageId: 'srv-notice-1', content: '공지 내용' },
    })
    expect(store.pinnedNotice).toEqual({ messageId: 'srv-notice-1', content: '공지 내용' })
  })

  it('content가 비면 해제(null)로 본다', () => {
    const store = useLiveStore()
    store.pinnedNotice = { messageId: 'srv-notice-1', content: '공지' }
    dispatchChatTopic(store, {
      type: 'NOTICE_UNPINNED',
      data: { messageId: 'srv-notice-1', content: null },
    })
    expect(store.pinnedNotice).toBeNull()
  })
})

describe('dispatchQuestionTopic', () => {
  // 확정 계약(명세 CHAT-06 질문 큐 수신 = BE StompQuestionAddedEventPublisher.QuestionAddedData)
  function questionData() {
    return {
      questionId: '01J8W3P2N9KQ4T',
      memberId: 7,
      nickname: '질문자',
      content: '언제 시작?',
      createdAt: '2026-01-01T00:00:01.000Z',
    }
  }

  it('QUESTION_ADDED — 큐에 ULID questionId와 닉네임 그대로 추가한다', () => {
    const store = useLiveStore()
    store.questions = []

    dispatchQuestionTopic(store, { type: 'QUESTION_ADDED', data: questionData() })

    expect(store.questions).toEqual([
      { questionId: '01J8W3P2N9KQ4T', nickname: '질문자', content: '언제 시작?' },
    ])
  })

  it('QUESTION_ADDED — 같은 questionId 재수신은 중복 추가하지 않는다(재접속·에코·REST 시드 겹침)', () => {
    const store = useLiveStore()
    store.questions = [{ questionId: '01J8W3P2N9KQ4T', nickname: '질문자', content: '언제 시작?' }]

    dispatchQuestionTopic(store, { type: 'QUESTION_ADDED', data: questionData() })

    expect(store.questions).toHaveLength(1)
  })

  it('QUESTION_REMOVED — 해당 질문만 큐에서 제거한다', () => {
    const store = useLiveStore()
    store.questions = [
      { questionId: '01J8W3P2N9KQ4T', nickname: 'a', content: 'x' },
      { questionId: '01J8W3P2N9KQ4U', nickname: 'b', content: 'y' },
    ]

    dispatchQuestionTopic(store, {
      type: 'QUESTION_REMOVED',
      data: { questionId: '01J8W3P2N9KQ4T', reason: 'RESOLVED' },
    })

    expect(store.questions.map((q) => q.questionId)).toEqual(['01J8W3P2N9KQ4U'])
  })
})

function snapshot(over: Partial<RealtimeState> = {}): RealtimeState {
  return {
    stateVersion: 5,
    auctionStatus: 'LIVE',
    endAt: '2026-01-01T00:10:00.000Z',
    remainingSeconds: 600,
    extendCount: 1,
    remainingExtensions: 2,
    bidCount: 3,
    bidParticipantCount: 2,
    viewerCount: 10,
    myBidAmount: null,
    myPredictedPrice: null,
    myStakeAmount: null,
    myParticipationType: 'NONE',
    pinnedNotice: null,
    amIMuted: false,
    mutedUntil: null,
    ...over,
  }
}

describe('applySnapshot', () => {
  it('남은 연장 횟수는 스냅샷 값을 그대로 쓴다(FE 상한에서 유도하지 않는다)', () => {
    const store = useLiveStore()
    // FE 상수로 유도하면 2가 나오는 조합 — 서버 정책이 상한이므로 서버가 준 1이 이겨야 한다
    applySnapshot(store, snapshot({ extendCount: 1, remainingExtensions: 1 }))
    expect(store.extensionCount).toBe(1)
    expect(store.remainingExtensions).toBe(1)
  })

  it('pinnedNotice.messageId(ULID)를 Number 변환 없이 그대로 복원한다(S15P11A704-416)', () => {
    const store = useLiveStore()
    applySnapshot(
      store,
      snapshot({ pinnedNotice: { messageId: 'srv-notice-9', content: '재접속 공지' } }),
    )
    expect(store.pinnedNotice).toEqual({ messageId: 'srv-notice-9', content: '재접속 공지' })
  })

  it('예측 참여자의 금액·스테이크를 복원한다(S15P11A704-388)', () => {
    const store = useLiveStore()

    applySnapshot(
      store,
      snapshot({
        myParticipationType: 'PREDICTION',
        myPredictedPrice: 62_000,
        myStakeAmount: 1_500,
      }),
    )

    expect(store.myPredictedPrice).toBe(62_000)
    expect(store.myStakeAmount).toBe(1_500)
    expect(store.myParticipationType).toBe('PREDICTION')
  })

  it('입찰 참여자·미참여자의 예측 금액은 null로 남는다', () => {
    const store = useLiveStore()

    applySnapshot(store, snapshot({ myParticipationType: 'BID', myBidAmount: 52_000 }))

    expect(store.myBid).toBe(52_000)
    expect(store.myPredictedPrice).toBeNull()
    expect(store.myStakeAmount).toBeNull()
  })

  it('뮤트가 아니면 스냅샷에 시각이 있어도 mutedUntil을 null로 정리한다(이전 세션 잔존 방지)', () => {
    const store = useLiveStore()
    store.mutedUntil = 999 // 잔존값
    applySnapshot(store, snapshot({ amIMuted: false, mutedUntil: '2026-01-01T00:05:00.000Z' }))
    expect(store.muted).toBe(false)
    expect(store.mutedUntil).toBeNull()
  })

  it('뮤트면 스냅샷 해제 시각을 복원한다', () => {
    const store = useLiveStore()
    applySnapshot(store, snapshot({ amIMuted: true, mutedUntil: '2026-01-01T00:05:00.000Z' }))
    expect(store.muted).toBe(true)
    expect(store.mutedUntil).toBe(toEpochMs('2026-01-01T00:05:00.000Z'))
  })

  it('뮤트지만 스냅샷에 해제 시각이 없으면 null(폴백 — 규칙 5, 시각 자체 계산 금지)', () => {
    const store = useLiveStore()
    applySnapshot(store, snapshot({ amIMuted: true, mutedUntil: null }))
    expect(store.muted).toBe(true)
    expect(store.mutedUntil).toBeNull()
  })
})

describe('dispatchModeration', () => {
  it('CHAT_MUTED — 현재 방(roomId)과 auctionId가 같으면 muted=true, 해제 시각을 서버값으로 설정한다', () => {
    const store = useLiveStore()
    store.roomId = 100
    dispatchModeration(store, {
      type: 'CHAT_MUTED',
      data: { auctionId: 100, durationSeconds: 600, mutedUntil: '2026-01-01T00:05:00.000Z' },
    })
    expect(store.muted).toBe(true)
    expect(store.mutedUntil).toBe(toEpochMs('2026-01-01T00:05:00.000Z'))
  })

  it('CHAT_MUTED — 다른 방(auctionId≠roomId) 개인 뮤트는 무시한다(개인 큐 지연·다중 탭, 414 리뷰)', () => {
    const store = useLiveStore()
    store.roomId = 100
    dispatchModeration(store, {
      type: 'CHAT_MUTED',
      data: { auctionId: 999, durationSeconds: 600, mutedUntil: '2026-01-01T00:05:00.000Z' },
    })
    expect(store.muted).toBe(false) // 지금 보는 방 채팅은 잠기지 않는다
    expect(store.mutedUntil).toBeNull()
  })

  it('MODERATION_WARNING — id를 증가시켜 같은 사유가 연달아 와도 화면이 다시 뜨게 한다', () => {
    const store = useLiveStore()
    dispatchModeration(store, {
      type: 'MODERATION_WARNING',
      data: {
        messageId: 'srv-100',
        reason: 'OFF_PLATFORM_TRADE',
        message: '외부 거래 유도는 제한됩니다.',
      },
    })
    expect(store.moderationWarning?.id).toBe(1)
    expect(store.moderationWarning?.message).toBe('외부 거래 유도는 제한됩니다.') // 표시 문구를 담는다(414)

    dispatchModeration(store, {
      type: 'MODERATION_WARNING',
      data: {
        messageId: 'srv-101',
        reason: 'OFF_PLATFORM_TRADE',
        message: '외부 거래 유도는 제한됩니다.',
      },
    })
    expect(store.moderationWarning?.id).toBe(2) // 같은 reason이어도 id 증가
    expect(store.moderationWarning?.messageId).toBe('srv-101')
    // MODERATION_WARNING은 입력을 잠그지 않는다(잠금은 CHAT_MUTED 담당)
    expect(store.muted).toBe(false)
  })

  it('MODERATION_WARNING — messageId null은 null로 유지한다(Number 코어싱 안 함)', () => {
    const store = useLiveStore()
    dispatchModeration(store, {
      type: 'MODERATION_WARNING',
      data: { messageId: null, reason: 'x', message: '' },
    })
    expect(store.moderationWarning?.messageId).toBeNull()
  })
})

describe('dispatchAuctionTopic — 라우팅', () => {
  // 일시정지/재개 테스트가 이 라우터를 쓰는 유일한 곳이었는데 정책 변경으로 지워졌다(S15P11A704-429).
  // 라우터가 통째로 무보증이 되지 않게 살아 있는 이벤트로 대체한다.
  it('AUCTION_STARTED — LIVE 전환 + 서버 권위 마감 저장', () => {
    const store = useLiveStore()

    dispatchAuctionTopic(store, {
      type: 'AUCTION_STARTED',
      data: { startedAt: '2026-01-01T00:00:00.000Z', endAt: '2026-01-01T00:15:00.000Z' },
    })

    expect(store.auctionStatus).toBe('LIVE')
    expect(store.endsAt).toBe(toEpochMs('2026-01-01T00:15:00.000Z'))
  })

  /**
   * 시작 전에는 스냅샷 조회가 실패한다(실시간 상태 Hash를 AUCTION_STARTED가 만든다) — 그래서
   * 집계가 null로 남아 콘솔·라이브 지표가 「—」로 굳었고, 새로고침해야 숫자가 떴다.
   */
  it('AUCTION_STARTED — 집계가 미수신이면 0으로 세운다 (「—」로 굳지 않게)', () => {
    const store = useLiveStore()

    dispatchAuctionTopic(store, {
      type: 'AUCTION_STARTED',
      data: { startedAt: '2026-01-01T00:00:00.000Z', endAt: '2026-01-01T00:15:00.000Z' },
    })

    expect(store.bidCount).toBe(0)
    expect(store.participantCount).toBe(0)
  })

  /** 재연결 버퍼에 남아 있던 낡은 시작 이벤트가 최신 집계를 0으로 덮으면 안 된다 */
  it('AUCTION_STARTED — 이미 받은 집계는 덮지 않는다', () => {
    const store = useLiveStore()
    store.bidCount = 7
    store.participantCount = 3

    dispatchAuctionTopic(store, {
      type: 'AUCTION_STARTED',
      data: { startedAt: '2026-01-01T00:00:00.000Z', endAt: '2026-01-01T00:15:00.000Z' },
    })

    expect(store.bidCount).toBe(7)
    expect(store.participantCount).toBe(3)
  })

  /**
   * PRED-01. **이 테스트가 지키는 것은 `vibeFeed`가 안 늘어난다는 쪽이다.**
   *
   * 그 배열은 `useBidPulse`가 참조 변화를 세어 경매봉을 내려치는 입력이라, 예측 문구가 섞이면
   * 입찰 망치가 흔들림·섬광과 함께 예측에 울려, 예측 참여가 입찰과 구분되지 않는다.
   * 핸들러를 `handleBidAccepted`로 합치는 "정리"가 정확히 이걸 깨뜨리는데,
   * 렌더만 보면 멀쩡해 보여서 눈으로는 안 잡힌다.
   */
  it('PREDICTION_ACCEPTED — 예측 피드에만 쌓고 vibeFeed(=망치)는 건드리지 않는다', () => {
    const store = useLiveStore()

    dispatchAuctionTopic(store, {
      type: 'PREDICTION_ACCEPTED',
      data: { participantCount: 9, vibeMessage: '누군가 예측에 참여했습니다!' },
    })

    expect(store.predictionFeed).toEqual(['누군가 예측에 참여했습니다!'])
    expect(store.vibeFeed).toEqual([])
    // participantCount는 BID_ACCEPTED와 같은 카운터라 그대로 반영한다
    expect(store.participantCount).toBe(9)
  })

  // PRED-03 신호를 스토어에 남긴다 (S15P11A704-543). 화면(결과)이 이걸 watch해 PRED-02를 재조회하므로,
  // 여기가 no-op으로 되돌아가면 재조회 계기가 통째로 사라지고 화면은 "집계 중"에 굳는다.
  it('PREDICTION_SETTLED — 수신 사실을 남긴다 (다른 상태는 그대로)', () => {
    const store = useLiveStore()
    store.auctionStatus = 'SETTLED'

    dispatchAuctionTopic(store, {
      type: 'PREDICTION_SETTLED',
      data: { settlementStatus: 'COMPLETED', settledCount: 128 },
    })

    expect(store.predictionSettlement).toEqual({
      settlementStatus: 'COMPLETED',
      settledCount: 128,
    })
    expect(store.auctionStatus).toBe('SETTLED') // 신호일 뿐 — 상태 전이는 AUCTION_* 소관
  })

  // 실서버는 명세의 두 필드를 안 보낸다(`PredictionSettledMessage.completed()` → `data: {}`).
  // 필드로 분기하면 조용히 죽으므로, 빈 payload여도 "왔다"가 남아야 한다.
  it('PREDICTION_SETTLED — data가 비어도 신호로 남는다', () => {
    const store = useLiveStore()

    dispatchAuctionTopic(store, { type: 'PREDICTION_SETTLED', data: {} })

    expect(store.predictionSettlement).not.toBeNull()
  })

  // 종료 계열 4종은 상태 문자열만 남기면 안 된다 — 오버레이(BID-10)가 낙찰자·최종가·사유를
  // 이 payload에서만 받는다. 예전엔 `_data`로 버려서 실서버에서 낙찰 공개가 아예 안 떴다.
  it('AUCTION_SETTLED — 상태와 함께 payload 원본을 남긴다', () => {
    const store = useLiveStore()
    const event = {
      type: 'AUCTION_SETTLED' as const,
      data: {
        winner: '●●3',
        highestBidAmount: 82_000,
        secondHighestBidAmount: 74_000,
        finalPrice: 74_000,
        resultType: 'SECOND_PRICE',
      },
    }

    dispatchAuctionTopic(store, event)

    expect(store.auctionStatus).toBe('SETTLED')
    expect(store.endEvent).toEqual(event) // 화면이 그대로 reveal.apply에 넘긴다
  })

  it('AUCTION_ENDED — 상태는 서버 값, 없으면 SETTLING (이벤트는 그대로 보관)', () => {
    const store = useLiveStore()

    dispatchAuctionTopic(store, {
      type: 'AUCTION_ENDED',
      data: { endedAt: '2026-01-01T00:15:00.000Z' },
    } as unknown as Parameters<typeof dispatchAuctionTopic>[1])

    expect(store.auctionStatus).toBe('SETTLING')
    expect(store.endEvent?.type).toBe('AUCTION_ENDED')
  })

  it('AUCTION_NO_SALE·AUCTION_CANCELED — 사유를 실은 이벤트를 남긴다', () => {
    const store = useLiveStore()

    dispatchAuctionTopic(store, {
      type: 'AUCTION_NO_SALE',
      data: { reason: 'HOST_LEFT', endedAt: '2026-01-01T00:15:00.000Z' },
    })
    expect(store.auctionStatus).toBe('NO_SALE')
    expect(store.endEvent).toMatchObject({ data: { reason: 'HOST_LEFT' } })

    dispatchAuctionTopic(store, {
      type: 'AUCTION_CANCELED',
      data: {
        reason: 'FORCED',
        endedAt: '2026-01-01T00:16:00.000Z',
        resultPath: '/rooms/7/nosale',
      },
    })
    expect(store.auctionStatus).toBe('CANCELED')
    expect(store.endEvent).toMatchObject({ type: 'AUCTION_CANCELED', data: { reason: 'FORCED' } })
  })

  it('명세에 없는 타입은 무시한다 — 스토어를 건드리지 않는다 (§6-8)', () => {
    const store = useLiveStore()
    store.auctionStatus = 'LIVE'

    dispatchAuctionTopic(store, {
      type: 'AUCTION_PAUSED',
      data: {},
    } as unknown as Parameters<typeof dispatchAuctionTopic>[1])

    // 지워진 이벤트가 뒤늦게 날아와도 화면 상태가 흔들리면 안 된다
    expect(store.auctionStatus).toBe('LIVE')
  })
})
