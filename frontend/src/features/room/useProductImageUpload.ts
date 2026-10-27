// ROOM-01 대표 이미지 업로드 오케스트레이션 (S15P11A704-443 · 358)
//
// `RoomCreateStep1`은 파일을 고르면 `URL.createObjectURL(file)`로 만든 **`blob:` URL**을 `images`에
// 넣는다. 그런데 명세 ROOM-01의 `product.imageUrl`은 *"미리 업로드한 단일 상품 이미지 URL"*이고
// 서버가 **현재 판매자가 업로드한 URL인지까지 검증**한다 — blob URL을 보내면 거절된다.
//
// 그 변환 단계가 **수정 화면에만 있고 개설 화면에는 없었다.** 목 모드에서는 `createAuctionRoom`이
// `imageUrl`을 쓰지도 않았고 ROOM-01도 미구현이라 조용했는데, 둘 다 사라지면서(504·`222`)
// 개설이 400으로 떨어지는 상태가 됐다. 그래서 Step1을 고치는 대신 **경계를 밖에 두는** 이 훅을
// 두 화면이 공유한다.
//
// 여기 든 것은 전부 눈에 안 보이는 함정이라, 복사하면 한쪽만 고쳐진다:
//   ① 실패한 항목을 다시 집으면 무한 재시도가 된다 → 시도한 id를 기록해 한 번만
//   ② 교체된 이전 blob은 Step1의 언마운트 정리가 못 잡아 **샌다** → `nextTick` 뒤 revoke
//   ③ 업로드 중에 화면을 떠나면 뒤늦은 응답이 상태를 되살린다 → `isCancelled` 가드

import { computed, nextTick, ref, watch, type Ref } from 'vue'
import { uploadProductImage } from '@/shared/api/uploads'
import { toApiError } from '@/shared/api/errors'
import type { ProductImageDraft } from '@/stores/room'

export interface ProductImageUpload {
  /** 업로드가 진행 중인가 — 제출 버튼을 막는 데 쓴다 */
  uploading: Ref<boolean>
  /** 사용자에게 보여줄 실패 사유. 성공하면 빈 문자열 */
  error: Ref<string>
  /**
   * 아직 서버 URL로 바뀌지 않은 항목이 남았는가.
   *
   * **업로드 중이 아닌데 이게 참이면 실패한 것이다.** 이 상태로 제출하면 서버가 거절하거나
   * (수정 화면처럼) 그 필드가 페이로드에서 빠져 **에러 없는 실패**가 된다 — 화면은 "저장됐다"고
   * 말하는데 사진만 안 바뀐다. 두 화면 모두 제출 전에 이 값을 본다.
   */
  hasPending: Ref<boolean>
}

/**
 * `images`의 blob 항목을 업로드 URL로 바꾼다. 항목이 바뀔 때마다 자동으로 돈다.
 *
 * @param images    Step1과 `v-model:images`로 이어진 배열
 * @param isCancelled 화면을 이미 떠났는지 — 뒤늦은 응답으로 상태를 쓰지 않기 위한 가드
 */
export function useProductImageUpload(
  images: Ref<ProductImageDraft[]>,
  isCancelled: () => boolean = () => false,
): ProductImageUpload {
  const uploading = ref(false)
  const error = ref('')

  /**
   * 이미 처리를 **시도한** 항목 id. 성공·실패를 가리지 않는다 —
   * 실패한 것을 다시 집으면 watch가 재발화할 때마다 같은 파일을 계속 올리게 된다.
   * 재시도는 사용자가 사진을 다시 고르는 것이다(새 항목이므로 id가 다르다).
   */
  const syncedIds = new Set<string>()

  const hasPending = computed(() => images.value.some((img) => img.imageUrl.startsWith('blob:')))

  async function sync(): Promise<void> {
    if (uploading.value) return
    const pending = images.value.filter(
      (img) => img.imageUrl.startsWith('blob:') && !syncedIds.has(img.id),
    )
    if (pending.length === 0) return

    uploading.value = true
    error.value = ''
    try {
      for (const item of pending) {
        syncedIds.add(item.id)
        const response = await fetch(item.imageUrl)
        const blob = await response.blob()
        const file = new File([blob], 'product-image', { type: blob.type })
        const uploaded = await uploadProductImage(file)
        if (isCancelled()) return
        const target = images.value.find((img) => img.id === item.id)
        if (target === undefined) continue
        const previous = target.imageUrl
        target.imageUrl = uploaded
        // 덮어쓴 순간 이전 blob은 아무도 참조하지 않는데, Step1의 언마운트 정리는 배열의
        // **현재** 값만 훑으므로 이걸 못 잡는다 — 여기서 놓아주지 않으면 교체할 때마다 샌다.
        // DOM이 새 src로 갱신된 뒤에 revoke해야 교체 순간 깨진 이미지가 스치지 않는다.
        if (previous !== uploaded && previous.startsWith('blob:')) {
          void nextTick(() => URL.revokeObjectURL(previous))
        }
      }
    } catch (caught) {
      if (!isCancelled()) error.value = `사진을 올리지 못했어요 — ${toApiError(caught).message}`
    } finally {
      if (!isCancelled()) uploading.value = false
    }
  }

  watch(images, () => void sync(), { deep: true })

  return { uploading, error, hasPending }
}
