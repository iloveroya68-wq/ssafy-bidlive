package com.ssafy.bidlive.auction.api;

import com.ssafy.bidlive.auction.api.dto.AuctionRoomStartResponse;
import com.ssafy.bidlive.auction.application.AuctionRoomStartException;
import com.ssafy.bidlive.auction.application.AuctionRoomStartService;
import com.ssafy.bidlive.auction.domain.AuctionRoomStartErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 판매자가 대기 중인 경매방의 방송을 시작한다 (ROOM-05 / LIVE-02). 요청 본문은 없다. */
@RestController
@RequestMapping("/api/v1/auction-rooms")
public class AuctionRoomStartController {

	private final AuctionRoomStartService startService;

	public AuctionRoomStartController(AuctionRoomStartService startService) {
		this.startService = startService;
	}

	@PostMapping("/{auctionId}/start")
	public AuctionRoomStartResponse startRoom(
		@PathVariable long auctionId,
		Authentication authentication
	) {
		return AuctionRoomStartResponse.from(
			startService.startRoom(auctionId, authenticatedMemberId(authentication))
		);
	}

	private static Long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new AuctionRoomStartException(AuctionRoomStartErrorCode.UNAUTHORIZED);
		}
		try {
			long memberId = Long.parseLong(authentication.getName());
			if (memberId <= 0) {
				throw new AuctionRoomStartException(AuctionRoomStartErrorCode.UNAUTHORIZED);
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new AuctionRoomStartException(AuctionRoomStartErrorCode.UNAUTHORIZED);
		}
	}
}
