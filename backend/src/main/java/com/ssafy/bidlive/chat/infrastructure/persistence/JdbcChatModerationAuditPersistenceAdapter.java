package com.ssafy.bidlive.chat.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;

import com.ssafy.bidlive.chat.application.ChatModerationAuditEntry;
import com.ssafy.bidlive.chat.application.ChatModerationAuditPersistence;
import com.ssafy.bidlive.chat.application.EncryptedChatContent;
import com.ssafy.bidlive.chat.domain.MaskingCategory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 암호화된 검열 감사 레코드를 PostgreSQL에 멱등 저장하고 만료분을 삭제합니다. */
@Repository
public class JdbcChatModerationAuditPersistenceAdapter
	implements ChatModerationAuditPersistence {

	private static final String INSERT_SQL = """
		INSERT INTO chat_moderation_log (
			message_id,
			auction_room_id,
			member_id,
			encrypted_original_content,
			encryption_iv,
			encryption_key_version,
			original_content_hash,
			masked_fields,
			created_at,
			expires_at
		)
		VALUES (
			:messageId,
			:auctionId,
			:memberId,
			:ciphertext,
			:iv,
			:keyVersion,
			:contentHash,
			:maskedFields,
			:createdAt,
			:expiresAt
		)
		ON CONFLICT (message_id) DO NOTHING
		""";

	private static final String DELETE_EXPIRED_SQL = """
		DELETE FROM chat_moderation_log
		WHERE expires_at <= :now
		""";

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public JdbcChatModerationAuditPersistenceAdapter(
		NamedParameterJdbcTemplate jdbcTemplate
	) {
		this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
	}

	@Override
	public void saveIfAbsent(ChatModerationAuditEntry entry) {
		Objects.requireNonNull(entry, "entry");
		EncryptedChatContent encrypted = entry.originalContent();
		jdbcTemplate.update(
			INSERT_SQL,
			new MapSqlParameterSource()
				.addValue("messageId", entry.context().messageId().value())
				.addValue("auctionId", entry.context().auctionId())
				.addValue("memberId", entry.context().memberId())
				.addValue("ciphertext", encrypted.ciphertext())
				.addValue("iv", encrypted.iv())
				.addValue("keyVersion", encrypted.keyVersion())
				.addValue("contentHash", entry.originalContentHash())
				.addValue("maskedFields", maskedFields(entry))
				.addValue("createdAt", Timestamp.from(entry.createdAt()))
				.addValue("expiresAt", Timestamp.from(entry.expiresAt()))
		);
	}

	@Override
	public int deleteExpired(Instant now) {
		Objects.requireNonNull(now, "now");
		return jdbcTemplate.update(
			DELETE_EXPIRED_SQL,
			new MapSqlParameterSource("now", Timestamp.from(now))
		);
	}

	private static String maskedFields(ChatModerationAuditEntry entry) {
		return entry.maskedFields().stream()
			.sorted(Comparator.comparing(Enum::name))
			.map(MaskingCategory::name)
			.collect(Collectors.joining(","));
	}
}
