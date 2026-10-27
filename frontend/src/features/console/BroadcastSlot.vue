<script setup lang="ts">
// 송출 프리뷰 카드 (와이어프레임 console 우측).
// 프리뷰 본체는 FE2가 `features/live/media/PublisherPreview.vue`로 제공하고 여기서 꽂기만 한다
// (docs/02 「console 담당」 확정). 상대 소유 컴포넌트라 import만 하고 내부는 수정하지 않는다.
//
// ⚠️ 카메라·마이크 토글은 PublisherPreview가 자체적으로 갖고 있다 — 여기서 또 만들지 말 것.
//    예전에 이 파일에 껍데기 버튼 2개가 disabled로 남아 있었는데, 프리뷰를 꽂으면 중복이라 지웠다.
//
// ⚠️ **방 삭제 버튼은 여기 없다** (S15P11A704-691에서 제거). 대기 상태의 방 관리 액션은
//    화면(ConsoleView)이 `#status` 슬롯에 한 줄로 넣는다 — 예전에는 이 카드와 아래 액션 줄이
//    각각 「방 삭제하기」·「방 삭제」를 그려서, 가로 배치로 바꾸자 같은 되돌릴 수 없는 동작의
//    버튼 두 개가 한 화면에 나란히 보였다. 되돌릴 수 없는 동작의 진입점은 하나여야 한다.
import PublisherPreview from '@/features/live/media/PublisherPreview.vue'

defineProps<{
  state: 'wait' | 'live'
  pending: boolean
  /** 한 번 눌러 확인 대기 중 — 되돌릴 수 없는 동작이라 라벨로도 단계를 드러낸다 */
  confirming?: boolean
}>()

const emit = defineEmits<{ end: [] }>()
</script>

<template>
  <!--
    프리뷰와 방송 제어를 **가로로 나눈다** (S15P11A704-691). 세로로 쌓았을 때는 프리뷰 폭을 카드보다
    좁게 잡아야 해서(16:9라 폭이 곧 높이다) 카드 오른쪽이 통째로 비었고, 그만큼 아래 내용이 접혔다.
    프리뷰는 왼쪽에서 폭을 다 쓰고, 남는 오른쪽을 상태·제어가 채운다.
    좁은 화면(sm 미만)에서는 나란히 둘 자리가 없으므로 종전대로 쌓는다.
  -->
  <div class="flex flex-col gap-4 sm:flex-row">
    <!-- FE2 제공 프리뷰. 카메라·마이크 토글도 이 컴포넌트 안에 있다 -->
    <!-- 프리뷰가 16:9라 **폭이 곧 카드 높이**다. 1:1로 나누면 자기 구도를 보기엔 작고, 1.5를 넘기면
         카드 하나가 화면 절반을 먹는다 — 1.2에서 약 375×211이고 카드가 약 280px에 든다. -->
    <div class="min-w-0 sm:flex-[1.2]">
      <PublisherPreview />
    </div>

    <div class="flex min-w-0 flex-col sm:flex-1">
      <!-- 방 상태·제목·진행 제어는 화면(ConsoleView)이 채운다 — 이 컴포넌트는 자리만 낸다 -->
      <slot name="status" />

      <!--
        대기일 때만 — 시작 전 점검. 방송이 이미 돌고 있으면 알려줄 이유가 없다.
        ⚠️ 카메라·마이크 항목은 여기 두지 않는다. 하드코딩 ✓라 권한을 거부해도 "연결됨"이 떠서
           프리뷰가 붙은 지금은 화면이 스스로와 모순된다. 그 두 상태는 PublisherPreview가 진짜로 표시한다.
      -->
      <ul v-if="state === 'wait'" class="text-dim mt-3 flex flex-col gap-1.5 text-[12px]">
        <li><span class="text-success">✓</span> 상품 정보 등록 완료</li>
      </ul>

      <!--
        경고는 **버튼 바로 아래**에 둔다 — 되돌릴 수 없는 동작의 확인 문구가 버튼과 떨어져 있으면
        둘의 연결이 안 읽힌다. 예전에는 `ConsoleView` 쪽 슬롯 바깥에 있어 카드 최하단으로 밀렸다.
      -->
      <template v-if="state === 'live'">
        <button
          type="button"
          class="bg-terra text-on-dark hover:bg-terra-d mt-4 rounded-xl py-3 text-[13.5px] font-semibold transition disabled:cursor-not-allowed disabled:opacity-45 sm:mt-auto"
          :disabled="pending"
          @click="emit('end')"
        >
          {{ confirming ? '한 번 더 눌러 종료' : '방송 종료' }}
        </button>
        <p v-if="confirming" class="text-red mt-2 text-[11.5px]" role="alert">
          한 번 더 누르면 방송이 종료돼요. 되돌릴 수 없습니다.
        </p>
      </template>
    </div>
  </div>
</template>
