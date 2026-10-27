<script setup lang="ts">
// PRED-05 마이페이지 「리더보드」 패널 — 와이어프레임 mypage의 `board` 패널.
//
// 순위·수익률은 서버가 끝낸 계산이다(그날 정산분만, `payoutRate = 지급 ÷ 스테이크 × 100`).
// 화면은 정렬도 재계산도 하지 않는다 — 동률 처리 규칙까지 서버에 있다.
//
// ⚠️ 날짜를 넘기지 않으므로 **오늘이 아니라 어제 순위다**(기본값 Asia/Seoul 어제, 오늘은 400).
// 문구도 전부 어제 기준이다. 날짜 선택 UI는 없다 — 필요해지면 그때 붙인다.
//
// `myRank`는 목록과 별도로 받는다. 순위권 밖이어도 자기 순위는 보여야 하기 때문이다.
import { computed, onMounted, ref } from 'vue'
import { toApiError } from '@/shared/api/errors'
import {
  getDailyLeaderboard,
  getMyPredictionAchievements,
  type LeaderboardEntry,
  type PredictionAchievementsResponse,
} from '@/shared/api/predictions'
import { formatPoint } from '@/shared/lib/point'
import { formatPayoutRate, rankMark } from '@/shared/lib/leaderboard'

const items = ref<LeaderboardEntry[]>([])
const myRank = ref<LeaderboardEntry | null>(null)
const pending = ref(true)
const loadError = ref<string | null>(null)

const isEmpty = computed(() => items.value.length === 0)

// 「집계 중이에요」 문구는 붙일 자리가 없다 (MR !285 리뷰 제안 검토 결과).
// 옛 명세엔 `503 LEADERBOARD_NOT_READY`가 있었지만 구현·현행 명세 어디에도 없다 —
// `PredictionLeaderboardQueryErrorCode`는 `INVALID_RANKING_DATE`·`INVALID_PAGE_SIZE`·
// `INVALID_CURSOR`·`UNAUTHORIZED` 넷뿐이고 핸들러는 400/401만 낸다.
// 배치 미실행은 이제 **빈 응답**으로 오고, 명세가 "집계 대상 0명과 배치 미실행은 구분하지 않는다"고
// 못 박아서 화면이 둘을 갈라 말할 근거 자체가 없다. 코드로 분기하면 영영 안 도는 죽은 가지가 된다.

// 순위 표기·수익률 서식은 홈 티커와 **같은 규칙**이라 `shared/lib/leaderboard`에 둔다.
// 각자 들고 있으면 한쪽만 고쳐도 아무도 모른다 (S15P11A704-76).

// ── PRED-04 예측 업적 (S15P11A704-561) ──
//
// 와이어프레임에 업적 화면이 없어서(명세 P2) 새 탭·모달을 만들지 않고 이 패널 머리에 얹는다.
// 등급명·뱃지명·누적 집계는 **전부 서버가 계산해 문자열까지 내려준 값**이라 화면에 판정 로직이 없다.
//
// 적중률은 여기 싣지 않는다 — 마이페이지 헤더 활동 요약(`GET /members/me`의 `predictionHitRate`)이
// 이미 같은 화면에서 말하고 있고, 그 값은 **여기와 같은 집계다**(BE 대조: 같은 조인·같은 필터,
// 차이는 반올림 자릿수뿐 — scale 2 대 1이고 화면은 양쪽 다 `Math.round`라 사실상 같은 정수다).
// 다만 자릿수가 다르니 **이중 반올림 경계값에서는 1 차이가 날 수 있다**: 45.4545…라면
// scale 1은 45.5 → 46, scale 2는 45.45 → 45다. 그래서 1 차이는 반올림 경계일 뿐일 수 있다.
// 그보다 벌어지면 **표기를 맞출 게 아니라 데이터 버그로 봐야 한다.**
// 이 카드가 존재하는 이유는 헤더에 없는 것 — 등급·뱃지·배당이다.
const achievements = ref<PredictionAchievementsResponse | null>(null)
const achievementsPending = ref(true)

/** 요약 한 줄. 헤더 활동 요약에 없는 값만 담는다 */
const achievementSummary = computed(() => {
  const a = achievements.value
  if (a === null) return ''
  // 배당은 리더보드의 지급액과 같은 베팅 포인트다 — 같은 패널 안에서 단위를 갈라 쓰지 않는다
  return `참여 ${a.predictionCount}회 · 누적 배당 ${formatPoint(a.totalRewardPoints, 'BETTING')}`
})

async function loadLeaderboard() {
  try {
    const response = await getDailyLeaderboard()
    items.value = response.items
    myRank.value = response.myRank
  } catch (error) {
    loadError.value = toApiError(error).message
  } finally {
    pending.value = false
  }
}

/**
 * 업적은 **곁들이**라서 실패하면 조용히 접는다 — 오류 문구를 띄우면
 * 리더보드가 멀쩡히 떠 있는데도 화면 전체가 고장 난 것처럼 읽힌다(홈 티커가 401을 접는 것과 같은 판단).
 * 조회도 리더보드와 분리해서 던진다 — 한쪽 실패가 다른 쪽으로 옮지 않는다(테스트가 양방향으로 잠근다).
 */
async function loadAchievements() {
  try {
    achievements.value = await getMyPredictionAchievements()
  } catch {
    achievements.value = null
  } finally {
    achievementsPending.value = false
  }
}

onMounted(() => {
  void loadLeaderboard()
  void loadAchievements()
})
</script>

<template>
  <div>
    <!-- PRED-04 내 예측 업적 — 등급 칩(베팅이라 슬레이트 계열) + 누적 요약 + 뱃지 -->
    <section
      v-if="achievements"
      class="border-line bg-surface mb-3 rounded-2xl border px-4 py-3"
      aria-label="내 예측 업적"
    >
      <div class="flex flex-wrap items-center gap-2">
        <span
          class="bg-slate-tint text-slate-tint-text rounded-full px-2.5 py-1 text-[12px] font-bold"
        >
          {{ achievements.grade.name }}
        </span>
        <span class="text-faint text-[11.5px] tabular-nums">{{ achievementSummary }}</span>
      </div>

      <ul v-if="achievements.badges.length > 0" class="mt-2 flex flex-wrap gap-1.5">
        <!--
          획득일은 싣지 않는다. `title`은 포인터로 호버해야만 보여서 키보드·터치에서는 도달할 수 없고,
          `<li>`는 포커스도 못 받는다 — 절반의 사용자에게만 있는 정보가 된다.
          와이어프레임에 업적 화면 자체가 없어 접근 가능한 자리를 만들 근거도 아직 없다.
        -->
        <li
          v-for="badge in achievements.badges"
          :key="badge.badgeCode"
          class="border-line text-ink rounded-full border px-2 py-0.5 text-[11px]"
        >
          {{ badge.name }}
        </li>
      </ul>
      <p v-else class="text-faint mt-2 text-[11.5px]">아직 획득한 뱃지가 없어요.</p>
    </section>

    <!--
      조회 중에는 **자리를 비우지 않는다** — 나중에 나타나면 아래 리더보드가 통째로 밀린다(홈 티커와 같은 판단).
      실패하면 그때 통째로 접는다(위 `v-if`가 이미 그렇게 한다).
      높이는 계산해 못 박지 않고 **위 카드와 같은 마크업을 `invisible`로 둬서** 브라우저가 재게 한다 —
      칩·뱃지 크기를 손대도 자리표시자가 저절로 따라온다. 빈 칸은 `&nbsp;`다(줄 상자가 있어야 높이가 산다).
    -->
    <section
      v-else-if="achievementsPending"
      class="border-line bg-surface mb-3 rounded-2xl border px-4 py-3"
    >
      <p class="sr-only">예측 업적을 불러오는 중이에요</p>
      <div class="invisible" aria-hidden="true">
        <div class="flex flex-wrap items-center gap-2">
          <span
            class="bg-slate-tint text-slate-tint-text rounded-full px-2.5 py-1 text-[12px] font-bold"
            >&nbsp;</span
          >
          <span class="text-faint text-[11.5px]">&nbsp;</span>
        </div>
        <ul class="mt-2 flex flex-wrap gap-1.5">
          <li class="border-line text-ink rounded-full border px-2 py-0.5 text-[11px]">&nbsp;</li>
        </ul>
      </div>
    </section>

    <p class="text-faint text-[11.5px] leading-relaxed">
      어제 낙찰가 예측 수익률 순위 · 정산된 베팅만 집계 · 매일 자정 이후 갱신
    </p>

    <p v-if="pending" class="text-faint py-8 text-center text-[13px]">불러오는 중이에요…</p>
    <p v-else-if="loadError" class="text-red py-8 text-center text-[13px]" role="alert">
      {{ loadError }}
    </p>
    <p v-else-if="isEmpty" class="text-faint py-8 text-center text-[13px]">
      어제 정산된 예측이 없어요.
    </p>

    <template v-else>
      <!-- 내 순위 — 목록에 없더라도 따로 보여준다 -->
      <div
        v-if="myRank"
        class="bg-slate-tint border-slate mt-3 flex items-center gap-3 rounded-2xl border px-4 py-3"
      >
        <span class="text-slate-tint-text w-11 flex-none text-[13px] font-bold tabular-nums">
          {{ rankMark(myRank.rank) }}
        </span>
        <div class="min-w-0 flex-1">
          <p class="text-ink text-[13.5px] font-semibold">내 순위 · {{ myRank.nickname }}</p>
          <p class="text-slate-tint-text mt-0.5 text-[11.5px] tabular-nums">
            정산 {{ myRank.settledPredictionCount }}건 ·
            {{ formatPoint(myRank.totalStakedPoints, 'BETTING') }} →
            {{ formatPoint(myRank.totalPayoutPoints, 'BETTING') }}
          </p>
        </div>
        <span class="text-slate flex-none text-[15px] font-bold tabular-nums">
          {{ formatPayoutRate(myRank.payoutRate) }}
        </span>
      </div>

      <ol class="mt-2.5 flex flex-col gap-1.5">
        <li
          v-for="entry in items"
          :key="entry.rank"
          class="border-line bg-surface flex items-center gap-3 rounded-xl border px-4 py-2.5"
        >
          <span class="w-11 flex-none text-[13px] font-bold tabular-nums">
            {{ rankMark(entry.rank) }}
          </span>
          <div class="min-w-0 flex-1">
            <p class="text-ink truncate text-[13px] font-semibold">{{ entry.nickname }}</p>
            <p class="text-faint mt-0.5 text-[11px] tabular-nums">
              정산 {{ entry.settledPredictionCount }}건 ·
              {{ formatPoint(entry.totalStakedPoints, 'BETTING') }} →
              {{ formatPoint(entry.totalPayoutPoints, 'BETTING') }}
            </p>
          </div>
          <span class="text-success flex-none text-[14px] font-bold tabular-nums">
            {{ formatPayoutRate(entry.payoutRate) }}
          </span>
        </li>
      </ol>
    </template>
  </div>
</template>
