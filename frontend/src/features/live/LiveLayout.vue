<script setup lang="ts">
// LIVE 라이브 화면 슬롯 프레임 — 소유 FE1 (docs/03-역할-경계-합의.md §4)
// 슬롯 계약: #video(FE2 WebRTC 스트림) · #chat(FE2 ChatPanel) · #action(FE1 입찰↔예측 패널).
// 레이아웃·반응형·영역 크기 배분은 이 프레임 책임 — 슬롯 컴포넌트는 부모를 100% 채운다고 가정한다.
import { computed, onUnmounted, ref } from 'vue'
import { useCountdown } from '@/shared/lib/useCountdown'
import { useLiveStore } from '@/stores/live'

/**
 * 전체화면 대상 (S15P11A704-433). #video 슬롯에 슬롯 프롭으로 내주고, 누르는 쪽(FE2 LiveStage)이 받아 쓴다.
 * 레이아웃이 프레임 책임이라(docs/03 §4) **대상 엘리먼트와 전체화면 상태 스타일까지가 이 파일 몫**이다.
 */
const stageEl = ref<HTMLElement | null>(null)

withDefaults(
  defineProps<{
    /** 제품 타이틀 (스테이지 하단 정보 바) */
    title?: string
    /** 판매자 닉네임 */
    sellerName?: string
    /** 시청자 수 — null이면 미표시 (PRESENCE 이벤트 수신 전) */
    viewerCount?: number | null
  }>(),
  { title: '', sellerName: '', viewerCount: null },
)

const emit = defineEmits<{
  /** 헤더 뒤로가기(라이브 나가기) 클릭 */
  back: []
}>()

// BID-05 서버 권위 타이머 — 표시 전용 (임박 기준 60초). 둘 다 이 방에서 얻은 값이 우선이고,
// 아직 없으면 REST에서 받아둔 값이 선다 — 마감 시각은 목록의 endAt(S15P11A704-523),
// 오프셋은 목록·상세의 serverTime(S15P11A704-265). 둘 중 하나만 채워도 칩은 `--:--`다.
// (「이 방에서 얻은 값」의 출처는 소켓만이 아니다 — 쓰기 경로는 stores/live.ts의 두 필드 주석에 있다.)
const live = useLiveStore()
const { display, urgent, remainingMs } = useCountdown(() => live.deadlineAt, {
  serverOffset: () => live.clockOffset,
})

/**
 * 마지막 5초 — 칩이 커진다. `urgent`(60초, 색)와 **다른 겹이다**: 60초는 "슬슬 끝난다",
 * 5초는 "지금 안 넣으면 끝난다"라 같은 강조로 묶으면 60초 동안 화면이 계속 소리치게 된다.
 *
 * `useCountdown`에 옵션으로 넣지 않고 여기서 센다 — 이 임계값이 필요한 곳은 이 칩뿐인데
 * 공유 컴포저블을 건드리면 카운트다운 8곳이 전부 영향권에 들어온다.
 *
 * 5초는 **서버 기준으로도 5초다**(규칙 5) — `remainingMs`가 이미 endsAt·serverOffset으로만
 * 계산된 값이고, 여기서 다시 시각을 재지 않는다.
 */
const CRITICAL_MS = 5_000
const critical = computed(() => remainingMs.value !== null && remainingMs.value <= CRITICAL_MS)

/**
 * 입찰 폭주 배너 — 짧은 시간에 입찰이 몰리면 스테이지 위에 띄운다.
 *
 * 판정은 **한 창(window)의 증가분**뿐이다: 5초마다 `bidCount`를 재고, 직전보다 3건 이상 늘었으면
 * 그 창 동안 배너가 뜬다. 타임스탬프 배열·감쇠 곡선 없이 정수 둘만 들고 있는다.
 *
 * **쿨다운이 임계값만큼 중요하다.** 임계값만 두면 붐비는 방에서는 매 창이 조건을 만족해 배너가
 * 계속 켜져 있고, 그러면 「지금 몰리고 있다」가 아니라 화면의 벽지가 된다 — 뜨는 것 자체가 신호인
 * 연출이라 상시 노출은 그 신호를 없앤다. 한 번 뜨면 30초 동안은 다시 뜨지 않는다.
 * (반대로 조용한 방에서 안 뜨는 것은 고장이 아니다 — 실제로 안 몰리고 있다.)
 *
 * ponytail: 세 상수가 연출의 전부다 — 창 길이(=노출 시간), 임계값(민감도), 쿨다운(빈도).
 * 참여자 수 대비 비율·감쇠 평균은 안 쓴다. 사람 수를 분모로 넣으면 방마다 기준이 달라져
 * 「왜 저 방은 뜨는데 여긴 안 뜨나」를 설명할 수 없고, 계산도 화면에서 검증이 안 된다.
 *
 * 규칙 5(서버 권위 타이머)와 무관하다 — 남은 시간이 아니라 **우리 화면에서 센 이벤트 밀도**라
 * 서버 시각이 기준일 이유가 없다.
 */
const SURGE_WINDOW_MS = 5_000
const SURGE_THRESHOLD = 3
/** 쿨다운 30초 = 창 6개. 창 단위로 세서 별도 타이머·시각 계산을 만들지 않는다 */
const SURGE_COOLDOWN_WINDOWS = 6
const surging = ref(false)
let previousBidCount: number | null = null
let cooldownWindows = 0
const surgeTimer = window.setInterval(() => {
  const current = live.bidCount ?? 0
  // 끊긴 동안의 집계는 멈춰 있다 — 재연결 직후 한꺼번에 따라잡은 증가분을 폭주로 읽지 않는다.
  const measurable = previousBidCount !== null && live.connectionStatus === 'connected'
  const surged = measurable && current - previousBidCount! >= SURGE_THRESHOLD
  if (surged && cooldownWindows === 0) {
    surging.value = true
    cooldownWindows = SURGE_COOLDOWN_WINDOWS
  } else {
    surging.value = false
    if (cooldownWindows > 0) cooldownWindows -= 1
  }
  previousBidCount = current
}, SURGE_WINDOW_MS)
onUnmounted(() => clearInterval(surgeTimer))
</script>

<template>
  <!-- 데스크톱: 디자인 live-wrap(스테이지 + 344px 레일, 화면 고정) / 모바일: 세로 스택 페이지 스크롤 -->
  <!-- F2: 전역 헤더(--header-h)만큼 뷰포트 기준 높이를 보정한다 -->
  <!--
    34px을 더 빼는 것은 와이어프레임의 `[data-screen="live"] .live-wrap{height:calc(100% - 34px)}`다.
    원문 주석이 이유를 적어 뒀다 — *"아래 섹션 헤더가 살짝 보이게 — 스크롤 단서"*. 아래 「다른 라이브」
    제목이 한 줄 걸쳐 보여야 스크롤할 것이 있다는 걸 알린다. 딱 맞게 채우면 그 블록이 통째로 숨는다.
    (모바일은 원래 세로 스택 페이지 스크롤이라 이 보정이 필요 없다.)
  -->
  <div
    class="bg-cream min-h-[calc(100dvh-var(--header-h))] lg:h-[calc(100dvh-var(--header-h)-34px)] lg:overflow-hidden"
  >
    <div class="flex flex-col gap-3.5 p-3 lg:h-full lg:flex-row lg:p-[18px]">
      <!-- ── 스테이지 (시네마틱 다크) — #video 슬롯 + 헤더 오버레이 ── -->
      <div class="min-w-0 lg:min-h-0 lg:flex-1">
        <!--
          `group/stage`는 **슬롯 계약의 일부**다 (S15P11A704-464). 와이어프레임이 `.stage:hover`로
          하단 정보 바와 우하단 전체화면 버튼을 함께 드러내므로, #video 슬롯(FE2)의 시청 컨트롤도
          같은 hover를 봐야 한다 — 이름 없는 `group`을 쓰면 슬롯 안에 다른 `group`이 생기는 순간
          FE2 쪽이 조용히 그것에 붙는다. 이름을 붙여 그 사고를 막는다.
          아래 정보 바는 기존 `group`을 계속 쓴다(같은 엘리먼트이므로 동작은 동일).
          ⚠️ 이 클래스를 지우면 FE2의 컨트롤이 영구히 숨는다 — LiveLayout.spec.ts가 그것을 잠근다.
        -->
        <div
          ref="stageEl"
          class="stage-frame group group/stage bg-stage relative aspect-video w-full overflow-hidden rounded-[20px] shadow-2xl lg:aspect-auto lg:h-full"
        >
          <!--
            FE2 스트림 컴포넌트가 이 영역을 100% 채운다.
            `fullscreen-target`으로 **이 컨테이너를** 내준다 (S15P11A704-433).
            전체화면 대상이 `LiveStage`의 루트면 타이머 칩·prodbar(아래 형제들)가 따라오지 않아
            남은 시간이 안 보인다 — BID-05 핵심 정보다. 그렇다고 미디어 쪽이 프레임 DOM을
            `closest()`로 찾아 올라오게 하면 남의 구조를 가정하는 건 마찬가지라, 슬롯 프롭으로 건넨다.
            버튼·상태·이벤트·iOS 폴백은 계속 FE2 몫이고, 이 프레임은 **대상과 전체화면 상태 스타일**만 진다.
          -->
          <div class="absolute inset-0">
            <slot name="video" :fullscreen-target="stageEl" />
          </div>

          <!-- 헤더(좌상단): 뒤로가기 · LIVE 뱃지 · 판매자 메타 -->
          <div class="absolute top-4 left-4 z-[4] flex items-center gap-2">
            <button
              type="button"
              class="text-on-dark flex h-[26px] w-[26px] flex-none items-center justify-center rounded-full bg-white/10 backdrop-blur-sm transition-colors hover:bg-white/20 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
              aria-label="라이브 나가기"
              title="라이브 나가기"
              @click="emit('back')"
            >
              <svg
                class="h-4 w-4"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M15 18l-6-6 6-6" />
              </svg>
            </button>
            <!--
              점이 뛰는 것은 **실시간이 살아 있다는 신호**다 (S15P11A704-438).
              소켓이 끊기면 채팅·입찰 건수·타이머 동기화가 멈추는데, 그때도 계속 뛰면
              화면이 자기모순에 빠진다 — 425가 넣은 배너는 "연결이 끊겼어요"라고 말하고
              배지는 LIVE라고 깜빡인다. 그래서 `connected`일 때만 뛴다.
              색은 그대로 둔다: 레드는 라이브·입찰 기능색이라(규칙 1) 끊김을 레드로 강조하면
              신호가 뒤집힌다. 멈추는 것 자체가 "지금 실시간이 아니다"다.
              라벨을 바꾸지 않는 이유 — 방은 여전히 LIVE 상태다. 끊긴 건 내 연결이지 경매가 아니다.
            -->
            <span
              class="bg-red inline-flex items-center gap-[5px] rounded-full px-2.5 py-1 text-[11px] font-bold text-white"
            >
              <span
                class="bg-surface h-1.5 w-1.5 rounded-full"
                :class="live.connectionStatus === 'connected' && 'animate-pulse'"
              />LIVE
              <!-- 점의 움직임은 스크린리더에 안 잡힌다. 상주 리전이 아니라 낭독을 가로채지도 않는다 -->
              <span v-if="live.connectionStatus !== 'connected'" class="sr-only">
                · 실시간 연결이 끊겨 있어요
              </span>
            </span>
            <span
              v-if="sellerName"
              class="text-on-dark rounded-full bg-white/10 px-3 py-[5px] text-xs backdrop-blur-sm"
            >
              판매자 {{ sellerName
              }}<template v-if="viewerCount !== null">
                · 시청 <span class="tabular-nums">{{ viewerCount }}</span
                >명</template
              >
            </span>
            <!-- 판매자 칩 오른쪽 — 와이어프레임이 `.rep`(신고)를 이 자리에 뒀다 -->
            <slot name="header-action" />
          </div>

          <!-- 타이머 칩(상단 중앙) — BID-05 서버 권위, 값 없으면 '--:--'
               강조는 두 겹이다: 60초(`urgent`)는 **색**만, 마지막 5초(`critical`)는 **크기**까지.
               같은 겹으로 묶으면 60초 내내 화면이 소리치게 된다.
               커지는 방향으로 잡은 이유는 자리 때문이다 — `left-1/2 -translate-x-1/2`라 중앙에서
               양옆으로 자라고, `absolute`라 아래 요소를 밀지 않는다. 스테이지 위 z-[6]이라
               경매봉(z-[5])에 가려질 일도 없다.
               5초 구간에서는 「남은 시간」 라벨을 뺀다 — 그 시점에 필요한 건 숫자뿐이고,
               라벨을 남기면 칩이 가로로 길어져 스테이지를 가로지른다. -->
          <div
            class="text-on-dark ease-out-quint absolute top-4 left-1/2 z-[6] flex -translate-x-1/2 items-center rounded-full border whitespace-nowrap shadow-lg backdrop-blur-md transition-all duration-200 motion-reduce:transition-none"
            :class="[
              urgent ? 'border-red/60 bg-ink/80' : 'bg-ink/80 border-white/10',
              critical ? 'gap-0 px-6 py-3' : 'gap-2.5 px-4 py-2 text-[13px]',
            ]"
          >
            <span v-if="!critical" class="text-on-dark-dim">남은 시간</span>
            <span
              class="font-bold tabular-nums"
              :class="[urgent && 'text-red', critical ? 'text-[34px] leading-none' : 'text-[17px]']"
              >{{ display }}</span
            >
          </div>

          <!-- 입찰 폭주 배너 — 타이머 칩 아래에서 **스테이지를 가로지르는 띠**로 깐다.
               레드는 「지금 벌어지는 일」 기능색이라 이 연출이 정확히 그 뜻이다(규칙 1).
               ⚠️ 같은 행을 `LiveStage`의 재연결 배지·자동재생 안내가 쓴다. 재연결과는 조건이
               배타적이라(연결됐을 때만 폭주로 친다) 겹치지 않지만, **자동재생 안내와는 겹칠 수 있다** —
               둘 다 잠깐 뜨는 연출이라 자리를 더 만들지 않고 그대로 둔다. -->
          <Transition
            enter-active-class="transition duration-200"
            leave-active-class="transition duration-200"
            enter-from-class="-translate-y-3 opacity-0"
            leave-to-class="-translate-y-3 opacity-0"
          >
            <div
              v-if="surging"
              class="bg-red absolute inset-x-0 top-14 z-[6] flex items-center justify-center gap-2.5 py-3 text-white shadow-lg"
              role="status"
            >
              <span class="bg-surface h-2 w-2 rounded-full motion-safe:animate-pulse" />
              <span class="text-[17px] font-bold tracking-tight">입찰 폭주 중!</span>
              <span class="hidden text-[13px] opacity-80 sm:inline">지금 경쟁이 몰리고 있어요</span>
            </div>
          </Transition>

          <!-- 입찰·참여 집계(좌하단) — 공개 가능한 값은 건수·참여자 수뿐이다(규칙 6).
               예전에는 `#action` 레일 안에만 있어 스테이지를 보는 동안 눈에 안 들어왔다.
               ponytail: prodbar 본문(하단 16~52px) 위 `bottom-16`에 고정한다 — 좌측에는 우측 컨트롤 같은
               여백 예약이 없어서, 겹치지 않는 유일한 방법이 이 자리다. prodbar 높이가 바뀌면 함께 본다. -->
          <div
            class="text-on-dark bg-ink/70 absolute bottom-16 left-4 z-[6] flex items-center gap-3.5 rounded-2xl border border-white/15 px-4 py-2.5 shadow-lg backdrop-blur-md"
            aria-live="polite"
          >
            <div class="flex flex-col gap-1 leading-none">
              <span class="text-on-dark-dim text-[11px]">입찰</span>
              <span class="text-[17px] font-bold tabular-nums">
                {{ live.bidCount ?? '—'
                }}<span class="text-on-dark-dim ml-0.5 text-[12px]">건</span>
              </span>
            </div>
            <span class="h-7 w-px flex-none bg-white/15" aria-hidden="true" />
            <div class="flex flex-col gap-1 leading-none">
              <span class="text-on-dark-dim text-[11px]">참여</span>
              <span class="text-[17px] font-bold tabular-nums">
                {{ live.participantCount ?? '—'
                }}<span class="text-on-dark-dim ml-0.5 text-[12px]">명</span>
              </span>
            </div>
          </div>

          <!-- 제품 타이틀 바(하단) — 디자인 prodbar: hover 시 slide-in, 모바일은 상시 노출 -->
          <!--
            우측 여백은 **시청 컨트롤 자리 예약**이다(S15P11A704-475). 컨트롤이 코너로 내려와 이 바의
            본문과 같은 줄에 앉는데, 타이틀이 `flex-1` + `truncate`라 예약이 없으면 긴 상품명이 컨트롤
            아래로 파고든다 — 컨트롤은 z-[7]이라 글자 위에 덮인다.
            **두 값인 이유는 컨트롤 배치가 폭에 따라 갈리기 때문이다.**
             · `lg:pr-48`(192px) — lg 이상은 오디오·전체화면이 가로 한 줄이다. 클러스터 최대 폭 156px
               (아이콘 + 슬라이더 펼침) + 오프셋 16px → 172px 필요. 1280px 화면에서 제목 영역이 674px 남는다.
               S15P11A704-476이 「소리 켜기」 라벨(약 52px)을 없애 214px에서 줄었고, 예약도 240 → 192로 줄여
               제목 폭을 되찾았다.
             · `pr-14`(56px) — lg 미만은 오디오가 이 바 위로 올라가 세로로 쌓이므로 전체화면(28px) 몫만
               예약한다. 여기서 가로 한 줄만큼 예약하면 360px 화면에서 제목에 68px만 남는다(썸네일·gap 제외).
            `px-5`를 쓰지 않고 `pl-5 pr-14`로 나눈 이유: px와 pr을 같이 쓰면 어느 쪽이 이기는지 클래스 정렬
            순서에 달려 조용히 뒤집힐 수 있다. 갈라 쓰면 그 의존이 없다.
          -->
          <div
            class="from-ink/90 via-ink/60 text-on-dark absolute inset-x-0 bottom-0 z-[6] flex items-center gap-3 bg-gradient-to-t to-transparent pt-9 pr-14 pb-4 pl-5 transition-all duration-300 motion-reduce:transition-none lg:translate-y-full lg:pr-48 lg:opacity-0 lg:group-hover:translate-y-0 lg:group-hover:opacity-100"
          >
            <div class="min-w-0 flex-1">
              <div class="truncate text-sm leading-tight font-semibold">{{ title }}</div>
              <div v-if="sellerName" class="text-on-dark-dim mt-[3px] text-xs">
                판매자 {{ sellerName }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ── 사이드 레일 (데스크톱 344px 고정) — #action 위 · #chat 아래 ── -->
      <div class="flex w-full flex-none flex-col gap-3 lg:min-h-0 lg:w-[344px]">
        <!-- 입찰↔예측 패널 영역 (FE1) -->
        <div class="flex-none">
          <slot name="action" />
        </div>
        <!-- 채팅 패널 영역 (FE2) — 데스크톱은 남은 높이 전부, 모바일은 고정 높이 -->
        <div class="h-[400px] lg:h-auto lg:min-h-0 lg:flex-1">
          <slot name="chat" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/*
  전체화면일 때의 스테이지 컨테이너 (S15P11A704-433).

  평소 이 박스는 **레일 옆 카드**다 — 둥근 모서리·그림자에 16:9 비율(데스크톱은 부모 높이)로 잡혀 있다.
  전체화면에 들어가면 그 전제가 전부 깨진다: 화면 비율은 기기가 정하고(16:9가 아닐 수 있다),
  카드 모서리는 화면 밖에 있어 의미가 없으며, `aspect-video`가 남아 있으면 세로가 남는 기기에서
  위아래로 잘리거나 여백이 생긴다.

  색·폰트는 건드리지 않으므로 규칙 1과 무관하고(레이아웃뿐), 유틸리티로 못 쓰는 이유는
  Tailwind v4에 `fullscreen` 변형이 없어서다. `:fullscreen`은 유틸리티보다 특이도가 높아
  클래스 순서에 의존하지 않는다.
*/
.stage-frame:fullscreen {
  aspect-ratio: auto;
  height: 100%;
  border-radius: 0;
  box-shadow: none;
}
</style>
