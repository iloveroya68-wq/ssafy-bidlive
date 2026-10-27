package com.ssafy.bidlive.auction.api;

import com.ssafy.bidlive.auction.api.dto.AuctionRoomJoinResponse;
import com.ssafy.bidlive.auction.application.AuctionRoomJoinException;
import com.ssafy.bidlive.auction.application.AuctionRoomJoinService;
import com.ssafy.bidlive.auction.domain.AuctionRoomJoinErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 회원이 경매방에 입장하고 상태별 권한과 구독 destination을 받는다 (ROOM-07). 요청 본문은 없다. */
@RestController
@RequestMapping("/api/v1/auction-rooms")
public class AuctionRoomJoinController {

	private final AuctionRoomJoinService joinService;

	public AuctionRoomJoinController(AuctionRoomJoinService joinService) {
		this.joinService = joinService;
	}

	@PostMapping("/{auctionId}/join")
	public AuctionRoomJoinResponse joinRoom(
		@PathVariable long auctionId,
		Authentication authentication
	) {
		return AuctionRoomJoinResponse.from(
			joinService.joinRoom(auctionId, authenticatedMemberId(authentication))
		);
	}

	private static Long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new AuctionRoomJoinException(AuctionRoomJoinErrorCode.UNAUTHORIZED);
		}
		try {
			long memberId = Long.parseLong(authentication.getName());
			if (memberId <= 0) {
				throw new AuctionRoomJoinException(AuctionRoomJoinErrorCode.UNAUTHORIZED);
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new AuctionRoomJoinException(AuctionRoomJoinErrorCode.UNAUTHORIZED);
		}
	}
}
