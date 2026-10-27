package com.ssafy.bidlive.auction.api;

import static com.ssafy.bidlive.auction.domain.AuctionRoomDetailErrorCode.INVALID_AUCTION_ID;

import com.ssafy.bidlive.auction.api.dto.AuctionRoomDetailResponse;
import com.ssafy.bidlive.auction.application.AuctionRoomDetailException;
import com.ssafy.bidlive.auction.application.AuctionRoomDetailQueryService;
import com.ssafy.bidlive.auction.application.AuctionRoomViewerContext;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 공개 경매방 상세와 선택 인증 회원에게 허용된 추가 정보를 반환합니다. */
@RestController
@RequestMapping("/api/v1/auction-rooms")
public class AuctionRoomDetailController {

	private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

	private final AuctionRoomDetailQueryService detailQueryService;

	public AuctionRoomDetailController(
		AuctionRoomDetailQueryService detailQueryService
	) {
		this.detailQueryService = detailQueryService;
	}

	@GetMapping("/{auctionId}")
	public AuctionRoomDetailResponse getRoom(
		@PathVariable String auctionId,
		Authentication authentication
	) {
		return AuctionRoomDetailResponse.from(
			detailQueryService.getRoom(
				parseAuctionId(auctionId),
				viewer(authentication)
			)
		);
	}

	private static long parseAuctionId(String value) {
		try {
			long auctionId = Long.parseLong(value);
			if (auctionId <= 0) {
				throw new NumberFormatException("auctionId must be positive");
			}
			return auctionId;
		} catch (NumberFormatException exception) {
			throw new AuctionRoomDetailException(INVALID_AUCTION_ID, exception);
		}
	}

	private static AuctionRoomViewerContext viewer(
		Authentication authentication
	) {
		if (authentication == null || !authentication.isAuthenticated()) {
			return AuctionRoomViewerContext.anonymous();
		}
		try {
			long memberId = Long.parseLong(authentication.getName());
			boolean admin = authentication.getAuthorities().stream()
				.anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
			return AuctionRoomViewerContext.authenticated(memberId, admin);
		} catch (IllegalArgumentException exception) {
			return AuctionRoomViewerContext.anonymous();
		}
	}
}
