package com.ssafy.bidlive.auction.domain;

/**
 * 판매자 닉네임을 공개 가능한 형태로 가린다. 앞 절반만 남기고 나머지를 `*`로 덮는다
 * (명세 예시 `서연***`·`판**`이 모두 이 규칙이다).
 */
public final class SellerNicknameMask {

	private static final String UNKNOWN = "***";

	private SellerNicknameMask() {
	}

	public static String of(String nickname) {
		if (nickname == null || nickname.isBlank()) {
			return UNKNOWN;
		}
		int length = nickname.length();
		if (length == 1) {
			return "*";
		}
		int revealed = length / 2;
		return nickname.substring(0, revealed) + "*".repeat(length - revealed);
	}
}
