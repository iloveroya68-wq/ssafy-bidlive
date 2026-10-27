package com.ssafy.bidlive.chat.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.chat.application.QuestionQueueCursor;
import com.ssafy.bidlive.chat.application.QuestionQueuePersistence;
import com.ssafy.bidlive.chat.application.QuestionResolutionResult;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.QuestionQueueItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL에서 PENDING 질문을 생성 시각과 ULID 기준으로 안정 조회한다. */
@Repository
public class JpaQuestionQueuePersistenceAdapter
	implements QuestionQueuePersistence {

	private static final String FIND_CURSOR_QUERY = """
		SELECT created_at
		FROM chat_message
		WHERE auction_room_id = :auctionId
			AND message_id = :questionId
			AND message_type = 'QUESTION'
			AND is_deleted = FALSE
		""";

	private static final String FIND_FIRST_PAGE_QUERY = """
		SELECT
			cm.message_id,
			cm.sender_member_id,
			m.nickname,
			cm.content,
			cm.created_at
		FROM chat_message cm
		JOIN member m ON m.member_id = cm.sender_member_id
		WHERE cm.auction_room_id = :auctionId
			AND cm.message_type = 'QUESTION'
			AND cm.question_queue_status = 'PENDING'
			AND cm.is_deleted = FALSE
		ORDER BY cm.created_at ASC, cm.message_id ASC
		""";

	private static final String FIND_AFTER_CURSOR_QUERY = """
		SELECT
			cm.message_id,
			cm.sender_member_id,
			m.nickname,
			cm.content,
			cm.created_at
		FROM chat_message cm
		JOIN member m ON m.member_id = cm.sender_member_id
		WHERE cm.auction_room_id = :auctionId
			AND cm.message_type = 'QUESTION'
			AND cm.question_queue_status = 'PENDING'
			AND cm.is_deleted = FALSE
			AND (
				cm.created_at > :cursorCreatedAt
				OR (
					cm.created_at = :cursorCreatedAt
					AND cm.message_id > :cursorQuestionId
				)
			)
		ORDER BY cm.created_at ASC, cm.message_id ASC
		""";

	private static final String RESOLVE_PENDING_QUERY = """
		UPDATE chat_message
		SET question_queue_status = 'RESOLVED'
		WHERE auction_room_id = :auctionId
			AND message_id = :questionId
			AND message_type = 'QUESTION'
			AND question_queue_status = 'PENDING'
			AND is_deleted = FALSE
		""";

	private static final String FIND_QUESTION_STATUS_QUERY = """
		SELECT question_queue_status
		FROM chat_message
		WHERE auction_room_id = :auctionId
			AND message_id = :questionId
			AND message_type = 'QUESTION'
			AND is_deleted = FALSE
		""";

	private final EntityManager entityManager;

	public JpaQuestionQueuePersistenceAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<QuestionQueueCursor> findCursor(
		long auctionId,
		ChatMessageId questionId
	) {
		@SuppressWarnings("unchecked")
		List<Object> rows = entityManager.createNativeQuery(FIND_CURSOR_QUERY)
			.setParameter("auctionId", auctionId)
			.setParameter("questionId", questionId.value())
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new QuestionQueueCursor(
			questionId,
			toInstant(rows.getFirst())
		));
	}

	@Override
	@Transactional(readOnly = true)
	public List<QuestionQueueItem> findPendingAfter(
		long auctionId,
		Optional<QuestionQueueCursor> cursor,
		int limit
	) {
		Query query = cursor
			.map(value -> entityManager
				.createNativeQuery(FIND_AFTER_CURSOR_QUERY)
				.setParameter("auctionId", auctionId)
				.setParameter("cursorCreatedAt", value.createdAt())
				.setParameter(
					"cursorQuestionId",
					value.questionId().value()
				))
			.orElseGet(() -> entityManager
				.createNativeQuery(FIND_FIRST_PAGE_QUERY)
				.setParameter("auctionId", auctionId));
		query.setMaxResults(limit);

		@SuppressWarnings("unchecked")
		List<Object[]> rows = query.getResultList();
		return rows.stream().map(JpaQuestionQueuePersistenceAdapter::toItem)
			.toList();
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public QuestionResolutionResult resolvePending(
		long auctionId,
		ChatMessageId questionId
	) {
		int updated = entityManager.createNativeQuery(RESOLVE_PENDING_QUERY)
			.setParameter("auctionId", auctionId)
			.setParameter("questionId", questionId.value())
			.executeUpdate();
		if (updated == 1) {
			return QuestionResolutionResult.RESOLVED;
		}

		@SuppressWarnings("unchecked")
		List<String> statuses = entityManager
			.createNativeQuery(FIND_QUESTION_STATUS_QUERY)
			.setParameter("auctionId", auctionId)
			.setParameter("questionId", questionId.value())
			.getResultList();
		if (statuses.isEmpty()) {
			return QuestionResolutionResult.NOT_FOUND;
		}
		String status = statuses.getFirst();
		if ("RESOLVED".equals(status) || "FILTERED".equals(status)) {
			return QuestionResolutionResult.ALREADY_HANDLED;
		}
		return QuestionResolutionResult.NOT_FOUND;
	}

	private static QuestionQueueItem toItem(Object[] row) {
		return new QuestionQueueItem(
			new ChatMessageId((String) row[0]),
			((Number) row[1]).longValue(),
			(String) row[2],
			(String) row[3],
			toInstant(row[4])
		);
	}

	private static Instant toInstant(Object value) {
		return switch (value) {
			case Instant instant -> instant;
			case Timestamp timestamp -> timestamp.toInstant();
			case OffsetDateTime offsetDateTime -> offsetDateTime.toInstant();
			default -> throw new IllegalStateException(
				"unsupported question createdAt type: "
					+ value.getClass().getName()
			);
		};
	}
}
