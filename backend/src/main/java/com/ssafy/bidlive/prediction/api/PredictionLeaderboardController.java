package com.ssafy.bidlive.prediction.api;

import com.ssafy.bidlive.prediction.api.dto.PredictionLeaderboardRequest;
import com.ssafy.bidlive.prediction.api.dto.PredictionLeaderboardResponse;
import com.ssafy.bidlive.prediction.application.PredictionLeaderboardQueryException;
import com.ssafy.bidlive.prediction.application.PredictionLeaderboardQueryService;
import com.ssafy.bidlive.prediction.domain.PredictionLeaderboardQueryErrorCode;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 회원에게 일일 예측 리더보드와 본인 순위를 반환한다. */
@RestController
@RequestMapping("/api/v1/predictions/leaderboards")
public class PredictionLeaderboardController {

	private final PredictionLeaderboardQueryService queryService;

	public PredictionLeaderboardController(
		PredictionLeaderboardQueryService queryService
	) {
		this.queryService = queryService;
	}

	@GetMapping("/daily")
	public ResponseEntity<PredictionLeaderboardResponse> getDailyLeaderboard(
		@Valid @ModelAttribute PredictionLeaderboardRequest request,
		Authentication authentication
	) {
		return ResponseEntity.ok(PredictionLeaderboardResponse.from(
			queryService.query(
				request.date(),
				request.size(),
				request.cursor(),
				authenticatedMemberId(authentication)
			)
		));
	}

	private long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw unauthorized();
		}
		try {
			long memberId = Long.parseLong(authentication.getName());
			if (memberId <= 0) {
				throw new NumberFormatException("member ID must be positive");
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new PredictionLeaderboardQueryException(
				PredictionLeaderboardQueryErrorCode.UNAUTHORIZED,
				exception
			);
		}
	}

	private PredictionLeaderboardQueryException unauthorized() {
		return new PredictionLeaderboardQueryException(
			PredictionLeaderboardQueryErrorCode.UNAUTHORIZED
		);
	}
}
