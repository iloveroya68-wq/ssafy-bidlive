// PNT-01·02 지갑·원장 목 데이터 — 백엔드 스키마(`wallet`, `trade_point_ledger`, `betting_point_ledger`) 기준.
// 참조: docs/04-백엔드-스키마-메모.md §3.5
//
// 스키마 반영 사항
// - `wallet`: UNIQUE(member_id, wallet_type), wallet_type IN ('TRADE','BETTING')
//   → 회원 1명당 지갑 2행. availableBalance/heldBalance는 별개 컬럼이고 둘 다 >= 0.
//   → availableBalance는 **이미 보류를 제외한 값**이다. 총 보유 = available + held.
// - 원장은 DB상 두 테이블이지만 **API는 단일 엔드포인트**(`GET /members/me/point-ledgers`)다.
//   행은 availableChange·heldChange 2델타를 함께 가진다.
//   → 홀드 행은 availableChange=-X / heldChange=+X로 총합이 변하지 않는다(원장 UI에서 "증감 0"으로 보이면 안 됨).
// - `*BalanceAfter`가 함께 오므로 각 행 시점의 잔액을 표시할 수 있다.
//
// 값 집합의 단일 소스는 `shared/api/wallets.ts`다 — 목이 타입을 따로 정의하면 또 어긋난다.
import type { PointReferenceType, PointTransactionType } from '@/shared/api/wallets'

/** `wallet.wallet_type` */
export type WalletType = 'TRADE' | 'BETTING'

/** `wallet` 1행 */
export interface Wallet {
  /** `wallet.wallet_id` (BIGINT — 직렬화 형식은 BE 확인 필요) */
  walletId: number
  walletType: WalletType
  /** `available_balance` — 보류를 이미 제외한 사용 가능 잔액 (>= 0) */
  availableBalance: number
  /** `held_balance` — 보류 중 잔액 (>= 0) */
  heldBalance: number
}

/**
 * 원장 행. **명세 「포인트 원장 내역 조회」(PNT-02, 확정)의 응답 항목과 필드가 1:1**이다 —
 * 예외는 `createdAt`뿐으로, 목 작성 편의상 epoch ms를 쓰고 `shared/api/wallets.ts`에서 ISO로 옮긴다.
 *
 * FE가 임의로 지어냈던 13종 `transactionType`(`BID_HOLD`·`SIGNUP_REWARD` 등)은 걷어냈다.
 * 명세 8종 + `referenceType` 7값이 정본이고, 행 문구는 **서버가 준 `description`**을 그대로 쓴다.
 */
export interface LedgerEntry {
  /** `ledgerId` */
  id: number
  walletType: WalletType
  transactionType: PointTransactionType
  /** 가용 포인트 증감 (부호 있음) */
  availableChange: number
  /** 보류 포인트 증감 (부호 있음). 홀드 행은 `availableChange`와 부호가 반대고 합이 0이다 */
  heldChange: number
  availableBalanceAfter: number
  heldBalanceAfter: number
  /** 목은 epoch ms. 서버는 ISO 8601이다 */
  createdAt: number
  /** 서버 생성 문구 — 프론트가 enum을 조합해 만들지 않는다 */
  description: string
  referenceType: PointReferenceType
  referenceId: number | null
}

const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

// 목 데이터는 모듈 로드 시각 기준으로 상대 시각을 만든다 (고정 epoch를 박으면 나중에 열었을 때 어색해진다).
// 실 연동 뒤에는 **테스트 픽스처 전용**이다(S15P11A704-504) — `mocks/walletServer.ts`가 유일한 소비자다.
const now = Date.now()

export const mockWallets: Wallet[] = [
  // 거래 지갑 — 충전·출금 가능. 진행 중인 입찰 1건이 보류로 잡혀 있다.
  { walletId: 1, walletType: 'TRADE', availableBalance: 128_000, heldBalance: 82_000 },
  // 베팅 지갑 — 무료 재화(충전·출금·상호 전환 없음). 예측 1건이 보류 중.
  { walletId: 2, walletType: 'BETTING', availableBalance: 950, heldBalance: 300 },
]

// ── 거래 포인트 원장 — 최신순 ──
export const mockTradeLedger: LedgerEntry[] = [
  {
    id: 1011,
    walletType: 'TRADE',
    transactionType: 'HOLD',
    availableChange: -82_000,
    heldChange: 82_000, // 총합 0 — 원장 UI는 이 행을 "보류"로 표기해야 한다
    availableBalanceAfter: 128_000,
    heldBalanceAfter: 82_000,
    createdAt: now - 26 * MINUTE,
    description: '기계식 키보드 커스텀 풀빌드 · 입찰 보류',
    referenceType: 'BID',
    referenceId: 351,
  },
  {
    id: 1010,
    walletType: 'TRADE',
    transactionType: 'CHARGE',
    availableChange: 100_000,
    heldChange: 0,
    availableBalanceAfter: 210_000,
    heldBalanceAfter: 0,
    createdAt: now - 3 * HOUR,
    description: '카카오페이 충전 · 테스트 결제',
    referenceType: 'PAYMENT',
    referenceId: 9001,
  },
  {
    id: 1009,
    walletType: 'TRADE',
    transactionType: 'SETTLEMENT',
    availableChange: 0,
    heldChange: -74_000, // 보류분이 그대로 결제로 빠진다
    availableBalanceAfter: 110_000,
    heldBalanceAfter: 0,
    createdAt: now - DAY - 2 * HOUR,
    description: '빈티지 필름 카메라 세트 · 낙찰 결제(2등가)',
    referenceType: 'DEAL',
    referenceId: 501,
  },
  {
    id: 1008,
    walletType: 'TRADE',
    transactionType: 'RELEASE',
    availableChange: 8_000,
    heldChange: -8_000, // 총합 0 — 2등가 낙찰로 생긴 홀드 차액 복구
    availableBalanceAfter: 110_000,
    heldBalanceAfter: 74_000,
    createdAt: now - DAY - 2 * HOUR,
    description: '빈티지 필름 카메라 세트 · 홀드 차액 복구',
    referenceType: 'BID',
    referenceId: 349,
  },
  {
    id: 1007,
    walletType: 'TRADE',
    transactionType: 'HOLD',
    availableChange: -82_000,
    heldChange: 82_000,
    availableBalanceAfter: 102_000,
    heldBalanceAfter: 82_000,
    createdAt: now - DAY - 3 * HOUR,
    description: '빈티지 필름 카메라 세트 · 입찰 보류',
    referenceType: 'BID',
    referenceId: 349,
  },
  {
    id: 1006,
    walletType: 'TRADE',
    transactionType: 'RELEASE',
    availableChange: 61_000,
    heldChange: -61_000, // 총합 0 — 미낙찰 보류 해제
    availableBalanceAfter: 184_000,
    heldBalanceAfter: 0,
    createdAt: now - 2 * DAY,
    description: '헤드폰 하이엔드 오픈형 · 미낙찰 보류 해제',
    referenceType: 'BID',
    referenceId: 341,
  },
  {
    id: 1005,
    walletType: 'TRADE',
    transactionType: 'HOLD',
    availableChange: -61_000,
    heldChange: 61_000,
    availableBalanceAfter: 123_000,
    heldBalanceAfter: 61_000,
    createdAt: now - 2 * DAY - 40 * MINUTE,
    description: '헤드폰 하이엔드 오픈형 · 입찰 보류',
    referenceType: 'BID',
    referenceId: 341,
  },
  {
    id: 1004,
    walletType: 'TRADE',
    transactionType: 'SETTLEMENT',
    availableChange: 96_000,
    heldChange: 0,
    availableBalanceAfter: 184_000,
    heldBalanceAfter: 0,
    createdAt: now - 4 * DAY,
    description: '미니 턴테이블 + LP 5장 · 판매 정산',
    referenceType: 'DEAL',
    referenceId: 498,
  },
  {
    id: 1003,
    walletType: 'TRADE',
    // 화면상 "출금"이지만 명세 유형은 REFUND다 — 「거래 포인트 환불」이 대응 API이고
    // 비고가 "실제 현금 환불이 아닌 프로젝트 내 포인트 차감"이다.
    transactionType: 'REFUND',
    availableChange: -50_000,
    heldChange: 0,
    availableBalanceAfter: 88_000,
    heldBalanceAfter: 0,
    createdAt: now - 6 * DAY,
    description: '국민은행 123456-78-901234 · 출금',
    referenceType: 'PAYMENT',
    referenceId: 9002,
  },
  {
    id: 1002,
    walletType: 'TRADE',
    transactionType: 'CHARGE',
    availableChange: 50_000,
    heldChange: 0,
    availableBalanceAfter: 138_000,
    heldBalanceAfter: 0,
    createdAt: now - 9 * DAY,
    description: '신용·체크카드 충전 · 테스트 결제',
    referenceType: 'PAYMENT',
    referenceId: 9003,
  },
  {
    id: 1001,
    walletType: 'TRADE',
    transactionType: 'CHARGE',
    availableChange: 88_000,
    heldChange: 0,
    availableBalanceAfter: 88_000,
    heldBalanceAfter: 0,
    createdAt: now - 21 * DAY,
    description: '카카오페이 충전 · 테스트 결제',
    referenceType: 'PAYMENT',
    referenceId: 9004,
  },
]

// ── 베팅 포인트 원장 — 최신순 ──
//
// 가입 축하·출석·시청 보상이 전부 `GRANT` + `REWARD`로 뭉친다. 배지로는 구분되지 않고
// **`description`이 유일한 구분**이다 — 명세가 문구 생성을 서버 책임으로 못박은 이유이기도 하다.
export const mockBettingLedger: LedgerEntry[] = [
  {
    id: 2009,
    walletType: 'BETTING',
    // 예측 스테이크는 BET으로 둔다. 명세가 8종의 뜻을 정의하지 않아 입찰 홀드(HOLD)와
    // 갈라 쓰는 게 맞는지는 단정할 수 없다 — 배지 문구에만 쓰이고 금액 계산과는 무관하다.
    transactionType: 'BET',
    availableChange: -300,
    heldChange: 300, // 총합 0
    availableBalanceAfter: 950,
    heldBalanceAfter: 300,
    createdAt: now - 48 * MINUTE,
    description: '아트토이 굿즈 컬렉션 · 낙찰가 예측 스테이크 보류',
    referenceType: 'PREDICTION',
    referenceId: 703,
  },
  {
    id: 2008,
    walletType: 'BETTING',
    transactionType: 'GRANT',
    availableChange: 50,
    heldChange: 0,
    availableBalanceAfter: 1_250,
    heldBalanceAfter: 0,
    createdAt: now - 5 * HOUR,
    description: '라이브 시청 보상',
    referenceType: 'REWARD',
    referenceId: null, // 직접 지급이라 연결 대상이 없다
  },
  {
    id: 2007,
    walletType: 'BETTING',
    transactionType: 'PAYOUT',
    availableChange: 900, // 보류 해제 + 배당이 한 행에 기록된 경우 (스테이크 300 × 3)
    heldChange: -300,
    availableBalanceAfter: 1_200,
    heldBalanceAfter: 0,
    createdAt: now - DAY - HOUR,
    description: '한정판 피규어 미개봉 · 오차 10% 이내 적중 ×3',
    referenceType: 'PREDICTION',
    referenceId: 702,
  },
  {
    id: 2006,
    walletType: 'BETTING',
    transactionType: 'BET',
    availableChange: -300,
    heldChange: 300,
    availableBalanceAfter: 300,
    heldBalanceAfter: 300,
    createdAt: now - DAY - 2 * HOUR,
    description: '한정판 피규어 미개봉 · 낙찰가 예측 스테이크 보류',
    referenceType: 'PREDICTION',
    referenceId: 702,
  },
  {
    id: 2005,
    walletType: 'BETTING',
    transactionType: 'GRANT',
    availableChange: 50,
    heldChange: 0,
    availableBalanceAfter: 600,
    heldBalanceAfter: 0,
    createdAt: now - 2 * DAY,
    description: '출석 보상',
    referenceType: 'REWARD',
    referenceId: null,
  },
  {
    id: 2004,
    walletType: 'BETTING',
    transactionType: 'SETTLEMENT',
    availableChange: 0,
    heldChange: -500, // 미적중 — 보류분이 그대로 소멸
    availableBalanceAfter: 550,
    heldBalanceAfter: 0,
    createdAt: now - 3 * DAY,
    description: '레트로 게임 콘솔 + 타이틀 · 미적중 ×0',
    referenceType: 'PREDICTION',
    referenceId: 701,
  },
  {
    id: 2003,
    walletType: 'BETTING',
    transactionType: 'BET',
    availableChange: -500,
    heldChange: 500,
    availableBalanceAfter: 550,
    heldBalanceAfter: 500,
    createdAt: now - 3 * DAY - 3 * HOUR,
    description: '레트로 게임 콘솔 + 타이틀 · 낙찰가 예측 스테이크 보류',
    referenceType: 'PREDICTION',
    referenceId: 701,
  },
  {
    id: 2002,
    walletType: 'BETTING',
    transactionType: 'GRANT',
    availableChange: 50,
    heldChange: 0,
    availableBalanceAfter: 1_050,
    heldBalanceAfter: 0,
    createdAt: now - 5 * DAY,
    description: '출석 보상',
    referenceType: 'REWARD',
    referenceId: null,
  },
  {
    id: 2001,
    walletType: 'BETTING',
    transactionType: 'GRANT',
    // 가입 축하 지급액은 1,000BP로 확정됐다 (S15P11A704-313). UI 카피(SignupView·LandingView)가 정본이고
    // 목 원장이 3,000으로 어긋나 있던 것을 맞췄다 — 이 배열은 최신순이라, 이 값을 바꾸면
    // **위쪽** 행들의 availableBalanceAfter와 mockWallets의 BETTING 잔액이 전부 함께 밀린다.
    availableChange: 1_000,
    heldChange: 0,
    availableBalanceAfter: 1_000,
    heldBalanceAfter: 0,
    createdAt: now - 16 * DAY,
    description: '가입 축하 지급',
    referenceType: 'REWARD',
    referenceId: null,
  },
]
