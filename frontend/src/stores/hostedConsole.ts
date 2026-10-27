import { defineStore } from 'pinia'
import { getHostedAuctions } from '@/shared/api/myAuctions'

// 오너: FE1 (S15P11A704-692) — 헤더의 「진행 콘솔」 바로가기가 가리킬 방.
//
// 콘솔은 `/rooms/:id/console`이라 방 번호가 있어야 갈 수 있고, 그래서 전역 nav에 둘 수 없었다.
// 진입 경로가 「마이페이지 → 경매 목록 → 개최한 경매 → 콘솔 열기」 하나뿐이라, 방송 중에 홈으로
// 한 번 나가면 3단계를 거쳐야 돌아왔다. **끝나지 않은 내 방이 있을 때만** 그 방을 가리키는
// 링크를 헤더에 띄워서 그 동선을 없앤다.
//
// 목록 응답(`AuctionRoomSummary`)에는 `isSeller`가 없어서 방 목록만으로는 내 방을 못 가른다 —
// 그래서 「개최한 경매」(MEM-02)를 소스로 쓴다. 이미 마이페이지가 쓰는 엔드포인트다.

/** 진행 중이 예정보다 먼저다 — 지금 방송 중인 방이 있으면 그쪽이 판매자가 찾는 화면이다. */
const LIVE = 'LIVE'
const ENDED = 'ENDED'

/** 겹쳐 부를 때 같은 프라미스를 돌려준다 (`stores/category.ts`와 같은 방식) */
let inFlight: Promise<void> | null = null

/**
 * `refreshIfStale()` 전용 재조회 간격 (S15P11A704-698).
 *
 * 규칙 5(타이머 자체 계산 금지)와 무관하다 — 화면에 그리는 시각이 아니라 조회 빈도를 누르는
 * 값이라 서버 시각 기준일 이유가 없다. `load()`는 이 값을 보지 않는다.
 */
const RESTALE_MS = 30_000
let lastLoadedAt = 0

export const useHostedConsoleStore = defineStore('hostedConsole', {
  state: () => ({
    /** 헤더 링크의 목적지. 끝나지 않은 내 방이 없으면 null이라 링크 자체가 안 뜬다 */
    roomId: null as number | null,
    /**
     * 그 방이 방송 중인가. **헤더의 강조를 가르는 값이다** — 레드는 이 앱에서 「지금 벌어지는 일」
     * 전용 기능색이라(AGENTS 규칙 1), 대기 중인 방을 가리키면서 라이브 점을 달면 화면이 거짓말을 한다.
     * 실제로 그렇게 보여서 「종료했는데 왜 안 없어지냐」로 읽혔다.
     */
    isLive: false,
    /**
     * 한 번이라도 조회했는지. **`roomId === null`로 대신할 수 없다** — 열어 둔 방이 없는
     * 사용자(대부분)는 그 값이 영영 null이라, 그것을 「아직 안 불렀다」로 읽으면 내비게이션마다
     * 조회가 나간다.
     */
    loaded: false,
  }),
  actions: {
    /**
     * 끝나지 않은 내 방 하나를 고른다. 실패는 삼킨다 — 이 값이 없으면 링크가 안 뜰 뿐이고,
     * 그건 이 기능이 생기기 전과 같은 상태다. 헤더에서 에러를 띄울 일이 아니다.
     *
     * ponytail: 첫 페이지만 본다. 열어 둔 방이 한 페이지를 넘는 판매자는 오래된 방이 안 잡히는데,
     * 그때는 마이페이지 경로가 그대로 있다. 커서를 끝까지 도는 건 헤더가 할 일이 아니다.
     */
    async load(): Promise<void> {
      if (inFlight !== null) return inFlight
      inFlight = (async () => {
        try {
          const { items } = await getHostedAuctions()
          const open = items.filter((item) => item.roomStatus !== ENDED)
          const live = open.find((item) => item.roomStatus === LIVE)
          const target = live ?? open[0]
          this.roomId = target?.auctionId ?? null
          this.isLive = target?.roomStatus === LIVE
        } catch {
          this.roomId = null
          this.isLive = false
        } finally {
          this.loaded = true
          lastLoadedAt = Date.now()
          inFlight = null
        }
      })()
      return inFlight
    },

    /**
     * 링크가 떠 있는 동안의 갱신 (S15P11A704-698).
     *
     * 헤더는 「방 경로를 빠져나올 때」 재조회하는데, **마감은 서버가 하는 일이라 그 내비게이션과
     * 아무 상관이 없다.** 판매자가 방 밖에 있는 동안 타이머로 마감되면 나갈 방 경로가 없어
     * 재조회가 영영 안 돌고, 링크가 빨간 점을 단 채 남는다.
     *
     * 내비게이션마다 부르는 건 이 스토어가 원래 거부한 방향이라(조회가 화면 수만큼 나간다)
     * **링크가 실제로 떠 있을 때만** 대상으로 삼고 쓰로틀을 건다. 비용이 열어 둔 방이 있는
     * 판매자에게만 붙고, 그게 정확히 이 링크가 필요한 사람이다. 열어 둔 방이 없는 대부분의
     * 사용자는 `roomId`가 null이라 지금까지와 똑같이 한 번만 조회한다.
     *
     * `load()`는 이 가드를 타지 않는다 — 방을 새로 연 판매자에게 링크가 곧바로 떠야 하고,
     * 그 경로(방 경로 이탈)를 쓰로틀이 삼키면 692가 그대로 되돌아간다.
     */
    async refreshIfStale(): Promise<void> {
      if (this.roomId === null) return
      if (Date.now() - lastLoadedAt < RESTALE_MS) return
      return this.load()
    },

    /** 로그아웃·회원 전환 — 다음 회원 기준으로 다시 조회하도록 비운다 */
    clear(): void {
      this.roomId = null
      this.isLive = false
      this.loaded = false
      // 남겨 두면 재로그인 직후 30초 동안 `refreshIfStale()`이 침묵한다
      lastLoadedAt = 0
    },
  },
})
