import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as questionsApi from '@/shared/api/questions'
import { ApiError, API_ERROR_CODE } from '@/shared/api/errors'
import type { CursorPage } from '@/shared/api/types'
import type { QuestionItem } from '@/stores/live'
import { QUESTION_SEED_PAGES, useQuestionQueue } from './useQuestionQueue'

// 커서 부기는 **눈으로 안 잡힌다.** 상한(100건)을 넘긴 큐를 브라우저에서 만들려면 질문을 100건 넣어야 하고,
// 「재시드와 「더 보기」가 겹치는」 경합은 손으로는 아예 못 만든다.

type Page = CursorPage<questionsApi.QuestionQueueItem>

function q(id: string): questionsApi.QuestionQueueItem {
  return { questionId: id, memberId: 1, nickname: '서연', content: id, createdAt: '2026-01-01' }
}

/** 페이지 하나 — `hasNext`면 다음 커서를 준다 */
function page(ids: string[], nextCursor: string | null): Page {
  return { items: ids.map(q), nextCursor, hasNext: nextCursor !== null }
}

/** 소켓이 넣은 항목(REST를 거치지 않은 것) */
const fromSocket = (id: string): QuestionItem => ({
  questionId: id,
  nickname: '실시간',
  content: id,
})

/**
 * 응답을 **호출 순서대로** 정해 둔다.
 * `mockResolvedValueOnce` 체인은 못 쓴다 — 시드가 상한까지 5번 부르므로 첫 하나만 소비되고
 * 나머지가 실제 목 구현으로 흘러 버린다(그 사고를 실제로 냈다).
 */
function script(...pages: (Page | (() => Promise<Page>))[]) {
  let i = 0
  getQueue.mockImplementation(async () => {
    const next = pages[i]
    i += 1
    if (next === undefined) throw new Error('대본에 없는 호출 ' + i)
    return typeof next === 'function' ? next() : next
  })
}

/** 시드가 상한(5페이지)을 다 쓰게 만드는 앞 4페이지 — 항목 없이 커서만 잇는다 */
const fillerPages = () =>
  Array.from({ length: QUESTION_SEED_PAGES - 1 }, (_, i) => page([], 'f' + i))

let getQueue: ReturnType<typeof vi.spyOn>
let resolveApi: ReturnType<typeof vi.spyOn>

beforeEach(() => {
  vi.restoreAllMocks()
  getQueue = vi.spyOn(questionsApi, 'getQuestionQueue')
  resolveApi = vi.spyOn(questionsApi, 'resolveQuestion').mockResolvedValue(undefined)
})

const apiError = (code: string) => new ApiError('서버 오류', { status: 400, code })

/** 늘 실패하는 응답 하나 — `script`의 자리 하나를 차지한다 */
const fails = (code: string) => () => Promise.reject(apiError(code))

const setup = (initial: QuestionItem[] = []) => {
  const sink = { questions: initial }
  const onHostRejected = vi.fn()
  return { sink, onHostRejected, api: useQuestionQueue(() => 7, sink, onHostRejected) }
}

const ids = (sink: { questions: QuestionItem[] }) => sink.questions.map((x) => x.questionId)

describe('useQuestionQueue 시드', () => {
  it('상한까지만 받고 남은 커서를 들고 있는다', async () => {
    // 커서를 계속 물려 줘서 상한에서만 멈추게 한다
    script(...fillerPages(), page(['a'], 'c-last'))
    const { api } = setup()

    await api.load()

    expect(getQueue).toHaveBeenCalledTimes(QUESTION_SEED_PAGES)
    expect(api.hasMore.value).toBe(true) // 남았다 → 「더 보기」가 뜬다
  })

  it('상한 전에 끝나면 더 보기가 안 뜬다', async () => {
    getQueue.mockResolvedValue(page(['a', 'b'], null))
    const { api } = setup()

    await api.load()

    expect(getQueue).toHaveBeenCalledTimes(1)
    expect(api.hasMore.value).toBe(false)
  })

  it('소켓이 넣은 항목을 시드가 덮지 않는다 — 그쪽이 더 최신이다', async () => {
    getQueue.mockResolvedValue(page(['a', 'b'], null))
    const { api, sink } = setup([fromSocket('z')])

    await api.load()

    expect(ids(sink)).toEqual(['a', 'b', 'z'])
  })
})

describe('useQuestionQueue 더 보기', () => {
  it('이어 받은 페이지는 소켓 항목 **앞**에 끼운다 — createdAt ASC가 깨지지 않게', async () => {
    // 소켓이 넣은 것은 라이브 중 도착이라 제일 최신이고, 이어 받는 구간은 그보다 오래됐다.
    // 그냥 뒤에 붙이면 오래된 100건 → 최신 → 중간 구간 순서가 된다.
    script(...fillerPages(), page(['a', 'b'], 'c1'), page(['c', 'd'], null))
    const { api, sink } = setup([fromSocket('z')])
    await api.load()
    expect(ids(sink)).toEqual(['a', 'b', 'z'])

    await api.loadMore()

    expect(ids(sink)).toEqual(['a', 'b', 'c', 'd', 'z'])
  })

  it('이미 있는 항목은 다시 넣지 않는다', async () => {
    script(...fillerPages(), page(['a'], 'c1'), page(['a', 'b', 'c'], null))
    const { api, sink } = setup([fromSocket('b')])
    await api.load()

    await api.loadMore()

    expect(ids(sink)).toEqual(['a', 'c', 'b'])
    expect(api.lastAppended.value).toBe(1)
  })

  it('남은 게 없으면 아무 요청도 안 나간다', async () => {
    getQueue.mockResolvedValue(page(['a'], null))
    const { api } = setup()
    await api.load()
    getQueue.mockClear()

    await api.loadMore()

    expect(getQueue).not.toHaveBeenCalled()
  })
})

// S15P11A704-329·367에서 두 번 고친 그 부류. 재연결 재시드가 「더 보기」 응답보다 먼저 끝나면,
// 늦게 도착한 낡은 응답이 재시드가 세운 커서를 덮는다 — 남은 게 있는데 버튼이 사라진다.
describe('useQuestionQueue 재시드와 겹칠 때', () => {
  it('늦게 도착한 더 보기 응답이 재시드의 커서를 덮지 않는다', async () => {
    let releaseMore: (value: Page) => void = () => {}
    script(
      ...fillerPages(),
      page(['a'], 'c1'), // 시드 완료 — 아직 남았다
      () => new Promise<Page>((resolve) => (releaseMore = resolve)), // 「더 보기」 (붙잡아 둔다)
      ...fillerPages(),
      page(['a', 'b'], 'c9'), // 재시드 완료 — 여전히 남았다(c9)
    )
    const { api, sink } = setup()
    await api.load()

    const more = api.loadMore() // 응답을 붙잡아 둔 채
    await api.load() // 그 사이 재연결 재시드가 완주한다
    expect(api.hasMore.value).toBe(true)

    // 이제 낡은 「더 보기」 응답이 도착한다 — hasNext:false라 예전엔 커서를 null로 덮었다
    releaseMore(page(['x'], null))
    await more

    expect(api.hasMore.value).toBe(true) // 재시드가 세운 커서가 살아 있다
    expect(ids(sink)).toEqual(['a', 'b']) // 낡은 응답의 항목도 안 붙는다
  })

  it('진 쪽도 버튼 잠금은 푼다 — 안 그러면 다시 못 누른다', async () => {
    let releaseMore: (value: Page) => void = () => {}
    script(
      ...fillerPages(),
      page(['a'], 'c1'),
      () => new Promise<Page>((resolve) => (releaseMore = resolve)),
      ...fillerPages(),
      page(['a'], 'c9'),
    )
    const { api } = setup()
    await api.load()

    const more = api.loadMore()
    await api.load()
    releaseMore(page(['x'], null))
    await more

    expect(api.loadingMore.value).toBe(false)
  })
})

// 세대 번호를 **대칭으로** 공유하면 이번엔 「더 보기」가 재시드를 죽인다 (!175 리뷰 2차).
// 둘의 손실이 다르다 — 「더 보기」가 취소되면 한 페이지를 잃고 다시 누르면 끝이지만,
// 재시드가 취소되면 끊긴 동안 들어온 질문 전량을 잃고 다음 재연결까지 복구 지점이 없다.
describe('useQuestionQueue 재시드가 먼저 돌고 있을 때', () => {
  it('그동안 누른 「더 보기」는 요청조차 안 나간다 — 재시드를 죽이지 않는다', async () => {
    let releaseSeed: (value: Page) => void = () => {}
    script(
      ...fillerPages(),
      page(['a'], 'c1'), // 1차 시드 완료 — 커서가 서야 「더 보기」가 시도라도 한다
      () => new Promise<Page>((resolve) => (releaseSeed = resolve)), // 재시드 1페이지에서 붙잡는다
      page(['a', 'b'], null), // 재시드 2페이지
    )
    const { api, sink } = setup()
    await api.load()
    expect(api.hasMore.value).toBe(true)

    const reseeding = api.load() // 재연결 재시드 시작 (1페이지 응답을 붙잡아 둔 상태)
    const callsBefore = getQueue.mock.calls.length

    await api.loadMore() // 그 와중에 클릭

    // 나가지 않았다. 나갔다면 재시드 2페이지 자리를 가로채고, 재시드는 세대에 밀려 죽는다.
    expect(getQueue.mock.calls.length).toBe(callsBefore)

    releaseSeed(page([], 'f0'))
    await reseeding

    // 재시드가 완주했다. (최종 목록은 두 경로가 같아져서 판별은 위 호출 수가 진다 —
    //  「더 보기」가 가로챈 경우에도 같은 항목이 붙기 때문이다.)
    expect(ids(sink)).toEqual(['a', 'b'])
    expect(api.hasMore.value).toBe(false)
  })

  it('재시드가 끝난 뒤의 「더 보기」는 정상이다 — 클릭이 영구히 막히지 않는다', async () => {
    script(...fillerPages(), page(['a'], 'c1'), page(['b'], null))
    const { api, sink } = setup()
    await api.load()

    await api.loadMore()

    expect(ids(sink)).toEqual(['a', 'b'])
    expect(api.hasMore.value).toBe(false)
  })
})

// 「더 보기 → 재연결 재시드 → 더 보기」 (!175 리뷰 3차).
//
// 재시드는 상한(5페이지)까지만 받는데 「더 보기」로 받아 둔 6페이지는 거기 안 들어온다.
// 그러면 그 항목들이 스토어에는 남은 채 `restIds`에서만 빠져 **소켓 항목으로 재분류**된다.
// 바로 다음 순서는 안 깨진다(filter가 상대 순서를 보존한다) — 깨지는 건 그다음 클릭이다.
describe('useQuestionQueue 더 보기 뒤의 재시드', () => {
  /** 시드 5페이지 + 「더 보기」 1페이지까지 간 상태를 만든다 */
  async function seededAndOnePageMore() {
    script(
      ...fillerPages(),
      page(['a'], 'c6'), // 시드 완료 — 6페이지가 남았다
      page(['b'], 'c7'), // 「더 보기」로 6페이지를 받는다
    )
    const { api, sink } = setup()
    await api.load()
    await api.loadMore()
    expect(ids(sink)).toEqual(['a', 'b'])
    return { api, sink }
  }

  it('재시드가 「더 보기」로 갔던 만큼 더 받는다 — 커서가 되돌아가지 않게', async () => {
    const { api, sink } = await seededAndOnePageMore()

    getQueue.mockClear()
    // 재시드: 5페이지가 아니라 **6페이지**를 받아야 커서가 c7로 돌아온다
    script(...fillerPages(), page(['a'], 'c6'), page(['b'], 'c7'))
    await api.load()

    expect(getQueue).toHaveBeenCalledTimes(6)
    expect(ids(sink)).toEqual(['a', 'b'])
  })

  it('재시드 뒤의 클릭이 같은 페이지를 다시 받지 않는다', async () => {
    const { api } = await seededAndOnePageMore()
    script(...fillerPages(), page(['a'], 'c6'), page(['b'], 'c7'))
    await api.load()
    getQueue.mockClear()

    // 커서가 c6으로 되돌아갔다면 여기서 'b'를 또 받고 붙는 게 0건이 된다
    script(page(['c'], null))
    await api.loadMore()

    expect(api.lastAppended.value).toBe(1)
  })

  it('재시드 뒤에도 정렬이 유지된다 — 7페이지가 6페이지 앞에 끼지 않게', async () => {
    const { api, sink } = await seededAndOnePageMore()
    script(...fillerPages(), page(['a'], 'c6'), page(['b'], 'c7'))
    await api.load()

    script(page(['c'], null))
    await api.loadMore()

    expect(ids(sink)).toEqual(['a', 'b', 'c'])
  })

  it('붙은 게 0건이어도 페이지 수는 센다 — 그 페이지가 이미 소켓으로 들어와 있을 수 있다', async () => {
    script(...fillerPages(), page(['a'], 'c6'), page(['z'], 'c7'))
    const { api } = setup([fromSocket('z')]) // 6페이지 내용이 이미 소켓으로 들어와 있다
    await api.load()
    await api.loadMore()
    expect(api.lastAppended.value).toBe(0)

    getQueue.mockClear()
    script(...fillerPages(), page(['a'], 'c6'), page(['z'], 'c7'))
    await api.load()

    expect(getQueue).toHaveBeenCalledTimes(6) // 재시드도 6페이지까지 간다
  })
})

// 시드는 여러 페이지를 이어 받으므로 **2페이지 이후에도** 커서가 죽을 수 있다 (S15P11A704-680 리뷰).
// 예전엔 그 하나가 바깥 catch로 떨어져 이미 받은 앞 페이지를 전량 버리고 BE 원문 오류를 띄웠다 —
// 큐가 비어 보이는데, 버려진 앞 페이지가 제일 오래된 = 판매자가 제일 먼저 답할 질문이다.
describe('useQuestionQueue 시드 도중 커서가 죽을 때', () => {
  it('받은 앞 페이지를 유지하고 오류를 띄우지 않는다', async () => {
    script(
      page(['a'], 'c1'),
      page(['b'], 'c2'),
      fails(API_ERROR_CODE.INVALID_CURSOR), // 3페이지 — 쥐고 있던 c2가 그사이 삭제됐다
    )
    const { api, sink } = setup([fromSocket('z')])

    await api.load()

    expect(getQueue).toHaveBeenCalledTimes(3)
    expect(api.error.value).toBeNull()
    expect(ids(sink)).toEqual(['a', 'b', 'z']) // 앞 2페이지도, 소켓 항목도 산다
    expect(api.hasMore.value).toBe(true) // 남은 건 사실이라 「더 보기」는 뜬다
  })

  it('커서 실패가 아닌 오류는 그대로 오류다 — 삼키지 않는다', async () => {
    script(page(['a'], 'c1'), fails(API_ERROR_CODE.INVALID_PAGE_SIZE))
    const { api, sink } = setup()

    await api.load()

    expect(api.error.value).toBe('서버 오류')
    expect(ids(sink)).toEqual([])
  })
})

// 커서로 쥔 질문 **메시지가 소프트 삭제**되면 서버가 400 `INVALID_CURSOR`로 막는다 (S15P11A704-680).
// 커서 조회는 `is_deleted = FALSE`만 걸고 `question_queue_status`는 안 보므로, 그 질문이
// RESOLVED(답변 완료)·FILTERED(단순 검열)가 된 것만으로는 커서가 죽지 않는다 — 죽이는 것은
// 호스트·관리자 삭제와 모더레이션 BLOCKED뿐이다.
// 그 커서는 영영 못 쓰므로 예전에는 「더 보기」가 누를 때마다 같은 400만 반복했다 — 그 방에서 상한
// 뒤의 질문을 볼 방법이 아예 없어진다. 복구는 커서를 버리고 첫 페이지부터 다시 받는 것뿐이다.
describe('useQuestionQueue 커서가 무효해졌을 때', () => {
  /** 시드가 상한에 걸려 커서를 쥔 상태 */
  const seeded = async () => {
    const ready = setup()
    await ready.api.load()
    expect(ready.api.hasMore.value).toBe(true)
    return ready
  }

  it('INVALID_CURSOR면 커서를 버리고 처음부터 다시 받는다 — 오류를 띄우지 않는다', async () => {
    script(
      ...fillerPages(),
      page(['a'], 'c6'), // 시드 완료
      fails(API_ERROR_CODE.INVALID_CURSOR), // 「더 보기」 — 쥐고 있던 커서가 죽었다
      ...fillerPages(),
      page(['a', 'b'], null), // 재조회로 복구
    )
    const { api, sink } = await seeded()

    await api.loadMore()

    expect(api.error.value).toBeNull()
    expect(ids(sink)).toEqual(['a', 'b'])
    expect(api.hasMore.value).toBe(false)
    expect(api.loadingMore.value).toBe(false)
  })

  // 복구가 항목을 붙여 놓고도 `lastAppended`가 0이면 화면이 「새로 불러온 질문은 없어요」로
  // 읽어 준다(`ConsoleView.onLoadMoreQuestions`) — 비시각 사용자에겐 복구가 실패한 것으로 들린다.
  it('복구로 늘어난 만큼을 통지 건수에 싣는다', async () => {
    script(
      ...fillerPages(),
      page(['a'], 'c6'),
      fails(API_ERROR_CODE.INVALID_CURSOR),
      ...fillerPages(),
      page(['a', 'b', 'c'], null), // 재조회로 2건이 늘었다
    )
    const { api, sink } = await seeded()

    await api.loadMore()

    expect(ids(sink)).toEqual(['a', 'b', 'c'])
    expect(api.lastAppended.value).toBe(2)
  })

  it('재조회도 실패하면 그 오류를 띄우고 멈춘다 — 되풀이하지 않는다', async () => {
    script(
      ...fillerPages(),
      page(['a'], 'c6'),
      fails(API_ERROR_CODE.INVALID_CURSOR),
      fails(API_ERROR_CODE.NOT_AUCTION_HOST), // 재조회 1페이지에서 바로 실패
    )
    const { api, onHostRejected } = await seeded()
    const callsBefore = getQueue.mock.calls.length

    await api.loadMore()

    // 「더 보기」 1회 + 재조회 1회뿐이다. 재조회가 또 loadMore를 부르면 대본이 바닥나 던진다.
    expect(getQueue.mock.calls.length).toBe(callsBefore + 2)
    expect(onHostRejected).toHaveBeenCalledOnce() // 재조회 쪽 오류 처리가 그대로 산다
  })

  it('다른 오류는 재조회하지 않고 문구만 띄운다', async () => {
    script(...fillerPages(), page(['a'], 'c6'), fails(API_ERROR_CODE.INVALID_PAGE_SIZE))
    const { api } = await seeded()
    const callsBefore = getQueue.mock.calls.length

    await api.loadMore()

    expect(getQueue.mock.calls.length).toBe(callsBefore + 1)
    expect(api.error.value).toBe('서버 오류')
    expect(api.hasMore.value).toBe(true) // 커서는 멀쩡하다 — 다시 누를 수 있다
  })
})

// 답변 완료도 조회와 **같은 403(`NOT_AUCTION_HOST`)**을 쓴다 (S15P11A704-680).
// 화면이 따로 잡던 시절엔 여기서만 오류 문구가 떴고 안내 카드로 안 바뀌어서, 큐가 그대로 남은 채
// 눌러도 계속 실패했다.
describe('useQuestionQueue 답변 완료', () => {
  it('성공하면 큐에서 지운다', async () => {
    const { api, sink } = setup([fromSocket('a'), fromSocket('b')])

    await api.resolve('a')

    expect(resolveApi).toHaveBeenCalledWith(7, 'a')
    expect(ids(sink)).toEqual(['b'])
    expect(api.resolving.value).toEqual([])
  })

  it('QUESTION_NOT_FOUND도 큐에서 지운다 — 서버 기준으로 처리가 끝난 항목이다', async () => {
    resolveApi.mockRejectedValue(apiError(API_ERROR_CODE.QUESTION_NOT_FOUND))
    const { api, sink } = setup([fromSocket('a')])

    await api.resolve('a')

    expect(ids(sink)).toEqual([])
    expect(api.error.value).toBeNull()
  })

  it('NOT_AUCTION_HOST는 조회와 같은 안내 경로로 보낸다 — 문구만 띄우지 않는다', async () => {
    resolveApi.mockRejectedValue(apiError(API_ERROR_CODE.NOT_AUCTION_HOST))
    const { api, sink, onHostRejected } = setup([fromSocket('a')])

    await api.resolve('a')

    expect(onHostRejected).toHaveBeenCalledOnce()
    expect(api.error.value).toBeNull()
    expect(ids(sink)).toEqual(['a']) // 권한 문제라 큐는 손대지 않는다
  })

  it('그 밖의 오류는 문구를 띄우고 큐를 남긴다', async () => {
    resolveApi.mockRejectedValue(apiError('SOMETHING_ELSE'))
    const { api, sink } = setup([fromSocket('a')])

    await api.resolve('a')

    expect(api.error.value).toBe('서버 오류')
    expect(ids(sink)).toEqual(['a'])
  })

  it('같은 질문의 중복 클릭은 한 번만 나간다', async () => {
    let release: () => void = () => {}
    resolveApi.mockImplementation(() => new Promise<void>((resolve) => (release = resolve)))
    const { api } = setup([fromSocket('a')])

    const first = api.resolve('a')
    await api.resolve('a') // 잠긴 동안의 두 번째 클릭

    expect(resolveApi).toHaveBeenCalledOnce()
    release()
    await first
  })
})
