package com.ssafy.bidlive.wallet.application;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.ssafy.bidlive.wallet.application.TradePointOperationPersistence.LockedTradeWallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

/** DB의 TRADE 지갑 스냅샷이 Redis 최초 적재 Port로 전달되는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class TradeWalletRealtimeInitializationServiceTest {

	@Mock
	private TradePointOperationPersistence persistence;

	@Mock
	private TradeWalletRealtimeState realtimeState;

	private TradeWalletRealtimeInitializationService service;

	@BeforeEach
	void setUp() {
		service = new TradeWalletRealtimeInitializationService(
			persistence,
			realtimeState
		);
	}

	@Test
	void initializesRealtimeTradeWalletFromLockedDatabaseSnapshot() {
		when(realtimeState.isInitialized(25L)).thenReturn(false);
		when(persistence.lockTradeWallet(25L)).thenReturn(Optional.of(
			new LockedTradeWallet(101L, 25L, 180_000L, 20_000L)
		));

		service.initializeIfAbsent(25L);

		InOrder order = inOrder(realtimeState, persistence);
		order.verify(realtimeState).isInitialized(25L);
		order.verify(persistence).lockTradeWallet(25L);
		order.verify(realtimeState).initializeIfAbsent(
			25L,
			180_000L,
			20_000L
		);
	}

	@Test
	void skipsDatabaseWhenRealtimeTradeWalletAlreadyExists() {
		when(realtimeState.isInitialized(25L)).thenReturn(true);

		service.initializeIfAbsent(25L);

		verifyNoInteractions(persistence);
		verify(realtimeState).isInitialized(25L);
		verifyNoMoreInteractions(realtimeState);
	}
}
