<script setup lang="ts">
// PRED-05 어제 예측 수익률 리더보드 티커 (S15P11A704-309). 디자인 원본 `main`·`livelist`의 `.ticker`.
//
// ⚠️ **오늘이 아니라 어제 순위다** — 날짜를 생략한 조회의 기본값이 Asia/Seoul 어제이고,
// 오늘 날짜를 넘기면 400이다. 문구를 "오늘"로 되돌리면 화면이 조용히 거짓말한다.
//
// 트랙을 2벌 복제해 `-50%`까지 흐르면 첫 벌의 시작과 정확히 겹친다 — 원본 `.tk-half` 2개 구조 그대로다.
// 두 번째 벌은 같은 내용의 시각적 반복이라 `aria-hidden`으로 스크린리더에서 뺀다.
//
// hover 정지는 장식이 아니다: 흐르는 텍스트가 클릭 목표라 멈추지 않으면 누를 수가 없다.
// (원본도 같은 이유로 주석을 달아 뒀다.)
//
// ⚠️ 진행 중 베팅은 들어오지 않는다 — **정산된 것만** 집계한다. 진행 중 베팅을 넣으면
// 예측이 새어나가 herding 방지 원칙이 깨진다(AGENTS.md 규칙 7). 그 필터는 서버 몫이고,
// 순위·수익률도 서버가 끝낸 계산이라 화면은 정렬도 재계산도 하지 않는다.
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { getDailyLeaderboard, type LeaderboardEntry } from '@/shared/api/predictions'
import { formatPayoutRate, rankMark } from '@/shared/lib/leaderboard'

/** 명세 비고: 홈은 `size=10`, 마이페이지는 커서 페이지(기본 10, 최대 100) */
const HOME_SIZE = 10

const entries = ref<LeaderboardEntry[]>([])
const pending = ref(true)

/**
 * 흐를지 말지. **항목이 적으면 흐르면 안 된다.**
 *
 * 2벌 복제 + `translateX(-50%)`는 한 벌이 컨테이너보다 **넓을 때만** 이음매가 맞물린다.
 * 좁으면 내용이 왼쪽으로 밀려가고 오른쪽에 빈자리가 생겼다가 38초마다 튄다.
 * 예전엔 목이 항상 10명이라 이 상황이 없었지만, 이제 "그날 정산된 예측" 기준이라
 * 서비스 초기나 데모 첫날엔 1~3명이 정상 케이스다.
 *
 * 세는 게 아니라 **재서** 판단한다 — 닉네임 길이와 화면 폭에 따라 개수 기준은 어긋난다.
 */
const trackEl = ref<HTMLElement | null>(null)
const scrolling = ref(false)
let pillObserver: ResizeObserver | null = null

function measure() {
  const track = trackEl.value
  const pill = track?.closest('.ticker')
  if (!track || !(pill instanceof HTMLElement)) return
  // 복제본 수로 나눠 **한 벌의 폭**을 얻는다. 이 값은 복제 수와 무관하므로 판정이 진동하지 않는다.
  const halfWidth = track.scrollWidth / (scrolling.value ? 2 : 1)
  scrolling.value = halfWidth > pill.clientWidth
}

onMounted(async () => {
  try {
    const response = await getDailyLeaderboard({ size: HOME_SIZE })
    entries.value = response.items
  } catch {
    // 티커는 홈의 곁다리다. 실패를 화면에 띄우는 대신 조용히 접는다 —
    // 여기서 오류 배너를 그리면 정작 중요한 라이브 목록 위에 얹힌다.
    entries.value = []
  } finally {
    pending.value = false
  }

  await nextTick()
  measure()

  // 창 폭이 바뀌면 판정도 바뀐다 (좁히면 흘러야 하고, 넓히면 멈춰야 한다)
  const pill = trackEl.value?.closest('.ticker')
  if (pill instanceof HTMLElement && typeof ResizeObserver !== 'undefined') {
    pillObserver = new ResizeObserver(() => measure())
    pillObserver.observe(pill)
  }
})

onBeforeUnmount(() => {
  pillObserver?.disconnect()
  pillObserver = null
})
</script>

<template>
  <!--
    조회 전에는 **자리를 비우지 않는다** — 나중에 나타나면 아래 목록이 통째로 밀린다.
    받아 보니 비었거나(그날 정산된 예측이 없다) 실패했으면 그때는 통째로 접는다.
    빈 알약만 남기면 고장난 것처럼 보이고, 티커는 없어도 되는 요소다.
  -->
  <div
    v-if="pending"
    class="border-line bg-surface text-faint rounded-full border py-[9px] text-center text-[12.5px] leading-5"
  >
    어제의 예측 순위를 불러오는 중이에요
  </div>

  <!-- 클릭하면 마이페이지 리더보드 탭으로 (탭은 S15P11A704-336에서 생겼다) -->
  <RouterLink
    v-else-if="entries.length > 0"
    :to="{ name: 'my-page', query: { tab: 'board' } }"
    class="ticker border-line bg-surface relative block overflow-hidden rounded-full border py-[9px]"
    title="어제 낙찰가 예측 수익률 순위 · 눌러서 전체 보기"
  >
    <div ref="trackEl" class="flex" :class="scrolling ? 'tk-track w-max' : 'w-full justify-center'">
      <!-- 흐르지 않을 땐 복제본을 두지 않는다 — 같은 이름이 두 번 보인다 -->
      <div
        v-for="half in scrolling ? 2 : 1"
        :key="half"
        class="tk-half"
        :class="!scrolling && 'tk-half-static'"
        :aria-hidden="half === 2 || undefined"
      >
        <span v-for="entry in entries" :key="entry.nickname" class="tk-rank">
          <span class="text-[13px]">{{ rankMark(entry.rank) }}</span>
          <b class="text-ink font-semibold">{{ entry.nickname }}</b>
          <span class="text-terra font-bold tabular-nums">
            {{ formatPayoutRate(entry.payoutRate, { withSign: true }) }}
          </span>
        </span>
      </div>
    </div>
  </RouterLink>
</template>

<style scoped>
/* 양 끝 페이드 — 글자가 테두리에서 잘리지 않고 사라지게 */
.ticker::before,
.ticker::after {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  width: 64px;
  z-index: 2;
  pointer-events: none;
}
.ticker::before {
  left: 0;
  background: linear-gradient(90deg, var(--color-surface) 20%, transparent);
}
.ticker::after {
  right: 0;
  background: linear-gradient(270deg, var(--color-surface) 20%, transparent);
}

.tk-track {
  animation: tk 38s linear infinite;
}
.ticker:hover .tk-track,
.ticker:focus-visible .tk-track {
  animation-play-state: paused;
}

.tk-half {
  display: flex;
  gap: 44px;
  padding-right: 44px;
  white-space: nowrap;
  font-size: 12.5px;
  /*
    줄 높이를 **못 박는다.** 안 박으면 줄 상자를 안쪽의 13px 메달 span이 정하는데,
    그 값이 로딩 알약(12.5px)보다 1px 커서 데이터가 도착하는 순간 아래 목록이 그만큼 밀린다.
    자리표시자를 둔 이유가 바로 그걸 막으려는 거라 둘을 같은 값으로 고정한다
    (알약 쪽은 `leading-5` = 20px). 기존 렌더 높이(40px)와 같은 값이라 보이는 건 그대로다.
  */
  line-height: 20px;
  color: var(--color-dim);
}
/* 흐르지 않을 땐 이어붙일 다음 벌이 없다 — 오른쪽 여백을 두면 가운데 정렬이 그만큼 밀린다 */
.tk-half.tk-half-static {
  padding-right: 0;
}

.tk-rank {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

@keyframes tk {
  to {
    transform: translateX(-50%);
  }
}

/* 무한 루프는 정지시킨다 — 순위 내용은 그대로 읽을 수 있다 */
@media (prefers-reduced-motion: reduce) {
  .tk-track {
    animation: none;
  }
}
</style>
