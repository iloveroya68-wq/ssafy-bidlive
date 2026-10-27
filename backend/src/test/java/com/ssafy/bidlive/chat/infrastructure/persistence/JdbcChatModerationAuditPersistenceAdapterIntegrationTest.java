package com.ssafy.bidlive.chat.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import com.ssafy.bidlive.chat.application.ChatModerationAuditContext;
import com.ssafy.bidlive.chat.application.ChatModerationAuditEntry;
import com.ssafy.bidlive.chat.application.ChatModerationAuditPersistence;
import com.ssafy.bidlive.chat.application.EncryptedChatContent;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.MaskingCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL 감사 레코드의 암호문 저장·멱등성·보존기간 삭제를 검증합니다. */
@SpringBootTest
@Transactional
class JdbcChatModerationAuditPersistenceAdapterIntegrationTest {

	private static final long MEMBER_ID = 991_001L;
	private static final long CATEGORY_ID = 992_001L;
	private static final long PRODUCT_ID = 993_001L;
	private static final long AUCTION_ID = 994_001L;
	private static final ChatMessageId MESSAGE_ID = new ChatMessageId(
		"01J8W3P2N9KQ4T123456789ACP"
	);
	private static final ChatMessageId SECOND_MESSAGE_ID = new ChatMessageId(
		"01J8W3P2N9KQ4T123456789ACQ"
	);
	private static final Instant CREATED_AT =
		Instant.parse("2026-08-02T01:00:00Z");

	@Autowired
	private ChatModerationAuditPersistence persistence;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void insertFixture() {
		jdbcTemplate.update("""
			INSERT INTO member (member_id, nickname, role, status)
			VALUES (?, '감사사용자', 'USER', 'ACTIVE')
			""", MEMBER_ID);
		jdbcTemplate.update("""
			INSERT INTO product_category
				(category_id, code, name, is_active, display_order)
			VALUES (?, 'TEST_MODERATION_AUDIT', '감사 테스트', TRUE, 9501)
			""", CATEGORY_ID);
		jdbcTemplate.update("""
			INSERT INTO product
				(product_id, seller_member_id, category_id, title,
				description, moderation_status)
			VALUES (?, ?, ?, '감사 상품', '감사 테스트', 'PASSED')
			""", PRODUCT_ID, MEMBER_ID, CATEGORY_ID);
		jdbcTemplate.update("""
			INSERT INTO auction_room
				(auction_room_id, product_id, reserve_price,
				duration_minutes, start_type, status)
			VALUES (?, ?, 1000, 10, 'IMMEDIATE', 'LIVE')
			""", AUCTION_ID, PRODUCT_ID);
		insertMessage(MESSAGE_ID);
		insertMessage(SECOND_MESSAGE_ID);
	}

	@Test
	void storesEncryptedMetadataOnceWithoutPlaintextColumn() {
		ChatModerationAuditEntry entry = entry(
			MESSAGE_ID,
			CREATED_AT.plusSeconds(60)
		);

		persistence.saveIfAbsent(entry);
		persistence.saveIfAbsent(entry);

		assertThat(jdbcTemplate.queryForObject("""
			SELECT COUNT(*) FROM chat_moderation_log WHERE message_id = ?
			""", Integer.class, MESSAGE_ID.value())).isEqualTo(1);
		Map<String, Object> stored = jdbcTemplate.queryForMap("""
			SELECT
				encrypted_original_content,
				encryption_iv,
				encryption_key_version,
				original_content_hash,
				masked_fields
			FROM chat_moderation_log
			WHERE message_id = ?
			""", MESSAGE_ID.value());
		assertThat(stored)
			.containsEntry("encrypted_original_content", "ciphertext-base64")
			.containsEntry("encryption_iv", "iv-base64")
			.containsEntry("encryption_key_version", "v1")
			.containsEntry("original_content_hash", "a".repeat(64))
			.containsEntry("masked_fields", "ACCOUNT,CONTACT");
	}

	@Test
	void deletesOnlyRecordsAtOrBeforeRetentionDeadline() {
		persistence.saveIfAbsent(entry(
			MESSAGE_ID,
			CREATED_AT.plusSeconds(30)
		));
		persistence.saveIfAbsent(entry(
			SECOND_MESSAGE_ID,
			CREATED_AT.plusSeconds(90)
		));

		int deleted = persistence.deleteExpired(CREATED_AT.plusSeconds(30));

		assertThat(deleted).isEqualTo(1);
		assertThat(jdbcTemplate.queryForList("""
			SELECT message_id FROM chat_moderation_log ORDER BY message_id
			""", String.class)).containsExactly(SECOND_MESSAGE_ID.value());
	}

	private void insertMessage(ChatMessageId messageId) {
		jdbcTemplate.update("""
			INSERT INTO chat_message (
				message_id, auction_room_id, sender_member_id, client_message_id,
				content, message_type, moderation_status, is_deleted,
				created_at
			)
			VALUES (?, ?, ?, ?, '마스킹된 본문', 'CHAT', 'PENDING', FALSE,
				CURRENT_TIMESTAMP)
			""",
			messageId.value(),
			AUCTION_ID,
			MEMBER_ID,
			"00000000-0000-7000-8000-" + messageId.value().substring(14)
		);
	}

	private static ChatModerationAuditEntry entry(
		ChatMessageId messageId,
		Instant expiresAt
	) {
		return new ChatModerationAuditEntry(
			new ChatModerationAuditContext(messageId, AUCTION_ID, MEMBER_ID),
			new EncryptedChatContent(
				"ciphertext-base64",
				"iv-base64",
				"v1"
			),
			"a".repeat(64),
			Set.of(MaskingCategory.CONTACT, MaskingCategory.ACCOUNT),
			CREATED_AT,
			expiresAt
		);
	}
}
