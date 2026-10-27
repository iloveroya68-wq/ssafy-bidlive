// PRED-05 리더보드 표기 (S15P11A704-76).
//
// 홈 티커와 마이페이지 패널이 **같은 값을 같은 모양으로** 보여야 한다. 각자 들고 있으면
// 한쪽만 고쳐도 아무도 모르고, 실제로 티커는 0-based 인덱스로 / 패널은 1-based rank로
// 메달을 붙이고 있었다 — 서버가 주는 건 `rank`(1부터)다.

/** 1~3위는 메달, 그 아래는 `N위` (와이어프레임 정본 `medal()`) */
export function rankMark(rank: number): string {
  return ['🥇', '🥈', '🥉'][rank - 1] ?? `${rank}위`
}

/**
 * 수익률 표기. `payoutRate`는 **이미 100을 곱해 온 값**이라 그대로 붙인다
 * (`지급 ÷ 스테이크 × 100`이므로 **100%가 본전**이다).
 *
 * 부호는 100을 넘을 때만 붙인다. 와이어프레임 티커가 `+312%`처럼 항상 `+`를 달고 있지만
 * 거기 등장하는 값이 전부 100 초과라서다 — 원금도 못 건진 50%에 `+`를 붙이면 거짓말이 된다.
 */
export function formatPayoutRate(rate: number, { withSign = false } = {}): string {
  const rounded = Math.round(rate)
  const sign = withSign && rounded > 100 ? '+' : ''
  return `${sign}${rounded}%`
}
