import { defineStore } from 'pinia'
import { getAuctionCategories, type AuctionCategory } from '@/shared/api/rooms'
import { toApiError } from '@/shared/api/errors'

export type { AuctionCategory }

// 오너: FE1 (ROOM-02) — 상품 카테고리 마스터.
//
// 화면 넷이 같은 목록을 쓴다: 방 개설·방 수정(선택), 방 목록(칩), 방 카드(코드 → 표시명).
// 명세가 **"프론트의 하드코딩된 카테고리 목록 대신 이 API 결과를 사용한다"**고 못 박았고,
// "목록 변경이 잦지 않으므로 프론트에서 캐시할 수 있다"고 캐시까지 허용했다.
//
// ⚠️ 식별자가 두 종류다. 목록 필터 쿼리는 `categoryId`(Long)인데 목록 응답·생성·수정 요청은
//    `code` 문자열이다. **둘을 잇는 유일한 곳이 이 응답**이라, 여기가 없으면 카테고리 칩을
//    서버 필터로 넘길 수 없다 (S15P11A704-396의 전제조건).

/**
 * 진행 중인 카테고리 조회. 네 화면이 각자 `onMounted`에서 부르므로 겹치면 같은 조회가 여러 번 나간다.
 * `stores/auth.ts`의 `inFlightTerms`와 같은 방식이다 — 조기 return이 아니라 **같은 프라미스**를
 * 돌려줘서 나중에 온 호출도 완료를 기다릴 수 있게 한다.
 */
let inFlight: Promise<void> | null = null

export const useCategoryStore = defineStore('category', {
  state: () => ({
    categories: [] as AuctionCategory[],
    loaded: false,
    loadError: null as string | null,
  }),
  getters: {
    /**
     * 코드 → 표시명. **못 찾으면 코드를 그대로 돌려준다** —
     * 서버가 카테고리를 추가하면 배지가 `ELECTRONICS`처럼 보이겠지만, 빈칸보다는 낫고
     * 무엇이 빠졌는지 화면에서 바로 드러난다.
     */
    nameByCode(state) {
      return (code: string): string => state.categories.find((c) => c.code === code)?.name ?? code
    },
    /** 코드 → `categoryId`. 목록 필터 쿼리에 실을 값이다. 못 찾으면 undefined = 필터 없음 */
    idByCode(state) {
      return (code: string): number | undefined =>
        state.categories.find((c) => c.code === code)?.categoryId
    },
  },
  actions: {
    /**
     * ROOM-02 카테고리 목록 (`GET /auctions/categories`).
     *
     * 한 번 받으면 다시 받지 않는다. 마스터 데이터라 세션 중 바뀌지 않고,
     * 화면을 옮길 때마다 다시 받으면 칩이 매번 깜빡인다.
     * (지갑·방 목록의 single-flight는 재조회를 **열어 두는** 쪽인데, 그건 값이 바뀌는 데이터라서다.)
     *
     * 단 **실패는 캐시하지 않는다** — `loaded`를 세우지 않으므로 다음 진입에서 다시 시도한다.
     */
    loadCategories(): Promise<void> {
      if (this.loaded) return Promise.resolve()
      if (inFlight) return inFlight
      this.loadError = null
      inFlight = (async () => {
        try {
          this.categories = await getAuctionCategories()
          this.loaded = true
        } catch (error) {
          this.loadError = toApiError(error).message
        } finally {
          inFlight = null
        }
      })()
      return inFlight
    },
  },
})
