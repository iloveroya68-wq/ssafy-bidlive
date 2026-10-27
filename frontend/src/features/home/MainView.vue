<script setup lang="ts">
// ROOM-02·ROOM-08 홈(main) — 로그인 사용자의 `/`. 와이어프레임 `data-screen="main"` 1:1 구현
// (S15P11A704-309, 화면 틀 슬라이스 2).
//
// 3블록이다: 예측 수익률 티커 → "지금 열려 있는 라이브" 히어로 캐러셀 → 마감 임박 컬럼.
// 히어로·마감 임박은 `room` 스토어(ROOM-02 실 목록)에서 파생한다 — `./homeFeed` 참조.
// (2026-07-31까지 히어로만 목이었다. 방 id 1~4를 대조해서 실 서버에서 비었다 — 507.)
//
// 정본 대비 이탈은 `docs/superpowers/specs/2026-07-25-main-화면-design.md`에 근거를 남겼다.
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useRoomStore, type AuctionRoomSummary } from '@/stores/room'
import { useCategoryStore } from '@/stores/category'
import { isDemoRoom, withDemoRooms } from '@/features/demo/demoRooms'
import DemoRoomNotice from '@/features/demo/DemoRoomNotice.vue'
import { endingSoon as pickEndingSoon, heroSlides as pickHeroSlides } from './homeFeed'
import HomeTicker from './HomeTicker.vue'
import LiveHeroCarousel from './LiveHeroCarousel.vue'
import EndingSoonCard from './EndingSoonCard.vue'

const router = useRouter()
const room = useRoomStore()
const categoryStore = useCategoryStore()

// 두 블록 모두 **`room` 스토어를 단일 소스로** 파생한다. 파생 함수가 스토어를 직접 읽으면
// 화면이 데이터를 두 곳에서 가져오게 되므로, 여기서 쥐고 인자로 넘긴다.
// 히어로 칩은 카테고리 표시명이다 — `RoomCard`가 배지에 쓰는 것과 같은 값(507).
// 시연 더미는 **파생 입력에** 붙인다 — 히어로·마감 임박이 같은 목록을 앞에서 자르므로,
// 뒤에 붙여 두면 실 방이 4개를 채우는 순간 자동으로 밀려난다(진짜가 항상 앞이다).
const feedRooms = computed(() => withDemoRooms(room.rooms, 'LIVE'))
const heroSlides = computed(() => pickHeroSlides(feedRooms.value, categoryStore.nameByCode))
const endingSoon = computed(() => pickEndingSoon(feedRooms.value, 4))

/**
 * 아직 진행 중 방을 못 받은 상태. 목록 화면과 스토어를 공유하므로
 * `/rooms?state=ended`에서 넘어오면 **비어 있는 채로 잠깐 그려진다** — 그때 두 블록이
 * 통째로 사라져 레이아웃이 튄다. 빈 목록과 "아직 안 왔다"를 구분해서 안내한다.
 */
const pending = computed(() => room.loading && room.rooms.length === 0)

onMounted(() => {
  // 홈이 쓰는 건 **진행 중 방뿐**이다(히어로·마감 임박 둘 다). 서버에 그렇게 묻는다 —
  // 예전엔 전체를 받아 화면에서 걸렀는데, 목록이 커서 페이지가 되면서 그 방식이 깨졌다.
  // `/rooms`와 같은 스토어라 목록 화면을 다녀오면 조건이 바뀌어 있다 → 매번 다시 건다.
  void room.loadRooms({ status: 'LIVE' })
  // 히어로 칩이 쓰는 카테고리 표시명. 안 부르면 `nameByCode`가 코드(`ELECTRONICS`)를 그대로 낸다.
  // 마스터 데이터라 스토어가 한 번만 받고 중복 호출은 single-flight가 막는다.
  void categoryStore.loadCategories()
})

/** 시연 더미 카드를 눌렀을 때 뜨는 안내 */
const demoNoticeOpen = ref(false)

function openRoom(target: AuctionRoomSummary) {
  // 더미는 실제 방이 아니라 입장시키지 않는다 — 음수 id로 라우팅하면 그 화면이 404를 받는다
  if (isDemoRoom(target)) {
    demoNoticeOpen.value = true
    return
  }
  void router.push({ name: 'live', params: { id: String(target.auctionId) } })
}
</script>

<template>
  <main class="mx-auto w-full max-w-[1180px] px-6 pt-6 pb-14">
    <HomeTicker />

    <div class="mt-6 mb-4 flex items-end justify-between gap-4">
      <div>
        <p class="text-dim text-[13px]">합리적 가치 탐색을 위한 실시간 비공개 경매</p>
        <!-- 원본 `.serif`는 서체가 아니라 굵기 700 + 자간 -.028em이다 (S15P11A704-305에서 확인) -->
        <h1 class="text-ink mt-1.5 text-[34px] leading-tight font-bold tracking-[-0.028em]">
          지금 열려 있는 라이브
        </h1>
      </div>
      <RouterLink
        :to="{ name: 'room-create' }"
        class="bg-terra hover:bg-terra-d shrink-0 rounded-xl px-[22px] py-3 text-[14px] font-semibold text-white transition"
      >
        ＋ 경매 방 개설
      </RouterLink>
    </div>

    <!--
      heroRow — 원본은 `1fr 332px` 2열. `lg` 미만에서는 1열로 쌓는다.
      "전체 보기"는 원본이 배너 우상단 바깥에 absolute로 두는데 그 좌표(`right:362px`)가
      332px 컬럼을 전제하므로, 1열에서는 흐름 배치로 되돌린다.
    -->
    <div class="relative lg:grid lg:grid-cols-[1fr_332px] lg:gap-4">
      <div
        class="mb-2 flex justify-end lg:absolute lg:-top-[21px] lg:right-[362px] lg:z-[2] lg:mb-0"
      >
        <RouterLink
          :to="{ name: 'room-list' }"
          class="text-dim hover:text-ink text-[12px] transition"
        >
          전체 보기 →
        </RouterLink>
      </div>

      <!--
        진행 중 방이 0개일 때도 안내를 낸다. 캐러셀은 빈 배열이어도 `bg-stage` 상자를 그리므로
        그냥 두면 **검은 상자만 남아** 로딩 중으로 읽힌다 (507에서 실 서버가 그 상태였다).
      -->
      <div
        v-if="pending || heroSlides.length === 0"
        class="border-line bg-surface text-faint flex min-h-75 items-center justify-center rounded-2xl border text-[13px]"
      >
        {{ pending ? '라이브를 불러오는 중이에요' : '지금 열려 있는 라이브가 없어요' }}
      </div>
      <LiveHeroCarousel v-else :slides="heroSlides" />

      <section class="mt-4 flex min-h-0 flex-col gap-2.5 lg:mt-0" aria-label="마감 임박">
        <h2 class="text-ink flex items-center gap-2 text-[14px] font-bold tracking-[-0.01em]">
          <span class="bg-red h-1.5 w-1.5 rounded-full" aria-hidden="true" />
          마감 임박
        </h2>
        <p v-if="pending" class="text-faint text-[12.5px]">불러오는 중이에요</p>
        <!--
          히어로와 같은 목록을 쓰므로 **둘은 항상 같이 빈다.** 여기에만 빈 상태가 없으면
          제목 아래가 통째로 비어 고장으로 읽힌다 — 히어로 쪽과 같은 이유다(507 리뷰).
        -->
        <p v-else-if="endingSoon.length === 0" class="text-faint text-[12.5px]">
          마감을 앞둔 방이 없어요
        </p>
        <template v-else>
          <EndingSoonCard
            v-for="target in endingSoon"
            :key="target.auctionId"
            :room="target"
            @select="openRoom"
          />
        </template>
      </section>
    </div>

    <DemoRoomNotice v-if="demoNoticeOpen" @close="demoNoticeOpen = false" />
  </main>
</template>
