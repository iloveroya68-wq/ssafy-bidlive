<script setup lang="ts">
// `/` 세션 분기 (FE1). 와이어프레임의 go('main')이 data-auth="out"이면 landing으로 가는 동작을
// 경로 분리 없이 재현한다 — /landing을 따로 두면 로그인 사용자가 그 링크를 받았을 때 갈 곳이 없다
// (docs/02 「라우트 설계 판단」).
import { useAuthStore } from '@/stores/auth'
import LandingView from './LandingView.vue'
import MainView from './MainView.vue'

const auth = useAuthStore()
</script>

<template>
  <MainView v-if="auth.isLoggedIn" />
  <LandingView v-else />
</template>
