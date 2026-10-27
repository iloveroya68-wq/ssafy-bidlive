<script setup lang="ts">
// MEM 온보딩 (FE1) — 카카오가 주지 않는 것(약관 동의·닉네임)만 최초 1회 받는다.
// 라우트를 별도로 둔 이유: `member.nickname`이 nullable이고 `terms`가 버전 관리되므로
// "온보딩 미완료" 상태가 로그인과 무관하게 다시 발생한다(약관 개정 → 재동의).
// /login 안의 단계로 두면 이미 로그인한 회원을 다시 데려올 자리가 없다 (docs/04 §5.1).
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import TermsAgreement from './TermsAgreement.vue'
import OnboardingStep from './OnboardingStep.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const STEPS = ['약관 동의', '프로필'] as const
const step = ref<0 | 1>(0)
const submitting = ref(false)

const redirectTo = computed(() => {
  const raw = route.query.redirect
  return typeof raw === 'string' && raw.startsWith('/') ? raw : '/'
})

const agreedIds = computed(() => auth.agreements.filter((a) => a.isAgreed).map((a) => a.termsId))

onMounted(async () => {
  if (auth.terms.length === 0) await auth.loadTerms()
  // 약관은 이미 다 동의했고 닉네임만 없는 경우(재로그인 등)는 프로필 단계에서 시작.
  // 조회가 실패해 목록이 비면 pendingRequiredTerms도 0건이라, 약관 단계에 머물러 재시도하게 둔다.
  if (auth.isLoggedIn && auth.terms.length > 0 && auth.pendingRequiredTerms.length === 0)
    step.value = 1
})

async function handleTerms(checked: Record<number, boolean>) {
  submitting.value = true
  const ok = await auth.agreeToTerms(checked)
  submitting.value = false
  if (!ok) return
  // 닉네임이 이미 있으면(약관 개정 재동의 케이스) 프로필 단계를 건너뛰고 끝낸다
  if (auth.user?.nickname) {
    router.replace(redirectTo.value)
    return
  }
  step.value = 1
}

async function handleProfile(nickname: string) {
  submitting.value = true
  const result = await auth.completeOnboarding(nickname)
  submitting.value = false
  if (result === 'done') {
    router.replace(redirectTo.value)
    return
  }
  // 닉네임은 저장됐는데 서버가 필수 약관이 남았다고 한 경우(스토어가 목록을 다시 받아 뒀다).
  // 프로필 단계에 붙잡아 두면 사용자가 여기서 할 수 있는 게 없다 — 약관 단계로 되돌린다.
  //
  // `pendingRequiredTerms.length`로 판단하지 않는다. 낡은 로컬 동의가 남아 있으면 그 값이
  // 0이라 되돌아가지 못하고 같은 문구만 반복해서 보게 된다 (`completeOnboarding` 주석 참조).
  // 목록은 방금 새로 받았으므로 약관 화면으로 보내는 것 자체는 언제나 손해가 없다.
  if (result === 'terms-required') step.value = 0
}

function back() {
  step.value = 0
}
</script>

<template>
  <main class="bg-cream flex min-h-screen items-center justify-center px-5 py-10">
    <div class="w-full max-w-105">
      <!-- 단계 표시 -->
      <ol class="mb-6 flex items-center gap-2" aria-label="온보딩 진행 단계">
        <li v-for="(label, i) in STEPS" :key="label" class="flex flex-1 flex-col gap-1.5">
          <span
            class="h-1 rounded-full transition"
            :class="i <= step ? 'bg-terra' : 'bg-line'"
            aria-hidden="true"
          />
          <span
            class="text-[11px] tabular-nums"
            :class="i === step ? 'text-terra font-semibold' : 'text-faint'"
          >
            {{ i + 1 }}. {{ label }}
          </span>
        </li>
      </ol>

      <!-- 약관 조회는 서버 호출이다 — 목록이 오기 전에 동의 버튼을 열어 두지 않는다 -->
      <template v-if="step === 0">
        <p v-if="auth.termsPending" class="text-dim py-10 text-center text-[13px]">
          약관을 불러오는 중이에요…
        </p>
        <div v-else-if="auth.terms.length === 0" class="py-10 text-center">
          <p class="text-dim text-[13px]">{{ auth.termsError ?? '약관을 불러오지 못했어요' }}</p>
          <button
            type="button"
            class="border-line text-ink hover:bg-surface mt-3 rounded-lg border px-4 py-2 text-[13px] font-semibold transition"
            @click="auth.loadTerms()"
          >
            다시 시도
          </button>
        </div>
        <TermsAgreement
          v-else
          :terms="auth.terms"
          :agreed-ids="agreedIds"
          :pending="submitting"
          :error="auth.onboardingError"
          @submit="handleTerms"
        />
      </template>
      <OnboardingStep
        v-else
        :error="auth.onboardingError"
        :pending="submitting"
        @submit="handleProfile"
        @back="back"
      />
    </div>
  </main>
</template>
