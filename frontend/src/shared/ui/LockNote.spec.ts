import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import LockNote from './LockNote.vue'

// 이 파일의 실질적 목적은 **하네스 검증**이다 — jsdom 환경과 `.vue` SFC 트랜스폼이
// 실제로 붙어 있는지 확인한다. 설정이 깨지면 여기서 먼저 터진다.
describe('LockNote', () => {
  it('넘긴 문구를 그대로 렌더한다', () => {
    const wrapper = mount(LockNote, { props: { text: '입찰로 참여 중이에요' } })
    expect(wrapper.text()).toBe('입찰로 참여 중이에요')
  })

  it('role="status"로 노출한다 — 잠긴 패널을 숨기지 않고 사유를 읽히게 하는 게 요점', () => {
    const wrapper = mount(LockNote, { props: { text: '예측은 잠겼어요' } })
    expect(wrapper.attributes('role')).toBe('status')
  })
})
