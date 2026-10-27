package com.ssafy.bidlive.bid.api;

import java.util.UUID;

import com.ssafy.bidlive.bid.api.dto.BidCreateRequest;
import com.ssafy.bidlive.bid.api.dto.BidCreateResponse;
import com.ssafy.bidlive.bid.application.BidCommandService;
import com.ssafy.bidlive.bid.application.BidResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 회원의 비공개 직접 입력 입찰을 접수하고 개인 처리 결과만 반환한다. */
@RestController
@RequestMapping("/api/v1/auctions")
public class BidController {

	private final BidCommandService bidCommandService;

	public BidController(BidCommandService bidCommandService) {
		this.bidCommandService = bidCommandService;
	}

	@PostMapping("/{auctionId}/bids")
	public ResponseEntity<BidCreateResponse> createBid(
		@PathVariable Long auctionId,
		@RequestHeader("Idempotency-Key") UUID idempotencyKey,
		@Valid @RequestBody BidCreateRequest request,
		Authentication authentication
	) {
		BidResult result = bidCommandService.createBid(
			auctionId,
			authenticatedMemberId(authentication),
			request.amount(),
			idempotencyKey
		);
		return ResponseEntity.status(201).body(BidCreateResponse.from(result));
	}

	private Long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new BidUnauthenticatedException();
		}
		try {
			Long memberId = Long.valueOf(authentication.getName());
			if (memberId <= 0) {
				throw new NumberFormatException("member ID must be positive");
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new BidUnauthenticatedException(exception);
		}
	}
}
