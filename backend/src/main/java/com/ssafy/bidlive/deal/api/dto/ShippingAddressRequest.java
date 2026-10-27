package com.ssafy.bidlive.deal.api.dto;

import com.ssafy.bidlive.deal.application.ShippingAddressCommand;

/**
 * 배송지 입력 요청이다. 필드 이름은 명세 Request 예시를 그대로 따른다 —
 * `deal_shipping` 컬럼명(`recipient_name`·`recipient_phone`·`address_detail`)과 다르며 매핑은
 * 어댑터에서 한다.
 *
 * <p>명세에 있는 {@code memo}는 받지 않는다 — 이유는 {@link ShippingAddressCommand} 참조.
 */
public record ShippingAddressRequest(
	String receiverName,
	String contact,
	String postalCode,
	String address,
	String detailAddress
) {

	public ShippingAddressCommand toCommand() {
		return new ShippingAddressCommand(
			receiverName,
			contact,
			postalCode,
			address,
			detailAddress
		);
	}
}
