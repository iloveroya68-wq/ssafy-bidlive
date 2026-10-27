<script setup lang="ts">
// ROOM-04 방 수정 (S15P11A704-324).
//
// 개설은 1→2단계 마법사지만 수정은 **단일 폼**이다 — PATCH가 부분 수정이라 한 필드만 고치는 게 기본이고,
// EMPTY_UPDATE_REQUEST를 피하려면 변경분 유무를 한 화면에서 보여줘야 한다 (spec 참조).
// RoomCreateStep1·Step2는 defineModel 기반 순수 폼 섹션이라 **수정 없이** 재사용한다.
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getAuctionRoom,
  updateAuctionRoom,
  type AuctionRoomDetail,
  type UpdateAuctionRoomRequest,
} from '@/shared/api/rooms'
import { API_ERROR_CODE, hasErrorCode, toApiError } from '@/shared/api/errors'
import { useCategoryStore } from '@/stores/category'
import type { ConditionStatus, ProductImageDraft, StartType } from '@/stores/room'
import RoomCreateStep1 from './RoomCreateStep1.vue'
import RoomCreateStep2 from './RoomCreateStep2.vue'
import { useProductImageUpload } from './useProductImageUpload'

const route = useRoute()
const router = useRouter()
const categoryStore = useCategoryStore()
const auctionId = computed(() => Number(route.params.id))
const consolePath = computed(() => `/rooms/${auctionId.value}/console`)

const loading = ref(true)
const loadError = ref('')

// ── 폼 상태 (Step1·Step2가 v-model로 잡는다) ──
const title = ref('')
const description = ref('')
const categoryCode = ref('')
const conditionStatus = ref<ConditionStatus | ''>('')
const images = ref<ProductImageDraft[]>([])
const reservePrice = ref<number | null>(null)
const durationMinutes = ref<number | null>(null)
const startType = ref<StartType>('IMMEDIATE')
const scheduledDate = ref('')
const scheduledTime = ref('')

/** 프리필 직후 스냅샷 — Task 4의 변경분 비교 기준 */
const initial = ref<{
  title: string
  description: string
  imageUrl: string
  reservePrice: number
  durationMinutes: number
  startType: StartType
  scheduledAt: string | null
} | null>(null)

const pad2 = (n: number) => String(n).padStart(2, '0')

/** ISO 시각 → `RoomCreateStep2`의 date/time 입력칸이 쓰는 `YYYY-MM-DD`·`HH:mm` 한 쌍으로 분해한다.
 * `fill()`(최초 프리필)과 I-1의 예약 복귀 복원이 **같은 분해 로직**을 써야 어긋나지 않는다. */
function decomposeScheduledAt(iso: string): { date: string; time: string } {
  const d = new Date(iso)
  return {
    date: `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`,
    time: `${pad2(d.getHours())}:${pad2(d.getMinutes())}`,
  }
}

function fill(detail: AuctionRoomDetail) {
  title.value = detail.product.title
  description.value = detail.product.description
  // 상세 조회가 category를 주지 않는다 — 비워두고 "고르면 변경"으로 안내한다 (spec)
  categoryCode.value = ''
  // conditionStatus는 수정 API 페이로드에 없는 필드라 항상 비운다
  conditionStatus.value = ''
  images.value = detail.product.imageUrl
    ? [{ id: 'current', imageUrl: detail.product.imageUrl, sortOrder: 1, isThumbnail: true }]
    : []
  reservePrice.value = detail.reservePrice
  durationMinutes.value = detail.durationMinutes
  // startType은 응답에 없다. ck_auction_room_schedule(IMMEDIATE면 scheduledAt이 반드시 null)로 추론한다
  startType.value = detail.scheduledAt === null ? 'IMMEDIATE' : 'SCHEDULED'
  if (detail.scheduledAt) {
    const parts = decomposeScheduledAt(detail.scheduledAt)
    scheduledDate.value = parts.date
    scheduledTime.value = parts.time
  }
  initial.value = {
    title: detail.product.title.trim(),
    description: detail.product.description.trim(),
    imageUrl: detail.product.imageUrl,
    reservePrice: detail.reservePrice,
    durationMinutes: detail.durationMinutes,
    startType: startType.value,
    // scheduledDate·scheduledTime은 분 단위까지만 담아, 서버 원본(초 단위)을 그대로 스냅샷하면
    // 아무것도 안 바꿔도 patch의 scheduledAtDate 재구성값과 어긋나 dirty가 true로 뜬다.
    // 같은 필드에서 재구성한 값으로 스냅샷해 비교 기준의 정밀도를 맞춘다.
    scheduledAt: scheduledAtDate.value?.toISOString() ?? null,
  }
}

// I-1: RoomCreateStep2는 IMMEDIATE 선택 시 scheduledDate·scheduledTime을 지운다(무수정 전제,
// selectStartType 참조). 개설 화면은 잃을 값이 없지만 수정 화면에선 그게 서버 프리필이라
// SCHEDULED→IMMEDIATE→SCHEDULED 왕복에 값이 증발한다. 여기서 initial.scheduledAt으로 복원한다.
// 필드가 이미 채워져 있으면(=지워진 적이 없으면) 덮어쓰지 않는다.
watch(startType, (next, prev) => {
  if (next !== 'SCHEDULED' || prev !== 'IMMEDIATE') return
  if (scheduledDate.value || scheduledTime.value) return
  const iso = initial.value?.scheduledAt
  if (!iso) return
  const parts = decomposeScheduledAt(iso)
  scheduledDate.value = parts.date
  scheduledTime.value = parts.time
})

// ── 검증 규칙 (RoomCreateView.vue와 같은 필드·같은 분기·같은 문구를 쓴다 — 같은 필드라 규칙이
//    달라지면 안 된다. 예약 시각만 예외가 하나 있다: I-3, 프리필을 안 바꿨으면 과거여도 통과) ──
const TITLE_MAX = 100

const scheduledAtDate = computed<Date | null>(() => {
  if (startType.value !== 'SCHEDULED') return null
  if (!scheduledDate.value || !scheduledTime.value) return null
  const parsed = new Date(`${scheduledDate.value}T${scheduledTime.value}`)
  return Number.isNaN(parsed.getTime()) ? null : parsed
})

const step1Errors = computed(() => {
  const errors: Partial<Record<'title' | 'description' | 'categoryCode', string>> = {}
  const trimmedTitle = title.value.trim()
  if (trimmedTitle.length === 0) errors.title = '제목을 입력해 주세요.'
  else if (trimmedTitle.length > TITLE_MAX)
    errors.title = `제목은 ${TITLE_MAX}자 이내로 입력해 주세요.`
  // product.description은 NOT NULL — 공백만 입력하는 것도 막는다
  if (description.value.trim().length === 0) errors.description = '상품 설명을 입력해 주세요.'
  return errors
})

const step2Errors = computed(() => {
  const errors: Partial<Record<'reservePrice' | 'durationMinutes' | 'scheduledAt', string>> = {}
  // reserve_price > 0 — RoomCreateView.vue의 규칙·문구를 그대로 옮긴다 (같은 필드라 규칙이 달라지면 안 된다)
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
  // SCHEDULED면 scheduled_at NOT NULL — RoomCreateView.vue와 같은 3분기
  if (startType.value === 'SCHEDULED') {
    if (!scheduledDate.value || !scheduledTime.value) {
      errors.scheduledAt = '예약 시작은 날짜와 시각을 모두 입력해야 해요.'
    } else if (scheduledAtDate.value === null) {
      errors.scheduledAt = '시작 예정 시각을 올바르게 입력해 주세요.'
    } else {
      // I-3: 프리필된 예약 시각은 안내용이고 실제 시작은 판매자 수동이라, "지난 예약 시각의
      // WAITING 방"은 정상 상태다. 사용자가 실제로 시각을 바꿨을 때만(=프리필과 달라졌을 때만)
      // 미래 시각을 요구한다 — 안 바꿨으면 과거여도 통과시켜 다른 필드 저장을 막지 않는다.
      const iso = scheduledAtDate.value.toISOString()
      if (iso !== initial.value?.scheduledAt && scheduledAtDate.value.getTime() <= Date.now()) {
        errors.scheduledAt = '시작 예정 시각은 현재보다 뒤여야 해요.'
      }
    }
  }
  return errors
})

const attempted = ref(false)
const valid = computed(
  () => Object.keys(step1Errors.value).length === 0 && Object.keys(step2Errors.value).length === 0,
)
const shownStep1Errors = computed(() => (attempted.value ? step1Errors.value : {}))
const shownStep2Errors = computed(() => (attempted.value ? step2Errors.value : {}))

// ── 변경분 계산 ──
const thumbnailUrl = computed(
  () => images.value.find((img) => img.isThumbnail)?.imageUrl ?? images.value[0]?.imageUrl ?? '',
)

/** 바뀐 필드만 담은 PATCH 페이로드. 바뀐 게 없으면 null */
const patch = computed<UpdateAuctionRoomRequest | null>(() => {
  const base = initial.value
  if (!base) return null

  const product: NonNullable<UpdateAuctionRoomRequest['product']> = {}
  if (title.value.trim() !== base.title) product.title = title.value.trim()
  if (description.value.trim() !== base.description) product.description = description.value.trim()
  // 프리필이 항상 빈 값이므로 고르기만 하면 변경이다
  if (categoryCode.value) product.category = categoryCode.value
  // 업로드가 안 끝났거나 실패한 대표 이미지는 blob: URL로 남는다 — 그대로 보내면 서버가
  // URL 형식을 검증할 때 사진 실패가 제목·가격 등 다른 필드 저장까지 통째로 막는다(PATCH가 단일 요청).
  // blob:이면 페이로드에서 제외해 서버 값을 그대로 둔다.
  // 다만 그러면 **사진만 조용히 안 바뀐다** — 그래서 `save()`가 `hasPendingImage`로 저장 자체를 막는다.
  if (
    thumbnailUrl.value &&
    thumbnailUrl.value !== base.imageUrl &&
    !thumbnailUrl.value.startsWith('blob:')
  )
    product.imageUrl = thumbnailUrl.value

  const auction: NonNullable<UpdateAuctionRoomRequest['auction']> = {}
  if (reservePrice.value !== null && reservePrice.value !== base.reservePrice)
    auction.reservePrice = reservePrice.value
  if (durationMinutes.value !== null && durationMinutes.value !== base.durationMinutes)
    auction.durationMinutes = durationMinutes.value
  if (startType.value !== base.startType) auction.startType = startType.value
  // SCHEDULED일 때만 시각을 비교·전송한다. IMMEDIATE로 바꾸면 서버가 scheduledAt을 제거한다
  if (startType.value === 'SCHEDULED') {
    const iso = scheduledAtDate.value?.toISOString() ?? null
    if (iso && iso !== base.scheduledAt) auction.scheduledAt = iso
  }

  const result: UpdateAuctionRoomRequest = {}
  if (Object.keys(product).length > 0) result.product = product
  if (Object.keys(auction).length > 0) result.auction = auction
  return Object.keys(result).length > 0 ? result : null
})

const dirty = computed(() => patch.value !== null)

/** 마운트가 끝나기 전에 화면을 떠났는지. 그 경우 뒤늦게 도착한 응답으로 상태를 쓰지 않는다
 * (ConsoleView.vue의 `cancelled` 플래그와 같은 패턴 — 커밋 b8d1f56 참조) */
let cancelled = false

// ── 대표 이미지 업로드 배선 ──
// Step1이 넣은 blob 항목을 업로드 URL로 바꾼다. 개설 화면과 **같은 훅을 공유한다** —
// 함정 셋(재시도 폭주·blob 누수·이탈 후 응답)이 눈에 안 보여서 복사하면 한쪽만 고쳐진다(443).
const {
  uploading: imageUploading,
  error: imageError,
  hasPending: hasPendingImage,
} = useProductImageUpload(images, () => cancelled)

onMounted(async () => {
  // 상세와 나란히 받는다 — 셀렉트 목록은 상세 응답과 무관하다
  void categoryStore.loadCategories()
  try {
    const detail = await getAuctionRoom(auctionId.value)
    if (cancelled) return
    if (detail.status !== 'WAITING') {
      // 서버도 AUCTION_UPDATE_NOT_ALLOWED로 막지만 화면이 먼저 거른다.
      // 조용히 replace만 하면 사용자는 "방 수정" 버튼이 안 먹은 줄 안다 — 쿼리로 사유를 넘겨
      // ConsoleView가 안내 문구를 띄우게 한다 (spec 「라우트·진입·복귀」).
      await router.replace({ path: consolePath.value, query: { notice: 'edit-unavailable' } })
      return
    }
    fill(detail)
  } catch (caught) {
    if (!cancelled) loadError.value = toApiError(caught).message
  } finally {
    if (!cancelled) loading.value = false
  }
})

onUnmounted(() => {
  cancelled = true
})

function cancel() {
  void router.push(consolePath.value)
}

const saving = ref(false)
const saveError = ref('')

async function save() {
  attempted.value = true
  saveError.value = ''
  // I-4: 업로드가 안 끝났거나 **실패한** blob: URL은 patch에서 제외되므로(옳은 방어),
  // 이 상태로 저장하면 "사진 저장 성공"처럼 보이는 에러 없는 실패가 된다.
  // 진행 중일 때만 막으면 실패한 경우가 그대로 새므로 `hasPendingImage`까지 본다(443).
  if (!valid.value || !dirty.value || saving.value || imageUploading.value) return
  if (hasPendingImage.value) {
    saveError.value = '사진을 올리지 못했어요. 사진을 다시 골라 주세요.'
    return
  }

  const body = patch.value
  if (body === null) return

  saving.value = true
  try {
    await updateAuctionRoom(auctionId.value, body)
    // 저장 중 화면을 떠났으면 뒤늦은 push로 사용자를 도로 끌고 오지 않는다 (onMounted와 같은 cancelled 가드)
    if (cancelled) return
    await router.push(consolePath.value)
  } catch (caught) {
    if (cancelled) return
    const apiError = toApiError(caught)
    if (hasErrorCode(apiError, API_ERROR_CODE.AUCTION_UPDATE_NOT_ALLOWED)) {
      saveError.value = '이미 시작된 방은 수정할 수 없어요.'
    } else if (hasErrorCode(apiError, API_ERROR_CODE.NOT_AUCTION_SELLER)) {
      saveError.value = '이 방의 판매자만 수정할 수 있어요.'
    } else if (hasErrorCode(apiError, API_ERROR_CODE.EMPTY_UPDATE_REQUEST)) {
      // 화면이 막고 있어 정상 경로에서는 안 나온다 — 방어용
      saveError.value = '변경한 내용이 없어요.'
    } else {
      saveError.value = apiError.message
    }
  } finally {
    if (!cancelled) saving.value = false
  }
}
</script>

<template>
  <!-- F2: 전역 헤더(--header-h)만큼 뷰포트 기준 높이를 보정한다. RoomCreateView.vue와 같은 폼 컴포넌트를
       쓰는 화면이라 컨테이너 폭·패딩도 맞춘다 (M-1). -->
  <main class="bg-cream min-h-[calc(100dvh-var(--header-h))]">
    <div class="mx-auto max-w-5xl px-6 py-10">
      <h1 class="text-ink text-[30px] font-bold tracking-[-0.028em]">경매 방 수정</h1>
      <p class="text-dim mt-2 text-[13.5px]">시작 전에만 고칠 수 있어요 · 바꾼 항목만 저장돼요</p>

      <p v-if="loading" class="text-dim mt-8 text-sm">불러오는 중…</p>
      <p v-else-if="loadError" class="text-red mt-8 text-sm" role="alert">{{ loadError }}</p>

      <div v-else class="mt-6 flex flex-col gap-5">
        <RoomCreateStep1
          v-model:title="title"
          v-model:description="description"
          v-model:category-code="categoryCode"
          v-model:condition-status="conditionStatus"
          v-model:images="images"
          :categories="categoryStore.categories"
          :errors="shownStep1Errors"
        />
        <p class="text-faint text-[11.5px]">
          사진은 대표 1장만 저장돼요 · 카테고리는 고르면 변경돼요(비워두면 유지)
        </p>
        <p v-if="imageUploading" class="text-dim text-[11.5px]">사진 올리는 중…</p>
        <p v-if="imageError" class="text-red text-[11.5px]" role="alert">{{ imageError }}</p>

        <RoomCreateStep2
          v-model:reserve-price="reservePrice"
          v-model:duration-minutes="durationMinutes"
          v-model:start-type="startType"
          v-model:scheduled-date="scheduledDate"
          v-model:scheduled-time="scheduledTime"
          :errors="shownStep2Errors"
        />

        <div class="flex flex-wrap items-center gap-2.5">
          <button
            type="button"
            class="bg-terra text-on-dark hover:bg-terra-d rounded-xl px-5.5 py-3 text-sm font-semibold transition active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-45"
            :disabled="!dirty || saving || imageUploading"
            @click="save"
          >
            {{ saving ? '저장 중…' : '저장' }}
          </button>
          <button
            type="button"
            class="border-line text-dim rounded-xl border px-5 py-3 text-[13.5px] font-semibold transition"
            @click="cancel"
          >
            취소
          </button>
          <!-- I-4: 업로드 중에는 "변경한 내용이 없어요"를 띄우지 않는다 — 사진만 바꾼 상태에서
               잘못된 안내가 뜨는 부수 증상을 함께 정리한다 -->
          <span v-if="imageUploading" class="text-faint text-[11.5px]">
            사진을 올리는 중이에요 · 끝나면 저장할 수 있어요
          </span>
          <span v-else-if="!dirty" class="text-faint text-[11.5px]">변경한 내용이 없어요</span>
        </div>
        <p v-if="saveError" class="text-red text-[12px]" role="alert">{{ saveError }}</p>
      </div>
    </div>
  </main>
</template>
