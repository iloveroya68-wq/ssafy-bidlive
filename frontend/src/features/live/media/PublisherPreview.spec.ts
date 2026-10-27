import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PublisherPreview from './PublisherPreview.vue'
import type { AuctionEndEvent } from '@/features/live/socket/types'
import { useLiveStore } from '@/stores/live'

// 얼굴 모자이크 토글의 **트랙 교체 경로** (S15P11A704-699).
//
// 이 파일이 잠그는 것은 하나다: 세션이 있느냐 없느냐로 교체 방법이 갈린다는 것.
// 처음엔 두 경우 모두 `publisher.replaceTrack`을 썼는데, 세션 없는 publisher(목 모드 전체 +
// 실 모드에서 방송 시작 전)에서 그걸 부르면 openvidu 내부가 `this.session.emitEvent`를 건드리다 터진다
// (OpenVidu.js:618, initSession 전에는 session이 undefined). 그러고도 **터지기 전에 기존 트랙을
// stop + removeTrack까지 끝내 놔서** catch로 상태만 되돌려서는 프리뷰가 검게 굳은 채 남았다.
//
// 코드를 읽어 세운 가정이라 타입도 기존 테스트도 못 잡았고 실기기에서만 드러났다 — 그래서
// 「replaceTrack을 부르지 않는다」를 단정으로 박아 둔다. 리팩터로 한 경로로 합치면 여기가 먼저 깨진다.

/** jsdom엔 MediaStreamTrack이 없다 — stop()이 readyState를 실제로 바꾸는 최소 대역이면 충분하다. */
function fakeTrack(label: string) {
  const track = {
    label,
    kind: 'video',
    readyState: 'live',
    stop: vi.fn(() => {
      track.readyState = 'ended'
    }),
    clone: vi.fn(() => fakeTrack(`${label}-clone`)),
  }
  return track
}
type FakeTrack = ReturnType<typeof fakeTrack>

/** addVideoElement가 <video>.srcObject에 물리는 바로 그 객체 — 교체가 여기에 반영돼야 프리뷰가 따라온다. */
function fakeStream(initial: FakeTrack) {
  const tracks: FakeTrack[] = [initial]
  return {
    getTracks: () => [...tracks],
    getVideoTracks: () => [...tracks],
    removeTrack: (t: FakeTrack) => {
      const i = tracks.indexOf(t)
      if (i >= 0) tracks.splice(i, 1)
    },
    addTrack: (t: FakeTrack) => {
      tracks.push(t)
    },
  }
}

const h = vi.hoisted(() => ({
  isMock: { value: true },
  createFaceMosaic: vi.fn(),
  uploadLiveSnapshot: vi.fn(),
  publisher: null as unknown,
  session: {
    connect: () => Promise.resolve(),
    publish: () => Promise.resolve(),
    disconnect: () => {},
  },
}))

vi.mock('@/shared/api/mock', () => ({ isMockApi: () => h.isMock.value }))
vi.mock('./api', () => ({
  issuePublisherToken: () => Promise.resolve({ token: 't' }),
  uploadLiveSnapshot: h.uploadLiveSnapshot,
}))
vi.mock('openvidu-browser', () => ({
  OpenVidu: class {
    initPublisherAsync() {
      return Promise.resolve(h.publisher)
    }
    initSession() {
      return h.session
    }
  },
}))
// 컴포넌트가 `await import('./useFaceMosaic')`로 부르는 자리 — wasm·캔버스는 jsdom에 없으므로 통째로 건다.
vi.mock('./useFaceMosaic', () => ({ createFaceMosaic: h.createFaceMosaic }))

let camera: FakeTrack
let stream: ReturnType<typeof fakeStream>
let mosaicTrack: FakeTrack
let dispose: ReturnType<typeof vi.fn>
let replaceTrack: ReturnType<typeof vi.fn>

// jsdom엔 2D 컨텍스트도 toBlob도 없다(네이티브 canvas 바인딩이 빠져 있다). 「어떤 픽셀이 떠지는가」는
// 어차피 실기기에서만 확인되는 것이라, 인코딩 대역만 최소로 세워 두고 그 위의 **트리거 가드**를 잠근다.
beforeAll(() => {
  Object.defineProperty(HTMLVideoElement.prototype, 'videoWidth', {
    configurable: true,
    get: () => 640,
  })
  Object.defineProperty(HTMLVideoElement.prototype, 'videoHeight', {
    configurable: true,
    get: () => 480,
  })
  HTMLCanvasElement.prototype.getContext = (() => ({ drawImage: () => {} })) as never
  HTMLCanvasElement.prototype.toBlob = function toBlob(callback: BlobCallback) {
    callback(new Blob(['jpeg'], { type: 'image/jpeg' }))
  }
})

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
  h.isMock.value = true
  h.uploadLiveSnapshot.mockResolvedValue(undefined)
  camera = fakeTrack('camera')
  stream = fakeStream(camera)
  mosaicTrack = fakeTrack('mosaic')
  dispose = vi.fn()
  replaceTrack = vi.fn(() => Promise.resolve())
  h.publisher = {
    stream: { getMediaStream: () => stream },
    addVideoElement: vi.fn(),
    publishAudio: vi.fn(),
    publishVideo: vi.fn(),
    replaceTrack,
  }
  h.createFaceMosaic.mockResolvedValue({ track: mosaicTrack, dispose })
})

/** 프리뷰를 준비 완료(status='ready') 상태까지 띄운다. */
async function ready() {
  const wrapper = mount(PublisherPreview)
  await flushPromises()
  return wrapper
}

/** 실 모드에서 라이브 전환 → startBroadcast가 connect+publish까지 끝낸 상태를 만든다. */
async function broadcasting() {
  h.isMock.value = false
  const live = useLiveStore()
  live.roomId = 1
  const wrapper = await ready()
  live.auctionStatus = 'LIVE'
  await flushPromises()
  return wrapper
}

const mosaicButton = (wrapper: Awaited<ReturnType<typeof ready>>) =>
  wrapper.get('button[data-mosaic]')

/** 지금 프리뷰에 실제로 그려질 수 있는 트랙 — 죽은 트랙만 남으면 화면이 검어진다. */
const liveTrack = () => stream.getVideoTracks().find((t) => t.readyState === 'live')

describe('PublisherPreview 모자이크 토글 — 세션 없는 프리뷰 (S15P11A704-699)', () => {
  it('세션이 없으면 replaceTrack을 부르지 않고 MediaStream을 직접 갈아끼운다', async () => {
    const wrapper = await ready()

    await mosaicButton(wrapper).trigger('click')
    await flushPromises()

    // 이 한 줄이 버그 자체다 — 세션 없는 publisher에서 replaceTrack은 openvidu 내부에서 터진다
    expect(replaceTrack).not.toHaveBeenCalled()
    // 프리뷰가 물고 있는 그 스트림에 처리된 트랙이 들어가 있어야 화면이 바뀐다(재바인딩 없음)
    expect(stream.getVideoTracks()).toEqual([mosaicTrack])
    expect(mosaicButton(wrapper).attributes('aria-pressed')).toBe('true')
  })

  it('교체당한 원본 트랙은 멈춘다 — openvidu 경로와 수명 규칙을 맞춘다', async () => {
    // 여기가 어긋나면 ON/OFF 로직이 두 경로에서 달라진다: 원본이 살아 있으면 카메라 표시등이 남고,
    // 반대로 파이프라인 입력(클론)까지 멈추면 캔버스가 검게 언다.
    const wrapper = await ready()

    await mosaicButton(wrapper).trigger('click')
    await flushPromises()

    expect(camera.stop).toHaveBeenCalledOnce()
    expect(camera.clone).toHaveBeenCalledOnce() // 파이프라인 입력은 클론이다
    const source = h.createFaceMosaic.mock.calls[0][0] as FakeTrack
    expect(source.readyState).toBe('live') // 클론은 살아 있어야 파이프라인이 돈다
  })

  it('토글을 끄면 클론을 되붙이고 처리된 트랙을 멈춘다 — 원본은 이미 죽어 있다', async () => {
    const wrapper = await ready()
    await mosaicButton(wrapper).trigger('click')
    await flushPromises()
    const source = h.createFaceMosaic.mock.calls[0][0] as FakeTrack

    await mosaicButton(wrapper).trigger('click')
    await flushPromises()

    expect(replaceTrack).not.toHaveBeenCalled()
    expect(stream.getVideoTracks()).toEqual([source])
    expect(mosaicTrack.stop).toHaveBeenCalled()
    expect(dispose).toHaveBeenCalledOnce()
    expect(mosaicButton(wrapper).attributes('aria-pressed')).toBe('false')
  })

  it('준비에 실패해도 프리뷰가 살아 있고 토글은 꺼진 채로 안내한다', async () => {
    // wasm·모델을 못 받는 브라우저의 경로. 아직 아무것도 못 바꾼 실패라 원본이 그대로 남아야 한다.
    h.createFaceMosaic.mockRejectedValue(new Error('wasm 로드 실패'))
    vi.spyOn(console, 'warn').mockImplementation(() => {})
    const wrapper = await ready()

    await mosaicButton(wrapper).trigger('click')
    await flushPromises()

    expect(stream.getVideoTracks()).toEqual([camera])
    expect(camera.stop).not.toHaveBeenCalled()
    expect(mosaicButton(wrapper).attributes('aria-pressed')).toBe('false')
    expect(mosaicButton(wrapper).attributes('title')).toBe('모자이크를 사용할 수 없어요')
  })
})

describe('PublisherPreview 모자이크 토글 — 방송 중 (S15P11A704-699)', () => {
  it('세션이 있으면 replaceTrack으로 간다 — 발행 중 교체는 재협상 없는 그 경로다', async () => {
    const wrapper = await broadcasting()

    await mosaicButton(wrapper).trigger('click')
    await flushPromises()

    expect(replaceTrack).toHaveBeenCalledWith(mosaicTrack)
    expect(mosaicButton(wrapper).attributes('aria-pressed')).toBe('true')
  })

  it('replaceTrack이 트랙을 죽인 뒤 거부해도 프리뷰가 검게 남지 않는다', async () => {
    // openvidu는 기존 트랙 stop + removeTrack을 **먼저** 하고 나서 던진다(Publisher.js:750~753).
    // 복구가 없으면 스트림에 살아 있는 트랙이 하나도 안 남아 화면이 검게 굳는다 — 실기기에서 본 그 증상이다.
    vi.spyOn(console, 'warn').mockImplementation(() => {})
    replaceTrack.mockImplementation(() => {
      stream.removeTrack(camera)
      camera.stop()
      return Promise.reject(new Error('교체 실패'))
    })
    const wrapper = await broadcasting()

    await mosaicButton(wrapper).trigger('click')
    await flushPromises()

    const source = h.createFaceMosaic.mock.calls[0][0] as FakeTrack
    expect(liveTrack()).toBe(source) // 살아 있는 클론이 그 자리를 메운다
    expect(dispose).toHaveBeenCalledOnce() // 쓰지 못한 파이프라인은 정리한다
    expect(mosaicButton(wrapper).attributes('aria-pressed')).toBe('false')
    expect(mosaicButton(wrapper).attributes('title')).toBe('모자이크를 사용할 수 없어요')
  })
})

// LIVE-06 낙찰 스냅샷 트리거 (S15P11A704-704).
//
// 잠그는 것은 **언제 찍고 언제 안 찍는가**다. 캡처 자체(어떤 픽셀이 뜨는가)는 jsdom에 캔버스가 없어
// 확인할 수 없고, 정작 조용히 틀어지는 쪽은 가드다 — 유찰인데 올리면 서버가 409로 막아 주지만
// 실패는 삼켜지므로 아무 흔적이 없고, 반대로 중복이면 같은 S3 key를 덮어써서 이것도 티가 안 난다.
describe('PublisherPreview 낙찰 스냅샷 (S15P11A704-704)', () => {
  const settled = (): AuctionEndEvent => ({
    type: 'AUCTION_SETTLED',
    data: {
      winner: '입찰자',
      highestBidAmount: 120_000,
      secondHighestBidAmount: 100_000,
      finalPrice: 100_000,
      resultType: 'SECOND_PRICE',
    },
  })

  it('낙찰이 확정되면 그 경매 id로 한 번 올린다', async () => {
    await broadcasting()
    const live = useLiveStore()

    live.endEvent = settled()
    await flushPromises()

    expect(h.uploadLiveSnapshot).toHaveBeenCalledOnce()
    // 경로의 roomId도 폼의 auctionId도 이 한 값이다 — 콘솔이 경매 id로 방에 붙기 때문이다.
    expect(h.uploadLiveSnapshot.mock.calls[0][0]).toBe(1)
    expect(h.uploadLiveSnapshot.mock.calls[0][1]).toBeInstanceOf(Blob)
  })

  it('같은 경매의 종료 이벤트가 다시 와도 두 번 올리지 않는다', async () => {
    await broadcasting()
    const live = useLiveStore()

    live.endEvent = settled()
    await flushPromises()
    live.endEvent = settled() // 재접속 스냅샷 등으로 같은 사실이 다시 실릴 수 있다
    await flushPromises()

    expect(h.uploadLiveSnapshot).toHaveBeenCalledOnce()
  })

  it('유찰·강제 종료·집계 시작에는 찍지 않는다 — 명세가 「낙찰 시점」이다', async () => {
    await broadcasting()
    const live = useLiveStore()
    const endedAt = '2026-08-05T12:00:00+09:00'

    live.endEvent = { type: 'AUCTION_ENDED', data: { endedAt, status: 'ENDED' } }
    await flushPromises()
    live.endEvent = { type: 'AUCTION_NO_SALE', data: { reason: 'NO_BID', endedAt } }
    await flushPromises()
    live.endEvent = {
      type: 'AUCTION_CANCELED',
      data: { reason: 'HOST_LEFT', endedAt, resultPath: '/nosale/1' },
    }
    await flushPromises()

    expect(h.uploadLiveSnapshot).not.toHaveBeenCalled()
  })

  it('송출 중이 아니면 무동작 — 시청자에게 나간 적 없는 화면은 증빙이 아니다', async () => {
    await ready() // 목 모드 프리뷰: 세션도 서버도 없다
    const live = useLiveStore()

    live.endEvent = settled()
    await flushPromises()

    expect(h.uploadLiveSnapshot).not.toHaveBeenCalled()
  })

  it('업로드가 실패해도 삼킨다 — 증빙이 낙찰 오버레이·방송 종료를 막으면 안 된다', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    h.uploadLiveSnapshot.mockRejectedValue(new Error('502 SNAPSHOT_STORAGE_ERROR'))
    await broadcasting()
    const live = useLiveStore()

    live.endEvent = settled()
    await flushPromises() // 여기서 거절이 새면 unhandled rejection으로 이 줄이 터진다

    expect(warn).toHaveBeenCalled()
  })
})
