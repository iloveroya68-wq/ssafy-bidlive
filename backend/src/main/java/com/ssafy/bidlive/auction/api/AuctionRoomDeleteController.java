package com.ssafy.bidlive.auction.api;

import com.ssafy.bidlive.auction.application.AuctionRoomDeleteException;
import com.ssafy.bidlive.auction.application.AuctionRoomDeleteService;
import com.ssafy.bidlive.auction.domain.AuctionRoomDeleteErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 판매자가 방송 시작 전 경매방을 삭제한다. */
@RestController
@RequestMapping("/api/v1/auction-rooms")
public class AuctionRoomDeleteController {

	private final AuctionRoomDeleteService deleteService;

	public AuctionRoomDeleteController(AuctionRoomDeleteService deleteService) {
		this.deleteService = deleteService;
	}

	@DeleteMapping("/{auctionId}")
	public ResponseEntity<Void> deleteRoom(
		@PathVariable long auctionId,
		Authentication authentication
	) {
		deleteService.deleteRoom(auctionId, authenticatedMemberId(authentication));
		return ResponseEntity.noContent().build();
	}

	private static Long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new AuctionRoomDeleteException(AuctionRoomDeleteErrorCode.UNAUTHORIZED);
		}
		try {
			long memberId = Long.parseLong(authentication.getName());
			if (memberId <= 0) {
				throw new AuctionRoomDeleteException(AuctionRoomDeleteErrorCode.UNAUTHORIZED);
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new AuctionRoomDeleteException(AuctionRoomDeleteErrorCode.UNAUTHORIZED);
		}
	}
}
