package com.ssafy.bidlive.chat.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.chat.application.ChatLlmFailureCode;
import com.ssafy.bidlive.chat.application.ChatModerationJob;
import com.ssafy.bidlive.chat.application.ChatModerationJobQueue;
import com.ssafy.bidlive.chat.application.ClaimedChatModerationJob;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 비동기 검열 작업을 messageId 기준으로 한 번만 PostgreSQL에 등록합니다. */
@Repository
public class JdbcChatModerationJobQueueAdapter
	implements ChatModerationJobQueue {

	private static final String ENQUEUE = """
		INSERT INTO chat_moderation_job (
			auction_room_id,
			message_id,
			member_id,
			filtered_content,
			is_question,
			room_title,
			product_description,
			product_category_code,
			next_attempt_at,
			created_at,
			updated_at
		)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
		ON CONFLICT (message_id) DO NOTHING
		""";
	private static final String CLAIM_DUE = """
		WITH candidate AS (
			SELECT job_id
			FROM chat_moderation_job
			WHERE attempt_count < ?
				AND (
					(status = 'PENDING' AND next_attempt_at <= ?)
					OR (status = 'PROCESSING' AND locked_at < ?)
				)
			ORDER BY next_attempt_at, job_id
			FOR UPDATE SKIP LOCKED
			LIMIT 1
		)
		UPDATE chat_moderation_job job
		SET status = 'PROCESSING',
			attempt_count = job.attempt_count + 1,
			locked_at = ?,
			updated_at = ?
		FROM candidate
		WHERE job.job_id = candidate.job_id
		RETURNING job.job_id, job.auction_room_id, job.message_id,
			job.member_id, job.filtered_content, job.is_question,
			job.room_title, job.product_description, job.product_category_code,
			job.created_at, job.attempt_count
		""";
	private static final String SUCCEED = """
		UPDATE chat_moderation_job
		SET status = 'SUCCEEDED', locked_at = NULL,
			last_error_code = NULL, updated_at = ?
		WHERE job_id = ? AND status = 'PROCESSING'
		""";
	private static final String RETRY = """
		UPDATE chat_moderation_job
		SET status = 'PENDING', next_attempt_at = ?, locked_at = NULL,
			last_error_code = ?, updated_at = ?
		WHERE job_id = ? AND status = 'PROCESSING'
		""";
	private static final String DEAD_LETTER = """
		UPDATE chat_moderation_job
		SET status = 'DEAD_LETTER', locked_at = NULL,
			member_id = NULL, filtered_content = NULL,
			room_title = NULL, product_description = NULL,
			product_category_code = NULL,
			last_error_code = ?, updated_at = ?
		WHERE job_id = ? AND status = 'PROCESSING'
		""";
	private static final String RECOVER_EXHAUSTED = """
		UPDATE chat_moderation_job
		SET status = 'DEAD_LETTER', locked_at = NULL,
			member_id = NULL, filtered_content = NULL,
			room_title = NULL, product_description = NULL,
			product_category_code = NULL,
			last_error_code = 'LLM_RETRY_EXHAUSTED', updated_at = ?
		WHERE status = 'PROCESSING'
			AND attempt_count >= ?
			AND locked_at < ?
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcChatModerationJobQueueAdapter(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public boolean enqueue(ChatModerationJob job) {
		Timestamp createdAt = Timestamp.from(job.createdAt());
		return jdbcTemplate.update(
			ENQUEUE,
			job.auctionId(),
			job.messageId().value(),
			job.memberId(),
			job.filteredContent(),
			job.question(),
			job.roomTitle(),
			job.productDescription(),
			job.productCategoryCode(),
			createdAt,
			createdAt,
			createdAt
		) == 1;
	}

	@Override
	public Optional<ClaimedChatModerationJob> claimDue(
		Instant now,
		Duration lease,
		int maxAttempts
	) {
		Timestamp timestamp = Timestamp.from(now);
		List<ClaimedChatModerationJob> claimed = jdbcTemplate.query(
			CLAIM_DUE,
			(resultSet, rowNumber) -> new ClaimedChatModerationJob(
				resultSet.getLong("job_id"),
				new ChatModerationJob(
					resultSet.getLong("auction_room_id"),
					new ChatMessageId(resultSet.getString("message_id")),
					resultSet.getLong("member_id"),
					resultSet.getString("filtered_content"),
					resultSet.getBoolean("is_question"),
					resultSet.getString("room_title"),
					resultSet.getString("product_description"),
					resultSet.getString("product_category_code"),
					resultSet.getTimestamp("created_at").toInstant()
				),
				resultSet.getInt("attempt_count")
			),
			maxAttempts,
			timestamp,
			Timestamp.from(now.minus(lease)),
			timestamp,
			timestamp
		);
		return claimed.stream().findFirst();
	}

	@Override
	public void succeed(long jobId, Instant now) {
		requireSingleUpdate(SUCCEED, Timestamp.from(now), jobId);
	}

	@Override
	public void retry(
		long jobId,
		Instant nextAttemptAt,
		ChatLlmFailureCode failureCode,
		Instant now
	) {
		requireSingleUpdate(
			RETRY,
			Timestamp.from(nextAttemptAt),
			failureCode.name(),
			Timestamp.from(now),
			jobId
		);
	}

	@Override
	public void deadLetter(
		long jobId,
		ChatLlmFailureCode failureCode,
		Instant now
	) {
		requireSingleUpdate(
			DEAD_LETTER,
			failureCode.name(),
			Timestamp.from(now),
			jobId
		);
	}

	@Override
	public int recoverExhaustedLeases(
		Instant staleBefore,
		int maxAttempts,
		Instant now
	) {
		return jdbcTemplate.update(
			RECOVER_EXHAUSTED,
			Timestamp.from(now),
			maxAttempts,
			Timestamp.from(staleBefore)
		);
	}

	private void requireSingleUpdate(String sql, Object... arguments) {
		if (jdbcTemplate.update(sql, arguments) != 1) {
			throw new IllegalStateException(
				"moderation job state transition did not update one row"
			);
		}
	}
}
