<script setup lang="ts">
// ChatPanel — 대기방·라이브 공용 채팅 패널 (FE2, CHAT-01·02·06)
//
// props 계약: docs/03-역할-경계-합의.md §5. 데이터는 live 스토어(handlers가 갱신), 전송은 sendChat(204).
// 슬롯 부모(LiveLayout #chat)를 100% 채우고 자체 크기·위치는 잡지 않는다(docs/02). 색·폰트는 토큰만(규칙 1).
import { computed, nextTick, onUnmounted, ref, useId, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useLiveStore, CHAT_MAX_LENGTH, type ChatMessage } from '@/stores/live'
import { useAuthStore } from '@/stores/auth'
import { deleteChatMessage, pinNotice, unpinNotice } from '@/shared/api/chat'
import { toApiError } from '@/shared/api/errors'
import { useMockDemoFlag } from '@/shared/lib/useMockDemoFlag'
import { dispatchChatTopic } from '@/features/live/socket/handlers'
import { appendedRowCount, prependedRowCount, removedRowIndices } from './chatScroll'
import LockNote from '@/shared/ui/LockNote.vue'

const props = withDefaults(
  defineProps<{
    /** 방 id (계약상 필수) = auctionId. 고정 공지 API(CHAT-02)의 경로 스코프로 쓴다 */
    roomId: number
    /** CHAT-06 질문 강조 — 켜면 질문 메시지에 슬레이트 틴트 배경을 준다(라이브에서 켬) */
    highlightQuestions?: boolean
    /** 입력 잠금 — 방송 종료 후 등 */
    disabled?: boolean
    /** 헤더 라벨 — 대기방은 '대기 채팅', 라이브는 '실시간 채팅' */
    title?: string
    /** 정적 안내 스트립 — 대기방의 "시작 전에도 질문 남길 수 있어요" 같은 문맥 안내(서버 공지 pinnedNotice와 별개) */
    hint?: string
    /** disabled일 때 잠금 문구 — 사유가 문맥마다 달라 컨테이너가 지정한다(기본: 종료 후) */
    disabledText?: string
    /**
     * CHAT-02 고정 공지 액션 노출 — 판매자만 켠다. 서버도 `NOT_AUCTION_HOST`로 막지만
     * 안 보이는 게 먼저다. 판정은 컨테이너가 한다(라이브는 `currentRoom.isSeller`).
     */
    canPin?: boolean
    /**
     * CHAT-01 메시지 삭제 액션 노출 — 판매자·운영자만 켠다(S15P11A704-681). 서버도 403
     * `CHAT_DELETE_FORBIDDEN`으로 막지만 안 보이는 게 먼저다. canPin과 판정이 다르다:
     * 고정은 판매자 전용(NOT_AUCTION_HOST)이고 삭제는 운영자도 된다.
     */
    canDelete?: boolean
    /**
     * CHAT-06 질문 체크박스 노출 (S15P11A704-685). **판매자 화면에서는 끈다** — 질문 큐는
     * 판매자가 받아 보는 것이라, 자기 메시지를 자기 큐에 넣는 동작에 뜻이 없다.
     * `canPin`·`canDelete`와 방향이 반대라 별도 프롭이다: 저 둘은 판매자만 켜고 이건 판매자만 끈다.
     */
    canAskQuestion?: boolean
  }>(),
  {
    highlightQuestions: false,
    disabled: false,
    title: '실시간 채팅',
    hint: undefined,
    disabledText: undefined,
    canPin: false,
    canDelete: false,
    canAskQuestion: true,
  },
)

const live = useLiveStore()
const auth = useAuthStore()
const route = useRoute()

const messages = computed(() => live.chat)
const pinned = computed(() => live.pinnedNotice)

// 과거 조회 보관 상한 도달 여부(S15P11A704-406) — 스토어 getter를 읽는다(loadOlderChat 가드와 단일 소스, 리뷰 nit).
const atOlderCap = computed(() => live.atOlderChatCap)

// 입력 잠금 사유를 한 값으로 모은다 — 문구와 액션(로그인 유도)이 같은 근거를 읽어야 어긋나지 않는다.
// 우선순위: 컨테이너 잠금 > 비로그인 > 뮤트.
//  - 컨테이너 잠금이 먼저인 이유: 그쪽이 이 패널보다 방 상태를 많이 안다. 로그인으로 풀리지 않는 사유일 수
//    있으므로(종료 등) 로그인을 권하면 헛걸음이 된다.
//    ⚠️ 'disabled-by-container'라고 이름을 붙인 것은 이 값이 종료 전용이 아니기 때문이다 — props 주석대로
//    사유는 컨테이너가 정하고, 실제로 대기방은 권한(!permissions.chat)으로 쓴다. 권한은 로그인으로 바뀔 수
//    있는 값이라, 컨테이너가 비로그인에게도 이 prop을 켜서 넘기면 로그인 유도가 가려진다.
//    **그게 터미널 사유라는 보장은 컨테이너에 있다.** 지금은 도달 경로가 없다 — 라이브는 이 prop을 안 넘기고,
//    대기방은 실 모드에서 비로그인 join이 401로 error phase가 되어 패널 자체가 안 그려진다. 그 전제가 깨지면
//    (예: 비로그인 join이 성공하며 chat:false를 주면) 사유를 컨테이너가 구분해 넘기게 계약을 넓혀야 한다.
//
// ⚠️ 비로그인은 여기 없다(S15P11A704-461). 430에서는 잠금 사유였는데, 그러면 입력창이 통째로 사라져
// 패널이 "채팅이 안 되는 방"으로 보이고 참여 의사가 생기기 전에 차단한다. 지금은 입력부를 열어 두고
// 입력하려는 순간(포커스) 안내한다 — 아래 needsLogin.
type LockReason = 'disabled-by-container' | 'muted'
const lockReason = computed<LockReason | null>(() => {
  if (props.disabled) return 'disabled-by-container'
  if (live.muted) return 'muted'
  return null
})
const locked = computed(() => lockReason.value !== null)
const lockText = computed(() => {
  if (lockReason.value === 'disabled-by-container')
    return props.disabledText ?? '🔒 종료된 방송이라 채팅을 보낼 수 없어요.'
  // 뮤트 해제 시각은 서버값(mutedUntil) 기준 표시(규칙 5). 재접속 복원 전(null)이면 폴백 문구.
  // 스냅샷에 mutedUntil이 들어가는지는 350에서 BE 확인 중이다 (원래 여기 적혀 있던 키는 삭제된 번호였다).
  if (live.mutedUntil !== null) {
    const t = new Date(live.mutedUntil)
    const hh = String(t.getHours()).padStart(2, '0')
    const mm = String(t.getMinutes()).padStart(2, '0')
    return `🔒 채팅이 ${hh}:${mm}까지 제한되었어요.`
  }
  return '🔒 채팅이 일시 제한되었어요. 잠시 후 다시 시도해 주세요.'
})

/**
 * 비로그인 로그인 유도 (S15P11A704-461).
 *
 * 라이브 진입 자체는 비로그인도 허용한다(구경 허용 정책 — 라우터 가드가 로그아웃을 막지 않는다).
 * 그래서 입력부를 잠그지 않고 **정상처럼 보이게** 두되, 입력하려는 순간에 안내한다.
 *
 * 설계 판단 셋(461):
 *  - **모달이 아니라 인라인** — 라이브 중에 화면을 덮으면 방송과 남은 시간이 가려진다. 보고 있는 것을 막지 않는다.
 *  - **포커스를 훔치지 않는다** — 안내가 포커스를 가져가면 사용자가 입력창으로 돌아올 때 다시 뜨는 루프가 된다.
 *  - **입력창을 readonly로 둔다** — "한 글자라도 치면 안내"를 이벤트로 처리하면 그 한 글자는 이미 들어가 있다.
 *    readonly면 애초에 못 치므로 **잃을 초안이 없다**. 실 모드 로그인은 window.location.assign으로 페이지를
 *    떠나서(useKakaoAuth) 메모리 초안은 어차피 살릴 수 없다 — 저장·복원 로직 자체를 없애는 선택이다.
 *
 * 컨테이너 잠금(종료 등)이 우선이라 종료된 방에서는 로그인을 권하지 않는다(430에서 세운 우선순위 유지).
 * 그 우선순위는 **조건이 아니라 구조로** 성립한다 — 입력창·전송 버튼·이 안내가 모두 `locked`의 `v-else`
 * 안에 있어서, 잠긴 방에서는 이 값을 읽는 지점 자체에 도달하지 않는다. `lockReason === null`을 조건에
 * 덧붙여 봤지만 변형 테스트에서 아무것도 깨지 않았다(도달 불가) — 방어 코드로 남기지 않고 근거만 남긴다.
 */
const needsLogin = computed(() => !auth.isLoggedIn)
const loginPromptShown = ref(false)
// 안내를 입력창에 aria-describedby로 잇는다(!174 리뷰 1) — readonly는 리더가 "읽기 전용"까지만 알려 주고
// **이유**는 이 안내에 있다. 422의 상주 리전이 첫 포커스에 한 번 읽어 주지만, 같은 문구가 연속으로 들어가면
// 재낭독이 생략되는 특성이 있어 두 번째 포커스부터는 침묵한다 — describedby는 그 영향을 받지 않는다.
// 고정 문자열 대신 useId를 쓰는 이유: 한 화면에 패널이 둘 이상 뜨면 id가 겹친다(라이브·대기방 공용 컴포넌트다).
const loginPromptId = `${useId()}-chat-login`

/** 입력 시도(포커스·전송)에서 부른다. 표시만 하고 포커스는 건드리지 않는다. */
function promptLogin(): void {
  if (!needsLogin.value) return
  loginPromptShown.value = true
  announce('채팅을 보내려면 로그인이 필요해요')
}

// 빈 목록 문구 (S15P11A704-428) — 잠긴 방에서 "먼저 인사를 건네보세요"를 권하면 바로 아래 입력창이
// LockNote로 막힌 것과 모순된다. 사실("아직 채팅이 없어요")은 같고 권유만 보낼 수 있을 때 붙인다.
// 잠금 사유는 여기서 다시 안내하지 않는다 — LockNote가 바로 아래에서 이미 말한다(중복 방지).
const emptyText = computed(() =>
  locked.value ? '아직 채팅이 없어요' : '아직 채팅이 없어요 — 먼저 인사를 건네보세요',
)

/**
 * 스크린리더 통지 리전 (S15P11A704-422).
 *
 * 라이브 채팅에 들어오는 새 메시지를 비시각 사용자가 전혀 알 수 없었다 — 목록에 role도 라이브 리전도 없었다.
 *
 * 목록 자체에 {{aria-live}}를 걸지 않고 **전용 리전 하나로 모은 이유**가 둘이다.
 *  1. {{aria-relevant="additions"}}는 꼬리 추가와 앞단 추가를 구분하지 못한다. 과거 조회(379)가 한 번에 20건을
 *     prepend하므로, 목록에 라이브를 걸면 사용자가 위로 스크롤한 순간 옛 메시지 20건이 낭독된다 — 고치려던
 *     소음보다 더 크다. 이 패널은 이미 꼬리 추가만 세는 수단(appendedRowCount)을 갖고 있어 그걸 쓴다.
 *  2. {{v-if}}로 나타나는 {{role="status"}} 요소는 내용과 함께 삽입되면 낭독을 건너뛰는 리더가 있다(MR !148 리뷰).
 *     상주하는 리전에 텍스트만 갈아 끼우면 그 문제가 없다. 연결 안내·전송 실패도 여기로 모은다.
 *
 * {{role="alert"}}인 검열 경고는 그대로 둔다 — alert은 삽입 시 낭독이 규격에 포함된 역할이라 같은 문제가 없다.
 */
const srAnnouncement = ref('')

/**
 * 통지 리전에 문구를 쓴다 (S15P11A704-516).
 *
 * 라이브 리전은 **텍스트가 바뀔 때만** 낭독된다. 같은 문구를 두 번 쓰면 값이 같아서 Vue가 DOM을 갱신조차
 * 하지 않고, 그러면 리더에게 알릴 변경이 없어 **두 번째가 통째로 유실된다.** 같은 사람이 같은 말을 두 번
 * 하거나(「ㅋㅋㅋ」) 「이전 메시지를 불러왔어요」를 연달아 겪는 경로가 그것이다.
 *
 * 그래서 보이지 않는 토글 문자(U+200B, zero-width space)를 번갈아 붙여 값이 항상 바뀌게 한다.
 * 422는 이 우회를 「실측 없이 넣으면 잡음만 는다」며 보류했는데, 447에서 **리더 종류와 무관한 확정**으로
 * 판정됐다(Vue가 DOM을 안 건드리는 것이라 낭독 엔진 문제가 아니다). 그래서 넣을 근거가 생겼다.
 *
 * 공백이 아니라 U+200B인 이유: 리더·브라우저가 텍스트를 비교할 때 공백은 정규화로 접힐 수 있어
 * 「바뀌지 않았다」로 판정될 여지가 남는다. U+200B는 공백이 아니어서 접히지 않고, 화면·낭독 어디에도
 * 나타나지 않는다.
 *
 * 리전이 하나라 **같은 tick에 두 통지가 겹치면 먼저 것은 여전히 유실된다** — 그건 큐가 필요해
 * 516에서 별개로 판단한다. 이 함수는 「같은 문구」 경로만 막는다.
 */
let announceToggle = false
function announce(text: string): void {
  announceToggle = !announceToggle
  srAnnouncement.value = announceToggle ? `${text}\u200B` : text
}

/**
 * 실시간 연결 상태 안내 (S15P11A704-425).
 *
 * 스토어에 connectionStatus가 있었는데 템플릿 어디에도 렌더되지 않아, 소켓이 끊겨 채팅·입찰 건수·타이머
 * 동기화가 멈춘 상태와 "그냥 조용한 상태"를 구분할 수 없었다 — 사용자는 허공에 타이핑하게 된다.
 *
 * 'idle'·'closed'·'connected'는 안내하지 않는다.
 *  - 'idle': 아직 연결을 시작하지 않은 상태. **목 모드 대기방은 자체 드라이버로 돌아 이 값이 계속 'idle'이다**
 *    (WaitingRoomView가 목에선 connectWaitingRoom을 부르지 않는다) — 여기에 배너를 띄우면 정상 화면에
 *    상시 경고가 붙는다.
 *  - 'closed': 사용자가 방을 떠나 의도적으로 끊은 상태.
 *
 * 재연결 주기 중 'connecting'으로 되돌아가지 않는다 — liveSocket이 activate() 시점에만 'connecting'을 세우고
 * 재연결 진입은 onWebSocketClose가 'reconnecting'으로 잡으므로, 복구까지 문구가 흔들리지 않는다.
 */
const connectionNote = computed(() => {
  if (live.connectionStatus === 'reconnecting') return '연결이 끊겼어요 — 다시 연결 중이에요'
  if (live.connectionStatus === 'connecting') return '실시간 연결 중이에요'
  return null
})
// 시각 요소는 v-if로 나타나므로 통지는 상주 리전이 맡는다(S15P11A704-422). 값이 사라질 때(연결 복구)는
// 리전을 비우지 않는다 — 직전 안내를 지우려고 빈 문자열을 넣으면 리더가 그것도 변경으로 읽을 수 있다.
watch(connectionNote, (note) => {
  if (note) announce(note)
})

// 과거 조회 진행·완료 통지(S15P11A704-422). 새 메시지 통지가 꼬리 추가만 대상이라 prepend는 조용한데,
// 그러면 위로 스크롤해 불러온 사실 자체를 비시각 사용자가 알 수 없다 — 시작과 끝을 여기서 알린다.
//
// **사용자가 위로 스크롤해 부른 로드만** 알린다(422의 원래 취지). 방 입장 시드(679)는 사용자가 요청한
// 적 없는 로드라 「불러오는 중 → 모두 불러왔어요」가 입장하자마자 낭독되고, 그건 시작 안내를 밀어낸다.
// 스토어 플래그로는 두 경로가 같은 모양이라 **부른 쪽**에서 표시한다 — onScroll이 세우고 여기서 소비한다.
let announceNextLoad = false
watch(
  () => live.chatLoadingOlder,
  (loading, before) => {
    if (!announceNextLoad) return
    if (loading) announce('이전 메시지 불러오는 중')
    else if (before) {
      announceNextLoad = false
      // 4xx로 끊긴 경우(chatOlderBlocked)는 「모두 불러왔어요」가 거짓말이다 — 못 불러온 것이지 끝난 게 아니다.
      if (live.chatOlderBlocked) announce('이전 메시지를 불러오지 못했어요')
      else announce(live.chatHasMore ? '이전 메시지를 불러왔어요' : '이전 메시지를 모두 불러왔어요')
    }
  },
)

// 전송 실패가 새로 생기면 알린다(S15P11A704-422). 행마다 role="status"를 두는 대신 여기서 한 번 알린다 —
// 실패는 재시도·닫기 조치가 필요한 상태라 통지 가치가 있고, 인라인 표식은 시각 사용자용으로 남는다.
const failedCount = computed(() => messages.value.filter((m) => m.failed === true).length)
watch(failedCount, (now, before) => {
  if (now <= before) return
  // 사유(558)는 **덧붙이고 대체하지 않는다** — BE publicMessage 중 SLOW_MODE_LIMIT("잠시 후 다시 채팅을 보내
  // 주세요.")·CHAT_CONTENT_INVALID("채팅 내용을 확인해 주세요.")는 전송이 실패했다는 사실 자체를 말하지 않는다
  // (chat/application/ChatSendErrorCode.java). 사유로 갈아 끼우면 "안 나갔다"를 잃는다.
  const reason = [...messages.value].reverse().find((m) => m.failed === true)?.failReason
  announce(`보낸 메시지가 전송되지 않았어요.${reason ? ` ${reason}` : ''} 다시 시도할 수 있어요.`)
})

const draft = ref('')
const asQuestion = ref(false)

// 잔여 글자 카운터는 상한에 가까울 때만 띄운다 — 한 줄짜리 채팅 입력에 상시 숫자는 소음이다.
// 세는 단위는 UTF-16(`draft.length`)이다. maxlength가 실제로 자르는 단위와 같아야 보이는 숫자와
// 입력이 멈추는 시점이 일치한다 — 코드 포인트로 세면 이모지 입력 시 240/250인데 이미 안 쳐지는 모순이 난다.
const charCountShown = computed(() => CHAT_MAX_LENGTH - draft.value.length <= 20)

// AI-05 개인 검열 경고(MODERATION_WARNING) — 발신자에게만 보이는 안내. 입력 잠금은 CHAT_MUTED(locked) 담당.
// 스토어의 moderationWarning.id가 바뀔 때마다(같은 사유 반복 포함) 다시 띄운다.
//
// **10초 뒤 자동으로 사라진다 + X로 조기 종료(S15P11A704-703).** 표시 정책이 두 번 바뀐 자리라 경위를 남긴다:
//  1. 처음엔 6초 자동 소멸 + 한 줄 말줄임이었다 — 사유가 길면 읽기 전에 사라지고 다시 불러올 방법이 없어,
//     이유를 모른 채 같은 이유로 재차 경고·뮤트를 받았다.
//  2. 428이 그 둘을 함께 걷어내 **닫기 전까지 상주**로 바꿨다. 말줄임이 사라진 것으로 "못 읽는다"는 이미
//     해소됐는데, 상주까지 겹치니 안내가 메시지 목록을 계속 덮고 있어 닫기 전엔 채팅을 못 읽는다.
//  3. 지금은 **10초 자동 소멸 + X 병행**이다. 6초라 못 읽던 문제는 10초 + 여러 줄 렌더로 덮이고,
//     조기에 치우고 싶은 사람은 X로 걷는다(자동 소멸이 유일한 수단이 아니다).
// 같은 사유가 연속으로 오면 타이머를 리셋한다 — 새로 뜬 안내에 옛 카운트다운이 남으면 순식간에 사라진다.
const WARNING_TOAST_MS = 10_000
const warningText = ref<string | null>(null)
let warningTimer: ReturnType<typeof setTimeout> | null = null
function clearWarningTimer() {
  if (warningTimer !== null) clearTimeout(warningTimer)
  warningTimer = null
}
watch(
  () => live.moderationWarning,
  (w) => {
    clearWarningTimer()
    if (!w) {
      // 스토어에서 경고가 지워지면($reset·방 재입장) 떠 있던 안내도 즉시 내린다(리뷰 3).
      warningText.value = null
      return
    }
    // 최종 명세(414): message가 사용자 표시 문구다 → 그대로 띄운다. reason은 공개 enum(서버 코드)이라
    // 사용자에게 노출하지 않고 콘솔로만 남긴다(356 해소). message가 비어 오면 안전한 폴백 문구로 대체한다.
    if (w.reason) console.warn('[chat] MODERATION_WARNING reason:', w.reason)
    warningText.value = w.message || '부적절한 내용으로 검열 경고를 받았어요'
    // 스토어(live.moderationWarning)는 건드리지 않는다 — 표시 수명만 이 컴포넌트 것이고, 스토어 값을
    // 지우면 같은 경고를 보는 다른 소비자가 생겼을 때 그쪽 수명까지 이 타이머가 정하게 된다.
    warningTimer = setTimeout(() => {
      warningText.value = null
    }, WARNING_TOAST_MS)
  },
)
function dismissWarning() {
  clearWarningTimer()
  warningText.value = null
}
// 언마운트 후 발화하는 타이머는 사라진 컴포넌트의 ref를 만진다(방 전환·라우팅에서 실제로 걸린다).
onUnmounted(clearWarningTimer)

// 검열 표시 정책 (CHAT-03, 최종 명세 정합 S15P11A704-413).
// 구 per-message moderationStatus(FLAGGED/BLOCKED) 모델은 폐기됐다. 최종 명세는:
//  - 서버가 발신 시점에 마스킹을 끝내 content를 이미 가린 채로 보낸다 → 화면은 content를 그대로 렌더한다.
//  - maskedFields가 어느 필드를 가렸는지 알려줄 뿐이라, 비어있지 않으면 '검열됨' 힌트만 덧붙인다(내용 자체는 서버본 그대로).
//  - 개인 대상 판정(경고·뮤트)은 이 이벤트가 아니라 /user/queue/moderation으로 온다(dispatchModeration).
const isMasked = (m: ChatMessage) => (m.maskedFields?.length ?? 0) > 0

const listEl = ref<HTMLElement | null>(null)
function scrollToBottom() {
  if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
}

// 바닥 근처(40px 이내) 판정 — 여기 있으면 새 메시지에 맞춰 내려가고, 위로 읽는 중이면 그대로 둔다
// (라이브는 수 초 간격으로 들어와 자동 스크롤하면 읽기가 불가능해짐).
function nearBottom(el: HTMLElement): boolean {
  return el.scrollHeight - el.scrollTop - el.clientHeight < 40
}

// 위로 과거를 읽는 중 쌓인 새 메시지 수 — 하단 "새 메시지" pill로 안내한다(S15P11A704-375).
const unreadCount = ref(0)
const unreadLabel = computed(() => (unreadCount.value > 99 ? '99+' : String(unreadCount.value)))

// 내가 보낸 직후엔 스크롤 위치와 무관하게 바닥으로 — 아래 watch가 이 프레임을 '바닥으로 간주'하게 한다.
let pinNextToBottom = false

// 사용자가 실패 행을 '닫기'로 지운 프레임은 트림 보정을 건너뛴다(S15P11A704-400 리뷰 2) — 지운 행은 시야 안이라
// 아래가 위로 당겨질 뿐이고, 여기에 scrollTop 보정까지 걸면 화면이 한 행 더 튄다.
let skipTrimOnce = false

// chat 배열은 매 갱신마다 **새 참조로 교체**된다(handlers·sendChat). 상한(CHAT_KEEP) 도달 후엔 length가
// 고정돼 length 기준 watch는 새 메시지를 놓친다(VibeOverlay.vue:22-23와 같은 함정) — 참조 변화를 본다.
function keyOf(m: ChatMessage): string | number {
  return m.clientMessageId ?? m.id
}

// 브라우저가 overflow-anchor를 지원하면(Chrome·FF) 앞단 제거를 네이티브 스크롤 앵커링이 이미 보정하므로
// 수동 보정을 하지 않는다(이중 보정 방지 + 멀쩡한 경로 무변경). 미지원(Safari)에서만 아래 수동 보정이 돈다(S15P11A704-392).
const supportsScrollAnchor =
  typeof CSS !== 'undefined' &&
  typeof CSS.supports === 'function' &&
  CSS.supports('overflow-anchor', 'auto')

// 사라진 행들이 차지하던 픽셀 높이를 옛 DOM에서 합산한다(S15P11A704-392, 399). 각 제거 행의 세로 점유는
// 「다음 행의 offsetTop − 이 행의 offsetTop」(자기 높이 + 뒤 간격)이라, 제거가 중간·불연속이어도 정확하다.
// 메시지 행만 [data-chat-row]로 집는다 — el.children 순서에 기대지 않아 날짜 구분선·시스템 행이 껴도 안전(리뷰 3).
// jsdom 등 레이아웃이 없으면 offsetTop이 0이라 보정이 no-op(무해).
//
// **시야 위로 사라진 만큼만 센다**(S15P11A704-681). scrollTop을 줄이는 보정은 「내가 보던 것이 위로 밀렸다」를
// 되돌리는 것인데, 그건 사라진 행이 시야보다 **위**에 있었을 때만 참이다. 시야 안·아래 행이 사라지면 아래가
// 위로 당겨질 뿐 보던 지점은 그대로라, 거기에 보정까지 걸면 화면이 그 높이만큼 튄다.
// 상한 트림(capChat)은 항상 앞단이라 이 구분이 없어도 됐지만, CHAT_DELETED(681)는 **아무 위치의 행이나** 지운다
// — 사용자가 직접 닫은 실패 행(skipTrimOnce)이 같은 이유로 이미 예외였던 것을, 모든 제거 경로에 일반화한다.
//
// 상단 경계에 **걸친** 행은 위쪽 부분만 센다(!319 리뷰) — 위 절반이 시야 밖, 아래 절반이 시야 안인 행이
// 사라지면 밀리는 양도 그 절반이다. 전체를 세면 그만큼 과보정하고, 아예 건너뛰면 그만큼 미보정이라
// 어느 쪽이든 화면이 튄다. `min(bottom, scrollTop) - offsetTop`이 세 경우(완전히 위·걸침·시야 안)를 한 식으로
// 덮는다: 완전히 위면 전체 높이, 걸치면 scrollTop까지, 시야 안·아래면 음수라 max(0)이 0으로 접는다.
function measureRemovedPx(el: HTMLElement, indices: number[]): number {
  if (indices.length === 0) return 0
  const rows = el.querySelectorAll<HTMLElement>('[data-chat-row]')
  let sum = 0
  for (const i of indices) {
    const row = rows[i]
    if (!row) continue
    const nextRow = rows[i + 1]
    const bottom = nextRow ? nextRow.offsetTop : el.scrollHeight
    sum += Math.max(0, Math.min(bottom, el.scrollTop) - row.offsetTop)
  }
  return sum
}

// 앞에 붙은 행 블록의 픽셀 높이를 갱신 후 새 DOM에서 잰다(과거 조회 prepend, S15P11A704-379) —
// 첫 생존(기존) 행의 offsetTop 차이 = 앞에 끼운 블록 높이(간격 포함). 전부 새거면(초기 로드) 앵커 기준이 없어 0.
function measurePrependedPx(el: HTMLElement, count: number): number {
  if (count <= 0) return 0
  const rows = el.querySelectorAll<HTMLElement>('[data-chat-row]')
  const first = rows[0]
  const boundary = rows[count]
  if (!first || !boundary) return 0
  return boundary.offsetTop - first.offsetTop
}

watch(messages, async (next, prev) => {
  const el = listEl.value
  if (!el) return
  if (next.length === 0) {
    unreadCount.value = 0 // $reset·방 전환
    return
  }
  const pinned = pinNextToBottom
  pinNextToBottom = false
  const wasNearBottom = pinned || nearBottom(el)
  // 새 (라이브) 메시지 수는 **꼬리에 붙은 새 키 수**로 센다(prependedRowCount의 대칭, 리뷰 2). 과거 조회 prepend는
  // 꼬리가 그대로라 0, 에코 승격도 clientMessageId 보존이라 0 — 내 메시지 승격·prepend를 새 메시지로 오인하지 않는다.
  const appended = appendedRowCount(prev.map(keyOf), next.map(keyOf))
  // 위로 읽는 중(바닥 아님)이고 네이티브 앵커링이 없을 때만 수동 보정한다. 트림(제거)은 갱신 전 옛 DOM에서 잰다.
  // 단, 사용자가 닫기로 지운 프레임은 보정을 건너뛴다(리뷰 2 — 지운 행이 시야 안이라 보정하면 오히려 튄다).
  const manual = !wasNearBottom && !supportsScrollAnchor
  const skipTrim = skipTrimOnce
  skipTrimOnce = false
  const trimmedPx =
    manual && !skipTrim
      ? measureRemovedPx(el, removedRowIndices(prev.map(keyOf), next.map(keyOf)))
      : 0
  const prependedCount = manual ? prependedRowCount(prev.map(keyOf), next.map(keyOf)) : 0
  await nextTick()
  if (wasNearBottom) {
    scrollToBottom()
    unreadCount.value = 0
  } else {
    // 잘린 앞단만큼 위로 밀리면 scrollTop을 줄이고(392·399), 앞에 붙은 과거 메시지 높이만큼은 늘려(379) 읽던 위치를 고정한다.
    if (trimmedPx > 0) el.scrollTop = Math.max(0, el.scrollTop - trimmedPx)
    if (prependedCount > 0) el.scrollTop += measurePrependedPx(el, prependedCount)
    if (appended > 0) unreadCount.value += appended
  }
  // 꼬리에 붙은 것만 통지한다(S15P11A704-422) — 과거 조회 prepend·에코 승격은 appended가 0이라 조용하다.
  // 내가 방금 보낸 낙관 행은 빼고 통지한다(!150 리뷰 1) — 직접 친 문장을 되읽으면 통지 채널을 가장
  // 아깝게 쓰는 것이고, 전송 결과는 실패 통지가 따로 맡는다.
  if (appended > 0) {
    const others = next.slice(next.length - appended).filter((m) => !isMyOptimistic(m))
    if (others.length > 0) announceAppended(others.length, others)
  }
})

/**
 * 내가 보낸 뒤 아직 서버 에코로 승격되지 않은 행 (S15P11A704-422).
 *
 * 낙관 행 id는 `opt-` 프리픽스로 발급되고(stores/live.ts nextOptimisticId, 416) 서버 id는 ULID라 겹치지
 * 않는다. 에코가 오면 행이 서버 값으로 통째 교체돼 이 판정에서 빠지지만, 그 프레임은 꼬리 키가 그대로라
 * appended가 0이므로 애초에 통지 대상이 아니다.
 *
 * 리뷰가 제안한 `pinned`(= send()가 세운 pinNextToBottom) 대신 이걸 쓰는 이유: pinned는 프레임 단위
 * 불리언이라 "이 프레임에 내 전송이 있었다"까지만 알려 준다. 내 전송과 남의 메시지가 같은 프레임에 들어오면
 * 남의 메시지까지 함께 조용해진다 — 행 단위로 가리면 그 경우에도 남의 메시지는 통지된다.
 */
function isMyOptimistic(m: ChatMessage): boolean {
  return m.id.startsWith('opt-')
}

/**
 * 새 메시지 통지 문구 (S15P11A704-422). 한 건이면 그대로 읽고, 여러 건이 한 프레임에 들어오면 건수 + 마지막만
 * 읽는다 — 전부 읽으면 라이브 채팅에선 낭독이 끝나기 전에 다음 묶음이 온다.
 */
function announceAppended(count: number, rows: ChatMessage[]): void {
  const last = rows[rows.length - 1]
  if (!last) return
  const who = last.nickname ?? '탈퇴한 사용자'
  const body = `${who}: ${last.text}`
  announce(count === 1 ? body : `새 메시지 ${count}개. 마지막 ${body}`)
}

// 상단 근처에 닿으면 이전 메시지를 불러온다(과거 조회, S15P11A704-379). 중복은 store가 loadingOlder로 막는다.
// 빈 채팅창 시드(679)는 여기가 아니라 스토어 연결 경로(live.seedChatHistory)가 맡는다 — 빈 목록은 넘칠 게
// 없어 스크롤 이벤트 자체가 안 나는데, 그걸 이 패널의 onMounted로 메우면 자식이 부모(LiveView.connect)보다
// 먼저 마운트되는 순서에 기대게 된다.
const LOAD_OLDER_THRESHOLD_PX = 80

// 사용자가 스크롤로 바닥에 되돌아오면 미읽음을 해제하고, 상단에 닿으면 과거를 로드한다.
function onScroll() {
  const el = listEl.value
  if (!el) return
  if (nearBottom(el)) unreadCount.value = 0
  if (
    el.scrollTop <= LOAD_OLDER_THRESHOLD_PX &&
    live.chatHasMore &&
    !live.chatLoadingOlder &&
    !atOlderCap.value // 보관 상한 도달이면 더 안 부른다(406) — store도 가드하지만 스크롤마다 호출하지 않게
  ) {
    announceNextLoad = true
    void live.loadOlderChat()
  }
}

// "새 메시지" pill 클릭 — 최신으로 이동하고 미읽음 해제.
function jumpToLatest() {
  scrollToBottom()
  unreadCount.value = 0
}

// 전송 실패 행 조치 (S15P11A704-400) — 같은 clientMessageId로 재시도(멱등)하거나 닫는다.
function retryFailed(m: ChatMessage) {
  if (m.clientMessageId) live.retryChat(m.clientMessageId)
}
function dismissFailed(m: ChatMessage) {
  if (!m.clientMessageId) return
  skipTrimOnce = true // 지운 행은 시야 안 — 트림 보정 건너뛰기(리뷰 2). dismiss가 chat을 바꿔 watch가 소비한다.
  live.dismissChatFailed(m.clientMessageId)
}

/**
 * 행 액션 실패 문구 — 고정 공지(CHAT-02, S15P11A704-559)·메시지 삭제(CHAT-01, S15P11A704-681) 공용.
 *
 * **셋 다 성공해도 화면을 직접 갱신하지 않는다.** 서버가 커밋 후 채팅 토픽으로 `NOTICE_PINNED`/
 * `NOTICE_UNPINNED`/`CHAT_DELETED`를 되쏘고 handlers가 스토어를 갱신한다 — 응답으로도 덮으면 같은 상태를
 * 두 경로가 쓴다. 삭제는 낙관적으로 지우면 403·404로 실패했을 때 되살릴 방법도 없다.
 * 그래서 **실패만 화면에 남긴다.** 조용히 무시하면 눌렀는데 아무 일도 안 일어난 이유를 알 수 없다.
 *
 * 스트립이 하나인 이유: 한 번에 한 액션만 나갈 수 있어(actionBusy) 두 문구가 겹칠 일이 없다.
 * ⚠️ 이름이 `pinError`였는데 삭제 실패까지 담게 되어 바꿨다 — 이름이 담는 것을 좁게 말하면
 * 다음 사람이 "삭제 실패는 어디 있지"를 코드에서 찾게 된다.
 *
 * **문구와 함께 출처를 든다**(S15P11A704-681) — 아래 `watch(pinned)`가 공지 에코로 스트립을 지우는데,
 * 삭제 실패 문구까지 같이 지우면 관계없는 공지 변경에 사유가 사라진다. 플래그를 따로 두지 않고 한 값에
 * 묶는 이유는 둘이 어긋날 수 없게 하기 위해서다.
 */
type ActionSource = 'pin' | 'delete'
const actionError = ref<{ source: ActionSource; message: string } | null>(null)

/**
 * 호출 중 잠금 (리뷰 반영). 성공해도 화면은 소켓 에코를 기다리므로 누른 직후 **아무 일도 일어나지 않은 것처럼
 * 보인다** — 그 사이 버튼을 잠가 요청이 나갔다는 것을 알리고, 연타로 같은 요청이 겹치는 것도 막는다.
 * 행마다 상태를 들지는 않는다 — 목록이 흐르는 동안 그 상태를 어느 키에 매달지부터 문제가 된다.
 *
 * **불리언이 아니라 출처를 담는다**(!319 리뷰) — 잠금은 전부 걸어야 하지만(`!== null`) 문구는 그렇지 않다.
 * 한 값이 참·거짓뿐이면 삭제를 누른 순간 옆 행의 고정 버튼까지 「고정 중…」이 되어, 누른 적 없는 요청이
 * 나간 것처럼 보인다. 출처는 actionError와 같은 어휘를 쓴다(`ActionSource`) — 둘이 갈리면 실패 문구와
 * 진행 문구가 서로 다른 액션을 가리키게 된다. 고정·해제가 한 값('pin')인 것도 그 어휘를 따른 것이다.
 */
const actionBusy = ref<ActionSource | null>(null)

// NOTICE_* 에코가 오면(= pinnedNotice 변화) 실패 스트립을 지운다 — 다시 눌러 성공한 뒤에도 옛 실패 문구가
// 남아 있으면 화면이 스스로 모순된다. 별도 닫기 버튼은 두지 않는다(성공이 곧 해소다).
// **삭제에는 대응하는 감시가 없다**(681): 삭제 성공의 에코는 `live.chat`에서 행이 빠지는 것인데, 그 배열은
// 남의 메시지가 들어올 때마다 바뀌어서 감시하면 **다음 채팅 한 줄에 스트립이 지워진다** — 사유를 읽기 전에
// 사라지는 쪽이 더 나쁘다. 재시도할 때 아래 액션들이 진입 즉시 비우므로 「고친 뒤에도 옛 문구가 남는」
// 경로는 그걸로 이미 닫힌다.
// **고정 실패만 지운다**(681) — CHAT_DELETED가 공지 대상 메시지를 지우면 서버가 NOTICE_UNPINNED도 함께
// 보내므로, 출처를 안 가리면 삭제 실패 문구가 그 에코에 휩쓸려 사라진다(둘은 관계없는 사건이다).
watch(pinned, () => {
  if (actionError.value?.source === 'pin') actionError.value = null
})

async function pin(m: ChatMessage) {
  actionError.value = null
  actionBusy.value = 'pin'
  try {
    await pinNotice(props.roomId, m.id)
  } catch (caught) {
    actionError.value = {
      source: 'pin',
      message: `공지로 고정하지 못했어요 — ${toApiError(caught).message}`,
    }
  } finally {
    actionBusy.value = null
  }
}

async function unpin() {
  actionError.value = null
  actionBusy.value = 'pin'
  try {
    await unpinNotice(props.roomId)
  } catch (caught) {
    actionError.value = {
      source: 'pin',
      message: `공지를 해제하지 못했어요 — ${toApiError(caught).message}`,
    }
  } finally {
    actionBusy.value = null
  }
}

/**
 * CHAT-01 메시지 삭제 (S15P11A704-681) — 판매자·운영자(`canDelete`).
 *
 * **확인 단계를 두지 않는다.** 2단계 확인은 방 삭제처럼 되돌릴 수 없고 범위가 큰 조작의 선례이고,
 * 채팅 한 줄은 라이브 중에 즉시 치워야 하는 쪽이다 — 확인 모달이 방송을 덮는 비용이 오조작 비용보다 크다.
 * 실수로 눌렀을 때의 안전망은 서버 감사 로그(삭제 시각·회원·사유)이지 이 화면이 아니다.
 */
async function removeMessage(m: ChatMessage) {
  actionError.value = null
  actionBusy.value = 'delete'
  try {
    await deleteChatMessage(props.roomId, m.id)
    // 성공은 화면에 아무 흔적을 남기지 않는다 — 행이 사라지는 것은 CHAT_DELETED 에코가 오고 나서다.
    // 비시각 사용자에겐 그 사이가 통째로 침묵이라(눌렀는데 아무 일도 없다) 204 시점에 한 번 알린다.
    announce('메시지를 삭제했어요')
  } catch (caught) {
    actionError.value = {
      source: 'delete',
      message: `메시지를 삭제하지 못했어요 — ${toApiError(caught).message}`,
    }
  } finally {
    actionBusy.value = null
  }
}

/**
 * 행 액션 버튼의 접근 이름 — 「고정」·「삭제」만으로는 어느 행인지 특정할 수 없어 발신자와 본문을 함께 읽는다.
 * 본문은 CHAT-01 상한이 250자라 그대로 실으면 라벨이 문단이 된다 — 행을 고르는 데 필요한 만큼만 자른다.
 * 탈퇴 회원 표기는 행 렌더와 같은 문자열을 쓴다(리뷰) — 화면과 낭독이 다른 이름을 부르면 안 된다.
 */
/** 고정·삭제 버튼 공용 클래스 — 노출 이디엄(hover/focus 조건)이 갈리지 않게 한 곳에 둔다. 근거는 템플릿 주석. */
const ROW_ACTION_CLASS =
  'text-faint hover:text-dim ml-1.5 text-[11px] font-medium underline underline-offset-2 transition-opacity group-hover/row:pointer-events-auto group-hover/row:opacity-100 focus:pointer-events-auto focus:opacity-100 disabled:no-underline motion-reduce:transition-none [@media(hover:hover)]:pointer-events-none [@media(hover:hover)]:opacity-0'

const ROW_LABEL_MAX = 40
function rowActionLabel(m: ChatMessage, action: string): string {
  const body = m.text.length > ROW_LABEL_MAX ? `${m.text.slice(0, ROW_LABEL_MAX)}…` : m.text
  return `${m.nickname ?? '(탈퇴한 사용자)'}의 메시지를 ${action}: ${body}`
}

function send() {
  // 비로그인은 스토어를 부르기 **전에** 막는다(S15P11A704-461) — sendChat이 닉네임 null로 낙관 행을 올려
  // 목에선 유령 메시지가 쌓이고 실 서버는 거절한다(430이 막았던 실제 버그).
  if (needsLogin.value) return promptLogin()
  if (locked.value || !draft.value.trim()) return
  // 내 메시지는 위치와 무관하게 바닥으로 보내고, 미읽음으로 오인하지 않게 한다(위 watch가 pinned로 처리).
  // pinNextToBottom은 아래 sendChat이 반드시 chat을 교체해 watch가 돌 때 소비된다 — 안 돌면 다음 남의
  // 메시지에서 잘못 소비되므로, 이 함수의 가드가 sendChat의 내부 가드(muted·빈 문자열)를 이미 덮어야 한다:
  // locked(종료·비로그인·뮤트 — muted 커버), !draft.trim()(빈 문자열 커버). 사이에 await이 없어
  // 같은 tick이라 경합도 없다. ⚠️ sendChat에 가드가 더 붙으면 여기 조건도 함께 맞춰야 이 소비 보장이 유지된다.
  // sendChat의 길이 상한(CHAT_MAX_LENGTH)은 아래 textarea `maxlength`가 이미 덮는다(단위 차이는 그 상수 주석)
  // — 여기서 그 가드에 걸리는 입력을 만들 수 없어 조건을 더 붙이지 않았다.
  pinNextToBottom = true
  live.sendChat(draft.value, asQuestion.value)
  draft.value = ''
  asQuestion.value = false
}

/**
 * Enter 전송 키 핸들러 (S15P11A704-420). 한글 IME 조합 중 마지막 글자를 확정하려고 누르는 Enter는
 * `isComposing`(구형 브라우저 폴백 keyCode 229)으로 걸러 전송하지 않는다 — 조합이 덜 끝난 미완성 문장이
 * 전송되거나 같은 메시지가 두 번 나가는 것을 막는다. 조합이 아닐 때만 기본 줄바꿈을 막고 전송한다.
 * (template의 `.exact`가 Shift+Enter 등 수식어 조합은 이미 제외 → 줄바꿈으로 남는다. `.prevent`는
 *  핸들러 실행 전 무조건 preventDefault라 조합 확정까지 막으므로 쓰지 않고 여기서 조건부로 막는다.)
 */
function onEnterKey(e: KeyboardEvent) {
  if (e.isComposing || e.keyCode === 229) return
  e.preventDefault()
  send()
}

// ── 시연·검증 트리거 (목 모드 전용, S15P11A704-453) ───────────────────────────────
//
// 목 드라이버는 검열 경고·전송 실패·재연결을 쏘지 않는다. 그래서 447(스크린리더 실측)의 검증 대상 중
// 셋을 띄울 방법이 없었고, 실연동 체크리스트 §8(검열·뮤트·실패 표시)도 서버가 뜰 때까지 확인 불가였다.
//
// LiveView의 `?reveal=`·WaitingRoomView의 시연 컨트롤과 같은 성격이다 — 실 모드에는 나오지 않는다.
// ChatPanel에 둔 이유: 라이브·대기방이 이 패널을 공용하므로 한 곳이면 두 화면이 함께 커버된다.
//
// **쿼리 옵트인(`?chatdemo=1`)이다** — 목 모드만으로는 켜지지 않는다(!161 리뷰). 시연도 목 모드로 도는데
// 여기는 시연의 중심 화면이라, 상시 노출이면 심사위원에게 버튼이 보인다. 인용한 두 선례도 둘 다 조건부다:
// WaitingRoomView는 `phase === 'waiting'`에서만, LiveView의 `?reveal=`은 이미 쿼리 옵트인이다.
// 검증할 때만 쿼리를 붙이면 "시연 전에 가려야 한다"를 기억할 필요가 없어진다 — 잊었을 때의 비용이
// 한쪽으로만 쏠려 있다.
// 쿼리 파싱은 LiveStage의 ?audiodemo와 같아 shared/lib으로 뽑았다(!184 리뷰 1).
const showDemo = useMockDemoFlag('chatdemo')

function demoModerationWarning() {
  // 긴 문구로 세운다 — 428의 다중 라인 렌더와 닫기 버튼도 같이 확인된다.
  live.moderationWarning = {
    id: Date.now(),
    reason: 'OFF_PLATFORM_TRADE',
    message: '외부 메신저로 거래를 유도하는 내용이 감지되었어요. 반복되면 채팅이 제한될 수 있어요.',
    messageId: null,
  }
}

function demoSendFailure() {
  live.sendChat('전송 실패 시연 메시지')
  const mine = [...live.chat].reverse().find((m) => m.clientMessageId !== null && !m.failed)
  // 누를 때마다 사유 있는 서버 거절(558)과 사유 없는 큐 폐기(383)를 번갈아 띄운다 — 표시가 두 갈래라 둘 다 봐야 한다.
  // 갈림은 이미 쌓인 실패 행 수로 정한다(모듈 상태를 안 만든다 — 첫 클릭은 항상 사유 있는 쪽).
  const withReason = live.chat.filter((m) => m.failed === true).length % 2 === 0
  if (mine?.clientMessageId)
    live.markChatFailed(
      mine.clientMessageId,
      // 실제 BE publicMessage를 그대로 쓴다(ChatSendErrorCode). CHAT_CONTENT_INVALID를 고른 이유는
      // 이 문구가 전송 실패 사실을 전혀 말하지 않는 최악 케이스라 — 「전송 실패」 병기가 실제로 필요한지
      // 시연에서 바로 드러난다. 창작 문구를 쓰면 그 검증이 안 된다.
      withReason ? '채팅 내용을 확인해 주세요.' : undefined,
    )
}

function demoReconnecting() {
  // 토글 — 끊김 안내를 띄운 뒤 복구까지 들어봐야 리전 순서를 볼 수 있다.
  live.connectionStatus = live.connectionStatus === 'reconnecting' ? 'connected' : 'reconnecting'
}

function demoDuplicateMessage() {
  // 같은 닉네임·내용을 연속 두 번 — 통지 리전의 텍스트가 동일해지는 경우다(447 항목 1).
  // messageId만 다르게 해야 handleChatMessage가 중복으로 걸러내지 않는다.
  const base = {
    memberId: 99,
    nickname: '서연',
    content: '이거 실물 상태 어때요?',
    isQuestion: false,
  }
  for (const suffix of ['a', 'b']) {
    dispatchChatTopic(live, {
      type: 'CHAT_MESSAGE',
      data: { ...base, messageId: `demo-dup-${Date.now()}-${suffix}`, clientMessageId: null },
    })
  }
}
</script>

<template>
  <div
    class="bg-surface border-line relative flex h-full flex-col overflow-hidden rounded-[14px] border"
  >
    <!-- 헤더 -->
    <div class="border-line text-ink flex-none border-b px-4 py-3 text-[13.5px] font-semibold">
      {{ title }}
    </div>

    <!-- AI-05 개인 검열 경고 — 발신자에게만. 10초 뒤 자동으로 사라지고 X로 조기에도 걷는다(S15P11A704-703).
         428이 말줄임과 함께 걷어냈던 자동 소멸을, 못 읽던 원인이던 6초가 아니라 10초로 되살린 것이다
         (경위는 스크립트 warningText 주석). 여러 줄로 늘어나므로 rounded-full 대신 각을 죽인 박스이고,
         아이콘은 첫 줄에 맞춘다(items-start). 레드=라이브·입찰 기능색이라 중립 다크 톤(규칙 1). -->
    <div
      v-if="warningText"
      class="text-on-dark bg-ink/90 absolute top-14 left-1/2 z-[2] flex max-w-[calc(100%-1.5rem)] -translate-x-1/2 items-start gap-2 rounded-[12px] px-3.5 py-2 text-[12px] shadow-lg backdrop-blur-md"
      role="alert"
    >
      <svg
        class="mt-[3px] h-3.5 w-3.5 flex-none"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <path
          d="M10.3 3.9 2.6 17.3A2 2 0 0 0 4.3 20.3h15.4a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"
        />
        <path d="M12 9v4" />
        <path d="M12 17h.01" />
      </svg>
      <!-- text-balance는 두 줄 이상일 때 줄 길이를 고르게 나눈다 — 마지막 줄에 두 글자만 남는 모양을 막는다. -->
      <span class="min-w-0 flex-1 leading-relaxed text-balance">{{ warningText }}</span>
      <!-- p-1.5는 미관이 아니라 타깃 크기다 — 14px 아이콘 + 좌우 6px = 26×26으로 WCAG 2.5.8(AA) 최소 24를 넘긴다.
           10초 자동 소멸이 돌아온 뒤로도(703) 이 버튼은 남는다 — 안내가 메시지 목록을 덮는 동안 기다리지 않고
           치우는 수단이고, 못 맞히면 그 10초 내내 채팅을 못 읽는다. 더 줄이지 말 것(!142 리뷰 1). -->
      <button
        type="button"
        class="text-on-dark-dim hover:text-on-dark -mr-1 flex-none rounded p-1.5 transition-colors motion-reduce:transition-none"
        aria-label="검열 경고 닫기"
        @click="dismissWarning"
      >
        <svg
          class="h-3.5 w-3.5"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <path d="M6 6l12 12M18 6L6 18" />
        </svg>
      </button>
    </div>

    <!-- 정적 안내(대기방 문맥 등) — 서버 공지(pinnedNotice)와 별개. 디자인 원본 `.notice`는 레드 틴트 -->
    <p
      v-if="hint"
      class="border-line bg-terra-tint text-terra-tint-text flex-none border-b px-4 py-2.5 text-[12px] leading-relaxed"
    >
      {{ hint }}
    </p>

    <!-- 고정 공지 (CHAT-02) — 공지는 레드 틴트 -->
    <div
      v-if="pinned?.content"
      class="bg-terra-tint text-terra-tint-text flex flex-none items-start gap-2 px-4 py-2.5 text-[12px] leading-relaxed"
    >
      <svg
        class="mt-0.5 h-3.5 w-3.5 flex-none"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <path d="M3 11l14-6v14L3 13Z" />
        <path d="M17 8.5a3 3 0 0 1 0 7" />
      </svg>
      <span class="min-w-0 flex-1">{{ pinned.content }}</span>
      <!-- CHAT-02 해제 (S15P11A704-559) — 판매자만. 배너 안이라 색은 그 틴트의 전경 토큰을 그대로 쓰고,
           밑줄로만 링크성을 준다(레드 배경 위에 또 다른 색을 얹지 않는다, 규칙 1). -->
      <button
        v-if="canPin"
        type="button"
        class="text-terra-tint-text flex-none rounded px-1 text-[11px] font-medium underline underline-offset-2 hover:no-underline disabled:no-underline disabled:opacity-60"
        aria-label="고정 공지 해제"
        :disabled="actionBusy !== null"
        @click="unpin"
      >
        {{ actionBusy === 'pin' ? '해제 중…' : '해제' }}
      </button>
    </div>

    <!-- 행 액션 실패 (고정 S15P11A704-559 · 삭제 S15P11A704-681) — 조용한 무시 금지. 성공은 소켓 에코가
         배너·행 제거로 보여주므로 여기 남는 것은 실패뿐이다. 레드는 라이브·입찰 기능색이라 중립 톤(규칙 1).
         role="alert"은 삽입 시 낭독이 규격에 포함돼 v-if로 나타나도 통지가 유실되지 않는다(422 주석 참조). -->
    <p
      v-if="actionError"
      class="border-line bg-line/40 text-dim flex-none border-b px-4 py-2 text-[12px] leading-relaxed"
      role="alert"
    >
      {{ actionError.message }}
    </p>

    <!-- 메시지 목록 -->
    <div class="relative flex min-h-0 flex-1 flex-col">
      <!-- 과거 조회 로딩(S15P11A704-379) — absolute라 목록 높이를 안 밀어(스크롤 앵커링 방해 없음) 상단에 뜬다 -->
      <div
        v-if="live.chatLoadingOlder"
        class="bg-ink text-surface absolute top-2 left-1/2 z-[1] -translate-x-1/2 rounded-full px-3 py-1 text-[11px] shadow-md backdrop-blur-sm"
      >
        이전 메시지 불러오는 중…
      </div>
      <!-- 스크린리더 통지 리전 (S15P11A704-422) — 화면에서 보이지 않고 항상 DOM에 남는다.
           내용을 갈아 끼우는 방식이라 v-if 삽입으로 낭독을 놓치는 문제가 없다(MR !148 리뷰). -->
      <p class="sr-only" role="status" aria-live="polite">{{ srAnnouncement }}</p>
      <!-- role="log"은 채팅 로그의 의미를 준다(탐색에 쓰인다). 다만 log의 암시 aria-live(polite)는 끈다 —
           그대로 두면 과거 조회 prepend 20건까지 낭독된다. 새 메시지 통지는 위 리전이 꼬리 추가만 골라 맡는다. -->
      <div
        ref="listEl"
        class="min-h-0 flex-1 space-y-1 overflow-y-auto px-4 py-3"
        role="log"
        aria-live="off"
        @scroll="onScroll"
      >
        <!-- 과거 조회 끝(S15P11A704-379) — 맨 위에 도달했고 더 없을 때. 인라인이라 스크롤백 최상단에 정적으로 뜬다.
             **서버가 마지막 페이지를 준 경우만이다**(679): 4xx로 끊긴 경우(chatOlderBlocked)는 hasMore가 같이
             내려가지만 "처음"에 닿은 게 아니라 401 비로그인·403·비공개 방 404로 **못 읽은** 것이라, 그때 이
             문구를 띄우면 화면이 사용자에게 거짓말을 한다. 대신 아무 문구도 띄우지 않는다 —
             막힌 이유를 여기서 설명할 재료가 없고(코드는 콘솔에 남긴다), 위쪽 잠금·로그인 안내가 이미 맥락을 준다. -->
        <p
          v-if="!live.chatHasMore && !live.chatOlderBlocked && messages.length > 0"
          class="text-faint py-2 text-center text-[11.5px]"
        >
          대화의 처음이에요
        </p>
        <!-- 과거 보관 상한(406) — 서버엔 더 있지만 메모리 방어로 여기까지만. 종료(처음이에요)와 구분한다. -->
        <p
          v-else-if="atOlderCap && messages.length > 0"
          class="text-faint py-2 text-center text-[11.5px]"
        >
          이전 메시지가 많아 여기까지만 불러왔어요
        </p>
        <p v-if="messages.length === 0" class="text-faint py-8 text-center text-[12.5px]">
          {{ emptyText }}
        </p>
        <!-- 실패 행은 좌측 보더로 행 자체를 표시한다(S15P11A704-424) — 인라인 문구만으로는 빠르게 흐르는
             라이브 채팅에서 내 메시지가 안 나갔다는 걸 놓친다. 레드는 라이브·입찰 기능색이라 중립 톤(규칙 1).
             (설명을 :class 배열 안에 줄 주석으로 두지 않는다 — 속성이 한 줄로 접히면 뒤 항목이 통째로
              주석 처리돼 클래스가 조용히 사라진다. 빌드는 그대로 통과한다.) -->
        <div
          v-for="m in messages"
          :key="m.clientMessageId ?? m.id"
          data-chat-row
          class="group/row text-[13px] leading-relaxed"
          :class="[
            highlightQuestions && m.isQuestion ? 'bg-slate-tint rounded-[8px] px-2 py-1' : '',
            m.pending ? 'opacity-60' : '',
            m.failed ? 'border-faint border-l-2 pl-2' : '',
          ]"
        >
          <span
            v-if="m.isQuestion"
            class="bg-slate-tint text-slate-tint-text mr-1.5 rounded-full px-1.5 py-0.5 text-[10px] font-semibold"
            >질문</span
          >
          <span class="text-ink mr-1.5 font-semibold">{{ m.nickname ?? '(탈퇴한 사용자)' }}</span>
          <!-- content는 서버가 이미 마스킹해 보낸다(CHAT-03) — 그대로 렌더한다. -->
          <span class="text-dim">{{ m.text }}</span>
          <!-- 가려진 필드가 있으면 중립 '검열됨' 힌트만 붙인다. 레드는 라이브·입찰 기능색이라 안 쓴다(규칙 1).
             maskedFields는 content 외 다른 필드(닉네임 등)도 가릴 수 있어 문구는 특정 필드를 단정하지 않는다. -->
          <span
            v-if="isMasked(m)"
            class="bg-line/60 text-dim ml-1.5 rounded-full px-1.5 py-0.5 text-[10px] font-medium"
            title="약관에 따라 일부 내용이 가려졌어요"
            >검열됨</span
          >
          <!-- 전송 실패 — 세 경로가 여기로 모인다: 대기 큐 상한 초과 폐기(383)·에코 없는 전송 타임아웃(424)·
             개인 에러 큐를 통한 서버 거절(558). 재입력 신호라 흐리게 안 하고 중립 표식으로 주의를 끈다
             (레드=기능색이라 배제, 규칙 1). 재시도(같은 clientMessageId, 멱등)·닫기 경로.
             flex-wrap: 사유가 길면 문구 + 버튼 2개가 패널 폭을 넘어 잘린다 — 넘치면 줄을 바꾼다. -->
          <span
            v-if="m.failed"
            class="ml-1.5 inline-flex flex-wrap items-center gap-1.5 text-[11.5px]"
          >
            <!-- 표식을 키우고(10 → 11.5px) 굵게, 아이콘을 붙였다(S15P11A704-424). 이전엔 text-dim 10px라
                 "주의를 끈다"는 의도와 실제 렌더가 어긋났다. 색이 아니라 크기·굵기·아이콘으로 대비를 만든다. -->
            <span class="text-ink inline-flex items-center gap-1 font-semibold">
              <svg
                class="h-3 w-3 flex-none"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2.2"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path
                  d="M10.3 3.9 2.6 17.3A2 2 0 0 0 4.3 20.3h15.4a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"
                />
                <path d="M12 9v4" />
                <path d="M12 17h.01" />
              </svg>
              <!-- 서버 사유(WS-04 publicMessage, S15P11A704-558)는 **병기**한다 — 대체하면 안 된다.
                   SLOW_MODE_LIMIT·CHAT_CONTENT_INVALID의 문구는 전송이 실패했다는 사실을 말하지 않아서
                   (ChatSendErrorCode.java) 사유만 남기면 "안 나갔다"가 사라진다. -->
              {{ m.failReason ? `전송 실패 — ${m.failReason}` : '전송 실패' }}
            </span>
            <button
              type="button"
              class="text-ink disabled:text-faint font-semibold underline underline-offset-2 hover:no-underline disabled:no-underline"
              :disabled="locked"
              @click="retryFailed(m)"
            >
              다시 시도
            </button>
            <button
              type="button"
              class="text-faint hover:text-dim"
              aria-label="실패한 메시지 닫기"
              @click="dismissFailed(m)"
            >
              닫기
            </button>
          </span>
          <!-- 전송 대기(S15P11A704-376) — 에코 승격 전. 중립색만. 에코 오면 pending 해제돼 사라진다 -->
          <span v-else-if="m.pending" class="text-faint ml-1.5 text-[10px]" title="전송 중"
            >전송 중…</span
          >
          <!-- CHAT-02 고정 (S15P11A704-559, 판매자) · CHAT-01 삭제 (S15P11A704-681, 판매자·운영자) —
               와이어프레임에 이 액션들의 UI 근거가 없어 같은 행에 이미 있는 텍스트 버튼(재시도·닫기)과 같은
               톤으로 둔다. 레드는 기능색이라 안 쓴다(규칙 1).
               **상시 노출하지 않는다** — 라이브 채팅은 행이 계속 흐르는데 매 행에 버튼이 붙으면 읽기가 방해된다.
               opacity만 바꾸므로 행 높이가 흔들리지 않는다(스크롤 보정 392·379의 측정과 무관).

               ⚠️ **숨김 자체를 `(hover: hover)`로 가둔다** — LiveStage.vue의 시청 컨트롤과 같은 해법이다.
               Tailwind v4는 `group-hover`를 `@media (hover:hover)`로 감싸 내보내므로, 조건 없는 `opacity-0`은
               터치 기기에서 **영구 투명인데 클릭은 살아 있는** 유령 버튼이 된다 — 판매자가 모르는 사이 공지가
               바뀌거나 남의 메시지가 지워진다. hover를 못 만드는 환경에서는 처음부터 상시 노출이고, 그게 의도다.
               `pointer-events-none`을 함께 걸어 hover 환경에서도 투명한 동안엔 눌리지 않게 한다.
               focus 쪽은 미디어 조건 없이 둔다 — 키보드는 hover를 못 만드는 유일한 비-터치 경로다.
               hover 소스는 행의 `group/row`다(이름 있는 그룹 규약, LiveLayout.vue:52-57) — 이름 없는 `group`을
               쓰면 행 안에 다른 `group`이 생기는 순간 조용히 그쪽에 붙는다.
               클래스가 길어 script의 ROW_ACTION_CLASS 한 곳에 두고 둘이 나눠 쓴다 — 복붙하면 한쪽만 고치는
               순간 노출 규칙이 갈린다.
               서버 확정 ULID(m.id)를 보내야 하므로 아직 id가 임시(`opt-`)인 행은 제외한다 — 판별은 id
               프리픽스를 보는 기존 `isMyOptimistic`을 그대로 쓴다(loadOlderChat 커서와 같은 이디엄).
               pending·failed 플래그로 가리면 목 모드처럼 플래그 없이 낙관 행만 남는 경로에서 버튼이 새고,
               누르면 임시 id가 그대로 서버로 나간다(!296 리뷰). -->
          <button
            v-if="canPin && !isMyOptimistic(m)"
            type="button"
            :class="ROW_ACTION_CLASS"
            :aria-label="rowActionLabel(m, '공지로 고정')"
            :disabled="actionBusy !== null"
            @click="pin(m)"
          >
            {{ actionBusy === 'pin' ? '고정 중…' : '고정' }}
          </button>
          <button
            v-if="canDelete && !isMyOptimistic(m)"
            type="button"
            :class="ROW_ACTION_CLASS"
            :aria-label="rowActionLabel(m, '삭제')"
            :disabled="actionBusy !== null"
            @click="removeMessage(m)"
          >
            {{ actionBusy === 'delete' ? '삭제 중…' : '삭제' }}
          </button>
        </div>
      </div>

      <!-- 위로 읽는 중 새 메시지 도착 안내(S15P11A704-375) — 클릭 시 최신으로 이동. 신호가 아니라 UI
           어포던스라 레드(기능색, 규칙 1) 대신 중립(bg-ink, 전송 버튼과 동일 톤). 개수·동작은 aria-label로 안내 -->
      <button
        v-if="unreadCount > 0"
        type="button"
        class="bg-ink text-surface absolute bottom-3 left-1/2 flex -translate-x-1/2 items-center gap-1 rounded-full px-3 py-1.5 text-[12px] font-semibold shadow-lg transition-opacity hover:opacity-90 motion-reduce:transition-none"
        :aria-label="`새 메시지 ${unreadLabel}개 — 최신으로 이동`"
        @click="jumpToLatest"
      >
        <svg
          class="h-3.5 w-3.5"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <path d="M6 9l6 6 6-6" />
        </svg>
        새 메시지 {{ unreadLabel }}
      </button>
    </div>

    <!-- 입력 -->
    <div class="border-line flex-none border-t p-3">
      <!-- 연결 상태 안내 (S15P11A704-425) — 입력 바로 위에 둔다. 타이핑하는 자리 옆이 가장 눈에 걸리고,
           헤더 아래는 검열 경고(absolute top-14)와 박스가 겹친다. 레드는 라이브·입찰 기능색이라 중립 톤(규칙 1) —
           끊김을 레드로 칠하면 "지금 벌어지는 일" 신호와 뜻이 섞인다. -->
      <p
        v-if="connectionNote"
        class="bg-line/40 text-dim mb-2.5 flex items-center gap-1.5 rounded-[10px] px-3 py-2 text-[12px]"
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
          <path d="M21 12a9 9 0 0 1-9 9 9 9 0 0 1-6.7-3" />
          <path d="M3 12a9 9 0 0 1 9-9 9 9 0 0 1 6.7 3" />
          <path d="M3 17v-5h5M21 7v5h-5" />
        </svg>
        {{ connectionNote }}
      </p>
      <template v-if="locked">
        <LockNote :text="lockText" />
      </template>
      <template v-else>
        <!-- 비로그인 로그인 유도 (S15P11A704-461) — 입력하려는 순간에만 뜬다. 모달이 아니라 인라인이라
             방송·남은 시간을 가리지 않고, 포커스를 훔치지 않아 입력창으로 돌아올 때 반복되지 않는다.
             복귀 지점은 헤더 지갑 칩과 같은 방식으로 redirect 쿼리에 실어 보낸다(useKakaoAuth가 소비).
             레드(terra)는 라이브·입찰 기능색이라 안내엔 쓰지 않고 중립 톤으로 둔다(규칙 1). -->
        <p
          v-if="needsLogin && loginPromptShown"
          :id="loginPromptId"
          class="border-faint/50 bg-line/40 text-dim mb-2.5 flex flex-wrap items-center gap-x-1.5 gap-y-1 rounded-[10px] border border-dashed px-3 py-2 text-[12px]"
        >
          <span>🔒 채팅을 보내려면 로그인이 필요해요.</span>
          <RouterLink
            :to="{ name: 'login', query: { redirect: route.fullPath } }"
            class="text-ink font-semibold underline underline-offset-2 hover:no-underline"
          >
            로그인하기
          </RouterLink>
        </p>
        <div class="flex items-end gap-2">
          <!-- 비로그인은 readonly다(461) — 이벤트로 첫 글자를 막으면 그 글자는 이미 들어가 있다.
               못 치게 하면 잃을 초안이 없고, 실 모드 로그인은 페이지를 떠나 초안 보존이 불가능하다.
               모양은 로그인 상태와 같게 둔다 — 잠긴 박스로 바꾸면 430의 "고장 나 보이는" 문제로 돌아간다. -->
          <!-- CHAT-01 확정 상한 (단위 차이는 stores/live.ts CHAT_MAX_LENGTH 주석 참조). -->
          <textarea
            v-model="draft"
            rows="1"
            :maxlength="CHAT_MAX_LENGTH"
            :readonly="needsLogin"
            :placeholder="needsLogin ? '로그인하면 채팅할 수 있어요' : '메시지 입력…'"
            :aria-describedby="needsLogin && loginPromptShown ? loginPromptId : undefined"
            aria-label="채팅 메시지"
            class="border-line text-ink focus:border-terra focus:ring-terra-tint bg-surface max-h-24 min-h-[38px] flex-1 resize-none rounded-[10px] border px-3 py-2 text-[13px] outline-none focus:ring-2"
            @focus="promptLogin"
            @keydown.enter.exact="onEnterKey"
          />
          <button
            type="button"
            class="bg-ink text-surface rounded-[10px] px-3.5 py-2 text-[13px] font-semibold transition-opacity disabled:opacity-40"
            :disabled="!needsLogin && !draft.trim()"
            @click="send"
          >
            전송
          </button>
        </div>
        <!-- 판매자 화면(콘솔)은 질문 체크박스가 없어 이 줄이 글자수 카운터만 남는다 —
             둘 다 없을 때 빈 줄의 위 여백만 남지 않게 래퍼째 접는다 (S15P11A704-685) -->
        <div v-if="canAskQuestion || charCountShown" class="mt-2 flex items-center gap-2">
          <label
            v-if="canAskQuestion"
            class="text-dim flex min-w-0 items-center gap-1.5 text-[12px]"
          >
            <input v-model="asQuestion" type="checkbox" class="accent-slate h-3.5 w-3.5" />
            질문 — 체크하면 판매자가 모아서 확인해요
          </label>
          <!-- 상한 근처에서만 나온다. 표기·색은 RoomCreateStep1·OnboardingStep의 글자수 카운터와 같게
               (토큰 색 + tabular-nums), aria도 그 둘처럼 붙이지 않는다 — 값은 입력창에 그대로 보이고
               타이핑마다 낭독되면 오히려 방해다. -->
          <span v-if="charCountShown" class="text-dim ml-auto flex-none text-[11.5px] tabular-nums">
            {{ draft.length }}/{{ CHAT_MAX_LENGTH }}
          </span>
        </div>
      </template>

      <!-- 시연·검증 트리거 (목 모드 전용, S15P11A704-453) — 목이 쏘지 않는 통지를 손으로 띄운다.
           실 모드에는 나오지 않는다. WaitingRoomView의 시연 컨트롤과 같은 톤(중립·작은 텍스트 버튼). -->
      <div
        v-if="showDemo"
        class="border-line mt-2.5 flex flex-wrap items-center gap-1 border-t pt-2"
      >
        <span class="text-faint pr-1 text-[10.5px]">시연</span>
        <button
          type="button"
          class="text-dim hover:bg-cream rounded-full px-2 py-1 text-[11px] font-medium transition motion-reduce:transition-none"
          @click="demoModerationWarning"
        >
          검열 경고
        </button>
        <button
          type="button"
          class="text-dim hover:bg-cream rounded-full px-2 py-1 text-[11px] font-medium transition motion-reduce:transition-none"
          @click="demoSendFailure"
        >
          전송 실패
        </button>
        <button
          type="button"
          class="text-dim hover:bg-cream rounded-full px-2 py-1 text-[11px] font-medium transition motion-reduce:transition-none"
          :aria-pressed="live.connectionStatus === 'reconnecting'"
          @click="demoReconnecting"
        >
          재연결
        </button>
        <button
          type="button"
          class="text-dim hover:bg-cream rounded-full px-2 py-1 text-[11px] font-medium transition motion-reduce:transition-none"
          @click="demoDuplicateMessage"
        >
          같은 메시지 2연속
        </button>
      </div>
    </div>
  </div>
</template>
