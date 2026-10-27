package com.ssafy.bidlive.chat.infrastructure.persistence;

import java.util.List;

import com.ssafy.bidlive.chat.application.ChatModerationDecision;
import com.ssafy.bidlive.chat.application.ChatModerationJob;
import com.ssafy.bidlive.chat.application.ChatModerationOutcomePersistence;
import com.ssafy.bidlive.chat.application.ModerationOutcomeApplyResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PENDING 채팅에 BLOCKED 이외의 LLM 판정을 최초 한 번만 반영합니다. */
@Repository
public class JdbcChatModerationOutcomePersistenceAdapter
	implements ChatModerationOutcomePersistence {

	private static final String APPLY = """
		UPDATE chat_message
		SET moderation_status = 'PASSED',
			moderation_reason = ?,
			question_queue_status = CASE
				WHEN ? = 'QUESTION_FILTERED'
					AND message_type = 'QUESTION'
					AND question_queue_status = 'PENDING'
				THEN 'FILTERED'
				ELSE question_queue_status
			END
		WHERE auction_room_id = ?
			AND message_id = ?
			AND moderation_status = 'PENDING'
			AND is_deleted = FALSE
		RETURNING message_type
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcChatModerationOutcomePersistenceAdapter(
		JdbcTemplate jdbcTemplate
	) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public ModerationOutcomeApplyResult apply(
		ChatModerationJob job,
		ChatModerationDecision decision
	) {
		if (decision == ChatModerationDecision.BLOCKED) {
			throw new IllegalArgumentException(
				"BLOCKED must use deletion persistence"
			);
		}
		List<String> changedTypes = jdbcTemplate.query(
			APPLY,
			(resultSet, rowNumber) -> resultSet.getString("message_type"),
			decision.name(),
			decision.name(),
			job.auctionId(),
			job.messageId().value()
		);
		if (changedTypes.isEmpty()) {
			return ModerationOutcomeApplyResult.unchanged();
		}
		boolean filtered = decision == ChatModerationDecision.QUESTION_FILTERED
			&& "QUESTION".equals(changedTypes.getFirst());
		return ModerationOutcomeApplyResult.changed(filtered);
	}
}
