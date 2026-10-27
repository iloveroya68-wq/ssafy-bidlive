package com.ssafy.bidlive.deal.api;

import com.ssafy.bidlive.deal.api.dto.DealShippingResponse;
import com.ssafy.bidlive.deal.application.DealShippingQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEAL-05 배송 정보 조회 (S15P11A704-694).
 *
 * <p>배송지는 지금까지 <b>쓰기 전용</b>이었다 — 구매자가 넣으면 저장만 되고 되읽는 경로가 없어
 * 판매자는 「발송 확인」 버튼만 받았다. 어디로 보낼지 알 방법이 없던 것이 이 API를 만든 이유다.
 *
 * <p>거래 상태와 무관하게 당사자면 조회된다. 발송 뒤 분쟁·반품에서 판매자가 다시 봐야 할 수 있어
 * 완료 후에도 닫지 않는다.
 */
@RestController
@RequestMapping("/api/v1/deals")
public class DealShippingController {

	private final DealShippingQueryService shippingQueryService;

	public DealShippingController(DealShippingQueryService shippingQueryService) {
		this.shippingQueryService = shippingQueryService;
	}

	@GetMapping("/{dealId}/shipping")
	public DealShippingResponse findShipping(
		@PathVariable long dealId,
		Authentication authentication
	) {
		return DealShippingResponse.from(
			shippingQueryService.findShipping(dealId, DealPrincipal.memberId(authentication))
		);
	}
}
