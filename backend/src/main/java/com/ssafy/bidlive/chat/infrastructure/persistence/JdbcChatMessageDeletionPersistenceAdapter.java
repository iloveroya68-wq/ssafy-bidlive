package com.ssafy.bidlive.chat.infrastructure.persistence;

import java.sql.Timestamp;
import java.util.List;

import com.ssafy.bidlive.chat.application.ChatMessageDeletionCommand;
import com.ssafy.bidlive.chat.application.ChatMessageDeletionPersistence;
import com.ssafy.bidlive.chat.application.ChatMessageDeletionResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 채팅 행을 잠근 뒤 삭제 감사 정보와 공지·질문 연계 상태를 원자적으로 변경한다. */
@Repository
public class JdbcChatMessageDeletionPersistenceAdapter
	implements ChatMessageDeletionPersistence {

	private static final String FIND_FOR_UPDATE = """
		SELECT
			is_deleted,
			is_pinned,
			message_type,
			question_queue_status
		FROM chat_message
		WHERE auction_room_id = ?
			AND message_id = ?
		FOR UPDATE
		""";

	private static final String DELETE_MESSAGE = """
		UPDATE chat_message
		SET
			is_deleted = TRUE,
			is_pinned = FALSE,
			deleted_at = ?,
			deleted_by_member_id = ?,
			deletion_reason = ?,
			moderation_status = CASE
				WHEN ? = 'MODERATION_BLOCKED' THEN 'BLOCKED'
				WHEN ? = 'QUESTION_FILTERED' THEN 'PASSED'
				ELSE moderation_status
			END,
			question_queue_status = CASE
				WHEN ? IN ('MODERATION_BLOCKED', 'QUESTION_FILTERED')
					AND message_type = 'QUESTION'
					AND question_queue_status = 'PENDING'
				THEN 'FILTERED'
				ELSE question_queue_status
			END
		WHERE auction_room_id = ?
			AND message_id = ?
			AND is_deleted = FALSE
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcChatMessageDeletionPersistenceAdapter(
		JdbcTemplate jdbcTemplate
	) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public ChatMessageDeletionResult delete(
		ChatMessageDeletionCommand command
	) {
		List<DeletionTarget> targets = jdbcTemplate.query(
			FIND_FOR_UPDATE,
			(resultSet, rowNumber) -> new DeletionTarget(
				resultSet.getBoolean("is_deleted"),
				resultSet.getBoolean("is_pinned"),
				"QUESTION".equals(resultSet.getString("message_type"))
					&& "PENDING".equals(
						resultSet.getString("question_queue_status")
					)
			),
			command.auctionId(),
			command.messageId().value()
		);
		if (targets.isEmpty()) {
			return ChatMessageDeletionResult.notFound();
		}

		DeletionTarget target = targets.getFirst();
		if (target.deleted()) {
			return ChatMessageDeletionResult.alreadyDeleted();
		}

		String reason = command.reason().name();
		int updated = jdbcTemplate.update(
			DELETE_MESSAGE,
			Timestamp.from(command.deletedAt()),
			command.deletedByMemberId(),
			reason,
			reason,
			reason,
			reason,
			command.auctionId(),
			command.messageId().value()
		);
		if (updated != 1) {
			throw new IllegalStateException(
				"locked chat message deletion did not update exactly one row"
			);
		}
		return ChatMessageDeletionResult.deleted(
			target.pinned(),
			target.pendingQuestion()
		);
	}

	/** 잠금 시점의 삭제 여부와 후속 이벤트가 필요한 연계 상태를 보관한다. */
	private record DeletionTarget(
		boolean deleted,
		boolean pinned,
		boolean pendingQuestion
	) {
	}
}
