import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'
import { useProductImageUpload } from './useProductImageUpload'
import * as uploadsApi from '@/shared/api/uploads'
import type { ProductImageDraft } from '@/stores/room'

// ROOM-01 대표 이미지 업로드 (S15P11A704-443).
//
// 여기서 잡는 것은 전부 **화면이 멀쩡해 보이는 실패**다:
//   ① blob URL이 그대로 나가면 서버가 400으로 거절한다 (개설 화면에 이 단계가 아예 없었다)
//   ② 실패한 항목을 다시 집으면 watch가 돌 때마다 같은 파일을 계속 올린다
//   ③ 교체된 blob을 놓아주지 않으면 사진을 바꿀 때마다 샌다 — 눈에 전혀 안 보인다
//   ④ 이탈 뒤 도착한 응답이 상태를 되살린다

function draft(over: Partial<ProductImageDraft> = {}): ProductImageDraft {
  return { id: 'a', imageUrl: 'blob:local/a', sortOrder: 0, isThumbnail: true, ...over }
}

/**
 * `sync`는 watch로만 돈다 — 배열을 건드린 뒤 flush를 기다린다.
 * `nextTick`만으로는 부족하다: `Response.blob()`이 **매크로태스크**로 풀려서
 * 마이크로태스크만 비우면 훅이 첫 `await`에 멈춰 있는 채로 검사하게 된다(실제로 겪었다).
 */
async function settle() {
  for (let i = 0; i < 5; i += 1) {
    await nextTick()
    await new Promise((resolve) => setTimeout(resolve, 0))
  }
}

describe('useProductImageUpload', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    // 훅은 blob을 File로 바꾸려고 fetch를 탄다. jsdom에는 blob: 핸들러가 없다.
    // **호출마다 새 Response를 만든다** — 같은 인스턴스를 돌려주면 두 번째 `.blob()`이
    // "body already read"로 터져서, 첫 장만 올라간 걸 「업로드가 안 된다」로 오진하게 된다.
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response(new Blob(['x']))),
    )
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL: vi.fn(() => 'blob:local/new'),
      revokeObjectURL: vi.fn(),
    })
  })

  it('blob 항목을 서버 URL로 바꿔치기한다 — 그대로 보내면 서버가 거절한다', async () => {
    vi.spyOn(uploadsApi, 'uploadProductImage').mockResolvedValue('https://cdn/a.jpg')
    const images = ref([draft()])

    useProductImageUpload(images)
    images.value.push(draft({ id: 'b', imageUrl: 'blob:local/b', isThumbnail: false }))
    await settle()

    expect(images.value.map((i) => i.imageUrl)).toEqual(['https://cdn/a.jpg', 'https://cdn/a.jpg'])
  })

  it('이미 서버 URL인 항목은 다시 올리지 않는다', async () => {
    const spy = vi.spyOn(uploadsApi, 'uploadProductImage').mockResolvedValue('https://cdn/a.jpg')
    const images = ref([draft({ imageUrl: 'https://cdn/kept.jpg' })])

    useProductImageUpload(images)
    images.value.push(draft({ id: 'b', imageUrl: 'https://cdn/kept2.jpg' }))
    await settle()

    expect(spy).not.toHaveBeenCalled()
  })

  it('실패한 항목을 다시 집지 않는다 — 재시도 폭주를 막는다', async () => {
    // 실패해도 배열은 blob인 채로 남는다. "blob이면 올린다"만으로는 watch가 돌 때마다
    // 같은 파일을 계속 올리게 된다 — 사용자가 다른 칸을 타이핑하는 것만으로도 재발화한다.
    const spy = vi.spyOn(uploadsApi, 'uploadProductImage').mockRejectedValue(new Error('boom'))
    const images = ref<ProductImageDraft[]>([])

    const { error, hasPending } = useProductImageUpload(images)
    images.value.push(draft())
    await settle()
    expect(spy).toHaveBeenCalledOnce()

    // 무관한 변경으로 watch를 다시 때린다
    images.value[0].sortOrder = 5
    await settle()

    expect(spy).toHaveBeenCalledOnce()
    expect(error.value).not.toBe('')
    // 실패했으므로 blob이 남아 있다 — 제출을 막는 근거가 된다
    expect(hasPending.value).toBe(true)
  })

  it('교체한 blob을 놓아준다 — 안 하면 사진을 바꿀 때마다 샌다', async () => {
    vi.spyOn(uploadsApi, 'uploadProductImage').mockResolvedValue('https://cdn/a.jpg')
    const images = ref([draft()])

    useProductImageUpload(images)
    images.value.push(draft({ id: 'b', imageUrl: 'blob:local/b' }))
    await settle()

    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:local/a')
  })

  it('화면을 떠난 뒤 도착한 응답으로 상태를 쓰지 않는다', async () => {
    vi.spyOn(uploadsApi, 'uploadProductImage').mockResolvedValue('https://cdn/a.jpg')
    const images = ref([draft()])
    let left = false

    const { uploading } = useProductImageUpload(images, () => left)
    left = true
    images.value.push(draft({ id: 'b', imageUrl: 'blob:local/b' }))
    await settle()

    // 이탈 뒤에는 배열도 진행 플래그도 건드리지 않는다
    expect(images.value[0].imageUrl).toBe('blob:local/a')
    expect(uploading.value).toBe(true)
  })

  it('업로드가 끝나면 hasPending이 내려간다 — 제출 가능 판정의 근거다', async () => {
    vi.spyOn(uploadsApi, 'uploadProductImage').mockResolvedValue('https://cdn/a.jpg')
    const images = ref<ProductImageDraft[]>([])

    const { hasPending, uploading } = useProductImageUpload(images)
    images.value.push(draft())
    await settle()

    expect(hasPending.value).toBe(false)
    expect(uploading.value).toBe(false)
  })
})
