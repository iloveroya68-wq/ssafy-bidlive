// 낙찰가·집계 건수 카운트업 (S15P11A704-66·67).
//
// 디자인 원본의 `tween()`에 해당한다. 결과 화면 두 곳에 랜딩(BP 카운트업)까지 세 곳이 쓰게 되어
// 컨벤션(공용 유틸은 shared/lib)대로 features/result에서 이곳으로 옮겼다 (S15P11A704-308).
// `prefers-reduced-motion`이면 애니메이션 없이 최종값으로 바로 간다 — 원본도 `RM`이면 건너뛴다.

import { onUnmounted, ref, watch, type Ref } from 'vue'

export interface CountUpOptions {
  /** 진행 시간 (ms) */
  durationMs?: number
  /** 시작 전 지연 (ms). 원본은 카드가 뜬 뒤 350ms 있다가 숫자를 올린다 */
  delayMs?: number
}

function prefersReducedMotion(): boolean {
  return (
    typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
  )
}

/**
 * `target()`이 바뀔 때마다 0에서 그 값까지 올린다.
 * @returns 화면에 그릴 현재 값 (정수)
 */
export function useCountUp(target: () => number, options: CountUpOptions = {}): Ref<number> {
  const { durationMs = 1000, delayMs = 0 } = options
  const value = ref(0)

  let frame = 0
  let timer: ReturnType<typeof setTimeout> | null = null

  function stop() {
    if (frame !== 0) cancelAnimationFrame(frame)
    frame = 0
    if (timer !== null) clearTimeout(timer)
    timer = null
  }

  function run(to: number) {
    stop()
    if (!Number.isFinite(to) || to <= 0 || prefersReducedMotion()) {
      value.value = Number.isFinite(to) ? to : 0
      return
    }

    value.value = 0
    const begin = () => {
      let startedAt: number | null = null
      const step = (now: number) => {
        startedAt ??= now
        const progress = Math.min(1, (now - startedAt) / durationMs)
        // easeOutQuint — main.css의 --ease-out-quint와 같은 곡선
        value.value = Math.round(to * (1 - Math.pow(1 - progress, 5)))
        if (progress < 1) frame = requestAnimationFrame(step)
      }
      frame = requestAnimationFrame(step)
    }

    if (delayMs > 0) timer = setTimeout(begin, delayMs)
    else begin()
  }

  watch(target, run, { immediate: true })
  onUnmounted(stop)

  return value
}
