package com.ssafy.bidlive.prediction.api;

import com.ssafy.bidlive.prediction.api.dto.PredictionResultResponse;
import com.ssafy.bidlive.prediction.api.dto.PredictionResultResponse.BandResult;
import com.ssafy.bidlive.prediction.application.PredictionResultQueryResult;
import com.ssafy.bidlive.prediction.application.PredictionResultQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 정산 완료된 경매의 예측 결과 분포를 공개 조회 API로 반환한다. */
@RestController
@RequestMapping("/api/v1/auctions")
public class PredictionResultController {

	private static final String COMPLETED_STATUS = "COMPLETED";

	private final PredictionResultQueryService queryService;

	public PredictionResultController(
		PredictionResultQueryService queryService
	) {
		this.queryService = queryService;
	}

	@GetMapping("/{auctionId}/prediction-results")
	public ResponseEntity<PredictionResultResponse> getPredictionResults(
		@PathVariable Long auctionId
	) {
		PredictionResultQueryResult result =
			queryService.getPredictionResult(auctionId);
		return ResponseEntity.ok(new PredictionResultResponse(
			result.auctionId(),
			COMPLETED_STATUS,
			result.bands().stream()
				.map(band -> new BandResult(
					band.band().name(),
					band.participantCount(),
					band.multiplier(),
					band.totalPayoutAmount()
				))
				.toList()
		));
	}
}
