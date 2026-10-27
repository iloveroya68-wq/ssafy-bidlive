package com.ssafy.bidlive.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.bidlive.auction.application.AuctionOwnershipReader;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.QuestionQueueErrorCode;
import com.ssafy.bidlive.chat.domain.QuestionRemovalReason;
import com.ssafy.bidlive.chat.domain.QuestionRemovedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** 질문 해결의 판매자 권한·멱등 상태 전이·이벤트 발행 조건을 검증한다. */
class QuestionQueueCommandServiceTest {

	private static final long AUCTION_ID = 100L;
	private static final long SELLER_ID = 25L;
	private static final ChatMessageId QUESTION_ID =
		new ChatMessageId("01J8W3P2N9KQ4T123456789ABC");

	private final QuestionQueuePersistence persistence =
		mock(QuestionQueuePersistence.class);
	private final AuctionOwnershipReader ownershipReader =
		mock(AuctionOwnershipReader.class);
	private final ApplicationEventPublisher eventPublisher =
		mock(ApplicationEventPublisher.class);
	private QuestionQueueCommandService service;

	@BeforeEach
	void setUp() {
		service = new QuestionQueueCommandService(
			persistence,
			ownershipReader,
			eventPublisher
		);
		when(ownershipReader.isSeller(AUCTION_ID, SELLER_ID)).thenReturn(true);
	}

	@Test
	void publishesResolvedEventOnlyWhenPendingStateChanges() {
		when(persistence.resolvePending(AUCTION_ID, QUESTION_ID))
			.thenReturn(QuestionResolutionResult.RESOLVED);

		service.resolve(AUCTION_ID, SELLER_ID, QUESTION_ID);

		ArgumentCaptor<QuestionRemovedEvent> event =
			ArgumentCaptor.forClass(QuestionRemovedEvent.class);
		verify(eventPublisher).publishEvent(event.capture());
		assertThat(event.getValue().auctionId()).isEqualTo(AUCTION_ID);
		assertThat(event.getValue().questionId()).isEqualTo(QUESTION_ID);
		assertThat(event.getValue().reason())
			.isEqualTo(QuestionRemovalReason.RESOLVED);
	}

	@Test
	void treatsAlreadyResolvedOrFilteredQuestionAsIdempotentSuccess() {
		when(persistence.resolvePending(AUCTION_ID, QUESTION_ID))
			.thenReturn(QuestionResolutionResult.ALREADY_HANDLED);

		service.resolve(AUCTION_ID, SELLER_ID, QUESTION_ID);

		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	void rejectsMissingOrOtherAuctionQuestion() {
		when(persistence.resolvePending(AUCTION_ID, QUESTION_ID))
			.thenReturn(QuestionResolutionResult.NOT_FOUND);

		QuestionQueueException exception = catchThrowableOfType(
			() -> service.resolve(AUCTION_ID, SELLER_ID, QUESTION_ID),
			QuestionQueueException.class
		);

		assertThat(exception.errorCode())
			.isEqualTo(QuestionQueueErrorCode.QUESTION_NOT_FOUND);
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	void rejectsNonSellerBeforeChangingQuestion() {
		when(ownershipReader.isSeller(AUCTION_ID, SELLER_ID)).thenReturn(false);

		QuestionQueueException exception = catchThrowableOfType(
			() -> service.resolve(AUCTION_ID, SELLER_ID, QUESTION_ID),
			QuestionQueueException.class
		);

		assertThat(exception.errorCode())
			.isEqualTo(QuestionQueueErrorCode.NOT_AUCTION_HOST);
		verify(persistence, never()).resolvePending(anyLong(), any());
	}
}
