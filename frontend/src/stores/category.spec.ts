import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useCategoryStore } from './category'
import * as roomsApi from '@/shared/api/rooms'
import { http } from '@/shared/api/http'
import { mockCategories } from '@/mocks/categories'

// 카테고리는 화면 넷이 공유하는 마스터 데이터라, 깨져도 렌더는 멀쩡하고 라벨만 코드로 보인다.
// 셋을 잠근다 — ① 화면마다 부르면 조회가 겹친다 ② 실패를 캐시하면 세션 내내 칩이 안 나온다
// ③ 코드↔ID 매핑이 어긋나면 396의 서버 필터가 엉뚱한 카테고리를 조회한다.

describe('useCategoryStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
    // API 함수가 아니라 **http를 스텁한다** — `getAuctionCategories`의 정렬까지 실제로 돌려야
    // "칩 순서가 흔들리지 않는다"를 검사할 수 있다. 그래서 일부러 **역순**으로 응답한다.
    vi.spyOn(http, 'get').mockResolvedValue({ data: { items: [...mockCategories].reverse() } })
  })

  it('겹쳐 불러도 조회는 한 번만 나간다 — 화면 넷이 각자 onMounted에서 부른다', async () => {
    const spy = vi.spyOn(roomsApi, 'getAuctionCategories')
    const store = useCategoryStore()

    await Promise.all([store.loadCategories(), store.loadCategories(), store.loadCategories()])

    expect(spy).toHaveBeenCalledTimes(1)
    expect(store.loaded).toBe(true)
  })

  it('한 번 받으면 다시 받지 않는다 — 마스터 데이터라 세션 중 안 바뀐다', async () => {
    const spy = vi.spyOn(roomsApi, 'getAuctionCategories')
    const store = useCategoryStore()

    await store.loadCategories()
    await store.loadCategories()

    expect(spy).toHaveBeenCalledTimes(1)
  })

  it('실패는 캐시하지 않는다 — 다음 진입에서 다시 시도한다', async () => {
    // 실패까지 캐시하면 첫 조회가 한 번 어긋난 것만으로 세션 내내 칩이 안 나온다
    const spy = vi.spyOn(roomsApi, 'getAuctionCategories').mockRejectedValueOnce(new Error('boom'))
    const store = useCategoryStore()

    await store.loadCategories()
    expect(store.loaded).toBe(false)
    expect(store.loadError).not.toBeNull()

    await store.loadCategories()

    expect(spy).toHaveBeenCalledTimes(2)
    expect(store.loaded).toBe(true)
  })

  it('displayOrder 오름차순으로 들고 있다 — 칩 순서가 매 조회마다 흔들리면 안 된다', async () => {
    const store = useCategoryStore()

    await store.loadCategories()

    const orders = store.categories.map((c) => c.displayOrder)
    expect(orders).toEqual([...orders].sort((a, b) => a - b))
  })

  describe('코드 매핑', () => {
    it('코드를 표시명으로 옮긴다', async () => {
      const store = useCategoryStore()
      await store.loadCategories()

      expect(store.nameByCode('ELECTRONICS')).toBe('전자기기')
    })

    it('모르는 코드는 코드를 그대로 돌려준다 — 빈칸이면 무엇이 빠졌는지 안 보인다', async () => {
      const store = useCategoryStore()
      await store.loadCategories()

      expect(store.nameByCode('DRONES')).toBe('DRONES')
    })

    it('코드로 categoryId를 찾는다 — 목록 필터 쿼리에 실을 값이다 (396)', async () => {
      const store = useCategoryStore()
      await store.loadCategories()

      const electronics = store.categories.find((c) => c.code === 'ELECTRONICS')
      expect(store.idByCode('ELECTRONICS')).toBe(electronics?.categoryId)
      // 못 찾으면 undefined = 필터 없음. 0이나 -1로 떨어지면 서버가 404를 준다
      expect(store.idByCode('DRONES')).toBeUndefined()
    })
  })
})
