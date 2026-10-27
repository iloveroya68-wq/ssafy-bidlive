package com.ssafy.bidlive.prediction.application;

import java.util.List;

import com.ssafy.bidlive.prediction.application.PredictionResultAggregationPersistence.BandAggregate;
import com.ssafy.bidlive.prediction.domain.PredictionSettlementState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 정산 완료 여부를 확인한 뒤 공개 가능한 예측 결과 분포를 조회한다. */
@Service
public class PredictionResultQueryService {

	private final PredictionResultAvailabilityService availabilityService;
	private final PredictionResultAggregationPersistence aggregationPersistence;

	public PredictionResultQueryService(
		PredictionResultAvailabilityService availabilityService,
		PredictionResultAggregationPersistence aggregationPersistence
	) {
		this.availabilityService = availabilityService;
		this.aggregationPersistence = aggregationPersistence;
	}

	@Transactional(readOnly = true)
	public PredictionResultQueryResult getPredictionResult(Long auctionId) {
		PredictionSettlementState state =
			availabilityService.validateCompleted(auctionId);
		List<BandAggregate> bands = aggregationPersistence
			.findBandAggregatesByAuctionResultId(state.auctionResultId());
		return new PredictionResultQueryResult(auctionId, bands);
	}
}
