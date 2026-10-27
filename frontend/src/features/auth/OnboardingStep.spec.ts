import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import OnboardingStep from './OnboardingStep.vue'
import * as membersApi from '@/shared/api/members'

// MEM-02 닉네임 사전 중복 확인 (S15P11A704-432).
// 여기서 잡는 건 전부 "깨져도 화면은 멀쩡해 보이는" 것들이다 —
// 디바운스가 풀리면 타이핑마다 요청이 나가고, 순서 가드가 없으면 옛 응답이 최신 판정을 덮는다.

const DEBOUNCE_MS = 400

function mountStep(props: Record<string, unknown> = {}) {
  return mount(OnboardingStep, { props })
}

async function type(wrapper: ReturnType<typeof mountStep>, value: string) {
  await wrapper.get('input').setValue(value)
}

describe('OnboardingStep 닉네임 사전 확인', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('입력이 멎을 때까지 요청하지 않는다 — 글자마다 쏘면 온보딩 한 번에 수십 건이다', async () => {
    const spy = vi.spyOn(membersApi, 'checkNicknameAvailability')
    const wrapper = mountStep()

    await type(wrapper, '택')
    await type(wrapper, '택림')
    await type(wrapper, '택림이')
    expect(spy).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(DEBOUNCE_MS)

    expect(spy).toHaveBeenCalledTimes(1)
    expect(spy).toHaveBeenCalledWith('택림이')
  })

  it('길이가 벗어난 값은 확인하지 않는다 — 서버가 어차피 형식 오류로 답한다', async () => {
    const spy = vi.spyOn(membersApi, 'checkNicknameAvailability')
    const wrapper = mountStep()

    await type(wrapper, '   ')
    await vi.advanceTimersByTimeAsync(DEBOUNCE_MS)

    expect(spy).not.toHaveBeenCalled()
  })

  it('사유별로 다른 문장을 보여준다 — 저장 API는 이 셋을 한 코드로 뭉쳐 보낸다', async () => {
    vi.spyOn(membersApi, 'checkNicknameAvailability').mockResolvedValue({
      nickname: '운영자',
      available: false,
      reason: 'PROHIBITED_WORD',
    })
    const wrapper = mountStep()

    await type(wrapper, '운영자')
    await vi.advanceTimersByTimeAsync(DEBOUNCE_MS)
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('사용할 수 없는 단어가 들어 있어요')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
  })

  it('늦게 도착한 옛 응답이 최신 판정을 덮지 않는다', async () => {
    // 첫 요청이 느리고 두 번째가 빠르면, 가드가 없을 때 "불가"가 마지막에 눌러앉는다
    vi.spyOn(membersApi, 'checkNicknameAvailability').mockImplementation(async (value: string) => {
      if (value === '준호') {
        await new Promise((r) => setTimeout(r, 500))
        return { nickname: value, available: false, reason: 'ALREADY_EXISTS' as const }
      }
      return { nickname: value, available: true, reason: null }
    })
    const wrapper = mountStep()

    await type(wrapper, '준호')
    await vi.advanceTimersByTimeAsync(DEBOUNCE_MS)
    await type(wrapper, '택림')
    await vi.advanceTimersByTimeAsync(DEBOUNCE_MS)
    // 여기서 첫 요청(준호)의 응답이 뒤늦게 도착한다
    await vi.advanceTimersByTimeAsync(500)
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('사용할 수 있는 닉네임이에요')
    expect(wrapper.text()).not.toContain('이미 사용 중인 닉네임이에요')
  })

  it('요청이 떠 있는 사이 값이 바뀌면 그 응답은 버린다 — 다음 요청보다 먼저 와도', async () => {
    // 위 테스트는 새 요청이 **이미 출발한 뒤** 옛 응답이 오는 배치라, 세대 번호를
    // 요청 진입 시점에 올려도 통과한다. 여기서는 옛 응답이 다음 디바운스보다 **먼저** 온다:
    //   t=400 '준호' 요청 → t=450 '택림'으로 수정 → t=600 '준호' 응답 → t=850 '택림' 요청
    // 무효화가 감시자로 올라가 있지 않으면 t=600에 '준호'의 불가 판정이 확정되고,
    // 입력칸은 '택림'인데 제출이 잠긴다.
    vi.spyOn(membersApi, 'checkNicknameAvailability').mockImplementation(async (value: string) => {
      await new Promise((r) => setTimeout(r, 200))
      if (value === '준호') return { nickname: value, available: false, reason: 'ALREADY_EXISTS' }
      return { nickname: value, available: true, reason: null }
    })
    const wrapper = mountStep()

    await type(wrapper, '준호')
    await vi.advanceTimersByTimeAsync(DEBOUNCE_MS) // 요청 출발
    await vi.advanceTimersByTimeAsync(50) // 아직 응답 전
    await type(wrapper, '택림')
    await vi.advanceTimersByTimeAsync(150) // '준호' 응답 도착 — 다음 요청은 아직 안 떴다
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).not.toContain('이미 사용 중인 닉네임이에요')
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
  })

  it('입력을 비우면 지운 닉네임의 판정이 남지 않는다 — 뒤이을 요청이 없어 스스로 낫지 못한다', async () => {
    vi.spyOn(membersApi, 'checkNicknameAvailability').mockImplementation(async (value: string) => {
      await new Promise((r) => setTimeout(r, 200))
      return { nickname: value, available: false, reason: 'ALREADY_EXISTS' as const }
    })
    const wrapper = mountStep()

    await type(wrapper, '준호')
    await vi.advanceTimersByTimeAsync(DEBOUNCE_MS)
    await type(wrapper, '') // 응답 오기 전에 비운다
    await vi.advanceTimersByTimeAsync(1000)
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).not.toContain('이미 사용 중인 닉네임이에요')
  })

  it('저장 중에는 입력을 잠근다 — 그사이 바뀐 값에 오류가 붙는다', async () => {
    const wrapper = mountStep({ pending: true })

    expect(wrapper.get('input').attributes('disabled')).toBeDefined()
  })

  it('오류 문구는 제출한 값에 붙는다 — 응답 시점의 입력값이 아니라', async () => {
    vi.spyOn(membersApi, 'checkNicknameAvailability').mockResolvedValue({
      nickname: '택림',
      available: true,
      reason: null,
    })
    const wrapper = mountStep()
    await type(wrapper, '준호')
    await wrapper.get('form').trigger('submit')
    // 저장이 도는 동안 입력이 바뀐 상황을 흉내 낸다
    await type(wrapper, '택림')
    await wrapper.setProps({ error: '이미 사용 중인 닉네임이에요' })

    // '준호'로 제출해 실패했으므로 '택림'에는 붙지 않는다
    expect(wrapper.text()).not.toContain('이미 사용 중인 닉네임이에요')
    await type(wrapper, '준호')
    expect(wrapper.text()).toContain('이미 사용 중인 닉네임이에요')
  })

  it('안내 리전은 노드째 사라지지 않는다 — 라이브 리전이 낭독을 건너뛴다', async () => {
    const wrapper = mountStep()

    // 아무 상태도 아닐 때부터 두 리전이 DOM에 있어야 한다
    expect(wrapper.find('#onboarding-nickname-error').exists()).toBe(true)
    expect(wrapper.find('#onboarding-nickname-status').exists()).toBe(true)
    expect(wrapper.get('#onboarding-nickname-error').attributes('role')).toBe('alert')
    expect(wrapper.get('#onboarding-nickname-status').attributes('role')).toBe('status')
  })

  it('불가일 때 입력에 aria-invalid가 선다', async () => {
    vi.spyOn(membersApi, 'checkNicknameAvailability').mockResolvedValue({
      nickname: '준호',
      available: false,
      reason: 'ALREADY_EXISTS',
    })
    const wrapper = mountStep()
    expect(wrapper.get('input').attributes('aria-invalid')).toBeUndefined()

    await type(wrapper, '준호')
    await vi.advanceTimersByTimeAsync(DEBOUNCE_MS)
    await wrapper.vm.$nextTick()

    expect(wrapper.get('input').attributes('aria-invalid')).toBe('true')
  })

  it('확인이 실패해도 제출을 막지 않는다 — 편의 기능이 가입을 잠그면 안 된다', async () => {
    vi.spyOn(membersApi, 'checkNicknameAvailability').mockRejectedValue(new Error('boom'))
    const wrapper = mountStep()

    await type(wrapper, '택림')
    await vi.advanceTimersByTimeAsync(DEBOUNCE_MS)
    await wrapper.vm.$nextTick()

    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
  })

  it('제출 실패 문구는 그 값에만 붙는다 — 안 그러면 다음 입력에도 옛 오류가 남는다', async () => {
    vi.spyOn(membersApi, 'checkNicknameAvailability').mockResolvedValue({
      nickname: '택림',
      available: true,
      reason: null,
    })
    const wrapper = mountStep()
    await type(wrapper, '준호')
    await wrapper.get('form').trigger('submit')
    await wrapper.setProps({ error: '이미 사용 중인 닉네임이에요' })
    expect(wrapper.text()).toContain('이미 사용 중인 닉네임이에요')

    await type(wrapper, '택림')

    expect(wrapper.text()).not.toContain('이미 사용 중인 닉네임이에요')
  })

  it('제출 실패가 사전 확인 결과보다 우선한다 — 통과시킨 값도 저장에서 409가 난다', async () => {
    vi.spyOn(membersApi, 'checkNicknameAvailability').mockResolvedValue({
      nickname: '택림',
      available: true,
      reason: null,
    })
    const wrapper = mountStep()

    await type(wrapper, '택림')
    await vi.advanceTimersByTimeAsync(DEBOUNCE_MS)
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('사용할 수 있는 닉네임이에요')

    await wrapper.get('form').trigger('submit')
    await wrapper.setProps({ error: '이미 사용 중인 닉네임이에요' })

    expect(wrapper.text()).toContain('이미 사용 중인 닉네임이에요')
    expect(wrapper.text()).not.toContain('사용할 수 있는 닉네임이에요')
  })
})
