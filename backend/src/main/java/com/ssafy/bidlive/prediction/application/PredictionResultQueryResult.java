package com.ssafy.bidlive.prediction.application;

import java.util.List;

import com.ssafy.bidlive.prediction.application.PredictionResultAggregationPersistence.BandAggregate;

/** PRED-02 조회에 필요한 경매 식별자와 정산 밴드 집계를 전달한다. */
public record PredictionResultQueryResult(
	Long auctionId,
	List<BandAggregate> bands
) {

	public PredictionResultQueryResult {
		bands = List.copyOf(bands);
	}
}
