// STOMP 클라이언트 연결 계층 (S15P11A704-207) — WS-01·02·04.
//
// @stomp/stompjs Client를 감싸, 방 입장 시 토픽을 구독하고 수신 메시지를 실 핸들러(handlers.ts, 202)로 흘린다.
// 목 드라이버(liveFeed·waitingRoom)를 대체하는 실 연결이며, 아직 WS 서버가 없어 실제 연결은 WS URL이
// 열린 실 모드에서만 성립한다(목 모드에선 store.connect가 호출되지 않아 이 모듈이 로드조차 되지 않는다).
//
// 범위: 연결·Bearer 인증(WS-01)·heart-beat·자동 재연결·토픽 구독→핸들러 라우팅(WS-02)·채팅 SEND·
//       토큰 만료 전 선제 재인증 + AUTH_EXPIRED 폴백(WS-01, receipt로 성공 확인)·에러 큐(WS-04). 연결/재연결 시 WS-03 재접속 재생 절차(receipt 버퍼링 →
//       스냅샷 → stateVersion 정합 재생, S15P11A704-228, snapshotReplay.ts).
// 후속: 질문 큐 구독(CHAT-06).

import { Client, type IMessage, type StompHeaders } from '@stomp/stompjs'
import { API_BASE_URL, refreshAccessToken } from '@/shared/api/http'
import { getAccessToken, getAccessTokenExpiresAt } from '@/shared/api/authToken'
import { fetchRealtimeState } from './realtimeState'
import { planReplay } from './snapshotReplay'
import type { useLiveStore } from '@/stores/live'
import type { ChatSendPayload, ViewerCountData, WsErrorData } from './types'
import {
  applySnapshot,
  dispatchAuctionTopic,
  dispatchChatTopic,
  dispatchModeration,
  dispatchQuestionTopic,
  handleViewerCount,
} from './handlers'

type LiveStore = ReturnType<typeof useLiveStore>

interface RawEvent {
  type: string
  data: unknown
}

/** 한 번에 하나의 라이브 연결만 유지한다(방 전환 시 이전 연결을 끊고 새로 연다) */
let active: { client: Client; roomId: number } | null = null

/**
 * 자동 재연결 중 보낸 채팅 SEND 대기 큐 (S15P11A704-373).
 * 끊긴 사이 publishChat이 무동작으로 빠지면 낙관 메시지가 서버로 안 나간 유령이 되므로,
 * 미연결이면 여기 쌓고 재연결(onConnect)에서 순서대로 flush한다. clientMessageId 멱등이라 재전송이
 * 중복 저장/렌더를 만들지 않는다. 방 전환·퇴장(disconnectLiveSocket) 시 폐기해 이전 방 메시지가 새 방으로 새지 않게 한다.
 */
let chatQueue: ChatSendPayload[] = []

/** 대기 큐 상한 — 장기 단절 중 무한 적재를 막는다. 초과 시 오래된 것부터 버리고 로깅한다(무시된 유실을 감춘 채로 두지 않음). */
const MAX_CHAT_QUEUE = 50

/**
 * 채팅 실패 통지 콜백 (S15P11A704-383). 상한 초과로 payload가 확정 폐기되거나 서버가 SEND를 거절할 때(558)
 * 그 clientMessageId를 스토어로 돌려준다(chatPublish 주입의 역방향). 스토어가 해당 낙관 행을 '전송 실패'로
 * 표시해, 「전송 중…」이 무한히 남는 걸 막는다. reason은 서버가 준 표시용 문구 — 큐 폐기엔 없다.
 * connect가 주입하고 disconnect가 비운다.
 */
let onChatDropped: ((clientMessageId: string, reason?: string) => void) | null = null

/** 스토어가 실패 통지 콜백을 등록/해제한다(연결 시 주입, 해제 시 null) */
export function setChatDroppedHandler(
  fn: ((clientMessageId: string, reason?: string) => void) | null,
): void {
  onChatDropped = fn
}

function enqueueChat(payload: ChatSendPayload): void {
  chatQueue.push(payload)
  if (chatQueue.length > MAX_CHAT_QUEUE) {
    const dropped = chatQueue.splice(0, chatQueue.length - MAX_CHAT_QUEUE)
    console.warn(
      `[live/socket] 재연결 대기 채팅 큐 상한(${MAX_CHAT_QUEUE}) 초과 — 오래된 ${dropped.length}건 폐기`,
    )
    // 폐기된 낙관 행을 '전송 실패'로 표시하도록 스토어에 통지한다 — 다시 시도되지 않으므로 '전송 중'으로 남기면 안 된다.
    dropped.forEach((p) => onChatDropped?.(p.clientMessageId))
  }
}

/** 재연결 시 대기 큐를 도착 순서대로 전송한다(onConnect에서 호출, 해당 세션의 client·roomId로) */
function flushChatQueue(client: Client, roomId: number): void {
  if (chatQueue.length === 0) return
  const pending = chatQueue.splice(0)
  pending.forEach((payload) => {
    client.publish({
      destination: `/app/auctions/${roomId}/chat`,
      body: JSON.stringify(payload),
    })
  })
}

/**
 * RECEIPT 폴백 대기(ms). 브로커가 RECEIPT를 안 돌려주는 구성(예: Spring 내장 심플 브로커)에서
 * 재생이 영구히 안 시작돼 메인 토픽이 버퍼에만 쌓이는 걸 막는다(WS-03, 리뷰 1).
 */
const RECEIPT_TIMEOUT_MS = 3_000

/**
 * 재인증 RECEIPT 대기(ms). 넘기면 세션 만료 시각이 갱신됐는지 알 수 없어 재연결로 확실히 만든다.
 * 구독 receipt(3초)보다 넉넉한 이유: 오탐의 대가가 「재생 조기 시작」이 아니라 **전면 재연결**이라
 * 잠깐 느린 왕복으로 방을 통째로 다시 붙이면 손해가 크다.
 */
const RENEW_RECEIPT_TIMEOUT_MS = 10_000

/**
 * 만료까지 남은 시간 중 재인증을 미룰 비율. 나머지가 여유분이다 — 30분 TTL이면 만료 5분 전에 갱신한다.
 * 비율로 잡는 이유: TTL이 바뀌어도(BE `application.yml`) 여유가 같이 줄어 만료 뒤에 도는 일이 없다.
 */
const RENEW_AT_RATIO = 5 / 6

/** 만료 전 미리 도는 재인증 타이머(연결당 하나). 재연결·퇴장 때 반드시 정리한다 — 방을 나간 뒤 도는 타이머는 유령이다. */
let renewTimer: ReturnType<typeof setTimeout> | null = null

/** renew SEND의 RECEIPT 대기 타이머. 서버 확인이 안 오면 이 타이머가 재연결을 건다. */
let renewReceiptTimer: ReturnType<typeof setTimeout> | null = null

/**
 * 재인증 진행 중 표시. 미리 도는 타이머와 AUTH_EXPIRED 반응 경로가 겹칠 수 있는데,
 * 겹친 채로 두면 refresh를 두 번 쏘고 renew도 두 번 나가 RECEIPT 짝이 엉킨다(먼저 온 하나로 뒤엣것을 '성공' 처리).
 */
let renewInFlight = false

/** renew receipt id 일련번호 — 이전 시도의 늦은 RECEIPT를 지금 시도의 성공으로 오인하지 않게 매번 새로 판다. */
let renewSeq = 0

function clearRenewTimers(): void {
  if (renewTimer !== null) {
    clearTimeout(renewTimer)
    renewTimer = null
  }
  if (renewReceiptTimer !== null) {
    clearTimeout(renewReceiptTimer)
    renewReceiptTimer = null
  }
}

/**
 * 만료 전 재인증을 예약한다(onConnect·갱신 성공 직후).
 * 만료 시각을 모르면 예약하지 않는다 — 그 경우는 AUTH_EXPIRED 반응 경로가 폴백이다.
 */
function scheduleRenew(): void {
  if (renewTimer !== null) {
    clearTimeout(renewTimer)
    renewTimer = null
  }
  const expiresAt = getAccessTokenExpiresAt()
  if (expiresAt === null) return
  const delay = Math.max(0, (expiresAt - Date.now()) * RENEW_AT_RATIO)
  renewTimer = setTimeout(() => {
    renewTimer = null
    void reauth()
  }, delay)
}

/**
 * 재인증이 확인되지 않았다 — 서버는 이 세션을 만료로 보고 발신을 막는데 화면은 「연결됨」 그대로라
 * 입찰·채팅이 조용히 안 나가는 상태가 된다. 세션을 끊고 새 토큰으로 CONNECT를 다시 태워
 * 「살아 있다고 믿는 죽은 세션」을 남기지 않는다.
 */
function forceReconnect(reason: string): void {
  const current = active
  if (!current) return
  console.warn(`[live/socket] 재인증 미확인(${reason}) — 세션을 다시 연결합니다`)
  clearRenewTimers()
  renewInFlight = false
  const { client } = current
  void client.deactivate().then(() => {
    // 그사이 방 전환·퇴장으로 주인이 바뀌었으면 죽은 세션을 되살리지 않는다.
    if (active?.client === client) client.activate()
  })
}

/** 명세 WS-01: CONNECT /ws. API_BASE_URL 오리진 + /ws로 브로커 URL을 만든다(http→ws, https→wss) */
function resolveBrokerUrl(): string {
  const base = new URL(API_BASE_URL, globalThis.location?.origin ?? 'http://localhost:8080')
  const wsProtocol = base.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${wsProtocol}//${base.host}/ws`
}

function bearerHeaders(): StompHeaders {
  const token = getAccessToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

/** CONNECT에 그대로 쓰면 거절당할 토큰인가(없거나 이미 만료). 만료 시각을 모르면 만료로 치지 않는다. */
function needsFreshToken(): boolean {
  const expiresAt = getAccessTokenExpiresAt()
  return getAccessToken() === null || (expiresAt !== null && expiresAt <= Date.now())
}

/** {type,data} JSON 파싱 — 손상 메시지는 연결을 죽이지 않고 삼킨 뒤 로깅한다 */
function parse(message: IMessage): RawEvent | null {
  try {
    const parsed = JSON.parse(message.body) as RawEvent
    return parsed && typeof parsed.type === 'string' ? parsed : null
  } catch {
    console.warn('[live/socket] JSON 파싱 실패:', message.body?.slice(0, 120))
    return null
  }
}

/** WS-04 에러 큐. 채팅 SEND 거절은 낙관 행에 사유를 붙이고, AUTH_EXPIRED면 토큰 재발급 후 /app/auth/renew (수신 유지·발신 재개, WS-01) */
function handleWsError(event: RawEvent): void {
  const data = event.data as Partial<WsErrorData> | undefined
  const code = data?.code ?? event.type
  // 라우팅 여부와 무관하게 먼저 남긴다 — 채팅 실패로 라우팅되는 에러가 콘솔에서 조용히 사라지면
  // 서버 거절을 추적할 흔적이 없어진다.
  console.warn('[live/socket] WS 에러:', code)
  // 채팅 SEND 거절(뮤트·슬로우 모드·상태 위반 등, S15P11A704-558). BE가 correlationId에 요청의 clientMessageId를,
  // refDestination에 원 SEND destination을 실어 주므로 낙관 행을 바로 찾아 **사유와 함께** 실패로 표시한다
  // — 없으면 10초 타임아웃(424) 뒤에야 사유 없는 「전송 실패」가 뜬다.
  // `/app` 프리픽스까지 본다: BE는 SEND에만 그 프리픽스를 쓰고, SUBSCRIBE 거부 receipt의 refDestination은
  // `/topic/...`이다. 접미사만 보면 `/topic/auctions/{id}/chat` 구독 거부가 남의 낙관 행을 실패로 만든다.
  // 아래 AUTH_EXPIRED 재인증보다 **앞**에 둔다(MR !295 리뷰): 만료 토큰으로 거절된 채팅도 SEND 거절이라
  // 사유를 붙여야 하는데, reauth()는 /app/auth/renew만 쏘고 원 메시지를 재전송하지 않는다 — 뒤에 두면
  // 이 경로만 사유 없는 「전송 실패」로 남는다. 여긴 return하지 않으니 재인증도 그대로 병행되고,
  // 재인증 SEND(`/app/auth/renew`)는 correlationId가 없어 이 가드에 걸리지 않는다.
  if (
    data?.correlationId &&
    data.refDestination?.startsWith('/app/') &&
    data.refDestination.endsWith('/chat')
  ) {
    onChatDropped?.(data.correlationId, data.message ?? undefined)
  }
  if (code === 'AUTH_EXPIRED') void reauth()
  // 재인증 자체가 거절됐다(WS-01 실패 코드 3종). 서버는 옛 만료 시각을 그대로 두므로 이 세션은 곧
  // 발신이 막히는데, 가만두면 화면은 끝까지 「연결됨」이다 — 새 토큰으로 CONNECT부터 다시 한다.
  else if (code.startsWith('AUTH_RENEW_') || code === 'AUTH_PRINCIPAL_MISMATCH')
    forceReconnect(code)
}

/**
 * 토큰을 재발급받아 현재 STOMP 세션의 만료 시각을 갱신한다(WS-01 `/app/auth/renew`).
 * 미리 도는 타이머(정상 경로)와 AUTH_EXPIRED 수신(폴백) 둘 다 여기로 들어온다.
 *
 * `receipt`를 반드시 단다 — 서버는 요청받은 경우에만 RECEIPT를 돌려주므로, 안 달면 갱신됐는지 알 방법이
 * 없어 만료된 세션 위에서 「연결됨」 화면을 계속 보여주게 된다.
 */
async function reauth(): Promise<void> {
  if (renewInFlight) return
  renewInFlight = true

  let token: string
  try {
    token = await refreshAccessToken()
  } catch {
    renewInFlight = false
    // 재발급 실패 = 세션 자체가 끝났다. 여기서 재연결을 걸면 죽은 쿠키로 5초마다 CONNECT를 두드릴 뿐이라,
    // 서버가 세션을 닫아 주기를 기다린다(그 뒤 재연결의 beforeConnect가 같은 실패를 화면에 드러낸다).
    console.warn('[live/socket] 재인증 실패 — 재로그인이 필요합니다')
    return
  }

  // 재연결 중이면 보낼 세션이 없다 — 어차피 다음 CONNECT가 새 토큰으로 붙는다(beforeConnect).
  const current = active
  if (!current?.client.connected) {
    renewInFlight = false
    return
  }

  const { client } = current
  const receiptId = `auth-renew-${(renewSeq += 1)}`
  client.watchForReceipt(receiptId, () => {
    if (active?.client !== client) return // 그사이 방 전환 — 옛 세션의 뒤늦은 확인
    if (renewReceiptTimer !== null) {
      clearTimeout(renewReceiptTimer)
      renewReceiptTimer = null
    }
    renewInFlight = false
    scheduleRenew() // 새 만료 시각 기준으로 다음 갱신을 다시 건다
  })
  // RECEIPT가 안 오면 갱신 여부를 모른다 — 모르는 채로 두지 않고 재연결로 확정한다.
  renewReceiptTimer = setTimeout(() => {
    renewReceiptTimer = null
    forceReconnect('RECEIPT 없음')
  }, RENEW_RECEIPT_TIMEOUT_MS)
  client.publish({
    destination: '/app/auth/renew',
    headers: { receipt: receiptId },
    body: JSON.stringify({ accessToken: token }),
  })
}

/** 구독 옵션 — 인가가 걸린 토픽만 여기서 켠다(기본은 전부 끈 상태) */
export interface LiveSocketOptions {
  /**
   * 질문 큐(`/questions`) 구독 여부. **판매자만 켠다** — BE `StompSubscriptionAuthorizationService`가
   * 비판매자의 SUBSCRIBE 프레임을 폐기하고 `SUBSCRIPTION_DENIED`를 개인 에러 큐로 돌려주므로,
   * 켠 채로 두면 참가자 전원이 입장마다 에러를 받는다(S15P11A704-541).
   */
  questions?: boolean
}

/**
 * 방 입장 — 연결하고 토픽을 구독한다. 이전 연결이 있으면 먼저 끊는다(방 전환).
 * onConnect는 최초 연결과 자동 재연결마다 실행되므로, 재연결 시 재구독 + 스냅샷 재복원이 함께 일어난다.
 */
export function connectLiveSocket(
  store: LiveStore,
  roomId: number,
  opts?: LiveSocketOptions,
): void {
  disconnectLiveSocket()

  // RECEIPT 폴백 타이머 — onConnect가 걸고, receipt 수신·연결 종료 시 정리한다(연결당 하나).
  let pendingReplayFallback: ReturnType<typeof setTimeout> | null = null

  const client = new Client({
    brokerURL: resolveBrokerUrl(),
    connectHeaders: bearerHeaders(),
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    reconnectDelay: 5_000, // 자동 재연결(WS-01)
  })
  active = { client, roomId }

  // 매 (재)연결 시도마다 최신 토큰으로 CONNECT 헤더를 재평가한다 — 재연결 시 옛 토큰으로 붙는 걸 막는다.
  // 토큰이 이미 만료됐으면 **여기서 재발급까지** 받는다. 안 그러면 죽은 토큰으로 CONNECT → 서버 거절 →
  // 5초 뒤 같은 죽은 토큰으로 재시도가 무한 반복돼, 사용자는 「재연결 중」에서 영영 못 벗어난다.
  // stompjs 7.3.0은 `await this.beforeConnect(this)`로 기다려 준다(client.ts `_connect`) — 재발급이
  // 끝난 뒤에야 CONNECT 프레임이 나간다. 재발급이 필요 없을 땐 Promise를 만들지 않고 동기로 끝낸다.
  client.beforeConnect = () => {
    if (!needsFreshToken()) {
      client.connectHeaders = bearerHeaders()
      return
    }
    return refreshAccessToken()
      .catch(() => {
        // 재발급도 실패 — 있는 헤더로라도 붙여 서버 판단(거절)에 맡긴다. 여기서 삼키지 않으면
        // stompjs가 재연결 루프를 멈춰 버려 세션이 되살아날 기회까지 사라진다.
        console.warn('[live/socket] 재연결 전 토큰 재발급 실패 — 재로그인이 필요합니다')
      })
      .then(() => {
        client.connectHeaders = bearerHeaders()
      })
  }

  client.onConnect = () => {
    store.connectionStatus = 'connected'
    const base = `/topic/auctions/${roomId}`

    // 이 세션의 토큰 만료 전에 미리 재인증을 걸어 둔다(재연결마다 새로). 없으면 경매 도중 만료된 순간부터
    // 입찰·채팅이 조용히 거절되기 시작하는데 화면은 「연결됨」 그대로다.
    clearRenewTimers()
    renewInFlight = false
    scheduleRenew()

    // ── WS-03 재접속 재생 절차 (docs/03 §6-5, S15P11A704-228) ──
    // 메인 토픽만 버퍼링한다: 스냅샷(applySnapshot)이 덮어쓰는 스칼라 상태(bidCount·endsAt 등)가 여기 있어,
    // 스냅샷 조회 지연 사이 도착한 더 신선한 이벤트가 스냅샷에 덮이는 레이스를 막아야 한다.
    // 채팅·시청자·검열은 추가/멱등이라(스냅샷이 chat 배열을 안 건드림) 버퍼링 없이 그대로 흘린다.
    const buffer: RawEvent[] = []
    let buffering = true
    const receiptId = `sub-auction-${roomId}`

    // 1단계: receipt를 달아 구독한다. RECEIPT 수신 전 메인 토픽 이벤트는 버퍼로.
    client.subscribe(
      base,
      (m) => {
        const e = parse(m)
        if (!e) return
        if (buffering) buffer.push(e)
        else dispatchAuctionTopic(store, e)
      },
      { receipt: receiptId },
    )
    client.subscribe(`${base}/chat`, (m) => {
      const e = parse(m)
      if (e) dispatchChatTopic(store, e)
    })
    client.subscribe(`${base}/viewers`, (m) => {
      const e = parse(m)
      if (e?.type === 'VIEWER_COUNT') handleViewerCount(store, e.data as ViewerCountData)
    })
    // 질문 큐(CHAT-06) — QUESTION_ADDED/REMOVED로 콘솔 큐 실시간 유지(S15P11A704-364). 추가/멱등이라 버퍼링 대상 아님.
    // 판매자 전용 구독이다 — 비판매자가 걸면 SUBSCRIPTION_DENIED만 돌아온다(위 LiveSocketOptions).
    if (opts?.questions) {
      client.subscribe(`${base}/questions`, (m) => {
        const e = parse(m)
        if (e) dispatchQuestionTopic(store, e)
      })
    }
    client.subscribe('/user/queue/moderation', (m) => {
      const e = parse(m)
      if (e) dispatchModeration(store, e)
    })
    client.subscribe('/user/queue/errors', (m) => {
      const e = parse(m)
      if (e) handleWsError(e)
    })

    // 재연결 중 쌓인 채팅 SEND를 flush한다(S15P11A704-373). /chat 구독 뒤라 우리 에코를 받아
    // handleChatMessage가 clientMessageId로 낙관 행을 서버 행으로 교체한다(멱등).
    flushChatQueue(client, roomId)

    // 버퍼를 도착 순서대로 흘려보내고 실시간 모드로 전환한다(스냅샷 실패 폴백 — 버퍼를 잃지 않는다).
    const flushRaw = (): void => {
      const pending = buffer.splice(0)
      buffering = false
      pending.forEach((e) => dispatchAuctionTopic(store, e))
    }

    // 2~5단계: 스냅샷 조회 → 반영 → 버퍼 재생. 불연속·역전이면 재조회(추측 적용 금지, 1회만).
    const runReplay = async (resyncDepth = 0): Promise<void> => {
      let snapshot
      try {
        snapshot = await fetchRealtimeState(roomId)
      } catch {
        if (active?.client !== client) return // await 사이 방 전환 — 옛 세션이 새 방 스토어를 오염시키지 않게(리뷰 2)
        flushRaw() // 스냅샷 실패 — 버퍼는 도착 순서대로라도 반영하고 실시간 전환
        return
      }
      if (active?.client !== client) return // await 사이 방 전환/재연결 — 낡은 세션은 폐기
      applySnapshot(store, snapshot)
      const pending = buffer.splice(0) // 이번 회차 대상 확보 + 버퍼 비움
      const plan = planReplay(pending, snapshot.stateVersion)
      if (plan.needsResync && resyncDepth < 1) {
        // 버전 불연속·역전 — 이번 버퍼는 버리고 스냅샷을 다시 조회한다(재조회 중 도착분은 다시 버퍼링됨).
        // 새 스냅샷이 더 신선해 버린 버퍼를 포섭하므로 유실이 아니다(5단계, 추측 적용 금지).
        await runReplay(resyncDepth + 1)
        return
      }
      buffering = false // 이후 메인 토픽 이벤트는 실시간 dispatch
      if (plan.needsResync) {
        // 재조회 후에도 불연속(예: 221 전환 과도기의 버전/무버전 혼재) — 버전 정렬을 신뢰할 수 없으니
        // kept만 적용해 무버전 이벤트를 버리지 말고, 도착 순서대로 전부 흘린다(유실 방지, 리뷰 3).
        pending.forEach((e) => dispatchAuctionTopic(store, e))
      } else {
        plan.apply.forEach((e) => dispatchAuctionTopic(store, e))
      }
    }

    // 재생은 한 번만 시작한다 — RECEIPT와 폴백 타이머가 겹쳐도 이중 진입을 막는다(재조회 재귀는 runReplay가 직접).
    let replayStarted = false
    const startReplay = (): void => {
      if (replayStarted) return
      replayStarted = true
      void runReplay()
    }

    // RECEIPT가 안 와도(브로커 미지원 등) 재생이 멈추지 않도록 타임아웃 폴백(리뷰 1). receipt가 먼저 오면 취소.
    pendingReplayFallback = setTimeout(startReplay, RECEIPT_TIMEOUT_MS)

    // RECEIPT = 서버에 구독이 활성화됨(이후 누락 없음). 이때 스냅샷 조회·재생을 시작한다.
    client.watchForReceipt(receiptId, () => {
      if (pendingReplayFallback !== null) {
        clearTimeout(pendingReplayFallback)
        pendingReplayFallback = null
      }
      startReplay()
    })
  }
  client.onWebSocketClose = () => {
    // 끊긴 세션에 renew를 쏴 봐야 갈 곳이 없다 — 재인증 타이머도 함께 접고, 다음 onConnect가 새로 건다.
    clearRenewTimers()
    renewInFlight = false
    // 연결이 끊기면 대기 중인 폴백 타이머를 정리한다 — 다음 (재)연결의 onConnect가 새 세션으로 새로 건다.
    if (pendingReplayFallback !== null) {
      clearTimeout(pendingReplayFallback)
      pendingReplayFallback = null
    }
    // 이 클라이언트가 아직 활성일 때만 상태를 바꾼다 — 의도적 disconnect()/방 전환으로 닫힌 경우
    // (active가 null이거나 이미 새 클라이언트) close 콜백이 'closed'를 'reconnecting'으로 덮는 걸 막는다.
    if (active?.client === client) store.connectionStatus = 'reconnecting'
  }
  client.onStompError = (frame) => {
    console.warn('[live/socket] STOMP 오류:', frame.headers['message'])
  }

  store.connectionStatus = 'connecting'
  client.activate()
}

/** 퇴장 — 연결을 끊고 정리한다(구독은 deactivate가 함께 정리) */
export function disconnectLiveSocket(): void {
  // 방을 나갔는데 타이머가 살아 있으면, 없는 세션에 renew를 쏘거나 이미 닫은 연결을 되살리려 든다.
  clearRenewTimers()
  renewInFlight = false
  if (!active) return
  // 방 전환·퇴장 — 이전 방으로 갈 대기 채팅은 폐기한다(새 방에 누출 금지, S15P11A704-373).
  // 이 폐기도 상한 초과와 똑같이 '다시 시도 안 됨'이라, 비우기 전에 실패 통지한다(S15P11A704-383 리뷰 1).
  // 의도적 퇴장(store.disconnect)은 setChatDroppedHandler(null)을 먼저 부르고, 방 재입장(connect의 $reset)은
  // chat이 비어 idx===-1로 무시되므로 — 통지가 실제로 실패 표시하는 건 대기방 재연결처럼 chat이 남는 경우뿐이다.
  chatQueue.forEach((p) => onChatDropped?.(p.clientMessageId))
  chatQueue = []
  void active.client.deactivate()
  active = null
}

/**
 * 채팅 SEND (CHAT-01·06). 연결돼 있으면 즉시 전송, 자동 재연결 중이면 큐에 쌓아 재연결 시 flush한다(S15P11A704-373).
 * active가 없으면(목 모드 등 전송 대상 서버 없음) 무동작 — 낙관 반영만 남는다.
 */
export function publishChat(payload: ChatSendPayload): void {
  const current = active
  if (!current) return
  // 자동 재연결 중엔 active는 있지만 미연결이라 stompjs publish()가 throw한다 — connected일 때만 즉시 전송하고,
  // 아니면 큐잉해 유실을 막는다(clientMessageId 멱등이라 재전송이 중복을 만들지 않음).
  if (!current.client.connected) {
    enqueueChat(payload)
    return
  }
  current.client.publish({
    destination: `/app/auctions/${current.roomId}/chat`,
    body: JSON.stringify(payload),
  })
}
