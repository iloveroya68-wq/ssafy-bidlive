import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './app/router'
import { useAuthStore } from './stores/auth'
import './app/styles/main.css'

// Access Token은 메모리에만 있다(S15P11A704-223). 새로고침하면 사라지므로 Refresh 쿠키로
// 세션을 되살려야 하는데, **그 왕복 동안 마운트를 막지 않는다** — 비로그인 첫 방문자까지
// 401 왕복만큼 백지를 보게 된다(느린 회선이면 타임아웃 15초까지). 앱 셸은 바로 띄우고,
// 로그인 여부가 필요한 판정은 라우터 가드가 `restoreSession()`을 기다린다.
const app = createApp(App).use(createPinia())

// 401 → refresh 재시도까지 실패하면 회원 범위를 비운다. 등록이 없으면 세션이 끊겨도
// 화면은 로그인 상태로 남아 이전 회원의 동의 이력·잔액을 계속 보여준다.
useAuthStore().registerAuthFailureHandler()

app.use(router).mount('#app')
