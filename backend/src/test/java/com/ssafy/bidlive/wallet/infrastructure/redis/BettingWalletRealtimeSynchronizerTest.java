package com.ssafy.bidlive.wallet.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.ssafy.bidlive.wallet.application.BettingWalletBalanceChangedEvent;
import com.ssafy.bidlive.wallet.application.BettingWalletRealtimeState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** DB 커밋 후 BETTING 증가분이 Redis Port로 전달되는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class BettingWalletRealtimeSynchronizerTest {

	@Mock
	private BettingWalletRealtimeState realtimeState;

	@Test
	void delegatesCommittedIncreaseToRealtimeState() {
		BettingWalletBalanceChangedEvent event =
			new BettingWalletBalanceChangedEvent(25L, 1_500L);

		new BettingWalletRealtimeSynchronizer(realtimeState)
			.synchronize(event);

		verify(realtimeState).increaseAvailableBalance(25L, 1_500L);
	}

	@Test
	void receivesChangesOnlyAfterDatabaseCommit() throws Exception {
		TransactionalEventListener listener =
			BettingWalletRealtimeSynchronizer.class
				.getMethod(
					"synchronize",
					BettingWalletBalanceChangedEvent.class
				)
				.getAnnotation(TransactionalEventListener.class);

		assertThat(listener).isNotNull();
		assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
	}
}
