package com.ssafy.bidlive.member.api;

import com.ssafy.bidlive.member.api.dto.ParticipatedAuctionQueryResponse;
import com.ssafy.bidlive.member.application.ParticipatedAuctionQueryException;
import com.ssafy.bidlive.member.application.ParticipatedAuctionQueryService;
import com.ssafy.bidlive.member.domain.ParticipatedAuctionQueryErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 인증 회원이 입찰 또는 예측으로 참여한 경매 이력을 조회한다. */
@RestController
@RequestMapping("/api/v1/members/me/auctions/participated")
public class ParticipatedAuctionQueryController {

	private final ParticipatedAuctionQueryService queryService;

	public ParticipatedAuctionQueryController(
		ParticipatedAuctionQueryService queryService
	) {
		this.queryService = queryService;
	}

	@GetMapping
	public ParticipatedAuctionQueryResponse query(
		Authentication authentication,
		@RequestParam(required = false) String cursor,
		@RequestParam(required = false) Integer size
	) {
		return ParticipatedAuctionQueryResponse.from(
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
			throw new ParticipatedAuctionQueryException(
				ParticipatedAuctionQueryErrorCode.UNAUTHORIZED,
				exception
			);
		}
	}

	private ParticipatedAuctionQueryException unauthorized() {
		return new ParticipatedAuctionQueryException(
			ParticipatedAuctionQueryErrorCode.UNAUTHORIZED
		);
	}
}
