package com.ssafy.bidlive.prediction.application;

import java.util.Optional;

import com.ssafy.bidlive.prediction.domain.PredictionSettlementState;

/** PRED-02 결과 공개 여부 판단에 필요한 경매별 정산 상태를 조회한다. */
public interface PredictionResultAvailabilityPersistence {

	Optional<PredictionSettlementState> findStateByAuctionId(Long auctionId);
}
