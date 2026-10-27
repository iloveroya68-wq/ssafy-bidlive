<script setup lang="ts">
// 시연 더미 카드를 눌렀을 때 뜨는 안내. `demoRooms.ts`의 안전장치 ②다 —
// 더미 카드는 진짜와 똑같이 생겼고, 눌러 보는 순간 가짜임을 알려 주는 것이 이 컴포넌트뿐이다.
//
// 모달 골격은 `TermsDetailModal`과 같은 모양이다(배경 클릭 닫기 + role="dialog").
const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <div
    class="bg-stage/55 fixed inset-0 z-50 flex items-center justify-center p-5 backdrop-blur-sm"
    role="dialog"
    aria-modal="true"
    aria-label="시연용 더미 데이터 안내"
    @click.self="emit('close')"
  >
    <div class="bg-surface w-full max-w-100 rounded-2xl px-6 py-6 text-center">
      <p class="text-ink text-base font-bold">시연용 더미 데이터입니다</p>
      <p class="text-dim mt-2.5 text-[13px] leading-relaxed">
        화면 구성을 보여주기 위한 예시 카드예요.<br />
        실제 경매방이 아니라 입장할 수 없어요.
      </p>
      <button
        type="button"
        class="bg-ink text-on-dark hover:bg-stage mt-5 w-full rounded-lg px-5 py-2.5 text-[13px] font-semibold transition"
        @click="emit('close')"
      >
        확인
      </button>
    </div>
  </div>
</template>
