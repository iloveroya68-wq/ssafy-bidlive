import { defineStore } from 'pinia'
import { submitPrediction, type SubmitPredictionResponse } from '@/shared/api/predictions'
import { newRequestId } from '@/shared/api/idempotency'

// 오너: FE1 (PRED-01~05) — 낙찰가 예측 베팅
// 구간(bucket) 파리뮤추얼은 폐기된 구 설계다. 현행은 금액 직접 입력 + 오차율 고정 배당.

/** 낙찰가 대비 오차율 밴드 (PRED-03) */
export type PredictionBand = 'WITHIN_5' | 'WITHIN_10' | 'WITHIN_20' | 'MISS'

/**
 * 밴드별 고정 배수 — 베팅 시점에 확정된다.
 * 명세 「예측 고정 배당 정산」(PRED-03, 상태: 확정):
 *   errorRate = abs(predictedPrice - finalPrice) / finalPrice × 100
 *   5% 이내 ×5 / 10% 이내 ×3 / 20% 이내 ×1.5 / 그 외 0
 */
export const BAND_MULTIPLIER: Record<PredictionBand, number> = {
  WITHIN_5: 5,
  WITHIN_10: 3,
  WITHIN_20: 1.5,
  MISS: 0,
}

/** 배당 스트립에 그리는 순서 — 미적중(×0)은 안내 대상이 아니라 제외한다 */
export const PAYOUT_BANDS = [
  { band: 'WITHIN_5' as const, label: '오차 5%' },
  { band: 'WITHIN_10' as const, label: '10%' },
  { band: 'WITHIN_20' as const, label: '20%' },
]

/** PRED-02 결과 분포 — 경매 종료·정산 완료 후에만 채워진다 (진행 중 조회 시 409) */
export interface BandResult {
  band: PredictionBand
  participantCount: number
  multiplier: number
  totalPayoutAmount: number
}

interface PredictionState {
  myPrediction: number | null // predictedPrice — 금액 직접 입력, 구간·상한 없음 (PRED-01)
  myStake: number // stakeAmount — 베팅 포인트
  /**
   * 이 방에서 예측 베팅을 이미 제출했는지.
   * `prediction.auction_participant_id`가 UNIQUE라 **방당 1회뿐**이고 수정·취소가 없다
   * (docs/04 §3.1). true가 되면 폼을 확정 표시로 바꾼다.
   */
  submitted: boolean
  submitting: boolean
  /**
   * PRED-01 `clientRequestId` — 제출 시 만들고 **성공할 때까지 유지**한다.
   * 재시도마다 새로 만들면 네트워크 재시도가 중복 베팅이 된다.
   */
  pendingRequestId: string | null
  bands: BandResult[] // 종료 후에만 공개. 라이브 중 분포 노출 금지 (herding 방지)
}

export const usePredictionStore = defineStore('prediction', {
  state: (): PredictionState => ({
    myPrediction: null,
    myStake: 0,
    submitted: false,
    submitting: false,
    pendingRequestId: null,
    bands: [],
  }),
  getters: {
    // 베팅 전 실수령 BP 미리보기용. 정산 자체는 서버가 한다 (PRED-03).
    estimatedPayout: (state) => (band: PredictionBand) =>
      Math.floor(state.myStake * BAND_MULTIPLIER[band]),
  },
  actions: {
    /**
     * PRED-01 예측 베팅 접수. 실패하면 그대로 throw하고 `submitted`를 올리지 않는다 —
     * 화면이 서버 문구를 그려야 하고, 잠금은 성공했을 때만 걸려야 한다.
     */
    async placePrediction(
      auctionId: number,
      predictedPrice: number,
      stakeAmount: number,
    ): Promise<SubmitPredictionResponse> {
      if (this.submitted) throw new Error('이 방에서는 이미 예측 베팅을 했어요')

      // 폴백이 있는 계약 레이어 헬퍼를 쓴다 — `crypto.randomUUID`는 보안 컨텍스트 전용이다
      this.pendingRequestId ??= newRequestId()
      this.submitting = true
      try {
        // `clientRequestId`는 본문 필드지만 계약 레이어가 조립한다 — 여기서는 키만 넘긴다.
        // 방당 1회 제약이라 재시도에 키를 새로 만들면 "이미 베팅함" 거절이 된다.
        const result = await submitPrediction(
          auctionId,
          { predictedPrice, stakeAmount },
          this.pendingRequestId,
        )
        this.myPrediction = result.predictedPrice
        this.myStake = result.stakeAmount
        this.submitted = true
        this.pendingRequestId = null
        return result
      } finally {
        this.submitting = false
      }
    },

    /**
     * 재접속 복원 (WS-03).
     * 스냅샷의 `myParticipationType === 'PREDICTION'`이면 이 방의 예측은 확정된 상태다.
     *
     * 배선 완료 (S15P11A704-388): `RealtimeState` → `handlers.applySnapshot` →
     * `live.myPredictedPrice`·`myStakeAmount` → `LiveView.vue`의 watch가 인자로 넘긴다.
     * 예측 미참여면 서버가 null을 주므로 입찰자·미참여자는 영향받지 않는다.
     *
     * 기본값을 남겨 둔 이유는 호출부가 값을 모르는 경우(테스트·목)에도 잠금만 복원할 수 있게
     * 하기 위해서다. 신규 제출 직후에는 호출되지 않는다 — `LiveView.vue`의 `!prediction.submitted`
     * 가드가 방금 제출값을 기본값으로 덮는 것을 막는다.
     */
    restoreSubmitted(predictedPrice: number | null = null, stakeAmount = 0) {
      this.submitted = true
      this.myPrediction = predictedPrice
      this.myStake = stakeAmount
      this.pendingRequestId = null
    },
  },
})
