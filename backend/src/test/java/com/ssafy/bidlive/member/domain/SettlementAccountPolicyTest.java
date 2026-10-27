package com.ssafy.bidlive.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 계좌번호 정규화·검증·마스킹과 예금주명 검증 정책을 확인한다. */
class SettlementAccountPolicyTest {

	@Test
	void normalizesAndMasksAccountNumber() {
		String normalized =
			SettlementAccountPolicy.normalizeAccountNumber(
				" 123-456-789-012 "
			);

		assertThat(normalized).isEqualTo("123456789012");
		assertThat(SettlementAccountPolicy.isValidAccountNumber(normalized))
			.isTrue();
		assertThat(SettlementAccountPolicy.maskAccountNumber(normalized))
			.isEqualTo("123-****-**012");
	}

	@Test
	void rejectsInvalidAccountNumbers() {
		assertThat(SettlementAccountPolicy.isValidAccountNumber(null))
			.isFalse();
		assertThat(SettlementAccountPolicy.isValidAccountNumber("123456789"))
			.isFalse();
		assertThat(SettlementAccountPolicy.isValidAccountNumber(
			"123456789012345"
		)).isFalse();
		assertThat(SettlementAccountPolicy.isValidAccountNumber(
			"123456789O12"
		)).isFalse();
		assertThatThrownBy(() ->
			SettlementAccountPolicy.maskAccountNumber("invalid")
		).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void normalizesAndValidatesAccountHolderName() {
		String normalized =
			SettlementAccountPolicy.normalizeAccountHolderName(
				"  신  희원  "
			);

		assertThat(normalized).isEqualTo("신 희원");
		assertThat(
			SettlementAccountPolicy.isValidAccountHolderName(normalized)
		).isTrue();
		assertThat(SettlementAccountPolicy.isValidAccountHolderName(" "))
			.isFalse();
		assertThat(
			SettlementAccountPolicy.isValidAccountHolderName("신희원\n")
		).isFalse();
	}
}
