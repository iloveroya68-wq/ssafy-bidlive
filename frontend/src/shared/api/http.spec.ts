import { afterEach, describe, expect, it, vi } from 'vitest'
import { AxiosHeaders, type AxiosAdapter } from 'axios'
import { http } from './http'
import { API_ERROR_CODE, type ApiError, toApiError } from './errors'

// 배포 사고 재발 방지 (S15P11A704-671).
//
// 빈 `VITE_API_BASE_URL`이 `??` 폴백을 그대로 통과해(`'' ?? '/api/v1'` === `''`) 접두사 없는
// 요청이 나갔고, nginx SPA 폴백이 index.html을 **200 text/html**로 돌려줘 스토어가 HTML을
// 「성공」으로 담았다 — `loadError`도 없이 방 목록만 백지가 됐다.

/** 지정한 헤더·본문으로 한 번 응답하는 어댑터. 인터셉터는 그대로 탄다. */
function respondWith(contentType: string, data: unknown): AxiosAdapter {
  return async (config) => ({
    data,
    status: 200,
    statusText: 'OK',
    headers: new AxiosHeaders({ 'content-type': contentType }),
    config,
  })
}

/** 실패를 기대하는 호출. 통과해 버리면 그 자체가 실패다. */
async function failureOf(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise
  } catch (error) {
    return toApiError(error)
  }
  throw new Error('rejected 되어야 하는데 통과했다')
}

describe('API_BASE_URL', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
    vi.resetModules()
  })

  it('env가 비어 있으면 /api/v1로 떨어진다 — `??`면 빈 문자열이 그대로 통과한다', async () => {
    vi.stubEnv('VITE_API_BASE_URL', '')
    vi.resetModules()

    const reloaded = await import('./http')

    expect(reloaded.API_BASE_URL).toBe('/api/v1')
  })

  it('env가 있으면 그 값을 쓴다', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://example.test/api/v9')
    vi.resetModules()

    const reloaded = await import('./http')

    expect(reloaded.API_BASE_URL).toBe('https://example.test/api/v9')
  })
})

describe('text/html 응답 가드', () => {
  it('200 text/html은 실패로 바꾼다 — SPA 폴백이 API 자리에 온 것이다', async () => {
    const error = await failureOf(
      http.get('/auction-rooms', {
        adapter: respondWith('text/html; charset=utf-8', '<!doctype html><div id="app"></div>'),
      }),
    )

    expect(error.code).toBe(API_ERROR_CODE.SPA_FALLBACK_HTML)
    // 화면들은 `toApiError(error).message`를 loadError로 그대로 띄운다 — 사람이 읽을 문구여야 한다
    expect(error.message).toBe('서버 응답이 올바르지 않아요. 잠시 후 다시 시도해 주세요')
  })

  it('정상 JSON 200은 그대로 통과한다', async () => {
    const response = await http.get('/auction-rooms', {
      adapter: respondWith('application/json', { items: [] }),
    })

    expect(response.data).toEqual({ items: [] })
  })

  it('JSON이 아닌 다른 타입은 막지 않는다 — 이미지·파일 응답이 걸리면 안 된다', async () => {
    const response = await http.get('/product-images/1', {
      adapter: respondWith('image/png', 'binary'),
    })

    expect(response.status).toBe(200)
  })
})
