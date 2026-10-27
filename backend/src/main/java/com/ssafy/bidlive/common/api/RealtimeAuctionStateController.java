package com.ssafy.bidlive.common.api;

import static com.ssafy.bidlive.common.application.RealtimeAuctionStateQueryException.Reason.AUCTION_NOT_FOUND;

import com.ssafy.bidlive.common.api.dto.RealtimeAuctionStateResponse;
import com.ssafy.bidlive.common.application.RealtimeAuctionStateQueryException;
import com.ssafy.bidlive.common.application.RealtimeAuctionStateQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 최초 입장과 재접속에 사용할 서버 권위 realtime-state를 제공합니다. */
@RestController
@RequestMapping("/api/v1/auctions")
public class RealtimeAuctionStateController {

	private final RealtimeAuctionStateQueryService queryService;

	public RealtimeAuctionStateController(
		RealtimeAuctionStateQueryService queryService
	) {
		this.queryService = queryService;
	}

	@GetMapping("/{auctionId}/realtime-state")
	public RealtimeAuctionStateResponse getState(
		@PathVariable String auctionId,
		Authentication authentication
	) {
		return RealtimeAuctionStateResponse.from(queryService.getState(
			parseAuctionId(auctionId),
			viewerMemberId(authentication)
		));
	}

	private static long parseAuctionId(String value) {
		try {
			long auctionId = Long.parseLong(value);
			if (auctionId <= 0) {
				throw new NumberFormatException("auctionId must be positive");
			}
			return auctionId;
		} catch (NumberFormatException exception) {
			throw new RealtimeAuctionStateQueryException(
				AUCTION_NOT_FOUND,
				exception
			);
		}
	}

	private static Long viewerMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			return null;
		}
		try {
			long memberId = Long.parseLong(authentication.getName());
			return memberId > 0 ? memberId : null;
		} catch (NumberFormatException exception) {
			return null;
		}
	}
}
