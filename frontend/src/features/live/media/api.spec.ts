import { beforeEach, describe, expect, it, vi } from 'vitest'
import { http } from '@/shared/api/http'
import { API_ERROR_CODE, hasErrorCode } from '@/shared/api/errors'
import {
  issuePublisherToken,
  issueSubscriberToken,
  uploadCatchUpAudioChunk,
  uploadLiveSnapshot,
  type LiveTokenEnvelope,
} from './api'

// LIVE-01 미디어 토큰 봉투를 잠근다 (S15P11A704-521).
//
// 이 경로는 **실서버에서만 터졌다.** 응답이 `{ type, data }` 봉투인데 FE가 최상위 `token`을
// 읽고 있었고, 타입만 `{ token }`으로 붙어 있어 컴파일·목 모드는 멀쩡했다.
// 그래서 여기서 잠그는 것은 둘이다 — ① 봉투에서 꺼내는가 ② 옛 모양에 속지 않는가.
//
// 목 모드는 http에 닿지 않으므로(`isMockApi()` 조기 반환) 테스트는 실 모드 기준이다.

vi.mock('@/shared/api/mock', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/api/mock')>()),
  isMockApi: () => false,
}))

/**
 * 봉투 불일치는 전용 코드로 던진다 — 네트워크 실패·401과 섞이면 로그에서 「연결이 안 된다」로만
 * 보이고 정작 봐야 할 응답 모양이 묻힌다 (!245 리뷰 1).
 */
const isShapeError = (error: unknown): boolean =>
  hasErrorCode(error, API_ERROR_CODE.MEDIA_TOKEN_SHAPE_UNEXPECTED)

function envelope(role: 'PUBLISHER' | 'SUBSCRIBER', token: string): { data: LiveTokenEnvelope } {
  return {
    data: {
      type: role === 'PUBLISHER' ? 'LIVE_PUBLISHER_TOKEN_ISSUED' : 'LIVE_SUBSCRIBER_TOKEN_ISSUED',
      data: {
        roomId: '7',
        sessionId: 'ses_ABC',
        token,
        role,
        createdAt: '2026-07-31T09:00:00+09:00',
      },
    },
  }
}

describe('미디어 토큰 발급', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('시청자 — 봉투 안 data.token을 꺼낸다', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(envelope('SUBSCRIBER', 'sub-token'))

    await expect(issueSubscriberToken(7)).resolves.toEqual({ token: 'sub-token' })
  })

  it('판매자 — 봉투 안 data.token을 꺼낸다', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(envelope('PUBLISHER', 'pub-token'))

    await expect(issuePublisherToken(7)).resolves.toEqual({ token: 'pub-token' })
  })

  it('baseURL 기준 상대경로로 보낸다 — 미디어만 /rooms/{id}/live/* 다', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue(envelope('SUBSCRIBER', 't'))

    await issueSubscriberToken(7)
    expect(post.mock.calls[0][0]).toBe('/rooms/7/live/subscriber')

    post.mockResolvedValue(envelope('PUBLISHER', 't'))
    await issuePublisherToken(7)
    expect(post.mock.calls[1][0]).toBe('/rooms/7/live/publisher')
  })

  it('최상위 token만 있는 옛 모양에 속지 않는다 — 그대로 통과하면 session.connect(undefined)가 된다', async () => {
    // 봉투 이전의 FE 가정. 조용히 { token: undefined }를 돌려주면 실서버에서만 죽는다.
    vi.spyOn(http, 'post').mockResolvedValue({ data: { token: 'legacy-token' } })

    await expect(issueSubscriberToken(7)).rejects.toSatisfy(isShapeError)
  })

  it('봉투는 왔는데 data가 없는 모양도 던진다 — 그래서 payload?.data?.token의 ?.를 지우면 안 된다', async () => {
    // 타입상으로는 올 수 없는 모양이라 옵셔널 체이닝이 불필요해 보인다.
    // 서버가 data: null을 주면 타입이 거짓말이 되고 런타임만 남으므로 여기서 잠근다.
    vi.spyOn(http, 'post').mockResolvedValue({
      data: { type: 'LIVE_SUBSCRIBER_TOKEN_ISSUED', data: null },
    })

    await expect(issueSubscriberToken(7)).rejects.toSatisfy(isShapeError)
  })

  it('data.token이 빈 문자열이어도 던진다 — 빈 토큰으로는 세션에 붙지 못한다', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(envelope('SUBSCRIBER', ''))

    await expect(issueSubscriberToken(7)).rejects.toSatisfy(isShapeError)
  })
})

describe('방송 요약 음성 청크', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('완결된 음성 파일과 순서·구간 시각을 multipart로 보낸다', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ status: 202, data: undefined })

    await uploadCatchUpAudioChunk(7, {
      sequence: 3,
      startedAt: '2026-07-31T09:00:00.000Z',
      endedAt: '2026-07-31T09:00:15.000Z',
      audio: new Blob(['audio'], { type: 'audio/webm' }),
    })

    expect(post.mock.calls[0][0]).toBe('/auction-rooms/7/stt/chunks')
    const form = post.mock.calls[0][1] as FormData
    expect(form.get('sequence')).toBe('3')
    expect(form.get('startedAt')).toBe('2026-07-31T09:00:00.000Z')
    expect(form.get('endedAt')).toBe('2026-07-31T09:00:15.000Z')
    expect(form.get('audio')).toBeInstanceOf(Blob)
  })
})

// LIVE-06 낙찰 스냅샷 (S15P11A704-704) — **파트 이름과 경로가 계약의 전부다.**
// 이 엔드포인트는 한 번 「서버가 직접 캡처한다」로 잘못 읽혀 FE 구현이 통째로 지워진 적이 있다.
// 파트 이름이 어긋나면 서버는 400 INVALID_SNAPSHOT_REQUEST만 주고, 그 실패는 호출부가 삼키므로
// 화면상 아무 일도 안 일어난다 — 조용히 증빙만 안 남는 종류라 여기서 잠근다.
describe('낙찰 스냅샷 업로드', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('image·auctionId 파트로 /rooms/{id}/live/snapshot에 올린다', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ status: 201, data: undefined })

    await uploadLiveSnapshot(7, new Blob(['jpeg'], { type: 'image/jpeg' }))

    expect(post.mock.calls[0][0]).toBe('/rooms/7/live/snapshot')
    const form = post.mock.calls[0][1] as FormData
    expect(form.get('image')).toBeInstanceOf(Blob)
    // 숫자가 아니라 form field 문자열이다 — 명세가 number라고 적은 건 파싱 후 타입이다.
    expect(form.get('auctionId')).toBe('7')
  })
})
