package com.ssafy.bidlive.deal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.bidlive.deal.domain.DealErrorCode;
import org.junit.jupiter.api.Test;

/** 배송지 정규화·검증 규칙을 검증한다. 여기서 통과한 값만 DB로 간다. */
class ShippingAddressCommandTest {

	@Test
	void trimsEveryFieldAndKeepsDetailAddress() {
		ShippingAddressCommand normalized = command(
			" 김서연 ",
			" 010-1234-5678 ",
			" 06236 ",
			" 서울시 강남구 ",
			" 101동 202호 "
		).normalized();

		assertThat(normalized).isEqualTo(new ShippingAddressCommand(
			"김서연",
			"010-1234-5678",
			"06236",
			"서울시 강남구",
			"101동 202호"
		));
	}

	/** 프론트 모달은 상세주소를 필수로 막지 않고 빈 문자열을 보낸다 — 그건 "입력 안 함"이다. */
	@Test
	void treatsBlankDetailAddressAsAbsent() {
		assertThat(command("김서연", "010", "06236", "서울시", "   ").normalized().detailAddress())
			.isNull();
	}

	@Test
	void rejectsMissingRequiredField() {
		assertThatThrownBy(() -> command("김서연", "010", "06236", null, "101동").normalized())
			.isInstanceOf(DealException.class)
			.satisfies(e -> assertThat(((DealException) e).getErrorCode())
				.isEqualTo(DealErrorCode.INVALID_SHIPPING_ADDRESS));
	}

	/**
	 * 컬럼 폭(`address` VARCHAR(255))을 넘는 값은 400으로 돌려준다. DB가 조용히 자르면 배송이
	 * 실패하고 그때는 원래 주소가 어디에도 없다.
	 */
	@Test
	void rejectsValueLongerThanColumn() {
		assertThatThrownBy(() -> command(
			"김서연",
			"010",
			"06236",
			"서".repeat(256),
			"101동"
		).normalized())
			.satisfies(e -> assertThat(((DealException) e).getErrorCode())
				.isEqualTo(DealErrorCode.INVALID_SHIPPING_ADDRESS));
	}

	private static ShippingAddressCommand command(
		String receiverName,
		String contact,
		String postalCode,
		String address,
		String detailAddress
	) {
		return new ShippingAddressCommand(
			receiverName,
			contact,
			postalCode,
			address,
			detailAddress
		);
	}
}
