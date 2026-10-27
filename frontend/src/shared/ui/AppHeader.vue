<script setup lang="ts">
// 전역 헤더 (FE1 앱셸). 와이어프레임 <header> 블록 대응 — 로고·nav·지갑 칩·인증 토글.
// 로그인 상태(h-in)/비로그인(h-out)으로 우측 영역이 통째로 교체된다.
import { computed, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useWalletStore } from '@/stores/wallet'
import { useHostedConsoleStore } from '@/stores/hostedConsole'
import { formatPoint } from '@/shared/lib/point'

const auth = useAuthStore()
const wallet = useWalletStore()
const hostedConsole = useHostedConsoleStore()
const route = useRoute()

/**
 * 「진행 콘솔」 바로가기 (S15P11A704-692).
 *
 * 콘솔은 방 번호가 있어야 갈 수 있어 전역 nav에 둘 수 없었고, 그래서 진입 경로가
 * 「마이페이지 → 경매 목록 → 개최한 경매 → 콘솔 열기」 하나뿐이었다. 방송 중에 홈으로 한 번
 * 나가면 3단계를 거쳐야 돌아온다 — 방송하는 동안 계속 오가는 화면치고는 깊다.
 *
 * **언제 다시 조회하느냐**가 이 기능의 전부다. 내비게이션마다 부르면 조회가 화면 수만큼 나가고,
 * 로그인 때 한 번만 부르면 방을 새로 연 판매자에게 링크가 안 뜬다(개설 직후가 정확히 그 경우다).
 * 내 열린 방의 목록이 바뀌는 곳은 **방 경로 안**뿐이라(개설·시작·삭제·종료), 방 경로를
 * **빠져나올 때** 한 번 다시 조회한다. 지갑 칩처럼 매 내비게이션이 아니다.
 */
// 경로 판정은 `fullPath`로 한다 — 지갑 칩 감시와 같은 값을 쓴다(쿼리가 붙어도 접두사는 그대로).
const inRoomScope = (path: string) => path.startsWith('/rooms')
let wasInRoomScope = inRoomScope(route.fullPath)
watch(
  [() => auth.isLoggedIn, () => route.fullPath],
  ([loggedIn, path]) => {
    if (!loggedIn) {
      wasInRoomScope = inRoomScope(path)
      return
    }
    const nowInRoomScope = inRoomScope(path)
    const leftRoomScope = wasInRoomScope && !nowInRoomScope
    wasInRoomScope = nowInRoomScope
    if (!hostedConsole.loaded || leftRoomScope) {
      void hostedConsole.load()
      return
    }
    // 방 밖에서 마감되면 위 두 조건이 한 번도 안 걸린다 — 링크가 떠 있는 동안만 쓰로틀 갱신한다
    // (S15P11A704-698). 열어 둔 방이 없으면 아무것도 안 한다.
    void hostedConsole.refreshIfStale()
  },
  { immediate: true },
)

// `member.nickname`은 nullable이다(온보딩 미완료). 그 상태는 가드가 /onboarding에 붙잡아
// 헤더가 보이지 않지만, 방어적으로 물음표를 둔다.
const avatarInitial = computed(() => auth.user?.nickname?.[0] ?? '?')

/**
 * PNT-01 지갑 칩 (S15P11A704-332).
 *
 * 헤더는 모든 화면에 있는데 지갑을 부르는 화면은 일부뿐이라(마이페이지·지갑·충전·출금·라이브),
 * 홈이나 방 목록에서는 조회가 안 나간다. 그래서 **헤더가 직접 채운다.**
 *
 * `isLoggedIn`이 아니라 **`loaded`를 감시한다**: `resetMemberScope()`가 지갑을 `$reset()`하므로
 * 로그아웃·재로그인 어느 쪽이든 여기서 걸린다. `isLoggedIn`만 보면 **로그아웃을 거치지 않은
 * 재로그인**에서 값이 같은 틱에 false→true로 돌아와 감시가 안 걸린다.
 *
 * 실패했으면 **같은 화면에서는** 다시 시도하지 않는다(재시도 루프 금지). 다만 그대로 두면
 * `loadError`를 지우는 건 `loadWallet` 자신뿐이라 홈·방 목록만 도는 사용자는 칩이 영구히 `—`가
 * 된다 — 헤더가 그 화면들에서 유일한 호출자이기 때문이다. **내비게이션마다 한 번**은 다시 시도해
 * 루프 없이 스스로 회복한다. 중복 호출은 스토어의 single-flight가 막는다(S15P11A704-333).
 */
let lastAttemptedPath: string | null = null
watch(
  [() => auth.isLoggedIn, () => wallet.loaded, () => route.fullPath],
  () => {
    if (!auth.isLoggedIn || wallet.loaded) return
    if (wallet.loadError !== null && lastAttemptedPath === route.fullPath) return
    lastAttemptedPath = route.fullPath
    void wallet.loadWallet()
  },
  { immediate: true },
)

/**
 * **가용 잔액**을 보여준다 (총 보유가 아니다).
 *
 * 입찰·베팅은 돈이 나가는 게 아니라 가용 → 홀드로 **옮기는** 것이라 총 보유는 안 변한다.
 * 총 보유를 띄우면 입찰하고도 칩이 그대로라 배선을 해도 하드코딩이던 때와 화면상 구분이 안 간다.
 * 칩이 충전 진입점이라는 점도 같은 결론이다 — 사용자가 여기서 보려는 건 "지금 쓸 수 있는 돈"이다.
 * 총 보유는 마이페이지·지갑 화면이 "총 보유" 항목으로 따로 보여준다.
 *
 * 조회 전·실패 시에는 **0을 그리지 않는다.** 잔액 0은 그 자체로 말이 되는 값이라
 * "못 받았다"와 구분이 안 되고, 이 칩이 자리표시 숫자로 사람을 속인 게 이 티켓의 발단이다.
 */
const PENDING_LABEL = '—'
const tradeLabel = computed(() =>
  wallet.loaded ? formatPoint(wallet.tradeWallet.availableBalance) : PENDING_LABEL,
)
const bettingLabel = computed(() =>
  wallet.loaded ? formatPoint(wallet.bettingWallet.availableBalance, 'BETTING') : PENDING_LABEL,
)

/**
 * `—`만으로는 스크린리더가 "거래 —"로 읽고 끝난다. 왜 숫자가 없는지 소리로도 알려준다.
 * 값이 있을 때는 null이라 아무것도 덧붙지 않는다.
 */
const chipStatusNote = computed(() => {
  if (wallet.loaded) return null
  return wallet.loadError === null ? '잔액을 불러오는 중이에요' : '잔액을 불러오지 못했어요'
})
</script>

<template>
  <header
    class="border-line bg-cream sticky top-0 z-40 flex h-[var(--header-h)] items-center gap-6 border-b px-6"
  >
    <RouterLink to="/">
      <!-- 정적 src면 plugin-vue가 vitest(SSR 트랜스폼)에서 `file:///logo.png` 임포트로 바꾸다
           스위트 로드째 깨진다. public/ 자산은 변환을 안 타도록 동적 바인딩으로 둔다. -->
      <img :src="'/logo.png'" alt="BidLive" class="h-9 w-auto" />
    </RouterLink>

    <!-- 와이어프레임 nav의 '진행 콘솔'·'낙찰 결과'는 방 단위 경로(/rooms/:id/...)라
         전역 nav에 둘 수 없다. 해당 화면은 방 안에서 진입한다. -->
    <nav class="text-dim flex items-center gap-5 text-sm">
      <RouterLink to="/" class="hover:text-ink">홈</RouterLink>
      <RouterLink to="/rooms" class="hover:text-ink">라이브</RouterLink>
      <!-- F4: 방 개설·마이페이지는 로그인 세션 전용 화면 — 비로그인 nav에서는 숨긴다 (와이어프레임 420-422행) -->
      <RouterLink v-if="auth.isLoggedIn" to="/rooms/new" class="hover:text-ink">방 개설</RouterLink>
      <!-- 끝나지 않은 내 방이 있을 때만 (S15P11A704-692). 목적지가 방 번호에 매여 있어 상시 노출이
           불가능한 항목이라, 갈 곳이 정해졌을 때만 나타난다. 다른 항목과 달리 점을 붙여 구분한다 —
           같은 모양으로 두면 없다가 생기는 항목이 nav가 흔들린 것처럼 보인다.

           **점 색이 방 상태를 따라간다.** 레드는 「지금 벌어지는 일」 전용 기능색이라(규칙 1),
           대기 중인 방을 가리키면서 라이브 점을 달면 화면이 거짓말을 한다 — 실제로 방송을 끝낸 뒤
           대기 방 하나가 남았을 때 「종료했는데 왜 안 없어지냐」로 읽혔다. 예정된 일은 슬레이트다. -->
      <RouterLink
        v-if="auth.isLoggedIn && hostedConsole.roomId !== null"
        :to="{ name: 'console', params: { id: hostedConsole.roomId } }"
        class="hover:text-ink inline-flex items-center gap-1.5 font-semibold"
        :class="hostedConsole.isLive ? 'text-terra-d hover:text-terra' : 'text-slate-tint-text'"
      >
        <span
          class="h-1.5 w-1.5 rounded-full"
          :class="hostedConsole.isLive ? 'bg-terra' : 'bg-slate'"
          aria-hidden="true"
        />
        진행 콘솔
      </RouterLink>
      <RouterLink v-if="auth.isLoggedIn" to="/me" class="hover:text-ink">마이페이지</RouterLink>
    </nav>

    <div class="flex-1"></div>

    <div v-if="auth.isLoggedIn" class="flex items-center gap-3">
      <!--
        와이어프레임대로 칩 전체가 충전이다(`data-go="charge"`). 원장은 마이페이지 지갑 카드의
        "거래 내역 보기 →"로 들어간다 — 그 화면이 stub이던 동안만 칩을 갈라 뒀다가 되돌렸다.
        충전은 전체 화면이라 어디서 눌렀는지를 넘겨 완료 후 그 자리로 돌려보낸다.
      -->
      <RouterLink
        :to="{ name: 'wallet-charge', query: { redirect: route.fullPath } }"
        title="충전하기"
        class="bg-terra-tint text-terra-tint-text flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs tabular-nums"
      >
        <span class="text-faint">거래</span
        ><b
          >{{ tradeLabel
          }}<span v-if="chipStatusNote" class="sr-only"> {{ chipStatusNote }}</span></b
        ><span class="text-terra font-bold">+</span>
      </RouterLink>
      <div
        class="bg-slate-tint text-slate-tint-text flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs tabular-nums"
      >
        <span class="text-faint">베팅</span
        ><b
          >{{ bettingLabel
          }}<span v-if="chipStatusNote" class="sr-only"> {{ chipStatusNote }}</span></b
        >
      </div>
      <RouterLink
        to="/me"
        title="마이페이지"
        aria-label="마이페이지"
        class="bg-terra-tint text-terra-tint-text flex h-9 w-9 items-center justify-center rounded-full text-sm font-semibold"
      >
        {{ avatarInitial }}
      </RouterLink>
    </div>

    <div v-else class="flex items-center gap-2">
      <RouterLink
        to="/login"
        class="border-line text-ink rounded-full border px-4 py-2 text-sm font-medium"
      >
        로그인
      </RouterLink>
      <RouterLink
        :to="{ name: 'signup' }"
        class="bg-terra text-on-dark rounded-full px-4 py-2 text-sm font-semibold"
      >
        회원가입
      </RouterLink>
    </div>
  </header>
</template>
