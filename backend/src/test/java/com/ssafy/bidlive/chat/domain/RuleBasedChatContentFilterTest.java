package com.ssafy.bidlive.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RuleBasedChatContentFilterTest {

	@Test
	void masksProhibitedWordsCaseInsensitively() {
		ChatContentFilter filter = filterWith(Set.of("bad"));

		FilterResult result = filter.filter("이건 BAD입니다.");

		assertThat(result.filteredContent()).isEqualTo("이건 ***입니다.");
		assertThat(result.maskedFields()).containsExactly(
			MaskingCategory.PROHIBITED_WORD
		);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"010-1234-5678",
		"010 1234 5678",
		"01012345678",
		"011-123-4567"
	})
	void masksDomesticMobilePhoneNumbers(String phoneNumber) {
		ChatContentFilter filter = filterWith(Set.of());

		FilterResult result = filter.filter(phoneNumber);

		assertThat(result.filteredContent()).isEqualTo(
			"*".repeat(phoneNumber.length())
		);
		assertThat(result.maskedFields()).containsExactly(
			MaskingCategory.CONTACT
		);
	}

	@Test
	void masksMessengerIdOnlyWhenMessengerIsIdentified() {
		ChatContentFilter filter = filterWith(Set.of());

		FilterResult result = filter.filter(
			"카톡 abc123으로 연락 주세요."
		);

		assertThat(result.filteredContent()).isEqualTo(
			"카톡 ******으로 연락 주세요."
		);
		assertThat(result.maskedFields()).containsExactly(
			MaskingCategory.CONTACT
		);
	}

	@Test
	void doesNotTreatOrdinaryProductCodeAsMessengerId() {
		ChatContentFilter filter = filterWith(Set.of());

		FilterResult result = filter.filter("상품 모델은 abc123입니다.");

		assertThat(result.filteredContent()).isEqualTo(
			"상품 모델은 abc123입니다."
		);
		assertThat(result.maskedFields()).isEmpty();
	}

	@Test
	void masksSeparatedAccountNumber() {
		ChatContentFilter filter = filterWith(Set.of());

		FilterResult result = filter.filter(
			"계좌는 110-123-456789입니다."
		);

		assertThat(result.filteredContent()).isEqualTo(
			"계좌는 **************입니다."
		);
		assertThat(result.maskedFields()).containsExactly(
			MaskingCategory.ACCOUNT
		);
	}

	@Test
	void masksCompactAccountNumberFollowingAccountLabel() {
		ChatContentFilter filter = filterWith(Set.of());

		FilterResult result = filter.filter(
			"계좌번호 110123456789로 보내 주세요."
		);

		assertThat(result.filteredContent()).isEqualTo(
			"계좌번호 ************로 보내 주세요."
		);
		assertThat(result.maskedFields()).containsExactly(
			MaskingCategory.ACCOUNT
		);
	}

	@Test
	void doesNotTreatUnlabeledNumberAsAccount() {
		ChatContentFilter filter = filterWith(Set.of());

		FilterResult result = filter.filter("주문번호 110123456789입니다.");

		assertThat(result.filteredContent()).isEqualTo(
			"주문번호 110123456789입니다."
		);
		assertThat(result.maskedFields()).isEmpty();
	}

	@Test
	void doesNotRecordPhoneNumberAsAccount() {
		ChatContentFilter filter = filterWith(Set.of());

		FilterResult result = filter.filter("010-1234-5678");

		assertThat(result.maskedFields()).containsExactly(
			MaskingCategory.CONTACT
		);
	}

	@Test
	void recordsEveryDetectedCategory() {
		ChatContentFilter filter = filterWith(Set.of("나쁜말"));

		FilterResult result = filter.filter(
			"나쁜말이고 연락처는 010-1234-5678입니다."
		);

		assertThat(result.maskedFields()).containsExactlyInAnyOrder(
			MaskingCategory.PROHIBITED_WORD,
			MaskingCategory.CONTACT
		);
	}

	@Test
	void mergesOverlappingRangesAndPreservesEveryCategory() {
		ChatContentFilter filter = filterWith(Set.of("abc123"));

		FilterResult result = filter.filter("카톡 abc123");

		assertThat(result.filteredContent()).isEqualTo("카톡 ******");
		assertThat(result.maskedFields()).containsExactlyInAnyOrder(
			MaskingCategory.PROHIBITED_WORD,
			MaskingCategory.CONTACT
		);
	}

	@Test
	void mergesAdjacentRanges() {
		ChatContentFilter filter = filterWith(Set.of("금칙", "단어"));

		FilterResult result = filter.filter("앞 금칙단어 뒤");

		assertThat(result.filteredContent()).isEqualTo("앞 **** 뒤");
	}

	@Test
	void leavesNormalContentUnchanged() {
		ChatContentFilter filter = filterWith(Set.of("금칙어"));

		FilterResult result = filter.filter("상품  상태가 궁금합니다.");

		assertThat(result.filteredContent()).isEqualTo(
			"상품  상태가 궁금합니다."
		);
		assertThat(result.maskedFields()).isEmpty();
	}

	@Test
	void handlesEmptyProhibitedWordSet() {
		ChatContentFilter filter = filterWith(Set.of());

		FilterResult result = filter.filter("일반 채팅입니다.");

		assertThat(result.filteredContent()).isEqualTo("일반 채팅입니다.");
		assertThat(result.maskedFields()).isEmpty();
	}

	@Test
	void preservesContentOutsideDetectedRanges() {
		ChatContentFilter filter = filterWith(Set.of("금칙어"));

		FilterResult result = filter.filter("앞부분 금칙어 뒷부분");

		assertThat(result.filteredContent()).isEqualTo(
			"앞부분 *** 뒷부분"
		);
	}

	@Test
	void rejectsNullContent() {
		ChatContentFilter filter = filterWith(Set.of());

		assertThatThrownBy(() -> filter.filter(null))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("content");
	}

	@Test
	void rejectsNullProhibitedWordProvider() {
		assertThatThrownBy(() -> new RuleBasedChatContentFilter(null))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("prohibitedWordProvider");
	}

	private static ChatContentFilter filterWith(
		Set<String> prohibitedWords
	) {
		return new RuleBasedChatContentFilter(() -> prohibitedWords);
	}
}
