package com.ssafy.bidlive.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.auction.application.AuctionOwnershipReader;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.QuestionQueueErrorCode;
import com.ssafy.bidlive.chat.domain.QuestionQueueItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 질문 큐의 판매자 권한·커서·페이지 크기·hasNext 계산을 검증한다. */
class QuestionQueueQueryServiceTest {

	private static final long AUCTION_ID = 100L;
	private static final long SELLER_ID = 25L;
	private static final ChatMessageId QUESTION_ID_1 =
		new ChatMessageId("01J8W3P2N9KQ4T123456789ABC");
	private static final ChatMessageId QUESTION_ID_2 =
		new ChatMessageId("01J8W3P2N9KQ4T123456789ABD");
	private static final Instant CREATED_AT =
		Instant.parse("2026-07-31T04:00:00Z");

	private final QuestionQueuePersistence persistence =
		mock(QuestionQueuePersistence.class);
	private final AuctionOwnershipReader ownershipReader =
		mock(AuctionOwnershipReader.class);
	private QuestionQueueQueryService service;

	@BeforeEach
	void setUp() {
		service = new QuestionQueueQueryService(persistence, ownershipReader);
		when(ownershipReader.isSeller(AUCTION_ID, SELLER_ID)).thenReturn(true);
	}

	@Test
	void returnsDefaultSizedFirstPageAndLastItemCursor() {
		when(persistence.findPendingAfter(
			AUCTION_ID,
			Optional.empty(),
			QuestionQueueQueryService.DEFAULT_PAGE_SIZE + 1
		)).thenReturn(List.of(item(QUESTION_ID_1), item(QUESTION_ID_2)));

		QuestionQueuePage page = service.getPendingQuestions(
			AUCTION_ID,
			SELLER_ID,
			null,
			null
		);

		assertThat(page.items())
			.extracting(QuestionQueueItem::questionId)
			.containsExactly(QUESTION_ID_1, QUESTION_ID_2);
		assertThat(page.nextCursor()).isEqualTo(QUESTION_ID_2.value());
		assertThat(page.hasNext()).isFalse();
	}

	@Test
	void fetchesOneExtraItemAndExcludesItFromCurrentPage() {
		when(persistence.findPendingAfter(
			AUCTION_ID,
			Optional.empty(),
			2
		)).thenReturn(List.of(item(QUESTION_ID_1), item(QUESTION_ID_2)));

		QuestionQueuePage page = service.getPendingQuestions(
			AUCTION_ID,
			SELLER_ID,
			null,
			1
		);

		assertThat(page.items()).containsExactly(item(QUESTION_ID_1));
		assertThat(page.nextCursor()).isEqualTo(QUESTION_ID_1.value());
		assertThat(page.hasNext()).isTrue();
	}

	@Test
	void resolvesCursorBeforeQueryingNextPage() {
		QuestionQueueCursor cursor = new QuestionQueueCursor(
			QUESTION_ID_1,
			CREATED_AT
		);
		when(persistence.findCursor(AUCTION_ID, QUESTION_ID_1))
			.thenReturn(Optional.of(cursor));
		when(persistence.findPendingAfter(
			AUCTION_ID,
			Optional.of(cursor),
			21
		)).thenReturn(List.of(item(QUESTION_ID_2)));

		QuestionQueuePage page = service.getPendingQuestions(
			AUCTION_ID,
			SELLER_ID,
			QUESTION_ID_1.value(),
			null
		);

		assertThat(page.items()).containsExactly(item(QUESTION_ID_2));
	}

	@Test
	void returnsEmptyPageWithoutCursor() {
		when(persistence.findPendingAfter(
			AUCTION_ID,
			Optional.empty(),
			21
		)).thenReturn(List.of());

		QuestionQueuePage page = service.getPendingQuestions(
			AUCTION_ID,
			SELLER_ID,
			null,
			null
		);

		assertThat(page.items()).isEmpty();
		assertThat(page.nextCursor()).isNull();
		assertThat(page.hasNext()).isFalse();
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 101})
	void rejectsOutOfRangePageSize(int size) {
		QuestionQueueException exception = catchThrowableOfType(
			() -> service.getPendingQuestions(
				AUCTION_ID,
				SELLER_ID,
				null,
				size
			),
			QuestionQueueException.class
		);

		assertThat(exception.errorCode())
			.isEqualTo(QuestionQueueErrorCode.INVALID_PAGE_SIZE);
		verify(persistence, never())
			.findPendingAfter(anyLong(), any(), anyInt());
	}

	@Test
	void rejectsMalformedCursor() {
		QuestionQueueException exception = catchThrowableOfType(
			() -> service.getPendingQuestions(
				AUCTION_ID,
				SELLER_ID,
				"not-a-ulid",
				null
			),
			QuestionQueueException.class
		);

		assertThat(exception.errorCode())
			.isEqualTo(QuestionQueueErrorCode.INVALID_CURSOR);
	}

	@Test
	void rejectsUnknownCursor() {
		when(persistence.findCursor(AUCTION_ID, QUESTION_ID_1))
			.thenReturn(Optional.empty());

		QuestionQueueException exception = catchThrowableOfType(
			() -> service.getPendingQuestions(
				AUCTION_ID,
				SELLER_ID,
				QUESTION_ID_1.value(),
				null
			),
			QuestionQueueException.class
		);

		assertThat(exception.errorCode())
			.isEqualTo(QuestionQueueErrorCode.INVALID_CURSOR);
	}

	@Test
	void rejectsNonSellerBeforeReadingQuestionData() {
		when(ownershipReader.isSeller(AUCTION_ID, SELLER_ID)).thenReturn(false);

		QuestionQueueException exception = catchThrowableOfType(
			() -> service.getPendingQuestions(
				AUCTION_ID,
				SELLER_ID,
				null,
				null
			),
			QuestionQueueException.class
		);

		assertThat(exception.errorCode())
			.isEqualTo(QuestionQueueErrorCode.NOT_AUCTION_HOST);
		verify(persistence, never())
			.findPendingAfter(anyLong(), any(), anyInt());
	}

	private static QuestionQueueItem item(ChatMessageId questionId) {
		return new QuestionQueueItem(
			questionId,
			7L,
			"질문자",
			"흠집을 보여주세요",
			CREATED_AT
		);
	}
}
