package com.ssafy.bidlive.auction.api;

import static com.ssafy.bidlive.auction.domain.AuctionForceEndErrorCode.INVALID_AUCTION_ID;

import com.ssafy.bidlive.auction.api.dto.ForceEndRequest;
import com.ssafy.bidlive.auction.api.dto.ForceEndResponse;
import com.ssafy.bidlive.auction.application.AuctionForceEndException;
import com.ssafy.bidlive.auction.application.AuctionForceEndService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEAL-09 운영 강제 종료.
 *
 * <p>권한은 `SecurityConfig`가 경로 단위로 `hasRole("ADMIN")`을 건다 — 이 레포는
 * 메서드 시큐리티(`@PreAuthorize`)를 켜지 않고 경로 매처로만 판정한다.
 */
@RestController
@RequestMapping("/api/v1/admin/auctions")
public class AdminAuctionForceEndController {

	private final AuctionForceEndService forceEndService;

	public AdminAuctionForceEndController(
		AuctionForceEndService forceEndService
	) {
		this.forceEndService = forceEndService;
	}

	@PostMapping("/{auctionId}/force-end")
	public ForceEndResponse forceEnd(
		@PathVariable String auctionId,
		@Valid @RequestBody ForceEndRequest request
	) {
		return ForceEndResponse.from(forceEndService.forceEnd(
			parseAuctionId(auctionId),
			request.reason()
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
			throw new AuctionForceEndException(INVALID_AUCTION_ID, exception);
		}
	}
}
