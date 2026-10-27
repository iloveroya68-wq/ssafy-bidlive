<script setup lang="ts">
// ROOM-02 홈 · 방 목록 (FE1) — 상태 탭·카테고리 필터·카드 그리드
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { AUCTION_STATUS_BY_ROOM_STATE } from '@/shared/api/rooms'
import { useAuthStore } from '@/stores/auth'
import { endAtMs, roomStateOf, useRoomStore, type AuctionRoomSummary } from '@/stores/room'
import type { RoomState } from '@/mocks/rooms'
import { useCategoryStore } from '@/stores/category'
import { cssUrl } from '@/shared/lib/cssUrl'
import { formatUpcomingAt } from '@/shared/lib/relativeTime'
import RoomCard from './RoomCard.vue'
import { isDemoRoom, withDemoRooms } from '@/features/demo/demoRooms'
import DemoRoomNotice from '@/features/demo/DemoRoomNotice.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const roomStore = useRoomStore()

const categoryStore = useCategoryStore()

onMounted(() => {
  // 칩과 카드 배지가 같은 목록을 쓴다. 겹쳐 불러도 스토어가 한 번만 조회한다.
  // 목록 조회는 아래 watch가 조건과 함께 건다 — 여기서 또 부르면 같은 조회가 두 번 나간다.
  void categoryStore.loadCategories()
})

// 상태 탭: 진행중 / 대기 / 종료 (디자인 livelist 화면 기준)
const STATE_TABS: { state: RoomState; label: string }[] = [
  { state: 'live', label: '진행중' },
  { state: 'waiting', label: '대기' },
  { state: 'ended', label: '종료' },
]
// 카테고리 칩은 서버 목록이다 ('' = 전체). 하드코딩 배열은 S15P11A704-397에서 걷어냈다

// ── `?state=`·`?cat=` 딥링크 (S15P11A704-361) ──
// 필터를 걸어둔 목록을 링크로 공유할 수 있어야 한다.
// (docs/02 「화면 내 상태」 표가 이 화면의 분기 소스를 `?state=`·`?cat=`으로 못 박고 있다 —
//  표에 없는 요구사항 문장을 인용하고 있어 출처를 바로잡았다.)
// **쿼리가 정본이고 화면 상태는 그 사본이다** — MyPageView의 `?tab=`과 같은 규약.
// 필터 조작은 `replace`다. 칩을 몇 번 누를 때마다 뒤로가기 이력이 쌓이면 화면을 벗어나기 어려워진다.
// (개설 화면의 `?step=`만 `push`인데, 거기선 이력 자체가 "← 이전" UI이기 때문이다.)
const STATE_KEYS = STATE_TABS.map((tab) => tab.state)

function isRoomState(value: unknown): value is RoomState {
  return typeof value === 'string' && (STATE_KEYS as string[]).includes(value)
}

function readStateFromQuery(): RoomState {
  return isRoomState(route.query.state) ? route.query.state : 'live'
}

/**
 * `?cat=`은 **카테고리 코드**를 담는다(`?cat=ELECTRONICS`).
 * 표시명을 담던 것을 397에서 코드로 바꿨다 — URL-safe하고, 표시명이 바뀌어도 링크가 산다.
 *
 * ⚠️ 카테고리 목록이 오기 전에는 **유효성을 판정하지 않는다.** 아직 못 받은 상태에서
 * "모르는 값"으로 처리하면 정상 링크(`?cat=ELECTRONICS`)를 열자마자 쿼리를 지워 버린다.
 *
 * 옛 표시명 링크(`?cat=취미·수집`)는 코드로 옮겨 준다. 안 그러면 **오류 없이 필터만 풀려서**
 * 사용자는 링크가 깨진 걸 모르고 전체 목록을 본다. `syncQuery`가 주소도 새 형식으로 고쳐 쓴다.
 */
function readCategoryFromQuery(): string {
  const raw = route.query.cat
  if (typeof raw !== 'string') return ''
  if (!categoryStore.loaded) return raw
  const matched = categoryStore.categories.find((c) => c.code === raw || c.name === raw)
  return matched?.code ?? ''
}

/** `?q=` — 앞뒤 공백은 의미가 없다. 공백만 친 검색어는 "검색 안 함"과 같다 */
function readKeywordFromQuery(): string {
  const raw = route.query.q
  return typeof raw === 'string' ? raw.trim() : ''
}

const activeState = ref<RoomState>(readStateFromQuery())
const activeCategory = ref(readCategoryFromQuery())

/**
 * 검색어는 **두 개로 나눠 든다.**
 *
 * - `searchInput` — 입력창이 그리는 값. 타이핑 즉시 바뀐다
 * - `activeKeyword` — 확정된 검색어. 쿼리와 서버 조회의 근거다
 *
 * 한 글자마다 `router.replace` + 목록 재조회를 하면 이력·요청이 과해진다.
 * 그래서 **보이는 것은 즉시, 나가는 것은 늦게** 한다.
 */
const SEARCH_DEBOUNCE_MS = 300
const searchInput = ref(readKeywordFromQuery())
const activeKeyword = ref(readKeywordFromQuery())
let searchTimer: ReturnType<typeof setTimeout> | null = null

function cancelPendingSearch() {
  if (searchTimer !== null) clearTimeout(searchTimer)
  searchTimer = null
}

function onSearchInput() {
  cancelPendingSearch()
  searchTimer = setTimeout(() => {
    activeKeyword.value = searchInput.value.trim()
    syncQuery()
  }, SEARCH_DEBOUNCE_MS)
}

/**
 * 알아볼 수 없는 값은 URL에서도 정리한다. 화면만 기본값으로 떨어뜨리고 주소를 두면
 * 공유·새로고침 때마다 같은 어긋남이 반복된다.
 * 기본값(`state=live`·카테고리 전체)은 쿼리에서 아예 뺀다 — 주소가 짧을수록 공유하기 좋다.
 */
function syncQuery() {
  const next: Record<string, string> = { ...(route.query as Record<string, string>) }
  if (activeState.value === 'live') delete next.state
  else next.state = activeState.value
  if (activeCategory.value === '') delete next.cat
  else next.cat = activeCategory.value
  if (activeKeyword.value === '') delete next.q
  else next.q = activeKeyword.value

  const same =
    next.state === (route.query.state as string | undefined) &&
    next.cat === (route.query.cat as string | undefined) &&
    next.q === (route.query.q as string | undefined)
  if (same) return
  void router.replace({ query: next })
}

function selectState(state: RoomState) {
  activeState.value = state
  syncQuery()
}

function selectCategory(code: string) {
  activeCategory.value = code
  syncQuery()
}

// 쿼리 → 화면. 뒤로가기·직접 진입이 여기로 들어온다. 진입 시점의 정규화도 겸한다.
watch(
  () => [route.query.state, route.query.cat, route.query.q],
  () => {
    activeState.value = readStateFromQuery()
    activeCategory.value = readCategoryFromQuery()
    // 뒤로가기로 검색어가 바뀌는 중이라면 **입력 중이던 디바운스는 무효다.**
    // 안 끊으면 방금 되돌린 주소를 옛 입력값이 다시 덮어쓴다.
    cancelPendingSearch()
    activeKeyword.value = readKeywordFromQuery()
    // 입력창은 **다른 검색어로 바뀔 때만** 갈아 끼운다. 여기로는 우리 자신의 `syncQuery()`가
    // 만든 주소 변경도 되돌아오는데, 그때 무조건 쓰면 사용자가 방금 친 **앞뒤 공백이 지워진다** —
    // `카메라 렌즈 `까지 치고 잠깐 쉬면 `카메라 렌즈`가 되고 다음 글자가 `카메라 렌즈2`로 붙는다.
    if (searchInput.value.trim() !== activeKeyword.value) searchInput.value = activeKeyword.value
    syncQuery()
  },
  { immediate: true },
)

onBeforeUnmount(cancelPendingSearch)

// 목록이 늦게 도착하면 그때 `?cat=`을 다시 판정한다 (진입 시점에는 판정을 미뤄 뒀다)
watch(
  () => categoryStore.loaded,
  (loaded) => {
    if (!loaded) return
    activeCategory.value = readCategoryFromQuery()
    syncQuery()
  },
)

/**
 * 조건이 바뀌면 서버에 다시 묻는다. **클라이언트에서 거르지도, 정렬하지도 않는다** —
 * 명세가 상태별 정렬 정책까지 서버 몫으로 못 박았고(`LIVE`는 `endAt` 오름차순 등),
 * 한 페이지만 들고 있는 상태에서 FE가 다시 세우면 페이지 경계에서 순서가 어긋난다.
 *
 * 카테고리는 코드 → `categoryId`로 옮겨 보낸다 (필터 쿼리가 Long이다, S15P11A704-397).
 */
function reload() {
  const categoryId =
    activeCategory.value === '' ? undefined : categoryStore.idByCode(activeCategory.value)
  void roomStore.loadRooms({
    status: AUCTION_STATUS_BY_ROOM_STATE[activeState.value],
    ...(categoryId === undefined ? {} : { categoryId }),
    ...(activeKeyword.value === '' ? {} : { keyword: activeKeyword.value }),
  })
}

// 탭·칩·검색어가 바뀌면 곧 다시 조회한다. 진입 시점(`immediate`)의 첫 조회도 여기서 걸린다.
watch([activeState, activeCategory, activeKeyword], reload, { immediate: true })

// 카테고리 목록이 늦게 오면 `?cat=`으로 들어온 칩의 `categoryId`를 그때서야 알 수 있다.
// 위 watch는 코드 문자열이 그대로라 안 돈다 — 여기서 한 번 더 건다.
watch(
  () => categoryStore.loaded,
  (loaded) => {
    if (loaded && activeCategory.value !== '') reload()
  },
)

/**
 * 시연 더미는 **실 목록 뒤에만** 붙는다 (`features/demo/demoRooms.ts`).
 *
 * 검색·카테고리 필터가 걸려 있으면 붙이지 않는다 — 「asdf」로 검색해도 더미가 나오면 필터가
 * 고장 난 것처럼 보이고, 그때는 「결과가 없어요」가 정확한 화면이다.
 */
/**
 * ⚠️ 시연용 임시 필터 — **시연이 끝나면 이 상수와 함수, 아래 분기를 통째로 지운다.**
 * 종료 탭에 그 전 며칠간의 테스트 방이 쌓여 있어 이 시각 이후에 끝난 것만 남긴다.
 * 굴러가는 "오늘"이 아니라 **고정 날짜**다 — 시연 다음 날에도 같은 목록이 나와야 한다.
 * `endAt`이 없으면 남긴다 — 판정할 수 없는 방을 감추면 시연할 방이 사라질 수 있다.
 */
const DEMO_ENDED_AFTER = new Date('2026-08-10T00:00:00+09:00').getTime()

function isEndedAfterCutoff(room: AuctionRoomSummary): boolean {
  const ms = endAtMs(room)
  return ms === undefined || ms >= DEMO_ENDED_AFTER
}

const visibleRooms = computed(() => {
  const rooms =
    activeState.value === 'ended' ? roomStore.rooms.filter(isEndedAfterCutoff) : roomStore.rooms
  if (activeKeyword.value !== '' || activeCategory.value !== '') return rooms
  return withDemoRooms(rooms, AUCTION_STATUS_BY_ROOM_STATE[activeState.value])
})

// ── ROOM-08 「곧 시작해요」 스트립 ──
// 1시간 이내 시작 예정인 대기방을 예정 시각순 최대 5개로 **서버가 잘라서** 준다
// (명세 「곧 시작 경매 조회」 §규칙) — 여기서 다시 자르거나 정렬하지 않는다.

// 조회는 **스트립을 그리는 이 화면이 건다.** 진행중 탭이 켜지는 순간 1회 —
// 칩·검색어로는 다시 묻지 않는다(ROOM-08은 조건을 받지 않아 같은 응답이 온다).
// 스토어의 목록 조회에 얹어 두면 스트립이 없는 홈에서도 요청이 나간다.
// 타이머·폴링은 없다 — 분 단위 표기라 로드 시점 1회 계산이면 충분하다.
watch(
  () => activeState.value === 'live',
  (isLive) => {
    if (isLive) void roomStore.loadUpcoming()
  },
  { immediate: true },
)

/**
 * 스트립에 실제로 그릴 항목. 비어 있으면 스트립을 통째로 숨긴다.
 *
 * - **진행중 탭에서만** 보인다. 대기 탭에는 같은 방이 그리드에 그대로 있고, 종료 탭에는 무관하다.
 * - 검색어는 화면에서 거른다. 목록과 달리 ROOM-08에는 `keyword` 파라미터가 없다.
 * - ⚠️ **카테고리 칩이 켜져 있으면 스트립을 통째로 숨긴다.** ROOM-08 응답에 `categoryCode`가 없어
 *   항목별로 맞는지 판정할 방법이 없다 — 거르지 않고 두면 「전자기기」를 고른 사람에게
 *   패션 방이 섞여 보인다. 서버가 `categoryId` 필터를 받게 되면 그때 거르고 보여주면 된다.
 */
const upcomingRooms = computed(() => {
  if (activeState.value !== 'live' || activeCategory.value !== '') return []
  const keyword = activeKeyword.value.toLowerCase()
  if (keyword === '') return roomStore.upcoming
  return roomStore.upcoming.filter((room) => room.productTitle.toLowerCase().includes(keyword))
})

/** "21:30 · 12분 후" — 상대 표기는 서버 시각 기준이다 (규칙 5) */
const upcomingLabel = (iso: string) => formatUpcomingAt(iso, roomStore.serverOffset)

/**
 * 탭 옆 숫자 (S15P11A704-417). **없으면 아무것도 그리지 않는다** —
 * 0을 보여주면 "방이 없다"로 읽히는데, 조회 중이거나 서버가 안 준 것뿐일 수 있다.
 *
 * 종료 탭은 항상 undefined다. 와이어프레임 원본에 숫자가 없고, 계속 쌓이는 쪽이라
 * 집계 비용도 커서 응답에서 아예 뺐다(명세 "`ENDED` 개수는 `counts`에 포함하지 않는다").
 *
 * 이 값은 **전체 개수**라 스크롤로 다음 장을 받아도 변하지 않는다 —
 * 화면에 보이는 카드 수와 다를 수 있고, 그게 맞다.
 */
function tabCount(state: RoomState): number | undefined {
  if (state === 'ended') return undefined
  return roomStore.counts?.[AUCTION_STATUS_BY_ROOM_STATE[state]]
}

// ── 무한 스크롤 ──
// 센티널이 화면에 들어오면 다음 페이지를 받는다. 스토어가 `loading`·`hasNext`로
// 중복 요청을 막으므로 여기서는 관찰만 한다.
const sentinel = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null

function loadMore() {
  void roomStore.loadMoreRooms()
}

onMounted(() => {
  // 테스트 환경(jsdom)·구형 브라우저에는 없을 수 있다 — 그때는 "더 보기" 버튼만 남는다
  if (typeof IntersectionObserver === 'undefined') return
  observer = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting)) loadMore()
    },
    // 화면 끝에 닿기 전에 미리 받아 스크롤이 끊기지 않게 한다
    { rootMargin: '300px' },
  )
  // 센티널은 `hasNext`에 따라 붙었다 떨어졌다 하므로 ref를 지켜본다
  watch(
    sentinel,
    (el, _prev, onCleanup) => {
      if (el === null || observer === null) return
      observer.observe(el)
      onCleanup(() => observer?.unobserve(el))
    },
    { immediate: true },
  )
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
})

// 카드 클릭 → 상태별 라우팅: live→라이브 뷰, waiting→대기방, ended→결과(관전자)
const ROUTE_SUFFIX_BY_STATE: Record<RoomState, string> = {
  live: 'live',
  waiting: 'waiting',
  ended: 'result',
}

function openRoomState(auctionId: number, state: RoomState) {
  router.push(`/rooms/${auctionId}/${ROUTE_SUFFIX_BY_STATE[state]}`)
}

/** 시연 더미 카드를 눌렀을 때 뜨는 안내 */
const demoNoticeOpen = ref(false)

function openRoom(room: AuctionRoomSummary) {
  // 더미는 실제 방이 아니라 입장시키지 않는다 — 음수 id로 라우팅하면 그 화면이 404를 받는다
  if (isDemoRoom(room)) {
    demoNoticeOpen.value = true
    return
  }
  openRoomState(room.auctionId, roomStateOf(room))
}
</script>

<template>
  <!-- F2: 전역 헤더(--header-h)만큼 뷰포트 기준 높이를 보정한다 -->
  <main class="bg-cream min-h-[calc(100dvh-var(--header-h))]">
    <div class="mx-auto max-w-7xl px-6 py-10">
      <!-- 헤더 + 검색바 -->
      <div class="flex flex-wrap items-end justify-between gap-4">
        <div>
          <div class="text-dim flex items-center gap-2 text-[13px]">
            <span class="bg-red h-1.5 w-1.5 animate-pulse rounded-full" aria-hidden="true" />
            지금 방송 중
          </div>
          <h1 class="text-ink mt-1.5 text-[34px] font-bold tracking-tight">전체 라이브 방송</h1>
        </div>
        <div
          class="border-line text-faint focus-within:border-terra bg-surface flex h-10.5 w-75 items-center gap-2 rounded-full border px-4 transition"
        >
          <svg
            class="h-4 w-4 flex-none"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.9"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <circle cx="11" cy="11" r="7" />
            <path d="M21 21l-4.5-4.5" />
          </svg>
          <input
            v-model="searchInput"
            type="search"
            class="text-ink placeholder:text-faint w-full min-w-0 bg-transparent text-[13.5px] outline-none"
            placeholder="상품·카테고리 검색"
            aria-label="방송 검색"
            @input="onSearchInput"
            @search="onSearchInput"
          />
        </div>

        <!-- 방 개설. 전역 헤더에도 있지만 **여기가 판매자가 실제로 서 있는 자리다** —
             다른 방들을 둘러보다 "나도 올려야지"가 되는 화면이라 그 자리에 진입점을 둔다.
             비로그인에는 내지 않는다: 헤더 nav와 같은 판정이고, 눌러 봐야 로그인으로 튕긴다. -->
        <RouterLink
          v-if="auth.isLoggedIn"
          :to="{ name: 'room-create' }"
          class="bg-terra text-on-dark hover:bg-terra-d flex h-10.5 flex-none items-center gap-1.5 rounded-full px-5 text-[13.5px] font-semibold transition"
        >
          <svg
            class="h-4 w-4"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2.1"
            stroke-linecap="round"
            aria-hidden="true"
          >
            <path d="M12 5v14M5 12h14" />
          </svg>
          방 개설
        </RouterLink>
      </div>

      <!-- 상태 탭 -->
      <div class="bg-line mt-5 inline-flex gap-1 rounded-xl p-1">
        <button
          v-for="tab in STATE_TABS"
          :key="tab.state"
          type="button"
          class="rounded-lg px-4 py-2 text-[13px] transition"
          :class="
            activeState === tab.state
              ? 'text-ink bg-surface font-semibold shadow-sm'
              : 'text-dim hover:text-ink'
          "
          @click="selectState(tab.state)"
        >
          {{ tab.label }}
          <span v-if="tabCount(tab.state) !== undefined" class="tabular-nums">
            {{ tabCount(tab.state) }}
          </span>
        </button>
      </div>

      <!-- 카테고리 칩 -->
      <div class="mt-3.5 mb-5 flex flex-wrap gap-2">
        <button
          type="button"
          class="rounded-full px-4 py-2 text-[13px] font-medium transition"
          :class="
            activeCategory === ''
              ? 'bg-ink text-cream'
              : 'border-line text-dim hover:bg-cream bg-surface border'
          "
          @click="selectCategory('')"
        >
          전체
        </button>
        <button
          v-for="cat in categoryStore.categories"
          :key="cat.code"
          type="button"
          class="rounded-full px-4 py-2 text-[13px] font-medium transition"
          :class="
            activeCategory === cat.code
              ? 'bg-ink text-cream'
              : 'border-line text-dim hover:bg-cream bg-surface border'
          "
          @click="selectCategory(cat.code)"
        >
          {{ cat.name }}
        </button>
        <p v-if="categoryStore.loadError" class="text-red self-center text-[12px]" role="alert">
          {{ categoryStore.loadError }}
        </p>
      </div>

      <!--
        ROOM-08 「곧 시작해요」 — 진행중 탭 전용 가로 스크롤 스트립.
        곧 시작하는 방이 없으면(또는 필터로 다 걸러지면) 섹션 제목까지 통째로 사라진다.
      -->
      <section v-if="upcomingRooms.length > 0" aria-labelledby="upcoming-heading" class="mb-5">
        <h2 id="upcoming-heading" class="text-ink mb-2.5 text-sm font-semibold">곧 시작해요</h2>
        <div class="flex gap-2.5 overflow-x-auto pb-1">
          <button
            v-for="room in upcomingRooms"
            :key="room.auctionId"
            type="button"
            class="border-line hover:border-faint hover:shadow-stage/20 bg-surface w-49 flex-none overflow-hidden rounded-xl border text-left transition duration-200 ease-out hover:-translate-y-[3px] hover:shadow-lg motion-reduce:transition-none motion-reduce:hover:translate-y-0"
            @click="openRoomState(room.auctionId, 'waiting')"
          >
            <!--
              **실사진 썸네일이다** — 그리드 RoomCard의 그라데이션 placeholder와 정책이 갈린다.
              와이어프레임 livelist의 `.sthumb`가 이미지 배경(`background:center/cover`)이고,
              스트립 카드에는 LIVE 배지·남은 시간처럼 그 위에 얹을 진행 상태 연출이 없다 —
              placeholder로 두면 회색 칸만 남는다. 없으면(`imageUrl === null`) 토큰 배경으로 떨어진다.
            -->
            <div
              class="bg-line h-21 bg-cover bg-center brightness-92"
              aria-hidden="true"
              :style="
                room.imageUrl === null ? undefined : { backgroundImage: cssUrl(room.imageUrl) }
              "
            />
            <div class="px-2.75 pt-2.25 pb-2.75">
              <div class="text-ink line-clamp-2 h-8.5 text-[12.5px] leading-[1.35] font-semibold">
                {{ room.productTitle }}
              </div>
              <!-- 슬레이트 = 예정된 일 (AGENTS.md 기능색 2) -->
              <div class="text-slate mt-1 text-xs font-bold tabular-nums">
                {{ upcomingLabel(room.scheduledAt) }}
              </div>
            </div>
          </button>

          <!--
            잘린 방이 있을 때만 붙는다 — 없는데 띄우면 사용자를 빈 대기 탭으로 보내게 된다.
            ⚠️ N은 **서버가 센 잘린 개수**(hiddenCount)라 지금 걸린 검색어를 반영하지 않는다.
            ROOM-08이 `keyword`를 받지 않아 필터가 켜진 동안에는 근사값이다 — 눌러서 가는 대기 탭은
            검색어가 걸린 목록이라 실제로 보이는 방이 N보다 적을 수 있다.
            반대 방향으로도 근사다: 대기 탭에는 **1시간 창 제한이 없어** 더 뒤에 시작하는 방까지
            들어오므로 N보다 많이 보일 수 있다. 두 값이 맞아떨어지는 게 이 카드의 약속이 아니다.
          -->
          <button
            v-if="roomStore.upcomingHidden > 0"
            type="button"
            class="border-line text-dim hover:border-faint hover:text-ink w-26 flex-none rounded-xl border border-dashed px-2 text-[12px] font-medium transition"
            @click="selectState('waiting')"
          >
            → 더보기 +{{ roomStore.upcomingHidden }}개
          </button>
        </div>
      </section>

      <!-- 카드 그리드 -->
      <div
        v-if="visibleRooms.length > 0"
        class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
      >
        <RoomCard
          v-for="room in visibleRooms"
          :key="room.auctionId"
          :room="room"
          @select="openRoom"
        />
      </div>

      <!-- 빈 상태 + 방 만들기 CTA. 조회 중에는 "없어요"라고 단정하지 않는다 -->
      <div v-else-if="roomStore.loading" class="text-faint py-18 text-center text-[13px]">
        방송을 불러오는 중이에요
      </div>
      <div v-else class="py-18 text-center">
        <div class="text-dim text-[15px] font-semibold">조건에 맞는 방송이 없어요</div>
        <div class="text-faint mt-1.5 text-[12.5px]">검색어를 바꾸거나 카테고리를 넓혀 보세요</div>
        <RouterLink
          to="/rooms/new"
          class="bg-terra text-on-dark hover:bg-terra-d mt-6 inline-flex items-center gap-1.5 rounded-full px-5 py-2.5 text-sm font-semibold transition"
        >
          + 방 만들기
        </RouterLink>
      </div>

      <!--
        무한 스크롤 센티널. 화면에 들어오면 다음 페이지를 받는다.
        실패·관찰자 미지원일 때를 위해 **누를 수 있는 버튼**으로 둔다 — 스크롤만으로는
        실패한 뒤 다시 시도할 방법이 없고, 키보드 사용자는 센티널에 닿지도 못한다.
      -->
      <div v-if="roomStore.hasNext" ref="sentinel" class="pt-8 pb-4 text-center">
        <button
          type="button"
          class="border-line text-dim bg-surface hover:bg-cream rounded-full border px-5 py-2 text-[13px] font-medium transition disabled:opacity-60"
          :disabled="roomStore.loading"
          @click="loadMore"
        >
          {{ roomStore.loading ? '불러오는 중…' : '더 보기' }}
        </button>
      </div>

      <p v-if="roomStore.loadError" class="text-red mt-3 text-center text-[12.5px]" role="alert">
        {{ roomStore.loadError }}
      </p>
    </div>

    <DemoRoomNotice v-if="demoNoticeOpen" @close="demoNoticeOpen = false" />
  </main>
</template>
