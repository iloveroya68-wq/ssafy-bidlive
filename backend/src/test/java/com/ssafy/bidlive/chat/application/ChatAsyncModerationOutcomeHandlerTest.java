package com.ssafy.bidlive.chat.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.ssafy.bidlive.chat.domain.ChatDeletedEvent;
import com.ssafy.bidlive.chat.domain.ChatMessageDeletionReason;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.ModerationWarningEvent;
import com.ssafy.bidlive.chat.domain.QuestionRemovalReason;
import com.ssafy.bidlive.chat.domain.QuestionRemovedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** AI 판정의 멱등 상태 반영과 BLOCKED·질문·경고 이벤트를 검증합니다. */
class ChatAsyncModerationOutcomeHandlerTest {

	private static final Instant NOW = Instant.parse("2026-08-02T01:00:00Z");
	private ChatModerationOutcomePersistence outcomePersistence;
	private ChatMessageDeletionPersistence deletionPersistence;
	private ChatModerationPenaltyPort penaltyPort;
	private ApplicationEventPublisher eventPublisher;
	private ChatAsyncModerationOutcomeHandler handler;

	@BeforeEach
	void setUp() {
		outcomePersistence = mock(ChatModerationOutcomePersistence.class);
		deletionPersistence = mock(ChatMessageDeletionPersistence.class);
		penaltyPort = mock(ChatModerationPenaltyPort.class);
		eventPublisher = mock(ApplicationEventPublisher.class);
		handler = new ChatAsyncModerationOutcomeHandler(
			outcomePersistence,
			deletionPersistence,
			penaltyPort,
			eventPublisher,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void hidesBlockedMessageAndPublishesFilteredQuestionOnce() {
		ChatModerationJob job = job(true);
		when(deletionPersistence.delete(any())).thenReturn(
			ChatMessageDeletionResult.deleted(false, true)
		);
		when(penaltyPort.recordViolation(42L, 7L, NOW)).thenReturn(
			new ModerationPenaltyResult(1L, Optional.empty())
		);

		handler.apply(job, new ChatModerationJudgement(
			ChatModerationDecision.BLOCKED,
			ChatModerationReason.OFF_PLATFORM_TRADE
		));

		ArgumentCaptor<ChatMessageDeletionCommand> command =
			ArgumentCaptor.forClass(ChatMessageDeletionCommand.class);
		verify(deletionPersistence).delete(command.capture());
		org.assertj.core.api.Assertions.assertThat(command.getValue().reason())
			.isEqualTo(ChatMessageDeletionReason.MODERATION_BLOCKED);
		verify(eventPublisher).publishEvent(
			new ChatDeletedEvent(42L, job.messageId())
		);
		verify(eventPublisher).publishEvent(new QuestionRemovedEvent(
			42L,
			job.messageId(),
			QuestionRemovalReason.FILTERED
		));
		verify(eventPublisher).publishEvent(new ModerationWarningEvent(
			7L,
			job.messageId(),
			ChatModerationReason.OFF_PLATFORM_TRADE
		));
	}

	@Test
	void treatsNormalChatPolicyWarningAsBlocked() {
		ChatModerationJob job = job(false);
		when(deletionPersistence.delete(any())).thenReturn(
			ChatMessageDeletionResult.deleted(false, false)
		);
		when(penaltyPort.recordViolation(42L, 7L, NOW)).thenReturn(
			new ModerationPenaltyResult(1L, Optional.empty())
		);

		handler.apply(job, new ChatModerationJudgement(
			ChatModerationDecision.WARNING,
			ChatModerationReason.AUCTION_MANIPULATION
		));

		verify(deletionPersistence).delete(any());
		verify(outcomePersistence, never()).apply(any(), any());
	}

	@Test
	void filtersQuestionAndSkipsEventsOnDuplicateOutcome() {
		ChatModerationJob job = job(true);
		when(deletionPersistence.delete(any())).thenReturn(
			ChatMessageDeletionResult.deleted(false, true)
		);
		when(outcomePersistence.apply(
			job,
			ChatModerationDecision.QUESTION_FILTERED
		)).thenReturn(
			ModerationOutcomeApplyResult.changed(true),
			ModerationOutcomeApplyResult.unchanged()
		);

		handler.apply(job, new ChatModerationJudgement(
			ChatModerationDecision.QUESTION_FILTERED,
			ChatModerationReason.DUPLICATE_QUESTION
		));
		handler.apply(job, new ChatModerationJudgement(
			ChatModerationDecision.QUESTION_FILTERED,
			ChatModerationReason.DUPLICATE_QUESTION
		));

		verify(eventPublisher).publishEvent(new QuestionRemovedEvent(
			42L,
			job.messageId(),
			QuestionRemovalReason.FILTERED
		));
		verify(eventPublisher).publishEvent(
			new ChatDeletedEvent(42L, job.messageId())
		);
		ArgumentCaptor<ChatMessageDeletionCommand> command =
			ArgumentCaptor.forClass(ChatMessageDeletionCommand.class);
		verify(deletionPersistence).delete(command.capture());
		org.assertj.core.api.Assertions.assertThat(command.getValue().reason())
			.isEqualTo(ChatMessageDeletionReason.QUESTION_FILTERED);
		verify(penaltyPort, never()).recordViolation(any(Long.class), any(Long.class), any());
	}

	private ChatModerationJob job(boolean question) {
		return new ChatModerationJob(
			42L,
			new ChatMessageId("01J8W3P2N9KQ4T123456789ABC"),
			7L,
			"마스킹된 본문",
			question,
			NOW.minusSeconds(10)
		);
	}
}
