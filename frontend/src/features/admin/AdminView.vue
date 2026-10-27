<script setup lang="ts">
// 운영자 콘솔 (S15P11A704-525) — 신고 접수 방 / 전체 방 두 탭에서 행마다 2단계 강제 종료.
//
// **라우터 가드가 없다.** 서버가 403을 주므로 화면이 그걸 그린다 — 가드를 두면 role 판정이
// 서버와 라우터 두 곳에 생기고, 둘이 어긋나면 조용히 갈린다.
//
// 보내는 `reason`은 운영 고정값이고 운영자가 고르지 않는다 — 신고 사유를 실으면
// `AUCTION_CANCELED`로 그 방 시청자 전원에게 브로드캐스트된다(`useAdminForceEnd`의 근거 주석).
// 서버는 받지만 저장하지 않으므로(`admin.ts`의 `adminNote` 주석) **감사 기록도 아니다**.
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAdminReports, type ReportedAuctionItem } from '@/shared/api/admin'
import { getAuctionRooms, type AuctionRoomSummary, type AuctionStatus } from '@/shared/api/rooms'
import { extractApiErrorStatus } from '@/shared/lib/apiError'
import { useAuthStore } from '@/stores/auth'
import ReportedAuctionRow from './ReportedAuctionRow.vue'
import { useAdminForceEnd } from './useAdminForceEnd'
import type { Id } from '@/shared/api/types'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

/**
 * 운영자인가 (S15P11A704-714).
 *
 * **서버 403에만 기대면 「전체 방」 탭이 안 막힌다.** 그 탭이 부르는 방 목록은 운영자 API가
 * 아니라 공개 API(`GET /auction-rooms`, `SecurityConfig`에서 GET은 permitAll)라 누구에게나
 * 200이 온다 — 비운영자가 진행 중인 방 전체를 「강제 종료」 버튼과 함께 보게 된다.
 * 서버가 판정을 주지 않는 경로라 화면이 그릴 근거가 아예 없다.
 *
 * 라우터 가드를 두지 않는 것(`app/router.ts`)은 그대로다 — 판정을 여기 한 곳에만 둔다.
 * 강제 종료 자체는 서버가 `hasRole("ADMIN")`으로 막으므로 이건 노출 차단이지 인가가 아니다.
 * `restoreSession()`을 라우터 가드가 이미 기다리므로 마운트 시점엔 값이 서 있다.
 */
const isAdmin = computed(() => auth.user?.role === 'ADMIN')

type AdminTab = 'reports' | 'rooms'

const TABS: { key: AdminTab; label: string }[] = [
  { key: 'reports', label: '신고 접수 방' },
  { key: 'rooms', label: '전체 방' },
]

const TAB_KEYS = TABS.map((tab) => tab.key)

function isAdminTab(value: unknown): value is AdminTab {
  return typeof value === 'string' && (TAB_KEYS as string[]).includes(value)
}

function readTabFromQuery(): AdminTab {
  return isAdminTab(route.query.tab) ? route.query.tab : 'reports'
}

// 탭은 `?tab=`으로 공유·새로고침이 되게 쿼리에 노출한다. 쿼리가 정본이고 화면 상태는 그 사본이다.
const activeTab = ref<AdminTab>(readTabFromQuery())

/**
 * 알아볼 수 없는 `?tab=` 값은 URL에서도 정리한다 (`MyPageView`와 같은 규약).
 * 화면만 기본 탭으로 떨어뜨리고 주소를 그대로 두면 공유·새로고침 때마다 같은 어긋남이 반복된다.
 */
function normalizeTabQuery() {
  const raw = route.query.tab
  if (raw === undefined || isAdminTab(raw)) return
  void router.replace({ query: { ...route.query, tab: activeTab.value } })
}

function selectTab(tab: AdminTab) {
  if (tab === activeTab.value) return
  void router.replace({ query: { ...route.query, tab } })
}

watch(
  () => route.query.tab,
  () => {
    activeTab.value = readTabFromQuery()
    normalizeTabQuery()
  },
)

/**
 * 「전체 방」 탭의 상태 필터. **`ENDED`를 두지 않는다** — 이미 끝난 방은 강제 종료가 409라
 * 이 화면에서 할 수 있는 일이 없다.
 */
const ROOM_FILTERS: { key: AuctionStatus; label: string }[] = [
  { key: 'LIVE', label: '진행 중' },
  { key: 'WAITING', label: '대기' },
]

const roomStatus = ref<AuctionStatus>('LIVE')

// ── 목록 ──
//
// 두 탭이 **배열 하나**를 쓴다. 탭·필터가 바뀔 때마다 새로 받으므로 탭별 캐시가 필요 없고,
// 캐시를 두면 한쪽에서 종료한 방이 다른 쪽에 남아 눌러도 409만 나는 행이 생긴다.
const items = ref<ReportedAuctionItem[]>([])
const cursor = ref<string | null>(null)
const hasNext = ref(false)
const pending = ref(false)
/** 원본 오류를 그대로 쥔다 — 403·401 분기가 상태 코드로 갈린다 */
const loadError = ref<unknown>(null)

/**
 * 「전체 방」 응답을 행이 아는 모양으로 옮긴다.
 * 신고 정보가 없으므로 `reasonCounts`가 비고, 그러면 행이 사유 요약을 그리지 않고
 * 종료 사유도 `OTHER`로 채운다.
 */
function fromRoom(room: AuctionRoomSummary): ReportedAuctionItem {
  return {
    auctionId: room.auctionId,
    title: room.title,
    thumbnailUrl: room.thumbnailUrl,
    sellerNickname: room.sellerNickname,
    status: room.status,
    reportCount: 0,
    reasonCounts: [],
    // 타입을 맞추는 채움값이다 — 이 화면은 어디서도 읽지 않는다(방 목록에 신고 시각이 없다)
    latestReportedAt: '',
  }
}

async function fetchPage(append: boolean) {
  const cur = append ? (cursor.value ?? undefined) : undefined
  if (activeTab.value === 'reports') return await getAdminReports({ cursor: cur })

  const page = await getAuctionRooms({ status: roomStatus.value, cursor: cur })
  return { items: page.items.map(fromRoom), nextCursor: page.nextCursor, hasNext: page.hasNext }
}

/**
 * 요청 세대. **탭·필터가 배열 하나를 공유해서 필요하다** — 늦게 온 옛 응답이 그냥 덮으면
 * 「대기」 필터 밑에 진행 중인 방이 그려지고, 운영자는 대기 방을 끝낸다고 믿으며 라이브를 끝낸다.
 * 되돌릴 수 없는 동작 화면이라 목록 라벨이 거짓말하면 안 된다.
 * (`features/console/useQuestionQueue.ts`·`features/auth/OnboardingStep.vue`가 같은 가드를 쓴다.)
 */
let seq = 0

async function load(append = false) {
  // 운영자가 아니면 아무것도 부르지 않는다 — 「전체 방」 탭의 공개 API가 200을 주기 때문에
  // 응답이 오면 그대로 목록이 그려진다 (S15P11A704-714). `onMounted`와 탭·필터 watch가
  // 모두 이 함수를 거치므로 가드도 여기 하나면 된다.
  if (!isAdmin.value) return
  // 서버가 `hasNext: true`인데 `nextCursor: null`을 주면 커서 없이 1페이지를 다시 받아 뒤에 붙인다 —
  // 같은 `auctionId`가 중복 `:key`로 들어가고 `hasNext`가 계속 true라 무한 반복이다.
  // (`stores/room.ts`의 `loadMoreRooms`와 같은 가드. 나머지 조건은 버튼의 `v-if`·`disabled`가 이미 본다.)
  if (append && cursor.value === null) return
  const mine = ++seq
  // **맥락이 바뀌면 2단계 무장과 종료 오류를 함께 푼다** (S15P11A704-382).
  // composable은 목록을 소유하지 않아 탭·필터·페이지가 바뀐 걸 알 수 없다. 안 풀면
  // 한 탭에서 1회 + 다른 탭에서 1회로 두 번째 클릭이 곧바로 강제 종료가 되고,
  // 남은 `errorId`는 새 목록의 같은 id 행에 엉뚱하게 붙는다.
  disarm()
  clearError()
  // **맥락이 바뀌면 옛 목록도 즉시 버린다.** 안 버리면 탭·필터 라벨만 먼저 바뀌고 응답이 올 때까지
  // (axios timeout 15초까지) 옛 목록이 새 라벨 밑에 남는다 — 위 `seq` 주석의 「목록 라벨이 거짓말한다」와
  // 같은 사고고, 그 창에서 행을 두 번 누르면 다른 탭 소속 방이 강제 종료된다.
  // 실패 경로(`catch`)도 같은 일을 한다.
  if (!append) items.value = []
  pending.value = true
  loadError.value = null
  try {
    const page = await fetchPage(append)
    if (mine !== seq) return
    // 「더 보기」는 **뒤에 붙인다.** 다시 조회하면 보던 자리가 밀려 다음 행을 잘못 누른다
    items.value = append ? [...items.value, ...page.items] : page.items
    cursor.value = page.nextCursor
    hasNext.value = page.hasNext
  } catch (error) {
    if (mine !== seq) return
    loadError.value = error
    // 이어받기 실패는 이미 본 목록을 지우지 않는다 —
    // 템플릿의 전면 오류도 `items.length === 0`일 때만 뜬다(둘이 어긋나면 이 가드가 죽는다).
    if (!append) {
      items.value = []
      hasNext.value = false
    }
  } finally {
    // 더 새로운 조회가 이미 돌고 있으면 그쪽이 주인이다 — 여기서 풀면 비행 중인 쪽 잠금까지 푼다
    if (mine === seq) pending.value = false
  }
}

const { armedId, pendingId, error, errorId, arm, disarm, clearError, confirm } = useAdminForceEnd(
  (auctionId: Id) => {
    items.value = items.value.filter((item) => item.auctionId !== auctionId)
  },
)

watch([activeTab, roomStatus], () => void load())
onMounted(() => {
  normalizeTabQuery()
  void load()
})

/**
 * 목록 오류 안내. 403·401은 다시 눌러도 같은 결과라 재시도 버튼을 감춘다.
 * 403은 **비ADMIN 진입의 정상 경로**다 — 사고가 아니라 안내다.
 */
const loadErrorInfo = computed(() => {
  if (loadError.value === null) return null
  const status = extractApiErrorStatus(loadError.value)
  if (status === 403) {
    return { title: '권한이 없어요.', hint: '운영자 계정으로 로그인해 주세요.', retry: false }
  }
  if (status === 401) return { title: '로그인이 필요해요.', hint: '', retry: false }
  return { title: '불러오지 못했어요.', hint: '', retry: true }
})

/**
 * 행에 붙일 수 없는 종료 오류.
 *
 * 409(이미 종료됨)·404는 그 행을 목록에서 **지우므로** `errorId`가 null이다.
 * `errorId === row.auctionId`로만 그리면 「이미 종료된 경매예요」가 영영 렌더되지 않는다 —
 * 그래서 목록 바깥의 이 자리가 필요하다.
 */
const detachedError = computed(() => (errorId.value === null ? error.value : null))
</script>

<template>
  <main class="bg-cream min-h-[calc(100dvh-var(--header-h))]">
    <div class="mx-auto max-w-[820px] px-6 py-10">
      <header class="mb-5">
        <h1 class="text-ink text-2xl font-bold tracking-tight">운영자 콘솔</h1>
        <p class="text-faint mt-1 text-[12.5px]">
          강제 종료하면 입찰 보류금과 예측 포인트가 전액 환급되고 방이 즉시 종료돼요. 되돌릴 수
          없어요.
        </p>
      </header>

      <!-- 운영자가 아니면 탭도 목록도 그리지 않는다 (S15P11A704-714).
           탭 바만 남겨 두면 기본 탭의 403 카드 옆에서 「전체 방」을 눌러 공개 API 목록을
           띄울 수 있다 — 「권한이 없어요」와 강제 종료 버튼이 한 화면에 공존하게 된다. -->
      <div v-if="!isAdmin" class="py-10 text-center">
        <p class="text-ink text-[14px] font-semibold" role="alert">권한이 없어요.</p>
        <p class="text-faint mt-1 text-[12.5px]">운영자 계정으로 로그인해 주세요.</p>
      </div>

      <div
        v-if="isAdmin"
        class="bg-line inline-flex gap-1 rounded-xl p-1"
        role="tablist"
        aria-label="운영자 콘솔 탭"
      >
        <button
          v-for="tab in TABS"
          :id="`admin-tab-${tab.key}`"
          :key="tab.key"
          type="button"
          role="tab"
          class="rounded-lg px-3.5 py-1.5 text-[12.5px] transition"
          :class="
            activeTab === tab.key ? 'bg-surface text-ink font-semibold shadow-sm' : 'text-dim'
          "
          :aria-selected="activeTab === tab.key"
          aria-controls="admin-panel"
          @click="selectTab(tab.key)"
        >
          {{ tab.label }}
        </button>
      </div>

      <div
        v-if="isAdmin"
        id="admin-panel"
        role="tabpanel"
        :aria-labelledby="`admin-tab-${activeTab}`"
        class="mt-4"
      >
        <!-- 상태 필터는 「전체 방」 탭에만 있다 — 신고 목록은 서버가 이미 진행 중·대기만 준다 -->
        <div v-if="activeTab === 'rooms'" class="mb-3.5">
          <div class="flex gap-1.5">
            <button
              v-for="filter in ROOM_FILTERS"
              :key="filter.key"
              type="button"
              class="rounded-full border px-3 py-1 text-[11.5px] font-medium transition"
              :class="
                roomStatus === filter.key
                  ? 'bg-ink text-on-dark border-ink'
                  : 'border-line text-dim bg-surface'
              "
              :aria-pressed="roomStatus === filter.key"
              @click="roomStatus = filter.key"
            >
              {{ filter.label }}
            </button>
          </div>
          <!-- 「전체 방」이라는 탭 이름이 없는 걸 약속하지 않게 한 줄로 밝힌다 -->
          <p class="text-faint mt-1.5 text-[11.5px]">
            종료된 방은 강제 종료할 수 없어 목록에 없어요.
          </p>
        </div>

        <!-- 행이 사라진 뒤에도 보여야 하는 종료 오류 (409·404) -->
        <p
          v-if="detachedError"
          class="border-line bg-surface text-red mb-3 rounded-xl border px-4 py-2.5 text-[12.5px]"
          role="alert"
        >
          {{ detachedError }}
        </p>

        <!--
          목록이 남아 있는데 이어받기가 실패한 경우. 전면 오류로 갈아치우면 보던 목록이
          통째로 사라지고, 「다시 시도」는 커서를 버려 1페이지부터 다시 받는다 —
          운영자는 보던 자리와 이어받은 페이지를 둘 다 잃는다. 「더 보기」가 그대로 재시도다.

          재시도 안내는 `retry`일 때만 붙인다 — 401(refresh까지 실패한 죽은 세션)·403은 몇 번을
          눌러도 같은 결과라, 전면 오류가 재시도 버튼을 감추는 것과 여기가 반대 말을 하면 안 된다.
        -->
        <p
          v-if="loadErrorInfo && items.length > 0"
          class="border-line bg-surface text-red mb-3 rounded-xl border px-4 py-2.5 text-[12.5px]"
          role="alert"
        >
          {{ loadErrorInfo.title }}
          <template v-if="loadErrorInfo.retry">「더 보기」로 다시 시도할 수 있어요.</template>
        </p>

        <div v-if="loadErrorInfo && items.length === 0" class="py-10 text-center">
          <p class="text-ink text-[14px] font-semibold" role="alert">{{ loadErrorInfo.title }}</p>
          <p v-if="loadErrorInfo.hint" class="text-faint mt-1 text-[12.5px]">
            {{ loadErrorInfo.hint }}
          </p>
          <button
            v-if="loadErrorInfo.retry"
            type="button"
            class="border-line text-dim bg-surface hover:bg-cream mt-3.5 rounded-full border px-4 py-1.5 text-[12px] font-medium transition"
            @click="load()"
          >
            다시 시도
          </button>
        </div>

        <p
          v-else-if="pending && items.length === 0"
          class="text-faint py-10 text-center text-[13px]"
        >
          불러오는 중이에요…
        </p>

        <p v-else-if="items.length === 0" class="text-faint py-10 text-center text-[13px]">
          {{ activeTab === 'reports' ? '접수된 신고가 없어요.' : '해당 상태의 방이 없어요.' }}
        </p>

        <ul v-else class="flex flex-col gap-2.5">
          <ReportedAuctionRow
            v-for="item in items"
            :key="item.auctionId"
            :item="item"
            :armed="armedId === item.auctionId"
            :pending="pendingId === item.auctionId"
            :busy="pendingId !== null"
            :error="errorId === item.auctionId ? error : null"
            @arm="arm"
            @confirm="confirm"
          />
        </ul>

        <div v-if="hasNext" class="pt-4 text-center">
          <button
            type="button"
            class="border-line text-dim bg-surface hover:bg-cream rounded-full border px-5 py-2 text-[12.5px] font-medium transition disabled:opacity-45"
            :disabled="pending"
            @click="load(true)"
          >
            {{ pending ? '불러오는 중…' : '더 보기' }}
          </button>
        </div>
      </div>
    </div>
  </main>
</template>
