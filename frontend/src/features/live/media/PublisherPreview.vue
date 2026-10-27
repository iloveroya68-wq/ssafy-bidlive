<script setup lang="ts">
// 송출 미리보기 서브컴포넌트 (S15P11A704-211) — 진행 콘솔 ④ (docs/03 §2-1).
//
// 콘솔 화면은 FE1이 만들고, 이 송출 프리뷰 블록만 FE2가 제공해 FE1이 꽂는다(#video 슬롯과 같은 패턴).
// 프리뷰는 **로컬 카메라**(OpenVidu initPublisher = getUserMedia)라 미디어 서버 없이도 동작한다.
//
// 실제 송출 (LIVE-01/02, S15P11A704-363): 방이 라이브로 전환(auctionStatus='LIVE')되고 프리뷰가 준비되면
// publisher 토큰(209)을 발급받아 세션에 connect 후 이 publisher를 publish한다 — 그때부터 시청자(LiveStage, 210)가 받는다.
// 목 모드는 OpenVidu 서버가 없어 송출하지 않고 프리뷰만 보여준다(실 연결은 210·211처럼 서버 준비 후 검증).
//
// ⚠️ SDK(값)는 동적 import(목 모드/초기 번들에서 제외, 210·208 패턴). 카메라 권한 거부·부재 시 플레이스홀더로 폴백.
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import type { OpenVidu, Publisher, Session } from 'openvidu-browser'
import { isMockApi } from '@/shared/api/mock'
import { useLiveStore } from '@/stores/live'
import { issuePublisherToken, uploadCatchUpAudioChunk, uploadLiveSnapshot } from './api'
import type { FaceMosaic } from './useFaceMosaic'

type PreviewStatus = 'starting' | 'ready' | 'denied'
const status = ref<PreviewStatus>('starting')
const cameraOn = ref(true)
const micOn = ref(true)
// 실 송출 시작 실패 표시 — 프리뷰는 멀쩡해 조용히 실패하면 판매자가 방송이 나가는 줄 안다(리뷰 1·2).
const broadcastError = ref(false)

// 얼굴 모자이크 (S15P11A704-699). **기본 OFF** — 켜기 전에는 라이브러리도 wasm도 받지 않고,
// 송출 경로는 손대지 않은 원본 그대로다. mosaicBusy는 준비(수 MB 로드) 중 연타를 막는다.
const mosaicOn = ref(false)
const mosaicBusy = ref(false)
const mosaicFailed = ref(false)

const live = useLiveStore()

const videoRef = ref<HTMLVideoElement | null>(null)
let ov: OpenVidu | null = null
let publisher: Publisher | null = null
// 실제 송출 세션(라이브 전환 시 connect+publish) — 로컬 프리뷰용 publisher와 구분한다.
let session: Session | null = null
let broadcasting = false // 송출 시작 1회 가드(재전이·재시도 중복 방지)
let disposed = false
// 모자이크 파이프라인과, 그 입력으로 쓰는 카메라 트랙 클론(끌 때 원본으로 되돌릴 트랙이기도 하다).
let mosaic: FaceMosaic | null = null
let mosaicSource: MediaStreamTrack | null = null

// AI-03 STT 입력. MediaRecorder의 timeslice 조각은 단독 재생이 불가능한 브라우저가 있어,
// 짧게 start→stop한 **완결 파일**을 순서대로 보낸다. 마이크 수집과 Credit 사용을 승인한 환경에서만 켠다.
const sttCaptureEnabled = import.meta.env.VITE_STT_CAPTURE_ENABLED === 'true'
const STT_CHUNK_DURATION_MS = 15_000
const STT_MIME_TYPES = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus']
let sttRecorder: MediaRecorder | null = null
let sttStopTimer: ReturnType<typeof setTimeout> | null = null
let sttSequence = 0
let discardSttChunk = false

const overlayText = computed(() => {
  if (status.value === 'starting') return '카메라 준비 중…'
  if (status.value === 'denied') return '카메라를 사용할 수 없어요 — 권한을 허용해 주세요'
  return '' // ready
})

async function startPreview(): Promise<void> {
  status.value = 'starting'
  try {
    const { OpenVidu } = await import('openvidu-browser')
    if (disposed) return
    ov = new OpenVidu()
    // targetElement 없이 로컬 publisher 생성 → 세션 연결 없이 카메라 프리뷰만(getUserMedia)
    publisher = await ov.initPublisherAsync(undefined, {
      publishAudio: micOn.value,
      publishVideo: cameraOn.value,
      resolution: '640x480',
      mirror: true,
    })
    if (disposed) {
      stopTracks()
      return
    }
    if (videoRef.value) publisher.addVideoElement(videoRef.value)
    status.value = 'ready'
  } catch (error) {
    // 권한 거부·카메라 부재 등 — 콘솔이 죽지 않도록 플레이스홀더로 떨어진다
    console.warn('[live/media] 송출 프리뷰 시작 실패:', error)
    if (!disposed) status.value = 'denied'
  }
}

function toggleCamera(): void {
  cameraOn.value = !cameraOn.value
  publisher?.publishVideo(cameraOn.value)
}

function toggleMic(): void {
  micOn.value = !micOn.value
  publisher?.publishAudio(micOn.value)
  if (micOn.value) startSttCapture()
  else stopSttCapture(false)
}

function supportedSttMimeType(): string | undefined {
  if (typeof MediaRecorder === 'undefined') return undefined
  return STT_MIME_TYPES.find((type) => MediaRecorder.isTypeSupported(type))
}

/** 방송 중인 판매자 마이크 트랙을 15초 단위 파일로 순차 수집한다. */
function startSttCapture(): void {
  if (
    !sttCaptureEnabled ||
    disposed ||
    !broadcasting ||
    live.auctionStatus !== 'LIVE' ||
    !micOn.value ||
    sttRecorder !== null ||
    typeof MediaRecorder === 'undefined'
  ) {
    return
  }
  const roomId = live.roomId
  const track = publisher?.stream?.getMediaStream?.()?.getAudioTracks()[0]
  if (roomId === null || !track || track.readyState !== 'live') return

  const mimeType = supportedSttMimeType()
  const inputStream = new MediaStream([track])
  const recorder = new MediaRecorder(inputStream, mimeType ? { mimeType } : undefined)
  const parts: BlobPart[] = []
  const startedAt = new Date()
  discardSttChunk = false
  sttRecorder = recorder

  recorder.addEventListener('dataavailable', (event) => {
    if (event.data.size > 0) parts.push(event.data)
  })
  recorder.addEventListener('stop', () => {
    if (sttStopTimer !== null) clearTimeout(sttStopTimer)
    sttStopTimer = null
    sttRecorder = null

    if (!discardSttChunk && parts.length > 0) {
      const sequence = sttSequence++
      const audio = new Blob(parts, { type: recorder.mimeType || mimeType || 'audio/webm' })
      void uploadCatchUpAudioChunk(roomId, {
        sequence,
        startedAt: startedAt.toISOString(),
        endedAt: new Date().toISOString(),
        audio,
      }).catch((error) => {
        // 요약 보조 기능 실패는 송출을 막지 않는다. 바이너리·발화 내용은 로그에 남기지 않는다.
        console.warn('[live/media] 방송 요약용 음성 청크 전송 실패:', error)
      })
    }

    if (!disposed && broadcasting && live.auctionStatus === 'LIVE' && micOn.value) {
      queueMicrotask(startSttCapture)
    }
  })
  recorder.addEventListener('error', (event) => {
    console.warn('[live/media] 방송 요약용 음성 수집 실패:', event)
  })
  recorder.start()
  sttStopTimer = setTimeout(() => {
    if (recorder.state !== 'inactive') recorder.stop()
  }, STT_CHUNK_DURATION_MS)
}

/** 현재 파일을 닫는다. discard=true는 화면 이탈처럼 전송할 필요가 없는 경우다. */
function stopSttCapture(discard: boolean): void {
  if (sttStopTimer !== null) clearTimeout(sttStopTimer)
  sttStopTimer = null
  discardSttChunk = discard
  if (sttRecorder?.state !== 'inactive') sttRecorder?.stop()
}

/**
 * 송출 중인 비디오 트랙을 갈아끼운다 (S15P11A704-699). 모자이크 ON/OFF 양쪽이 이 함수만 쓴다.
 *
 * **세션이 있을 때만** `publisher.replaceTrack`을 쓴다.
 *  - 세션 있음(startBroadcast가 connect한 뒤): replaceTrackAux(Publisher.js:613)가
 *    `stream.isLocalStreamPublished` 분기에서 네이티브 `RTCRtpSender.replaceTrack`을 부른다
 *    (Publisher.js:788) — 재협상 없이 시청자 쪽 트랙이 바뀐다. 방송 중 토글이 여기로 간다.
 *  - 세션 없음(목 모드 전체 + 실 모드에서 방송 시작 전): **replaceTrack을 부르면 터진다.**
 *    미발행 분기(Publisher.js:632)가 replaceTrackInMediaStream을 타고, 그 안에서
 *    `OpenVidu.sendTrackChangedEvent`(OpenVidu.js:618)가 `this.session.emitEvent`를 부르는데
 *    `initSession()` 전에는 그 `session`이 undefined다 → `TypeError: reading 'emitEvent'`.
 *    (처음엔 그 분기가 "로컬 MediaStream만 바꾸니 세션 없이도 된다"고 읽고 그대로 썼다 — 실기기에서
 *    프리뷰가 검게 죽으며 틀린 것으로 드러났다. 코드를 읽어 세운 가정이라 타입체크·테스트가 못 잡았다.)
 *    더 나쁜 건 **터지기 전에 기존 트랙 stop() + removeTrack을 이미 끝냈다는 것**이라(Publisher.js:750~753)
 *    호출자가 catch해도 스트림에 죽은 트랙만 남아 프리뷰가 검게 굳는다.
 *    그래서 세션이 없으면 같은 일을 우리가 직접 한다 — 스왑을 우리가 소유해야 실패해도 되돌릴 수 있다.
 *    수명 규칙(교체당한 트랙을 stop한다)은 openvidu 경로와 일부러 맞춰, ON/OFF 로직이 두 경로에서 똑같이 돈다.
 *
 * ⚠️ 프리뷰가 재바인딩 없이 따라오는 근거는 두 경로가 같다: addVideoElement가 `<video>.srcObject`에 물려 둔
 *    **그 MediaStream 객체를** removeTrack/addTrack으로 고친다(openvidu도 Publisher.js:736에서 같은 짓을 한다).
 */
async function swapVideoTrack(next: MediaStreamTrack): Promise<void> {
  const pub = publisher
  if (!pub) throw new Error('송출 프리뷰가 준비되지 않았습니다')
  if (session) {
    await pub.replaceTrack(next)
    return
  }
  const stream = pub.stream?.getMediaStream?.()
  if (!stream) throw new Error('로컬 미디어 스트림을 찾을 수 없습니다')
  const current = stream.getVideoTracks()[0]
  // 붙이고 나서 멈춘다 — 순서가 반대면 addTrack이 실패했을 때 되돌릴 트랙까지 이미 죽어 있다.
  if (current) stream.removeTrack(current)
  stream.addTrack(next)
  current?.stop()
}

/**
 * 스왑이 도중에 죽어 프리뷰가 검게 남는 것을 막는다 — 살아 있는 트랙을 스트림에 되붙인다.
 * 이미 살아 있는 트랙이 있으면(대개 아직 아무것도 못 바꾼 실패다) 손대지 않는다.
 * @returns fallback을 실제로 썼는가. false면 호출자가 그 트랙을 직접 정리해야 한다.
 */
function restoreCamera(fallback: MediaStreamTrack): boolean {
  const stream = publisher?.stream?.getMediaStream?.()
  if (!stream) return false
  if (stream.getVideoTracks().some((track) => track.readyState === 'live')) return false
  for (const dead of stream.getVideoTracks()) stream.removeTrack(dead)
  stream.addTrack(fallback)
  return true
}

/**
 * 얼굴 모자이크 토글 (S15P11A704-699).
 * 처리는 전부 판매자 브라우저에서 하고, 서버로는 **처리된 트랙만** 나간다. 교체 경로는 swapVideoTrack 참조.
 */
async function toggleMosaic(): Promise<void> {
  if (!publisher || mosaicBusy.value) return
  mosaicBusy.value = true
  try {
    if (mosaicOn.value) await stopMosaic()
    else await startMosaic()
  } finally {
    mosaicBusy.value = false
  }
}

async function startMosaic(): Promise<void> {
  const camera = publisher?.stream?.getMediaStream?.()?.getVideoTracks()[0]
  if (!camera) return
  // 파이프라인 입력은 반드시 **클론**이다. 교체된 트랙은 두 경로 모두 stop되므로(openvidu는
  // Publisher.js:750~752, 세션 없는 경로는 swapVideoTrack이 직접) 원본을 그대로 물리면 갈아끼우는
  // 순간 입력이 끊겨 캔버스가 검게 언다. 클론은 카메라 소스를 공유하되 수명이 독립이라 계속 산다.
  // 동시에 이 클론이 **토글을 끌 때 되돌릴 트랙**이다 — 원본은 그때 이미 죽어 있다.
  const source = camera.clone()
  let created: FaceMosaic | null = null
  try {
    // 모듈째 동적 import — 켜기 전에는 mediapipe가 번들·네트워크 어디에도 등장하지 않는다(210·208 패턴).
    const { createFaceMosaic } = await import('./useFaceMosaic')
    created = await createFaceMosaic(source)
    if (disposed) {
      created.dispose()
      source.stop()
      return
    }
    await swapVideoTrack(created.track)
    mosaic = created
    mosaicSource = source
    mosaicOn.value = true
    mosaicFailed.value = false
  } catch (error) {
    // 준비 실패(wasm·모델 로드, 캔버스 미지원, 교체 거부)로 **송출·프리뷰가 깨지면 안 된다.**
    console.warn('[live/media] 얼굴 모자이크를 켤 수 없습니다:', error)
    created?.dispose()
    // 스왑이 도중에 죽었으면 살아 있는 클론을 그 자리에 되붙인다. 아직 아무것도 못 바꾼 실패라면
    // 원본이 멀쩡히 남아 있으므로 restoreCamera가 false를 주고, 그때 클론은 우리가 버린다.
    if (!restoreCamera(source)) source.stop()
    mosaicOn.value = false
    mosaicFailed.value = true
  }
}

async function stopMosaic(): Promise<void> {
  if (!mosaic || !mosaicSource) return
  try {
    // 원본으로 먼저 되돌리고 나서 파이프라인을 내린다. 순서가 반대면 아직 송출 중인 캔버스 트랙이
    // 멈춰 시청자 화면이 잠시 검어진다.
    await swapVideoTrack(mosaicSource)
  } catch (error) {
    // 되돌리지 못했으면 파이프라인을 살려 둔다 — 여기서 dispose하면 송출 중인 트랙이 죽는다.
    console.warn('[live/media] 원본 카메라로 되돌리지 못했습니다:', error)
    return
  }
  mosaic.dispose()
  mosaic = null
  mosaicSource = null // 이제 publisher가 이 트랙을 들고 있다 — 해제는 stopTracks 몫이다
  mosaicOn.value = false
}

/** 권한 거부 후 사용자가 브라우저 설정에서 허용했을 때 재마운트 없이 복구하는 경로(리뷰 1) */
function retry(): void {
  stopTracks()
  publisher = null
  void startPreview()
}

/** 로컬 카메라·마이크 트랙 해제 — 세션이 없으므로 트랙을 직접 멈춰야 장치가 풀린다 */
function stopTracks(): void {
  // 모자이크가 켜져 있으면 publisher가 든 것은 캔버스 트랙이고, 카메라를 실제로 쥔 것은 그 입력 클론이다.
  // 여기서 함께 놓지 않으면 화면을 떠난 뒤에도 카메라 표시등이 켜진 채 남는다(699).
  mosaic?.dispose()
  mosaic = null
  mosaicSource?.stop()
  mosaicSource = null
  mosaicOn.value = false // 파이프라인이 없어졌으니 토글도 내린다(retry로 카메라를 새로 잡는 경로)
  publisher?.stream
    ?.getMediaStream?.()
    ?.getTracks()
    .forEach((track) => track.stop())
}

/**
 * 실제 송출 시작 (LIVE-01, S15P11A704-363). 프리뷰용 publisher를 실 세션에 publish한다.
 * 목 모드는 OpenVidu 서버가 없어 건너뛴다(프리뷰만). 방 라이브 전환 + 프리뷰 준비 후 1회 실행.
 */
async function startBroadcast(): Promise<void> {
  if (isMockApi() || broadcasting) return
  if (!ov || !publisher) return
  const roomId = live.roomId
  if (roomId === null) return
  broadcasting = true
  try {
    const { token } = await issuePublisherToken(roomId)
    if (disposed) return
    session = ov.initSession()
    await session.connect(token)
    if (disposed) {
      session.disconnect()
      session = null
      return
    }
    await session.publish(publisher)
    broadcastError.value = false
    startSttCapture()
  } catch (error) {
    console.warn('[live/media] 송출 시작 실패:', error)
    session?.disconnect()
    session = null
    broadcasting = false
    // 실패를 판매자에게 드러낸다 — watch는 상태가 안 변해 재발화하지 않으므로, 재시도는 retryBroadcast가 직접 부른다.
    if (!disposed) broadcastError.value = true
  }
}

/** 송출 실패 후 판매자 수동 재시도 — auctionStatus·status가 그대로라 watch가 다시 안 도니 직접 호출한다(리뷰 1). */
function retryBroadcast(): void {
  broadcastError.value = false
  void startBroadcast()
}

/** 명세 상한 5MB. 넘으면 품질을 낮춰 한 번만 다시 뜬다(413 IMAGE_TOO_LARGE 회피). */
const SNAPSHOT_MAX_BYTES = 5 * 1024 * 1024
/** 스냅샷을 이미 뜬 경매 id — 경매당 1회 가드(LIVE-06 무결성 규칙: 경매당 스냅샷은 하나). */
let snapshotTakenFor: number | null = null

/**
 * 지금 프리뷰에 보이는 프레임을 jpeg Blob으로 뜬다.
 *
 * 소스가 `<video>`인 것이 핵심이다 — 모자이크가 켜져 있으면 publisher의 MediaStream 비디오 트랙이
 * 이미 캔버스 트랙으로 갈려 있고(swapVideoTrack), 이 `<video>`는 그 스트림을 그대로 물고 있다.
 * 즉 **시청자가 실제로 본 화면**이 떠진다. 원본 카메라 트랙에서 뜨면 가린 얼굴이 증빙에 남아
 * 모자이크(699)가 무의미해진다.
 */
async function captureFrame(video: HTMLVideoElement): Promise<Blob | null> {
  // 첫 프레임 전이면 0이다 — 0×0 캔버스는 toBlob이 null을 주거나 빈 이미지가 된다.
  if (video.videoWidth === 0 || video.videoHeight === 0) return null
  const canvas = document.createElement('canvas')
  canvas.width = video.videoWidth
  canvas.height = video.videoHeight
  const ctx = canvas.getContext('2d')
  if (!ctx) return null
  ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
  const encode = (quality: number): Promise<Blob | null> =>
    new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality))
  const blob = await encode(0.85)
  return blob !== null && blob.size > SNAPSHOT_MAX_BYTES ? encode(0.5) : blob
}

/**
 * LIVE-06 낙찰 스냅샷 캡처·업로드 (S15P11A704-704).
 *
 * 트리거를 이 컴포넌트에 둔 이유는 둘이다 — ① 떠야 할 프레임이 **여기가 들고 있는 `<video>`**뿐이고
 * (콘솔은 그 요소에 손이 닿지 않는다), ② 이 컴포넌트는 판매자 콘솔에서만 마운트되므로
 * 「판매자가 아니면 무동작」이 구조적으로 성립한다.
 *
 * `broadcasting`을 조건에 넣는 것은 목 모드(서버 없음)·프리뷰만(송출 전)·송출 실패를 한 번에 거르기
 * 위해서다 — 시청자에게 나간 적 없는 화면은 증빙이 아니다.
 *
 * 실패는 콘솔 경고로 끝낸다. 증빙은 best-effort고, 여기서 던지면 낙찰 오버레이·방송 종료 흐름이 막힌다.
 */
async function captureWinningSnapshot(): Promise<void> {
  const auctionId = live.roomId
  const video = videoRef.value
  if (!broadcasting || disposed || auctionId === null || video === null) return
  // 재진입 가드는 **await 앞에** 세운다. 실패해도 다시 찍지 않는다 — 재시도 경로가 없고,
  // 종료 이벤트가 재전달되는 경우(재접속 등) 같은 프레임을 또 올릴 이유도 없다.
  if (snapshotTakenFor === auctionId) return
  snapshotTakenFor = auctionId
  try {
    const image = await captureFrame(video)
    if (image === null) throw new Error('캡처할 프레임이 없습니다')
    await uploadLiveSnapshot(auctionId, image)
  } catch (error) {
    console.warn('[live/media] 낙찰 스냅샷 업로드 실패:', error)
  }
}

onMounted(startPreview)

// 방이 라이브로 전환(auctionStatus='LIVE')되고 프리뷰가 준비되면 실제 송출을 시작한다(실 모드).
// 둘 중 나중에 충족되는 시점에 걸리도록 두 값을 함께 본다. 목 모드는 startBroadcast가 자체 스킵.
watch(
  () => [live.auctionStatus, status.value] as const,
  ([auctionStatus, previewStatus]) => {
    if (auctionStatus === 'LIVE' && previewStatus === 'ready') void startBroadcast()
    if (auctionStatus !== 'LIVE') stopSttCapture(false)
  },
)

// LIVE-06 낙찰 순간 증빙 스냅샷 (S15P11A704-704) — 소켓 종료 이벤트가 오면 한 번 뜬다.
watch(
  () => live.endEvent,
  (event) => {
    // **낙찰(AUCTION_SETTLED)만** 찍는다. 명세가 「낙찰 시점」이라 유찰(NO_SALE)·강제 종료(CANCELED)는
    // 대상이 아니고, AUCTION_ENDED는 아직 집계 시작 신호라 낙찰자가 정해지지 않았다.
    // (서버도 낙찰 아닌 경매를 409 AUCTION_NOT_SETTLED로 막지만, 안 찍을 것을 올려 보고 받을 이유가 없다.)
    if (event?.type === 'AUCTION_SETTLED') void captureWinningSnapshot()
  },
)

onUnmounted(() => {
  disposed = true
  stopSttCapture(true)
  session?.disconnect() // 송출 세션 종료(연결됐던 경우)
  session = null
  stopTracks() // 로컬 트랙 해제 — 세션이 없던(프리뷰만) 경우에도 장치를 푼다
  publisher = null
  ov = null
})
</script>

<template>
  <div class="flex flex-col gap-2">
    <!-- 프리뷰 박스 (다크 스테이지) -->
    <div class="bg-stage relative aspect-video w-full overflow-hidden rounded-xl">
      <!-- 로컬 프리뷰는 반드시 muted (자기 마이크 하울링 방지) -->
      <video
        v-show="status === 'ready' && cameraOn"
        ref="videoRef"
        class="h-full w-full object-cover"
        autoplay
        playsinline
        muted
      ></video>
      <div
        v-if="status !== 'ready' || !cameraOn"
        class="text-on-dark-dim absolute inset-0 flex flex-col items-center justify-center gap-2 px-4 text-center"
        role="status"
      >
        <svg
          class="h-8 w-8 opacity-70"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.6"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M23 7l-6 4 6 4V7Z" />
          <rect x="1" y="5.5" width="15" height="13" rx="2.5" />
        </svg>
        <p class="text-[12.5px]">
          {{ !cameraOn && status === 'ready' ? '카메라 꺼짐' : overlayText }}
        </p>
        <button
          v-if="status === 'denied'"
          type="button"
          class="text-on-dark rounded-full border border-white/20 px-4 py-1.5 text-[12px] font-medium transition-colors hover:bg-white/10 motion-reduce:transition-none"
          @click="retry"
        >
          다시 시도
        </button>
      </div>

      <!-- 실 송출 실패 — 프리뷰(카메라)는 정상이라 이 배너가 없으면 조용히 실패한다. 명시 + 직접 재시도(리뷰 1·2) -->
      <div
        v-if="broadcastError"
        class="text-on-dark bg-ink/85 absolute inset-x-0 bottom-0 flex items-center gap-2 px-3 py-2 text-[11.5px] backdrop-blur-sm"
        role="alert"
      >
        <svg
          class="h-3.5 w-3.5 flex-none"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path
            d="M10.3 3.9 2.6 17.3A2 2 0 0 0 4.3 20.3h15.4a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"
          />
          <path d="M12 9v4" />
          <path d="M12 17h.01" />
        </svg>
        <span class="flex-1">송출 연결에 실패해 시청자에게 방송이 나가지 않고 있어요.</span>
        <button
          type="button"
          class="text-on-dark flex-none rounded-full border border-white/25 px-2.5 py-1 text-[11px] font-medium transition-colors hover:bg-white/10 motion-reduce:transition-none"
          @click="retryBroadcast"
        >
          다시 시도
        </button>
      </div>
    </div>

    <!-- 카메라·마이크 토글 -->
    <div class="flex gap-2">
      <button
        type="button"
        class="border-line flex flex-1 items-center justify-center gap-1.5 rounded-[10px] border px-3 py-2 text-[13px] font-medium transition-colors disabled:opacity-50 motion-reduce:transition-none"
        :class="cameraOn ? 'text-ink bg-surface' : 'text-faint bg-line/40'"
        :aria-pressed="cameraOn"
        :disabled="status !== 'ready'"
        @click="toggleCamera"
      >
        <svg
          class="h-4 w-4"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M23 7l-6 4 6 4V7Z" />
          <rect x="1" y="5.5" width="15" height="13" rx="2.5" />
        </svg>
        카메라 {{ cameraOn ? '켜짐' : '꺼짐' }}
      </button>
      <button
        type="button"
        class="border-line flex flex-1 items-center justify-center gap-1.5 rounded-[10px] border px-3 py-2 text-[13px] font-medium transition-colors disabled:opacity-50 motion-reduce:transition-none"
        :class="micOn ? 'text-ink bg-surface' : 'text-faint bg-line/40'"
        :aria-pressed="micOn"
        :disabled="status !== 'ready'"
        @click="toggleMic"
      >
        <svg
          class="h-4 w-4"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <rect x="9" y="2.5" width="6" height="11" rx="3" />
          <path d="M5 11a7 7 0 0 0 14 0M12 18v3" />
        </svg>
        마이크 {{ micOn ? '켜짐' : '꺼짐' }}
      </button>
      <!-- 얼굴 모자이크 (S15P11A704-699) — 켜면 시청자와 이 프리뷰 둘 다 처리된 화면을 본다.
           준비 실패는 title/aria-label로만 알린다: 송출은 멀쩡하므로 배너로 막을 일이 아니다. -->
      <button
        type="button"
        data-mosaic
        class="border-line flex flex-1 items-center justify-center gap-1.5 rounded-[10px] border px-3 py-2 text-[13px] font-medium transition-colors disabled:opacity-50 motion-reduce:transition-none"
        :class="mosaicOn ? 'text-ink bg-surface' : 'text-faint bg-line/40'"
        :aria-pressed="mosaicOn"
        :disabled="status !== 'ready' || mosaicBusy"
        :title="mosaicFailed ? '모자이크를 사용할 수 없어요' : undefined"
        :aria-label="mosaicFailed ? '모자이크 — 모자이크를 사용할 수 없어요' : undefined"
        @click="toggleMosaic"
      >
        <svg
          class="h-4 w-4"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <rect x="3" y="3" width="7" height="7" rx="1" />
          <rect x="14" y="3" width="7" height="7" rx="1" />
          <rect x="3" y="14" width="7" height="7" rx="1" />
          <rect x="14" y="14" width="7" height="7" rx="1" />
        </svg>
        모자이크 {{ mosaicOn ? '켜짐' : '꺼짐' }}
      </button>
    </div>
  </div>
</template>
