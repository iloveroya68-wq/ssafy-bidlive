// 지난 시각을 "오늘 / 어제 / N일 전 / M월 D일"로 옮긴다.
//
// 서버는 절대 시각(ISO 8601)만 주고 표기는 FE가 정한다 (docs/04 §4.1).
// 종료 카드의 "어제 낙찰"처럼 **날짜 단위**로만 읽히면 되는 자리에 쓴다.
//
// 시각까지 필요한 자리(원장 행 "오늘 14:23")는 `shared/lib/point.ts`의 `formatLedgerTime`이 담당한다.
// 둘을 하나로 합치지 않는 이유는 경계 규칙이 같아도 **출력 단위가 다르기** 때문이다.

const DAY_MS = 86_400_000

/** 그날 00:00의 epoch ms */
function startOfDay(epochMs: number): number {
  const d = new Date(epochMs)
  d.setHours(0, 0, 0, 0)
  return d.getTime()
}

/**
 * @param epochMs 대상 시각
 * @param now     기준 시각 (테스트에서 고정하려고 주입받는다)
 *
 * 경계는 **달력 날짜**로 센다 — 24시간 단위로 세면 어제 23:50이 "오늘"로 보인다.
 * 미래 시각은 "오늘"로 접는다: 서버·클라이언트 시계 차로 몇 초 앞선 값이 올 수 있는데
 * 그걸 "-1일 전"처럼 쓰면 화면이 깨진 것처럼 보인다.
 */
export function formatRelativeDay(epochMs: number, now: number = Date.now()): string {
  if (!Number.isFinite(epochMs)) return ''

  const dayDiff = Math.round((startOfDay(now) - startOfDay(epochMs)) / DAY_MS)
  if (dayDiff <= 0) return '오늘'
  if (dayDiff === 1) return '어제'
  if (dayDiff < 7) return `${dayDiff}일 전`

  const d = new Date(epochMs)
  return `${d.getMonth() + 1}월 ${d.getDate()}일`
}

/** ISO 8601 문자열용. 파싱할 수 없으면 빈 문자열 — 호출부가 그 자리를 비운다 */
export function formatRelativeDayIso(iso: string | null, now: number = Date.now()): string {
  if (iso === null) return ''
  const ms = Date.parse(iso)
  return Number.isNaN(ms) ? '' : formatRelativeDay(ms, now)
}

const pad2 = (n: number) => String(n).padStart(2, '0')

/**
 * **예정** 시각 표기 — "7월 30일 19:00". 대기방 카드의 시작 예정 시각에 쓴다.
 *
 * 지난 시각과 달리 상대 표기("3일 후")를 쓰지 않는다. 시작 시각은 사용자가 **일정에 적는 값**이라
 * 절대 시각이 그대로 보여야 하고, 예약 시각은 안내용이라 판매자가 수동으로 시작할 수도 있다(ROOM-05).
 */
export function formatScheduledAt(iso: string | null): string {
  if (iso === null) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getMonth() + 1}월 ${d.getDate()}일 ${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

/**
 * **곧 시작할** 경매 표기 — "21:30 · 12분 후". ROOM-08 스트립 카드에 쓴다.
 *
 * 상대 표기는 **서버 시각 기준**이다 (규칙 5 — 단말 시계 단독 계산 금지).
 * `serverOffset`을 아직 못 받았으면 절대 시각만 남긴다 — 몇 분 남았는지 지어내지 않는다.
 * 1분 미만은 "곧 시작"이다: `0분 후`도, 이미 지난 값의 `-1분 후`도 보여줄 값이 아니다.
 *
 * 분 단위라 초 틱이 필요 없어 호출부는 로드 시점 1회만 계산한다.
 *
 * @param serverOffset 서버-단말 시각 차이(ms). 모르면 null (`roomStore.serverOffset`)
 * @param now          기준 시각 (테스트에서 고정하려고 주입받는다)
 */
export function formatUpcomingAt(
  iso: string,
  serverOffset: number | null,
  now: number = Date.now(),
): string {
  const at = Date.parse(iso)
  if (Number.isNaN(at)) return ''
  const d = new Date(at)
  const clock = `${pad2(d.getHours())}:${pad2(d.getMinutes())}`
  if (serverOffset === null) return clock
  const minutes = Math.round((at - (now + serverOffset)) / 60_000)
  return minutes < 1 ? `${clock} · 곧 시작` : `${clock} · ${minutes}분 후`
}
