package com.ssafy.bidlive.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** 저장·송출 채팅 모델의 식별자와 불변 컬렉션 정책을 검증한다. */
class ChatMessageTest {

	private static final Instant CREATED_AT =
		Instant.parse("2026-07-31T01:00:00Z");

	@Test
	void createsMessageWithNormalizedNicknameAndImmutableMaskedFields() {
		Set<MaskingCategory> maskedFields =
			new HashSet<>(Set.of(MaskingCategory.CONTACT));

		ChatMessage message = new ChatMessage(
			new ChatMessageId("01J8W3P2N9KQ4T123456789ABC"),
			new ClientMessageId(
				"018f6f4e-2a61-7c00-a1b2-334455667788"
			),
			10L,
			20L,
			"  경매참여자  ",
			new ChatMessageContent("연락처는 가려 주세요."),
			false,
			maskedFields,
			CREATED_AT
		);
		maskedFields.clear();

		assertThat(message.nickname()).isEqualTo("경매참여자");
		assertThat(message.maskedFields())
			.containsExactly(MaskingCategory.CONTACT);
		assertThatThrownBy(() ->
			message.maskedFields().add(MaskingCategory.ACCOUNT)
		).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void rejectsNonPositiveAuctionId() {
		assertThatThrownBy(() -> message(0L, 20L))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsNonPositiveMemberId() {
		assertThatThrownBy(() -> message(10L, 0L))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private static ChatMessage message(long auctionId, long memberId) {
		return new ChatMessage(
			new ChatMessageId("01J8W3P2N9KQ4T123456789ABC"),
			new ClientMessageId(
				"018f6f4e-2a61-7c00-a1b2-334455667788"
			),
			auctionId,
			memberId,
			"경매참여자",
			new ChatMessageContent("안녕하세요."),
			false,
			Set.of(),
			CREATED_AT
		);
	}
}
