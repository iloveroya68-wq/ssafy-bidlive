package com.ssafy.bidlive.chat.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.ssafy.bidlive.chat.application.ChatHistoryPage;
import com.ssafy.bidlive.chat.application.ChatHistoryPersistence;
import com.ssafy.bidlive.chat.application.ChatMessagePersistence;
import com.ssafy.bidlive.chat.domain.ChatMessage;
import com.ssafy.bidlive.chat.domain.ChatMessageContent;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.ClientMessageId;
import com.ssafy.bidlive.chat.domain.MaskingCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL에서 ULID 커서 정렬과 삭제·BLOCKED 메시지 제외를 검증한다. */
@SpringBootTest
@Transactional
class JdbcChatHistoryPersistenceAdapterIntegrationTest {

	private static final long SELLER_ID = 961_001L;
	private static final long MEMBER_ID = 961_002L;
	private static final long CATEGORY_ID = 962_001L;
	private static final long PRODUCT_ID = 963_001L;
	private static final long AUCTION_ID = 964_001L;

	private static final String MESSAGE_A =
		"01J8W3P2N9KQ4T123456789ABA";
	private static final String MESSAGE_B =
		"01J8W3P2N9KQ4T123456789ABB";
	private static final String MESSAGE_C =
		"01J8W3P2N9KQ4T123456789ABC";
	private static final String MESSAGE_D =
		"01J8W3P2N9KQ4T123456789ABD";
	private static final String UNKNOWN_CURSOR =
		"01J8W3P2N9KQ4T123456789ABZ";

	@Autowired
	private ChatHistoryPersistence historyPersistence;

	@Autowired
	private ChatMessagePersistence messagePersistence;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void insertFixture() {
		jdbcTemplate.update("""
			INSERT INTO member (member_id, nickname, role, status)
			VALUES
				(?, '이력판매자', 'USER', 'ACTIVE'),
				(?, '이력참여자', 'USER', 'ACTIVE')
			""", SELLER_ID, MEMBER_ID);
		jdbcTemplate.update("""
			INSERT INTO product_category (
				category_id, code, name, is_active, display_order
			)
			VALUES (?, 'TEST_CHAT_HISTORY', '채팅 이력 테스트', TRUE, 9201)
			""", CATEGORY_ID);
		jdbcTemplate.update("""
			INSERT INTO product (
				product_id, seller_member_id, category_id,
				title, description, moderation_status
			)
			VALUES (?, ?, ?, '채팅 이력 상품', '채팅 이력 테스트', 'PASSED')
			""", PRODUCT_ID, SELLER_ID, CATEGORY_ID);
		jdbcTemplate.update("""
			INSERT INTO auction_room (
				auction_room_id, product_id, reserve_price,
				duration_minutes, start_type, status
			)
			VALUES (?, ?, 1000, 10, 'IMMEDIATE', 'LIVE')
			""", AUCTION_ID, PRODUCT_ID);
	}

	@Test
	void returnsFirstAndPreviousPagesInOldestToNewestOrder() {
		saveMessages();

		ChatHistoryPage first = historyPersistence
			.findBefore(AUCTION_ID, null, 2)
			.orElseThrow();
		ChatHistoryPage previous = historyPersistence
			.findBefore(AUCTION_ID, new ChatMessageId(MESSAGE_C), 2)
			.orElseThrow();

		assertThat(ids(first)).containsExactly(MESSAGE_C, MESSAGE_D);
		assertThat(first.hasMore()).isTrue();
		assertThat(ids(previous)).containsExactly(MESSAGE_A, MESSAGE_B);
		assertThat(previous.hasMore()).isFalse();
	}

	@Test
	void usesMessageIdAsTieBreakerForSameCreatedAt() {
		Instant sameTime = Instant.parse("2026-08-01T00:00:00Z");
		save(MESSAGE_A, 1, sameTime, Set.of());
		save(MESSAGE_B, 2, sameTime, Set.of());
		save(MESSAGE_C, 3, sameTime, Set.of());

		ChatHistoryPage first = historyPersistence
			.findBefore(AUCTION_ID, null, 2)
			.orElseThrow();
		ChatHistoryPage previous = historyPersistence
			.findBefore(AUCTION_ID, new ChatMessageId(MESSAGE_B), 2)
			.orElseThrow();

		assertThat(ids(first)).containsExactly(MESSAGE_B, MESSAGE_C);
		assertThat(ids(previous)).containsExactly(MESSAGE_A);
	}

	@Test
	void excludesDeletedAndBlockedMessagesAndRestoresMaskedFields() {
		saveMessages();
		jdbcTemplate.update(
			"UPDATE chat_message SET is_deleted = TRUE WHERE message_id = ?",
			MESSAGE_B
		);
		jdbcTemplate.update(
			"UPDATE chat_message SET moderation_status = 'BLOCKED' WHERE message_id = ?",
			MESSAGE_C
		);

		ChatHistoryPage page = historyPersistence
			.findBefore(AUCTION_ID, null, 10)
			.orElseThrow();

		assertThat(ids(page)).containsExactly(MESSAGE_A, MESSAGE_D);
		assertThat(page.messages().getFirst().maskedFields())
			.containsExactly(MaskingCategory.CONTACT);
	}

	@Test
	void returnsEmptyLookupForUnknownCursor() {
		saveMessages();

		assertThat(historyPersistence.findBefore(
			AUCTION_ID,
			new ChatMessageId(UNKNOWN_CURSOR),
			30
		)).isEmpty();
	}

	private void saveMessages() {
		save(
			MESSAGE_A,
			1,
			Instant.parse("2026-08-01T00:00:01Z"),
			Set.of(MaskingCategory.CONTACT)
		);
		save(
			MESSAGE_B,
			2,
			Instant.parse("2026-08-01T00:00:02Z"),
			Set.of(MaskingCategory.ACCOUNT)
		);
		save(
			MESSAGE_C,
			3,
			Instant.parse("2026-08-01T00:00:03Z"),
			Set.of(MaskingCategory.PROHIBITED_WORD)
		);
		save(
			MESSAGE_D,
			4,
			Instant.parse("2026-08-01T00:00:04Z"),
			Set.of()
		);
	}

	private void save(
		String messageId,
		int sequence,
		Instant createdAt,
		Set<MaskingCategory> maskedFields
	) {
		messagePersistence.saveIfAbsent(new ChatMessage(
			new ChatMessageId(messageId),
			new ClientMessageId(
				"018f6f4e-2a61-7c00-a1b2-3344556677%02d"
					.formatted(sequence)
			),
			AUCTION_ID,
			MEMBER_ID,
			"이력참여자",
			new ChatMessageContent("채팅 메시지 " + sequence),
			false,
			maskedFields,
			createdAt
		));
	}

	private static List<String> ids(ChatHistoryPage page) {
		return page.messages().stream()
			.map(message -> message.messageId().value())
			.toList();
	}
}
