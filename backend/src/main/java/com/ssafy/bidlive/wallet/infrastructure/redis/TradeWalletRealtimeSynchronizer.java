package com.ssafy.bidlive.wallet.infrastructure.redis;

import com.ssafy.bidlive.wallet.application.TradeWalletBalanceChangedEvent;
import com.ssafy.bidlive.wallet.application.TradeWalletRealtimeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 커밋된 TRADE 지갑 DB 변경만 Redis 실시간 잔액에 반영한다. */
@Component
public class TradeWalletRealtimeSynchronizer {

	private static final Logger log = LoggerFactory.getLogger(
		TradeWalletRealtimeSynchronizer.class
	);

	private final TradeWalletRealtimeState realtimeState;

	public TradeWalletRealtimeSynchronizer(
		TradeWalletRealtimeState realtimeState
	) {
		this.realtimeState = realtimeState;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void synchronize(TradeWalletBalanceChangedEvent event) {
		try {
			realtimeState.synchronizeCommittedChange(event);
		} catch (RuntimeException exception) {
			log.error(
				"TRADE wallet Redis synchronization failed: "
					+ "memberId={}, ledgerId={}, failureType={}",
				event.memberId(),
				event.ledgerId(),
				exception.getClass().getSimpleName()
			);
		}
	}
}
