package com.ssafy.bidlive.chat.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import com.ssafy.bidlive.chat.domain.ChatDeletedEvent;
import com.ssafy.bidlive.chat.domain.ChatMessageDeletionReason;
import com.ssafy.bidlive.chat.domain.ChatMutedEvent;
import com.ssafy.bidlive.chat.domain.ModerationWarningEvent;
import com.ssafy.bidlive.chat.domain.NoticeUnpinnedEvent;
import com.ssafy.bidlive.chat.domain.QuestionRemovalReason;
import com.ssafy.bidlive.chat.domain.QuestionRemovedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 확정된 LLM 판정을 메시지·질문·경고·뮤트 상태와 후속 이벤트에 반영합니다. */
@Service
public class ChatAsyncModerationOutcomeHandler
	implements ChatModerationOutcomeHandler {

	private final ChatModerationOutcomePersistence outcomePersistence;
	private final ChatMessageDeletionPersistence deletionPersistence;
	private final ChatModerationPenaltyPort penaltyPort;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	@Autowired
	public ChatAsyncModerationOutcomeHandler(
		ChatModerationOutcomePersistence outcomePersistence,
		ChatMessageDeletionPersistence deletionPersistence,
		ChatModerationPenaltyPort penaltyPort,
		ApplicationEventPublisher eventPublisher
	) {
		this(
			outcomePersistence,
			deletionPersistence,
			penaltyPort,
			eventPublisher,
			Clock.systemUTC()
		);
	}

	ChatAsyncModerationOutcomeHandler(
		ChatModerationOutcomePersistence outcomePersistence,
		ChatMessageDeletionPersistence deletionPersistence,
		ChatModerationPenaltyPort penaltyPort,
		ApplicationEventPublisher eventPublisher,
		Clock clock
	) {
		this.outcomePersistence = Objects.requireNonNull(
			outcomePersistence,
			"outcomePersistence"
		);
		this.deletionPersistence = Objects.requireNonNull(
			deletionPersistence,
			"deletionPersistence"
		);
		this.penaltyPort = Objects.requireNonNull(penaltyPort, "penaltyPort");
		this.eventPublisher = Objects.requireNonNull(
			eventPublisher,
			"eventPublisher"
		);
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	@Transactional
	public void apply(
		ChatModerationJob job,
		ChatModerationJudgement judgement
	) {
		if (judgement.decision() == ChatModerationDecision.BLOCKED
			|| shouldBlockWarning(job, judgement)) {
			applyBlocked(job, judgement.reason());
			return;
		}
		ModerationOutcomeApplyResult result = outcomePersistence.apply(
			job,
			judgement.decision()
		);
		if (!result.changed()) {
			return;
		}
		if (result.questionFiltered()) {
			applyQuestionFiltered(job);
			return;
		}
		if (judgement.decision() == ChatModerationDecision.WARNING) {
			publishPenalty(job, judgement.reason());
		}
	}

	private void applyQuestionFiltered(ChatModerationJob job) {
		ChatMessageDeletionResult result = deletionPersistence.delete(
			new ChatMessageDeletionCommand(
				job.auctionId(),
				job.messageId(),
				null,
				ChatMessageDeletionReason.QUESTION_FILTERED,
				clock.instant()
			)
		);
		if (!result.deleted()) {
			return;
		}
		eventPublisher.publishEvent(new ChatDeletedEvent(
			job.auctionId(),
			job.messageId()
		));
		if (result.pinned()) {
			eventPublisher.publishEvent(new NoticeUnpinnedEvent(job.auctionId()));
		}
		eventPublisher.publishEvent(new QuestionRemovedEvent(
			job.auctionId(),
			job.messageId(),
			QuestionRemovalReason.FILTERED
		));
	}

	private static boolean shouldBlockWarning(
		ChatModerationJob job,
		ChatModerationJudgement judgement
	) {
		if (job.question() || judgement.decision() != ChatModerationDecision.WARNING) {
			return false;
		}
		return switch (judgement.reason()) {
			case OFF_PLATFORM_TRADE,
				TOXIC_CONTENT,
				PROHIBITED_GOODS,
				AUCTION_MANIPULATION,
				ADVERTISING_SPAM -> true;
			default -> false;
		};
	}

	private void applyBlocked(
		ChatModerationJob job,
		ChatModerationReason reason
	) {
		Instant now = clock.instant();
		ChatMessageDeletionResult result = deletionPersistence.delete(
			new ChatMessageDeletionCommand(
				job.auctionId(),
				job.messageId(),
				null,
				ChatMessageDeletionReason.MODERATION_BLOCKED,
				now
			)
		);
		if (!result.deleted()) {
			return;
		}
		eventPublisher.publishEvent(new ChatDeletedEvent(
			job.auctionId(),
			job.messageId()
		));
		if (result.pinned()) {
			eventPublisher.publishEvent(new NoticeUnpinnedEvent(job.auctionId()));
		}
		if (result.question()) {
			eventPublisher.publishEvent(new QuestionRemovedEvent(
				job.auctionId(),
				job.messageId(),
				QuestionRemovalReason.FILTERED
			));
		}
		publishPenalty(job, reason);
	}

	private void publishPenalty(
		ChatModerationJob job,
		ChatModerationReason reason
	) {
		eventPublisher.publishEvent(new ModerationWarningEvent(
			job.memberId(),
			job.messageId(),
			reason
		));
		ModerationPenaltyResult penalty = penaltyPort.recordViolation(
			job.auctionId(),
			job.memberId(),
			clock.instant()
		);
		penalty.mutedUntil().ifPresent(mutedUntil ->
			eventPublisher.publishEvent(new ChatMutedEvent(
				job.memberId(),
				job.auctionId(),
				java.time.Duration.between(clock.instant(), mutedUntil),
				mutedUntil
			))
		);
	}
}
