package com.ssafy.bidlive.chat.infrastructure.persistence;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.Optional;
import java.util.stream.Collectors;

import com.ssafy.bidlive.chat.application.ChatMessagePersistence;
import com.ssafy.bidlive.chat.application.ChatMessagePersistenceResult;
import com.ssafy.bidlive.chat.domain.ChatMessage;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.ClientMessageId;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL UNIQUE와 ON CONFLICT를 이용해 채팅을 발신자별로 한 번만 저장한다. */
@Repository
public class JdbcChatMessagePersistenceAdapter
	implements ChatMessagePersistence {

	private static final String FIND_MESSAGE_ID_QUERY = """
		SELECT message_id
		FROM chat_message
		WHERE sender_member_id = :memberId
			AND client_message_id = :clientMessageId
		LIMIT 1
		""";

	private static final String INSERT_MESSAGE_QUERY = """
		INSERT INTO chat_message (
			message_id,
			client_message_id,
			auction_room_id,
			sender_member_id,
			message_type,
			content,
			moderation_status,
			masked_fields,
			question_queue_status,
			created_at
		)
		VALUES (
			:messageId,
			:clientMessageId,
			:auctionId,
			:memberId,
			:messageType,
			:content,
			'PENDING',
			string_to_array(:maskedFields, ',')::VARCHAR(30)[],
			:questionQueueStatus,
			:createdAt
		)
		ON CONFLICT (sender_member_id, client_message_id) DO NOTHING
		RETURNING message_id
		""";

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public JdbcChatMessagePersistenceAdapter(
		NamedParameterJdbcTemplate jdbcTemplate
	) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ChatMessageId> findMessageId(
		long memberId,
		ClientMessageId clientMessageId
	) {
		MapSqlParameterSource parameters = new MapSqlParameterSource()
			.addValue("memberId", memberId)
			.addValue("clientMessageId", clientMessageId.value());
		return queryMessageId(FIND_MESSAGE_ID_QUERY, parameters);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public ChatMessagePersistenceResult saveIfAbsent(ChatMessage message) {
		MapSqlParameterSource parameters = new MapSqlParameterSource()
			.addValue("messageId", message.messageId().value())
			.addValue(
				"clientMessageId",
				message.clientMessageId().value()
			)
			.addValue("auctionId", message.auctionId())
			.addValue("memberId", message.memberId())
			.addValue(
				"messageType",
				message.isQuestion() ? "QUESTION" : "CHAT"
			)
			.addValue("content", message.content().value())
			.addValue(
				"maskedFields",
				message.maskedFields().stream()
					.sorted()
					.map(Enum::name)
					.collect(Collectors.joining(","))
			)
			.addValue(
				"questionQueueStatus",
				message.isQuestion() ? "PENDING" : null,
				Types.VARCHAR
			)
			.addValue("createdAt", Timestamp.from(message.createdAt()));

		Optional<ChatMessageId> insertedMessageId =
			queryMessageId(INSERT_MESSAGE_QUERY, parameters);
		if (insertedMessageId.isPresent()) {
			return ChatMessagePersistenceResult.created(
				insertedMessageId.orElseThrow()
			);
		}

		ChatMessageId existingMessageId = findMessageId(
			message.memberId(),
			message.clientMessageId()
		).orElseThrow(() -> new IllegalStateException(
			"idempotency conflict occurred without an existing message"
		));
		return ChatMessagePersistenceResult.existing(existingMessageId);
	}

	private Optional<ChatMessageId> queryMessageId(
		String sql,
		MapSqlParameterSource parameters
	) {
		return jdbcTemplate.query(
			sql,
			parameters,
			(resultSet, rowNumber) ->
				new ChatMessageId(resultSet.getString("message_id"))
		).stream().findFirst();
	}
}
