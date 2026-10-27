package com.ssafy.bidlive.auction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.time.Instant;

import com.ssafy.bidlive.auction.domain.AuctionExtendedEvent;
import com.ssafy.bidlive.auction.domain.AuctionStartedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 커밋된 경매 시작·연장 이벤트가 최신 종료 시각으로 예약되는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class AuctionSettlementDeadlineEventListenerTest {

	private static final long AUCTION_ID = 93L;
	private static final Instant STARTED_AT =
		Instant.parse("2026-08-02T06:00:00Z");
	private static final Instant END_AT = STARTED_AT.plusSeconds(600);
	private static final Instant EXTENDED_END_AT = END_AT.plusSeconds(300);

	@Mock
	private AuctionSettlementDeadlineScheduler deadlineScheduler;

	@InjectMocks
	private AuctionSettlementDeadlineEventListener listener;

	@Test
	void schedulesSettlementAtTheCommittedAuctionDeadline() {
		listener.onAuctionStarted(new AuctionStartedEvent(
			AUCTION_ID,
			71L,
			10_000L,
			STARTED_AT,
			END_AT
		));

		verify(deadlineScheduler).schedule(AUCTION_ID, END_AT);
	}

	@Test
	void reschedulesSettlementAtTheCommittedExtendedDeadline() {
		listener.onAuctionExtended(new AuctionExtendedEvent(
			AUCTION_ID,
			EXTENDED_END_AT,
			1,
			2
		));

		verify(deadlineScheduler).schedule(AUCTION_ID, EXTENDED_END_AT);
	}

	@Test
	void listensOnlyAfterTheStartAndExtensionTransactionsCommit()
		throws Exception {
		assertAfterCommit("onAuctionStarted", AuctionStartedEvent.class);
		assertAfterCommit("onAuctionExtended", AuctionExtendedEvent.class);
	}

	private static void assertAfterCommit(
		String methodName,
		Class<?> eventType
	) throws Exception {
		Method listenerMethod = AuctionSettlementDeadlineEventListener.class
			.getMethod(methodName, eventType);
		TransactionalEventListener annotation = listenerMethod.getAnnotation(
			TransactionalEventListener.class
		);

		assertThat(annotation).isNotNull();
		assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
	}
}
