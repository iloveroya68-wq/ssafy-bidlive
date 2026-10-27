<script setup lang="ts">
// MEM 약관 전문 보기 — `content`가 TEXT라 길다. 모달로 띄운다.
// 약관은 버전 관리되고(개정 시 새 버전 행) 재동의 대상이 되므로 헤더에 버전을 같이 보여준다.
// 발효일은 표시하지 않는다 — 「약관 조회」 응답에 `effective_at`이 없다(시행 중인 것만 내려온다).
import type { TermsItem } from '@/shared/api/terms'

defineProps<{ terms: TermsItem }>()

const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <div
    class="bg-stage/55 fixed inset-0 z-50 flex items-center justify-center p-5 backdrop-blur-sm"
    role="dialog"
    aria-modal="true"
    :aria-label="terms.title"
    @click.self="emit('close')"
  >
    <div class="bg-surface flex max-h-[80vh] w-full max-w-150 flex-col overflow-hidden rounded-2xl">
      <header class="border-line flex items-start justify-between gap-4 border-b px-6 py-4.5">
        <div>
          <h2 class="text-ink text-base font-bold">{{ terms.title }}</h2>
          <p class="text-faint mt-1 text-[11.5px] tabular-nums">v{{ terms.version }}</p>
        </div>
        <button
          type="button"
          class="text-faint hover:text-ink -mt-1 flex-none p-1 text-xl leading-none transition"
          aria-label="닫기"
          @click="emit('close')"
        >
          ×
        </button>
      </header>

      <div
        class="text-dim overflow-y-auto px-6 py-5 text-[13px] leading-relaxed whitespace-pre-line"
      >
        {{ terms.content }}
      </div>

      <footer class="border-line border-t px-6 py-3.5 text-right">
        <button
          type="button"
          class="bg-ink text-on-dark hover:bg-stage rounded-lg px-5 py-2.5 text-[13px] font-semibold transition"
          @click="emit('close')"
        >
          닫기
        </button>
      </footer>
    </div>
  </div>
</template>
