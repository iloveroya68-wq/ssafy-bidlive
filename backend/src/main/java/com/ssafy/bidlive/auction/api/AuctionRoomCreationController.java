package com.ssafy.bidlive.auction.api;

import static com.ssafy.bidlive.auction.domain.AuctionRoomCreationErrorCode.UNAUTHORIZED;

import com.ssafy.bidlive.auction.api.dto.AuctionRoomCreationRequest;
import com.ssafy.bidlive.auction.api.dto.AuctionRoomCreationResponse;
import com.ssafy.bidlive.auction.application.AuctionRoomCreationException;
import com.ssafy.bidlive.auction.application.AuctionRoomCreationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 판매자의 상품과 대기 상태 경매방을 함께 생성합니다. */
@RestController
@RequestMapping("/api/v1/auction-rooms")
public class AuctionRoomCreationController {

	private final AuctionRoomCreationService creationService;

	public AuctionRoomCreationController(
		AuctionRoomCreationService creationService
	) {
		this.creationService = creationService;
	}

	@PostMapping
	public ResponseEntity<AuctionRoomCreationResponse> create(
		@RequestBody AuctionRoomCreationRequest request,
		Authentication authentication
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(AuctionRoomCreationResponse.from(
				creationService.create(
					request.toCommand(authenticatedMemberId(authentication))
				)
			));
	}

	private Long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new AuctionRoomCreationException(UNAUTHORIZED);
		}
		try {
			Long memberId = Long.valueOf(authentication.getName());
			if (memberId <= 0) {
				throw new NumberFormatException("member ID must be positive");
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new AuctionRoomCreationException(UNAUTHORIZED, exception);
		}
	}
}
