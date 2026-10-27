import { beforeEach, describe, expect, it, vi } from 'vitest'
import { findProductRejection } from './ai'
import { http } from './http'

// AI-04 차단 판정을 잠근다 (S15P11A704-560).
//
// 「REJECTED만 차단」은 눈에 안 보이는 정책이다 — 보류(NEEDS_REVIEW)까지 막도록 잘못 고치면
// 화면은 멀쩡해 보이는데 GMS가 흔들리는 날 방 개설이 통째로 멈춘다. 세 분기를 여기서 고정한다.

function respond(data: unknown) {
  return { data: { type: 'AI_INSPECTION_RESULT', data } }
}

function unit(verdict: string, reason: string | null = null, imageUrl: string | null = null) {
  return {
    verdict,
    imageUrl,
    flaggedCategories: [],
    maxScore: 0.9,
    reason,
    failureReason: null,
  }
}

describe('findProductRejection', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(console, 'warn').mockImplementation(() => {})
  })

  it('baseURL 기준 상대경로로 입력을 그대로 보낸다', async () => {
    const post = vi
      .spyOn(http, 'post')
      .mockResolvedValue(
        respond({ verdict: 'PASSED', textResult: null, imageResults: [], inspectedAt: '' }),
      )

    await findProductRejection({
      title: '가죽 자켓',
      description: '정품',
      imageUrls: ['https://x/1.jpg'],
    })

    expect(post.mock.calls[0][0]).toBe('/ai/products/inspect')
    expect(post.mock.calls[0][1]).toEqual({
      title: '가죽 자켓',
      description: '정품',
      imageUrls: ['https://x/1.jpg'],
    })
  })

  it('PASSED면 차단하지 않는다', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(
      respond({
        verdict: 'PASSED',
        textResult: unit('PASSED'),
        imageResults: [unit('PASSED')],
        inspectedAt: '',
      }),
    )

    expect(await findProductRejection({ title: '자켓' })).toBeNull()
  })

  // 순번은 응답 순서가 아니라 **보낸 배열** 기준이다 — BE `normalizeUrls()`가 `distinct().sorted()`로
  // 갈아엎어 응답은 URL 사전순으로 온다. 응답 인덱스로 세면 사용자에게 엉뚱한 장을 가리킨다.
  it('REJECTED면 대표 사유 한 줄로 차단하고, 사진이면 보낸 순서 기준 순번을 붙인다', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(
      respond({
        verdict: 'REJECTED',
        textResult: unit('PASSED'),
        // 사전순이라 보낸 순서(b, a)와 어긋난다 — 걸린 b는 응답에선 2번째, 보낸 목록에선 1번째다
        imageResults: [
          unit('PASSED', null, 'https://x/a.jpg'),
          unit('REJECTED', '정품을 사칭한 로고가 확인됩니다.', 'https://x/b.jpg'),
        ],
        inspectedAt: '',
      }),
    )

    const message = await findProductRejection({
      title: '짝퉁',
      imageUrls: ['https://x/b.jpg', 'https://x/a.jpg'],
    })

    expect(message).toBe(
      '1번째 사진: 정품을 사칭한 로고가 확인됩니다. 다른 사진·문구로 수정해 주세요.',
    )
  })

  it('걸린 사진이 보낸 목록에 없으면(정규화로 걸러짐) 순번을 붙이지 않는다', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(
      respond({
        verdict: 'REJECTED',
        textResult: unit('PASSED'),
        // BE가 strip한 URL — 보낸 문자열과 일치하지 않는다
        imageResults: [unit('REJECTED', '위조품이 확인됩니다.', 'https://x/a.jpg')],
        inspectedAt: '',
      }),
    )

    const message = await findProductRejection({
      title: '짝퉁',
      imageUrls: [' https://x/a.jpg '],
    })

    expect(message).toBe('위조품이 확인됩니다. 다른 사진·문구로 수정해 주세요.')
  })

  it('텍스트·사진이 함께 걸리면 텍스트 사유를 먼저 보여준다', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(
      respond({
        verdict: 'REJECTED',
        textResult: unit('REJECTED', '금지된 품목명이 포함돼 있습니다.'),
        imageResults: [unit('REJECTED', '이미지에서 위조품이 확인됩니다.')],
        inspectedAt: '',
      }),
    )

    // 사진 순번을 붙이지 않는다 — 텍스트 판정에는 「N번째 사진」이 없다
    expect(await findProductRejection({ title: '짝퉁' })).toBe(
      '금지된 품목명이 포함돼 있습니다. 다른 사진·문구로 수정해 주세요.',
    )
  })

  it('REJECTED인데 사유가 비어 있어도 문구가 비지 않는다', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(
      respond({ verdict: 'REJECTED', textResult: null, imageResults: [], inspectedAt: '' }),
    )

    expect(await findProductRejection({ title: '짝퉁' })).toBe(
      '등록할 수 없는 상품으로 판정됐어요. 다른 사진·문구로 수정해 주세요.',
    )
  })

  it('NEEDS_REVIEW는 통과시킨다 — GMS 장애가 200 + 보류로 오기 때문', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(
      respond({
        verdict: 'NEEDS_REVIEW',
        textResult: {
          verdict: 'NEEDS_REVIEW',
          imageUrl: null,
          flaggedCategories: [],
          maxScore: 0,
          reason: null,
          failureReason: 'TIMEOUT',
        },
        imageResults: [],
        inspectedAt: '',
      }),
    )

    expect(await findProductRejection({ title: '자켓' })).toBeNull()
  })

  it('호출이 실패해도 통과시킨다 — fail-open (502 크레딧 소진·네트워크)', async () => {
    vi.spyOn(http, 'post').mockRejectedValue(new Error('boom'))

    expect(await findProductRejection({ title: '자켓' })).toBeNull()
    expect(console.warn).toHaveBeenCalled()
  })

  it('봉투가 무너져도 터지지 않고 통과시킨다 — 계약이 바뀌어도 방 개설은 계속돼야 한다', async () => {
    vi.spyOn(http, 'post').mockResolvedValue({ data: {} })

    expect(await findProductRejection({ title: '자켓' })).toBeNull()
    expect(console.warn).toHaveBeenCalled()
  })
})
