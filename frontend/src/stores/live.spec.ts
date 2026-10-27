import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import {
  CHAT_MAX_LENGTH,
  CHAT_OLDER_MAX,
  CHAT_SEND_TIMEOUT_MS,
  capChat,
  useLiveStore,
  type ChatMessage,
} from './live'
import { useRoomStore } from './room'
import { useAuthStore } from './auth'
import { ApiError } from '@/shared/api/errors'

// 과거 조회(S15P11A704-379) — loadOlderChat이 동적 import하는 fetch를 대역으로 통제한다.
const { fetchMock } = vi.hoisted(() => ({ fetchMock: vi.fn() }))
vi.mock('@/features/chat/history', () => ({ fetchChatHistory: fetchMock }))

// 전송 타임아웃(S15P11A704-424)은 pending 행에만 걸리고, pending은 chatPublish가 주입된 **실 모드**에서만
// 선다. 그래서 이 파일에서 실 모드 connect()를 한 번 태울 수 있어야 한다 — 모드 스위치를 값으로 바꿀 수 있게
// 두고(기본은 기존과 같은 목 모드), STOMP 계층은 대역으로 막는다.
const { mockApi, socketMock } = vi.hoisted(() => ({
  mockApi: { value: true },
  socketMock: {
    connectLiveSocket: vi.fn(),
    disconnectLiveSocket: vi.fn(),
    setChatDroppedHandler: vi.fn(),
    publishChat: vi.fn(),
  },
}))
vi.mock('@/shared/api/mock', async () => {
  const actual = await vi.importActual<typeof import('@/shared/api/mock')>('@/shared/api/mock')
  return { ...actual, isMockApi: () => mockApi.value }
})
vi.mock('@/features/live/socket/liveSocket', () => socketMock)

// markChatFailed는 소켓 없이 스토어만으로 검증되는 순수 액션이다(S15P11A704-383).
// 재연결 대기 큐가 상한 초과·방 전환으로 폐기될 때 liveSocket이 통지하며, 폐기 통지가
// 렌더 상태보다 늦게 올 수 있어(행이 이미 CHAT_KEEP 밖으로 밀려남) '없으면 무시'가 핵심이다.
function chatRow(over: Partial<ChatMessage>): ChatMessage {
  return {
    id: 'opt-t',
    nickname: '나',
    text: '안녕하세요',
    isQuestion: false,
    clientMessageId: 'c1',
    maskedFields: [],
    createdAt: '2026-01-01T00:00:00.000Z',
    pending: true,
    ...over,
  }
}

describe('useLiveStore.markChatFailed', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('clientMessageId가 일치하는 대기 행을 pending 해제 + failed로 바꾼다', () => {
    const store = useLiveStore()
    store.chat = [chatRow({ clientMessageId: 'c1', pending: true })]

    store.markChatFailed('c1')

    expect(store.chat[0].pending).toBe(false)
    expect(store.chat[0].failed).toBe(true)
  })

  it('이미 밀려나 없는 clientMessageId는 조용히 무시하고 다른 행을 건드리지 않는다', () => {
    const store = useLiveStore()
    store.chat = [chatRow({ clientMessageId: 'other', pending: true })]

    store.markChatFailed('gone')

    expect(store.chat).toHaveLength(1)
    expect(store.chat[0].pending).toBe(true)
    expect(store.chat[0].failed).toBeUndefined()
  })

  // 서버 거절 사유 표시 (S15P11A704-558) — 개인 에러 큐(WS-04)의 publicMessage를 행에 실어 나른다.
  it('사유를 받으면 실패 행에 그대로 남긴다 — 화면이 「전송 실패」 대신 그 문구를 쓴다', () => {
    const store = useLiveStore()
    store.chat = [chatRow({ clientMessageId: 'c1', pending: true })]

    store.markChatFailed('c1', '일시적으로 채팅이 제한되었습니다.')

    expect(store.chat[0].failed).toBe(true)
    expect(store.chat[0].failReason).toBe('일시적으로 채팅이 제한되었습니다.')
  })

  it('에코로 확정된 행(서버 id)은 실패로 만들지 않는다 — 에코와 ERROR가 겹치면 에코가 이긴다', () => {
    const store = useLiveStore()
    // 에코는 clientMessageId를 유지한 채 행을 서버 값으로 교체한다(임시키 opt- 소멸).
    store.chat = [chatRow({ id: 'srv-1', clientMessageId: 'c1', pending: undefined })]

    store.markChatFailed('c1', '일시적으로 채팅이 제한되었습니다.')

    expect(store.chat[0].failed).toBeUndefined()
    expect(store.chat[0].failReason).toBeUndefined()
    expect(store.chat[0].id).toBe('srv-1') // 확정 행 그대로
  })
})

// CHAT-01·06 전송(S15P11A704-204). 낙관 반영·멱등키·가드를 스토어만으로 검증한다(전송 계층 미주입 = 목 모드).
describe('useLiveStore.sendChat', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('내용을 trim해 낙관 행으로 추가하고 clientMessageId를 붙인다(목 모드는 pending=false)', () => {
    const store = useLiveStore()
    store.chat = []

    store.sendChat('  하이  ')

    expect(store.chat).toHaveLength(1)
    expect(store.chat[0].text).toBe('하이') // 앞뒤 공백 제거
    expect(store.chat[0].clientMessageId).toBeTruthy() // 멱등키 부여
    // pending=false는 chatPublish(stores/live.ts 모듈 변수)가 null이라 성립한다. createPinia()로 리셋되지
    // 않는 모듈 상태라, 이 파일은 connect()를 부르지 않아 항상 null이다 — 나중에 같은 파일에 연결 테스트가
    // 붙으면 실행 순서에 따라 이 단정이 깨질 수 있으니, 그땐 connect 후 setChatDroppedHandler(null) 등으로 격리할 것.
    expect(store.chat[0].pending).toBe(false)
  })

  it('보낼 때마다 새 clientMessageId를 발급한다(멱등 단위 분리)', () => {
    const store = useLiveStore()
    store.chat = []

    store.sendChat('첫째')
    store.sendChat('둘째')

    expect(store.chat).toHaveLength(2)
    expect(store.chat[0].clientMessageId).not.toBe(store.chat[1].clientMessageId)
  })

  it('뮤트 중이면 전송하지 않는다(낙관 행도 안 올린다)', () => {
    const store = useLiveStore()
    store.chat = []
    store.muted = true

    store.sendChat('안녕')

    expect(store.chat).toHaveLength(0)
  })

  it('공백만 있으면 전송하지 않는다', () => {
    const store = useLiveStore()
    store.chat = []

    store.sendChat('   ')

    expect(store.chat).toHaveLength(0)
  })

  it('CHAT-01 상한 250 코드 포인트를 넘으면 전송하지 않는다(낙관 행도 안 올린다)', () => {
    const store = useLiveStore()
    store.chat = []

    // 아래 251/250은 명세(CHAT-01) 값을 못 박은 리터럴이다. 상수를 따라가게 두면 상수를 잘못 바꿔도
    // 테스트가 같이 따라가 통과해버린다 — 대신 상수가 명세와 맞는지를 여기서 한 번 확인한다.
    expect(CHAT_MAX_LENGTH).toBe(250)

    store.sendChat('a'.repeat(251))

    expect(store.chat).toHaveLength(0)
    // 경계값 250은 통과한다
    store.sendChat('a'.repeat(250))
    expect(store.chat).toHaveLength(1)
  })
})

// 상한 적용 시 낙관 행(pending/failed) 예외 처리(S15P11A704-399) — 순수 함수라 스토어 없이 검증한다.
const confirmed = (key: string): ChatMessage => chatRow({ clientMessageId: key, pending: false })
const pendingRow = (key: string): ChatMessage => chatRow({ clientMessageId: key, pending: true })
const failedRow = (key: string): ChatMessage =>
  chatRow({ clientMessageId: key, pending: false, failed: true })
const keys = (chat: ChatMessage[]) => chat.map((m) => m.clientMessageId)

describe('capChat', () => {
  it('상한 이하면 그대로 반환한다', () => {
    const chat = [confirmed('a'), confirmed('b')]
    expect(capChat(chat, 2)).toBe(chat)
  })

  it('낙관 행이 없으면 오래된 확정 메시지부터 버린다(종전 slice와 동일)', () => {
    expect(keys(capChat([confirmed('a'), confirmed('b'), confirmed('c')], 2))).toEqual(['b', 'c'])
  })

  it('보호 행(pending)은 상한에 안 세고, 초과 시 오래된 확정만 버린다', () => {
    // 상한 2, 확정 3(a·b·c) → 초과 1: 오래된 확정 'a'만 버림. pending 'x'는 상한에 안 셈 + 보호.
    expect(
      keys(capChat([pendingRow('x'), confirmed('a'), confirmed('b'), confirmed('c')], 2)),
    ).toEqual(['x', 'b', 'c'])
  })

  it('failed 행도 상한에서 제외하고 보호한다', () => {
    expect(
      keys(capChat([failedRow('y'), confirmed('a'), confirmed('b'), confirmed('c')], 2)),
    ).toEqual(['y', 'b', 'c'])
  })

  it('과거 조회(older) 행도 상한에서 제외하고 보호한다 — 라이브 한 건에 잘려나가지 않게(379 리뷰 1)', () => {
    const olderRow = (key: string) => chatRow({ clientMessageId: key, pending: false, older: true })
    // older 2 + 확정 3, 상한 2 → 확정 초과 1: 오래된 확정 'a'만 버림. older는 상한에 안 세고 보호.
    expect(
      keys(
        capChat(
          [olderRow('o1'), olderRow('o2'), confirmed('a'), confirmed('b'), confirmed('c')],
          2,
        ),
      ),
    ).toEqual(['o1', 'o2', 'b', 'c'])
  })

  it('버릴 확정 메시지가 없으면(전부 낙관 행) 상한을 넘겨도 유지한다 — 미전송을 잃지 않는다', () => {
    const chat = [pendingRow('x'), pendingRow('y'), pendingRow('z')]
    expect(keys(capChat(chat, 2))).toEqual(['x', 'y', 'z'])
  })
})

const serverRow = (id: number): ChatMessage =>
  chatRow({ id: String(id), clientMessageId: null, pending: false })

describe('useLiveStore.loadOlderChat (S15P11A704-379)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    fetchMock.mockReset()
  })

  it('가장 오래된 확정 메시지 id를 커서로 조회해 앞에 붙이고 hasMore를 갱신한다', async () => {
    const store = useLiveStore()
    store.joinRoom(1)
    store.chat = [serverRow(100), serverRow(101)]
    fetchMock.mockResolvedValue({ messages: [serverRow(98), serverRow(99)], hasMore: false })

    await store.loadOlderChat()

    expect(fetchMock).toHaveBeenCalledWith(1, '100') // roomId, 가장 오래된 확정 id(ULID 문자열)
    expect(store.chat.map((m) => m.id)).toEqual(['98', '99', '100', '101']) // 앞에 prepend
    expect(store.chatHasMore).toBe(false)
    expect(store.chatLoadingOlder).toBe(false)
  })

  it('이미 있는 id는 제외한다(경계 겹침 dedup)', async () => {
    const store = useLiveStore()
    store.joinRoom(1)
    store.chat = [serverRow(100), serverRow(101)]
    fetchMock.mockResolvedValue({ messages: [serverRow(99), serverRow(100)], hasMore: true })

    await store.loadOlderChat()

    expect(store.chat.map((m) => m.id)).toEqual(['99', '100', '101']) // 100 중복 제외
  })

  it('로딩 중이면 무동작(중복 요청 방지)', async () => {
    const store = useLiveStore()
    store.joinRoom(1)
    store.chat = [serverRow(100)]
    store.chatLoadingOlder = true

    await store.loadOlderChat()

    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('더 없으면(!hasMore) 무동작', async () => {
    const store = useLiveStore()
    store.joinRoom(1)
    store.chat = [serverRow(100)]
    store.chatHasMore = false

    await store.loadOlderChat()

    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('확정 메시지가 없으면(빈 채팅창·낙관 행뿐) 커서 없이 첫 페이지를 시드한다 (S15P11A704-679)', async () => {
    const store = useLiveStore()
    store.joinRoom(1)
    store.chat = [pendingRow('x')] // 임시키 opt-N(pending) — 확정 커서 후보 아님
    fetchMock.mockResolvedValue({ messages: [serverRow(98), serverRow(99)], hasMore: true })

    await store.loadOlderChat()

    expect(fetchMock).toHaveBeenCalledWith(1, undefined) // before 생략 → 서버가 최신 한 페이지를 준다
    expect(store.chat.map((m) => m.id)).toEqual(['98', '99', 'opt-t']) // 낙관 행(chatRow 기본 id) 앞에 시드
  })

  it('조회 실패는 조용히 삼키고 로딩 플래그를 되돌린다(재시도 가능)', async () => {
    const store = useLiveStore()
    store.joinRoom(1)
    store.chat = [serverRow(100)]
    fetchMock.mockRejectedValue(new Error('network'))

    await store.loadOlderChat()

    expect(store.chatLoadingOlder).toBe(false)
    expect(store.chatHasMore).toBe(true) // 유지 → 재스크롤 시 재시도
  })

  it('4xx(INVALID_CURSOR 등)면 hasMore를 내리고 차단 표시를 세워 재요청을 끊는다 (S15P11A704-679)', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const store = useLiveStore()
    store.joinRoom(1)
    store.chat = [serverRow(100)]
    fetchMock.mockRejectedValue(
      new ApiError('커서가 올바르지 않아요', { status: 400, code: 'INVALID_CURSOR' }),
    )

    await store.loadOlderChat()

    expect(store.chatHasMore).toBe(false)
    // 「대화의 처음이에요」와 구분되는 종료다 — 화면이 이 플래그로 그 배너를 억제한다.
    expect(store.chatOlderBlocked).toBe(true)
    expect(warn.mock.calls[0]?.[0]).toContain('INVALID_CURSOR') // 원인 코드를 흔적으로 남긴다

    await store.loadOlderChat() // 다시 스크롤해도 같은 답이라 안 부른다
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('5xx·네트워크 실패는 차단으로 보지 않는다 — 재시도 가능한 실패다', async () => {
    const store = useLiveStore()
    store.joinRoom(1)
    store.chat = [serverRow(100)]
    fetchMock.mockRejectedValue(new Error('network'))

    await store.loadOlderChat()

    expect(store.chatOlderBlocked).toBe(false)
  })

  // 방 세대 가드 (S15P11A704-679, 367 관용구). 응답을 기다리는 사이 방이 바뀌면 이전 방의 이력이다.
  it('응답이 도착하기 전에 방이 바뀌었으면 그 페이지를 버린다', async () => {
    const store = useLiveStore()
    store.joinRoom(1)
    store.chat = [serverRow(100)]
    let resolvePage: (page: unknown) => void = () => {}
    fetchMock.mockReturnValue(
      new Promise((resolve) => {
        resolvePage = resolve
      }),
    )

    const loading = store.loadOlderChat()
    store.joinRoom(2) // 다른 방으로 이동($reset — chat·플래그가 비워진다)
    store.chat = [serverRow(500)] // 새 방이 자기 메시지를 받기 시작했다
    resolvePage({ messages: [serverRow(98), serverRow(99)], hasMore: false })
    await loading

    expect(store.chat.map((m) => m.id)).toEqual(['500']) // 이전 방의 이력이 섞이지 않는다
    expect(store.chatHasMore).toBe(true) // 이전 방의 hasMore:false도 새 방에 옮겨 붙지 않는다
  })

  it('꼬리가 늦게 도착해도 새 방의 로딩 플래그를 덮지 않는다 — 덮으면 중복 요청이 샌다', async () => {
    const store = useLiveStore()
    store.joinRoom(1)
    store.chat = [serverRow(100)]
    let rejectPage: (error: unknown) => void = () => {}
    fetchMock.mockReturnValue(
      new Promise((_resolve, reject) => {
        rejectPage = reject
      }),
    )

    const loading = store.loadOlderChat()
    store.joinRoom(2)
    store.chatLoadingOlder = true // 새 방이 자기 조회를 걸어 둔 상태
    rejectPage(new ApiError('없는 방', { status: 404, code: 'AUCTION_NOT_FOUND' }))
    await loading

    expect(store.chatLoadingOlder).toBe(true)
    expect(store.chatOlderBlocked).toBe(false) // 이전 방의 404를 새 방의 차단으로 기록하지 않는다
  })

  it('older 보관 상한(CHAT_OLDER_MAX)에 도달하면 더 안 불러온다(406 — 무한 누적 방어)', async () => {
    const store = useLiveStore()
    store.joinRoom(1)
    const older = Array.from({ length: CHAT_OLDER_MAX }, (_, i) => ({
      ...serverRow(1000 + i),
      older: true,
    }))
    store.chat = [...older, serverRow(3000)]
    fetchMock.mockResolvedValue({ messages: [serverRow(999)], hasMore: true })

    await store.loadOlderChat()

    expect(fetchMock).not.toHaveBeenCalled() // 상한 도달 → 조회 안 함
  })
})

// 빈 채팅창 이력 시드 (S15P11A704-679).
//
// 시드는 ChatPanel의 onMounted가 아니라 **방을 잡는 연결 경로**에 있다 — Vue가 자식을 부모보다 먼저
// 마운트하므로 패널에 두면 LiveView의 connect()(→ joinRoom → $reset)보다 앞서 돌고, 이전 방의 roomId로
// 나간 요청의 응답이 $reset 뒤에 도착한다. 여기 있으면 방 확정과 시드가 같은 함수 안에서 순서대로 일어난다.
describe('useLiveStore 채팅 이력 시드 (S15P11A704-679)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    fetchMock.mockReset()
    fetchMock.mockResolvedValue({ messages: [], hasMore: false })
    // 이력 조회는 인증이 필수라 기본 픽스처는 로그인 상태다 — 비로그인은 아래 전용 케이스에서 본다.
    useAuthStore().user = {
      id: 25,
      nickname: '준호',
      role: 'USER',
      status: 'ACTIVE',
      withdrawnAt: null,
    }
  })
  afterEach(async () => {
    mockApi.value = true
    await useLiveStore().disconnect()
  })

  it('connect는 방을 잡은 뒤 커서 없이 첫 페이지를 시드한다', async () => {
    mockApi.value = false
    const store = useLiveStore()

    await store.connect(7)

    // roomId는 joinRoom이 이미 잡았다 — 시드가 그 뒤라 0이나 이전 방으로 나가지 않는다.
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledWith(7, undefined))
  })

  it('connectWaitingRoom도 같은 자리에서 시드한다 — 대기 채팅도 입장하면 이력이 보여야 한다', async () => {
    mockApi.value = false
    const store = useLiveStore()
    store.joinRoom(7) // 호출부(WaitingRoomView.enter)가 먼저 잡는다

    await store.connectWaitingRoom(7)

    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledWith(7, undefined))
  })

  it('이미 채팅이 있으면 시드하지 않는다 — 채팅을 넘겨받은 입장에서 중복 조회를 막는다', async () => {
    mockApi.value = false
    const store = useLiveStore()
    store.joinRoom(7)
    store.chat = [serverRow(100)]

    await store.connectWaitingRoom(7)

    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('비로그인이면 시드하지 않는다 — 확정 401이 세울 차단이 로그인 후까지 남는다', async () => {
    mockApi.value = false
    useAuthStore().user = null
    const store = useLiveStore()

    await store.connect(7)

    expect(fetchMock).not.toHaveBeenCalled()
    expect(store.chatOlderBlocked).toBe(false)
  })
})

describe('useLiveStore 전송 실패 조치 (S15P11A704-400)', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('retryChat: 실패를 해제하고 같은 키·내용으로 재전송한다', () => {
    const store = useLiveStore()
    store.chat = [failedRow('c1')]

    store.retryChat('c1')

    expect(store.chat[0].failed).toBe(false)
    // pending은 sendChat과 동일하게 chatPublish 유무로 정해진다(리뷰 3) — 이 테스트는 전송 계층 미주입이라 false.
    expect(store.chat[0].pending).toBe(false)
    expect(store.chat[0].clientMessageId).toBe('c1') // 멱등 — 같은 키 재사용
    expect(store.chat[0].text).toBe('안녕하세요')
  })

  it('retryChat: 실패 상태가 아니면 무시', () => {
    const store = useLiveStore()
    store.chat = [confirmed('c1')]

    store.retryChat('c1')

    expect(store.chat[0].pending).toBeFalsy()
  })

  it('retryChat: 뮤트 중이면 무시한다(pending으로 안 바뀜 — 영구 「전송 중」·지울 수 없는 행 방지, 리뷰 1)', () => {
    const store = useLiveStore()
    store.chat = [failedRow('c1')]
    store.muted = true

    store.retryChat('c1')

    expect(store.chat[0].failed).toBe(true) // 그대로 실패
    expect(store.chat[0].pending).toBeFalsy()
  })

  it('dismissChatFailed: 실패 행만 제거하고 다른 행은 유지', () => {
    const store = useLiveStore()
    store.chat = [failedRow('c1'), confirmed('c2')]

    store.dismissChatFailed('c1')

    expect(store.chat.map((m) => m.clientMessageId)).toEqual(['c2'])
  })

  it('dismissChatFailed: 같은 키라도 실패 아닌 행은 안 지운다(확정 메시지 보호)', () => {
    const store = useLiveStore()
    store.chat = [confirmed('c1')]

    store.dismissChatFailed('c1')

    expect(store.chat).toHaveLength(1)
  })
})

// 낙관 행 전송 타임아웃 (S15P11A704-424).
//
// pending은 서버 에코 승격이나 큐 폐기 신호(markChatFailed)로만 풀렸다. 둘 다 오지 않는 경우
// — 순단으로 SEND가 새거나 에코만 유실되면 — 「전송 중…」이 영구히 남고, pending은 capChat 보호 행이라
// 트림도 안 되며 재시도·닫기(400)는 failed 행만 대상이라 그 경로로도 못 들어갔다.
describe('useLiveStore 낙관 행 전송 타임아웃 (S15P11A704-424)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockApi.value = false // 실 모드 — connect()가 chatPublish를 주입해 pending이 선다
    vi.useFakeTimers()
  })
  afterEach(async () => {
    vi.useRealTimers()
    // chatPublish는 createPinia()로 리셋되지 않는 **모듈 상태**다. 비우지 않으면 이 파일 다른 테스트의
    // `pending === false` 단정이 실행 순서에 따라 깨진다(파일 상단 sendChat 테스트의 경고 그대로).
    await useLiveStore().disconnect()
    mockApi.value = true
  })

  const connected = async () => {
    const store = useLiveStore()
    await store.connect(1)
    return store
  }

  it('응답이 없으면 타임아웃 뒤 pending을 failed로 승격시킨다', async () => {
    const store = await connected()
    store.sendChat('안녕하세요')
    expect(store.chat[0].pending).toBe(true)

    vi.advanceTimersByTime(CHAT_SEND_TIMEOUT_MS)

    expect(store.chat[0].pending).toBe(false)
    expect(store.chat[0].failed).toBe(true) // 재시도·닫기 경로로 떨어진다
  })

  it('타임아웃 전에는 대기 상태를 유지한다 — 정상 왕복을 실패로 만들지 않는다', async () => {
    const store = await connected()
    store.sendChat('안녕하세요')

    vi.advanceTimersByTime(CHAT_SEND_TIMEOUT_MS - 1)

    expect(store.chat[0].pending).toBe(true)
    expect(store.chat[0].failed).toBeUndefined()
  })

  it('에코가 먼저 오면 확정 메시지를 실패로 만들지 않는다', async () => {
    const store = await connected()
    store.sendChat('안녕하세요')
    const id = store.chat[0].clientMessageId
    if (!id) throw new Error('clientMessageId 없음')

    // 에코 승격을 흉내 낸다 — handlers.handleChatMessage는 같은 clientMessageId를 유지한 채 행을 **교체**하고
    // pending 플래그는 서버 값에 없다. 그래서 타임아웃이 pending을 안 보면 전송된 메시지가 실패로 표시된다.
    store.chat = [
      {
        id: 'srv-1',
        nickname: '나',
        text: '안녕하세요',
        isQuestion: false,
        clientMessageId: id,
        maskedFields: [],
        createdAt: '2026-01-01T00:00:00.000Z',
      },
    ]

    vi.advanceTimersByTime(CHAT_SEND_TIMEOUT_MS)

    expect(store.chat[0].failed).toBeUndefined()
    expect(store.chat[0].id).toBe('srv-1') // 확정 행 그대로
  })

  it('재시도한 행에도 타임아웃을 다시 건다 — 재시도만 영구 대기에 빠지지 않게', async () => {
    const store = await connected()
    store.chat = [chatRow({ clientMessageId: 'c1', pending: false, failed: true })]

    store.retryChat('c1')
    expect(store.chat[0].pending).toBe(true)

    vi.advanceTimersByTime(CHAT_SEND_TIMEOUT_MS)

    expect(store.chat[0].failed).toBe(true)
  })

  // failed가 되는 경로가 타이머를 이미 없애므로 dismissChatFailed엔 정리 코드가 없다 — 그 불변식을 잠근다.
  it('실패 표시 후 닫기까지 가도 타이머가 남지 않는다', async () => {
    const store = await connected()
    store.sendChat('안녕하세요')
    const id = store.chat[0].clientMessageId
    if (!id) throw new Error('clientMessageId 없음')
    expect(vi.getTimerCount()).toBe(1)

    store.markChatFailed(id)
    store.dismissChatFailed(id)

    expect(vi.getTimerCount()).toBe(0) // 지운 행을 뒤늦게 승격시킬 타이머가 없다
    expect(store.chat).toHaveLength(0)
  })

  it('퇴장하면 대기 타이머를 전량 정리한다', async () => {
    const store = await connected()
    store.sendChat('하나')
    store.sendChat('둘')
    expect(vi.getTimerCount()).toBe(2)

    await store.disconnect()

    expect(vi.getTimerCount()).toBe(0)
  })
})

// 화면 전환의 늦은 꼬리 (S15P11A704-329).
//
// connect·connectWaitingRoom·disconnect는 셋 다 동적 import를 await한 뒤에야 실제 연결·정리와 상태 쓰기를
// 한다. 떠나는 화면의 꼬리가 다음 화면이 연결을 세운 뒤에 도착하면 (a) connectionStatus를 덮고
// (b) `disconnectLiveSocket`이 `active`의 주인을 안 보므로 **다음 화면의 STOMP 연결을 끊는다.**
//
// ⚠️ **실 모드 경합은 이 하네스에서 재현할 수 없다.** vitest는 같은 모듈의 동적 import가 겹치면 목 모듈을
//    한 번 더 인스턴스화한다 — 스토어가 실제로 부른 함수가 여기 hoisted된 것과 다른 객체가 되고
//    (호출은 되는데 `mock.calls`는 0), 상태 단정도 어느 인스턴스를 보는지에 따라 흔들린다.
//    실제로 가드를 전부 빼고 돌려 봤더니 **경합 테스트가 그대로 통과했다** — 즉 아무것도 못 지킨다.
//    브라우저에는 없는 현상이라(같은 specifier의 `import()`는 항상 같은 모듈) 그런 테스트는 두지 않는다.
//
// 남긴 둘은 **가드를 빼면 실제로 깨지는 것만** 골랐다:
//   · 목 모드 connect 꼬리 — 목도 `await import('@/mocks/liveFeed')`를 거쳐 같은 구멍이 있다
//   · 겹치지 않는 정상 퇴장 — 가드가 평소 동작을 막지 않는지
describe('useLiveStore 연결 세대 가드 (S15P11A704-329)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })
  afterEach(async () => {
    mockApi.value = true
    await useLiveStore().disconnect()
  })

  it('떠난 뒤 도착한 connect 꼬리가 목 피드를 되살리지 않는다', async () => {
    // 목 disconnect는 await가 없어 동기로 끝난다. 그래서 순서를
    // 「connect 시작 → disconnect 완료 → connect 꼬리 도착」으로 만들 수 있다.
    // 가드가 없으면 이미 떠난 화면의 꼬리가 connected로 되돌리고 피드까지 다시 돌린다.
    mockApi.value = true
    const store = useLiveStore()

    const entering = store.connect(1)
    await store.disconnect()
    await entering

    expect(store.connectionStatus).toBe('closed')
  })

  it('겹치지 않으면 평소대로 끊고 closed로 간다 — 가드가 정상 퇴장을 막지 않는다', async () => {
    mockApi.value = false
    const store = useLiveStore()
    await store.connect(1)
    vi.clearAllMocks()

    await store.disconnect()

    expect(socketMock.disconnectLiveSocket).toHaveBeenCalledTimes(1)
    expect(store.connectionStatus).toBe('closed')
  })
})

// 방 입장 일련번호 (S15P11A704-367).
//
// 세 화면(Live·Waiting·Console)이 언마운트 정리에서 「내가 열었던 입장이 아직 살아 있나」를 묻는다.
// 예전엔 `roomId`로 물었는데, **같은 방을 다시 여는 경로**에서는 번호가 같아 대조가 통과했다 —
// 콘솔의 「라이브로 이동」이 실제로 그 경로고, 그때 지연 `$reset()`이 라이브가 방금 세운 상태를 지웠다.
describe('useLiveStore 방 입장 일련번호 (S15P11A704-367)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('같은 방을 다시 열어도 입장 번호가 달라진다 — roomId로는 구분되지 않는 자리다', () => {
    const store = useLiveStore()

    store.joinRoom(7)
    const first = store.roomSession
    store.joinRoom(7) // 콘솔 → 같은 방 라이브

    expect(store.roomId).toBe(7) // 방 번호는 그대로라 대조가 통과해 버린다
    expect(store.roomSession).not.toBe(first) // 입장 번호는 달라진다
  })

  it('입장 번호는 되돌아가지 않는다 — 이전 화면의 캡처와 절대 다시 같아지지 않게', () => {
    const store = useLiveStore()

    store.joinRoom(1)
    const first = store.roomSession
    store.joinRoom(2)
    store.joinRoom(1) // 원래 방으로 되돌아와도

    expect(store.roomSession).toBeGreaterThan(first)
  })

  it('$reset()은 입장 번호를 0으로 되돌린다 — 아무도 들고 있지 않은 값이라 정리가 안전하다', () => {
    const store = useLiveStore()
    store.joinRoom(7)

    store.$reset()

    expect(store.roomSession).toBe(0)
  })
})

// TIME_SYNC 도착 전 구간 (S15P11A704-265).
//
// serverOffset의 쓰기 주체는 TIME_SYNC 하나뿐이라 소켓이 서기 전까지 null이고, 그동안 라이브·콘솔의
// 타이머가 '--:--'로 굳었다. REST(목록·상세 응답의 serverTime)는 그 값을 이미 갖고 있으므로
// 읽기 쪽에서만 폴백한다 — 우선순위가 뒤집히면 소켓이 준 최신 보정을 REST 값이 덮어쓴다.
describe('useLiveStore clockOffset (S15P11A704-265)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('TIME_SYNC 전에는 REST가 준 오프셋을 쓴다', () => {
    const live = useLiveStore()
    useRoomStore().serverOffset = 1_500

    expect(live.serverOffset).toBeNull()
    expect(live.clockOffset).toBe(1_500)
  })

  it('TIME_SYNC가 오면 그쪽이 이긴다', () => {
    const live = useLiveStore()
    useRoomStore().serverOffset = 1_500
    live.serverOffset = 20

    expect(live.clockOffset).toBe(20)
  })

  it('TIME_SYNC가 0을 줬으면 0이다 — REST 값으로 되돌아가지 않는다', () => {
    const live = useLiveStore()
    useRoomStore().serverOffset = 1_500
    live.serverOffset = 0

    expect(live.clockOffset).toBe(0) // ?? 대신 ||로 적으면 여기서 1500이 된다
  })

  it('양쪽 다 없으면 null — 남은 시간을 지어내지 않는다 (규칙 5)', () => {
    expect(useLiveStore().clockOffset).toBeNull()
  })
})

// 마감 시각도 같은 모양의 폴백이 필요하다 (S15P11A704-523).
// 265에서 오프셋만 메웠을 때 라이브 칩이 그대로 `--:--`였던 이유가 이것 — useCountdown은
// 마감 시각이 없으면 오프셋을 보지도 않는다. 상세(ROOM-03)엔 endAt이 없어 목록이 유일한 REST 출처다.
describe('useLiveStore deadlineAt (S15P11A704-523)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  /** 목록 카드 한 장만 있으면 되는 테스트라 `deadlineOf`가 보는 필드만 채운다 */
  function seedListRoom(auctionId: number, endAt: string | null) {
    const room = useRoomStore()
    room.rooms = [{ auctionId, endAt } as (typeof room.rooms)[number]]
  }

  it('소켓 전에는 목록에서 받아둔 endAt을 쓴다', () => {
    const live = useLiveStore()
    live.joinRoom(7)
    seedListRoom(7, '2026-07-31T12:00:00+09:00')

    expect(live.endsAt).toBeNull()
    expect(live.deadlineAt).toBe(Date.parse('2026-07-31T12:00:00+09:00'))
  })

  it('TIME_SYNC가 오면 그쪽이 이긴다 — 연장(AUCTION_EXTENDED)이 목록 값에 덮이면 안 된다', () => {
    const live = useLiveStore()
    live.joinRoom(7)
    seedListRoom(7, '2026-07-31T12:00:00+09:00')
    live.endsAt = Date.parse('2026-07-31T12:05:00+09:00') // +5분 연장된 값

    expect(live.deadlineAt).toBe(Date.parse('2026-07-31T12:05:00+09:00'))
  })

  it('다른 방의 카드는 쓰지 않는다', () => {
    const live = useLiveStore()
    live.joinRoom(7)
    seedListRoom(99, '2026-07-31T12:00:00+09:00')

    expect(live.deadlineAt).toBeNull()
  })

  it('시작 전이라 endAt이 null이면 null — 딥링크로 목록을 안 거친 경우도 같다', () => {
    const live = useLiveStore()
    live.joinRoom(7)
    seedListRoom(7, null)
    expect(live.deadlineAt).toBeNull()

    useRoomStore().rooms = []
    expect(live.deadlineAt).toBeNull()
  })
})
