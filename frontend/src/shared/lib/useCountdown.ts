import { computed, onUnmounted, ref, toValue, type MaybeRefOrGetter } from 'vue'

// BID-05 서버 권위 타이머 — 남은 시간은 서버가 준 절대 마감 시각(endsAt) + serverOffset 기준으로 "표시만" 한다.
// 클라이언트가 자체 카운트다운 상태를 갖지 않는다: 1초 틱은 현재 시각 갱신용일 뿐이고,
// 남은 시간 = endsAt - (Date.now() + serverOffset). 서버 기준 없이 남은 시간을 지어내지 않는다.

// ── 공유 시계(shared ticker) ──
// 방 목록 카드처럼 같은 화면에 카운트다운이 N개 붙으면 useCountdown 호출마다 setInterval이 생겨
// 타이머가 N개로 늘어난다. 틱 값은 어차피 "현재 시각" 하나뿐이므로 모듈 스코프에 시계 하나를 두고
// 모든 구독자가 공유한다. 참조 카운트가 0이 되면 인터벌을 정지해 백그라운드 낭비를 막는다.
const sharedNow = ref(Date.now())
let tickerId: ReturnType<typeof setInterval> | null = null
let subscriberCount = 0

function subscribeTicker() {
  subscriberCount += 1
  if (tickerId === null) {
    sharedNow.value = Date.now() // 첫 구독 시점의 값을 즉시 최신화 (정지 중 흘러간 시간 보정)
    tickerId = setInterval(() => {
      sharedNow.value = Date.now()
    }, 1000)
  }
}

function unsubscribeTicker() {
  subscriberCount = Math.max(0, subscriberCount - 1)
  if (subscriberCount === 0 && tickerId !== null) {
    clearInterval(tickerId)
    tickerId = null
  }
}

export interface UseCountdownOptions {
  /**
   * 서버-클라이언트 시각 보정값 (ms). 라이브·대기방·콘솔은 live 스토어의 `clockOffset`
   * (TIME_SYNC 우선, 없으면 REST의 serverTime)을, 목록·홈은 `room.serverOffset`을 넘긴다.
   * null이면 서버 기준이 하나도 없다는 뜻이라 남은 시간을 계산하지 않는다(규칙 5 — 클라 시계 단독 금지).
   */
  serverOffset?: MaybeRefOrGetter<number | null>
  /** 마감 임박으로 볼 남은 시간 임계값 (ms). 기본 60초 */
  urgentMs?: number
}

/**
 * 서버 권위 마감 시각(epoch ms) 기준 실시간 카운트다운 (BID-05).
 *
 * @param endsAt 마감 시각(epoch ms). null/undefined면 표시 값이 비어 있다
 */
export function useCountdown(
  endsAt: MaybeRefOrGetter<number | null | undefined>,
  options: UseCountdownOptions = {},
) {
  const { serverOffset = 0, urgentMs = 60_000 } = options

  subscribeTicker()
  onUnmounted(unsubscribeTicker)

  /** 남은 ms. endsAt이 없으면 null (표시 측은 '--:--' 처리) */
  const remainingMs = computed<number | null>(() => {
    const end = toValue(endsAt)
    if (end === null || end === undefined) return null
    const offset = toValue(serverOffset)
    if (offset === null) return null // 서버 시각 출처 없음 — 기준 없이 남은 시간을 지어내지 않는다(규칙 5)
    return Math.max(0, end - (sharedNow.value + offset))
  })

  /** 'MM:SS' (1시간 이상이면 'H:MM:SS'), 값 없으면 '--:--' */
  const display = computed(() => {
    if (remainingMs.value === null) return '--:--'
    const total = Math.floor(remainingMs.value / 1000)
    const h = Math.floor(total / 3600)
    const m = Math.floor((total % 3600) / 60)
    const s = total % 60
    const mm = String(m).padStart(2, '0')
    const ss = String(s).padStart(2, '0')
    return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`
  })

  /** 마감 임박 — 타이머 칩 강조용 */
  const urgent = computed(() => remainingMs.value !== null && remainingMs.value <= urgentMs)

  return { remainingMs, display, urgent }
}
