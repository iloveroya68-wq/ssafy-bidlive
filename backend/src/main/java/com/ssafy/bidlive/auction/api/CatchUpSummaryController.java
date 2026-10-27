package com.ssafy.bidlive.auction.api;

import com.ssafy.bidlive.auction.api.dto.CatchUpSummaryResponse;
import com.ssafy.bidlive.auction.application.CatchUpSummaryException;
import com.ssafy.bidlive.auction.application.CatchUpSummaryService;
import com.ssafy.bidlive.auction.domain.CatchUpSummaryErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 방송 중간 입장자에게 Redis에 캐시된 AI-03 요약을 반환한다. */
@RestController
@RequestMapping("/api/v1/auction-rooms")
public class CatchUpSummaryController {

	private final CatchUpSummaryService service;

	public CatchUpSummaryController(CatchUpSummaryService service) {
		this.service = service;
	}

	@GetMapping("/{auctionId}/catch-up-summary")
	public ResponseEntity<CatchUpSummaryResponse> getSummary(
		@PathVariable long auctionId,
		Authentication authentication
	) {
		return service.getSummary(auctionId, memberId(authentication))
			.map(CatchUpSummaryResponse::from)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.noContent().build());
	}

	static Long memberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new CatchUpSummaryException(CatchUpSummaryErrorCode.UNAUTHORIZED);
		}
		try {
			long memberId = Long.parseLong(authentication.getName());
			if (memberId <= 0) {
				throw new NumberFormatException("memberId must be positive");
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new CatchUpSummaryException(
				CatchUpSummaryErrorCode.UNAUTHORIZED,
				exception
			);
		}
	}
}
