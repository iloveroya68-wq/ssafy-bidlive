package com.ssafy.bidlive.deal.api;

import com.ssafy.bidlive.deal.api.dto.DealTransitionResponses;
import com.ssafy.bidlive.deal.api.dto.ShippingAddressRequest;
import com.ssafy.bidlive.deal.application.DealEscrowService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 낙찰 이후 에스크로 거래를 단계별로 넘긴다(DEAL-05).
 *
 * <p>세 전이의 주체가 번갈아 바뀐다: 배송지=구매자 → 발송=판매자 → 수령=구매자. 판정은 전부
 * 서비스가 하고 여기서는 인증 주체만 꺼낸다.
 */
@RestController
@RequestMapping("/api/v1/deals")
public class DealEscrowController {

	private final DealEscrowService escrowService;

	public DealEscrowController(DealEscrowService escrowService) {
		this.escrowService = escrowService;
	}

	@PatchMapping("/{dealId}/shipping-address")
	public DealTransitionResponses.Address submitShippingAddress(
		@PathVariable long dealId,
		@RequestBody(required = false) ShippingAddressRequest request,
		Authentication authentication
	) {
		return DealTransitionResponses.Address.from(
			escrowService.submitShippingAddress(
				dealId,
				DealPrincipal.memberId(authentication),
				request == null ? null : request.toCommand()
			)
		);
	}

	/** 요청 본문 없음 — 발송 시각과 자동 확정 예정 시각은 서버가 정한다. */
	@PostMapping("/{dealId}/shipment-confirmation")
	public DealTransitionResponses.Shipment confirmShipment(
		@PathVariable long dealId,
		Authentication authentication
	) {
		return DealTransitionResponses.Shipment.from(
			escrowService.confirmShipment(dealId, DealPrincipal.memberId(authentication))
		);
	}

	/** 요청 본문 없음. 되돌릴 수 없다 — 에스크로 대금이 판매자 지갑으로 나간다. */
	@PostMapping("/{dealId}/receipt-confirmation")
	public DealTransitionResponses.Receipt confirmReceipt(
		@PathVariable long dealId,
		Authentication authentication
	) {
		return DealTransitionResponses.Receipt.from(
			escrowService.confirmReceipt(dealId, DealPrincipal.memberId(authentication))
		);
	}
}
