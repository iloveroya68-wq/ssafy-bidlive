package com.ssafy.bidlive.member.domain;

import java.util.Arrays;

/** 정산 계좌에서 지원하는 은행 코드와 표시 이름을 관리한다. */
public enum SettlementBank {
	KB_KOOKMIN("004", "KB국민은행"),
	SHINHAN("088", "신한은행"),
	WOORI("020", "우리은행"),
	HANA("081", "하나은행"),
	KAKAO_BANK("090", "카카오뱅크"),
	TOSS_BANK("092", "토스뱅크");

	private final String code;
	private final String displayName;

	SettlementBank(String code, String displayName) {
		this.code = code;
		this.displayName = displayName;
	}

	public String code() {
		return code;
	}

	public String displayName() {
		return displayName;
	}

	public static SettlementBank fromCode(String code) {
		return Arrays.stream(values())
			.filter(bank -> bank.code.equals(code))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException(
				"unsupported settlement bank code"
			));
	}
}
