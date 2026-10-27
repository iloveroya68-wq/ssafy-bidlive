package com.ssafy.bidlive.chat.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;

import com.ssafy.bidlive.chat.application.ChatMessageDeletionCommand;
import com.ssafy.bidlive.chat.application.ChatMessageDeletionPersistence;
import com.ssafy.bidlive.chat.application.ChatMessageDeletionResult;
import com.ssafy.bidlive.chat.domain.ChatMessageDeletionReason;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL에서 채팅 소프트 삭제·감사 정보·연계 상태의 멱등 변경을 검증한다. */
@SpringBootTest
@Transactional
class JdbcChatMessageDeletionPersistenceAdapterIntegrationTest {

	private static final long SELLER_ID = 971_001L;
	private static final long MEMBER_ID = 971_002L;
	private static final long CATEGORY_ID = 972_001L;
	private static final long PRODUCT_ID = 973_001L;
	private static final long AUCTION_ID = 974_001L;
	private static final ChatMessageId QUESTION_ID = new ChatMessageId(
		"01J8W3P2N9KQ4T123456789ABK"
	);
	private static final ChatMessageId CHAT_ID = new ChatMessageId(
		"01J8W3P2N9KQ4T123456789ABM"
	);
	private static final Instant DELETED_AT =
		Instant.parse("2026-08-02T01:00:00Z");

	@Autowired
	private ChatMessageDeletionPersistence persistence;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void insertFixture() {
		jdbcTemplate.update("""
			INSERT INTO member (member_id, nickname, role, status)
			VALUES
				(?, '삭제판매자', 'USER', 'ACTIVE'),
				(?, '삭제참여자', 'USER', 'ACTIVE')
			""", SELLER_ID, MEMBER_ID);
		jdbcTemplate.update("""
			INSERT INTO product_category (
				category_id, code, name, is_active, display_order
			)
			VALUES (?, 'TEST_CHAT_DELETE', '채팅 삭제 테스트', TRUE, 9301)
			""", CATEGORY_ID);
		jdbcTemplate.update("""
			INSERT INTO product (
				product_id, seller_member_id, category_id,
				title, description, moderation_status
			)
			VALUES (?, ?, ?, '채팅 삭제 상품', '삭제 테스트', 'PASSED')
			""", PRODUCT_ID, SELLER_ID, CATEGORY_ID);
		jdbcTemplate.update("""
			INSERT INTO auction_room (
				auction_room_id, product_id, reserve_price,
				duration_minutes, start_type, status
			)
			VALUES (?, ?, 1000, 10, 'IMMEDIATE', 'LIVE')
			""", AUCTION_ID, PRODUCT_ID);
		insertMessage(QUESTION_ID, "QUESTION", "PENDING", true);
		insertMessage(CHAT_ID, "CHAT", null, false);
	}

	@Test
	void softDeletesPinnedQuestionWithAuditAndIsIdempotent() {
		ChatMessageDeletionCommand command = command(
			QUESTION_ID,
			SELLER_ID,
			ChatMessageDeletionReason.SELLER_DELETED
		);

		ChatMessageDeletionResult first = persistence.delete(command);
		ChatMessageDeletionResult second = persistence.delete(command);

		assertThat(first).isEqualTo(
			ChatMessageDeletionResult.deleted(true, true)
		);
		assertThat(second).isEqualTo(
			ChatMessageDeletionResult.alreadyDeleted()
		);
		assertThat(jdbcTemplate.queryForMap("""
			SELECT
				is_deleted,
				is_pinned,
				deleted_at,
				deleted_by_member_id,
				deletion_reason,
				question_queue_status
			FROM chat_message
			WHERE message_id = ?
			""", QUESTION_ID.value()))
			.containsEntry("is_deleted", true)
			.containsEntry("is_pinned", false)
			.containsEntry("deleted_by_member_id", SELLER_ID)
			.containsEntry("deletion_reason", "SELLER_DELETED")
			.containsEntry("question_queue_status", "PENDING");
	}

	@Test
	void moderationDeletionBlocksMessageAndFiltersPendingQuestion() {
		ChatMessageDeletionResult result = persistence.delete(command(
			QUESTION_ID,
			null,
			ChatMessageDeletionReason.MODERATION_BLOCKED
		));

		assertThat(result).isEqualTo(
			ChatMessageDeletionResult.deleted(true, true)
		);
		assertThat(jdbcTemplate.queryForMap("""
			SELECT
				moderation_status,
				question_queue_status,
				deleted_by_member_id,
				deletion_reason
			FROM chat_message
			WHERE message_id = ?
			""", QUESTION_ID.value()))
			.containsEntry("moderation_status", "BLOCKED")
			.containsEntry("question_queue_status", "FILTERED")
			.containsEntry("deleted_by_member_id", null)
			.containsEntry("deletion_reason", "MODERATION_BLOCKED");
	}

	@Test
	void questionFilteringDeletesChatWithoutMarkingItBlocked() {
		ChatMessageDeletionResult result = persistence.delete(command(
			QUESTION_ID,
			null,
			ChatMessageDeletionReason.QUESTION_FILTERED
		));

		assertThat(result).isEqualTo(
			ChatMessageDeletionResult.deleted(true, true)
		);
		assertThat(jdbcTemplate.queryForMap("""
			SELECT
				is_deleted,
				moderation_status,
				question_queue_status,
				deletion_reason
			FROM chat_message
			WHERE message_id = ?
			""", QUESTION_ID.value()))
			.containsEntry("is_deleted", true)
			.containsEntry("moderation_status", "PASSED")
			.containsEntry("question_queue_status", "FILTERED")
			.containsEntry("deletion_reason", "QUESTION_FILTERED");
	}

	@Test
	void distinguishesUnknownAndOtherAuctionMessage() {
		assertThat(persistence.delete(command(
			new ChatMessageId("01J8W3P2N9KQ4T123456789ABN"),
			SELLER_ID,
			ChatMessageDeletionReason.SELLER_DELETED
		))).isEqualTo(ChatMessageDeletionResult.notFound());

		ChatMessageDeletionCommand otherAuction =
			new ChatMessageDeletionCommand(
				AUCTION_ID + 1,
				CHAT_ID,
				SELLER_ID,
				ChatMessageDeletionReason.SELLER_DELETED,
				DELETED_AT
			);
		assertThat(persistence.delete(otherAuction))
			.isEqualTo(ChatMessageDeletionResult.notFound());
	}

	private void insertMessage(
		ChatMessageId messageId,
		String messageType,
		String questionStatus,
		boolean pinned
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
				is_pinned,
				question_queue_status,
				created_at
			)
			VALUES (?, ?, ?, ?, ?, '삭제 대상', 'PENDING', ?, ?, ?)
			""",
			messageId.value(),
			"00000000-0000-7000-8000-" + messageId.value().substring(14),
			AUCTION_ID,
			MEMBER_ID,
			messageType,
			pinned,
			questionStatus,
			Timestamp.from(Instant.parse("2026-08-02T00:00:00Z"))
		);
	}

	private static ChatMessageDeletionCommand command(
		ChatMessageId messageId,
		Long memberId,
		ChatMessageDeletionReason reason
	) {
		return new ChatMessageDeletionCommand(
			AUCTION_ID,
			messageId,
			memberId,
			reason,
			DELETED_AT
		);
	}
}
