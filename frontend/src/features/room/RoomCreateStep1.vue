<script setup lang="ts">
// ROOM-01 방 개설 1단계 — 상품 정보 (product · product_image · product_category)
// 스키마 제약(docs/04 §3.4):
//  - title VARCHAR(100), description TEXT NOT NULL(공백만 불가)
//  - condition_status는 nullable → "선택 안 함"이 유효하다
//  - product_image: sort_order 1부터 연속, 대표 이미지는 최대 1장(라디오)
//  - moderation_status는 서버가 정하므로 폼에 노출하지 않는다
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import type { AuctionCategory } from '@/stores/category'
import type { ConditionStatus, ProductImageDraft } from '@/stores/room'

const title = defineModel<string>('title', { required: true })
const description = defineModel<string>('description', { required: true })
const categoryCode = defineModel<string>('categoryCode', { required: true })
const conditionStatus = defineModel<ConditionStatus | ''>('conditionStatus', { required: true })
const images = defineModel<ProductImageDraft[]>('images', { required: true })

const props = defineProps<{
  categories: AuctionCategory[]
  errors: Partial<Record<'title' | 'description' | 'categoryCode' | 'images', string>>
  /**
   * AI-04 사전 검열이 차단한 사유. 사유는 대개 「n번째 사진」을 가리키므로
   * 버튼 옆이 아니라 **사진 카드 바로 아래**에 붙인다 — 고쳐야 할 것 옆에 있어야 읽힌다.
   */
  inspectionError?: string
}>()

const inspectionAlert = ref<HTMLElement | null>(null)

// 「다음」은 화면 맨 아래에 있고 이 문구는 위에 뜬다 — 스크롤해 주지 않으면
// 누른 사람 눈에는 아무 일도 일어나지 않은 것처럼 보인다.
watch(
  () => props.inspectionError,
  async (message) => {
    if (!message) return
    await nextTick()
    inspectionAlert.value?.scrollIntoView({
      block: 'center',
      // motion-reduce 사용자에게는 순간 이동. 다른 화면의 `motion-reduce:` 클래스와 같은 기준이다
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
    })
  },
)

const TITLE_MAX = 100
/**
 * `description`은 DB가 TEXT고 ROOM-01 계약에도 길이 제한이 없다 — 이 값은 **AI-04 검열 상한**이다
 * (`ai-04-post-ai-products-inspect-*.md` Request Body: `description String(≤5000)`).
 * 넘으면 방 개설은 되는데 검열만 400으로 조용히 fail-open돼서, 입력 단계에서 잘라 둔다.
 */
const DESCRIPTION_MAX = 5000

const CONDITION_OPTIONS: { value: ConditionStatus; label: string }[] = [
  { value: 'NEW', label: '새 상품' },
  { value: 'LIKE_NEW', label: '거의 새것' },
  { value: 'USED', label: '사용감 있음' },
  { value: 'DAMAGED', label: '하자 있음' },
]

const fileInput = ref<HTMLInputElement | null>(null)
let idSeq = 0

// sort_order를 1부터 다시 매기고, 대표 이미지가 없으면 첫 장을 대표로 둔다 (대표는 항상 최대 1장).
function resequence(list: ProductImageDraft[]): ProductImageDraft[] {
  const hasThumb = list.some((img) => img.isThumbnail)
  return list.map((img, i) => ({
    ...img,
    sortOrder: i + 1,
    isThumbnail: hasThumb ? img.isThumbnail : i === 0,
  }))
}

function onFilesSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (files.length === 0) return

  const added: ProductImageDraft[] = files.map((file) => {
    idSeq += 1
    return {
      id: `img-${idSeq}`,
      // **일부러 blob으로 둔다.** 업로드는 이 컴포넌트 밖의 `useProductImageUpload`가 맡아
      // 서버 URL로 바꿔치기한다(S15P11A704-443) — Step1은 미리보기만 책임진다.
      // 여기서 직접 올리면 개설·수정 두 화면이 각자 업로드 로직을 갖게 된다.
      imageUrl: URL.createObjectURL(file),
      sortOrder: 0,
      isThumbnail: false,
    }
  })
  images.value = resequence([...images.value, ...added])
}

function removeImage(id: string) {
  const target = images.value.find((img) => img.id === id)
  if (target) URL.revokeObjectURL(target.imageUrl)
  images.value = resequence(images.value.filter((img) => img.id !== id))
}

function moveImage(id: string, delta: number) {
  const list = [...images.value]
  const from = list.findIndex((img) => img.id === id)
  const to = from + delta
  if (from < 0 || to < 0 || to >= list.length) return
  const [moved] = list.splice(from, 1)
  list.splice(to, 0, moved)
  images.value = resequence(list)
}

// uq_product_single_thumbnail — 대표는 라디오. 여러 장 동시 선택 불가
function setThumbnail(id: string) {
  images.value = images.value.map((img) => ({ ...img, isThumbnail: img.id === id }))
}

onBeforeUnmount(() => {
  images.value.forEach((img) => URL.revokeObjectURL(img.imageUrl))
})
</script>

<template>
  <div class="grid gap-4 lg:grid-cols-2">
    <div class="flex flex-col gap-3.5">
      <!-- 상품 사진 -->
      <section class="border-line bg-surface rounded-2xl border p-5">
        <div class="text-ink text-[13px] font-semibold">상품 사진</div>
        <p class="text-dim mt-1 text-[12px]">
          대표 이미지는 한 장만 고를 수 있어요. 순서는 위에서부터 1번입니다.
        </p>

        <button
          type="button"
          class="border-line text-faint hover:border-terra hover:text-terra mt-3 flex h-37 w-full flex-col items-center justify-center gap-2 rounded-xl border-[1.5px] border-dashed transition"
          @click="fileInput?.click()"
        >
          <svg
            class="h-7 w-7"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.6"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <rect x="3" y="3" width="18" height="18" rx="2.5" />
            <circle cx="8.5" cy="8.5" r="1.6" />
            <path d="M21 15l-5-5L5 21" />
          </svg>
          <span class="text-[12.5px]">클릭해 사진을 업로드하세요</span>
        </button>
        <input
          ref="fileInput"
          type="file"
          accept="image/*"
          multiple
          class="hidden"
          @change="onFilesSelected"
        />

        <p v-if="errors.images" class="text-red mt-2 text-[12px]" role="alert">
          {{ errors.images }}
        </p>

        <ul v-if="images.length > 0" class="mt-3 flex flex-col gap-2">
          <li
            v-for="img in images"
            :key="img.id"
            class="border-line bg-surface flex items-center gap-3 rounded-xl border p-2.5"
            :class="img.isThumbnail && 'border-terra bg-terra-tint'"
          >
            <img
              :src="img.imageUrl"
              alt=""
              class="bg-line h-12 w-12 flex-none rounded-lg object-cover"
            />
            <label class="flex flex-1 cursor-pointer items-center gap-2 text-[12.5px]">
              <input
                type="radio"
                name="thumbnail"
                class="accent-terra"
                :checked="img.isThumbnail"
                @change="setThumbnail(img.id)"
              />
              <span :class="img.isThumbnail ? 'text-terra font-semibold' : 'text-dim'">
                대표 이미지
              </span>
              <span class="text-faint tabular-nums">· {{ img.sortOrder }}번</span>
            </label>
            <div class="flex items-center gap-1">
              <button
                type="button"
                class="border-line text-dim hover:bg-cream disabled:text-faint rounded-lg border px-2 py-1 text-[12px] disabled:opacity-50"
                :disabled="img.sortOrder === 1"
                aria-label="위로 이동"
                @click="moveImage(img.id, -1)"
              >
                ↑
              </button>
              <button
                type="button"
                class="border-line text-dim hover:bg-cream disabled:text-faint rounded-lg border px-2 py-1 text-[12px] disabled:opacity-50"
                :disabled="img.sortOrder === images.length"
                aria-label="아래로 이동"
                @click="moveImage(img.id, 1)"
              >
                ↓
              </button>
              <button
                type="button"
                class="border-line text-red hover:bg-terra-tint rounded-lg border px-2 py-1 text-[12px]"
                @click="removeImage(img.id)"
              >
                삭제
              </button>
            </div>
          </li>
        </ul>
      </section>

      <p
        v-if="inspectionError"
        ref="inspectionAlert"
        class="text-red text-[12px] leading-relaxed"
        role="alert"
      >
        {{ inspectionError }}
      </p>
    </div>

    <div class="flex flex-col gap-3.5">
      <!-- 제목 · 설명 · 카테고리 · 상태 -->
      <section class="border-line bg-surface flex flex-col gap-4 rounded-2xl border p-5">
        <div>
          <label for="cr-title" class="text-ink text-[13px] font-semibold">
            제목 <span class="text-terra">*</span>
          </label>
          <input
            id="cr-title"
            v-model="title"
            type="text"
            :maxlength="TITLE_MAX"
            placeholder="예) 기계식 키보드 커스텀 풀빌드 (저소음 적축)"
            class="border-line text-ink placeholder:text-faint focus:border-terra mt-2 w-full rounded-xl border px-3.5 py-2.5 text-[14px] transition outline-none"
            :class="errors.title && 'border-red'"
          />
          <div class="mt-1 flex items-start justify-between gap-2">
            <p v-if="errors.title" class="text-red text-[12px]">{{ errors.title }}</p>
            <span class="text-faint ml-auto text-[11.5px] tabular-nums">
              {{ title.length }}/{{ TITLE_MAX }}
            </span>
          </div>
        </div>

        <div>
          <label for="cr-desc" class="text-ink text-[13px] font-semibold">
            설명 <span class="text-terra">*</span>
          </label>
          <textarea
            id="cr-desc"
            v-model="description"
            rows="5"
            :maxlength="DESCRIPTION_MAX"
            placeholder="상태·구성품·사용 기간 등 구매자가 궁금해할 내용을 적어주세요."
            class="border-line text-ink placeholder:text-faint focus:border-terra mt-2 w-full resize-y rounded-xl border px-3.5 py-2.5 text-[14px] leading-relaxed transition outline-none"
            :class="errors.description && 'border-red'"
          />
          <p v-if="errors.description" class="text-red mt-1 text-[12px]">
            {{ errors.description }}
          </p>
        </div>

        <div>
          <label for="cr-category" class="text-ink text-[13px] font-semibold">
            카테고리 <span class="text-terra">*</span>
          </label>
          <select
            id="cr-category"
            v-model="categoryCode"
            class="border-line text-ink focus:border-terra bg-surface mt-2 w-full rounded-xl border px-3.5 py-2.5 text-[14px] transition outline-none"
            :class="errors.categoryCode && 'border-red'"
          >
            <option value="">카테고리를 선택하세요</option>
            <option v-for="cat in categories" :key="cat.code" :value="cat.code">
              {{ cat.name }}
            </option>
          </select>
          <p v-if="errors.categoryCode" class="text-red mt-1 text-[12px]">
            {{ errors.categoryCode }}
          </p>
        </div>

        <div>
          <div class="text-ink text-[13px] font-semibold">상품 상태</div>
          <p class="text-dim mt-1 text-[12px]">선택하지 않아도 등록할 수 있어요.</p>
          <div class="mt-2 flex flex-wrap gap-2">
            <button
              type="button"
              class="rounded-full px-3.5 py-2 text-[12.5px] font-medium transition"
              :class="
                conditionStatus === ''
                  ? 'bg-ink text-cream'
                  : 'border-line text-dim hover:bg-cream bg-surface border'
              "
              @click="conditionStatus = ''"
            >
              선택 안 함
            </button>
            <button
              v-for="opt in CONDITION_OPTIONS"
              :key="opt.value"
              type="button"
              class="rounded-full px-3.5 py-2 text-[12.5px] font-medium transition"
              :class="
                conditionStatus === opt.value
                  ? 'bg-ink text-cream'
                  : 'border-line text-dim hover:bg-cream bg-surface border'
              "
              @click="conditionStatus = opt.value"
            >
              {{ opt.label }}
            </button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>
