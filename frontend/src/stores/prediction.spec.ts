import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { usePredictionStore } from './prediction'

// restoreSubmitted의 인자 의미를 잠근다 (S15P11A704-388).
// WS-03 스냅샷 → live 스토어 → LiveView watch → 이 액션 경로로 재접속 복원이 이뤄지므로,
// 인자를 흘리면 "예측 참여 완료"만 뜨고 금액이 빈칸이 된다 — 화면은 멀쩡해 보이는 종류의 회귀다.
beforeEach(() => setActivePinia(createPinia()))

describe('prediction.restoreSubmitted', () => {
  it('스냅샷 값으로 금액·스테이크를 복원한다', () => {
    const prediction = usePredictionStore()

    prediction.restoreSubmitted(62_000, 1_500)

    expect(prediction.submitted).toBe(true)
    expect(prediction.myPrediction).toBe(62_000)
    expect(prediction.myStake).toBe(1_500)
    expect(prediction.pendingRequestId).toBeNull()
  })

  /** 값을 모르는 호출부(목·테스트)도 잠금만은 복원할 수 있어야 해서 기본값을 남겨 뒀다. */
  it('인자가 없으면 잠금만 복원하고 금액은 비운다', () => {
    const prediction = usePredictionStore()

    prediction.restoreSubmitted()

    expect(prediction.submitted).toBe(true)
    expect(prediction.myPrediction).toBeNull()
    expect(prediction.myStake).toBe(0)
  })

  /**
   * 이미 제출한 상태에서 다시 부르면 값을 덮는다 — 그래서 LiveView.vue의 watch가
   * `!prediction.submitted` 가드로 신규 제출 직후의 재발화를 막는다. 가드를 떼면
   * 방금 제출한 금액이 스냅샷 값(또는 기본값)으로 덮인다.
   */
  it('이미 제출된 상태에서 호출되면 값을 덮는다 — 호출부 가드가 필요한 이유', () => {
    const prediction = usePredictionStore()
    prediction.$patch({ submitted: true, myPrediction: 70_000, myStake: 3_000 })

    prediction.restoreSubmitted()

    expect(prediction.myPrediction).toBeNull()
    expect(prediction.myStake).toBe(0)
  })

  it('복원은 진행 중 플래그를 건드리지 않는다', () => {
    const prediction = usePredictionStore()
    prediction.$patch({ submitting: true })

    prediction.restoreSubmitted(50_000, 1_000)

    expect(prediction.submitting).toBe(true)
  })
})
