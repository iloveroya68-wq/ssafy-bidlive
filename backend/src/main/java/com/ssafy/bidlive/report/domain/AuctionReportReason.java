package com.ssafy.bidlive.report.domain;

/**
 * 대기방·라이브 경매 신고 사유다(DEAL-04 「신고 사유」 표의 4값).
 *
 * <p>{@code ITEM_NOT_AS_DESCRIBED}는 없다 — 명세가 *"상품 수령 후 발생하는 거래 분쟁 사유이므로
 * 대기방·라이브 신고에서는 제외"*로 명시했다. 상품을 받은 뒤의 분쟁은 이 API가 다루는 단계가 아니다.
 */
public enum AuctionReportReason {
	/** 허위·오해를 유발하는 매물 */
	FALSE_LISTING,
	/** 거래 금지 품목 */
	PROHIBITED_ITEM,
	/** 부적절한 방송 또는 언행 */
	INAPPROPRIATE_BROADCAST,
	/** 기타 사유. 상세 설명이 필수다 */
	OTHER;

	/**
	 * 지원하지 않는 값이면 비어 있는 값을 돌려준다.
	 *
	 * <p>{@code valueOf}를 그대로 쓰지 않는 이유: 클라이언트가 보낸 문자열이라 오타 하나가
	 * {@code IllegalArgumentException} → 500이 된다. 명세는 이 경우 400
	 * {@code INVALID_REPORT_REQUEST}를 요구한다.
	 */
	public static AuctionReportReason parse(String value) {
		if (value == null) {
			return null;
		}
		for (AuctionReportReason reason : values()) {
			if (reason.name().equals(value)) {
				return reason;
			}
		}
		return null;
	}

	public boolean requiresDescription() {
		return this == OTHER;
	}
}
