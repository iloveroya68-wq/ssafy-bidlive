<script setup lang="ts">
// MEM-02 약관 동의 단계 (온보딩 1/2)
// 명세 근거: 「약관 조회」가 `required`·`version`·`agreed`를 내려준다.
//  - required = true인 약관은 전부 동의해야 다음 단계로 넘어간다(서버도 409로 막는다).
//  - required = false인 약관은 끄고 진행해도 된다.
//  - 버전이 있으므로 각 행에 v{version}을 표시하고, 전문은 모달로 본다.
import { computed, ref } from 'vue'
import type { TermsItem } from '@/shared/api/terms'
import { TERMS_CODE } from '@/shared/api/terms'
import TermsDetailModal from './TermsDetailModal.vue'

/**
 * 동의 목록 한 줄 요약 — 와이어프레임 `onboard`의 `.as`에 해당한다.
 * **API 응답에 없는 화면 전용 문구**라 `code`로 붙인다. 모르는 코드면 요약 없이 제목만 나온다.
 */
const SUMMARY_BY_CODE: Record<string, string> = {
  [TERMS_CODE.SERVICE_TERMS]: '경매 참여, 낙찰 시 거래 의무에 관한 내용',
  [TERMS_CODE.PRIVACY_POLICY]: '카카오 계정 식별자, 닉네임',
  [TERMS_CODE.MARKETING_CONSENT]: '관심 카테고리 경매 오픈 알림, 이벤트 소식',
  POINT_TERMS: '거래 포인트·베팅 포인트의 성격과 소멸 기준',
  LOCATION_CONSENT: '가까운 지역의 직거래 가능 경매 추천',
}

const props = defineProps<{
  terms: TermsItem[]
  /** 이미 동의한 약관 id (재동의 화면에서 기존 상태를 채워 준다) */
  agreedIds?: number[]
  /** 동의 저장 요청 진행 중 — 중복 제출 방지 */
  pending?: boolean
  /** 저장 실패 메시지 (예: 409 `REQUIRED_TERMS_NOT_AGREED`) */
  error?: string | null
}>()

const emit = defineEmits<{ submit: [checked: Record<number, boolean>] }>()

const checked = ref<Record<number, boolean>>(
  Object.fromEntries(
    props.terms.map((t) => [t.termId, props.agreedIds?.includes(t.termId) ?? false]),
  ),
)

const requiredTerms = computed(() => props.terms.filter((t) => t.required))
const optionalTerms = computed(() => props.terms.filter((t) => !t.required))

/** 필수 약관을 전부 켜야 다음 단계로 갈 수 있다. 목록이 비어 있으면(조회 실패) 아예 못 넘어간다 */
const canProceed = computed(
  () => props.terms.length > 0 && requiredTerms.value.every((t) => checked.value[t.termId]),
)
const allChecked = computed(
  () => props.terms.length > 0 && props.terms.every((t) => checked.value[t.termId]),
)

function toggleAll() {
  const next = !allChecked.value
  for (const t of props.terms) checked.value[t.termId] = next
}

function toggle(id: number) {
  checked.value[id] = !checked.value[id]
}

const detail = ref<TermsItem | null>(null)
</script>

<template>
  <div class="text-left">
    <h1 class="text-ink text-[26px] font-bold tracking-tight">약관에 동의해 주세요</h1>
    <p class="text-dim mt-1.5 text-[13px] leading-relaxed">
      필수 약관에 동의해야 서비스를 이용할 수 있어요. 선택 항목은 나중에 마이페이지에서 바꿀 수
      있습니다.
    </p>

    <!-- 전체 동의 -->
    <div class="mt-6">
      <button
        type="button"
        class="border-line flex w-full items-center gap-2.5 border-b pb-3.5 text-left"
        :aria-pressed="allChecked"
        @click="toggleAll"
      >
        <span
          class="flex h-5 w-5 flex-none items-center justify-center rounded-md border-[1.5px] text-xs transition"
          :class="
            allChecked ? 'bg-terra border-terra text-on-dark' : 'border-faint text-transparent'
          "
          aria-hidden="true"
          >✓</span
        >
        <span class="text-ink text-sm font-semibold">약관에 모두 동의합니다</span>
      </button>

      <!-- 필수 → 선택 순서 -->
      <div
        v-for="t in [...requiredTerms, ...optionalTerms]"
        :key="t.termId"
        class="border-line/60 flex items-start gap-2.5 border-b py-3 last:border-b-0"
      >
        <button
          type="button"
          class="flex flex-1 items-start gap-2.5 text-left"
          :aria-pressed="!!checked[t.termId]"
          @click="toggle(t.termId)"
        >
          <span
            class="mt-0.5 flex h-5 w-5 flex-none items-center justify-center rounded-md border-[1.5px] text-xs transition"
            :class="
              checked[t.termId]
                ? 'bg-terra border-terra text-on-dark'
                : 'border-faint text-transparent'
            "
            aria-hidden="true"
            >✓</span
          >
          <span class="min-w-0">
            <span class="text-ink block text-[13px] leading-snug">
              {{ t.title }}
              <span class="text-faint ml-1 text-[11px] tabular-nums">v{{ t.version }}</span>
            </span>
            <span v-if="SUMMARY_BY_CODE[t.code]" class="text-faint mt-0.5 block text-[11.5px]">
              {{ SUMMARY_BY_CODE[t.code] }}
            </span>
          </span>
        </button>
        <button
          type="button"
          class="text-dim hover:text-ink mt-0.5 flex-none text-[11.5px] underline transition"
          @click="detail = t"
        >
          보기
        </button>
      </div>
    </div>

    <p v-if="error" class="text-red mt-4 text-[11.5px]" role="alert">{{ error }}</p>
    <p v-else-if="!canProceed" class="text-faint mt-4 text-[11.5px]">
      필수 약관 {{ requiredTerms.length }}건에 모두 동의해야 다음으로 넘어갈 수 있어요.
    </p>

    <button
      type="button"
      class="bg-terra text-on-dark hover:bg-terra-d mt-5 min-h-12 w-full rounded-xl text-[15px] font-semibold transition active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-45"
      :disabled="!canProceed || pending"
      @click="emit('submit', { ...checked })"
    >
      {{ pending ? '저장 중…' : '동의하고 계속하기' }}
    </button>

    <TermsDetailModal v-if="detail" :terms="detail" @close="detail = null" />
  </div>
</template>
