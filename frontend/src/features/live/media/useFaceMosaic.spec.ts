import { describe, expect, it } from 'vitest'
import { holdBoxes, inflateBox, NO_BOXES, type Box } from './useFaceMosaic'

// 얼굴 모자이크의 박스 확장·유지 로직 (S15P11A704-699).
//
// 파이프라인 본체(wasm 검출기·captureStream·requestVideoFrameCallback)는 jsdom에 없어서 돌릴 수 없고,
// 렌더 결과를 스냅샷으로 굳혀 봐야 「무엇이 옳은가」를 말해 주지 않는다. 대신 **깨지면 조용한 것**만 잠근다:
// 확장 비율과 경계 클램프가 틀어지면 얼굴 가장자리가 새거나 검은 띠가 생기고, 유지 카운트가 틀어지면
// 모자이크가 깜빡인다 — 둘 다 실기기에서 눈으로 겨우 잡히는 종류다.
//
// ⚠️ 좌표는 캔버스 픽셀이고 원점은 좌상단이다(MediaPipe BoundingBox의 originX/originY와 같은 축).
// ⚠️ inflateBox가 돌려주는 사각형은 **그 자체로 그려지지 않는다** — 실제 모자이크는 여기에 내접하는
//    타원으로 클리핑된다. 그 클리핑은 캔버스 렌더 루프 안이라 jsdom에서 확인할 수 없고, 여기서
//    잠그는 것은 타원의 외곽 사각형이 어디에 서는가까지다.

/** 읽기 좋게 반올림해서 비교한다 — 0.1 곱셈이라 부동소수 꼬리가 남는다. */
const round = (box: Box): Box => ({
  x: Math.round(box.x * 100) / 100,
  y: Math.round(box.y * 100) / 100,
  width: Math.round(box.width * 100) / 100,
  height: Math.round(box.height * 100) / 100,
})

describe('inflateBox — 박스 10% 확장과 프레임 클램프', () => {
  // 확장률은 25%로 시작했다가 10%로 내렸다 — 사각 마스크 시절의 값이라 얼굴 앞·옆 사물까지 번졌다.
  // 마스크를 타원으로 바꾸면서 함께 내린 값이고, 이 숫자가 다시 커지면 그 피드백이 되돌아온다.
  it('프레임 한가운데 박스는 각 변으로 10%씩 넓어진다', () => {
    // 100x100 박스 → 각 변 10px씩 → 좌상단 -10, 폭 +20. 「각 변 10%」이지 「전체 10%」가 아니다.
    expect(round(inflateBox({ x: 200, y: 100, width: 100, height: 100 }, 640, 480))).toEqual({
      x: 190,
      y: 90,
      width: 120,
      height: 120,
    })
  })

  it('가로세로 비율이 다르면 각 변을 자기 축 길이 기준으로 넓힌다', () => {
    // 얼굴 박스는 세로로 길다 — 짧은 축 기준으로 한 값을 양쪽에 쓰면 위아래가 덜 덮인다.
    // 타원 반지름도 이 폭·높이의 절반이라, 축을 섞으면 타원이 얼굴 비율에서 어긋난다.
    expect(round(inflateBox({ x: 100, y: 100, width: 40, height: 80 }, 640, 480))).toEqual({
      x: 96, // 40*0.1 = 4
      y: 92, // 80*0.1 = 8
      width: 48,
      height: 96,
    })
  })

  it('왼쪽 위 모서리에 걸친 얼굴은 음수 좌표로 새지 않는다', () => {
    // 클램프가 없으면 drawImage의 소스 사각형이 캔버스 밖을 물어 그 부분이 투명(검은 띠)으로 채워진다.
    expect(round(inflateBox({ x: 2, y: 1, width: 40, height: 40 }, 640, 480))).toEqual({
      x: 0, // 2-4 = -2 → 0
      y: 0, // 1-4 = -3 → 0
      width: 46, // 오른쪽 끝 42+4=46, 왼쪽이 0으로 잘렸으니 폭은 46
      height: 45,
    })
  })

  it('오른쪽 아래 모서리에 걸친 얼굴은 프레임 밖으로 넘지 않는다', () => {
    expect(round(inflateBox({ x: 600, y: 450, width: 60, height: 60 }, 640, 480))).toEqual({
      x: 594, // 600-6
      y: 444, // 450-6
      width: 46, // 오른쪽 끝은 666이 아니라 640
      height: 36, // 아래 끝은 516이 아니라 480
    })
  })

  it('프레임보다 큰 박스는 프레임 전체가 된다', () => {
    expect(round(inflateBox({ x: -50, y: -50, width: 800, height: 700 }, 640, 480))).toEqual({
      x: 0,
      y: 0,
      width: 640,
      height: 480,
    })
  })

  it('프레임 완전 밖의 박스는 폭·높이가 음수가 아니라 0이다', () => {
    // 검출기가 이런 값을 줄 일은 드물지만, 음수 폭이 새면 drawImage가 던져 루프 전체가 죽는다.
    const box = round(inflateBox({ x: -300, y: -300, width: 40, height: 40 }, 640, 480))
    expect(box.width).toBe(0)
    expect(box.height).toBe(0)
  })
})

describe('holdBoxes — 미검출 플래시 방지 (히스테리시스)', () => {
  const face: Box = { x: 10, y: 10, width: 50, height: 50 }
  const other: Box = { x: 200, y: 30, width: 40, height: 40 }

  it('검출되면 그 박스로 갈아 끼우고 유지 카운트를 채운다', () => {
    expect(holdBoxes(NO_BOXES, [face])).toEqual({ boxes: [face], hold: 10 })
  })

  it('새로 검출될 때마다 유지 카운트가 다시 찬다 — 계속 잡히는 동안엔 만료가 없다', () => {
    const half = holdBoxes({ boxes: [face], hold: 3 }, [other])
    expect(half).toEqual({ boxes: [other], hold: 10 })
  })

  it('빈 검출이 와도 직전 박스를 그대로 그린다 — 한 프레임 놓친 것이 얼굴 노출이 되면 안 된다', () => {
    const next = holdBoxes({ boxes: [face], hold: 10 }, [])
    expect(next.boxes).toEqual([face])
    expect(next.hold).toBe(9)
  })

  it('검출을 건너뛴 프레임(null)도 빈 검출과 똑같이 감쇠한다', () => {
    // 유지 카운트를 **프레임 단위**로 세는 근거다. 검출 횟수로 세면 DETECT_EVERY를 바꾸는 순간
    // 유지 시간이 조용히 3배가 된다.
    expect(holdBoxes({ boxes: [face], hold: 10 }, null)).toEqual({ boxes: [face], hold: 9 })
  })

  it('빈 검출이 이어지면 정확히 10프레임을 버티고 11번째에 사라진다', () => {
    let held = holdBoxes(NO_BOXES, [face])
    for (let i = 0; i < 10; i += 1) {
      held = holdBoxes(held, [])
      expect(held.boxes).toEqual([face]) // 10번째까지는 아직 그린다
    }

    held = holdBoxes(held, [])

    expect(held.boxes).toEqual([]) // 11번째에 만료
    expect(held.hold).toBe(0)
  })

  it('만료된 뒤에는 카운트가 음수로 내려가지 않는다', () => {
    let held = NO_BOXES
    for (let i = 0; i < 5; i += 1) held = holdBoxes(held, null)
    expect(held).toEqual({ boxes: [], hold: 0 })
  })

  it('얼굴이 사라졌다 다시 잡히면 유지 상태가 되살아난다', () => {
    let held = holdBoxes(NO_BOXES, [face])
    for (let i = 0; i < 11; i += 1) held = holdBoxes(held, [])
    expect(held.boxes).toEqual([])

    held = holdBoxes(held, [other])

    expect(held).toEqual({ boxes: [other], hold: 10 })
  })

  it('여러 얼굴을 그대로 다 들고 간다 — 모자이크 대상은 「모든 얼굴」이다', () => {
    const held = holdBoxes(NO_BOXES, [face, other])
    expect(held.boxes).toHaveLength(2)
    expect(holdBoxes(held, null).boxes).toEqual([face, other]) // 유지 구간에서도 전부 남는다
  })

  it('입력 상태를 제자리에서 고치지 않는다 — 루프가 매 프레임 재대입하는 순수 함수다', () => {
    const prev = { boxes: [face], hold: 4 }
    holdBoxes(prev, [])
    expect(prev).toEqual({ boxes: [face], hold: 4 })
  })

  it('NO_BOXES 상수가 오염되지 않는다 — 만료된 모든 프레임이 이걸 공유한다', () => {
    holdBoxes(holdBoxes(NO_BOXES, [face]), null)
    expect(NO_BOXES).toEqual({ boxes: [], hold: 0 })
  })
})
