<script setup lang="ts">
// WebRTC 라이브 스테이지 (S15P11A704-210) — LiveLayout #video 슬롯을 채우는 시청자 수신 뷰 (LIVE-01).
//
// 실 모드: 209의 subscriber 토큰으로 OpenVidu 세션에 연결해 판매자(1:N) 스트림을 <video>에 바인딩한다.
// 목 모드: OpenVidu 서버가 없으므로 연결하지 않고 플레이스홀더만 보여준다(현재 데모 경로).
// 슬롯 부모(스테이지)를 100% 채우고 자체 크기·위치는 잡지 않는다(docs/03 §4, docs/02). 스테이지는 다크라 on-dark 토큰만.
//
// 네트워크 대응 (LIVE-04, S15P11A704-213): 자동 재연결(reconnecting/reconnected/sessionDisconnected) 상태 처리.
// 시청자 측 해상도는 WebRTC/SFU가 대역폭에 맞춰 자동 조절한다.
//
// 네트워크 품질 배지(networkQualityLevelChanged)는 제거했다(S15P11A704-359) — 그 이벤트는 OpenVidu
// PRO·ENTERPRISE 전용이라, 미디어 서버를 CE로 확정(S15P11A704-339, docs/01)한 이상 CE에선 영원히 발화하지 않는
// 죽은 경로였다. 대역폭 대응은 WebRTC/SFU 자동 조절에 맡긴다(필요 시 getStats() 자체 판정은 별도 티켓).
//
// ⚠️ OpenVidu 서버가 아직 없어 실 연결은 미검증이다 — 실 모드 경로는 BE·미디어 서버 준비 후 검증한다.
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
// 값(OpenVidu)은 connect()에서 동적 import한다 — SDK가 ~200KB라 목 모드 번들에 딸려 들어가지 않게(208과 같은 패턴).
// 타입만 정적 import(런타임 코드 아님).
import type { OpenVidu, Session, StreamEvent, SessionDisconnectedEvent } from 'openvidu-browser'
import { isMockApi } from '@/shared/api/mock'
import { useMockDemoFlag } from '@/shared/lib/useMockDemoFlag'
import { issueSubscriberToken } from './api'

const props = defineProps<{
  roomId: number
  /**
   * 전체화면 대상 — 프레임(LiveLayout)이 `#video` 슬롯 프롭으로 내주는 스테이지 컨테이너
   * (S15P11A704-433). 자세한 배경은 아래 toggleFullscreen 위 주석. 없으면 자기 루트를 쓴다.
   */
  fullscreenTarget?: HTMLElement | null
}>()

// 'no-publisher'와 'stream-ended'는 둘 다 "영상이 없다"지만 사용자에게 뜻이 반대다(S15P11A704-426) —
// 전자는 아직 시작 전(기다리면 시작된다), 후자는 보던 방송이 사라짐(다시 이어질 수 있다).
// 한 상태로 뭉쳐 두면 방금까지 보던 방송이 끊겼는데 "아직 시작 안 함"으로 안내돼,
// 계속 기다려야 할지 나가야 할지 판단할 근거가 없어진다.
type StageStatus =
  'connecting' | 'live' | 'reconnecting' | 'no-publisher' | 'stream-ended' | 'error' | 'mock'
const status = ref<StageStatus>('connecting')

const videoRef = ref<HTMLVideoElement | null>(null)
// SDK 객체는 반응형이 아니다(스트림 핸들·소켓을 품고 있어 reactive로 감싸면 안 된다)
let ov: OpenVidu | null = null
let session: Session | null = null
// 스트림 수신 여부 — 재연결 복귀 지점 판정 + 남길 프레임 유무로 오버레이/배지를 가른다(반응형, 리뷰 5)
const hasStream = ref(false)
// "지금 받고 있나"(hasStream)와 "받은 적 있나"(everHadStream)는 다르다 — 재연결 복귀 문구는 후자로 갈라야 한다.
// 정상 종료로 hasStream이 내려간 뒤 별개의 재연결 사이클이 돌면, hasStream만으로는 "받은 적 없음"과
// 구분되지 않아 중단 안내가 '아직 시작 안 함'으로 되돌아간다(!134 리뷰 1). 세션 단위 값이라 connect()에서 함께 내린다.
const everHadStream = ref(false)
// 언마운트 여부 — await 사이 화면을 나가면 이후 만들어진 세션을 정리 못 하는 누수를 막는다(리뷰 1)
let disposed = false

// 자동재생 정책 대응 (S15P11A704-421 → 476에서 방향 전환).
//
// 421은 **음소거로 시작**해 재생을 보장하고 「소리 켜기」로 풀게 했다. 476에서 기본을 소리 켬으로 뒤집는다 —
// 다만 정책을 무시할 수는 없다: 오디오가 있는 원격 스트림은 사용자 제스처 없이 자동재생이 차단되고,
// 막히면 playing이 오지 않아 status가 connecting에 갇힌다(검은 화면 + 「영상에 연결 중」). 그래서
// **소리 켠 상태로 먼저 시도하고 거부당하면 음소거로 낮춰 재시도**한다(tryPlay). 폴백된 경우엔 아이콘에
// 사선이 켜져 상태가 정직하게 보이고, 그걸 누르는 클릭이 곧 제스처가 되어 소리가 켜진다.
// 목 모드는 미디어 서버가 없어 **소리 자체가 없다.** 그런데 기본값이 소리 켬이면 사선 없는 스피커로
// 「소리 나는 중」이라고 그린다(!185 리뷰 2) — 465가 「슬라이더 0이면 사선을 켠다」로 세운 기준과 같은
// 종류의 거짓말이고, 하필 그 화면이 시연 화면이다(475로 알약이 기본 노출이 됐다).
// 그래서 목 모드만 무음으로 시작한다. 슬라이더도 함께 0으로 — 477의 「음량 100인데 무음」을 만들지 않는다.
const startsSilent = isMockApi()
const muted = ref(startsSilent)

// 컨트롤은 볼 게 있을 때만 노출한다 — 실 라이브(프레임 유지 재연결 포함) 또는 목 데모(플레이스홀더 전환).
// 463까지 canToggle이던 이름이다 — 그 "토글"이 사라져 이름값을 못 하게 됐다(!178 리뷰 nit 2).
//
// ⚠️ LIVE-04 저대역폭 수동 대응(「영상 끄기 · 소리만」, S15P11A704-378)은 제거됐다(S15P11A704-463).
// 자동 감지(networkQualityLevelChanged)는 CE 미지원으로 이미 없었으므로(S15P11A704-359), 이제 대역폭 대응은
// **서버·SFU 자동 조절에만** 의존한다. 그것이 실제로 켜져 있는지는 S15P11A704-466에서 확인 중이다 —
// 답이 "안 된다"면 378을 근거로 되살려야 한다.
const canShowControls = computed(
  () =>
    status.value === 'live' ||
    status.value === 'mock' ||
    (status.value === 'reconnecting' && hasStream.value),
)

// 음량 (S15P11A704-465) — 스피커에 마우스를 올리면 펼쳐지는 슬라이더로 조절한다(유튜브 방식).
// **퍼센트로 들고 있다.** <video>.volume은 0~1이지만 <input type="range">의 값이 그대로 리더에 읽히므로
// (0.65보다 65가 낫다) 경계에서 한 번만 나눈다. 단위가 둘로 갈리지 않게 상태는 이쪽 하나뿐이다.
//
// DOM 반영은 **muted와 같은 방식으로 템플릿 바인딩에 맡긴다**(`:volume`, !182 리뷰 1). 명령형으로 쓰면
// volumePercent를 바꾸는 경로가 늘어날 때 동기화를 잊는 순간 화면 값과 실제 소리가 어긋나고, 예외도
// 로그도 안 남는다. 네이티브 컨트롤(controls)을 쓰지 않으므로 역방향 동기화도 필요 없다.
const volumePercent = ref(startsSilent ? 0 : 100)

// **"소리가 안 나는 상태"는 muted 하나로 판정되지 않는다.** 슬라이더를 0까지 내리면 muted는 false인데도
// 무음이다 — 그때 스피커 아이콘이 「소리 나는 중」으로 보이면 화면이 거짓말을 한다(유튜브도 0이면 사선을 켠다).
const silent = computed(() => muted.value || volumePercent.value === 0)

// 자동재생 정책에 막혀 음소거로 시작했는지 (S15P11A704-479).
//
// 이 사실의 유일한 표시가 클러스터 안의 사선 아이콘인데, 그 클러스터는 hover·focus에서만 보인다(464).
// 즉 정책에 막힌 사용자는 **스테이지에 마우스를 올릴 때까지 소리가 없는 이유를 모른다** — 첫 방문·시크릿
// 창은 대체로 막히므로 드문 경우가 아니다. 그래서 클러스터 밖에 안내를 띄운다.
//
// 안내에 버튼을 붙인 이유: 안내만 하면 복구 수단(슬라이더)이 여전히 hover 안에 있어 같은 문제가 남는다.
// 476이 없앤 「소리 켜기」 글자 라벨과 충돌하지 않는다 — 그건 **클러스터의 상시 컨트롤**을 아이콘만으로
// 줄인 결정이고, 이건 정책 폴백에서만 잠깐 뜨는 별개의 안내다.
//
// 사용자가 **스스로** 음소거한 경우에는 뜨지 않는다(그건 자기가 한 일이라 안내가 잔소리다).
const mutedByPolicy = ref(false)

/** 안내를 접는다 — 소리가 켜졌거나, 사용자가 스스로 음소거했거나, 직접 닫았을 때. */
function dismissPolicyNotice(): void {
  mutedByPolicy.value = false
}

/**
 * 무음(볼륨 0)이면 들리는 기본값으로 올린다.
 *
 * 477의 mute()가 muted와 슬라이더를 **함께** 내리므로, 되돌리는 쪽도 함께 올려야 한다 —
 * muted만 풀면 볼륨 0이 남아 「소리를 켰는데 안 들린다」가 된다. 같은 실수를 두 곳에서 했고
 * (479의 [소리 켜기], 513의 connect()) 둘이 같은 한 줄을 들고 있었다(!233 리뷰 1). 규칙에 이름을 붙여
 * 한 곳으로 모은다 — 앞으로 「소리를 켜는」 경로가 늘어도 이 함수를 부르면 된다.
 *
 * 0일 때만 손대는 것이 중요하다. 무조건 100으로 올리면 사용자가 고른 음량(예: 30%)이 튄다.
 *
 * ℹ️ 알려진 한계(!235 리뷰 1): **슬라이더를 직접 0으로 내린 사용자**도 방 전환에서 100이 된다.
 * 폴백이 남긴 0과 구분할 수 없기 때문이다 — 구분하려면 「의도적 0」 플래그가 필요하고, 그건 476의
 * 「새 연결은 들리는 상태로 시작」과 부딪힌다. 그래서 구분하지 않는다.
 */
function restoreVolumeIfSilent(): void {
  if (volumePercent.value === 0) volumePercent.value = 100
}

/** 안내의 「소리 켜기」 — 정책 폴백에서 나오는 유일한 원클릭 복구다. */
function unmuteFromNotice(): void {
  muted.value = false
  restoreVolumeIfSilent()
  dismissPolicyNotice()
}

// 스피커 클릭 — 무음이면 아무 일도 하지 않는다(S15P11A704-480).
// `disabled` 대신 `aria-disabled`를 쓰기 때문에 브라우저가 클릭을 막아 주지 않는다. 그래서 여기서 막는다.
// mute()에 가드를 넣지 않는 이유: tryPlay 폴백은 volume이 이미 0이어도(방 전환 후 등) muted를 세워야 한다 —
// 자동재생 정책이 보는 값이 muted다. 가드를 안쪽에 두면 그 경로가 조용히 죽는다.
function onMuteClick(): void {
  if (silent.value) return
  mute()
  // 여기서 안내(mutedByPolicy)를 접지 않는 이유: **닿을 수 없는 코드가 된다.**
  // 안내가 떠 있다는 것은 폴백됐다는 뜻이고 = 무음이므로, 위 가드에서 이미 반환한다(!479 뮤테이션에서
  // 그 줄을 지워도 아무 테스트가 깨지지 않아 발견했다). 「사용자가 스스로 음소거」 경로에서 안내가 뜨지
  // 않는 것은 mutedByPolicy를 애초에 세우지 않기 때문이다 — 접을 일 자체가 없다.
}

// 음소거로 들어가는 유일한 문 (S15P11A704-477) — **슬라이더도 0으로 함께 내린다.**
// 465는 muted와 volumePercent를 따로 뒀는데, 그러면 스피커로 음소거했을 때 「음량 70인데 무음」이라는
// 모순된 화면이 남는다.
//
// **되돌리는 문은 슬라이더뿐이다.** 스피커는 음소거 전용이고 눌러서 다시 켜지지 않는다(477 요청).
// 그래서 무음일 때 버튼을 disabled로 둔다 — 눌러도 아무 일이 없는 버튼을 남기지 않는다. 복구 방법은
// 라벨에 적는다. disabled여도 이 클러스터는 계속 쓸 수 있다: 포인터가 disabled 버튼을 통과해 감싼
// 그룹에 hover가 걸리므로 슬라이더가 펼쳐지고, 키보드는 버튼을 건너뛰고 슬라이더로 바로 들어간다.
//
// ⚠️ 「어차피 0으로 내리니 muted 플래그는 필요 없다」가 아니다 — **자동재생 정책은 video.muted를 본다.**
// volume=0만으로는 「소리 있는 재생」으로 판정되어 476의 폴백이 통하지 않는다. 둘 다 유지한다.
function mute(): void {
  muted.value = true
  volumePercent.value = 0
}

// 소리 켠 상태로 재생을 시도하고, 자동재생 정책에 막히면 음소거로 낮춰 재시도한다(S15P11A704-476).
//
// `autoplay` 속성만으로는 이 폴백을 만들 수 없다 — 브라우저가 조용히 거부하면 우리가 알 방법이 없고,
// playing이 오지 않아 connecting에 갇힌다. 직접 play()를 불러 거부를 잡아야 한다.
// 두 번째 시도까지 막히는 경우(정책이 음소거 재생마저 막는 드문 설정)는 사용자 제스처를 기다린다 —
// 이때 화면은 「영상에 연결 중」으로 남고, 그게 사실이다.
//
// 폴백된 사실은 **클러스터 밖 안내**로 알린다(S15P11A704-479) — mutedByPolicy 참조.
// 클러스터 안의 사선 아이콘만으로는 hover 전까지 보이지 않아 이유를 알 수 없었다.
async function tryPlay(): Promise<void> {
  const v = videoRef.value
  if (!v) return
  try {
    await v.play()
  } catch {
    mute()
    mutedByPolicy.value = true // 클러스터 밖 안내를 띄운다(479)
    // 두 번째 play()가 같은 틱에서 돌아 Vue의 DOM 반영을 기다릴 수 없다 — 정책이 보는 muted를 직접 세운다.
    v.muted = true
    v.volume = 0
    try {
      await v.play()
    } catch (error) {
      console.warn('[live/media] 음소거 재생도 거부됨 — 사용자 제스처를 기다린다:', error)
    }
  }
}

// 슬라이더 조작 (S15P11A704-465 확정 결정) — **움직이면 음소거가 풀린다**(유튜브와 동일).
// 477에서 이것이 **무음을 되돌리는 유일한 경로**가 됐다(스피커는 음소거 전용). 그래서 여기서 muted를 푸는
// 것이 필수다 — 안 풀면 아무리 올려도 소리가 안 나고 화면에서 빠져나올 방법이 없다.
// 슬라이더 조작 자체가 사용자 제스처라 이 자리에서 풀어도 자동재생 정책에 걸리지 않는다(476 폴백 뒤에도 통한다).
// 0으로 내린 것은 "음소거하겠다"는 뜻이므로 그때는 풀지 않는다 — silent가 volume 0을 함께 보므로 아이콘도 맞는다.
function onVolumeInput(event: Event): void {
  const next = Number((event.target as HTMLInputElement).value)
  volumePercent.value = next
  if (next > 0) {
    muted.value = false
    dismissPolicyNotice() // 슬라이더로 소리를 켰으면 안내는 할 일을 다했다
  }
}

// 오디오 컨트롤은 실제로 소리가 있을 때만 — 스트림이 붙어 <video>가 살아 있는 동안.
// 목 모드는 미디어 서버가 없어 hasStream이 영원히 false다. 그래서 **목 화면에는 스피커·슬라이더가 아예
// 뜨지 않는다** — 눌러도 무반응인 컨트롤을 만들지 않으려는 423의 결정이고, 그 상태로 남긴다.
//
// 다만 그 결정 때문에 *목 모드에서 이 UI를 볼 방법이 없었다*(S15P11A704-470). 실 미디어 서버 없이
// 배치·펼침·키보드를 확인해야 하는데 화면에 없으니 확인할 수가 없다.
//
// 470은 이걸 쿼리 옵트인(`?audiodemo=1`)으로 뒀는데, **쿼리를 붙여야 보이는 것 자체가 문제**였다
// (S15P11A704-475). 목 모드면 그냥 뜨게 하고 끄는 표기만 남긴다 — `?audiodemo=0`.
// 470 리뷰가 옵트인의 근거로 든 「시연도 목 모드로 돈다」는 여전히 사실이지만, 확인 비용이 그 위험보다
// 컸다. 시연에서 감춰야 하면 링크에 `?audiodemo=0`을 붙인다.
// 쿼리 파싱은 ?chatdemo와 같아 shared/lib으로 뽑았고(!184 리뷰 1), 기본값만 반대다.
const audioDemo = useMockDemoFlag('audiodemo', true)
const canControlAudio = computed(() => hasStream.value || audioDemo.value)

// 전체화면 (S15P11A704-423) — 데스크톱에서 스테이지가 344px 레일 옆 좁은 영역에 갇혀 몰입 시청이 안 되는 문제.
//
// 대상은 **프레임이 내준 컨테이너**다 (S15P11A704-433 합의). 예전엔 이 컴포넌트의 루트였는데,
// 그러면 상단 타이머 칩·prodbar(프레임이 그리는 형제 요소)가 따라오지 않아 남은 시간이 안 보였다 — BID-05 핵심 정보다.
// 그렇다고 `closest()`로 프레임 DOM을 찾아 올라가면 남의 구조를 가정하는 건 마찬가지라,
// `LiveLayout`이 `#video` 슬롯 프롭으로 대상을 건네고 여기서 받기만 한다. 방향이 한쪽이라 결합이 명시적이다.
// 안 넘어오면(단독 마운트·테스트) 종전대로 자기 루트를 쓴다.
const stageRef = ref<HTMLElement | null>(null)
/** 실제 전체화면 대상 — 프레임이 준 것 우선, 없으면 자기 루트 */
const fullscreenEl = computed<HTMLElement | null>(() => props.fullscreenTarget ?? stageRef.value)
const isFullscreen = ref(false)

// iPhone Safari엔 Element.requestFullscreen이 없다(iPadOS엔 있다). 옵셔널 호출이라 예외도 안 나고
// catch도 안 타서 버튼이 조용히 무반응이 된다 — 웹모바일이 주 환경이라 아예 감춘다(!136 리뷰 1).
// 표준 feature-detect이며 allowfullscreen 없는 iframe에서도 false다. 런타임에 바뀌지 않으므로 반응형이 아니다.
const canFullscreen = document.fullscreenEnabled === true

/**
 * iPhone Safari 폴백 (S15P11A704-441).
 *
 * iPhone Safari는 표준 API가 없는 대신 `<video>`에 비표준 `webkitEnterFullscreen()`만 준다(타입 선언에 없다).
 * 그래서 위 가드만 두면 **iPhone에는 전체화면이 아예 없다** — 웹모바일이 주 환경이고 시연도 QR 입장이라
 * 심사위원이 폰으로 본다. 상품 실물을 크게 보는 것이 이 서비스의 핵심 가치라 폴백을 둔다.
 *
 * ⚠️ 대상이 스테이지가 아니라 `<video>`라 **네이티브 플레이어가 화면을 통째로 대체한다** — 남은 시간·입찰
 * 패널·채팅·우리 컨트롤이 전부 사라진다. 표준 경로와 뜻이 다르다는 것을 알고 두는 것이다:
 *  - 사용자가 명시적으로 누른 결과이고, 네이티브 「완료」 한 번으로 되돌아온다
 *  - 마감이 임박한 상태에서 오래 머물면 입찰을 놓칠 수 있다. 그 판단은 사용자에게 맡긴다 —
 *    타이머를 대신 띄워 줄 방법이 없다(네이티브 플레이어 위에 우리 DOM을 올릴 수 없다)
 *
 * `webkitSupportsFullscreen`은 메타데이터 로드 후에야 true가 되므로 setup에서 못 읽는다 — `loadedmetadata`에서 본다.
 */
type WebkitVideo = HTMLVideoElement & {
  webkitEnterFullscreen?: () => void
  webkitSupportsFullscreen?: boolean
}
const canWebkitFullscreen = ref(false)
function syncWebkitFullscreenSupport(): void {
  const v = videoRef.value as WebkitVideo | null
  canWebkitFullscreen.value = typeof v?.webkitEnterFullscreen === 'function'
}

// 폴백 버튼 노출 조건이 표준보다 좁다 — 숨은(`v-show=false`) 엘리먼트는 전체화면에 들어가지 못하므로
// 스트림 부재 상태에서는 감춘다. 눌러도 무반응인 버튼을 안 만드는 것이 !136 리뷰 1의 요지다.
// (영상을 끈 오디오-온리 조건도 여기 있었지만 그 토글이 사라져 함께 없어졌다 — S15P11A704-463)
const fullscreenAvailable = computed(
  () => canFullscreen || (canWebkitFullscreen.value && hasStream.value),
)
// 래퍼와 버튼이 같은 조건을 봐야 한다 — 둘을 따로 쓰면 자식 없는 빈 클러스터가 남는다(!178 리뷰 nit 3).
// iPhone 목 모드가 그 경우다: canShowControls는 true인데 fullscreenAvailable(표준·webkit 둘 다 false)과
// canControlAudio(hasStream=false)가 모두 false다. 지금은 배경이 없어 안 보이지만 464에서 hover 클러스터에
// 배경·히트영역을 얹으면 눈에 띈다.
const showFullscreenButton = computed(() => canShowControls.value && fullscreenAvailable.value)

async function toggleFullscreen(): Promise<void> {
  try {
    // document.fullscreenElement가 아니라 내 상태로 판정한다 — 전자는 "아무 요소가 전체화면이면"이라,
    // 프레임이 전체화면을 담당하게 되면 라벨은 「전체화면」인데 남의 전체화면을 끄게 된다(!136 리뷰 2).
    if (isFullscreen.value) await document.exitFullscreen()
    // 표준 경로 — 대상은 프레임이 내준 컨테이너다(433). 타이머 칩·prodbar가 함께 들어간다.
    else if (canFullscreen) await fullscreenEl.value?.requestFullscreen?.()
    // iOS 폴백(441) — 대상이 <video>라 프레임이 내준 컨테이너를 쓸 수 없다. 종료는 네이티브 플레이어의
    // 「완료」가 맡는다(우리 UI가 안 보이므로 라벨 상태를 추적하지 않는다).
    else (videoRef.value as WebkitVideo | null)?.webkitEnterFullscreen?.()
  } catch (error) {
    // 브라우저가 거부할 수 있다(권한·제스처 컨텍스트). 상태는 fullscreenchange가 잡으므로 여기선 흘린다.
    console.warn('[live/media] 전체화면 전환 실패:', error)
  }
}

// 브라우저 UI(ESC·F11)로도 빠져나올 수 있어 상태는 이벤트로만 따라간다 — 클릭 시점에 뒤집지 않는다.
function syncFullscreen(): void {
  isFullscreen.value = document.fullscreenElement === fullscreenEl.value
}

const overlayText = computed(() => {
  switch (status.value) {
    case 'connecting':
      return '영상에 연결 중…'
    case 'no-publisher':
      return '판매자가 아직 송출을 시작하지 않았어요'
    case 'stream-ended':
      // 재송출되면 streamCreated가 다시 와서 자동 복귀한다 — 기다려도 된다는 근거를 문구에 담는다.
      // 'reconnecting'과 같은 'A — B' 패턴을 따른다(오버레이는 한 줄 렌더).
      return '판매자 송출이 중단됐어요 — 다시 시작되면 이어서 보여드려요'
    case 'error':
      return '영상 연결에 실패했어요'
    case 'reconnecting':
      // 남길 프레임이 없을 때만 이 오버레이가 뜬다 — 프레임 유지 시엔 상단 배지로 처리(리뷰 5)
      return '네트워크가 불안정해요 — 재연결 중…'
    case 'mock':
      return '목 모드 — 실 미디어 서버 연결 시 영상이 표시돼요'
    default:
      return ''
  }
})

async function connect(): Promise<void> {
  session?.disconnect() // 재시도 시 직전 세션(실패 세션의 핸들러 포함)을 정리한다(리뷰 1)
  session = null
  hasStream.value = false
  everHadStream.value = false // 새 세션(첫 입장·방 전환·수동 재시도)은 아직 받은 적 없음으로 시작
  // 새 연결(첫 입장·방 전환·수동 재시도)은 기본값인 소리 켬으로 되돌린다 — 정책은 tryPlay가 상대한다(476).
  // 421 때는 여기서 muted=true로 되돌렸는데, 그러면 476의 기본이 방 전환 한 번으로 뒤집힌다.
  muted.value = false
  // **무음이 물려 오지 않게 한다**(S15P11A704-513) — 이전 방의 음소거·폴백이 남긴 볼륨 0을 되돌린다.
  // 규칙은 restoreVolumeIfSilent가 소유한다(사용자가 고른 음량은 지킨다는 것까지 그쪽 주석에 있다).
  // 목 모드의 무음 시작(startsSilent)은 영향받지 않는다 — 목은 start()가 조기 반환해 여기까지 오지 않는다.
  restoreVolumeIfSilent()
  // 폴백 안내도 함께 내린다(!232 리뷰 1) — 안 내리면 정책에 막힌 방 A에서 안내를 닫지 않고 방 B로
  // 옮겼을 때, B가 live가 되는 순간 안내가 다시 뜬다. B는 막힌 적이 없으므로 원인 표시가 거짓이 되고,
  // 안내를 무시한 사용자에게는 방을 옮길 때마다 따라온다.
  mutedByPolicy.value = false
  status.value = 'connecting'
  try {
    const { token } = await issueSubscriberToken(props.roomId)
    if (disposed) return
    const { OpenVidu } = await import('openvidu-browser')
    if (disposed) return
    ov = new OpenVidu()
    session = ov.initSession()

    session.on('streamCreated', (event) => {
      // 판매자 스트림 수신 → 내가 제어하는 <video>에 붙인다(스타일·object-fit 통제 위해 addVideoElement 방식)
      // 구독 핸들은 여기서만 쓴다 — 영상 트랙을 켜고 끄던 저대역폭 토글이 사라져(463) 보관할 이유가 없어졌다.
      const subscriber = session!.subscribe((event as StreamEvent).stream, undefined)
      if (videoRef.value) subscriber.addVideoElement(videoRef.value)
      void tryPlay()
      hasStream.value = true
      everHadStream.value = true
      // status='live'는 <video>의 playing 이벤트가 세운다(S15P11A704-421) — 스트림 수신과 실제 재생 시작은
      // 다르다. 검은/멈춘 화면에 LIVE로 표시되지 않도록 재생 대기(connecting)로 두고, playing이 live로 올린다.
      // 그전(connecting+hasStream)엔 video를 띄우되 「영상에 연결 중」 오버레이로 덮는다.
      // 예외 하나: 재연결 흐름은 reconnecting/reconnected가 관리하므로 건드리지 않는다.
      // (영상을 끈 오디오-온리 예외는 그 토글이 사라져 함께 없어졌다 — 463)
      if (status.value === 'reconnecting') return
      status.value = 'connecting'
    })
    session.on('streamDestroyed', (event) => {
      // 내 회선 끊김으로 인한 소멸은 재연결 경로다 — 상태·hasStream을 유지한다(reconnecting을 덮지 않게, 리뷰 4)
      if ((event as StreamEvent).reason === 'networkDisconnect') return
      hasStream.value = false
      // 여기 오는 건 "받다가 소멸"뿐이다(판매자 unpublish·이탈, 서버측 종료) — 송출 전이 아니다(S15P11A704-426).
      status.value = 'stream-ended'
    })

    // ── 네트워크 대응 (LIVE-04) ──
    // 일시 끊김: SDK가 자동 재연결한다. 마지막 프레임을 남긴 채 '재연결 중' 배지만 띄운다.
    session.on('reconnecting', () => {
      status.value = 'reconnecting'
    })
    session.on('reconnected', () => {
      // 재연결은 SDK가 마지막 프레임을 유지한 채 복구하므로 playing이 재발화하지 않을 수 있다 —
      // 재생 게이트(S15P11A704-421)를 의도적으로 우회하고 hasStream 기준으로 즉시 복귀한다(리뷰 3).
      // 프레임이 없을 때 무엇으로 복귀하느냐는 hasStream으로 못 가른다 — 위 reconnecting이 status를 무조건
      // 덮으므로 직전의 'stream-ended'가 이미 지워져 있고, "정상 종료 → 별개의 재연결"과 "받은 적 없는 방의
      // 재연결"이 둘 다 !hasStream으로 보인다. 그래서 everHadStream으로 판정한다(!134 리뷰 1).
      status.value = hasStream.value
        ? 'live'
        : everHadStream.value
          ? 'stream-ended'
          : 'no-publisher'
    })
    // 자동 재연결이 최종 실패(networkDisconnect)하면 에러로. 우리가 끊은 정상 종료는 무시한다.
    session.on('sessionDisconnected', (event) => {
      if (!disposed && (event as SessionDisconnectedEvent).reason === 'networkDisconnect') {
        status.value = 'error'
      }
    })
    // (networkQualityLevelChanged 리스너는 제거 — OpenVidu CE 전용 결정에 따른 죽은 경로 정리, S15P11A704-359)

    session.on('exception', (exception) => {
      console.warn('[live/media] OpenVidu 예외:', exception)
    })

    await session.connect(token)
    // await 사이 언마운트됐으면 방금 연결된 세션을 끊는다 — 아무도 안 끊는 누수 경로 차단(리뷰 1)
    if (disposed) {
      session.disconnect()
      session = null
      return
    }
    // 스트림을 아직 못 받았을 때만 '송출 전'이다. connect()는 기존 퍼블리셔의 streamCreated를 동기 emit한 뒤
    // resolve할 수 있어(이미 송출 중인 방 입장), 그 경우 hasStream이 이미 서 있으니 no-publisher로 덮지 않는다(리뷰 1).
    if (!hasStream.value && status.value === 'connecting') status.value = 'no-publisher'
  } catch (error) {
    console.warn('[live/media] 영상 연결 실패:', error)
    if (!disposed) status.value = 'error'
  }
}

// <video>가 실제로 재생을 시작하면 LIVE로 확정한다(S15P11A704-421). 스트림이 붙은 뒤(hasStream)에만 유효하며,
// 재연결 프레임 복귀는 reconnected가 별도로 처리하므로 여기서 재판정하지 않아도 된다.
function onStreamPlaying(): void {
  if (hasStream.value && status.value === 'connecting') status.value = 'live'
}

function start(): void {
  if (isMockApi()) {
    status.value = 'mock' // 미디어 서버 없음 — 플레이스홀더만
    return
  }
  void connect()
}

onMounted(() => {
  document.addEventListener('fullscreenchange', syncFullscreen)
  start()
})

// 방이 바뀌면(vue-router가 컴포넌트 재사용) 이전 세션을 끊고 새 방으로 재연결한다(리뷰 2)
watch(
  () => props.roomId,
  () => start(),
)

onUnmounted(() => {
  document.removeEventListener('fullscreenchange', syncFullscreen)
  disposed = true
  session?.disconnect()
  session = null
  ov = null
})
</script>

<template>
  <div ref="stageRef" class="bg-stage relative h-full w-full overflow-hidden">
    <!-- 루트에 ref를 잡는 이유는 전체화면 대상이기 때문이다(S15P11A704-423).
         주석을 루트 밖에 두면 프래그먼트가 되어 루트 엘리먼트를 잡을 수 없으므로 안에 둔다.
         bg-stage를 여기 깐 것도 전체화면용이다 — 전체화면 요소는 부모 배경을 상속하지 않아 흰 배경이 된다. -->
    <!-- 실 스트림 — 재연결 중이라도 남길 프레임이 있을 때만 유지한다(없으면 오버레이로, 리뷰 5). -->
    <!-- 스트림이 붙으면(hasStream) connecting 동안에도 화면에 두고(위 오버레이가 덮음) 실제 재생을 기다린다 —
         playing 이벤트가 보이는 <video>에서 확실히 발화해 LIVE로 전환하게 한다(S15P11A704-421).
         muted는 기본 false다 — 소리 켠 상태로 시도하고 정책에 막히면 tryPlay가 음소거로 낮춘다(476). -->
    <video
      v-show="
        hasStream && (status === 'live' || status === 'connecting' || status === 'reconnecting')
      "
      ref="videoRef"
      class="h-full w-full object-cover"
      :muted="muted"
      :volume="volumePercent / 100"
      autoplay
      playsinline
      @playing="onStreamPlaying"
      @loadedmetadata="syncWebkitFullscreenSupport"
    ></video>

    <!-- 재연결 중(프레임 유지 시) 배지. 프레임이 없으면 아래 오버레이가 안내한다. -->
    <!-- 타이머 칩(LiveLayout top-4, z-[6], FE1 소유)과 겹치지 않게 그 아래로 내린다(리뷰 1) -->
    <!-- 빨강은 팔레트상 '라이브·입찰' 기능색이라 경고엔 안 쓴다 — 중립 경고 아이콘 사용(리뷰 6) -->
    <!-- 중앙 정렬로 되돌렸다(S15P11A704-464). 423이 좌측으로 붙인 이유는 시청 컨트롤이 같은 top-16 행에
         있어 좁은 화면에서 배지가 컨트롤 아래로 파고들었기 때문이다 — 그 컨트롤이 우하단으로 내려가
         상단 우측이 비었으므로 제약이 사라졌다. 가로 예약(max-w calc(100%-10rem))도 컨트롤 폭 확보용이었고,
         이제 좌우 여백만 빼면 되므로 문구가 좁은 화면에서 덜 잘린다. 위 타이머 칩(top-4 중앙, FE1)과는
         행이 달라 겹치지 않는다. -->
    <div
      v-if="status === 'reconnecting' && hasStream"
      class="text-on-dark bg-ink/80 absolute top-16 left-1/2 flex max-w-[calc(100%-2rem)] -translate-x-1/2 items-center gap-1.5 rounded-full px-3.5 py-1.5 text-[12px] backdrop-blur-md"
      role="status"
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
      <span class="truncate">네트워크가 불안정해요 — 재연결 중…</span>
    </div>

    <!-- 비-라이브 상태 오버레이 (스테이지 다크 위). 재연결이라도 남길 프레임이 없으면 여기서 안내한다(리뷰 5).
         **pointer-events를 풀어 둔다**(!184 리뷰 3) — inset-0으로 스테이지 전체를 덮으므로, 클릭을 막으면
         그 아래·위의 컨트롤이 상태에 따라 조용히 죽는다. 지금은 컨트롤이 z-[7]로 히트테스트를 이겨서
         목 모드 데모(?audiodemo)가 성립하는데, 이 오버레이가 나중에 z를 얻으면 그 데모가 조용히 죽고
         테스트는 DOM 존재만 보므로 안 깨진다. 클릭이 필요한 것은 「다시 시도」 버튼뿐이라 그쪽만 되살린다. -->
    <div
      v-if="status !== 'live' && !(status === 'reconnecting' && hasStream)"
      class="text-on-dark-dim pointer-events-none absolute inset-0 flex flex-col items-center justify-center gap-3 px-6 text-center"
      role="status"
    >
      <svg
        class="h-9 w-9 opacity-70"
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
      <p class="text-[13px]">{{ overlayText }}</p>
      <button
        v-if="status === 'error'"
        type="button"
        class="text-on-dark pointer-events-auto rounded-full border border-white/20 px-4 py-1.5 text-[12px] font-medium transition-colors hover:bg-white/10 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white motion-reduce:transition-none"
        @click="connect"
      >
        다시 시도
      </button>
    </div>

    <!-- 자동재생 정책 폴백 안내 (S15P11A704-479) — **클러스터 밖**이라 hover 없이 보인다.
         자리는 재연결 배지와 같은 행(top-16 중앙)이다. 둘이 동시에 뜨지 않게 재연결 중에는 접는다 —
         그때는 소리가 없는 이유가 정책이 아니라 회선이고, 배지가 그걸 이미 말한다.
         role="status"라 스크린리더에도 한 번 전달된다(422의 통지 리전과 별개 — 이건 화면에도 보인다).
         `data-policy-notice`는 테스트 훅이다 — 재연결 배지도 role="status"라 그것만으로는 구분되지 않는다.

         조건에 `silent`를 곁들인 것은 **보험이다**(!232 리뷰 1). 지금은 복구 경로 둘(안내 버튼·슬라이더)이
         모두 플래그를 내리므로 이 조건 없이도 맞다 — 그래서 테스트로 가를 수 없다. 세 번째 경로가 생겼을 때
         「소리가 꺼진 채 재생 중이에요」가 거짓이 되지 않게 하려는 것이고, 화면이 사용자에게 하는 단정이라
         비용 대비 값이 있다고 봤다.
         레드는 라이브·입찰 기능색이라 중립 on-dark 톤(규칙 1). -->
    <div
      v-if="mutedByPolicy && silent && status === 'live'"
      data-policy-notice
      class="text-on-dark bg-ink/85 absolute top-16 left-1/2 z-[7] flex max-w-[calc(100%-2rem)] -translate-x-1/2 items-center gap-2 rounded-full py-1.5 pr-1.5 pl-3.5 text-[12px] backdrop-blur-md"
      role="status"
    >
      <svg
        class="h-3.5 w-3.5 flex-none"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <path d="M11 5 6 9H2v6h4l5 4V5Z" />
        <path d="M23 9l-6 6M17 9l6 6" />
      </svg>
      <span class="truncate">소리가 꺼진 채 재생 중이에요</span>
      <button
        type="button"
        class="bg-surface text-ink flex-none rounded-full px-2.5 py-1 text-[11px] font-semibold transition-colors hover:bg-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white motion-reduce:transition-none"
        @click="unmuteFromNotice"
      >
        소리 켜기
      </button>
      <button
        type="button"
        class="text-on-dark-dim hover:text-on-dark flex-none rounded-full p-1 transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white motion-reduce:transition-none"
        aria-label="안내 닫기"
        title="안내 닫기"
        @click="dismissPolicyNotice"
      >
        <svg
          class="h-3 w-3"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2.5"
          stroke-linecap="round"
          aria-hidden="true"
        >
          <path d="M6 6l12 12M18 6L6 18" />
        </svg>
      </button>
    </div>

    <!-- 시청 컨트롤 (S15P11A704-423) — 우하단 hover 클러스터 (S15P11A704-464 → 475에서 코너로).
         와이어프레임의 `.fsbtn`(right:16px bottom:16px)이 이 자리를 정의한다. 423이 우상단(top-16)에 둔 것이
         어긋난 상태였다.

         464는 와이어프레임의 `.stage:hover`에서의 `translateY(-62px)`까지 따라 하단 78px에 뒀는데,
         **475에서 그 상승을 버리고 코너로 내린다** — 전체화면을 더 내려 달라는 요청이다.
         매직 넘버는 gap-2가 대신한다.

         **배치가 폭에 따라 갈린다.** 요청은 「오디오를 전체화면 왼쪽에」이고 그게 lg 이상에서의 모습이다.
          · lg 이상 — 가로 한 줄(오디오 · 전체화면), 둘 다 하단 16~46px. prodbar 본문(16~52px)과 같은 줄이라
            프레임에 **192px(`lg:pr-48`)를 예약**해 둔다. 클러스터 최대 폭이 156px(아이콘 + 슬라이더 펼침)
            + 오프셋 16px이라 그만큼 필요하다. 1280px 화면에서 제목 영역이 674px 남아 문제없다.
            (476이 라벨 약 52px을 없애 214px에서 줄었고, 예약도 240 → 192로 함께 줄였다.)
          · lg 미만 — **세로 스택**을 유지한다(전체화면 16~44px, 오디오 52~82px). 가로로 놓으면 같은 예약이
            필요한데 360px 화면의 스테이지는 336px뿐이라 제목에 124px(썸네일·gap 빼면 68px)만 남는다.
            그래서 여기서는 오디오를 prodbar 본문 위로 올리고, 프레임 예약은 전체화면 몫(`pr-14`)만 둔다.
         세로 스택 덕분에 464가 만든 겹침 둘도 좁은 화면에서 사라졌다(S15P11A704-468): Vibe 칩(96~126px)과
         컨트롤 최상단(82px)이 떨어지고, 재연결 배지(위에서 64~94px)와도 떨어진다(197px 스테이지에서 115~181px).

         gap이 2가 아니라 3인 이유는 실측이다(S15P11A704-481). gap-2면 오디오 알약과 prodbar 상품명 사이
         여유가 **1px**이었다 — 슬라이더를 펼치면 알약이 제목 박스 안으로 80px 들어가므로 겹침을 막는 것이
         그 1px뿐이었다. 패딩·글꼴이 2px만 바뀌어도 슬라이더가 상품명 위에 올라탄다. gap-3이면 5px가 된다
         (Vibe 칩과의 간격은 14 → 10px로 줄지만 여전히 안 겹친다).

         ⚠️ 좁은 화면의 세로 스택은 **전체화면 버튼이 아래 칸을 채워 준다는 전제**로만 안전하다(!185 리뷰 1).
         그게 없으면 오디오 알약(약 120px)이 코너로 내려와 prodbar 제목을 덮는다 — 프레임 예약은 전체화면
         몫(56px)뿐이다. 전체화면이 없을 때(`connecting` 구간, `allowfullscreen` 없는 iframe 등) `pb-10`으로
         아래 한 칸(전체화면 28px + gap 12px)을 비워 알약을 제목 위로 올린다.
         **이 값은 gap과 물려 있다** — gap-2 시절엔 pb-9(36px)였고, gap-3으로 올리면서 40px가 됐다.
         어긋나면 알약이 전체화면이 있을 때와 다른 높이에 앉아 제목과 1px 겹친다(481 실측 기준).
         lg에서는 필요 없다 — 가로 한 줄이고 예약이 192px라 알약이 들어간다(`lg:pb-0`).

         ⚠️ z만 와이어프레임과 다르다(z-[7] vs 디자인 z-index:6). 디자인은 fsbtn이 prodbar **뒤 형제**라
         같은 z로도 이기지만, 우리 컨트롤은 #video 슬롯 안(prodbar보다 앞)이라 같은 z면 진다. 지면 클릭까지
         prodbar에 먹힌다(그쪽은 pointer-events를 풀지 않는다) — 423이 하단을 피한 진짜 이유가 이것이다.
         슬롯 래퍼와 이 컴포넌트 루트가 모두 z-auto라 스태킹 컨텍스트를 만들지 않으므로 z-[7]이 프레임까지
         올라가 이긴다. (형제 VibeOverlay는 자기 루트가 z-[5]로 갇혀 그게 불가능해 위치로 피했다.)

         노출 조건 — 와이어프레임은 fsbtn을 **상시 노출**로 둔다(hover에서 밀 뿐). 「hover에서만」은 의도적
         이탈이다: 스테이지를 비워 영상에 집중시킨다(462 요청). 대신 둘을 함께 건다.
          · **숨김 자체를 `(hover: hover)`로 가둔다** — 포인터가 hover를 못 만드는 환경(터치)에서는 처음부터
            상시 노출이다. 뷰포트 폭(`lg:`)으로 가르면 안 된다: Tailwind v4는 `group-hover`를
            `@media (hover:hover)`로 감싸 내보내므로, iPad 가로(1024px ≥ lg, hover 없음)에서 숨김만 걸리고
            hover는 영원히 안 와 컨트롤이 영구히 사라진다. 프레임의 prodbar가 `lg:`를 쓰고 있어 같은 구멍이
            있다 — 그건 FE1 파일이라 후속 티켓(467)으로 분리했다. 공용 변형(can-hover)은 그때 두 번째 사용처가
            생기면 추출한다(AGENTS 컨벤션).
          · 두 버튼에 포커스 링(focus-visible outline)을 준다 — 키보드가 이 컨트롤의 유일한 비-hover 경로가
            됐는데, 드러나기만 하고 어디에 포커스가 있는지 안 보이면 쓸 수 없다. 프레임의 나가기 버튼과 같은
            패턴이다.
          · focus-within — 키보드는 hover를 못 만든다. 이게 없으면 접근성 회귀다(와이어프레임도 prodbar에
            `.stage:focus-within`을 함께 건다). opacity-0은 포커스를 막지 않으므로 탭이 닿는 순간 드러난다.
            숨김이 hover 환경 전용이라 이쪽은 미디어 조건 없이 둔다.
         hover 소스는 프레임의 `group/stage`(LiveLayout, 464에서 이름을 붙였다) — 포인터가 prodbar 위로
         들어가도 그것 역시 그룹 안이라 클러스터가 사라지지 않는다. 루트를 소스로 쓰면 prodbar가 형제라
         그 위에서 hover가 끊겨 버튼에 닿기 직전에 사라진다. -->
    <div
      v-if="canControlAudio || showFullscreenButton"
      data-live-controls
      class="absolute right-4 bottom-4 z-[7] flex flex-col items-end gap-3 transition-opacity duration-300 group-hover/stage:pointer-events-auto group-hover/stage:opacity-100 focus-within:pointer-events-auto focus-within:opacity-100 motion-reduce:transition-none lg:flex-row lg:items-center [@media(hover:hover)]:pointer-events-none [@media(hover:hover)]:opacity-0"
      :class="!showFullscreenButton && 'pb-10 lg:pb-0'"
    >
      <!-- 오디오 묶음 (S15P11A704-465) — 스피커 + 펼쳐지는 음량 슬라이더. 유튜브와 같은 구성이다.
           `group/volume`이 스피커·슬라이더를 함께 감싸는 이유: 스피커만 hover 소스로 두면 포인터가
           슬라이더로 넘어가는 순간 접혀 조절이 불가능하다. 둘을 한 그룹으로 묶어야 이어진다. -->
      <div
        v-if="canControlAudio"
        class="group/volume text-on-dark bg-ink/70 flex items-center rounded-full border border-white/20 backdrop-blur-md"
      >
        <!-- 음소거 버튼 — **아이콘만 둔다**(S15P11A704-476). 421은 음소거로 시작하는 게 기본이라
             「소리 켜기」를 글자로 노출해 고장으로 오해하지 않게 했는데, 기본이 소리 켬으로 바뀌어
             그 근거가 사라졌다. 사선 아이콘만으로 구분한다(유튜브와 같다).
             aria-label·title은 유지한다 — 아이콘만 남으면 스크린리더·툴팁에는 그것이 유일한 이름이다.
             레드는 라이브·입찰 기능색이라 중립 on-dark 톤(규칙 1).
             muted가 아니라 silent로 가른다 — 슬라이더를 0까지 내린 것도 무음이다(465).

             **음소거 전용이다**(477) — 눌러서 다시 켜지지 않는다. 그 사실을 `aria-disabled`로 알리고
             클릭은 핸들러에서 막는다.
             ⚠️ `disabled`를 쓰면 안 된다(S15P11A704-480): 탭 순서에서 빠져 **복구 방법을 적은 라벨을 읽을
             경로가 사라지고**, 브라우저가 disabled 컨트롤에 마우스 이벤트를 안 보내 툴팁도 안 뜬다.
             아이콘만 있는 버튼이라(476) 그 라벨이 유일한 설명인데, 정작 필요한 상태에서만 닿을 수 없게 된다.
             `aria-disabled`면 포커스 가능한 정상 버튼이라 라벨·툴팁·hover가 모두 살아 있고, 눌러도 아무 일이
             없다는 사실은 그대로다. hover 강조만 끈다 — 눌리지 않는데 강조가 남으면 그것도 거짓말이다.
             `data-mute`는 테스트가 라벨 문구와 무관하게 버튼을 찾기 위한 훅이다 — 라벨은 상태에 따라 바뀐다. -->
        <button
          type="button"
          data-mute
          class="hover:bg-ink/85 flex flex-none items-center rounded-full px-3 py-1.5 transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white aria-disabled:cursor-default aria-disabled:hover:bg-transparent motion-reduce:transition-none"
          :aria-disabled="silent"
          :aria-label="silent ? '음소거됨 — 슬라이더로 소리를 켜세요' : '음소거'"
          :title="silent ? '음소거됨 — 슬라이더로 소리를 켜세요' : '음소거'"
          @click="onMuteClick"
        >
          <svg
            class="h-3.5 w-3.5 flex-none"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path d="M11 5 6 9H2v6h4l5 4V5Z" />
            <template v-if="silent">
              <path d="M23 9l-6 6M17 9l6 6" />
            </template>
            <template v-else>
              <path d="M15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13" />
            </template>
          </svg>
        </button>

        <!-- 슬라이더는 **감싼 span의 폭**을 접는다 — input 자체에 w-0을 주면 thumb이 밖으로 새어 보인다.
             숨김 축은 464와 같다: `(hover: hover)`에서만 접고, hover를 못 만드는 환경(터치)에서는 펼친
             채로 둔다. 뷰포트 폭으로 가르면 iPad 가로에서 음량을 조절할 방법이 사라진다.
             focus-within은 미디어 조건 없이 둔다 — 탭이 닿는 순간 펼쳐져야 한다(div로 만들면 아예 못 닿는다).

             치수 둘은 서로 물려 있다.
              · 펼친 폭 w-20(80px) = input w-16(64px) + mx-2(8+8). 어긋나면 오른쪽 여백이 잘린다.
              · span에 h-7을 준 것은 **thumb과 포커스 링이 세로로 잘리지 않게** 하려는 것이다 —
                overflow-hidden이 가로만 자를 수는 없다(한 축만 hidden이면 다른 축이 auto가 되어
                스크롤바가 생긴다). 필요 높이는 네이티브 range 높이(16~20px) + outline-offset-2 +
                outline-2 = 24~28px이라 h-6(24px)은 여유가 0이고 브라우저에 따라 잘린다(!182 리뷰 3).
                버튼 높이(약 30px)가 알약 높이를 정하므로 h-7로 올려도 겉모습은 그대로다.
                input 높이를 지정하지 않는 것도 같은 이유다: 네이티브 크기를 쓰면 thumb이 온전히 들어온다.
                트랙 색을 직접 칠하려면 벤더 의사요소가 필요해 accent-on-dark(네이티브 틴트)로 둔다. -->
        <span
          class="flex h-7 items-center overflow-hidden transition-[width,opacity] duration-200 group-hover/volume:w-20 group-hover/volume:opacity-100 focus-within:w-20 focus-within:opacity-100 motion-reduce:transition-none [@media(hover:hover)]:w-0 [@media(hover:hover)]:opacity-0"
        >
          <!-- ⚠️ 이 폭(w-16 + mx-2)과 위 버튼 폭이 클러스터 폭을 정하고, 그게 프레임의 우측 예약과
               물려 있다: 클러스터 156px + 오프셋 16px = 172px ≤ `lg:pr-48`(192px), 여유 20px뿐이다.
               폭을 늘리거나 라벨을 되살릴 땐 LiveLayout의 예약을 함께 본다(!185 리뷰) — spec은 예약이
               존재하는지만 보고 폭과의 관계는 보지 않는다. -->
          <input
            class="accent-on-dark mx-2 w-16 flex-none cursor-pointer focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
            type="range"
            min="0"
            max="100"
            step="5"
            :value="volumePercent"
            aria-label="음량"
            @input="onVolumeInput"
          />
        </span>
      </div>

      <!-- 전체화면 — 데스크톱에서 스테이지가 344px 레일 옆 좁은 영역에 갇히는 문제(S15P11A704-423).
             상태는 fullscreenchange로만 따라가므로 ESC·F11로 빠져나와도 라벨이 어긋나지 않는다. -->
      <button
        v-if="showFullscreenButton"
        type="button"
        class="text-on-dark bg-ink/70 hover:bg-ink/85 flex items-center justify-center rounded-full border border-white/20 p-1.5 backdrop-blur-md transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white motion-reduce:transition-none"
        :aria-label="isFullscreen ? '전체화면 종료' : '전체화면'"
        :title="isFullscreen ? '전체화면 종료' : '전체화면'"
        @click="toggleFullscreen"
      >
        <svg
          class="h-3.5 w-3.5 flex-none"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <template v-if="isFullscreen">
            <path d="M9 3H5a2 2 0 0 0-2 2v4M15 3h4a2 2 0 0 1 2 2v4" />
            <path d="M9 21H5a2 2 0 0 1-2-2v-4M15 21h4a2 2 0 0 0 2-2v-4" />
          </template>
          <template v-else>
            <path d="M8 3H5a2 2 0 0 0-2 2v3M16 3h3a2 2 0 0 1 2 2v3" />
            <path d="M8 21H5a2 2 0 0 1-2-2v-3M16 21h3a2 2 0 0 0 2-2v-3" />
          </template>
        </svg>
      </button>
    </div>
  </div>
</template>
