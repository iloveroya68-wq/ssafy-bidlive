package com.ssafy.bidlive.chat.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 채팅 외부 ID와 발신자별 클라이언트 메시지 멱등성 제약을 실제 PostgreSQL로 검증한다. */
@SpringBootTest
@Transactional
class ChatMessageIdempotencySchemaIntegrationTest {

	private static final long SELLER_ID = 910_001L;
	private static final long OTHER_MEMBER_ID = 910_002L;
	private static final long CATEGORY_ID = 920_001L;
	private static final long PRODUCT_ID = 930_001L;
	private static final long AUCTION_ID = 940_001L;

	private static final String MESSAGE_ID_1 = "01J8W3P2N9KQ4T123456789ABC";
	private static final String MESSAGE_ID_2 = "01J8W3P2N9KQ4T123456789ABD";
	private static final String CLIENT_MESSAGE_ID = "550e8400-e29b-41d4-a716-446655440000";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void insertFixture() {
		jdbcTemplate.update("""
			INSERT INTO member (member_id, nickname, role, status)
			VALUES
				(?, '채팅판매자', 'USER', 'ACTIVE'),
				(?, '채팅참여자', 'USER', 'ACTIVE')
			""", SELLER_ID, OTHER_MEMBER_ID);
		jdbcTemplate.update("""
			INSERT INTO product_category (category_id, code, name, is_active, display_order)
			VALUES (?, 'TEST_CHAT_IDEMPOTENCY', '채팅 멱등성 테스트', TRUE, 9001)
			""", CATEGORY_ID);
		jdbcTemplate.update("""
			INSERT INTO product (
				product_id, seller_member_id, category_id, title, description, moderation_status
			)
			VALUES (?, ?, ?, '채팅 테스트 상품', '채팅 스키마 검증용 상품', 'PASSED')
			""", PRODUCT_ID, SELLER_ID, CATEGORY_ID);
		jdbcTemplate.update("""
			INSERT INTO auction_room (
				auction_room_id, product_id, reserve_price, duration_minutes, start_type, status
			)
			VALUES (?, ?, 1000, 10, 'IMMEDIATE', 'WAITING')
			""", AUCTION_ID, PRODUCT_ID);
	}

	@Test
	void createsExternalIdColumnsWithExpectedNullabilityAndLength() {
		List<Map<String, Object>> columns = jdbcTemplate.queryForList("""
			SELECT column_name, character_maximum_length, is_nullable
			FROM information_schema.columns
			WHERE table_schema = 'public'
			  AND table_name = 'chat_message'
			  AND column_name IN ('message_id', 'client_message_id')
			ORDER BY column_name
			""");

		assertThat(columns)
			.extracting(
				row -> row.get("column_name"),
				row -> ((Number) row.get("character_maximum_length")).intValue(),
				row -> row.get("is_nullable")
			)
			.containsExactly(
				org.assertj.core.groups.Tuple.tuple("client_message_id", 36, "YES"),
				org.assertj.core.groups.Tuple.tuple("message_id", 26, "NO")
			);
	}

	@Test
	void rejectsDuplicateServerMessageId() {
		insertChatMessage(MESSAGE_ID_1, CLIENT_MESSAGE_ID, SELLER_ID, "첫 메시지");

		assertThatThrownBy(() ->
			insertChatMessage(MESSAGE_ID_1, null, OTHER_MEMBER_ID, "중복 서버 ID")
		).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsDuplicateClientMessageIdFromSameSender() {
		insertChatMessage(MESSAGE_ID_1, CLIENT_MESSAGE_ID, SELLER_ID, "첫 전송");

		assertThatThrownBy(() ->
			insertChatMessage(MESSAGE_ID_2, CLIENT_MESSAGE_ID, SELLER_ID, "재전송")
		).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void allowsSameClientMessageIdFromDifferentSenders() {
		insertChatMessage(MESSAGE_ID_1, CLIENT_MESSAGE_ID, SELLER_ID, "판매자 메시지");
		insertChatMessage(MESSAGE_ID_2, CLIENT_MESSAGE_ID, OTHER_MEMBER_ID, "참여자 메시지");

		Long count = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM chat_message
			WHERE client_message_id = ?
			""", Long.class, CLIENT_MESSAGE_ID);

		assertThat(count).isEqualTo(2L);
	}

	@Test
	void allowsMultipleMessagesWithoutClientMessageId() {
		insertChatMessage(MESSAGE_ID_1, null, SELLER_ID, "클라이언트 ID 없는 메시지 1");
		insertChatMessage(MESSAGE_ID_2, null, SELLER_ID, "클라이언트 ID 없는 메시지 2");

		Long count = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM chat_message
			WHERE sender_member_id = ?
			  AND client_message_id IS NULL
			""", Long.class, SELLER_ID);

		assertThat(count).isEqualTo(2L);
	}

	@Test
	void rollbackScriptRemovesAddedColumnsAndPreservesExistingMessage() throws IOException {
		insertChatMessage(MESSAGE_ID_1, CLIENT_MESSAGE_ID, SELLER_ID, "롤백 후에도 남을 메시지");

		Path rollbackScript = Path.of(
			"docs",
			"db",
			"rollback",
			"V19__rollback_chat_message_external_ids.sql"
		);
		jdbcTemplate.execute(Files.readString(rollbackScript));

		Integer addedColumnCount = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM information_schema.columns
			WHERE table_schema = 'public'
			  AND table_name = 'chat_message'
			  AND column_name IN ('message_id', 'client_message_id')
			""", Integer.class);
		String content = jdbcTemplate.queryForObject("""
			SELECT content
			FROM chat_message
			WHERE auction_room_id = ?
			  AND sender_member_id = ?
			""", String.class, AUCTION_ID, SELLER_ID);

		assertThat(addedColumnCount).isZero();
		assertThat(content).isEqualTo("롤백 후에도 남을 메시지");
	}

	private void insertChatMessage(
		String messageId,
		String clientMessageId,
		long senderMemberId,
		String content
	) {
		jdbcTemplate.update("""
			INSERT INTO chat_message (
				message_id,
				client_message_id,
				auction_room_id,
				sender_member_id,
				message_type,
				content,
				moderation_status
			)
			VALUES (?, ?, ?, ?, 'CHAT', ?, 'PASSED')
			""", messageId, clientMessageId, AUCTION_ID, senderMemberId, content);
	}
}
