<script setup lang="ts">
// MEM-01 로그인 (FE1) — data-screen="login".
// 이미 계정이 있는 사람을 빠르게 통과시키는 게 전부다. 회원가입은 별도 라우트(`/signup`)다 —
// 슬라이스 3 전까지는 이 화면이 `?mode=signup` 쿼리로 회원가입까지 겸했다.
// 신규(닉네임 null 또는 필수 약관 미동의)면 /onboarding으로, 기존 회원이면 원래 가려던 곳으로 간다.
import KakaoAuthButton from './KakaoAuthButton.vue'
import { useKakaoAuth } from './useKakaoAuth'

const { pending, startKakao } = useKakaoAuth()
</script>

<template>
  <main class="bg-cream flex min-h-screen items-center justify-center px-5 py-10">
    <div class="w-full max-w-95 text-center">
      <div class="text-ink text-[34px] font-bold tracking-tighter">
        Bid<span class="text-terra">Live</span>
      </div>
      <p class="text-dim mt-2.5 text-[13.5px] leading-relaxed">다시 오셨네요</p>

      <KakaoAuthButton label="카카오로 로그인" :pending="pending" @click="startKakao" />

      <p class="text-faint mt-3.5 text-[11.5px] leading-relaxed">
        아직 계정이 없으신가요?
        <RouterLink :to="{ name: 'signup' }" class="text-terra font-semibold underline">
          회원가입
        </RouterLink>
      </p>
    </div>
  </main>
</template>
