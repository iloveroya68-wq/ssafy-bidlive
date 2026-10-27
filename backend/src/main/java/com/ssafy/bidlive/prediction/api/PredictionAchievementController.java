package com.ssafy.bidlive.prediction.api;

import com.ssafy.bidlive.prediction.api.dto.PredictionAchievementResponse;
import com.ssafy.bidlive.prediction.application.PredictionAchievementException;
import com.ssafy.bidlive.prediction.application.PredictionAchievementQueryService;
import com.ssafy.bidlive.prediction.domain.PredictionAchievementErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 회원의 정산된 예측 통계와 명예 등급·뱃지를 반환한다. */
@RestController
@RequestMapping("/api/v1/members/me/prediction-achievements")
public class PredictionAchievementController {

	private final PredictionAchievementQueryService queryService;

	public PredictionAchievementController(PredictionAchievementQueryService queryService) {
		this.queryService = queryService;
	}

	@GetMapping
	public ResponseEntity<PredictionAchievementResponse> getPredictionAchievements(
		Authentication authentication
	) {
		return ResponseEntity.ok(PredictionAchievementResponse.from(
			queryService.getPredictionAchievements(
				authenticatedMemberId(authentication)
			)
		));
	}

	private Long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new PredictionAchievementException(
				PredictionAchievementErrorCode.UNAUTHORIZED
			);
		}

		try {
			Long memberId = Long.valueOf(authentication.getName());
			if (memberId <= 0) {
				throw new NumberFormatException("member ID must be positive");
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new PredictionAchievementException(
				PredictionAchievementErrorCode.UNAUTHORIZED,
				exception
			);
		}
	}
}
