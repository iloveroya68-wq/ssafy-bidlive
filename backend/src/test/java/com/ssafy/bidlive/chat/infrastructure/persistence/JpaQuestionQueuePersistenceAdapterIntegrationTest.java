package com.ssafy.bidlive.chat.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.ssafy.bidlive.chat.application.QuestionQueueCursor;
import com.ssafy.bidlive.chat.application.QuestionQueuePersistence;
import com.ssafy.bidlive.chat.application.QuestionResolutionResult;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.QuestionQueueItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 PostgreSQL에서 PENDING 필터와 생성 시각·ULID 커서 정렬을 검증한다. */
@SpringBootTest
@Transactional
class JpaQuestionQueuePersistenceAdapterIntegrationTest {

	private static final long SELLER_ID = 961_001L;
	private static final long PARTICIPANT_ID = 961_002L;
	private static final long CATEGORY_ID = 962_001L;
	private static final long PRODUCT_ID = 963_001L;
	private static final long AUCTION_ID = 964_001L;
	private static final ChatMessageId QUESTION_ID_1 =
		new ChatMessageId("01J8W3P2N9KQ4T123456789ABC");
	private static final ChatMessageId QUESTION_ID_2 =
		new ChatMessageId("01J8W3P2N9KQ4T123456789ABD");
	private static final ChatMessageId QUESTION_ID_3 =
		new ChatMessageId("01J8W3P2N9KQ4T123456789ABE");
	private static final ChatMessageId QUESTION_ID_4 =
		new ChatMessageId("01J8W3P2N9KQ4T123456789ABF");
	private static final Instant FIRST_CREATED_AT =
		Instant.parse("2026-07-31T04:00:00Z");

	@Autowired
	private QuestionQueuePersistence persistence;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeEach
	void insertFixture() {
		jdbcTemplate.update("""
			INSERT INTO member (member_id, nickname, role, status)
			VALUES
				(?, '질문큐판매자', 'USER', 'ACTIVE'),
				(?, '질문큐참여자', 'USER', 'ACTIVE')
			""", SELLER_ID, PARTICIPANT_ID);
		jdbcTemplate.update("""
			INSERT INTO product_category (
				category_id, code, name, is_active, display_order
			)
			VALUES (?, 'TEST_QUESTION_QUEUE', '질문 큐 테스트', TRUE, 9201)
			""", CATEGORY_ID);
		jdbcTemplate.update("""
			INSERT INTO product (
				product_id, seller_member_id, category_id,
				title, description, moderation_status
			)
			VALUES (?, ?, ?, '질문 큐 상품', '질문 큐 테스트 상품', 'PASSED')
			""", PRODUCT_ID, SELLER_ID, CATEGORY_ID);
		jdbcTemplate.update("""
			INSERT INTO auction_room (
				auction_room_id, product_id, reserve_price,
				duration_minutes, start_type, status
			)
			VALUES (?, ?, 1000, 10, 'IMMEDIATE', 'LIVE')
			""", AUCTION_ID, PRODUCT_ID);

		insertMessage(QUESTION_ID_1, "00000000-0000-7000-8000-000000000001", "QUESTION", "PENDING", false, FIRST_CREATED_AT);
		insertMessage(QUESTION_ID_2, "00000000-0000-7000-8000-000000000002", "QUESTION", "PENDING", false, FIRST_CREATED_AT);
		insertMessage(QUESTION_ID_3, "00000000-0000-7000-8000-000000000003", "QUESTION", "RESOLVED", false, FIRST_CREATED_AT.plusSeconds(1));
		insertMessage(QUESTION_ID_4, "00000000-0000-7000-8000-000000000004", "QUESTION", "PENDING", false, FIRST_CREATED_AT.plusSeconds(2));
		insertMessage(
			new ChatMessageId("01J8W3P2N9KQ4T123456789ABG"),
			"00000000-0000-7000-8000-000000000005",
			"CHAT",
			null,
			false,
			FIRST_CREATED_AT.plusSeconds(3)
		);
		insertMessage(
			new ChatMessageId("01J8W3P2N9KQ4T123456789ABH"),
			"00000000-0000-7000-8000-000000000006",
			"QUESTION",
			"PENDING",
			true,
			FIRST_CREATED_AT.plusSeconds(4)
		);
	}

	@Test
	void findsPendingQuestionsInStableCreatedAtAndUlidOrder() {
		List<QuestionQueueItem> items = persistence.findPendingAfter(
			AUCTION_ID,
			Optional.empty(),
			10
		);

		assertThat(items)
			.extracting(QuestionQueueItem::questionId)
			.containsExactly(QUESTION_ID_1, QUESTION_ID_2, QUESTION_ID_4);
		assertThat(items.getFirst().nickname()).isEqualTo("질문큐참여자");
		assertThat(items.getFirst().memberId()).isEqualTo(PARTICIPANT_ID);
	}

	@Test
	void findsNextPageAfterSameTimestampCursor() {
		QuestionQueueCursor cursor = persistence.findCursor(
			AUCTION_ID,
			QUESTION_ID_1
		).orElseThrow();

		List<QuestionQueueItem> items = persistence.findPendingAfter(
			AUCTION_ID,
			Optional.of(cursor),
			10
		);

		assertThat(items)
			.extracting(QuestionQueueItem::questionId)
			.containsExactly(QUESTION_ID_2, QUESTION_ID_4);
	}

	@Test
	void resolvedQuestionRemainsAValidCursorAnchor() {
		QuestionQueueCursor cursor = persistence.findCursor(
			AUCTION_ID,
			QUESTION_ID_3
		).orElseThrow();

		assertThat(persistence.findPendingAfter(
			AUCTION_ID,
			Optional.of(cursor),
			10
		)).extracting(QuestionQueueItem::questionId)
			.containsExactly(QUESTION_ID_4);
	}

	@Test
	void rejectsUnknownOrOtherAuctionCursorAtPersistenceBoundary() {
		assertThat(persistence.findCursor(
			AUCTION_ID,
			new ChatMessageId("01J8W3P2N9KQ4T123456789ABJ")
		)).isEmpty();
		assertThat(persistence.findCursor(999_999L, QUESTION_ID_1)).isEmpty();
	}

	@Test
	void resolvesPendingQuestionOnlyOnce() {
		QuestionResolutionResult first = persistence.resolvePending(
			AUCTION_ID,
			QUESTION_ID_1
		);
		QuestionResolutionResult second = persistence.resolvePending(
			AUCTION_ID,
			QUESTION_ID_1
		);

		String status = jdbcTemplate.queryForObject("""
			SELECT question_queue_status
			FROM chat_message
			WHERE message_id = ?
			""", String.class, QUESTION_ID_1.value());
		assertThat(first).isEqualTo(QuestionResolutionResult.RESOLVED);
		assertThat(second)
			.isEqualTo(QuestionResolutionResult.ALREADY_HANDLED);
		assertThat(status).isEqualTo("RESOLVED");
	}

	@Test
	void treatsFilteredQuestionAsAlreadyHandled() {
		jdbcTemplate.update("""
			UPDATE chat_message
			SET question_queue_status = 'FILTERED'
			WHERE message_id = ?
			""", QUESTION_ID_3.value());

		assertThat(persistence.resolvePending(AUCTION_ID, QUESTION_ID_3))
			.isEqualTo(QuestionResolutionResult.ALREADY_HANDLED);
	}

	@Test
	void returnsNotFoundForUnknownOrOtherAuctionQuestion() {
		ChatMessageId unknown =
			new ChatMessageId("01J8W3P2N9KQ4T123456789ABJ");

		assertThat(persistence.resolvePending(AUCTION_ID, unknown))
			.isEqualTo(QuestionResolutionResult.NOT_FOUND);
		assertThat(persistence.resolvePending(999_999L, QUESTION_ID_1))
			.isEqualTo(QuestionResolutionResult.NOT_FOUND);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void permitsExactlyOneConcurrentResolution() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		try {
			Future<QuestionResolutionResult> first = executor.submit(() ->
				resolveAfterStart(ready, start)
			);
			Future<QuestionResolutionResult> second = executor.submit(() ->
				resolveAfterStart(ready, start)
			);
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			assertThat(List.of(
				first.get(10, TimeUnit.SECONDS),
				second.get(10, TimeUnit.SECONDS)
			)).containsExactlyInAnyOrder(
				QuestionResolutionResult.RESOLVED,
				QuestionResolutionResult.ALREADY_HANDLED
			);
		} finally {
			start.countDown();
			executor.shutdownNow();
			executor.awaitTermination(5, TimeUnit.SECONDS);
			deleteCommittedFixture();
		}
	}

	private void insertMessage(
		ChatMessageId messageId,
		String clientMessageId,
		String messageType,
		String questionQueueStatus,
		boolean deleted,
		Instant createdAt
	) {
		jdbcTemplate.update("""
			INSERT INTO chat_message (
				message_id,
				client_message_id,
				auction_room_id,
				sender_member_id,
				message_type,
				content,
				moderation_status,
				question_queue_status,
				is_deleted,
				created_at
			)
			VALUES (?, ?, ?, ?, ?, '흠집을 보여주세요', 'PENDING', ?, ?, ?)
			""",
			messageId.value(),
			clientMessageId,
			AUCTION_ID,
			PARTICIPANT_ID,
			messageType,
			questionQueueStatus,
			deleted,
			Timestamp.from(createdAt)
		);
	}

	private QuestionResolutionResult resolveAfterStart(
		CountDownLatch ready,
		CountDownLatch start
	) throws InterruptedException {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException("question resolution start timed out");
		}
		TransactionTemplate transaction =
			new TransactionTemplate(transactionManager);
		return Objects.requireNonNull(transaction.execute(status ->
			persistence.resolvePending(AUCTION_ID, QUESTION_ID_1)
		));
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
