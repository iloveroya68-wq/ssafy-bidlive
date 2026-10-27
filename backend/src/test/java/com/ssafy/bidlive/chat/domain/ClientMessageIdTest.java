package com.ssafy.bidlive.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 클라이언트 멱등 키의 UUID·ULID 허용 형식을 검증한다. */
class ClientMessageIdTest {

	@Test
	void acceptsUuid() {
		String uuid = "018f6f4e-2a61-7c00-a1b2-334455667788";

		assertThat(new ClientMessageId(uuid).value()).isEqualTo(uuid);
	}

	@Test
	void acceptsCanonicalUlid() {
		String ulid = "01J8W3P2N9KQ4T123456789ABC";

		assertThat(new ClientMessageId(ulid).value()).isEqualTo(ulid);
	}

	@Test
	void rejectsInvalidId() {
		assertThatThrownBy(() -> new ClientMessageId("not-an-id"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsLowercaseUlid() {
		assertThatThrownBy(() ->
			new ClientMessageId("01j8w3p2n9kq4t123456789abc")
		).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsNullId() {
		assertThatThrownBy(() -> new ClientMessageId(null))
			.isInstanceOf(NullPointerException.class);
	}
}
