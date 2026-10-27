// 낙찰 공개 컨페티 (S15P11A704-66). 디자인 원본의 `fireConfetti()`.
//
// 유찰·강제 종료에는 쓰지 않는다 — 축하할 일이 아니다.
//
// AGENTS.md 규칙 1(색 하드코딩 금지)을 캔버스에서도 지키려면 색을 리터럴로 둘 수 없다.
// Tailwind v4의 `@theme`는 토큰을 `:root`의 CSS 변수로 내보내므로, 그 변수를 읽어서 쓴다 —
// 팔레트가 바뀌면 여기도 따라 바뀐다.

const TOKEN_VARS = ['--color-terra', '--color-slate', '--color-success', '--color-terra-tint']

function tokenColors(): string[] {
  const style = getComputedStyle(document.documentElement)
  const colors = TOKEN_VARS.map((name) => style.getPropertyValue(name).trim()).filter(
    (value) => value.length > 0,
  )
  return colors.length > 0 ? colors : ['currentColor']
}

/**
 * 캔버스에 컨페티를 한 번 쏜다.
 * @returns 중단 함수 (화면을 떠날 때 호출)
 */
export function fireConfetti(canvas: HTMLCanvasElement): () => void {
  const context = canvas.getContext('2d')
  if (context === null) return () => {}

  const width = window.innerWidth
  const height = window.innerHeight
  canvas.width = width
  canvas.height = height

  const colors = tokenColors()
  const pieces = Array.from({ length: 140 }, () => ({
    x: width / 2 + (Math.random() - 0.5) * 120,
    y: height * 0.42,
    vx: (Math.random() - 0.5) * 11,
    vy: -Math.random() * 13 - 4,
    size: 4 + Math.random() * 5,
    color: colors[Math.floor(Math.random() * colors.length)],
    rotation: Math.random() * 6,
    spin: (Math.random() - 0.5) * 0.4,
    life: 0,
  }))

  let frame = 0
  let previous: number | null = null

  const loop = (now: number) => {
    previous ??= now
    const delta = Math.min(32, now - previous) / 16
    previous = now

    context.clearRect(0, 0, width, height)
    let alive = false

    for (const piece of pieces) {
      piece.vy += 0.32 * delta
      piece.x += piece.vx * delta
      piece.y += piece.vy * delta
      piece.rotation += piece.spin * delta
      piece.life += delta
      if (piece.y >= height + 20) continue

      alive = true
      context.save()
      context.translate(piece.x, piece.y)
      context.rotate(piece.rotation)
      context.globalAlpha = Math.max(0, 1 - piece.life / 150)
      context.fillStyle = piece.color
      context.fillRect(-piece.size / 2, -piece.size / 2, piece.size, piece.size * 0.6)
      context.restore()
    }

    if (alive) frame = requestAnimationFrame(loop)
    else context.clearRect(0, 0, width, height)
  }

  frame = requestAnimationFrame(loop)

  return () => {
    if (frame !== 0) cancelAnimationFrame(frame)
    frame = 0
    context.clearRect(0, 0, width, height)
  }
}
