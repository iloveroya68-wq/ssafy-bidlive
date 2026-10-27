import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { RealtimeState } from './realtimeState'

// runReplay 오케스트레이션 통합 테스트 (S15P11A704-393). onConnect 클로저 안이라 직접 export가 없어,
// STOMP Client와 fetchRealtimeState를 모킹하고 handlers를 스파이로 대체해 "무엇이 언제 dispatch/apply되는가"를 관찰한다.
// planReplay(순수)는 실물 유지 — 버전 정합 판정은 그대로 쓰고 상위 절차(재조회·폐기·flush·방전환 가드)만 본다.

interface StompSub {
  dest: string
  cb: (m: { body: string }) => void
  receipt?: string
}
interface MockStompClient {
  connected: boolean
  connectHeaders?: unknown
  beforeConnect?: () => void | Promise<void>
  onConnect?: () => void
  onWebSocketClose?: () => void
  onStompError?: (frame: unknown) => void
  subs: StompSub[]
  receipts: Map<string, () => void>
  published: unknown[]
  /** activate() 호출 횟수 — 재인증 실패 시 deactivate→activate로 다시 붙었는지 본다 */
  activations: number
  emit(dest: string, obj: unknown): void
  fireReceipt(id: string): void
}

const { clients } = vi.hoisted(() => ({ clients: [] as MockStompClient[] }))

// @stomp/stompjs Client 대역 — activate()가 onConnect를 부르고, subscribe/watchForReceipt를 기록해
// 테스트가 emit()/fireReceipt()로 수신·RECEIPT를 구동한다.
vi.mock('@stomp/stompjs', () => {
  class Client implements MockStompClient {
    connected = false
    connectHeaders?: unknown
    beforeConnect?: () => void | Promise<void>
    onConnect?: () => void
    onWebSocketClose?: () => void
    onStompError?: (frame: unknown) => void
    subs: StompSub[] = []
    receipts = new Map<string, () => void>()
    published: unknown[] = []
    activations = 0
    constructor(config: unknown) {
      void config
      clients.push(this)
    }
    subscribe(dest: string, cb: (m: { body: string }) => void, opts?: { receipt?: string }) {
      this.subs.push({ dest, cb, receipt: opts?.receipt })
      return { unsubscribe() {} }
    }
    watchForReceipt(id: string, cb: () => void) {
      this.receipts.set(id, cb)
    }
    // 실물과 같은 순서: beforeConnect가 끝나야 CONNECT가 나간다(stompjs 7.3.0 `await this.beforeConnect(this)`).
    // 토큰 재발급이 필요 없으면 beforeConnect가 Promise를 만들지 않아 여기도 동기로 끝난다.
    activate() {
      this.activations += 1
      const connect = () => {
        this.connected = true
        this.onConnect?.()
      }
      const pending = this.beforeConnect?.()
      if (pending) void pending.then(connect)
      else connect()
    }
    deactivate() {
      this.connected = false
      this.onWebSocketClose?.()
      return Promise.resolve()
    }
    publish(params: unknown) {
      this.published.push(params)
    }
    emit(dest: string, obj: unknown) {
      this.subs.find((s) => s.dest === dest)?.cb({ body: JSON.stringify(obj) })
    }
    fireReceipt(id: string) {
      this.receipts.get(id)?.()
    }
  }
  return { Client }
})

// 스냅샷 조회 대역 — 회차별 반환/실패를 테스트가 통제한다.
vi.mock('./realtimeState', () => ({ fetchRealtimeState: vi.fn() }))

// 핸들러는 스파이로 — runReplay가 무엇을 apply/dispatch하는지만 관찰한다(핸들러 내부 효과와 분리).
vi.mock('./handlers', () => ({
  applySnapshot: vi.fn(),
  dispatchAuctionTopic: vi.fn(),
  dispatchChatTopic: vi.fn(),
  dispatchModeration: vi.fn(),
  dispatchQuestionTopic: vi.fn(),
  handleViewerCount: vi.fn(),
}))

import { connectLiveSocket, disconnectLiveSocket, setChatDroppedHandler } from './liveSocket'
import { fetchRealtimeState } from './realtimeState'
import * as handlers from './handlers'
import * as httpApi from '@/shared/api/http'
import { setAccessToken } from '@/shared/api/authToken'

const mockFetch = vi.mocked(fetchRealtimeState)
const mockApply = vi.mocked(handlers.applySnapshot)
const mockDispatch = vi.mocked(handlers.dispatchAuctionTopic)

const MAIN = '/topic/auctions/42'
const RECEIPT = 'sub-auction-42'
const snap = (stateVersion: number) => ({ stateVersion }) as unknown as RealtimeState
const fakeStore = () =>
  ({ connectionStatus: 'idle' }) as unknown as Parameters<typeof connectLiveSocket>[0]

// 마이크로태스크를 넉넉히 흘려 runReplay의 중첩 await(재조회 포함)를 끝까지 진행시킨다.
const flush = async () => {
  for (let i = 0; i < 8; i += 1) await Promise.resolve()
}

// 버전이 붙은 메인 토픽 이벤트(planReplay가 stateVersion을 읽는다). version 생략 시 무버전.
const versionedEvent = (type: string, version?: number) => ({
  type,
  data: version === undefined ? {} : { stateVersion: version },
})

// dispatchAuctionTopic에 넘어간 이벤트들의 stateVersion 순서
const dispatchedVersions = () =>
  mockDispatch.mock.calls.map((c) => (c[1].data as { stateVersion?: number }).stateVersion)
const dispatchedTypes = () => mockDispatch.mock.calls.map((c) => c[1].type)

/** 명세 예시와 같은 30분 TTL(초). 선제 재인증은 만료 5분 전 = 연결 후 25분에 돈다. */
const TTL_SECONDS = 1800
const RENEW_AT_MS = 25 * 60 * 1000

beforeEach(() => {
  vi.useFakeTimers() // RECEIPT 폴백(3s setTimeout)이 실제로 안 뜨게 — 재생은 fireReceipt로 구동한다
  disconnectLiveSocket() // 이전 테스트의 active 정리
  vi.resetAllMocks()
  clients.length = 0
  // 유효한 토큰을 쥔 상태가 기본이다 — 만료·부재는 beforeConnect에서 재발급을 태우므로(연결이 비동기가 된다)
  // 그걸 보려는 테스트에서만 따로 만든다.
  setAccessToken('access-token', TTL_SECONDS)
})

afterEach(() => {
  disconnectLiveSocket()
  setAccessToken(null)
  vi.useRealTimers()
})

describe('runReplay — 정상 경로', () => {
  it('RECEIPT 후 스냅샷을 반영하고, 버전 정합이면 버퍼를 계획대로 재생한다', async () => {
    mockFetch.mockResolvedValueOnce(snap(10))
    connectLiveSocket(fakeStore(), 42)
    const client = clients[0]

    // RECEIPT 전 도착 → 버퍼링
    client.emit(MAIN, versionedEvent('E11', 11))
    client.emit(MAIN, versionedEvent('E12', 12))
    client.fireReceipt(RECEIPT)
    await flush()

    expect(mockFetch).toHaveBeenCalledTimes(1)
    expect(mockFetch).toHaveBeenCalledWith(42)
    expect(mockApply).toHaveBeenCalledTimes(1)
    expect(mockApply.mock.calls[0][1]).toEqual(snap(10))
    // 11,12 연속이라 그대로 오름차순 재생
    expect(dispatchedVersions()).toEqual([11, 12])
  })
})

describe('runReplay — 불연속이면 버퍼 폐기 + 1회 재조회 (리뷰 a)', () => {
  it('버전 공백이면 이번 버퍼를 버리고 더 신선한 스냅샷을 다시 조회한다(포섭 — 유실 아님)', async () => {
    mockFetch
      .mockResolvedValueOnce(snap(10)) // 1회차
      .mockResolvedValueOnce(snap(20)) // 재조회 — 더 신선
    connectLiveSocket(fakeStore(), 42)
    const client = clients[0]

    client.emit(MAIN, versionedEvent('E11', 11))
    client.emit(MAIN, versionedEvent('E13', 13)) // 12 누락 → 불연속
    client.fireReceipt(RECEIPT)
    await flush()

    expect(mockFetch).toHaveBeenCalledTimes(2) // 재조회 발생
    expect(mockApply).toHaveBeenCalledTimes(2)
    expect(mockApply.mock.calls[1][1]).toEqual(snap(20)) // 더 신선한 스냅샷이 상태를 이어받음
    // 버린 버퍼(11,13)는 재생하지 않는다 — 재조회 회차엔 버퍼가 비어 dispatch 0
    expect(mockDispatch).not.toHaveBeenCalled()
  })
})

describe('runReplay — 재조회 후에도 불연속이면 pending 전부 도착순 flush (리뷰 b)', () => {
  it('버전 정렬을 못 믿을 땐 무버전 유실 방지로 도착 순서 그대로 전부 흘린다', async () => {
    let resolveRound2: (() => void) | undefined
    mockFetch
      .mockResolvedValueOnce(snap(10)) // 1회차
      .mockImplementationOnce(
        () =>
          new Promise<RealtimeState>((res) => {
            resolveRound2 = () => res(snap(10))
          }),
      )
    connectLiveSocket(fakeStore(), 42)
    const client = clients[0]

    client.emit(MAIN, versionedEvent('E11', 11))
    client.emit(MAIN, versionedEvent('E13', 13)) // 불연속 → 재조회
    client.fireReceipt(RECEIPT)
    await flush() // 재조회(2회차)가 스냅샷을 기다리는 중 — 아직 buffering=true

    // 재조회 대기 중 새 이벤트 도착 → 다시 버퍼로
    client.emit(MAIN, versionedEvent('E12', 12))
    client.emit(MAIN, versionedEvent('E15', 15)) // 12→15도 불연속
    resolveRound2?.()
    await flush()

    expect(mockFetch).toHaveBeenCalledTimes(2)
    // depth1에서 또 불연속이라 plan.apply가 아니라 pending 전부를 도착 순서로 dispatch
    expect(dispatchedVersions()).toEqual([12, 15])
  })
})

describe('runReplay — await 사이 방 전환 가드 (리뷰 c)', () => {
  // 지연 fetch를 fireReceipt로 시작만 시켜두고 resolve는 테스트가 쥔다. 방 전환/미전환만 다르게 태워 가드를 격리한다.
  const startPendingReplay = () => {
    let resolveFetch: (() => void) | undefined
    mockFetch.mockImplementationOnce(
      () =>
        new Promise<RealtimeState>((res) => {
          resolveFetch = () => res(snap(10))
        }),
    )
    connectLiveSocket(fakeStore(), 42)
    const first = clients[0]
    first.emit(MAIN, versionedEvent('E', 11))
    first.fireReceipt(RECEIPT)
    return () => resolveFetch?.()
  }

  it('대조군 — 방 전환이 없으면 뒤늦게 resolve된 스냅샷이 정상 반영된다', async () => {
    const resolveFetch = startPendingReplay()
    await flush() // fetch 대기 중

    resolveFetch() // 전환 없이 그대로 resolve
    await flush()

    // 같은 흐름인데 전환만 없다 — applySnapshot이 실제로 호출된다(아래 가드 테스트의 not.toHaveBeenCalled이 유의미함을 보증)
    expect(mockFetch).toHaveBeenCalledTimes(1)
    expect(mockApply).toHaveBeenCalledTimes(1)
  })

  it('스냅샷 조회 도중 방이 바뀌면 옛 세션은 새 방 스토어를 오염시키지 않고 폐기된다', async () => {
    const resolveFetch = startPendingReplay()
    await flush() // 1회차가 fetch를 기다리는 중

    // 방 전환 — 새 연결이 active를 교체한다(이전 세션 deactivate)
    connectLiveSocket(fakeStore(), 99)
    resolveFetch() // 옛 세션의 fetch가 뒤늦게 resolve
    await flush()

    // 앵커: fetch는 실제로 일어났고(거기까지 진행) → 그 뒤에 가드가 막았다("진행 자체가 안 됨"과 구분).
    expect(mockFetch).toHaveBeenCalledTimes(1)
    // 옛 세션은 active.client가 자기가 아니라 early return → apply/dispatch 없음. 새 세션은 receipt 미발화라 미시작.
    expect(mockApply).not.toHaveBeenCalled()
    expect(mockDispatch).not.toHaveBeenCalled()
  })
})

describe('runReplay — 재생 완료 후 실시간 전환(출구)', () => {
  it('재생이 끝나면(buffering=false) 이후 도착 이벤트는 버퍼를 거치지 않고 즉시 dispatch된다', async () => {
    mockFetch.mockResolvedValueOnce(snap(10))
    connectLiveSocket(fakeStore(), 42)
    const client = clients[0]

    client.fireReceipt(RECEIPT) // 버퍼 없음 — 재생은 즉시 끝나고 buffering=false로 전환
    await flush()
    expect(mockDispatch).not.toHaveBeenCalled() // 재생할 버퍼가 없었음

    // 재생 완료 후 도착한 실시간 이벤트 — 동기적으로 바로 dispatch된다(버퍼 경유 아님)
    client.emit(MAIN, versionedEvent('LIVE', 99))
    expect(dispatchedTypes()).toEqual(['LIVE'])
  })
})

// 질문 큐는 BE 인가가 판매자 전용이라, 참가자가 구독하면 SUBSCRIPTION_DENIED가 개인 에러 큐로 돌아온다.
// "구독하지 않는다"는 조용히 깨지는 종류라 양쪽 분기를 다 못 박는다.
describe('questions 구독 — 판매자 경로만 (S15P11A704-541)', () => {
  const destinations = () => clients[0].subs.map((s) => s.dest)

  it('옵션 없이 연결하면(참가자·대기방) /questions를 구독하지 않는다', () => {
    connectLiveSocket(fakeStore(), 42)
    expect(destinations()).not.toContain(`${MAIN}/questions`)
    expect(destinations()).toContain(MAIN) // 앵커 — 다른 구독은 정상적으로 걸렸다
  })

  it('questions 옵션을 켜면(콘솔) /questions를 구독한다', () => {
    connectLiveSocket(fakeStore(), 42, { questions: true })
    expect(destinations()).toContain(`${MAIN}/questions`)
  })

  it('자동 재연결(onConnect 재실행) 후에도 /questions를 다시 구독한다', () => {
    connectLiveSocket(fakeStore(), 42, { questions: true })
    const client = clients[0]

    client.onConnect?.() // 재연결 — stompjs는 재접속마다 onConnect를 다시 부른다
    // opts가 클로저에 남아 있어 재구독된다(연결 1회차 + 재연결 1회차 = 2건)
    expect(client.subs.filter((s) => s.dest === `${MAIN}/questions`)).toHaveLength(2)
  })
})

// 개인 에러 큐(WS-04) 라우팅 (S15P11A704-558). 채팅 SEND 거절은 correlationId(=clientMessageId)로 낙관 행에
// 연결돼 즉시 사유와 함께 실패 표시되고, 그 외 에러(구독 거부 등)는 채팅 행을 건드리면 안 된다 — 조용히 깨지는
// 종류라(오표시는 확정 메시지를 「전송 실패」로 만든다) 양쪽 분기를 다 못 박는다.
describe('개인 에러 큐 — 채팅 거절만 실패 통지 (S15P11A704-558)', () => {
  const ERRORS = '/user/queue/errors'
  const CHAT_DEST = '/app/auctions/42/chat'
  const MUTED_MESSAGE = '일시적으로 채팅이 제한되었습니다.'

  // 에러 프레임을 흘리고 실패 통지 스파이를 돌려준다.
  const emitError = (data: Record<string, unknown>) => {
    const onFailed = vi.fn()
    setChatDroppedHandler(onFailed)
    connectLiveSocket(fakeStore(), 42)
    clients[0].emit(ERRORS, { type: 'ERROR', data })
    return onFailed
  }

  afterEach(() => setChatDroppedHandler(null))

  it('채팅 SEND 거절이면 correlationId 행을 서버 문구(publicMessage)와 함께 실패로 통지한다', () => {
    const onFailed = emitError({
      code: 'CHAT_MUTED',
      message: MUTED_MESSAGE,
      refDestination: CHAT_DEST,
      correlationId: 'req-1',
    })

    expect(onFailed).toHaveBeenCalledWith('req-1', MUTED_MESSAGE)
  })

  it('채팅이 아닌 에러(구독 거부)는 통지하지 않는다 — 남의 실패로 내 메시지를 실패 표시하지 않는다', () => {
    const onFailed = emitError({
      code: 'SUBSCRIPTION_DENIED',
      message: '권한이 없어요.',
      refDestination: '/topic/auctions/42/questions',
      correlationId: null,
    })

    expect(onFailed).not.toHaveBeenCalled()
  })

  // BE는 SEND destination에만 `/app` 프리픽스를 쓴다. 접미사(`/chat`)만 보면 채팅 토픽 SUBSCRIBE 거부의
  // receipt가 correlationId를 달고 왔을 때 남의 실패로 내 낙관 행이 실패 표시된다.
  it('`/app` SEND가 아닌 `/topic/.../chat` 거부는 통지하지 않는다 — 구독 receipt 오탐 방지', () => {
    const onFailed = emitError({
      code: 'SUBSCRIPTION_DENIED',
      message: '권한이 없어요.',
      refDestination: '/topic/auctions/42/chat',
      correlationId: 'req-1',
    })

    expect(onFailed).not.toHaveBeenCalled()
  })

  // 채팅 실패로 라우팅되는 경로도 콘솔 흔적을 남긴다 — 조용히 삼키면 서버 거절을 추적할 수 없다.
  it('채팅 거절로 라우팅해도 콘솔 경고는 남긴다', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const onFailed = emitError({
      code: 'CHAT_MUTED',
      message: MUTED_MESSAGE,
      refDestination: CHAT_DEST,
      correlationId: 'req-1',
    })

    expect(onFailed).toHaveBeenCalledWith('req-1', MUTED_MESSAGE)
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('WS 에러'), 'CHAT_MUTED')
    warn.mockRestore()
  })

  // AUTH_EXPIRED는 재인증 경로지만, 만료 토큰으로 거절된 SEND를 서버가 재전송해 주지는 않는다
  // (reauth는 /app/auth/renew만 쏜다). 재인증 분기가 먼저 return하면 이 경로만 사유 없는 「전송 실패」로
  // 남으므로, 통지와 재인증이 **둘 다** 일어나야 한다(MR !295 리뷰).
  it('AUTH_EXPIRED로 거절된 채팅도 사유와 함께 통지하고 재인증도 함께 돈다', async () => {
    const refresh = vi.spyOn(httpApi, 'refreshAccessToken').mockResolvedValue('new-token')
    const onFailed = emitError({
      code: 'AUTH_EXPIRED',
      message: '인증이 만료되었어요. 다시 시도해 주세요.',
      refDestination: CHAT_DEST,
      correlationId: 'req-1',
    })
    await flush()

    expect(onFailed).toHaveBeenCalledWith('req-1', '인증이 만료되었어요. 다시 시도해 주세요.')
    expect(refresh).toHaveBeenCalled()
    expect(clients[0].published).toContainEqual(
      expect.objectContaining({ destination: '/app/auth/renew' }),
    )
    refresh.mockRestore()
  })

  it('correlationId가 없으면 어느 행인지 모르므로 통지하지 않는다', () => {
    const onFailed = emitError({
      code: 'CHAT_SEND_FAILED',
      message: MUTED_MESSAGE,
      refDestination: CHAT_DEST,
      correlationId: null,
    })

    expect(onFailed).not.toHaveBeenCalled()
  })
})

// 토큰 만료 재인증 (WS-01). STOMP 세션은 CONNECT 때 한 번 인증하고 끝이라, 만료가 화면에 드러나지 않는다 —
// 「연결됨」인 채로 입찰·채팅만 조용히 거절되는 상태가 제일 위험하다. 그래서 (1) 만료 전에 미리 갱신하고
// (2) RECEIPT로 갱신을 확인하고 (3) 확인이 안 되면 재연결로 확정한다. 셋 다 조용히 깨지는 종류라 못 박는다.
describe('토큰 재인증 (WS-01)', () => {
  const ERRORS = '/user/queue/errors'
  interface PublishParams {
    destination: string
    headers?: Record<string, string>
    body: string
  }
  const renewsOf = (client: MockStompClient) =>
    (client.published as PublishParams[]).filter((p) => p.destination === '/app/auth/renew')

  /** 재발급이 실제로 새 토큰·새 만료를 심는 것까지 흉내낸다 — 다음 갱신 예약이 그 값을 읽는다. */
  const stubRefresh = (token = 'renewed-token') =>
    vi.spyOn(httpApi, 'refreshAccessToken').mockImplementation(async () => {
      setAccessToken(token, TTL_SECONDS)
      return token
    })

  const silenceWarn = () => vi.spyOn(console, 'warn').mockImplementation(() => {})

  // 여기선 시간을 분 단위로 흘리므로 RECEIPT 폴백(3초)이 실제로 떠 재생이 돈다 — 스냅샷을 채워 두지 않으면
  // 재인증과 무관한 곳에서 터진다. 이 블록의 관심사는 재생이 아니라 재인증이다.
  beforeEach(() => mockFetch.mockResolvedValue(snap(1)))

  afterEach(() => vi.restoreAllMocks())

  it('만료 전에 스스로 재인증을 보낸다 — receipt를 달아서', async () => {
    const refresh = stubRefresh()
    connectLiveSocket(fakeStore(), 42)
    const client = clients[0]

    vi.advanceTimersByTime(RENEW_AT_MS - 1_000)
    await flush()
    expect(refresh).not.toHaveBeenCalled() // 아직 여유가 남았다 — 앵커

    vi.advanceTimersByTime(1_000)
    await flush()

    expect(refresh).toHaveBeenCalledTimes(1)
    const renews = renewsOf(client)
    expect(renews).toHaveLength(1)
    // receipt가 없으면 서버가 RECEIPT를 안 보내 갱신 성공을 확인할 방법이 사라진다(WS-01).
    expect(renews[0].headers?.receipt).toEqual(expect.any(String))
    expect(JSON.parse(renews[0].body)).toEqual({ accessToken: 'renewed-token' })
  })

  it('RECEIPT로 갱신이 확인되면 같은 세션에서 다음 갱신을 다시 예약한다', async () => {
    stubRefresh()
    connectLiveSocket(fakeStore(), 42)
    const client = clients[0]

    vi.advanceTimersByTime(RENEW_AT_MS)
    await flush()
    client.fireReceipt(renewsOf(client)[0].headers!.receipt)

    vi.advanceTimersByTime(RENEW_AT_MS)
    await flush()

    expect(renewsOf(client)).toHaveLength(2)
    expect(client.activations).toBe(1) // 재연결 없이 이어진다 — RECEIPT 대기 타이머도 함께 접혔다는 뜻
  })

  it('RECEIPT가 안 오면 세션을 다시 연결한다 — 갱신 여부를 모르는 채로 두지 않는다', async () => {
    silenceWarn()
    stubRefresh()
    connectLiveSocket(fakeStore(), 42)
    const client = clients[0]

    vi.advanceTimersByTime(RENEW_AT_MS)
    await flush()
    expect(client.activations).toBe(1) // 아직 대기 중 — 앵커

    vi.advanceTimersByTime(10_000) // RECEIPT 대기 만료
    await flush()

    expect(client.activations).toBe(2) // deactivate → activate로 CONNECT를 다시 태웠다
    expect(client.connected).toBe(true)
  })

  it('AUTH_RENEW_* 거절이면 새 토큰으로 CONNECT부터 다시 한다', async () => {
    silenceWarn()
    connectLiveSocket(fakeStore(), 42)
    const client = clients[0]

    client.emit(ERRORS, {
      type: 'ERROR',
      data: {
        code: 'AUTH_RENEW_EXPIRED',
        message: '재인증에 사용할 Access Token이 만료되었습니다.',
        refDestination: '/app/auth/renew',
        correlationId: 'auth-renew-1',
      },
    })
    await flush()

    expect(client.activations).toBe(2)
  })

  it('선제 타이머와 AUTH_EXPIRED가 겹쳐도 재발급·renew는 한 번만 나간다', async () => {
    silenceWarn()
    const refresh = stubRefresh()
    connectLiveSocket(fakeStore(), 42)
    const client = clients[0]

    vi.advanceTimersByTime(RENEW_AT_MS) // 선제 갱신이 RECEIPT를 기다리는 중
    await flush()
    client.emit(ERRORS, { type: 'ERROR', data: { code: 'AUTH_EXPIRED' } })
    await flush()

    expect(refresh).toHaveBeenCalledTimes(1)
    expect(renewsOf(client)).toHaveLength(1) // 둘이 겹쳐 쏘면 RECEIPT 짝이 엉킨다
  })

  it('방을 나가면 재인증 타이머도 접힌다 — 나간 뒤에 도는 유령 타이머 금지', async () => {
    const refresh = stubRefresh()
    connectLiveSocket(fakeStore(), 42)
    const client = clients[0]

    disconnectLiveSocket()
    vi.advanceTimersByTime(60 * 60 * 1000) // 한 시간을 흘려도
    await flush()

    expect(refresh).not.toHaveBeenCalled()
    expect(renewsOf(client)).toHaveLength(0)
    expect(client.activations).toBe(1) // 되살아나지도 않는다
  })

  // 재연결 경로의 실 버그: 만료된 토큰으로 CONNECT하면 서버가 거절하고 stompjs는 5초마다 **같은** 토큰으로
  // 다시 두드린다 — 사용자는 「재연결 중」에서 영영 못 벗어난다.
  it('토큰이 만료됐으면 CONNECT 전에 재발급받는다', async () => {
    setAccessToken('stale-token', 1)
    vi.advanceTimersByTime(2_000) // 만료됨
    const refresh = stubRefresh('fresh-token')

    connectLiveSocket(fakeStore(), 42)
    const client = clients[0]

    expect(refresh).toHaveBeenCalledTimes(1)
    expect(client.subs).toHaveLength(0) // 재발급을 기다리느라 아직 CONNECT 전 — 순서가 핵심이다
    await flush()

    expect(client.connectHeaders).toEqual({ Authorization: 'Bearer fresh-token' })
    expect(client.subs.length).toBeGreaterThan(0)
  })

  it('토큰이 멀쩡하면 재연결마다 재발급을 부르지 않는다', () => {
    const refresh = stubRefresh()
    connectLiveSocket(fakeStore(), 42)

    expect(refresh).not.toHaveBeenCalled()
    expect(clients[0].connectHeaders).toEqual({ Authorization: 'Bearer access-token' })
  })
})

describe('runReplay — 스냅샷 조회 실패 폴백', () => {
  it('스냅샷 조회가 실패해도 버퍼를 도착 순서대로 반영하고 실시간으로 전환한다', async () => {
    mockFetch.mockRejectedValueOnce(new Error('boom'))
    connectLiveSocket(fakeStore(), 42)
    const client = clients[0]

    client.emit(MAIN, versionedEvent('A'))
    client.emit(MAIN, versionedEvent('B'))
    client.fireReceipt(RECEIPT)
    await flush()

    expect(mockApply).not.toHaveBeenCalled() // 스냅샷 없음
    expect(dispatchedTypes()).toEqual(['A', 'B']) // flushRaw — 버퍼 도착 순서 반영
  })
})
