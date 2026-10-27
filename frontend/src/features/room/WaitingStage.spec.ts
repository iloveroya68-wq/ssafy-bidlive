import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useLiveStore } from '@/stores/live'
import WaitingStage from './WaitingStage.vue'

// 대기방 스테이지의 접근성 속성 (S15P11A704-422).
//
// 대기 인원 pill에 문장 전체를 감싼 aria-live="polite"가 걸려 있어, 인원이 바뀔 때마다
// "먼저 온 N명이 채팅 중이에요"가 통째로 재낭독됐다 — 목 드라이버는 5.2초마다 인원을 올리고
// (mocks/waitingRoom viewerIntervalMs), 실 서버도 VIEWER_COUNT를 주기적으로 브로드캐스트한다.
// 대기 내내 같은 문장이 반복되는 소음이라 걷어냈고, 되돌아오면 이 테스트가 잡는다.
//
// 깨져도 화면은 멀쩡해 보이는 종류라(스크린리더로만 드러난다) 테스트로 고정할 값이다.

describe('WaitingStage 접근성 (S15P11A704-422)', () => {
  beforeEach(() => setActivePinia(createPinia()))

  const mountStage = (waitingCount: number | null) =>
    mount(WaitingStage, { props: { sellerName: '지훈', waitingCount } })

  it('대기 인원 pill을 라이브 리전으로 두지 않는다 — 주기 갱신마다 문장이 재낭독된다', () => {
    const wrapper = mountStage(12)

    const pill = wrapper.findAll('p').find((p) => p.text().includes('채팅 중이에요'))
    if (!pill) throw new Error('대기 인원 pill을 찾지 못함')
    expect(pill.attributes('aria-live')).toBeUndefined()
  })

  it('대기 인원 자체는 그대로 보여준다 — 접근성 수정이 정보를 지우지 않았는지', () => {
    const wrapper = mountStage(12)
    expect(wrapper.text()).toContain('12')
    expect(wrapper.text()).toContain('채팅 중이에요')
  })

  it('첫 이벤트를 못 받았으면(null) 인원을 표시하지 않는다', () => {
    const wrapper = mountStage(null)
    expect(wrapper.text()).not.toContain('채팅 중이에요')
  })
})

// 예정 시각 경과(overdue) 시 중앙 문구·칩 정합 (S15P11A704-427).
//
// 이전에는 중앙 30px이 overdue와 무관하게 "오늘 HH:MM 시작 예정"을 계속 띄워, 화면에서 가장 큰 글씨가
// 이미 지난 시각을 예정이라 말했다. 정정하는 문구는 가장 작은 상단 칩에만 있었다.
// 예정 시각이 지나는 건 흔한 정상 상황이므로(ROOM-05: 자동 시작 트리거가 아니다) 큰 글씨가 틀리면 안 된다.
//
// 역할 분리(437 논의): 칩은 상태, 중앙은 이유. 둘이 같은 말을 반복하지 않는다.
describe('WaitingStage 예정 시각 경과 (S15P11A704-427)', () => {
  beforeEach(() => setActivePinia(createPinia()))

  const MINUTE = 60_000

  // overdue는 useCountdown의 remainingMs로 판정되고, 그 값은 serverOffset이 null이면 계산되지 않는다
  // (규칙 5 — 서버 기준 없이 남은 시간을 지어내지 않는다). TIME_SYNC를 받은 상태를 만들어야 이 분기에 닿는다.
  const mountAt = (scheduledAt: number | null) => {
    useLiveStore().serverOffset = 0
    return mount(WaitingStage, { props: { sellerName: '지훈', waitingCount: 12, scheduledAt } })
  }

  it('예정 시각이 지나면 중앙이 이유를 말한다 — "시작 예정"을 더 이상 말하지 않는다', () => {
    const wrapper = mountAt(Date.now() - 5 * MINUTE)

    expect(wrapper.text()).toContain('예정 시각이 지났어요')
    expect(wrapper.text()).not.toContain('시작 예정')
  })

  it('칩은 상태를 말하고 중앙은 이유를 말한다 — 같은 말을 두 번 하지 않는다', () => {
    const wrapper = mountAt(Date.now() - 5 * MINUTE)

    expect(wrapper.text()).toContain('판매자 시작 대기 중') // 칩 = 상태
    expect(wrapper.text()).toContain('예정 시각이 지났어요') // 중앙 = 이유
    // 다음 일은 기존 안내가 잇는다(디자인 원본 문장, overdue에서도 유지)
    expect(wrapper.text()).toContain('판매자가 시작하면 자동으로')
  })

  it('예정 시각 전이면 그대로 예정을 보여준다 — 정상 경로를 바꾸지 않았는지', () => {
    const wrapper = mountAt(Date.now() + 30 * MINUTE)

    expect(wrapper.text()).toContain('시작 예정')
    expect(wrapper.text()).not.toContain('지났어요')
  })

  it('시작 시각이 없으면 미정으로 남는다 — overdue 판정 대상이 아니다', () => {
    const wrapper = mountAt(null)

    expect(wrapper.text()).toContain('시작 시각 미정')
    expect(wrapper.text()).not.toContain('지났어요')
  })
})
