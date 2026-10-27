package com.ssafy.bidlive.wallet.infrastructure.redis;

import com.ssafy.bidlive.wallet.application.BettingWalletBalanceChangedEvent;
import com.ssafy.bidlive.wallet.application.BettingWalletRealtimeState;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 커밋된 BETTING 배당·환불 증가분을 Redis 가용 잔액에 반영한다. */
@Component
public class BettingWalletRealtimeSynchronizer {

	private final BettingWalletRealtimeState realtimeState;

	public BettingWalletRealtimeSynchronizer(
		BettingWalletRealtimeState realtimeState
	) {
		this.realtimeState = realtimeState;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void synchronize(BettingWalletBalanceChangedEvent event) {
		realtimeState.increaseAvailableBalance(
			event.memberId(),
			event.availableDelta()
		);
	}
}
