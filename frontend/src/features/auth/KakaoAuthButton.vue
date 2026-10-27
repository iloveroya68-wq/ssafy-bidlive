<script setup lang="ts">
// MEM-01 카카오 로그인 버튼 — 로그인 화면과 회원가입 화면 두 곳에 나오므로 컴포넌트로 뺐다.
// `social_account`에 CHECK (provider = 'KAKAO')가 걸려 있어 지원 provider는 카카오뿐이다.
// 구글·애플 버튼은 만들지 않는다 (백엔드가 저장할 수 없다).
defineProps<{ label: string; pending?: boolean }>()

const emit = defineEmits<{ click: [] }>()
</script>

<template>
  <button
    type="button"
    class="bg-kakao text-on-kakao mt-7 flex min-h-13 w-full items-center justify-center gap-2.5 rounded-xl text-[15px] font-bold transition hover:brightness-95 active:scale-[0.985] disabled:cursor-not-allowed disabled:opacity-60"
    :disabled="pending"
    @click="emit('click')"
  >
    <svg class="h-4.5 w-4.5 flex-none" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path
        d="M12 3C6.9 3 2.8 6.3 2.8 10.3c0 2.6 1.7 4.8 4.3 6.1l-1 3.7c-.1.3.3.6.6.4l4.4-2.9c.3 0 .6.1.9.1 5.1 0 9.2-3.3 9.2-7.4S17.1 3 12 3Z"
      />
    </svg>
    {{ pending ? '카카오 인증 중…' : label }}
  </button>
</template>
