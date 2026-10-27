// 방송 송출 얼굴 모자이크 (LIVE, S15P11A704-699).
//
// 판매자 브라우저에서만 도는 파이프라인이다 — **원본은 서버로 나가지 않는다.** 서버·미디어 서버는 그대로 두고
// 송출 트랙만 갈아끼운다:
//   원본 카메라 트랙 → 숨김 <video> → MediaPipe FaceDetector(WASM)로 얼굴 박스 → <canvas>에 프레임을 그리고
//   얼굴 영역만 픽셀화 → canvas.captureStream()의 비디오 트랙.
// 그 트랙을 PublisherPreview가 `publisher.replaceTrack()`으로 끼운다.
//
// ⚠️ 모델·wasm은 자체 호스팅한다(`public/mediapipe/`). CDN을 쓰면 방송 시작이 남의 가용성에 묶이고,
//    판매자 얼굴이라는 민감한 처리를 외부 도메인 스크립트에 맡기게 된다.
// ⚠️ `@mediapipe/tasks-vision`은 이 파일 **안에서 동적 import**한다(SDK를 다루는 208·210·211과 같은 패턴).
//    토글이 기본 OFF이므로, 켜기 전에는 라이브러리도 wasm도 한 바이트도 받지 않아야 한다 — 그래서
//    파일 상단의 import는 타입 전용이고, PublisherPreview도 이 모듈 자체를 동적 import한다.
import type { Detection } from '@mediapipe/tasks-vision'

/** 검출은 몇 프레임에 한 번 도는가. 검출이 프레임 예산의 대부분이고 얼굴은 그사이 거의 안 움직인다. */
const DETECT_EVERY = 3
/** 검출이 놓친 뒤에도 직전 박스를 몇 프레임 더 유지하는가 (24fps에서 약 0.4초). */
const HOLD_FRAMES = 10
/**
 * 박스를 각 변으로 얼마나 넓히는가. 모델 박스는 눈·코·입에 딱 붙어 턱·머리카락·귀가 새어 나온다.
 *
 * 25%로 시작했다가 10%로 줄였다 — **얼굴 앞·옆 사물까지 번진다**는 실사용 피드백 때문이다.
 * 마스크를 사각에서 타원으로 바꾸면서 같이 내렸다: 타원은 같은 박스에서 네 모서리를 버려 덮는 면적이
 * 약 21% 좁으므로, 확장을 줄여도 얼굴 둘레의 체감 마진은 비슷하고 침범만 줄어든다.
 */
const BOX_INFLATE = 0.1
/**
 * 얼굴로 인정할 최소 신뢰도 (기본 0.5). **오검출을 깎는 가장 싼 튜닝 노브다.**
 *
 * 손·주먹이 얼굴로 잡힌다는 피드백에서 올렸다. 그런 오검출은 대체로 문턱 근처 신뢰도로 들어오고
 * 실제 얼굴은 0.9 이상으로 나와서, 문턱을 조금만 올려도 손 쪽만 떨어져 나간다.
 * 다만 0.7 이상으로 올리면 마스크 쓴 얼굴·역광 얼굴을 놓치기 시작하므로 0.6에서 시작한다.
 */
const MIN_DETECTION_CONFIDENCE = 0.6
/** 모자이크 격자 크기 — 박스를 1/12로 줄였다 되확대한다. 클수록 뭉갠다. */
const PIXEL_SCALE = 12
/** captureStream fps. 얼굴만 뭉개는 화면이라 원본 30fps를 그대로 따라갈 이유가 없다. */
const CAPTURE_FPS = 24
/** 트랙 설정에 해상도가 없을 때(가상 카메라 등) 쓰는 기본값 — initPublisher의 '640x480'과 맞춘다. */
const FALLBACK_SIZE = { width: 640, height: 480 }

/** 캔버스 픽셀 좌표계의 사각형. MediaPipe의 BoundingBox(originX/originY)를 이 모양으로 옮겨 쓴다. */
export interface Box {
  x: number
  y: number
  width: number
  height: number
}

/**
 * 검출 박스를 각 변으로 BOX_INFLATE만큼 넓히고 프레임 밖을 잘라낸다.
 * 이 박스는 그 자체로 그려지지 않고 **내접 타원의 외곽 사각형**으로 쓰인다(pixelate 참조).
 *
 * 넓히는 이유는 두 가지다. (1) blaze_face 박스는 얼굴 중심부에만 붙어 턱선·이마·귀가 밖에 남는다.
 * (2) 검출을 3프레임에 한 번만 돌리므로 그사이 움직인 만큼의 여유가 필요하다.
 * 자르는 이유는 drawImage의 소스 사각형이 캔버스를 벗어나면 그 부분이 투명으로 채워져,
 * 얼굴이 화면 가장자리에 걸칠 때 모자이크 대신 검은 띠가 생기기 때문이다.
 */
export function inflateBox(box: Box, frameWidth: number, frameHeight: number): Box {
  const padX = box.width * BOX_INFLATE
  const padY = box.height * BOX_INFLATE
  const x = Math.max(0, box.x - padX)
  const y = Math.max(0, box.y - padY)
  // 프레임 완전 밖의 박스는 폭이 음수가 되므로 0으로 눌러 둔다(그리기 쪽이 0을 건너뛴다).
  return {
    x,
    y,
    width: Math.max(0, Math.min(frameWidth, box.x + box.width + padX) - x),
    height: Math.max(0, Math.min(frameHeight, box.y + box.height + padY) - y),
  }
}

/** 지금 그릴 박스와 남은 유지 프레임 수. */
export interface HeldBoxes {
  boxes: Box[]
  hold: number
}

/** 유지할 것이 없는 상태. */
export const NO_BOXES: HeldBoxes = { boxes: [], hold: 0 }

/**
 * 검출 결과를 유지 상태에 반영한다 — **미검출 플래시 방지**의 핵심.
 *
 * 검출은 한두 프레임씩 곧잘 놓친다(고개를 돌리거나 손이 스칠 때). 그때마다 박스를 지우면 모자이크가
 * 깜빡이고, 하필 그 순간이 얼굴이 드러나는 프레임이다. 그래서 놓쳐도 직전 박스를 HOLD_FRAMES 동안 쥔다.
 *
 * `detected`가 `null`이면 "이번 프레임은 검출을 건너뛴 프레임"이다. 빈 배열(검출했는데 못 찾음)과
 * **똑같이 감쇠시킨다** — 유지 카운트를 프레임 단위로 세야 「10프레임 유지」가 DETECT_EVERY와 무관하게
 * 성립하기 때문이다. 검출 횟수로 세면 주기를 바꾸는 순간 유지 시간이 몰래 3배가 된다.
 */
export function holdBoxes(prev: HeldBoxes, detected: Box[] | null): HeldBoxes {
  if (detected !== null && detected.length > 0) return { boxes: detected, hold: HOLD_FRAMES }
  if (prev.hold > 0) return { boxes: prev.boxes, hold: prev.hold - 1 }
  return NO_BOXES
}

/**
 * 2D 컨텍스트를 꺼내되 없으면 던진다.
 * 그냥 `if (!ctx) throw`로 좁히면 아래 렌더 루프(클로저)에서 좁힘이 유지되지 않아 매 호출이 null 가능으로 잡힌다.
 */
function context2d(canvas: HTMLCanvasElement): CanvasRenderingContext2D {
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('2D 캔버스 컨텍스트를 만들 수 없습니다')
  return ctx
}

export interface FaceMosaic {
  /** `publisher.replaceTrack()`에 넘길 처리된 비디오 트랙. */
  readonly track: MediaStreamTrack
  /** 루프·검출기·출력 트랙을 정리한다. `sourceTrack`은 호출자 소유라 여기서 멈추지 않는다. */
  dispose(): void
}

/**
 * 모자이크 파이프라인을 만든다. 준비(wasm·모델 로드, 카메라 재생, 캔버스 캡처) 중 하나라도 실패하면 throw한다 —
 * 호출자는 그걸 받아 토글을 되돌리고 **원본 송출은 그대로 유지**해야 한다.
 *
 * `sourceTrack`은 이 함수가 소유하지 않는다. 호출자가 넘긴 그대로 살려 두므로, 토글을 끌 때 그 트랙을
 * 다시 replaceTrack해 원본으로 돌아갈 수 있다.
 */
export async function createFaceMosaic(sourceTrack: MediaStreamTrack): Promise<FaceMosaic> {
  const settings = sourceTrack.getSettings()
  const width = settings.width ?? FALLBACK_SIZE.width
  const height = settings.height ?? FALLBACK_SIZE.height

  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = context2d(canvas)
  if (typeof canvas.captureStream !== 'function') {
    throw new Error('이 브라우저는 canvas.captureStream을 지원하지 않습니다')
  }

  // 픽셀화용 축소 버퍼 — 얼굴 박스를 여기에 작게 그렸다가 되확대한다.
  const small = document.createElement('canvas')
  const smallCtx = context2d(small)

  // DOM에 붙이지 않은 <video>다 — 화면에 보일 필요 없이 디코드된 프레임만 필요하다.
  const video = document.createElement('video')
  video.srcObject = new MediaStream([sourceTrack])
  video.muted = true // 오디오는 없지만, 음소거가 아니면 자동재생 정책에 걸릴 수 있다
  video.playsInline = true
  await video.play()

  // 무거운 준비(wasm 수 MB + 모델)를 마지막에 둔다 — 앞에서 던지면 검출기를 만들기 전이라 close할 것이 없다.
  const { FilesetResolver, FaceDetector } = await import('@mediapipe/tasks-vision')
  const vision = await FilesetResolver.forVisionTasks('/mediapipe/wasm')
  const detector = await FaceDetector.createFromOptions(vision, {
    baseOptions: { modelAssetPath: '/mediapipe/blaze_face_short_range.tflite' },
    runningMode: 'VIDEO', // 프레임 간 추적을 켠다 — IMAGE 모드는 매 호출이 독립이라 더 느리고 더 흔들린다
    minDetectionConfidence: MIN_DETECTION_CONFIDENCE,
  })

  const track = canvas.captureStream(CAPTURE_FPS).getVideoTracks()[0]

  let stopped = false
  let frame = 0
  let held = NO_BOXES
  let lastTimestamp = 0
  let rafHandle = 0
  let rvfcHandle = 0
  // rVFC는 「새 프레임이 합성기에 올라갔을 때」만 불려 카메라 fps와 정확히 맞는다. 미지원 브라우저는 rAF 폴백
  // (그쪽은 화면 주사율을 따라가므로 카메라보다 빨리 돌 수 있지만, 같은 프레임을 다시 그릴 뿐이라 결과는 같다).
  const useRvfc = typeof video.requestVideoFrameCallback === 'function'

  function schedule(): void {
    if (stopped) return
    if (useRvfc) rvfcHandle = video.requestVideoFrameCallback(render)
    else rafHandle = requestAnimationFrame(render)
  }

  function toBox(boundingBox: NonNullable<Detection['boundingBox']>): Box {
    return {
      x: boundingBox.originX,
      y: boundingBox.originY,
      width: boundingBox.width,
      height: boundingBox.height,
    }
  }

  function detect(): Box[] | null {
    try {
      // detectForVideo의 타임스탬프는 **엄격히 증가**해야 한다 — 같은 ms에 두 번 부르면 던진다.
      // 프레임 간격이 1ms 미만일 수 있어(rAF 폴백) 직전 값 +1을 하한으로 둔다.
      lastTimestamp = Math.max(lastTimestamp + 1, Math.round(performance.now()))
      const { detections } = detector.detectForVideo(video, lastTimestamp)
      return detections
        .map((d) => d.boundingBox)
        .filter((b): b is NonNullable<typeof b> => b !== undefined)
        .map((b) => inflateBox(toBox(b), width, height))
    } catch (error) {
      // 검출만 실패해도 그리기는 계속한다 — 여기서 던지면 schedule()에 못 닿아 루프가 죽고,
      // 프리뷰와 송출이 한 프레임에 얼어붙는다(원본으로 돌아가는 것보다 나쁘다).
      console.warn('[live/media] 얼굴 검출 실패:', error)
      return null
    }
  }

  /**
   * 박스 영역을 1/PIXEL_SCALE로 줄였다 보간 없이 되확대해 격자로 뭉갠다.
   * 되붙일 때는 **박스에 내접하는 타원으로 클리핑**한다 — 사각으로 덮으면 네 모서리가 얼굴 밖으로
   * 크게 나가서, 얼굴 앞·옆에 있는 사물(손에 든 상품, 옆에 놓인 물건)까지 같이 뭉개진다는
   * 실사용 피드백이 있었다. 얼굴은 대체로 타원이라 모서리는 버려도 되는 면적이다.
   */
  function pixelate(box: Box): void {
    if (box.width < 1 || box.height < 1) return // drawImage는 0폭 소스 사각형에 던진다
    const w = Math.max(1, Math.round(box.width / PIXEL_SCALE))
    const h = Math.max(1, Math.round(box.height / PIXEL_SCALE))
    // 축소는 **클리핑 밖에서** 한다: 읽는 쪽(canvas)은 clip의 영향을 안 받고, 박스 전체를 평균 내야
    // 타원 가장자리의 격자 한 칸도 주변색과 이어진다. 크기 대입이 컨텍스트 상태를 초기화하므로
    // 축소 쪽 imageSmoothing은 매 프레임 기본값(켬)이다 — 그래야 한 칸이 그 영역의 평균색이 된다.
    small.width = w
    small.height = h
    smallCtx.drawImage(canvas, box.x, box.y, box.width, box.height, 0, 0, w, h)

    ctx.save()
    ctx.beginPath()
    // 내접 타원 — 중심은 박스 중심, 반지름은 각 변의 절반.
    ctx.ellipse(
      box.x + box.width / 2,
      box.y + box.height / 2,
      box.width / 2,
      box.height / 2,
      0,
      0,
      Math.PI * 2,
    )
    ctx.clip()
    ctx.imageSmoothingEnabled = false // restore()가 되돌려 준다(그리기 상태의 일부)
    ctx.drawImage(small, 0, 0, w, h, box.x, box.y, box.width, box.height)
    ctx.restore()
  }

  function render(): void {
    if (stopped) return
    ctx.drawImage(video, 0, 0, width, height)
    held = holdBoxes(held, frame % DETECT_EVERY === 0 ? detect() : null)
    frame += 1
    for (const box of held.boxes) pixelate(box)
    schedule()
  }

  schedule()

  return {
    track,
    dispose(): void {
      // 멱등이어야 한다 — 토글을 끄는 도중 언마운트되면 stopMosaic과 정리 경로가 겹쳐 두 번 불린다.
      // detector.close()를 두 번 부르면 이미 해제된 wasm 핸들을 건드려 던진다.
      if (stopped) return
      stopped = true
      if (rvfcHandle) video.cancelVideoFrameCallback(rvfcHandle)
      if (rafHandle) cancelAnimationFrame(rafHandle)
      detector.close() // wasm 힙을 쥐고 있어 놓아주지 않으면 토글을 껐다 켤 때마다 쌓인다
      track.stop()
      video.pause()
      video.srcObject = null // sourceTrack은 멈추지 않는다 — 호출자가 원본으로 되돌릴 때 그대로 쓴다
    },
  }
}
