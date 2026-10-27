package com.ssafy.bidlive.auction.api;

import static com.ssafy.bidlive.auction.domain.AuctionResultErrorCode.INVALID_AUCTION_ID;

import com.ssafy.bidlive.auction.api.dto.AuctionResultResponse;
import com.ssafy.bidlive.auction.application.AuctionResultQueryException;
import com.ssafy.bidlive.auction.application.AuctionResultQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEAL-06·DEAL-08 경매 결과 조회.
 *
 * <p><b>인증은 선택이다</b>(명세 「인증: 불필요 / 공개 범위: Public」). 비로그인은 관전자로 보고
 * 공개 가능한 것만 받는다 — 개인 정산 두 필드는 {@code null}이다.
 */
@RestController
@RequestMapping("/api/v1/auction-rooms")
public class AuctionResultController {

	private final AuctionResultQueryService resultQueryService;

	public AuctionResultController(AuctionResultQueryService resultQueryService) {
		this.resultQueryService = resultQueryService;
	}

	@GetMapping("/{auctionId}/result")
	public AuctionResultResponse getResult(
		@PathVariable String auctionId,
		Authentication authentication
	) {
		return AuctionResultResponse.from(
			resultQueryService.getResult(parseAuctionId(auctionId), viewerMemberId(authentication))
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
			throw new AuctionResultQueryException(INVALID_AUCTION_ID, exception);
		}
	}

	/** 토큰이 없거나 형식이 어긋나면 익명으로 떨어뜨린다 — 공개 조회라 401로 막지 않는다 */
	private static Long viewerMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			return null;
		}
		try {
			return Long.parseLong(authentication.getName());
		} catch (NumberFormatException exception) {
			return null;
		}
	}
}
