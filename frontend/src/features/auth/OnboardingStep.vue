<script setup lang="ts">
// MEM 프로필 만들기 단계 (온보딩 2/2)
// `member` 테이블에는 nickname / role / status / 타임스탬프뿐이다 —
// 이메일·프로필 이미지·실명 컬럼이 없으므로 그런 입력을 만들지 않는다 (docs/04 §3.6).
// nickname은 VARCHAR(30) UNIQUE nullable → 길이 30 제한 + 중복(409) 처리가 필요하다.
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { NICKNAME_MAX_LENGTH } from '@/stores/auth'
import { checkNicknameAvailability, type NicknameUnavailableReason } from '@/shared/api/members'

const props = defineProps<{
  /** 스토어가 돌려준 제출 실패 사유 (닉네임 중복 등) */
  error?: string | null
  pending?: boolean
}>()

const emit = defineEmits<{ submit: [nickname: string]; back: [] }>()

const nickname = ref('')

// 이모지·서로게이트 페어를 1자로 세기 위해 스프레드로 센다
const length = computed(() => [...nickname.value.trim()].length)
const valid = computed(() => length.value > 0 && length.value <= NICKNAME_MAX_LENGTH)

/**
 * 제출 실패 문구는 **실패한 그 값에 묶어 둔다.** 스토어의 `onboardingError`는 다음 제출까지
 * 남아 있어서, 그냥 띄우면 사용자가 다른 닉네임을 입력하는 동안에도 옛 오류가 계속 붙어 있다.
 *
 * 기준은 **제출 당시 값**이다. 응답이 올 때의 입력값을 보면, 저장 중에 입력이 바뀐 경우
 * 오류가 엉뚱한 값에 붙는다(입력을 잠가 두긴 하지만 그 잠금에 기대지 않는다).
 */
const submittedNickname = ref<string | null>(null)
const erroredNickname = ref<string | null>(null)
watch(
  () => props.error,
  (message) => {
    erroredNickname.value = message ? submittedNickname.value : null
  },
)
const submitError = computed(() =>
  props.error && erroredNickname.value === nickname.value.trim() ? props.error : null,
)

// ── MEM-02 닉네임 사전 중복 확인 (S15P11A704-432) ──
// 저장 API는 형식 오류와 금칙어를 `INVALID_NICKNAME` 하나로 뭉쳐 보낸다.
// 이 API의 `reason`만 셋을 갈라 주므로, 입력 중에 정확한 사유를 보여줄 수 있는 건 여기뿐이다.
// 어디까지나 편의 기능이라(§개요) 실패해도 제출을 막지 않는다 — 최종 판정은 저장 API가 한다.

const AVAILABILITY_MESSAGE: Record<NicknameUnavailableReason, string> = {
  ALREADY_EXISTS: '이미 사용 중인 닉네임이에요',
  PROHIBITED_WORD: '사용할 수 없는 단어가 들어 있어요',
  INVALID_FORMAT: '한글·영문·숫자만 쓸 수 있어요',
}

const CHECK_DEBOUNCE_MS = 400

type Availability =
  | { state: 'idle' }
  | { state: 'checking' }
  | { state: 'available' }
  | { state: 'unavailable'; reason: NicknameUnavailableReason }

const availability = ref<Availability>({ state: 'idle' })

let debounceTimer: ReturnType<typeof setTimeout> | null = null
/**
 * 세대 번호. **값이 바뀌는 순간** 올린다 — 요청이 출발하는 시점이 아니다.
 * 요청을 보내 놓고 응답을 기다리는 사이에 입력이 바뀌면 그 요청은 이미 남의 값에 대한 답인데,
 * 진입 시점에 번호를 매기면 그 답이 여전히 "최신"이라 새 입력 위에 눌러앉는다
 * (입력칸은 '택림'인데 '준호'의 판정으로 제출이 잠긴다). 빈 칸으로 지운 경우엔
 * 뒤이을 요청조차 없어 스스로 낫지도 않는다.
 */
let latestRequest = 0

function cancelPendingCheck() {
  if (debounceTimer !== null) clearTimeout(debounceTimer)
  debounceTimer = null
}

watch(nickname, () => {
  cancelPendingCheck()
  // 값이 바뀐 순간 이전 판정도, 떠 있는 요청도 전부 무효다.
  const request = ++latestRequest
  availability.value = { state: 'idle' }
  if (!valid.value) return
  const target = nickname.value.trim()
  debounceTimer = setTimeout(() => void runCheck(target, request), CHECK_DEBOUNCE_MS)
})

async function runCheck(target: string, request: number) {
  availability.value = { state: 'checking' }
  try {
    const result = await checkNicknameAvailability(target)
    if (request !== latestRequest) return
    availability.value = result.available
      ? { state: 'available' }
      : { state: 'unavailable', reason: result.reason ?? 'ALREADY_EXISTS' }
  } catch {
    // 사전 확인이 죽었다고 가입을 막을 이유는 없다. 조용히 물러나고 저장 API에 맡긴다.
    if (request === latestRequest) availability.value = { state: 'idle' }
  }
}

onBeforeUnmount(cancelPendingCheck)

/** 사전 확인이 "불가"로 답한 값은 보내지 않는다 — 확인 전·확인 실패는 막지 않는다 */
const blocked = computed(() => availability.value.state === 'unavailable')

/**
 * 안내 한 줄. **노드를 만들고 없애지 않는다** — 라이브 리전은 내용이 바뀔 때 읽히고,
 * 내용과 함께 삽입되면 낭독을 건너뛰는 리더가 있다. 자리는 늘 두고 텍스트만 갈아 끼운다.
 * 제출 실패는 별도 `role="alert"` 리전이 맡으므로 여기서는 비운다(둘이 겹쳐 읽히면 안 된다).
 */
const statusText = computed(() => {
  if (submitError.value) return ''
  switch (availability.value.state) {
    case 'unavailable':
      return AVAILABILITY_MESSAGE[availability.value.reason]
    case 'available':
      return '사용할 수 있는 닉네임이에요'
    case 'checking':
      return '확인 중…'
    default:
      return ''
  }
})

const statusTone = computed(() => {
  if (availability.value.state === 'unavailable') return 'text-red'
  if (availability.value.state === 'available') return 'text-success'
  return 'text-faint'
})

function submit() {
  if (!valid.value || blocked.value || props.pending) return
  const value = nickname.value.trim()
  submittedNickname.value = value
  emit('submit', value)
}
</script>

<template>
  <form class="text-left" @submit.prevent="submit">
    <h1 class="text-ink text-[26px] font-bold tracking-tight">프로필 만들기</h1>
    <p class="text-dim mt-1.5 text-[13px] leading-relaxed">
      카카오 계정에는 없는 정보예요. 처음 한 번만 입력하면 됩니다.
    </p>

    <div class="mt-6">
      <label for="onboarding-nickname" class="text-dim mb-1.5 block text-xs">닉네임</label>
      <div
        class="bg-surface flex items-baseline gap-2.5 rounded-[10px] border px-3 py-2.5 transition focus-within:ring-3"
        :class="
          submitError || blocked
            ? 'border-red focus-within:ring-red/12'
            : 'border-line focus-within:border-slate focus-within:ring-slate/12'
        "
      >
        <input
          id="onboarding-nickname"
          v-model="nickname"
          type="text"
          class="text-ink placeholder:text-faint min-w-0 flex-1 bg-transparent text-base outline-none disabled:cursor-not-allowed"
          :maxlength="NICKNAME_MAX_LENGTH"
          placeholder="닉네임을 입력하세요"
          autocomplete="nickname"
          :disabled="pending"
          :aria-invalid="submitError || blocked ? 'true' : undefined"
          aria-describedby="onboarding-nickname-error onboarding-nickname-status onboarding-nickname-hint"
        />
        <span class="text-dim flex-none text-xs font-semibold tabular-nums">
          {{ length }}/{{ NICKNAME_MAX_LENGTH }}
        </span>
      </div>
      <!--
        두 리전 모두 **항상 DOM에 있고 텍스트만 바뀐다.** v-if로 노드째 만들면
        라이브 리전이 낭독을 건너뛸 수 있다. 빈 문단은 높이를 차지하지 않는다.
        제출 실패가 최우선이다 — 사전 확인이 통과시킨 값도 저장에서 409가 날 수 있다.
      -->
      <p
        id="onboarding-nickname-error"
        role="alert"
        class="text-red text-[11.5px]"
        :class="submitError ? 'mt-1.5' : ''"
      >
        {{ submitError ?? '' }}
      </p>
      <p
        id="onboarding-nickname-status"
        role="status"
        class="text-[11.5px]"
        :class="statusText ? [statusTone, 'mt-1.5'] : ''"
      >
        {{ statusText }}
      </p>
      <p id="onboarding-nickname-hint" class="text-faint mt-1.5 text-[11.5px] leading-relaxed">
        입찰·채팅에 표시돼요. 낙찰 결과에서는 다른 참여자에게 마스킹됩니다. 이미 쓰고 있는 닉네임은
        고를 수 없어요.
      </p>
    </div>

    <div class="mt-5.5 flex gap-2.5">
      <button
        type="button"
        class="border-line text-dim hover:bg-cream bg-surface min-h-12 flex-none rounded-xl border px-5 text-sm font-semibold transition"
        @click="emit('back')"
      >
        이전
      </button>
      <button
        type="submit"
        class="bg-terra text-on-dark hover:bg-terra-d min-h-12 flex-1 rounded-xl text-[15px] font-semibold transition active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-45"
        :disabled="!valid || blocked || pending"
      >
        {{ pending ? '저장 중…' : 'BidLive 시작하기' }}
      </button>
    </div>
  </form>
</template>
