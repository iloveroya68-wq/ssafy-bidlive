import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import LiveStage from './LiveStage.vue'

// LiveStage가 setup에서 useRoute()를 부른다 — 목 모드에서 오디오 컨트롤을 켜는 `?audiodemo=1`을 읽기 위해서다
// (S15P11A704-470). 라우터 없이 부르면 undefined라 ChatPanel.spec과 같은 방식으로 최소 스텁만 준다.
const currentQuery = ref<Record<string, string>>({})
vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router')
  return {
    ...actual,
    useRoute: () => ({
      get query() {
        return currentQuery.value
      },
    }),
  }
})

// LiveStage의 배선 검증. 실 미디어 서버가 없어 실제 재생·대역폭은 확인 못 하지만, OpenVidu SDK·토큰 발급·
// 목 스위치를 모킹해 상태 전이(connecting/live/reconnecting/stream-ended)와 컨트롤 노출 조건을 결정론적으로 고정한다.
// 원래 이 파일은 저대역폭 토글(S15P11A704-378) 전용이었다 — 그 토글은 S15P11A704-463에서 제거됐다.

const h = vi.hoisted(() => {
  const handlers = new Map<string, (p: unknown) => void>()
  const subscriber = { addVideoElement: vi.fn() }
  // connect()를 deferred로 둔다 — 실서버는 기존 퍼블리셔의 streamCreated를 동기 emit한 뒤 resolve하므로,
  // 그 순서(emit → resolve)를 재현하려면 테스트가 resolve 시점을 제어해야 한다. 기본 미완료라도 기존 테스트는
  // connect resolve를 안 기다리고 streamCreated 이후 상태만 보므로 영향이 없다.
  let resolveConnect: () => void = () => {}
  const session = {
    on: (ev: string, cb: (p: unknown) => void) => {
      handlers.set(ev, cb)
    },
    subscribe: () => subscriber,
    connect: () => new Promise<void>((r) => (resolveConnect = r)),
    disconnect: () => {},
  }
  return {
    isMock: { value: true },
    session,
    emit: (ev: string, p?: unknown) => handlers.get(ev)?.(p),
    finishConnect: () => resolveConnect(),
    reset: () => handlers.clear(),
  }
})

vi.mock('@/shared/api/mock', () => ({ isMockApi: () => h.isMock.value }))
vi.mock('./api', () => ({ issueSubscriberToken: () => Promise.resolve({ token: 't' }) }))
vi.mock('openvidu-browser', () => ({
  OpenVidu: class {
    initSession() {
      return h.session
    }
  },
}))

// live 전용 컨트롤의 노출 여부 — status가 live/mock/재연결+프레임일 때만(canShowControls) 뜨는 전체화면 버튼을 본다.
// 463 전까지는 저대역폭 토글의 [aria-pressed]가 이 프록시였다. 그 버튼이 사라져 같은 조건을 쓰는 것으로 옮겼다
// (jsdom엔 Fullscreen API가 없어 아래 setFullscreenEnabled(true)가 기본값이므로 항상 렌더된다).
//
// ⚠️ 이 프록시의 조건은 canShowControls보다 하나 넓다 — showFullscreenButton = canShowControls && fullscreenAvailable다.
// 전체화면 버튼의 노출을 건드리는 변경(464의 우하단 이동 등)은 아래 「지금 live인가」 단정 4개의 의미를 조용히
// 바꾼다. 그런 변경을 할 때 이 줄을 먼저 읽고, 필요하면 프록시를 다른 live 전용 요소로 옮긴다(!178 리뷰).
const liveControl = (w: ReturnType<typeof mount>) => w.find('[aria-label="전체화면"]')

// <video>의 playing 이벤트 발화 — 실 모드에서 status='live'는 실제 재생 시작 뒤에만 서므로(S15P11A704-421),
// 스트림 수신(streamCreated) 후 이걸 쳐야 live로 전환된다.
const play = (w: ReturnType<typeof mount>) =>
  w.find('video').element.dispatchEvent(new Event('playing'))

// jsdom엔 HTMLMediaElement.play()가 없어서 **부르면 던진다.** 스텁이 없으면 476의 tryPlay가 항상 catch로
// 빠져 모든 테스트가 「자동재생 정책에 막힌」 경로만 타게 된다 — 기본이 소리 켬이라는 것을 관측할 수 없다.
// 그래서 기본은 성공으로 두고, 거부는 개별 테스트에서 mockRejectedValueOnce로 만든다.
const mediaPlay = vi.fn<() => Promise<void>>()

/** 자동재생 거부는 브라우저가 NotAllowedError로 준다 — 이름까지 같게 만든다. */
function notAllowed(): Error {
  const error = new Error('play() blocked by autoplay policy')
  error.name = 'NotAllowedError'
  return error
}

// jsdom엔 Fullscreen API가 없어 document.fullscreenEnabled가 undefined다 — 컴포넌트는 그걸 feature-detect로
// 읽어 버튼을 감추므로(!136 리뷰 1), 기본값을 "지원하는 브라우저"로 두고 미지원은 개별 테스트에서 뒤집는다.
// setup에서 한 번만 읽는 값이라 mount 전에 세워야 한다.
const setFullscreenEnabled = (enabled: boolean) =>
  Object.defineProperty(document, 'fullscreenEnabled', { configurable: true, get: () => enabled })

beforeEach(() => {
  vi.clearAllMocks()
  h.reset()
  h.isMock.value = true
  currentQuery.value = {}
  setFullscreenEnabled(true)
  mediaPlay.mockResolvedValue(undefined)
  Object.defineProperty(HTMLMediaElement.prototype, 'play', {
    configurable: true,
    writable: true,
    value: mediaPlay,
  })
})
afterEach(() => {
  vi.restoreAllMocks()
  Reflect.deleteProperty(document, 'fullscreenEnabled')
})

describe('LiveStage 자동재생·소리 켜기 (S15P11A704-421)', () => {
  it('스트림 수신 직후엔 아직 live가 아니고(영상 연결 중), playing 이벤트 뒤에야 live로 전환한다', async () => {
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()

    h.emit('streamCreated', { stream: {} })
    await flushPromises()
    // 재생 전 — 검은/멈춘 화면에 LIVE로 표시하지 않는다. 연결 중 오버레이 유지 + live 전용 컨트롤 미노출.
    expect(wrapper.text()).toContain('영상에 연결 중')
    expect(liveControl(wrapper).exists()).toBe(false)

    play(wrapper)
    await flushPromises()
    // 실제 재생 시작 → LIVE 확정. 오버레이 사라지고 컨트롤 노출.
    expect(wrapper.text()).not.toContain('영상에 연결 중')
    expect(liveControl(wrapper).exists()).toBe(true)
  })

  it('기본은 소리 켠 상태다 — 라벨 없이 아이콘만 (S15P11A704-476)', async () => {
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises()

    const video = wrapper.find('video').element as HTMLVideoElement
    expect(video.muted).toBe(false) // 421의 「음소거로 시작」을 뒤집었다
    expect(mediaPlay).toHaveBeenCalledOnce() // 소리 켠 채 한 번에 성공
    // 421은 「소리 켜기」를 글자로 노출했다 — 기본이 소리 켬이 되어 그 근거가 사라졌다
    expect(wrapper.get('button[data-mute]').text()).toBe('')
    expect(wrapper.get('button[data-mute]').attributes('aria-label')).toBe('음소거')
  })

  it('자동재생이 거부되면 음소거로 낮춰 재시도한다 — connecting에 갇히지 않는다 (476)', async () => {
    // 이 폴백이 없으면 정책이 막는 브라우저에서 playing이 오지 않아 검은 화면 + 「영상에 연결 중」이 남는다.
    mediaPlay.mockRejectedValueOnce(notAllowed())
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    await flushPromises()

    const video = wrapper.find('video').element as HTMLVideoElement
    expect(mediaPlay).toHaveBeenCalledTimes(2) // 소리 켬 → 거부 → 음소거로 재시도
    expect(video.muted).toBe(true)
    // 폴백된 사실이 화면에 보여야 한다 — 사선 아이콘 + 복구 방법을 적은 라벨(477)
    const mute = wrapper.get('button[data-mute]')
    expect(mute.attributes('aria-label')).toContain('슬라이더로 소리를 켜세요')
    expect(mute.attributes('aria-disabled')).toBe('true') // 눌러도 안 켜진다는 사실을 알린다
  })

  it('폴백 뒤 슬라이더를 움직이면 소리가 켜진다 — 그 조작이 제스처가 된다 (477)', async () => {
    mediaPlay.mockRejectedValueOnce(notAllowed())
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises()

    const video = wrapper.find('video').element as HTMLVideoElement
    expect(video.muted).toBe(true) // 정책에 막혀 폴백된 상태

    // 스피커는 음소거 전용이라 되돌리는 문이 슬라이더뿐이다(477)
    await wrapper.get('[aria-label="음량"]').setValue('40')

    expect(video.muted).toBe(false) // 사용자 제스처로 오디오 켜짐
    expect(video.volume).toBeCloseTo(0.4)
    expect(wrapper.get('button[data-mute]').attributes('aria-label')).toBe('음소거') // 스피커 모양으로 복귀
  })

  it('이미 송출 중인 방 입장(connect resolve 전 streamCreated)에서 no-publisher로 덮이지 않는다 (리뷰 1)', async () => {
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises() // 토큰·import·session.on 등록, connect는 pending

    // 실서버 순서: connect가 resolve되기 전에 기존 퍼블리셔의 streamCreated가 동기 emit된다
    h.emit('streamCreated', { stream: {} })
    h.finishConnect()
    await flushPromises()

    // connect resolve 직후의 no-publisher가 방금 받은 스트림을 덮지 않아야 한다
    expect(wrapper.text()).not.toContain('아직 송출')

    play(wrapper)
    await flushPromises()
    expect(liveControl(wrapper).exists()).toBe(true) // 재생 시작 → live → 컨트롤 노출
  })

  it('빈 방 입장(스트림 없이 connect 완료)이면 송출 전 안내가 뜬다 (S15P11A704-431)', async () => {
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises() // 토큰·import·session.on 등록, connect는 pending

    // 판매자가 아직 송출하지 않은 방 — streamCreated 없이 connect만 resolve된다.
    // 위 「이미 송출 중인 방 입장」이 잠근 조건의 반대 분기다(둘이 짝이라 함께 있어야 양쪽이 고정된다).
    h.finishConnect()
    await flushPromises()

    expect(wrapper.text()).toContain('판매자가 아직 송출을 시작하지 않았어요')
  })
})

// 자동재생 정책 폴백 안내 (S15P11A704-479).
//
// 폴백된 사실의 유일한 표시가 클러스터 안 사선 아이콘이었고, 그 클러스터는 hover·focus에서만 보인다(464).
// 즉 정책에 막힌 사용자는 마우스를 올릴 때까지 이유를 몰랐다 — 첫 방문·시크릿 창은 대체로 막힌다.
// 여기서 잠그는 것은 「언제 뜨고 언제 사라지는가」다. 조건이 어긋나면 잔소리가 되거나(스스로 음소거했는데
// 안내가 뜬다) 원래 문제로 돌아간다(폴백인데 안 뜬다).
describe('LiveStage 정책 폴백 안내 (S15P11A704-479)', () => {
  /** 정책이 막은 상황을 만든다 — 첫 play()만 거부하면 tryPlay가 음소거로 재시도한다. */
  async function blocked() {
    mediaPlay.mockRejectedValueOnce(notAllowed())
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises()
    return {
      wrapper,
      video: wrapper.find('video').element as HTMLVideoElement,
      notice: () => wrapper.find('[data-policy-notice]'),
    }
  }

  it('정책에 막히면 클러스터 밖에 안내가 뜬다', async () => {
    const { wrapper, notice } = await blocked()

    expect(notice().exists()).toBe(true)
    expect(notice().text()).toContain('소리가 꺼진 채 재생 중이에요')
    // **클러스터 밖**이어야 한다 — 안이면 hover 전까지 안 보여 원래 문제가 그대로다
    expect(wrapper.get('[data-live-controls]').element.contains(notice().element)).toBe(false)
  })

  it('안내의 「소리 켜기」가 원클릭으로 복구한다', async () => {
    const { wrapper, video, notice } = await blocked()
    expect(video.muted).toBe(true)

    await notice().get('button').trigger('click') // 첫 버튼이 「소리 켜기」

    expect(video.muted).toBe(false)
    expect(video.volume).toBeCloseTo(1) // 폴백으로 0이 된 음량도 함께 되돌린다
    expect(wrapper.find('[data-policy-notice]').exists()).toBe(false) // 할 일을 다하면 사라진다
  })

  it('닫기로 접을 수 있다 — 음소거를 유지하고 싶은 사용자를 막지 않는다', async () => {
    const { wrapper, video, notice } = await blocked()

    await notice().get('[aria-label="안내 닫기"]').trigger('click')

    expect(wrapper.find('[data-policy-notice]').exists()).toBe(false)
    expect(video.muted).toBe(true) // 닫아도 음소거는 그대로다
  })

  it('슬라이더로 소리를 켜도 안내가 사라진다', async () => {
    const { wrapper, notice } = await blocked()
    expect(notice().exists()).toBe(true)

    await wrapper.get('[aria-label="음량"]').setValue('40')

    expect(wrapper.find('[data-policy-notice]').exists()).toBe(false)
  })

  it('스스로 음소거한 경우에는 뜨지 않는다 — 자기가 한 일에 안내는 잔소리다', async () => {
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises()

    await wrapper.get('button[data-mute]').trigger('click') // 사용자가 직접 음소거

    expect(wrapper.find('[data-policy-notice]').exists()).toBe(false)
  })

  it('안내가 뜬 상태에서 스피커를 눌러도 아무 일이 없다 — 480 가드와의 조합', async () => {
    // 안내가 떠 있다 = 폴백됐다 = 무음이다. 그 상태의 스피커는 aria-disabled이고 핸들러가 조기 반환한다.
    // 그래서 「폴백 뒤 스피커로 음소거를 확정」이라는 경로는 존재하지 않는다 —
    // 처음엔 그 경로에서 안내를 접는 줄을 넣었는데, 도달할 수 없어 지웠다(뮤테이션이 안 잡혀서 발견했다).
    const { wrapper, video, notice } = await blocked()

    await wrapper.get('button[data-mute]').trigger('click')

    expect(notice().exists()).toBe(true) // 안내는 그대로 — 실수로 접히지 않는다
    expect(video.muted).toBe(true)
  })

  it('방을 옮기면 안내가 따라오지 않는다 — 새 방은 막힌 적이 없다 (!232 리뷰 1)', async () => {
    // connect()가 플래그를 내리지 않으면, 폴백을 겪은 방에서 안내를 **닫지 않고** 옮겼을 때
    // 새 방이 live가 되는 순간 안내가 다시 뜬다 — 원인 표시가 거짓이고, 방마다 따라온다.
    const { wrapper, notice } = await blocked()
    expect(notice().exists()).toBe(true)

    await wrapper.setProps({ roomId: 2 }) // 방 전환 → start() → connect()
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises()

    expect(wrapper.find('[data-policy-notice]').exists()).toBe(false)

    // ⚠️ 여기까지만 보면 **513을 고친 뒤 판별력이 사라진다**(!232 재리뷰). connect()가 볼륨을 100으로
    // 되돌리면 silent가 false가 되어, mutedByPolicy가 남아 있어도 v-if의 silent 가드가 안내를 가린다.
    // 그래서 무음 상태를 다시 만들어 **플래그 자체가 내려갔는지**를 확인한다.
    await wrapper.get('button[data-mute]').trigger('click')
    expect(wrapper.find('[data-policy-notice]').exists()).toBe(false)
  })

  it('방을 옮기면 음량도 기본값으로 돌아온다 (S15P11A704-513)', async () => {
    // 477의 mute()가 muted와 슬라이더를 같이 내리므로, 되돌릴 때도 같이 올려야 「소리 켬」이 실제로
    // 복원된다. muted만 풀면 볼륨 0이 남아 새 방에서 슬라이더를 직접 올려야 소리가 난다.
    const { wrapper, video } = await blocked() // 폴백으로 muted=true, 볼륨 0
    expect(video.volume).toBe(0)

    await wrapper.setProps({ roomId: 2 })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises()

    expect(video.muted).toBe(false)
    expect(video.volume).toBeCloseTo(1)
    expect((wrapper.get('[aria-label="음량"]').element as HTMLInputElement).value).toBe('100')
  })

  it('사용자가 고른 음량은 방을 옮겨도 유지된다 (!233 리뷰 1)', async () => {
    // 고쳐야 했던 것은 「폴백·음소거가 남긴 0」뿐이다. 무조건 100으로 올리면 30%로 맞춰 둔 사용자가
    // 방을 옮길 때마다 소리가 갑자기 커진다 — 원래 버그와 다른 종류의 나쁜 동작이다.
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises()
    await wrapper.get('[aria-label="음량"]').setValue('30')

    await wrapper.setProps({ roomId: 2 })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises()

    const video = wrapper.find('video').element as HTMLVideoElement
    expect(video.volume).toBeCloseTo(0.3)
    expect((wrapper.get('[aria-label="음량"]').element as HTMLInputElement).value).toBe('30')
  })

  it('재연결 중에는 접는다 — 그때 무음의 이유는 정책이 아니라 회선이다', async () => {
    // 재연결 배지와 같은 행(top-16 중앙)이라 겹치기도 하고, 배지가 이미 이유를 말한다.
    const { wrapper, notice } = await blocked()
    expect(notice().exists()).toBe(true)

    h.emit('reconnecting')
    await flushPromises()

    expect(wrapper.text()).toContain('재연결 중')
    expect(wrapper.find('[data-policy-notice]').exists()).toBe(false)
  })
})

describe('LiveStage 시청 컨트롤 — 음소거·전체화면 (S15P11A704-423)', () => {
  it('스피커는 음소거 전용이고, 되돌리는 것은 슬라이더다 (S15P11A704-477)', async () => {
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises()

    const video = wrapper.find('video').element as HTMLVideoElement
    expect(video.muted).toBe(false) // 476: 기본은 소리 켬

    await wrapper.get('button[data-mute]').trigger('click')
    expect(video.muted).toBe(true)
    // 눌러서 다시 켜지지 않는다 — aria-disabled로 알리고 라벨에 복구 방법을 적는다(477·480)
    expect(wrapper.get('button[data-mute]').attributes('aria-disabled')).toBe('true')

    await wrapper.get('[aria-label="음량"]').setValue('50')
    expect(video.muted).toBe(false)
    expect(wrapper.get('button[data-mute]').attributes('aria-disabled')).toBe('false')
  })

  it('전체화면 버튼이 스테이지 루트에 requestFullscreen을 요청한다', async () => {
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises()

    // jsdom엔 Fullscreen API가 없다 — 루트에 직접 심어 호출 대상까지 확인한다
    const requestFullscreen = vi.fn(() => Promise.resolve())
    Object.assign(wrapper.element, { requestFullscreen })

    await wrapper.get('[aria-label="전체화면"]').trigger('click')
    expect(requestFullscreen).toHaveBeenCalledOnce()
  })

  it('브라우저 UI(ESC·F11)로 나가도 라벨이 실제 상태를 따라간다', async () => {
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises()

    const setFullscreenElement = (el: Element | null) =>
      Object.defineProperty(document, 'fullscreenElement', { configurable: true, get: () => el })
    try {
      // 클릭이 아니라 이벤트로 상태가 선다 — 클릭 시점에 뒤집으면 브라우저 UI 이탈과 어긋난다
      setFullscreenElement(wrapper.element)
      document.dispatchEvent(new Event('fullscreenchange'))
      await flushPromises()
      expect(wrapper.find('[aria-label="전체화면 종료"]').exists()).toBe(true)

      setFullscreenElement(null)
      document.dispatchEvent(new Event('fullscreenchange'))
      await flushPromises()
      expect(wrapper.find('[aria-label="전체화면"]').exists()).toBe(true)
    } finally {
      Reflect.deleteProperty(document, 'fullscreenElement')
    }
  })

  it('전체화면을 지원하지 않는 브라우저(iPhone Safari)에선 버튼을 감춘다', async () => {
    // requestFullscreen이 없으면 옵셔널 호출이 조용히 통과해 눌러도 아무 일이 없다 — 그래서 감춘다
    setFullscreenEnabled(false)
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises()

    expect(wrapper.find('[aria-label="전체화면"]').exists()).toBe(false)
    // 나머지 컨트롤은 그대로 살아 있어야 한다 — 클러스터 전체가 사라지면 음소거를 못 한다
    expect(wrapper.find('button[data-mute]').exists()).toBe(true)
  })

  it('내가 전체화면이 아니면 exitFullscreen을 부르지 않는다 — 남의 전체화면을 끄지 않는다', async () => {
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises()

    // 다른 요소가 전체화면인 상황 — isFullscreen은 false다(내 루트가 아니므로)
    const exitFullscreen = vi.fn(() => Promise.resolve())
    const requestFullscreen = vi.fn(() => Promise.resolve())
    Object.assign(document, { exitFullscreen })
    Object.assign(wrapper.element, { requestFullscreen })
    Object.defineProperty(document, 'fullscreenElement', {
      configurable: true,
      get: () => document.body,
    })
    try {
      await wrapper.get('[aria-label="전체화면"]').trigger('click')
      expect(exitFullscreen).not.toHaveBeenCalled()
      expect(requestFullscreen).toHaveBeenCalledOnce()
    } finally {
      Reflect.deleteProperty(document, 'fullscreenElement')
      Reflect.deleteProperty(document, 'exitFullscreen')
    }
  })

  // 423은 「목 모드엔 오디오 컨트롤이 없다 — 눌러도 무반응인 버튼을 안 만든다」였다.
  // 475에서 뒤집혔다: 그 결정 때문에 목 모드에서 배치·펼침·키보드를 확인할 방법이 함께 사라져 있었고,
  // 확인 비용이 「소리 안 나는 컨트롤이 보인다」는 위험보다 컸다. 감추려면 ?audiodemo=0이다.
  it('목 모드에도 오디오 컨트롤이 뜬다 — 확인 경로다 (S15P11A704-475)', async () => {
    h.isMock.value = true
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()

    expect(wrapper.find('button[data-mute]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="음량"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="전체화면"]').exists()).toBe(true) // 플레이스홀더 확대는 가능
  })

  it('보일 컨트롤이 하나도 없으면 클러스터 래퍼도 렌더하지 않는다 (!178 리뷰 nit 3)', async () => {
    // iPhone 목 모드 + 오디오를 끈 상태 — canShowControls는 true(status='mock')지만 자식 둘의 조건은
    // 모두 false다: 전체화면은 표준·webkit 둘 다 없고, 오디오는 ?audiodemo=0으로 껐다(hasStream도 false).
    // 래퍼가 자기 조건을 따로 갖고 있으면 자식 없는 빈 div가 남는다.
    // 475로 목 오디오가 기본 켬이 되어 이 상태를 만들려면 쿼리로 꺼야 한다 — 가드 자체는 그대로 유효하다.
    setFullscreenEnabled(false)
    h.isMock.value = true
    currentQuery.value = { audiodemo: '0' }
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()

    expect(wrapper.find('[data-live-controls]').exists()).toBe(false)
  })
})

// 음량 슬라이더 (S15P11A704-465) — 유튜브 방식. 판정이 muted 하나가 아니라 (muted, volume) 둘로 갈려서
// 경계가 여럿이다: 0까지 내린 무음, 그 상태의 스피커 복구, 슬라이더 조작의 음소거 해제.
// 그 조합이 어긋나면 "버튼을 눌렀는데 아무 일도 안 일어난다"로만 드러나므로 여기서 잠근다.
describe('LiveStage 음량 슬라이더 (S15P11A704-465)', () => {
  async function live() {
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises()
    return {
      wrapper,
      video: wrapper.find('video').element as HTMLVideoElement,
      slider: () => wrapper.get('[aria-label="음량"]'),
      /** 슬라이더가 지금 가리키는 값 — 화면에 보이는 바 위치다. */
      sliderValue: () => (wrapper.get('[aria-label="음량"]').element as HTMLInputElement).value,
      speaker: () => wrapper.get('button[data-mute]'),
    }
  }

  /** 슬라이더를 특정 퍼센트로 움직인다 — 실제 range의 input 이벤트와 같은 경로. */
  async function drag(slider: ReturnType<Awaited<ReturnType<typeof live>>['slider']>, pct: number) {
    await slider.setValue(String(pct))
  }

  it('range로 만든다 — div로 만들면 키보드로 못 쓴다', async () => {
    const { slider } = await live()
    expect(slider().attributes('type')).toBe('range')
    // 리더에 읽히는 값이라 0~1이 아니라 퍼센트다(0.65보다 65가 낫다)
    expect(slider().attributes('min')).toBe('0')
    expect(slider().attributes('max')).toBe('100')
  })

  it('움직이면 <video>.volume에 반영된다', async () => {
    const { video, slider } = await live()
    await drag(slider(), 40)
    expect(video.volume).toBeCloseTo(0.4)
  })

  it('음소거된 상태에서 움직이면 음소거가 풀린다 — 465의 확정 결정(유튜브와 동일)', async () => {
    const { video, slider, speaker } = await live()
    await speaker().trigger('click') // 사용자가 음소거한 상태를 만든다(기본은 소리 켬 — 476)
    expect(video.muted).toBe(true)

    await drag(slider(), 60)

    // 음량만 올리고 muted가 남으면 소리가 안 나 고장으로 읽힌다. 슬라이더 조작이 곧 사용자 제스처다.
    expect(video.muted).toBe(false)
    expect(video.volume).toBeCloseTo(0.6)
  })

  it('0까지 내리면 무음 표시가 된다 — muted는 false여도 소리는 안 난다', async () => {
    const { wrapper, video, slider } = await live()
    await drag(slider(), 70)
    expect(wrapper.get('button[data-mute]').attributes('aria-label')).toBe('음소거')

    await drag(slider(), 0)

    // 여기서 아이콘이 「소리 나는 중」으로 남으면 화면이 거짓말을 한다
    expect(video.muted).toBe(false) // muted 플래그는 안 켰다 — 무음 판정은 volume 0이 한다
    expect(wrapper.get('button[data-mute]').attributes('aria-label')).toContain('음소거됨')
  })

  it('0으로 내린 뒤 다시 올리면 소리가 돌아온다 — 스피커는 되돌리지 않는다 (477)', async () => {
    const { video, slider, speaker } = await live()
    await drag(slider(), 70)
    await drag(slider(), 0)

    // 무음이므로 스피커는 눌리지 않는다 — 예전엔 이 클릭이 직전 음량으로 복구했는데 그 경로를 없앴다
    expect(speaker().attributes('aria-disabled')).toBe('true')

    await drag(slider(), 30)

    expect(video.muted).toBe(false)
    expect(video.volume).toBeCloseTo(0.3)
  })

  it('0으로 내린 것은 음소거 의사다 — 그때는 muted를 풀지 않는다', async () => {
    const { video, slider, speaker } = await live()
    await speaker().trigger('click') // 먼저 음소거
    expect(video.muted).toBe(true)

    await drag(slider(), 0)

    expect(video.muted).toBe(true) // 무음 의사를 뒤집지 않는다
  })

  it('스피커와 슬라이더가 같은 hover 그룹 안에 있다', async () => {
    // 스피커만 hover 소스로 두면 포인터가 슬라이더로 넘어가는 순간 접혀 조절이 불가능하다.
    // 그래서 펼침 조건은 **둘을 함께 감싼 그룹**의 hover여야 한다.
    const { wrapper } = await live()
    const speaker = wrapper.get('button[data-mute]').element
    const box = wrapper.get('[aria-label="음량"]').element.parentElement as HTMLElement // 폭을 접는 span
    const group = box.parentElement as HTMLElement

    expect(group.classList.contains('group/volume')).toBe(true)
    expect(group.contains(speaker)).toBe(true)
    expect(box.className).toContain('group-hover/volume:w-20')
  })

  it('접는 것은 hover 가능한 환경에서만 — 464와 같은 축이다', async () => {
    // 뷰포트 폭으로 가르면 iPad 가로에서 음량을 조절할 방법이 사라진다(464 리뷰에서 확인한 함정).
    const { wrapper } = await live()
    const cls = (wrapper.get('[aria-label="음량"]').element.parentElement as HTMLElement).className
    const hiding = cls.split(/\s+/).filter((c) => /(^|:)(w-0|opacity-0)$/.test(c))
    expect(hiding.length).toBeGreaterThan(0)
    expect(hiding.every((c) => c.startsWith('[@media(hover:hover)]:'))).toBe(true)
    // 키보드로 탭이 닿는 순간 펼쳐져야 한다 — 이건 미디어 조건 없이
    expect(cls).toContain('focus-within:w-20')
  })

  // 음소거와 슬라이더 위치 동기화 (S15P11A704-477).
  // 465가 muted와 volumePercent를 따로 둔 결과, 스피커로 음소거하면 「음량 70인데 무음」이라는
  // 모순된 화면이 남았다. 눈에는 바로 보이지만 단정이 없으면 리팩터에서 조용히 되돌아간다.
  describe('음소거하면 슬라이더도 0으로 (S15P11A704-477)', () => {
    it('무음 상태의 스피커는 눌러도 아무 일이 없다 — 막는 것이 브라우저가 아니라 핸들러다 (480)', async () => {
      // disabled를 쓰면 브라우저가 클릭을 막아 주지만, 그 대가로 탭 순서에서 빠져 복구 안내 라벨을
      // 읽을 경로가 사라진다(툴팁도 안 뜬다). aria-disabled로 바꿨으므로 클릭은 우리가 막는다.
      //
      // **슬라이더로 0까지 내려서 무음이 된 상태**로 확인한다 — 그 경로에서만 가드의 유무가 드러난다.
      // 스피커로 음소거한 뒤 또 누르는 경로는 mute()가 멱등이라 가드를 지워도 차이가 없다(뮤테이션으로 확인).
      const { video, slider, speaker, sliderValue } = await live()
      await drag(slider(), 0)
      expect(video.muted).toBe(false) // muted 플래그는 안 켜진 무음 상태
      expect(speaker().attributes('aria-disabled')).toBe('true')

      await speaker().trigger('click')

      expect(video.muted).toBe(false) // 눌러도 상태가 바뀌지 않는다
      expect(sliderValue()).toBe('0')
    })

    it('무음 상태에서도 버튼이 포커스를 받고 안내가 읽힌다 (480)', async () => {
      // 이게 disabled를 버린 이유다 — 아이콘만 있는 버튼이라 aria-label이 유일한 설명인데,
      // 정작 그 설명이 필요한 상태에서만 닿을 수 없었다.
      const { speaker } = await live()
      await speaker().trigger('click')

      const el = speaker().element as HTMLButtonElement
      expect(el.disabled).toBe(false) // 탭 순서에 남는다
      expect(speaker().attributes('aria-label')).toContain('슬라이더로 소리를 켜세요')
      expect(speaker().attributes('title')).toContain('슬라이더로 소리를 켜세요') // 툴팁도 뜬다
      // 눌리지 않는데 hover 강조가 남으면 그것도 거짓말이다
      expect(speaker().classes()).toContain('aria-disabled:hover:bg-transparent')
    })

    it('스피커로 음소거하면 바가 0으로 내려간다', async () => {
      const { video, slider, speaker, sliderValue } = await live()
      await drag(slider(), 70)
      expect(sliderValue()).toBe('70')

      await speaker().trigger('click')

      expect(sliderValue()).toBe('0') // 화면의 바
      expect(video.volume).toBe(0) // 실제 요소
      expect(video.muted).toBe(true) // 정책이 보는 값은 따로 유지된다
    })

    it('바를 움직이면 자동으로 스피커 모양으로 돌아온다 — 누르는 버튼은 없다', async () => {
      const { video, slider, speaker, sliderValue } = await live()
      await drag(slider(), 70)
      await speaker().trigger('click') // 음소거 — 바가 0으로
      expect(sliderValue()).toBe('0')
      expect(speaker().attributes('aria-label')).toContain('음소거됨')

      await drag(slider(), 55) // 되돌리는 문은 이것뿐이다

      expect(video.muted).toBe(false)
      expect(video.volume).toBeCloseTo(0.55)
      expect(speaker().attributes('aria-label')).toBe('음소거') // 사선 없는 스피커로 복귀
      expect(speaker().attributes('aria-disabled')).toBe('false')
    })

    it('자동재생 폴백으로 음소거된 경우도 바가 0이다 (476과의 조합)', async () => {
      // 폴백은 정책이 막아 음소거로 시작한 상태다 — 그때도 바가 100에 남아 있으면 같은 모순이 된다.
      mediaPlay.mockRejectedValueOnce(notAllowed())
      h.isMock.value = false
      const wrapper = mount(LiveStage, { props: { roomId: 1 } })
      await flushPromises()
      h.emit('streamCreated', { stream: {} })
      await flushPromises()

      expect((wrapper.get('[aria-label="음량"]').element as HTMLInputElement).value).toBe('0')
      expect(wrapper.get('button[data-mute]').attributes('aria-disabled')).toBe('true')
    })
  })

  it('목 모드는 무음으로 시작한다 — 소리가 없는데 사선 없는 스피커면 거짓말이다 (!185 리뷰 2)', async () => {
    // 목 모드는 미디어 서버가 없어 소리 자체가 없다. tryPlay도 돌지 않아(streamCreated가 없다)
    // muted 기본값이 그대로 남는데, 그게 false면 「소리 나는 중」으로 그려진다.
    // 465가 「슬라이더 0이면 사선을 켠다」로 세운 기준과 같은 종류다 — 하필 이 화면이 시연 화면이다.
    h.isMock.value = true
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()

    expect((wrapper.get('[aria-label="음량"]').element as HTMLInputElement).value).toBe('0')
    const mute = wrapper.get('button[data-mute]')
    expect(mute.attributes('aria-label')).toContain('음소거됨')
    expect(mute.attributes('aria-disabled')).toBe('true')
  })

  it('목 모드에도 슬라이더가 뜬다 — 소리는 안 나지만 배치·조작을 확인해야 한다 (475)', async () => {
    h.isMock.value = true
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()
    expect(wrapper.find('[aria-label="음량"]').exists()).toBe(true)
  })

  // 위 결정의 대가: **목 모드에서 이 UI를 볼 방법이 없다**(S15P11A704-470).
  // 실 미디어 서버 없이 배치·펼침·키보드를 확인해야 하는데 화면에 없으면 확인할 수가 없다.
  // 여기서 볼 것은 **배선**뿐이다 — `?k`(null)·`?k=`·`?k=0` 같은 파싱 변형과 기본값은
  // shared/lib/useMockDemoFlag.spec.ts가 한곳에서 덮는다(!184 리뷰 2).
  describe('목 모드 스위치 ?audiodemo (S15P11A704-470 → 475에서 기본 켬)', () => {
    it('?audiodemo=0으로 감출 수 있다 — 시연 링크용 끄는 표기', async () => {
      h.isMock.value = true
      currentQuery.value = { audiodemo: '0' }
      const wrapper = mount(LiveStage, { props: { roomId: 1 } })
      await flushPromises()

      expect(wrapper.find('[aria-label="음량"]').exists()).toBe(false)
      expect(wrapper.find('button[data-mute]').exists()).toBe(false)
    })

    it('실 모드에선 목 스위치가 아무 힘도 없다 — 스트림 전에는 안 뜬다', async () => {
      // 실 서버에서는 "소리가 있을 때만"이라는 원래 조건이 그대로 지배해야 한다.
      h.isMock.value = false
      currentQuery.value = { audiodemo: '1' }
      const wrapper = mount(LiveStage, { props: { roomId: 1 } })
      await flushPromises()
      expect(wrapper.find('[aria-label="음량"]').exists()).toBe(false)
    })
  })
})

// 우하단 hover 클러스터 (S15P11A704-464).
//
// 여기만 **클래스 문자열을 단정한다.** 노출·위치가 순수 CSS(hover·미디어쿼리·z 경쟁)라 jsdom이 계산해 주지
// 않기 때문이다. 그러면서 깨지면 조용한 것들이다 — hover가 안 걸리면 컨트롤이 영구히 숨고, z가 낮으면
// prodbar 뒤로 들어가 눌리지 않는다. 둘 다 테스트 없이는 실기기에서만 드러난다.
// 리팩터로 클래스가 바뀌면 여기가 먼저 깨지는 것이 의도다: 그때 아래 이유를 다시 읽고 옮기면 된다.
describe('LiveStage 우하단 hover 클러스터 (S15P11A704-464)', () => {
  async function cluster() {
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises()
    return wrapper.get('[data-live-controls]').classes()
  }

  it('우하단 코너에 둔다 — lg는 가로 한 줄, 좁은 화면은 세로 스택 (S15P11A704-475)', async () => {
    // 464는 와이어프레임의 `.stage:hover` 상승값(-62px)까지 따라 하단 78px에 뒀다.
    // 475에서 그 상승을 버렸다 — 전체화면을 더 내려 달라는 요청이고, 코너가 와이어프레임의 기본 자리다.
    //
    // 배치가 폭에 따라 갈리는 이유는 prodbar 예약 때문이다. 가로 한 줄이면 클러스터(최대 214px) 자리를
    // 프레임이 비워 줘야 하는데, 360px 화면의 스테이지는 336px뿐이라 제목에 20px만 남는다.
    // 그래서 좁은 화면은 오디오를 prodbar 위로 올려 세로로 쌓는다. 짝: LiveLayout.spec의 예약 테스트.
    const cls = await cluster()
    expect(cls).toContain('right-4')
    expect(cls).toContain('bottom-4')
    expect(cls).toContain('flex-col') // 좁은 화면 기본값
    expect(cls).toContain('lg:flex-row') // 요청받은 모습 — 오디오가 전체화면 왼쪽
    expect(cls.some((c) => c.includes('translate-y'))).toBe(false) // 62px 상승 제거
    expect(cls).not.toContain('top-16') // 423의 우상단 자리
  })

  it('전체화면 버튼이 없으면 좁은 화면에서 아래 칸을 비운다 (!185 리뷰 1)', async () => {
    // 세로 스택은 **전체화면 버튼이 아래 칸을 채워 준다는 전제**로만 안전하다. 그게 없으면 오디오 알약이
    // 코너(하단 16px)로 내려와 prodbar 제목을 덮는다 — 프레임 예약은 전체화면 몫(56px)뿐이고 알약은 120px다.
    // 도달 경로: connecting 구간, allowfullscreen 없는 iframe. 클래스 하나라 화면으로는 안 잡힌다.
    setFullscreenEnabled(false)
    h.isMock.value = true // 목 모드는 오디오가 기본 노출이라 「알약 단독」 조합이 된다
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()

    const cls = wrapper.get('[data-live-controls]').classes()
    expect(wrapper.find('[aria-label="전체화면"]').exists()).toBe(false) // 알약 단독 상태 확인
    // 전체화면(28px) + gap-3(12px) = 40px — 두 분기의 알약이 같은 높이(하단 56px)에 앉게 하는 값이다.
    // **gap과 pb를 함께 단정한다**(!206 리뷰 1): gap만 바꾸면 이 분기의 알약이 낮아져 상품명과 다시
    // 겹치는데, pb만 보면 테스트가 통과해 버린다. 물린 값은 짝으로 잠근다(이 파일의 다른 계약들처럼).
    expect(cls).toContain('pb-10')
    expect(cls).toContain('gap-3')
    expect(cls).toContain('lg:pb-0') // lg는 가로 한 줄 + 192px 예약이라 필요 없다
  })

  it('전체화면 버튼이 있으면 아래 칸을 비우지 않는다', async () => {
    // 짝이 되는 반대 분기 — 항상 pb-9면 컨트롤이 늘 한 칸 위에 떠 코너 배치가 아니게 된다.
    const cls = await cluster()
    expect(cls).not.toContain('pb-10')
  })

  it('prodbar보다 위 z를 쓴다 — 같으면 클릭까지 먹힌다', async () => {
    // 디자인은 fsbtn이 prodbar 뒤 형제라 z-index:6으로도 이기지만, 우리 컨트롤은 #video 슬롯 안이라 진다.
    // 지면 prodbar(pointer-events 유지)가 클릭을 가로채 눌러도 아무 일이 없다 — 423이 하단을 피한 이유다.
    expect(await cluster()).toContain('z-[7]')
  })

  it('hover 소스는 프레임의 group/stage다 — 루트를 쓰면 버튼에 닿기 직전에 사라진다', async () => {
    // prodbar는 이 컴포넌트의 형제라, 루트 hover를 쓰면 포인터가 prodbar 위로 들어간 순간 hover가 끊긴다.
    // 클러스터의 최종 위치가 prodbar 박스 안이므로 그 경로를 반드시 지난다. 프레임 그룹이어야 살아남는다.
    // 짝: LiveLayout.spec.ts의 「group/stage를 유지한다」.
    const cls = await cluster()
    expect(cls.some((c) => c.includes('group-hover/stage:opacity-100'))).toBe(true)
  })

  it('두 버튼에 포커스 링이 있다 — 드러나도 어디에 포커스가 있는지 보여야 한다', async () => {
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises()

    // 키보드가 이 컨트롤의 유일한 비-hover 경로다(hover에서만 뜨므로). 링이 없으면 탭으로 드러내 놓고도
    // 어느 버튼을 누르는지 알 수 없다 — 눈에 안 보이는 종류의 접근성 회귀다.
    for (const sel of ['button[data-mute]', '[aria-label="전체화면"]']) {
      expect(wrapper.get(sel).classes()).toContain('focus-visible:outline')
    }
  })

  it('숨김은 hover 가능한 환경에서만 — 뷰포트 폭으로 가르면 iPad에서 영구히 사라진다', async () => {
    // Tailwind v4는 group-hover를 @media (hover:hover)로 감싸 내보낸다(빌드 산출물로 확인).
    // 그래서 숨김을 `lg:`로 걸면 iPad 가로(1024px ≥ lg인데 hover는 없음)에서 숨김만 적용되고
    // 드러낼 hover가 영원히 오지 않는다 — 음소거를 풀 방법까지 사라진다.
    // 축을 폭이 아니라 hover 가능 여부로 맞춰야 한다.
    const cls = await cluster()
    const hiding = cls.filter((c) => /(^|:)(opacity-0|pointer-events-none)$/.test(c))
    expect(hiding.length).toBeGreaterThan(0)
    expect(hiding.every((c) => c.startsWith('[@media(hover:hover)]:'))).toBe(true)
  })

  it('키보드로도 드러난다 — focus-within이 없으면 접근성 회귀다', async () => {
    // hover만 걸면 키보드 사용자는 컨트롤에 영구히 닿지 못한다(음소거를 풀 방법이 사라진다).
    // opacity-0은 포커스를 막지 않으므로, 탭이 닿는 순간 이 규칙이 드러내 준다.
    // 숨김이 hover 환경 전용이라 이쪽엔 미디어 조건을 걸지 않는다 — 걸면 조건이 두 축으로 갈린다.
    const cls = await cluster()
    expect(cls).toContain('focus-within:opacity-100')
    expect(cls).toContain('focus-within:pointer-events-auto')
  })
})

// no-publisher는 세 곳에서 세워진다(:167 connect resolve · :133 streamDestroyed · :144 reconnected).
// 426은 그중 "받다가 소멸"(:133)만 stream-ended로 가른다 — 셋을 한 곳에서 짝으로 고정해,
// 나중에 한 진입점만 고치고 나머지를 놓치는 일을 막는다(!133 리뷰 참고사항).
describe('LiveStage 송출 전 vs 송출 중단 구분 (S15P11A704-426)', () => {
  it('받다가 소멸하면(:133) 송출 전이 아니라 중단 안내로 갈린다', async () => {
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises() // live — 보고 있던 상태

    // 판매자 unpublish·이탈 등. 내 회선 끊김(networkDisconnect)이 아니라 재연결 경로가 아니다.
    h.emit('streamDestroyed', { reason: 'unpublish' })
    await flushPromises()

    expect(wrapper.text()).toContain('판매자 송출이 중단됐어요')
    // 방금까지 보던 방송인데 "아직 시작 안 함"으로 안내하던 것이 이 티켓의 증상이다
    expect(wrapper.text()).not.toContain('아직 송출을 시작하지 않았어요')
  })

  it('중단 뒤 재송출되면 자동 복귀한다 — 문구의 "다시 시작되면"이 사실이어야 한다', async () => {
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises()

    h.emit('streamDestroyed', { reason: 'unpublish' })
    await flushPromises()
    expect(wrapper.text()).toContain('중단됐어요')

    h.emit('streamCreated', { stream: {} }) // 판매자 재송출
    play(wrapper)
    await flushPromises()

    expect(wrapper.text()).not.toContain('중단됐어요')
    expect(liveControl(wrapper).exists()).toBe(true) // live 복귀 → 컨트롤 노출
  })

  // 아래 둘은 "정상 종료"와 "회선 재연결"이 겹칠 때의 경로다(!134 리뷰 1).
  // reconnecting이 status를 무조건 덮어 'stream-ended'가 지워지므로, 복귀 판정은 hasStream이 아니라
  // everHadStream("받은 적 있나")으로 해야 한다. 순서를 뒤집은 두 케이스를 함께 잠근다.
  it('중단된 뒤 회선 재연결이 돌아도 중단 안내를 유지한다 (everHadStream)', async () => {
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises() // live

    h.emit('streamDestroyed', { reason: 'unpublish' }) // 정상 종료 → stream-ended
    await flushPromises()

    // 그 뒤 내 회선이 일시 끊겼다 복구된다 — 판매자 송출 종료와는 별개의 사이클이다
    h.emit('reconnecting')
    h.emit('reconnected')
    await flushPromises()

    expect(wrapper.text()).toContain('중단됐어요')
    expect(wrapper.text()).not.toContain('아직 송출을 시작하지 않았어요')
  })

  it('재연결 중에 판매자가 송출을 멈추면 복귀 후에도 중단 안내다', async () => {
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises() // live

    h.emit('reconnecting')
    h.emit('streamDestroyed', { reason: 'unpublish' }) // 재연결 중 판매자 종료
    h.emit('reconnected')
    await flushPromises()

    expect(wrapper.text()).toContain('중단됐어요')
    expect(wrapper.text()).not.toContain('아직 송출을 시작하지 않았어요')
  })

  it('스트림을 받은 적 없이 재연결되면(:144) 송출 전 안내를 유지한다', async () => {
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()
    h.finishConnect() // 빈 방 — streamCreated 없이 connect만 완료
    await flushPromises()

    h.emit('reconnecting')
    h.emit('reconnected')
    await flushPromises()

    // hasStream이 선 적 없으므로 "받다가 끊김"이 아니다 — 여전히 송출 전이다
    expect(wrapper.text()).toContain('판매자가 아직 송출을 시작하지 않았어요')
    expect(wrapper.text()).not.toContain('중단됐어요')
  })

  it('내 회선 끊김(networkDisconnect)은 중단 안내로 새지 않는다 — 재연결 경로 유지', async () => {
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises()

    h.emit('reconnecting')
    h.emit('streamDestroyed', { reason: 'networkDisconnect' })
    await flushPromises()

    // 프레임을 남긴 채 재연결 배지로 안내한다. 판매자는 계속 송출 중일 수 있으므로 중단이라 말하면 안 된다.
    expect(wrapper.text()).toContain('재연결 중')
    expect(wrapper.text()).not.toContain('중단됐어요')
  })
})

// iPhone Safari 전체화면 폴백 (S15P11A704-441).
// 표준 API가 없으면 423에서 버튼을 감췄는데(!136 리뷰 1), 그 결과 iPhone엔 전체화면이 아예 없었다.
// iPhone은 <video>의 비표준 webkitEnterFullscreen()만 준다 — 대상이 스테이지가 아니라 영상이다.
describe('LiveStage iPhone 전체화면 폴백 (S15P11A704-441)', () => {
  // 표준 미지원 + video에 webkit API가 있는 상태를 만든다. 지원 판정은 loadedmetadata에서 하므로 그때 발화시킨다.
  const iphone = async () => {
    setFullscreenEnabled(false)
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises()

    const video = wrapper.find('video').element
    const webkitEnterFullscreen = vi.fn()
    Object.assign(video, { webkitEnterFullscreen })
    video.dispatchEvent(new Event('loadedmetadata'))
    await flushPromises()
    return { wrapper, webkitEnterFullscreen }
  }

  it('표준 API가 없어도 webkit 폴백이 있으면 버튼을 다시 노출한다', async () => {
    const { wrapper } = await iphone()
    expect(wrapper.find('[aria-label="전체화면"]').exists()).toBe(true)
  })

  it('폴백 경로는 스테이지가 아니라 video를 대상으로 한다', async () => {
    const { wrapper, webkitEnterFullscreen } = await iphone()
    const requestFullscreen = vi.fn(() => Promise.resolve())
    Object.assign(wrapper.element, { requestFullscreen })

    await wrapper.get('[aria-label="전체화면"]').trigger('click')

    expect(webkitEnterFullscreen).toHaveBeenCalledOnce()
    expect(requestFullscreen).not.toHaveBeenCalled() // 표준 경로로 새지 않는다
  })

  it('표준 API가 있으면 폴백을 쓰지 않는다 — 스테이지 전체화면이 더 많은 걸 담는다', async () => {
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises()

    const video = wrapper.find('video').element
    const webkitEnterFullscreen = vi.fn()
    Object.assign(video, { webkitEnterFullscreen })
    video.dispatchEvent(new Event('loadedmetadata'))
    const requestFullscreen = vi.fn(() => Promise.resolve())
    Object.assign(wrapper.element, { requestFullscreen })
    await flushPromises()

    await wrapper.get('[aria-label="전체화면"]').trigger('click')

    expect(requestFullscreen).toHaveBeenCalledOnce()
    expect(webkitEnterFullscreen).not.toHaveBeenCalled()
  })

  // 433으로 프레임이 전체화면 대상을 슬롯 프롭으로 내주게 됐다. 표준 경로는 그것을 쓰지만
  // iOS 폴백의 대상은 <video>다 — 프레임이 준 컨테이너로는 iPhone 전체화면에 들어갈 수 없다.
  it('프레임이 대상을 내줘도 iOS 폴백은 video를 쓴다 (433과의 조합)', async () => {
    setFullscreenEnabled(false)
    h.isMock.value = false
    const frameTarget = document.createElement('div')
    const frameRequest = vi.fn(() => Promise.resolve())
    Object.assign(frameTarget, { requestFullscreen: frameRequest })

    const wrapper = mount(LiveStage, { props: { roomId: 1, fullscreenTarget: frameTarget } })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises()

    const video = wrapper.find('video').element
    const webkitEnterFullscreen = vi.fn()
    Object.assign(video, { webkitEnterFullscreen })
    video.dispatchEvent(new Event('loadedmetadata'))
    await flushPromises()

    await wrapper.get('[aria-label="전체화면"]').trigger('click')

    expect(webkitEnterFullscreen).toHaveBeenCalledOnce()
    expect(frameRequest).not.toHaveBeenCalled() // 프레임 컨테이너로 새지 않는다
  })

  it('표준도 webkit도 없으면 여전히 버튼이 없다 — 423의 결론을 유지한다', async () => {
    setFullscreenEnabled(false)
    h.isMock.value = false
    const wrapper = mount(LiveStage, { props: { roomId: 1 } })
    await flushPromises()
    h.emit('streamCreated', { stream: {} })
    play(wrapper)
    await flushPromises()

    expect(wrapper.find('[aria-label="전체화면"]').exists()).toBe(false)
  })
})
