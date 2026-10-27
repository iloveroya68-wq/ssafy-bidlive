package com.ssafy.bidlive.chat.infrastructure.persistence;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.ssafy.bidlive.chat.application.ChatHistoryPage;
import com.ssafy.bidlive.chat.application.ChatHistoryPersistence;
import com.ssafy.bidlive.chat.domain.ChatMessage;
import com.ssafy.bidlive.chat.domain.ChatMessageContent;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.ClientMessageId;
import com.ssafy.bidlive.chat.domain.MaskingCategory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL에서 공개 ULID 커서 이전의 노출 가능한 채팅 이력을 안정적으로 조회한다. */
@Repository
public class JdbcChatHistoryPersistenceAdapter
	implements ChatHistoryPersistence {

	private static final String SELECT_MESSAGE_COLUMNS = """
		SELECT
			message.message_id,
			message.client_message_id,
			message.auction_room_id,
			message.sender_member_id,
			member.nickname,
			message.message_type,
			message.content,
			message.masked_fields,
			message.created_at
		FROM chat_message message
		JOIN member ON member.member_id = message.sender_member_id
		""";

	private static final String VISIBLE_HISTORY_CONDITION = """
		message.auction_room_id = :auctionId
			AND message.sender_member_id IS NOT NULL
			AND message.client_message_id IS NOT NULL
			AND message.message_type IN ('CHAT', 'QUESTION')
			AND message.is_deleted = FALSE
			AND message.moderation_status <> 'BLOCKED'
		""";

	private static final String FIND_FIRST_PAGE_QUERY =
		SELECT_MESSAGE_COLUMNS + "\nWHERE " + VISIBLE_HISTORY_CONDITION + """
		ORDER BY message.created_at DESC, message.message_id DESC
		LIMIT :limit
		""";

	private static final String FIND_BEFORE_QUERY =
		SELECT_MESSAGE_COLUMNS + "\nWHERE " + VISIBLE_HISTORY_CONDITION + """
			AND (message.created_at, message.message_id)
				< (:cursorCreatedAt, :cursorMessageId)
		ORDER BY message.created_at DESC, message.message_id DESC
		LIMIT :limit
		""";

	private static final String FIND_CURSOR_QUERY = """
		SELECT created_at, message_id
		FROM chat_message
		WHERE auction_room_id = :auctionId
			AND message_id = :messageId
		LIMIT 1
		""";

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public JdbcChatHistoryPersistenceAdapter(
		NamedParameterJdbcTemplate jdbcTemplate
	) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ChatHistoryPage> findBefore(
		long auctionId,
		ChatMessageId before,
		int size
	) {
		if (before == null) {
			return Optional.of(queryPage(
				FIND_FIRST_PAGE_QUERY,
				baseParameters(auctionId, size),
				size
			));
		}

		return findCursor(auctionId, before).map(cursor -> {
			MapSqlParameterSource parameters = baseParameters(auctionId, size)
				.addValue("cursorCreatedAt", Timestamp.from(cursor.createdAt()))
				.addValue("cursorMessageId", cursor.messageId());
			return queryPage(FIND_BEFORE_QUERY, parameters, size);
		});
	}

	private Optional<ChatHistoryCursor> findCursor(
		long auctionId,
		ChatMessageId before
	) {
		MapSqlParameterSource parameters = new MapSqlParameterSource()
			.addValue("auctionId", auctionId)
			.addValue("messageId", before.value());
		return jdbcTemplate.query(
			FIND_CURSOR_QUERY,
			parameters,
			(resultSet, rowNumber) -> new ChatHistoryCursor(
				resultSet.getTimestamp("created_at").toInstant(),
				resultSet.getString("message_id")
			)
		).stream().findFirst();
	}

	private ChatHistoryPage queryPage(
		String query,
		MapSqlParameterSource parameters,
		int size
	) {
		List<ChatMessage> descending = jdbcTemplate.query(
			query,
			parameters,
			(resultSet, rowNumber) -> new ChatMessage(
				new ChatMessageId(resultSet.getString("message_id")),
				new ClientMessageId(resultSet.getString("client_message_id")),
				resultSet.getLong("auction_room_id"),
				resultSet.getLong("sender_member_id"),
				resultSet.getString("nickname"),
				new ChatMessageContent(resultSet.getString("content")),
				"QUESTION".equals(resultSet.getString("message_type")),
				maskedFields(resultSet.getArray("masked_fields")),
				resultSet.getTimestamp("created_at").toInstant()
			)
		);

		boolean hasMore = descending.size() > size;
		List<ChatMessage> messages = new ArrayList<>(
			descending.subList(0, Math.min(size, descending.size()))
		);
		Collections.reverse(messages);
		return new ChatHistoryPage(messages, hasMore);
	}

	private static MapSqlParameterSource baseParameters(
		long auctionId,
		int size
	) {
		return new MapSqlParameterSource()
			.addValue("auctionId", auctionId)
			.addValue("limit", size + 1);
	}

	private static Set<MaskingCategory> maskedFields(Array array)
		throws SQLException {
		if (array == null) {
			return Set.of();
		}
		String[] values = (String[]) array.getArray();
		return Arrays.stream(values)
			.map(MaskingCategory::valueOf)
			.collect(Collectors.toUnmodifiableSet());
	}

	private record ChatHistoryCursor(
		Instant createdAt,
		String messageId
	) {
	}
}
