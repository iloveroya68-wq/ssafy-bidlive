<script setup lang="ts">
// ROOM-01 방 개설 2단계 폼 (FE1) — 1단계 상품 정보 → 2단계 경매 조건
// 스키마 근거는 docs/04-백엔드-스키마-메모.md §3.4. 검증은 DB CHECK 제약을 그대로 옮긴 것이다.
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { findProductRejection } from '@/shared/api/ai'
import { useCategoryStore } from '@/stores/category'
import {
  useRoomStore,
  type ConditionStatus,
  type CreateRoomPayload,
  type ProductImageDraft,
  type StartType,
} from '@/stores/room'
import RoomCreateStep1 from './RoomCreateStep1.vue'
import RoomCreateStep2 from './RoomCreateStep2.vue'
import { useProductImageUpload } from './useProductImageUpload'

const route = useRoute()
const router = useRouter()
const roomStore = useRoomStore()
const categoryStore = useCategoryStore()

// 셀렉트를 그리려면 목록이 있어야 한다. 스토어가 한 번만 받고 화면 넷이 나눠 쓴다
onMounted(() => {
  void categoryStore.loadCategories()
})

const TITLE_MAX = 100
/**
 * 사진 장수 상한. **ROOM-01 계약에는 장수 제한이 없다**(그쪽은 대표 한 장만 받는다) —
 * 이 값은 AI-04 검열이 거절하는 지점이라 여기서 먼저 막는다.
 * 근거는 서버 설정 `ai.gms.max-image-count`(`backend/src/main/resources/application.yml:81`,
 * 기본 10)이고, 넘기면 `POST /ai/products/inspect`가 400 `INVALID_INSPECTION_REQUEST`다.
 * AI-04 명세 표는 `imageUrls ≤20`이라고 적혀 있지만 실제로 거절하는 쪽은 이 설정값이다.
 */
const IMAGE_MAX = 10

// ── `?step=1|2` (S15P11A704-361) ──
// 단계를 쿼리에 노출하는 목적은 **뒤로가기를 "← 이전"으로 만드는 것**이다(docs/02 「라우트 설계 판단」).
// 그래서 다른 화면의 딥링크(`?tab=`·`?cur=`)와 달리 단계 이동은 `replace`가 아니라 **`push`**다.
// 필터는 이력을 더럽히면 안 되지만, 마법사 단계는 이력이 곧 UI이기 때문이다.
//
// ⚠️ 1단계 입력이 메모리에만 있어 `?step=2`로 직접 들어오거나 새로고침하면 복원되지 않는다.
//    그래서 쿼리는 "표시"일 뿐이고, 진입 시 **입력이 비어 있으면 1단계로 되돌린다**(같은 문서).
const step = ref<1 | 2>(1)
const step1Attempted = ref(false)
const step2Attempted = ref(false)
/** 2단계 개설 실패 한 줄. 버튼 바로 위 — 방금 누른 그 버튼이 실패한 것이라 여기가 맞다 */
const submitError = ref('')
/**
 * 1단계 AI-04 검열 차단 한 줄. **버튼 옆이 아니라 Step1의 사진 카드 아래에 그린다**
 * (S15P11A704-712) — 사유가 「n번째 사진」을 가리키는데 문구만 화면 밑에 있으면 어디를 고칠지 모른다.
 * 그래서 개설 실패와 상태를 나눠 둔다. 자리가 다르니 한 ref로는 섞인다.
 */
const inspectionError = ref('')
/** AI-04 사전 검열 대기 — 「다음」을 잠근다 */
const inspecting = ref(false)

// ── 1단계: 상품 정보 (product · product_image) ──
const title = ref('')
const description = ref('')
const categoryCode = ref('')
const conditionStatus = ref<ConditionStatus | ''>('')
const images = ref<ProductImageDraft[]>([])

// ── 대표 이미지 업로드 (S15P11A704-443) ──
// Step1은 고른 파일을 `blob:` URL로 넣는다. 명세 ROOM-01의 `product.imageUrl`은
// **미리 업로드한 서버 URL**이어야 하므로(서버가 소유자까지 검증한다) 여기서 바꿔치기한다.
// 수정 화면과 같은 훅이다 — 함정 셋이 눈에 안 보여서 복사하면 한쪽만 고쳐진다.
let leftView = false
onUnmounted(() => {
  leftView = true
})
const {
  uploading: imageUploading,
  error: imageError,
  hasPending: hasPendingImage,
} = useProductImageUpload(images, () => leftView)

// ── 2단계: 경매 조건 (auction_room) ──
const reservePrice = ref<number | null>(null)
const durationMinutes = ref<number | null>(10)
const startType = ref<StartType>('IMMEDIATE')
const scheduledDate = ref('')
const scheduledTime = ref('')

// SCHEDULED일 때만 값을 만든다. IMMEDIATE면 항상 null (ck_auction_room_schedule)
const scheduledAt = computed<Date | null>(() => {
  if (startType.value !== 'SCHEDULED') return null
  if (!scheduledDate.value || !scheduledTime.value) return null
  const parsed = new Date(`${scheduledDate.value}T${scheduledTime.value}`)
  return Number.isNaN(parsed.getTime()) ? null : parsed
})

const step1Errors = computed(() => {
  const errors: Partial<Record<'title' | 'description' | 'categoryCode' | 'images', string>> = {}
  const trimmedTitle = title.value.trim()
  if (trimmedTitle.length === 0) errors.title = '제목을 입력해 주세요.'
  else if (trimmedTitle.length > TITLE_MAX)
    errors.title = `제목은 ${TITLE_MAX}자 이내로 입력해 주세요.`
  // product.description은 NOT NULL — 공백만 입력하는 것도 막는다
  if (description.value.trim().length === 0) errors.description = '상품 설명을 입력해 주세요.'
  if (categoryCode.value === '') errors.categoryCode = '카테고리를 선택해 주세요.'
  // ROOM-01의 `product.imageUrl`은 **옵셔널이 아니다** — 빈 값도 거절된다.
  // 예전에는 한 장도 없으면 `''`가 그대로 나갔고, 목 모드에서는 그 값을 아무도 안 봐서 조용했다.
  if (images.value.length === 0) errors.images = '상품 사진을 한 장 이상 올려 주세요.'
  else if (images.value.length > IMAGE_MAX)
    errors.images = `상품 사진은 ${IMAGE_MAX}장까지 올릴 수 있어요.`
  else if (imageUploading.value) errors.images = '사진을 올리는 중이에요. 잠시만 기다려 주세요.'
  else if (hasPendingImage.value)
    errors.images = imageError.value || '사진을 올리지 못했어요. 사진을 다시 골라 주세요.'
  return errors
})

const step2Errors = computed(() => {
  const errors: Partial<Record<'reservePrice' | 'durationMinutes' | 'scheduledAt', string>> = {}
  // reserve_price > 0
  if (reservePrice.value === null || Number.isNaN(reservePrice.value)) {
    errors.reservePrice = '하한가를 입력해 주세요.'
  } else if (!Number.isInteger(reservePrice.value)) {
    errors.reservePrice = '하한가는 1P 단위 정수로 입력해 주세요.'
  } else if (reservePrice.value <= 0) {
    errors.reservePrice = '하한가는 1P 이상이어야 해요.'
  }
  // duration_minutes > 0
  if (durationMinutes.value === null || Number.isNaN(durationMinutes.value)) {
    errors.durationMinutes = '진행 시간을 입력해 주세요.'
  } else if (!Number.isInteger(durationMinutes.value)) {
    errors.durationMinutes = '진행 시간은 분 단위 정수로 입력해 주세요.'
  } else if (durationMinutes.value <= 0) {
    errors.durationMinutes = '진행 시간은 1분 이상이어야 해요.'
  }
  // SCHEDULED면 scheduled_at NOT NULL
  if (startType.value === 'SCHEDULED') {
    if (!scheduledDate.value || !scheduledTime.value) {
      errors.scheduledAt = '예약 시작은 날짜와 시각을 모두 입력해야 해요.'
    } else if (scheduledAt.value === null) {
      errors.scheduledAt = '시작 예정 시각을 올바르게 입력해 주세요.'
    } else if (scheduledAt.value.getTime() <= Date.now()) {
      errors.scheduledAt = '시작 예정 시각은 현재보다 뒤여야 해요.'
    }
  }
  return errors
})

const step1Valid = computed(() => Object.keys(step1Errors.value).length === 0)
const step2Valid = computed(() => Object.keys(step2Errors.value).length === 0)
const canSubmit = computed(() => step1Valid.value && step2Valid.value && !roomStore.creating)

// 시도하기 전에는 에러를 띄우지 않는다 (빈 폼이 빨갛게 열리는 것 방지)
const shownStep1Errors = computed(() => (step1Attempted.value ? step1Errors.value : {}))
const shownStep2Errors = computed(() => (step2Attempted.value ? step2Errors.value : {}))

/**
 * AI-04 사전 검열을 통과해야 2단계로 넘어간다.
 *
 * 검열은 **여기서만** 한다 — 1단계 검증(`step1Valid`)이 `hasPendingImage`까지 보므로 이 시점의
 * `images`는 전부 서버 URL이고, GMS가 읽을 수 있는 값이다(`blob:`이면 못 읽는다).
 * 차단은 `REJECTED`뿐이고 보류·호출 실패는 조용히 통과한다 — 근거는 `shared/api/ai.ts`.
 *
 * 응답은 **몇 초 뒤에 온다.** 그 사이에 화면을 떠났거나 입력이 바뀌었을 수 있어서, 판정을 쓰기 전에
 * 두 가지를 확인한다 (아래 주석).
 */
function inspectionInput() {
  return {
    title: title.value.trim(),
    description: description.value.trim(),
    imageUrls: images.value.map((img) => img.imageUrl),
  }
}

async function goStep2() {
  step1Attempted.value = true
  if (!step1Valid.value || inspecting.value) return

  inspectionError.value = ''
  inspecting.value = true
  const input = inspectionInput()
  const requested = JSON.stringify(input)
  try {
    const rejection = await findProductRejection(input)
    // ① 이탈 가드 — 「취소」·뒤로가기로 떠난 뒤 도착한 판정으로 push하면 **떠난 화면이 아니라
    //    지금 라우트 기준**이라 엉뚱한 경로에 `?step=2`가 눌어붙고 이력이 더러워진다.
    if (leftView) return
    // ② 판정 스냅샷 대조 — 검열 중에 입력을 고쳤다면 이 판정은 다른 내용에 대한 것이다.
    //    통과도(미검열 내용이 지나간다) 차단도(고친 내용에 옛 사유가 뜬다) 하지 않고 조용히 멈춘다.
    //    사용자는 바꾼 내용으로 「다음」을 다시 누르면 된다.
    if (JSON.stringify(inspectionInput()) !== requested) return
    if (rejection !== null) {
      inspectionError.value = rejection
      return
    }
  } finally {
    inspecting.value = false
  }

  // push — 뒤로가기가 "← 이전"이 되어야 한다
  void router.push({ query: { ...route.query, step: '2' } })
}

/**
 * "← 이전"은 `goStep2`가 쌓은 이력을 **되감는다**(`back`). push로 되돌리면 이력이 소비되지 않고
 * 하나 더 쌓여서, 이 버튼을 누른 뒤 브라우저 뒤로가기를 누르면 2단계로 되돌아간다 —
 * 1↔2를 오갈수록 개설 화면을 벗어나기 어려워진다. 이 화면이 고치려던 어긋남이 방향만 바뀌어 되살아나는 셈이다.
 *
 * 되감을 이력이 없으면(`?step=2` 직접 진입 등) `back()`이 앱 밖으로 나가버리므로 쿼리만 정리한다.
 * 그 경로는 아래 watch 가드가 이미 1단계로 replace해서 이 함수까지 오지 않지만, 방어로 남긴다.
 */
function goStep1() {
  // 개설 실패 문구는 버튼 위 한 자리를 단계와 무관하게 쓴다 — 들고 내려가면 1단계 「다음」 위에 남는다
  submitError.value = ''
  const previous = window.history.state?.back
  if (typeof previous === 'string' && previous.startsWith('/rooms/new')) {
    router.back()
    return
  }
  void router.replace({ query: { ...route.query, step: '1' } })
}

/** 쿼리에서 읽은 단계. 알아볼 수 없으면 1단계 */
function readStepFromQuery(): 1 | 2 {
  return route.query.step === '2' ? 2 : 1
}

/**
 * 쿼리 → 화면. 뒤로가기·직접 진입·새로고침이 전부 여기로 들어온다.
 *
 * 2단계를 요구받았는데 1단계 입력이 비어 있으면 되돌린다 — 그 입력은 메모리에만 있어서
 * 새로고침·공유로는 복원되지 않는다. URL도 함께 정리해야 새로고침 때마다 같은 어긋남이 반복되지 않는다.
 */
watch(
  () => route.query.step,
  () => {
    const requested = readStepFromQuery()
    if (requested === 2 && !step1Valid.value) {
      step.value = 1
      void router.replace({ query: { ...route.query, step: '1' } })
      return
    }
    step.value = requested
  },
  { immediate: true },
)

async function submit() {
  step1Attempted.value = true
  step2Attempted.value = true
  submitError.value = ''
  if (!step1Valid.value) {
    // 화면만 되돌리면 URL이 `?step=2`로 남아 새로고침 시 다시 어긋난다 — 쿼리를 통해 되돌린다
    void router.replace({ query: { ...route.query, step: '1' } })
    return
  }
  if (!step2Valid.value || roomStore.creating) return

  const payload: CreateRoomPayload = {
    product: {
      title: title.value.trim(),
      description: description.value.trim(),
      categoryCode: categoryCode.value,
      conditionStatus: conditionStatus.value === '' ? null : conditionStatus.value,
      images: images.value,
    },
    room: {
      reservePrice: reservePrice.value as number,
      durationMinutes: durationMinutes.value as number,
      startType: startType.value,
      scheduledAt: scheduledAt.value ? scheduledAt.value.toISOString() : null,
    },
  }

  try {
    const result = await roomStore.createRoom(payload)
    /**
     * **시작 방식과 무관하게 콘솔로 보낸다.**
     *
     * 방금 방을 만든 사람은 판매자다. `/live`·`/waiting`은 시청자 화면이고,
     * 방송 시작(ROOM-05)·송출·질문 큐가 있는 판매자 화면은 콘솔뿐이다.
     *
     * 개설 직후 상태는 **시작 방식과 무관하게 `WAITING`**이다(ROOM-01 §검증 규칙) —
     * "판매자가 방송 시작 API를 호출하기 전에는 입찰·예측을 받을 수 없다".
     * 예전에는 `IMMEDIATE`면 곧장 `/live`로 보냈는데, 스토어가 `LIVE`를 지어내고 있어서
     * 화면상 맞아 보였을 뿐이다. 계약대로면 그 자리에 아직 아무것도 없다.
     */
    await router.push(`/rooms/${result.roomId}/console`)
  } catch {
    submitError.value = '방 개설에 실패했어요. 잠시 후 다시 시도해 주세요.'
  }
}
</script>

<template>
  <!-- F2: 전역 헤더(--header-h)만큼 뷰포트 기준 높이를 보정한다 -->
  <main class="bg-cream min-h-[calc(100dvh-var(--header-h))]">
    <div class="mx-auto max-w-5xl px-6 py-10">
      <h1 class="text-ink text-[30px] font-bold tracking-tight">경매 방 개설</h1>

      <!-- 단계 표시 -->
      <ol class="mt-4.5 mb-6 flex items-center gap-2.5">
        <li class="flex items-center gap-2">
          <span
            class="flex h-6 w-6 items-center justify-center rounded-full text-[13px] font-semibold"
            :class="step === 1 ? 'bg-terra text-on-dark' : 'bg-line text-faint'"
          >
            {{ step === 1 ? '1' : '✓' }}
          </span>
          <span class="text-[14px]" :class="step === 1 ? 'text-ink font-semibold' : 'text-faint'">
            상품 정보
          </span>
        </li>
        <li class="bg-line h-px w-10" aria-hidden="true" />
        <li class="flex items-center gap-2">
          <span
            class="flex h-6 w-6 items-center justify-center rounded-full text-[13px] font-semibold"
            :class="step === 2 ? 'bg-terra text-on-dark' : 'bg-line text-faint'"
          >
            2
          </span>
          <span class="text-[14px]" :class="step === 2 ? 'text-ink font-semibold' : 'text-faint'">
            경매 조건
          </span>
        </li>
      </ol>

      <RoomCreateStep1
        v-show="step === 1"
        v-model:title="title"
        v-model:description="description"
        v-model:category-code="categoryCode"
        v-model:condition-status="conditionStatus"
        v-model:images="images"
        :categories="categoryStore.categories"
        :errors="shownStep1Errors"
        :inspection-error="inspectionError"
      />
      <p v-if="step === 1 && imageUploading" class="text-dim mt-2 text-[11.5px]">사진 올리는 중…</p>
      <p v-else-if="step === 1 && imageError" class="text-red mt-2 text-[11.5px]" role="alert">
        {{ imageError }}
      </p>

      <RoomCreateStep2
        v-if="step === 2"
        v-model:reserve-price="reservePrice"
        v-model:duration-minutes="durationMinutes"
        v-model:start-type="startType"
        v-model:scheduled-date="scheduledDate"
        v-model:scheduled-time="scheduledTime"
        :errors="shownStep2Errors"
      />

      <p v-if="submitError" class="text-red mt-4 text-[13px]" role="alert">{{ submitError }}</p>

      <!-- 단계 이동 · 제출 -->
      <div class="mt-6 flex items-center gap-2.5">
        <template v-if="step === 1">
          <!-- 취소는 직전 화면인 방 목록으로. `/`는 이제 랜딩/홈이라 리터럴 경로를 쓰면 홈으로 샌다 —
               다른 이탈 지점(LiveView·ResultView·NoSaleView·WaitingRoom)과 같이 라우트 이름으로 건다. -->
          <RouterLink
            :to="{ name: 'room-list' }"
            class="border-line text-dim hover:bg-cream bg-surface rounded-full border px-5.5 py-3 text-[14px] font-medium transition"
          >
            취소
          </RouterLink>
          <div class="flex-1" />
          <button
            type="button"
            class="bg-terra text-on-dark hover:bg-terra-d rounded-full px-6.5 py-3 text-[14px] font-semibold transition disabled:cursor-not-allowed disabled:opacity-45"
            :disabled="(step1Attempted && !step1Valid) || inspecting"
            @click="goStep2"
          >
            {{ inspecting ? '상품 확인 중…' : '다음 · 경매 조건 →' }}
          </button>
        </template>
        <template v-else>
          <button
            type="button"
            class="border-line text-dim hover:bg-cream bg-surface rounded-full border px-5.5 py-3 text-[14px] font-medium transition"
            @click="goStep1"
          >
            ← 이전
          </button>
          <div class="flex-1" />
          <button
            type="button"
            class="bg-terra text-on-dark hover:bg-terra-d rounded-full px-6.5 py-3 text-[14px] font-semibold transition disabled:cursor-not-allowed disabled:opacity-45"
            :disabled="!canSubmit"
            @click="submit"
          >
            <!-- `IMMEDIATE`가 「방송 시작하기」였다 (S15P11A704-684). 이 버튼은 방을 만들 뿐이라
                 누른 뒤 콘솔에서 「지금 시작하기」를 또 눌러야 방송·카운트다운이 시작된다.
                 와이어프레임의 `create2` 라벨과 달라진 지점이다 — 그쪽은 시연용이라
                 이 버튼이 곧 방송 시작인 것처럼 구매자 `live`로 보낸다(docs/02 「라우트 설계 판단」). -->
            {{
              roomStore.creating
                ? '개설 중…'
                : startType === 'IMMEDIATE'
                  ? '방송 개설하기'
                  : '대기방 열기'
            }}
          </button>
        </template>
      </div>
    </div>
  </main>
</template>
