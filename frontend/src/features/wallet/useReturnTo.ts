// 충전·출금처럼 "진입 직전 경로로 돌아가는 전체 화면"이 쓰는 복귀 처리.
// 두 화면이 같은 동작을 하게 되면서(슬라이스 5·6) 여기로 뽑았다.
//
// 복귀 예약은 타이머다 — 결과를 잠깐 보여주고 돌아가기 위한 것이라, 사용자가 먼저 화면을 떠나면
// 반드시 회수해야 한다. `onUnmounted`만으로는 부족하다: 목적지 청크를 처음 불러오는 경우
// 라우팅이 예약 시간을 넘겨서 언마운트보다 타이머 발화가 먼저 일어난다(슬라이스 5에서 실측).
// 그래서 이탈이 **시작될 때**(`onBeforeRouteLeave`) 버린다.

import { computed, onUnmounted } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'

export function useReturnTo(fallbackPath: string) {
  const route = useRoute()
  const router = useRouter()

  /** 진입 직전 경로. 외부 URL로 튕기지 않게 `/`로 시작하는 값만 받는다 */
  const returnTo = computed(() => {
    const raw = route.query.redirect
    return typeof raw === 'string' && raw.startsWith('/') ? raw : fallbackPath
  })

  let timer: ReturnType<typeof setTimeout> | null = null

  function clearScheduled() {
    if (timer === null) return
    clearTimeout(timer)
    timer = null
  }

  function goBack() {
    void router.replace(returnTo.value)
  }

  /** 결과를 잠깐 보여준 뒤 복귀 */
  function scheduleReturn(delayMs: number) {
    clearScheduled()
    timer = setTimeout(goBack, delayMs)
  }

  onBeforeRouteLeave(clearScheduled)
  onUnmounted(clearScheduled)

  return { returnTo, goBack, scheduleReturn }
}
