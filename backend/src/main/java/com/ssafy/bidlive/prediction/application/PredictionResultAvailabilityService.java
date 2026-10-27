package com.ssafy.bidlive.prediction.application;

import static com.ssafy.bidlive.prediction.domain.PredictionResultQueryErrorCode.AUCTION_NOT_FOUND;
import static com.ssafy.bidlive.prediction.domain.PredictionResultQueryErrorCode.INVALID_AUCTION_ID;
import static com.ssafy.bidlive.prediction.domain.PredictionResultQueryErrorCode.PREDICTION_RESULT_NOT_AVAILABLE;

import com.ssafy.bidlive.prediction.domain.PredictionSettlementState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 종료 경매의 모든 예측이 정산됐는지 검증해 PRED-02 조회를 보호한다. */
@Service
public class PredictionResultAvailabilityService {

	private final PredictionResultAvailabilityPersistence persistence;

	public PredictionResultAvailabilityService(
		PredictionResultAvailabilityPersistence persistence
	) {
		this.persistence = persistence;
	}

	@Transactional(readOnly = true)
	public PredictionSettlementState validateCompleted(Long auctionId) {
		if (auctionId == null || auctionId <= 0) {
			throw new PredictionResultQueryException(INVALID_AUCTION_ID);
		}

		PredictionSettlementState state = persistence
			.findStateByAuctionId(auctionId)
			.orElseThrow(() -> new PredictionResultQueryException(
				AUCTION_NOT_FOUND
			));
		if (!state.completed()) {
			throw new PredictionResultQueryException(
				PREDICTION_RESULT_NOT_AVAILABLE
			);
		}
		return state;
	}
}
