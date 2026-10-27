package com.ssafy.bidlive.chat.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import com.ssafy.bidlive.chat.application.ChatModerationJob;
import com.ssafy.bidlive.chat.application.ChatModerationJobQueue;
import com.ssafy.bidlive.chat.application.ChatLlmFailureCode;
import com.ssafy.bidlive.chat.application.ClaimedChatModerationJob;
import com.ssafy.bidlive.chat.application.ChatModerationDecision;
import com.ssafy.bidlive.chat.application.ChatModerationOutcomePersistence;
import com.ssafy.bidlive.chat.application.ModerationOutcomeApplyResult;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL 검열 작업 등록의 messageId 멱등성과 저장 필드를 검증합니다. */
@SpringBootTest
@Transactional
class JdbcChatModerationJobQueueAdapterIntegrationTest {

	private static final long MEMBER_ID = 981_001L;
	private static final long CATEGORY_ID = 982_001L;
	private static final long PRODUCT_ID = 983_001L;
	private static final long AUCTION_ID = 984_001L;
	private static final String MESSAGE_ID =
		"01J8W3P2N9KQ4T123456789ABC";

	@Autowired
	private ChatModerationJobQueue queue;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ChatModerationOutcomePersistence outcomePersistence;

	@BeforeEach
	void insertFixture() {
		jdbcTemplate.update("""
			INSERT INTO member (member_id, nickname, role, status)
			VALUES (?, '검열사용자', 'USER', 'ACTIVE')
			""", MEMBER_ID);
		jdbcTemplate.update("""
			INSERT INTO product_category
				(category_id, code, name, is_active, display_order)
			VALUES (?, 'TEST_ASYNC_MODERATION', '검열 테스트', TRUE, 9401)
			""", CATEGORY_ID);
		jdbcTemplate.update("""
			INSERT INTO product
				(product_id, seller_member_id, category_id, title,
				description, moderation_status)
			VALUES (?, ?, ?, '검열 상품', '검열 테스트', 'PASSED')
			""", PRODUCT_ID, MEMBER_ID, CATEGORY_ID);
		jdbcTemplate.update("""
			INSERT INTO auction_room
				(auction_room_id, product_id, reserve_price,
				duration_minutes, start_type, status)
			VALUES (?, ?, 1000, 10, 'IMMEDIATE', 'LIVE')
			""", AUCTION_ID, PRODUCT_ID);
		jdbcTemplate.update("""
			INSERT INTO chat_message (
				message_id, auction_room_id, sender_member_id, client_message_id,
				content, message_type, moderation_status, is_deleted,
				created_at
			)
			VALUES (?, ?, ?, '018f6f4e-2a61-7c00-a1b2-334455667788',
				'마스킹된 본문', 'CHAT', 'PENDING', FALSE,
				CURRENT_TIMESTAMP)
			""", MESSAGE_ID, AUCTION_ID, MEMBER_ID);
	}

	@Test
	void enqueuesSameMessageExactlyOnce() {
		ChatModerationJob job = job();

		assertThat(queue.enqueue(job)).isTrue();
		assertThat(queue.enqueue(job)).isFalse();
		assertThat(jdbcTemplate.queryForMap("""
			SELECT filtered_content, status, attempt_count
			FROM chat_moderation_job
			WHERE message_id = ?
			""", MESSAGE_ID))
			.containsEntry("filtered_content", "마스킹된 본문")
			.containsEntry("status", "PENDING")
			.containsEntry("attempt_count", 0);
	}

	@Test
	void claimsRetriesRecoversLeaseAndCompletes() {
		Instant now = Instant.parse("2026-08-02T01:00:00Z");
		queue.enqueue(job());

		ClaimedChatModerationJob first = queue.claimDue(
			now,
			Duration.ofMinutes(1),
			3
		).orElseThrow();
		assertThat(first.attemptCount()).isEqualTo(1);
		assertThat(first.job().roomTitle()).isEqualTo("검열 상품 경매");
		assertThat(first.job().productDescription())
			.isEqualTo("상품 설명과 사용 상태");
		assertThat(first.job().productCategoryCode())
			.isEqualTo("TEST_ASYNC_MODERATION");
		assertThat(queue.claimDue(
			now.plusSeconds(30),
			Duration.ofMinutes(1),
			3
		)).isEmpty();

		ClaimedChatModerationJob recovered = queue.claimDue(
			now.plusSeconds(61),
			Duration.ofMinutes(1),
			3
		).orElseThrow();
		assertThat(recovered.attemptCount()).isEqualTo(2);
		queue.retry(
			recovered.jobId(),
			now.plusSeconds(70),
			ChatLlmFailureCode.LLM_TIMEOUT,
			now.plusSeconds(61)
		);
		assertThat(queue.claimDue(
			now.plusSeconds(69),
			Duration.ofMinutes(1),
			3
		)).isEmpty();

		ClaimedChatModerationJob finalAttempt = queue.claimDue(
			now.plusSeconds(70),
			Duration.ofMinutes(1),
			3
		).orElseThrow();
		assertThat(finalAttempt.attemptCount()).isEqualTo(3);
		queue.succeed(finalAttempt.jobId(), now.plusSeconds(70));
		assertThat(jdbcTemplate.queryForMap("""
			SELECT status, attempt_count, last_error_code
			FROM chat_moderation_job WHERE message_id = ?
			""", MESSAGE_ID))
			.containsEntry("status", "SUCCEEDED")
			.containsEntry("attempt_count", 3)
			.containsEntry("last_error_code", null);
	}

	@Test
	void deadLettersWithoutRetainingMemberOrFilteredContent() {
		Instant now = Instant.parse("2026-08-02T01:00:00Z");
		queue.enqueue(job());
		ClaimedChatModerationJob claimed = queue.claimDue(
			now,
			Duration.ofMinutes(1),
			3
		).orElseThrow();

		queue.deadLetter(
			claimed.jobId(),
			ChatLlmFailureCode.LLM_RESPONSE_INVALID,
			now
		);

		assertRedactedDeadLetter(
			"LLM_RESPONSE_INVALID",
			1
		);
	}

	@Test
	void recoversExhaustedLeaseWithoutRetainingSensitiveJobData() {
		Instant now = Instant.parse("2026-08-02T01:00:00Z");
		queue.enqueue(job());
		queue.claimDue(now, Duration.ofMinutes(1), 3).orElseThrow();
		queue.claimDue(
			now.plusSeconds(61),
			Duration.ofMinutes(1),
			3
		).orElseThrow();
		queue.claimDue(
			now.plusSeconds(122),
			Duration.ofMinutes(1),
			3
		).orElseThrow();

		int recovered = queue.recoverExhaustedLeases(
			now.plusSeconds(183),
			3,
			now.plusSeconds(184)
		);

		assertThat(recovered).isEqualTo(1);
		assertRedactedDeadLetter(
			"LLM_RETRY_EXHAUSTED",
			3
		);
	}

	@Test
	void appliesNonBlockedOutcomeOnlyOnce() {
		ChatModerationJob job = job();

		assertThat(outcomePersistence.apply(
			job,
			ChatModerationDecision.WARNING
		)).isEqualTo(ModerationOutcomeApplyResult.changed(false));
		assertThat(outcomePersistence.apply(
			job,
			ChatModerationDecision.WARNING
		)).isEqualTo(ModerationOutcomeApplyResult.unchanged());
		assertThat(jdbcTemplate.queryForMap("""
			SELECT moderation_status, moderation_reason
			FROM chat_message WHERE message_id = ?
			""", MESSAGE_ID))
			.containsEntry("moderation_status", "PASSED")
			.containsEntry("moderation_reason", "WARNING");
	}

	private ChatModerationJob job() {
		return new ChatModerationJob(
			AUCTION_ID,
			new ChatMessageId(MESSAGE_ID),
			MEMBER_ID,
			"마스킹된 본문",
			true,
			"검열 상품 경매",
			"상품 설명과 사용 상태",
			"TEST_ASYNC_MODERATION",
			Instant.parse("2026-08-02T01:00:00Z")
		);
	}

	private void assertRedactedDeadLetter(
		String errorCode,
		int attemptCount
	) {
		assertThat(jdbcTemplate.queryForMap("""
			SELECT message_id, member_id, filtered_content,
				room_title, product_description, product_category_code, status,
				attempt_count, last_error_code
			FROM chat_moderation_job
			WHERE message_id = ?
			""", MESSAGE_ID))
			.containsEntry("message_id", MESSAGE_ID)
			.containsEntry("member_id", null)
			.containsEntry("filtered_content", null)
			.containsEntry("room_title", null)
			.containsEntry("product_description", null)
			.containsEntry("product_category_code", null)
			.containsEntry("status", "DEAD_LETTER")
			.containsEntry("attempt_count", attemptCount)
			.containsEntry("last_error_code", errorCode);
	}
}
