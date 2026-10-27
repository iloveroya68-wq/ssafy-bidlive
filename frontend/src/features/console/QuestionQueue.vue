<script setup lang="ts">
// CHAT-06 미답변 질문 큐. 「질문 큐 조회」가 PENDING만 내려주고, 답변 완료는 「질문 큐 항목 제거」가 받는다.
// 식별자는 채팅 메시지 id가 아니라 `questionId`(ULID 문자열)다.
// `<script setup>`은 export를 못 하므로 항목 타입은 인라인으로 둔다.
const props = defineProps<{
  items: { questionId: string; nickname: string; content: string }[]
  /** 답변 완료 요청이 나가 있는 questionId들 — 질문끼리 독립이라 여러 건이 동시에 처리될 수 있다 */
  resolvingIds?: string[]
  /**
   * 진입 시 받아 온 상한(100건) 뒤로 아직 남은 질문이 있는가 (S15P11A704-371).
   * 개수는 모른다 — 「질문 큐 조회」가 `hasNext`만 주고 총계를 안 준다. 그래서 「N건 더」로 못 쓴다.
   */
  hasMore?: boolean
  loadingMore?: boolean
  /** 「더 보기」 결과를 스크린리더에 알릴 문구 — 화면에는 안 보인다 (S15P11A704-371) */
  announcement?: string
}>()

const isResolving = (questionId: string) => props.resolvingIds?.includes(questionId) ?? false

const emit = defineEmits<{ answer: [questionId: string]; more: [] }>()
</script>

<template>
  <div class="mt-5">
    <div class="mb-2.5 flex items-center gap-1.5 text-[13px] font-semibold">
      <span class="text-ink">미답변 질문 큐</span>
      <span class="text-slate tabular-nums">{{ items.length }}</span>
    </div>

    <p v-if="items.length === 0" class="text-faint text-[12px]">아직 들어온 질문이 없어요.</p>

    <ul v-else class="flex flex-col gap-2">
      <li
        v-for="q in items"
        :key="q.questionId"
        class="bg-cream border-line flex items-start gap-3 rounded-xl border px-3.5 py-2.5"
      >
        <div class="min-w-0 flex-1">
          <div class="text-faint text-[11px]">{{ q.nickname }}</div>
          <p class="text-ink mt-0.5 text-[13px] leading-snug break-words">{{ q.content }}</p>
        </div>
        <button
          type="button"
          class="border-line text-dim hover:text-ink flex-none rounded-lg border px-2.5 py-1.5 text-[11.5px] transition disabled:opacity-45"
          :disabled="isResolving(q.questionId)"
          @click="emit('answer', q.questionId)"
        >
          {{ isResolving(q.questionId) ? '처리 중…' : '답변 완료' }}
        </button>
      </li>
    </ul>

    <!--
      상한 뒤로 남은 질문이 있을 때만 나온다 (S15P11A704-371).
      **개수를 안 쓰는 이유**: 「질문 큐 조회」가 `hasNext`만 주고 총계를 안 준다.
      "N건 더"라고 쓰려면 숫자를 지어내야 하고, 큐 위쪽 카운트(`items.length`)와 어긋나 보인다.
    -->
    <!--
      상주 리전이다 (S15P11A704-422의 교훈) — `v-if`로 나타나는 `role="status"`는 내용과 함께
      삽입되면 낭독을 건너뛰는 리더가 있다. 여기서는 텍스트만 갈아 끼운다.
    -->
    <p class="sr-only" role="status">{{ announcement }}</p>

    <button
      v-if="hasMore"
      type="button"
      class="border-line text-dim hover:text-ink mt-2 w-full rounded-xl border py-2.5 text-[12px] transition disabled:opacity-45"
      :disabled="loadingMore"
      @click="emit('more')"
    >
      {{ loadingMore ? '불러오는 중…' : '남은 질문 더 보기' }}
    </button>
  </div>
</template>
