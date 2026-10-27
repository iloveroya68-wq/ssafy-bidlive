package com.ssafy.bidlive.member.api;

import com.ssafy.bidlive.member.api.dto.HostedAuctionQueryResponse;
import com.ssafy.bidlive.member.application.HostedAuctionQueryException;
import com.ssafy.bidlive.member.application.HostedAuctionQueryService;
import com.ssafy.bidlive.member.domain.HostedAuctionQueryErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 인증 회원이 판매자로 개최한 경매 이력을 커서 기반으로 조회한다. */
@RestController
@RequestMapping("/api/v1/members/me/auctions/hosted")
public class HostedAuctionQueryController {

	private final HostedAuctionQueryService queryService;

	public HostedAuctionQueryController(HostedAuctionQueryService queryService) {
		this.queryService = queryService;
	}

	@GetMapping
	public HostedAuctionQueryResponse query(
		Authentication authentication,
		@RequestParam(required = false) String cursor,
		@RequestParam(required = false) Integer size
	) {
		return HostedAuctionQueryResponse.from(
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
			throw new HostedAuctionQueryException(
				HostedAuctionQueryErrorCode.UNAUTHORIZED,
				exception
			);
		}
	}

	private HostedAuctionQueryException unauthorized() {
		return new HostedAuctionQueryException(
			HostedAuctionQueryErrorCode.UNAUTHORIZED
		);
	}
}
