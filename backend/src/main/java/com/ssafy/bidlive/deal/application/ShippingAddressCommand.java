package com.ssafy.bidlive.deal.application;

import com.ssafy.bidlive.deal.domain.DealErrorCode;

/**
 * 구매자가 입력한 배송지다. 값 검증은 {@link #normalized()}에서 한 번만 하고, 그 뒤로는 저장 가능한
 * 값으로 다룬다.
 *
 * <p>길이 상한은 `deal_shipping` 컬럼 폭에서 왔다. 넘치면 DB가 잘라내는 대신 400으로 돌려준다 —
 * 조용히 잘린 주소는 배송 실패로 이어지고, 그때는 원래 값이 어디에도 남아 있지 않다.
 *
 * <p>명세 Request에는 {@code memo}가 있지만 받지 않는다. `deal_shipping`에 대응 컬럼이 없고
 * 와이어프레임 모달에도 입력 칸이 없어 프론트가 보내지 않는다(S15P11A704-337). 컬럼과 함께
 * 넣는 편이 맞아서 별도 이슈로 끊었다 — 그때까지 memo를 보내면 Jackson이 조용히 버린다.
 */
public record ShippingAddressCommand(
	String receiverName,
	String contact,
	String postalCode,
	String address,
	String detailAddress
) {

	private static final int RECEIVER_NAME_MAX = 50;
	private static final int CONTACT_MAX = 30;
	private static final int POSTAL_CODE_MAX = 20;
	private static final int ADDRESS_MAX = 255;
	private static final int DETAIL_ADDRESS_MAX = 255;

	/**
	 * 공백을 정리하고 필수값·길이를 확인한 사본을 돌려준다.
	 *
	 * <p>{@code detailAddress}만 선택이다 — 동·호수가 없는 주소가 있고, 비었다고 배송이 막히지는
	 * 않는다. 빈 문자열은 `null`로 눕혀 "입력 안 함"과 구분되지 않게 한다.
	 */
	public ShippingAddressCommand normalized() {
		return new ShippingAddressCommand(
			required(receiverName, RECEIVER_NAME_MAX),
			required(contact, CONTACT_MAX),
			required(postalCode, POSTAL_CODE_MAX),
			required(address, ADDRESS_MAX),
			optional(detailAddress, DETAIL_ADDRESS_MAX)
		);
	}

	private static String required(String value, int maxLength) {
		String trimmed = value == null ? null : value.trim();
		if (trimmed == null || trimmed.isEmpty()) {
			throw new DealException(DealErrorCode.INVALID_SHIPPING_ADDRESS);
		}
		return withinLimit(trimmed, maxLength);
	}

	private static String optional(String value, int maxLength) {
		String trimmed = value == null ? null : value.trim();
		if (trimmed == null || trimmed.isEmpty()) {
			return null;
		}
		return withinLimit(trimmed, maxLength);
	}

	private static String withinLimit(String value, int maxLength) {
		if (value.length() > maxLength) {
			throw new DealException(DealErrorCode.INVALID_SHIPPING_ADDRESS);
		}
		return value;
	}
}
