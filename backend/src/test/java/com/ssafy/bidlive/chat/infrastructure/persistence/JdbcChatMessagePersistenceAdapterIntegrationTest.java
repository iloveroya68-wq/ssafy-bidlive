package com.ssafy.bidlive.chat.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.ssafy.bidlive.chat.application.ChatMessagePersistence;
import com.ssafy.bidlive.chat.application.ChatMessagePersistenceResult;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 채팅 신규 저장과 발신자별 clientMessageId 멱등 처리를 실제 PostgreSQL로 검증한다. */
@SpringBootTest
@Transactional
class JdbcChatMessagePersistenceAdapterIntegrationTest {

	private static final long SELLER_ID = 951_001L;
	private static final long PARTICIPANT_ID = 951_002L;
	private static final long CATEGORY_ID = 952_001L;
	private static final long PRODUCT_ID = 953_001L;
	private static final long AUCTION_ID = 954_001L;

	private static final String MESSAGE_ID_1 =
		"01J8W3P2N9KQ4T123456789ABC";
	private static final String MESSAGE_ID_2 =
		"01J8W3P2N9KQ4T123456789ABD";
	private static final String CLIENT_MESSAGE_ID_1 =
		"018f6f4e-2a61-7c00-a1b2-334455667788";
	private static final String CLIENT_MESSAGE_ID_2 =
		"018f6f4e-2a61-7c00-a1b2-334455667789";
	private static final Instant CREATED_AT =
		Instant.parse("2026-07-31T03:00:00Z");

	@Autowired
	private ChatMessagePersistence persistence;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeEach
	void insertFixture() {
		jdbcTemplate.update("""
			INSERT INTO member (member_id, nickname, role, status)
			VALUES
				(?, '멱등판매자', 'USER', 'ACTIVE'),
				(?, '멱등참여자', 'USER', 'ACTIVE')
			""", SELLER_ID, PARTICIPANT_ID);
		jdbcTemplate.update("""
			INSERT INTO product_category (
				category_id, code, name, is_active, display_order
			)
			VALUES (
				?, 'TEST_CHAT_PERSISTENCE', '채팅 저장 테스트', TRUE, 9101
			)
			""", CATEGORY_ID);
		jdbcTemplate.update("""
			INSERT INTO product (
				product_id,
				seller_member_id,
				category_id,
				title,
				description,
				moderation_status
			)
			VALUES (
				?, ?, ?, '채팅 저장 상품', '채팅 저장 테스트 상품', 'PASSED'
			)
			""", PRODUCT_ID, SELLER_ID, CATEGORY_ID);
		jdbcTemplate.update("""
			INSERT INTO auction_room (
				auction_room_id,
				product_id,
				reserve_price,
				duration_minutes,
				start_type,
				status
			)
			VALUES (?, ?, 1000, 10, 'IMMEDIATE', 'WAITING')
			""", AUCTION_ID, PRODUCT_ID);
	}

	@Test
	void storesNewChatMessage() throws SQLException {
		ChatMessage message = message(
			MESSAGE_ID_1,
			CLIENT_MESSAGE_ID_1,
			SELLER_ID,
			false
		);

		ChatMessagePersistenceResult result =
			persistence.saveIfAbsent(message);

		Map<String, Object> row = jdbcTemplate.queryForMap("""
			SELECT
				message_id,
				client_message_id,
				auction_room_id,
				sender_member_id,
				message_type,
				content,
				masked_fields,
				moderation_status,
				question_queue_status
			FROM chat_message
			WHERE message_id = ?
			""", MESSAGE_ID_1);
		assertThat(result.created()).isTrue();
		assertThat(result.messageId()).isEqualTo(message.messageId());
		assertThat(row)
			.containsEntry("message_id", MESSAGE_ID_1)
			.containsEntry("client_message_id", CLIENT_MESSAGE_ID_1)
			.containsEntry("message_type", "CHAT")
			.containsEntry("content", "연락처 ******")
			.containsEntry("moderation_status", "PENDING")
			.containsEntry("question_queue_status", null);
		assertThat(((Number) row.get("auction_room_id")).longValue())
			.isEqualTo(AUCTION_ID);
		assertThat(((Number) row.get("sender_member_id")).longValue())
			.isEqualTo(SELLER_ID);
		Array maskedFields = (Array) row.get("masked_fields");
		assertThat((String[]) maskedFields.getArray())
			.containsExactly("CONTACT");
	}

	@Test
	void findsExistingMessageIdBySenderAndClientMessageId() {
		persistence.saveIfAbsent(message(
			MESSAGE_ID_1,
			CLIENT_MESSAGE_ID_1,
			SELLER_ID,
			false
		));

		assertThat(persistence.findMessageId(
			SELLER_ID,
			new ClientMessageId(CLIENT_MESSAGE_ID_1)
		)).contains(new ChatMessageId(MESSAGE_ID_1));
	}

	@Test
	void returnsExistingResultForDuplicateFromSameSender() {
		ChatMessagePersistenceResult first = persistence.saveIfAbsent(message(
			MESSAGE_ID_1,
			CLIENT_MESSAGE_ID_1,
			SELLER_ID,
			false
		));
		ChatMessagePersistenceResult duplicate =
			persistence.saveIfAbsent(message(
				MESSAGE_ID_2,
				CLIENT_MESSAGE_ID_1,
				SELLER_ID,
				false
			));

		Long storedCount = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM chat_message
			WHERE sender_member_id = ?
				AND client_message_id = ?
			""", Long.class, SELLER_ID, CLIENT_MESSAGE_ID_1);
		assertThat(first.created()).isTrue();
		assertThat(duplicate.created()).isFalse();
		assertThat(duplicate.messageId())
			.isEqualTo(new ChatMessageId(MESSAGE_ID_1));
		assertThat(storedCount).isEqualTo(1L);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void storesExactlyOnceForConcurrentDuplicateRequests() throws Exception {
		int requestCount = 2;
		ExecutorService executor = Executors.newFixedThreadPool(requestCount);
		CountDownLatch ready = new CountDownLatch(requestCount);
		CountDownLatch start = new CountDownLatch(1);

		try {
			Future<ChatMessagePersistenceResult> first = executor.submit(() ->
				saveAfterStart(
					message(
						MESSAGE_ID_1,
						CLIENT_MESSAGE_ID_1,
						SELLER_ID,
						false
					),
					ready,
					start
				)
			);
			Future<ChatMessagePersistenceResult> second = executor.submit(() ->
				saveAfterStart(
					message(
						MESSAGE_ID_2,
						CLIENT_MESSAGE_ID_1,
						SELLER_ID,
						false
					),
					ready,
					start
				)
			);

			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<ChatMessagePersistenceResult> results = List.of(
				first.get(10, TimeUnit.SECONDS),
				second.get(10, TimeUnit.SECONDS)
			);
			Long storedCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM chat_message
				WHERE sender_member_id = ?
					AND client_message_id = ?
				""", Long.class, SELLER_ID, CLIENT_MESSAGE_ID_1);

			assertThat(results)
				.extracting(ChatMessagePersistenceResult::created)
				.containsExactlyInAnyOrder(true, false);
			assertThat(results)
				.extracting(ChatMessagePersistenceResult::messageId)
				.containsOnly(results.getFirst().messageId());
			assertThat(storedCount).isEqualTo(1L);
		} finally {
			start.countDown();
			executor.shutdownNow();
			executor.awaitTermination(5, TimeUnit.SECONDS);
			deleteCommittedFixture();
		}
	}

	@Test
	void allowsSameClientMessageIdFromDifferentSenders() {
		ChatMessagePersistenceResult sellerMessage =
			persistence.saveIfAbsent(message(
				MESSAGE_ID_1,
				CLIENT_MESSAGE_ID_1,
				SELLER_ID,
				false
			));
		ChatMessagePersistenceResult participantMessage =
			persistence.saveIfAbsent(message(
				MESSAGE_ID_2,
				CLIENT_MESSAGE_ID_1,
				PARTICIPANT_ID,
				false
			));

		Long storedCount = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM chat_message
			WHERE client_message_id = ?
			""", Long.class, CLIENT_MESSAGE_ID_1);
		assertThat(sellerMessage.created()).isTrue();
		assertThat(participantMessage.created()).isTrue();
		assertThat(storedCount).isEqualTo(2L);
	}

	@Test
	void storesQuestionWithPendingQueueStatus() {
		persistence.saveIfAbsent(message(
			MESSAGE_ID_1,
			CLIENT_MESSAGE_ID_2,
			PARTICIPANT_ID,
			true
		));

		Map<String, Object> row = jdbcTemplate.queryForMap("""
			SELECT message_type, moderation_status, question_queue_status
			FROM chat_message
			WHERE message_id = ?
			""", MESSAGE_ID_1);
		assertThat(row)
			.containsEntry("message_type", "QUESTION")
			.containsEntry("moderation_status", "PENDING")
			.containsEntry("question_queue_status", "PENDING");
	}

	@Test
	void storesDuplicateQuestionOnlyOnce() {
		ChatMessage first = message(
			MESSAGE_ID_1,
			CLIENT_MESSAGE_ID_2,
			PARTICIPANT_ID,
			true
		);
		ChatMessage duplicate = message(
			MESSAGE_ID_2,
			CLIENT_MESSAGE_ID_2,
			PARTICIPANT_ID,
			true
		);

		ChatMessagePersistenceResult created = persistence.saveIfAbsent(first);
		ChatMessagePersistenceResult existing =
			persistence.saveIfAbsent(duplicate);

		Long storedCount = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM chat_message
			WHERE sender_member_id = ?
				AND client_message_id = ?
				AND question_queue_status = 'PENDING'
			""", Long.class, PARTICIPANT_ID, CLIENT_MESSAGE_ID_2);
		assertThat(created.created()).isTrue();
		assertThat(existing)
			.isEqualTo(ChatMessagePersistenceResult.existing(
				new ChatMessageId(MESSAGE_ID_1)
			));
		assertThat(storedCount).isEqualTo(1L);
	}

	@Test
	void returnsEmptyForUnknownIdempotencyKey() {
		assertThat(persistence.findMessageId(
			SELLER_ID,
			new ClientMessageId(CLIENT_MESSAGE_ID_1)
		)).isEmpty();
	}

	private static ChatMessage message(
		String messageId,
		String clientMessageId,
		long memberId,
		boolean isQuestion
	) {
		return new ChatMessage(
			new ChatMessageId(messageId),
			new ClientMessageId(clientMessageId),
			AUCTION_ID,
			memberId,
			memberId == SELLER_ID ? "멱등판매자" : "멱등참여자",
			new ChatMessageContent("연락처 ******"),
			isQuestion,
			Set.of(MaskingCategory.CONTACT),
			CREATED_AT
		);
	}

	private ChatMessagePersistenceResult saveAfterStart(
		ChatMessage message,
		CountDownLatch ready,
		CountDownLatch start
	) throws InterruptedException {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException(
				"concurrent persistence start timed out"
			);
		}

		TransactionTemplate transaction =
			new TransactionTemplate(transactionManager);
		return Objects.requireNonNull(
			transaction.execute(status -> persistence.saveIfAbsent(message)),
			"persistence result"
		);
	}

	private void deleteCommittedFixture() {
		jdbcTemplate.update(
			"DELETE FROM chat_message WHERE auction_room_id = ?",
			AUCTION_ID
		);
		jdbcTemplate.update(
			"DELETE FROM auction_room WHERE auction_room_id = ?",
			AUCTION_ID
		);
		jdbcTemplate.update(
			"DELETE FROM product WHERE product_id = ?",
			PRODUCT_ID
		);
		jdbcTemplate.update(
			"DELETE FROM product_category WHERE category_id = ?",
			CATEGORY_ID
		);
		jdbcTemplate.update(
			"DELETE FROM member WHERE member_id IN (?, ?)",
			SELLER_ID,
			PARTICIPANT_ID
		);
	}
}
