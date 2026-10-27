<script setup lang="ts">
// MEM-01 카카오 인증 복귀 지점 — `data-screen`이 없는 **경유 화면**이다 (S15P11A704-389).
//
// 백엔드가 인증을 마치면 여기로 리다이렉트한다 (`KakaoOAuth2AuthenticationSuccessHandler`):
//   성공  /oauth/callback?code=<43자 일회용 코드>
//   실패  /oauth/callback?error=<OAuth2CallbackErrorCode>
//
// 이 화면이 하는 일은 코드를 세션으로 바꾸고 원래 가려던 곳으로 보내는 것뿐이다.
// 와이어프레임에 대응 화면이 없는 이유도 그것 — 사용자가 여기 머무는 시간은 왕복 한 번이다.
//
// ⚠️ **로그인 코드는 1회용이고 TTL이 짧다.** 두 번 교환하면 409 `LOGIN_CODE_ALREADY_USED`다.
//    그래서 `onMounted`에서 딱 한 번만 부르고, 성공하든 실패하든 재시도는 처음부터(=카카오부터) 다시 한다.
//    주소창에 코드가 남아 새로고침으로 재실행되는 것도 막아야 해서, 실패 화면으로 갈 때도 `replace`를 쓴다.
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { exchangeLoginCode } from '@/shared/api/auth'
import { extractApiErrorCode } from '@/shared/lib/apiError'
import { toApiError } from '@/shared/api/errors'
import { useAuthStore } from '@/stores/auth'
import { takeLoginRedirect } from './loginRedirect'
import { callbackErrorMessage, exchangeErrorMessage, isRetryableLoginError } from './oauthErrors'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const errorText = ref('')
const canRetry = ref(true)

function firstQuery(key: string): string | null {
  const raw = route.query[key]
  if (typeof raw === 'string' && raw.length > 0) return raw
  return Array.isArray(raw) && typeof raw[0] === 'string' ? raw[0] : null
}

/** 로그인 성공 후 갈 곳 — 온보딩이 안 끝났으면 목적지 대신 게이트로 보낸다 */
async function goAfterLogin() {
  const target = takeLoginRedirect()
  if (auth.needsOnboarding) {
    await router.replace({ path: '/onboarding', query: { redirect: target } })
    return
  }
  await router.replace(target)
}

onMounted(async () => {
  const failure = firstQuery('error')
  if (failure !== null) {
    // 카카오 인증·회원 생성 단계 실패. 교환까지 가지도 않았다.
    errorText.value = callbackErrorMessage(failure)
    canRetry.value = isRetryableLoginError(failure)
    return
  }

  const code = firstQuery('code')
  if (code === null) {
    // 사용자가 주소를 직접 친 경우 등 — 조용히 로그인으로 돌린다
    await router.replace('/login')
    return
  }

  try {
    const result = await exchangeLoginCode(code)
    await auth.establishSession(result.member)
    await goAfterLogin()
  } catch (error) {
    const apiCode = extractApiErrorCode(error)
    errorText.value = exchangeErrorMessage(apiCode, toApiError(error).message)
    canRetry.value = isRetryableLoginError(apiCode)
  }
})

function backToLogin() {
  void router.replace('/login')
}
</script>

<template>
  <main class="bg-cream flex min-h-screen items-center justify-center px-6">
    <div class="w-full max-w-[380px] text-center">
      <template v-if="errorText === ''">
        <!-- 교환은 네트워크 왕복이다. 빈 화면을 두면 멈춘 것처럼 보인다.
             motion-reduce에서는 회전을 멈춘다 — 진행 중임은 아래 문구가 알려주므로 정보가 사라지지 않는다 -->
        <div
          class="border-line border-t-terra mx-auto size-8 animate-spin rounded-full border-2 motion-reduce:animate-none"
          role="status"
          aria-label="로그인 처리 중"
        ></div>
        <p class="text-dim mt-4 text-[13px]">로그인 중이에요…</p>
      </template>

      <template v-else>
        <p class="text-ink text-[15px] font-semibold" role="alert">{{ errorText }}</p>
        <button
          type="button"
          class="bg-terra text-on-dark hover:bg-terra-d mt-5 rounded-xl px-5 py-3 text-[14px] font-semibold transition"
          @click="backToLogin"
        >
          <!-- 탈퇴 계정처럼 다시 눌러도 같은 결과인 경우엔 "다시 시도"라고 하지 않는다 -->
          {{ canRetry ? '다시 로그인하기' : '로그인 화면으로' }}
        </button>
      </template>
    </div>
  </main>
</template>
