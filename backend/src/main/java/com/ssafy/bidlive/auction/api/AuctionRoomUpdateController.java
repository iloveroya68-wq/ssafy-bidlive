package com.ssafy.bidlive.auction.api;

import java.time.OffsetDateTime;

import com.ssafy.bidlive.auction.api.dto.AuctionRoomUpdateRequest;
import com.ssafy.bidlive.auction.api.dto.AuctionRoomUpdateResponse;
import com.ssafy.bidlive.auction.application.AuctionRoomUpdateCommand;
import com.ssafy.bidlive.auction.application.AuctionRoomUpdateException;
import com.ssafy.bidlive.auction.application.AuctionRoomUpdateService;
import com.ssafy.bidlive.auction.domain.AuctionRoomUpdateErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 판매자가 방송 시작 전 경매방의 상품·경매 조건을 부분 수정한다. */
@RestController
@RequestMapping("/api/v1/auction-rooms")
public class AuctionRoomUpdateController {

	private final AuctionRoomUpdateService updateService;

	public AuctionRoomUpdateController(AuctionRoomUpdateService updateService) {
		this.updateService = updateService;
	}

	@PatchMapping("/{auctionId}")
	public AuctionRoomUpdateResponse updateRoom(
		@PathVariable long auctionId,
		@RequestBody(required = false) AuctionRoomUpdateRequest request,
		Authentication authentication
	) {
		Long memberId = authenticatedMemberId(authentication);
		// 본문 자체가 없으면 바꿀 값이 없는 요청과 같다 — 서비스가 400으로 판정한다
		AuctionRoomUpdateRequest body = request == null
			? new AuctionRoomUpdateRequest(null, null)
			: request;
		return AuctionRoomUpdateResponse.from(updateService.updateRoom(
			new AuctionRoomUpdateCommand(
				auctionId,
				memberId,
				body.title(),
				body.description(),
				body.category(),
				body.imageUrl(),
				body.reservePrice(),
				body.durationMinutes(),
				body.startType(),
				toInstant(body.scheduledAt())
			)
		));
	}

	private static java.time.Instant toInstant(OffsetDateTime value) {
		return value == null ? null : value.toInstant();
	}

	private static Long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new AuctionRoomUpdateException(AuctionRoomUpdateErrorCode.UNAUTHORIZED);
		}
		try {
			long memberId = Long.parseLong(authentication.getName());
			if (memberId <= 0) {
				throw new AuctionRoomUpdateException(AuctionRoomUpdateErrorCode.UNAUTHORIZED);
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new AuctionRoomUpdateException(AuctionRoomUpdateErrorCode.UNAUTHORIZED);
		}
	}
}
