package com.ssafy.bidlive.auction.api;

import com.ssafy.bidlive.auction.api.dto.SellerEndResponse;
import com.ssafy.bidlive.auction.application.AuctionForceEndService;
import com.ssafy.bidlive.auction.application.AuctionSellerEndException;
import com.ssafy.bidlive.auction.domain.AuctionSellerEndErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ROOM-09 판매자 종료. 진행 콘솔의 「방송 종료」가 경매까지 닫는다. 요청 본문은 없다.
 *
 * <p>운영 강제 종료(DEAL-09)와 <b>같은 서비스</b>를 쓰되 진입 메서드가 다르다 — 마감 절차는 같고
 * 대상 판정만 다르기 때문이다. 사유는 서버가 `HOST_LEFT`로 고정하므로 본문을 받지 않는다.
 *
 * <p>방송 송출 종료(LIVE-02 `POST /rooms/{roomId}/live/end`)와는 별개다. 그쪽은
 * `live_broadcast`와 OpenVidu 세션만 닫고 경매 상태를 건드리지 않는다 — 둘 다 불러야 방송과
 * 경매가 함께 끝난다.
 */
@RestController
@RequestMapping("/api/v1/auctions")
public class AuctionSellerEndController {

	private final AuctionForceEndService endService;

	public AuctionSellerEndController(AuctionForceEndService endService) {
		this.endService = endService;
	}

	@PostMapping("/{auctionId}/end")
	public SellerEndResponse endBySeller(
		@PathVariable String auctionId,
		Authentication authentication
	) {
		return SellerEndResponse.from(endService.endBySeller(
			parseAuctionId(auctionId),
			authenticatedMemberId(authentication)
		));
	}

	/**
	 * 경로 변수를 문자열로 받아 직접 판정한다. `long`으로 바인딩하면 숫자가 아닌 값에서
	 * Spring이 먼저 400을 던져 명세의 오류 코드가 실리지 않는다 — 다른 경매 컨트롤러와 같은 방식.
	 */
	private static long parseAuctionId(String value) {
		try {
			long parsed = Long.parseLong(value);
			if (parsed <= 0) {
				throw new NumberFormatException("auctionId must be positive");
			}
			return parsed;
		} catch (NumberFormatException exception) {
			throw new AuctionSellerEndException(
				AuctionSellerEndErrorCode.INVALID_AUCTION_ID,
				exception
			);
		}
	}

	private static Long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new AuctionSellerEndException(AuctionSellerEndErrorCode.UNAUTHORIZED);
		}
		try {
			long memberId = Long.parseLong(authentication.getName());
			if (memberId <= 0) {
				throw new AuctionSellerEndException(AuctionSellerEndErrorCode.UNAUTHORIZED);
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new AuctionSellerEndException(
				AuctionSellerEndErrorCode.UNAUTHORIZED,
				exception
			);
		}
	}
}
