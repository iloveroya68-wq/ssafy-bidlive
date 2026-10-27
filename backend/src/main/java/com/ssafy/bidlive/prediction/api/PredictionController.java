package com.ssafy.bidlive.prediction.api;

import com.ssafy.bidlive.prediction.api.dto.PredictionCreateRequest;
import com.ssafy.bidlive.prediction.api.dto.PredictionResponse;
import com.ssafy.bidlive.prediction.application.PredictionCommandService;
import com.ssafy.bidlive.prediction.application.PredictionCreateResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 회원의 낙찰가 예측을 접수하고 저장된 개인 결과를 반환한다. */
@RestController
@RequestMapping("/api/v1/auctions")
public class PredictionController {

	private static final String PLACED_STATUS = "PLACED";

	private final PredictionCommandService commandService;

	public PredictionController(PredictionCommandService commandService) {
		this.commandService = commandService;
	}

	@PostMapping("/{auctionId}/predictions")
	public ResponseEntity<PredictionResponse> createPrediction(
		@PathVariable Long auctionId,
		@Valid @RequestBody PredictionCreateRequest request,
		Authentication authentication
	) {
		PredictionCreateResult result = commandService.createPrediction(
			auctionId,
			authenticatedMemberId(authentication),
			request.predictedPrice(),
			request.stakeAmount(),
			request.clientRequestId()
		);
		return ResponseEntity.status(201).body(new PredictionResponse(
			result.predictionId(),
			result.auctionId(),
			result.predictedPrice(),
			result.stakeAmount(),
			PLACED_STATUS,
			result.submittedAt()
		));
	}

	private Long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new PredictionUnauthenticatedException();
		}
		try {
			Long memberId = Long.valueOf(authentication.getName());
			if (memberId <= 0) {
				throw new NumberFormatException(
					"member ID must be positive"
				);
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new PredictionUnauthenticatedException(exception);
		}
	}
}
