package com.ssafy.bidlive.auction.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 판매자 닉네임이 명세 예시대로 앞 절반만 남기고 가려지는지 검증한다. */
class SellerNicknameMaskTest {

	@ParameterizedTest
	@CsvSource({
		"서연123, 서연***",
		"판매자, 판**",
		"가나, 가*"
	})
	void keepsFirstHalfAndMasksTheRest(String nickname, String expected) {
		assertThat(SellerNicknameMask.of(nickname)).isEqualTo(expected);
	}

	@Test
	void masksSingleCharacterNicknameEntirely() {
		assertThat(SellerNicknameMask.of("가")).isEqualTo("*");
	}

	@Test
	void fallsBackWhenNicknameIsMissing() {
		assertThat(SellerNicknameMask.of(null)).isEqualTo("***");
		assertThat(SellerNicknameMask.of(" ")).isEqualTo("***");
	}
}
