package com.ssafy.bidlive.wallet.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.ssafy.bidlive.wallet.application.TradeWalletBalanceChangedEvent;
import com.ssafy.bidlive.wallet.application.TradeWalletRealtimeState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 커밋 후 수신한 지갑 변경 이벤트가 Redis Port로 전달되는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class TradeWalletRealtimeSynchronizerTest {

	@Mock
	private TradeWalletRealtimeState realtimeState;

	@Test
	void delegatesCommittedChangeToRealtimeState() {
		TradeWalletBalanceChangedEvent event = event();

		new TradeWalletRealtimeSynchronizer(realtimeState)
			.synchronize(event);

		verify(realtimeState).synchronizeCommittedChange(event);
	}

	@Test
	void receivesChangesOnlyAfterDatabaseCommit() throws Exception {
		TransactionalEventListener listener =
			TradeWalletRealtimeSynchronizer.class
				.getMethod(
					"synchronize",
					TradeWalletBalanceChangedEvent.class
				)
				.getAnnotation(TransactionalEventListener.class);

		assertThat(listener).isNotNull();
		assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
	}

	@Test
	void doesNotReportDatabaseOperationAsFailedWhenRedisIsUnavailable() {
		doThrow(new IllegalStateException("redis unavailable"))
			.when(realtimeState)
			.synchronizeCommittedChange(event());

		assertThatCode(() -> new TradeWalletRealtimeSynchronizer(
			realtimeState
		).synchronize(event())).doesNotThrowAnyException();
	}

	private TradeWalletBalanceChangedEvent event() {
		return new TradeWalletBalanceChangedEvent(
			25L,
			1002L,
			20_000L,
			-20_000L,
			138_000L,
			62_000L
		);
	}
}
