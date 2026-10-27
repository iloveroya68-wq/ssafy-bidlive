package com.ssafy.bidlive.member.api;

import com.ssafy.bidlive.member.api.dto.WonAuctionQueryResponse;
import com.ssafy.bidlive.member.application.WonAuctionQueryException;
import com.ssafy.bidlive.member.application.WonAuctionQueryService;
import com.ssafy.bidlive.member.domain.WonAuctionQueryErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 인증 회원이 낙찰자로 확정된 경매 이력을 커서 기반으로 조회한다. */
@RestController
@RequestMapping("/api/v1/members/me/auctions/won")
public class WonAuctionQueryController {

	private final WonAuctionQueryService queryService;

	public WonAuctionQueryController(WonAuctionQueryService queryService) {
		this.queryService = queryService;
	}

	@GetMapping
	public WonAuctionQueryResponse query(
		Authentication authentication,
		@RequestParam(required = false) String cursor,
		@RequestParam(required = false) Integer size
	) {
		return WonAuctionQueryResponse.from(
			queryService.query(
				authenticatedMemberId(authentication),
				cursor,
				size
			)
		);
	}

	private Long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw unauthorized();
		}
		try {
			Long memberId = Long.valueOf(authentication.getName());
			if (memberId <= 0) {
				throw new NumberFormatException("member ID must be positive");
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new WonAuctionQueryException(
				WonAuctionQueryErrorCode.UNAUTHORIZED,
				exception
			);
		}
	}

	private WonAuctionQueryException unauthorized() {
		return new WonAuctionQueryException(
			WonAuctionQueryErrorCode.UNAUTHORIZED
		);
	}
}
