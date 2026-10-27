<script setup lang="ts">
// MEM-01 회원가입 (FE1) — data-screen="signup".
// OAuth라 "가입 폼"이 없다. 서비스를 파는 화면 + 카카오 버튼이 전부다.
// 기존 회원이 눌러도 막지 않는다 — 신규/기존 분기는 서버가 카카오 계정 식별자로 판별한다.
//
// 슬라이스 3에서 `SignupStep.vue`(LoginView가 `?mode=signup`으로 렌더하던 단계)를 이 라우트 화면으로 옮겼다.
import KakaoAuthButton from './KakaoAuthButton.vue'
import { useKakaoAuth } from './useKakaoAuth'

const { pending, startKakao } = useKakaoAuth()

// 이모지는 이미지가 아니라 텍스트라 외부 URL 문제가 없다
const BENEFITS = [
  {
    icon: '🔒',
    title: '아무도 남의 입찰가를 못 봐요',
    desc: '비공개 입찰 · 낙찰자는 2등 입찰가만 지불',
  },
  {
    icon: '🎯',
    title: '입찰 안 해도 참여할 수 있어요',
    desc: '낙찰가를 예측하고 베팅 포인트를 받아가세요',
  },
  { icon: '🎁', title: '가입하면 1,000BP', desc: '바로 예측 베팅에 쓸 수 있어요' },
]
</script>

<template>
  <main class="bg-cream flex min-h-screen items-center justify-center px-5 py-10">
    <div class="w-full max-w-105 text-center">
      <div class="text-ink text-[34px] font-bold tracking-tighter">
        Bid<span class="text-terra">Live</span>
      </div>
      <p class="text-dim mt-2.5 text-[13.5px] leading-relaxed">
        합리적 가치 탐색을 위한<br />실시간 비공개 경매
      </p>

      <ul
        class="border-line bg-surface mt-6 flex flex-col gap-3.5 rounded-2xl border px-5 py-4.5 text-left"
      >
        <li v-for="b in BENEFITS" :key="b.title" class="flex gap-3">
          <span class="text-base leading-6" aria-hidden="true">{{ b.icon }}</span>
          <div>
            <div class="text-ink text-[13.5px] font-semibold">{{ b.title }}</div>
            <div class="text-dim mt-0.5 text-xs">{{ b.desc }}</div>
          </div>
        </li>
      </ul>

      <KakaoAuthButton label="카카오로 3초 만에 시작하기" :pending="pending" @click="startKakao" />

      <p class="text-faint mt-3.5 text-[11.5px] leading-relaxed">
        이미 계정이 있으신가요?
        <RouterLink :to="{ name: 'login' }" class="text-terra font-semibold underline">
          로그인
        </RouterLink>
      </p>
    </div>
  </main>
</template>
