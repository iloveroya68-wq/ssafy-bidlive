// PRED 낙찰가 예측 베팅 API (S15P11A704-59)
//
// 명세 근거
// - 베팅 접수:      POST /api/v1/auctions/{auctionId}/predictions        (PRED-01)
// - 결과 분포 조회: GET  /api/v1/auctions/{auctionId}/prediction-results (PRED-02, 종료 후에만)
//
// AGENTS.md 규칙 7: 금액 직접 입력 + 스테이크, 오차율 고정 배당(5% ×5/10% ×3/20% ×1.5, 미적중 ×0).
// "구간(bucket) 선택"·"실시간 분포"는 폐기된 구 설계다 —
// 명세도 진행 중 조회 시 `409 PREDICTION_RESULT_NOT_AVAILABLE`로 막는다.

import { http } from '@/shared/api/http'
import { newRequestId } from '@/shared/api/idempotency'
import { AUCTIONS_PATH } from '@/shared/api/bids'
import type { CursorPageQuery, Id, IsoDateTime } from '@/shared/api/types'

// ── 베팅 접수 (PRED-01) ──

export interface SubmitPredictionRequest {
  /** `prediction.predicted_price > 0` — 구간·상한·프리셋 없음 */
  predictedPrice: number
  /** `prediction.stake_amount > 0` — 베팅 포인트. 가용 잔액 이하여야 한다 */
  stakeAmount: number
  /**
   * 중복 요청 방지 키. 입찰(BID-06)은 헤더인데 예측은 **본문 필드**다 — 명세 그대로 유지한다.
   * 재시도 시 같은 값을 재사용해야 중복 베팅이 되지 않는다.
   */
  clientRequestId: string
}

/** 명세 예시에는 `PLACED`만 등장한다 */
export type PredictionStatus = string

export interface SubmitPredictionResponse {
  predictionId: Id
  auctionId: Id
  predictedPrice: number
  stakeAmount: number
  status: PredictionStatus
  submittedAt: IsoDateTime
}

// ── 결과 분포 (PRED-02) ──

/** 낙찰가 대비 오차율 밴드 */
export type PredictionBand = 'WITHIN_5' | 'WITHIN_10' | 'WITHIN_20' | 'MISS'

/** `prediction_result.reward_multiplier IN (0, 1, 1.5, 3, 5)` */
export type PredictionRewardMultiplier = 0 | 1 | 1.5 | 3 | 5

/**
 * 밴드별 고정 배수. 명세 「예측 고정 배당 정산」(PRED-03) 비고가 단일 소스다:
 * "5% 이내 ×5, 10% 이내 ×3, 20% 이내 ×1.5, 그 외 0"
 * — 정산 시점에 서버가 저장한 배수와 동일한 밴드 정책을 사용한다.
 */
export const PREDICTION_BAND_MULTIPLIER: Record<PredictionBand, PredictionRewardMultiplier> = {
  WITHIN_5: 5,
  WITHIN_10: 3,
  WITHIN_20: 1.5,
  MISS: 0,
}

export interface PredictionBandResult {
  band: PredictionBand
  participantCount: number
  multiplier: PredictionRewardMultiplier
  /** 해당 밴드 참여자에게 지급된 합계 */
  totalPayoutAmount: number
}

/** 명세 예시에는 `COMPLETED`만 등장한다. 유찰 전액 환불 케이스의 값은 미확인 */
export type PredictionSettlementStatus = string

export interface PredictionResultsResponse {
  auctionId: Id
  settlementStatus: PredictionSettlementStatus
  bands: PredictionBandResult[]
}

// ── 엔드포인트 ──

/**
 * PRED-01 예측 베팅 제출. **이 엔드포인트의 유일한 구현이다** (S15P11A704-395).
 * 방당 1회뿐이고, 같은 방에서 입찰한 사용자는 참여할 수 없다(배타).
 *
 * 화면 문장 매핑과 배타 잠금 판정은 `features/prediction/api.ts`가 갖는다.
 *
 * @param clientRequestId 재시도 시 같은 값을 넘긴다. 생략하면 새로 만든다(= 최초 시도).
 *   방당 1회 제약이 있어 **재시도에 키를 새로 만들면 "이미 베팅함" 거절로 바뀐다.**
 */
export async function submitPrediction(
  auctionId: Id,
  request: Omit<SubmitPredictionRequest, 'clientRequestId'>,
  clientRequestId: string = newRequestId(),
): Promise<SubmitPredictionResponse> {
  const body: SubmitPredictionRequest = { ...request, clientRequestId }

  // 경로는 baseURL(`/api/v1`) 기준 상대경로다 — `/v1`을 또 붙이면 `/api/v1/v1/...`가 된다(394).
  const { data } = await http.post<SubmitPredictionResponse>(
    `${AUCTIONS_PATH}/${auctionId}/predictions`,
    body,
  )
  return data
}

/**
 * PRED-02 오차 밴드별 결과 분포. **경매 종료 + 예측 정산 완료 후에만** 열린다.
 * 진행 중 호출은 409 `PREDICTION_RESULT_NOT_AVAILABLE`이며, 라이브 화면에서 부르면 안 된다.
 *
 * 목 분기는 없다 (S15P11A704-520) — 서버에 `PredictionResultController`가 붙었다.
 * 목 모드의 데모 밴드는 `features/result/resultSource.ts` 한 곳에만 있다.
 */
export async function getPredictionResults(auctionId: Id): Promise<PredictionResultsResponse> {
  const { data } = await http.get<PredictionResultsResponse>(
    `${AUCTIONS_PATH}/${auctionId}/prediction-results`,
  )
  return data
}

// ── PRED-05 일일 낙찰가 예측 리더보드 ──
//
// 명세 「일일 낙찰가 예측 리더보드 조회」: 그 날짜에 **정산된** 예측만 집계하고
// `payoutRate = totalPayoutPoints ÷ totalStakedPoints × 100`으로 순위를 매긴다.
// 진행 중·취소·유찰 환불 건과 스테이크 0인 사용자는 제외된다 — 순위 계산은 전적으로 서버 몫이다.
//
// ⚠️ **기본 조회 날짜는 오늘이 아니라 어제다**(Asia/Seoul). 화면 문구도 전부 어제 기준이어야 한다.
// 비고: 홈은 `size=10`, 마이페이지는 커서 페이지로 쓴다(기본 10, 최대 100).

export const LEADERBOARD_PATH = '/predictions/leaderboards/daily'

export interface LeaderboardEntry {
  rank: number
  nickname: string
  /** 그날 정산이 끝난 예측 건수 */
  settledPredictionCount: number
  totalStakedPoints: number
  totalPayoutPoints: number
  /** 이미 100을 곱한 수익률(%). 400.0이면 스테이크의 4배를 받았다는 뜻 */
  payoutRate: number
}

export interface LeaderboardQuery extends CursorPageQuery {
  /**
   * `YYYY-MM-DD`. **생략하면 Asia/Seoul 기준 어제다** — 오늘이 아니다.
   * 오늘·미래 날짜는 `400 INVALID_RANKING_DATE`로 막힌다(전날 집계가 끝난 스냅샷만 연다).
   */
  date?: string
}

export interface DailyLeaderboardResponse {
  rankingDate: string
  /** 빈 날짜(집계 스냅샷 없음)면 null */
  calculatedAt: IsoDateTime | null
  items: LeaderboardEntry[]
  /** 내가 집계 대상이 아니면 null (그날 정산된 예측이 없거나 스테이크가 0) */
  myRank: LeaderboardEntry | null
  nextCursor: string | null
  hasNext: boolean
}

/**
 * PRED-05 일일 리더보드 — 순위·수익률 계산은 서버가 끝낸 값을 그대로 쓴다.
 *
 * 목 분기는 없다 (S15P11A704-547) — 서버에 리더보드 컨트롤러가 붙었다.
 * 인증이 필요한 엔드포인트라 비로그인 홈 티커는 401을 받는데, 티커가 조용히 접어서 처리한다.
 */
export async function getDailyLeaderboard(
  query: LeaderboardQuery = {},
): Promise<DailyLeaderboardResponse> {
  const { data } = await http.get<DailyLeaderboardResponse>(LEADERBOARD_PATH, { params: query })
  return data
}

// ── PRED-04 예측 업적(등급·뱃지) ──
//
// 명세 「내 예측 성과 조회」: 인증 필수, 쿼리·페이지네이션 없음.
// 집계 대상은 **정산이 끝난** 예측뿐이라 진행 중 베팅은 어디에도 안 잡힌다.

export const PREDICTION_ACHIEVEMENTS_PATH = '/members/me/prediction-achievements'

export interface PredictionGrade {
  /**
   * `ORACLE_1`~`ORACLE_5`. **화면은 이 값을 쓰지 않는다** — 표시는 항상 `name`이다.
   * 코드→라벨 맵을 프론트에 두면 등급 이름이 서버와 두 벌이 되고, 서버가 문구를 고쳐도 화면은 옛말을 한다.
   */
  code: string
  /** 서버가 내려주는 표시명(「시세 예언자 I」 등) */
  name: string
  /** 등급 산정 점수. 이력이 없으면 `ORACLE_1` / 0이다 */
  score: number
}

export interface PredictionBadge {
  badgeCode: string
  /** 표시명도 서버 값이다 — 등급과 같은 이유로 프론트에 맵을 두지 않는다 */
  name: string
  earnedAt: IsoDateTime
}

export interface PredictionAchievementsResponse {
  memberId: Id
  /** 정산된 예측 건수 */
  predictionCount: number
  hitCount: number
  /** 적중률(%) — 이미 100을 곱한 값이다. 소수 첫째 자리, 정산 이력이 없으면 0.0 */
  hitRate: number
  totalStakedPoints: number
  totalRewardPoints: number
  grade: PredictionGrade
  /** 아직 못 받았으면 빈 배열이다 (null이 아니다) */
  badges: PredictionBadge[]
}

/**
 * PRED-04 내 예측 업적 — 등급·뱃지·누적 통계 모두 서버가 끝낸 값이다.
 *
 * 오류는 401 `UNAUTHORIZED`, 404 `MEMBER_NOT_FOUND` 둘뿐이라 별도 문장 맵이 필요 없다
 * (`shared/api/errors.ts`가 이미 둘 다 갖고 있다).
 */
export async function getMyPredictionAchievements(): Promise<PredictionAchievementsResponse> {
  const { data } = await http.get<PredictionAchievementsResponse>(PREDICTION_ACHIEVEMENTS_PATH)
  return data
}
