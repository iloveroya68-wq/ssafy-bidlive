<script setup lang="ts">
// 히어로 슬라이드의 남은 시간 칩 (S15P11A704-309).
//
// 슬라이드마다 `endsAt`이 달라 카운트다운이 슬라이드 수만큼 필요한데 `useCountdown`은
// 컴포넌트 스코프에서만 부를 수 있다. 그래서 칩을 작은 컴포넌트로 뺐다 —
// 시계 자체는 모듈 스코프 공유라 슬라이드가 늘어도 인터벌은 하나다.
//
// 오프셋은 목록 응답의 `serverTime`에서 온다(`room.serverOffset`, S15P11A704-457).
// 바로 아래 `EndingSoonCard`와 같은 `MainView`에 있으면서 여기만 단말 시계로 돌면
// 같은 화면의 두 타이머가 서로 다른 기준으로 흐른다.
import { useCountdown } from '@/shared/lib/useCountdown'
import { useRoomStore } from '@/stores/room'

const props = defineProps<{ endsAt?: number }>()

const roomStore = useRoomStore()
const { display } = useCountdown(() => props.endsAt, {
  serverOffset: () => roomStore.serverOffset,
})
</script>

<template>
  <div
    class="bg-ink/70 text-on-dark absolute top-4 right-4 z-[3] rounded-full border border-white/15 px-3.5 py-[7px] text-[12.5px] tabular-nums backdrop-blur-md"
  >
    남은 시간<b class="ml-1.5 text-[15px] font-bold">{{ display }}</b>
  </div>
</template>
