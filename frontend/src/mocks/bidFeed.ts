// BID-02 입찰 상태 브로드캐스트 / BID-04 Vibe 피드 목 드라이버.
//
// **FE2의 실 STOMP 연동 전까지만 쓰는 임시 파일이다** (docs/03 §7의 목 드라이버와 같은 역할).
// 이벤트 이름·필드는 명세 「입찰 상태 브로드캐스트」를 그대로 따른다 — 목에서만 통하는 이름을
// 만들면 실소켓 전환 때 핸들러를 재사용한다는 전제가 깨진다(§6-6).
//
// 파일명을 `liveFeed.ts`로 하지 않은 이유: 그 이름은 FE2 소유 드라이버 자리로 문서에 예약돼 있다.

/** 백엔드 공통 규격 — 모든 WS 메시지는 `{ type, data }` 래핑 (AGENTS.md 규칙 9) */
export interface SocketEvent<TType extends string, TData> {
  type: TType
  data: TData
}

/**
 * `BID_ACCEPTED.data` — 비크리 공개 범위가 곧 이 세 필드다.
 * 입찰자 ID·닉네임·금액·최고가·2등가·순위는 **오지 않는다**(명세 「공개 금지 정보」).
 */
export interface BidAcceptedData {
  /** 누적 입찰 건수 */
  bidCount: number
  /** 한 번 이상 입찰한 고유 참여자 수 */
  participantCount: number
  /** 입찰 기세 연출 문구. 별도 BID_VIBE 이벤트는 없다 — 이 필드에 실려 온다 */
  vibeMessage: string
}

export type BidAcceptedEvent = SocketEvent<'BID_ACCEPTED', BidAcceptedData>

// 디자인 원본(`비드라이브 인터랙티브.html`)의 vibeTexts + 명세 예시 문구
const VIBE_MESSAGES = [
  '🔥 누군가 베팅을 진행 중입니다!',
  '⚡ 새 입찰이 들어왔어요',
  '👀 방금 3명 입장',
  '🔥 베팅 열기 상승 중',
]

export function randomVibeMessage(): string {
  return VIBE_MESSAGES[Math.floor(Math.random() * VIBE_MESSAGES.length)]
}

/** 이벤트 하나 만들기 — 내 입찰이 접수됐을 때 서버 브로드캐스트를 흉내 내는 데도 쓴다 */
export function makeBidAcceptedEvent(bidCount: number, participantCount: number): BidAcceptedEvent {
  return {
    type: 'BID_ACCEPTED',
    data: { bidCount, participantCount, vibeMessage: randomVibeMessage() },
  }
}

/**
 * 남이 입찰하는 상황을 주기적으로 흘려보낸다.
 * 반환값은 정지 함수 — 화면 언마운트 시 반드시 호출해야 타이머가 남지 않는다.
 */
export function startMockBidFeed(
  seed: { bidCount: number; participantCount: number },
  onEvent: (event: BidAcceptedEvent) => void,
  intervalMs = 5200,
): () => void {
  let bidCount = seed.bidCount
  let participantCount = seed.participantCount

  const timer = setInterval(() => {
    bidCount += 1
    // 상향 입찰은 참여자 수를 늘리지 않는다 — 가끔만 새 사람이 들어온 것으로 처리한다
    if (Math.random() < 0.35) participantCount += 1
    onEvent(makeBidAcceptedEvent(bidCount, participantCount))
  }, intervalMs)

  return () => clearInterval(timer)
}
