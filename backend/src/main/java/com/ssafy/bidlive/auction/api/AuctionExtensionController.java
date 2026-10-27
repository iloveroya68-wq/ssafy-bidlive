package com.ssafy.bidlive.auction.api;

import com.ssafy.bidlive.auction.api.dto.AuctionExtensionResponse;
import com.ssafy.bidlive.auction.application.AuctionExtensionException;
import com.ssafy.bidlive.auction.application.AuctionExtensionService;
import com.ssafy.bidlive.auction.domain.AuctionExtensionErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 판매자의 요청을 받아 진행 중인 경매 마감 시각을 5분 연장한다. */
@RestController
@RequestMapping("/api/v1/auctions")
public class AuctionExtensionController {

	private final AuctionExtensionService extensionService;

	public AuctionExtensionController(AuctionExtensionService extensionService) {
		this.extensionService = extensionService;
	}

	@PostMapping("/{auctionId}/extensions")
	public AuctionExtensionResponse extend(
		@PathVariable long auctionId,
		Authentication authentication
	) {
		return AuctionExtensionResponse.from(extensionService.extend(
			auctionId,
			authenticatedMemberId(authentication)
		));
	}

	private static long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new AuctionExtensionException(AuctionExtensionErrorCode.UNAUTHORIZED);
		}
		try {
			long memberId = Long.parseLong(authentication.getName());
			if (memberId <= 0) {
				throw new AuctionExtensionException(AuctionExtensionErrorCode.UNAUTHORIZED);
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new AuctionExtensionException(AuctionExtensionErrorCode.UNAUTHORIZED);
		}
	}
}
