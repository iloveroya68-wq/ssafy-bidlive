<script setup lang="ts">
// 잠긴 패널에 남기는 안내 박스 — 입찰 dock·예측 dock 두 곳에 같은 모양으로 등장해서 추출했다
// (AGENTS.md 컨벤션: 같은 UI가 두 번째로 등장할 때 shared/ui/로).
// 디자인 원본의 `.locknote`. 패널을 숨기는 대신 자리를 지키는 것이 요점이다 — docs/03 §4-1.
defineProps<{ text: string }>()
</script>

<template>
  <p
    class="border-faint/50 bg-line/40 text-dim mt-2.5 rounded-[10px] border border-dashed px-3 py-2.5 text-[12px] leading-relaxed"
    role="status"
  >
    {{ text }}
  </p>
</template>
