package com.ssafy.bidlive.prediction.application;

import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.prediction.domain.PredictionSettlementDecision;

/** PRED-03 정산 대상 조회와 예측 결과의 멱등 저장을 제공하는 영속성 Port다. */
public interface PredictionSettlementPersistence {

	List<SettlementTarget> findUnsettledTargets(Long auctionId);

	Optional<StoredSettlement> findByPredictionId(Long predictionId);

	SaveResult saveIfAbsent(NewSettlement settlement);

	record SettlementTarget(
		Long predictionId,
		Long memberId,
		long predictedPrice,
		long stakeAmount
	) {
	}

	record NewSettlement(
		Long predictionId,
		Long auctionResultId,
		PredictionSettlementDecision decision
	) {

		public NewSettlement {
			requirePositive(predictionId, "predictionId");
			requirePositive(auctionResultId, "auctionResultId");
			if (decision == null) {
				throw new IllegalArgumentException(
					"decision must not be null"
				);
			}
		}

		private static void requirePositive(Long value, String name) {
			if (value == null || value <= 0) {
				throw new IllegalArgumentException(name + " must be positive");
			}
		}
	}

	record StoredSettlement(
		Long predictionResultId,
		Long predictionId,
		Long auctionResultId,
		PredictionSettlementDecision decision
	) {
	}

	record SaveResult(
		StoredSettlement settlement,
		boolean created
	) {
	}
}
