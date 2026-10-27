package com.ssafy.bidlive.wallet.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.ssafy.bidlive.wallet.application.BettingPointBetPersistence.LockedWallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** DB의 BETTING 가용 잔액이 Redis 최초 적재 Port로 전달되는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class BettingWalletRealtimeInitializationServiceTest {

	@Mock
	private BettingPointBetPersistence persistence;

	@Mock
	private BettingWalletRealtimeState realtimeState;

	private BettingWalletRealtimeInitializationService service;

	@BeforeEach
	void setUp() {
		service = new BettingWalletRealtimeInitializationService(
			persistence,
			realtimeState
		);
	}

	@Test
	void initializesRealtimeBettingWalletFromLockedDatabaseSnapshot() {
		when(realtimeState.isInitialized(25L)).thenReturn(false);
		when(persistence.lockBettingWallet(25L)).thenReturn(Optional.of(
			new LockedWallet(101L, 25L, 2_000L, 0L)
		));

		service.initializeIfAbsent(25L);

		InOrder order = inOrder(realtimeState, persistence);
		order.verify(realtimeState).isInitialized(25L);
		order.verify(persistence).lockBettingWallet(25L);
		order.verify(realtimeState).initializeIfAbsent(25L, 2_000L);
	}

	@Test
	void skipsDatabaseWhenRealtimeBettingWalletAlreadyExists() {
		when(realtimeState.isInitialized(25L)).thenReturn(true);

		service.initializeIfAbsent(25L);

		verifyNoInteractions(persistence);
		verify(realtimeState).isInitialized(25L);
		verifyNoMoreInteractions(realtimeState);
	}

	@Test
	void rejectsMissingBettingWallet() {
		when(realtimeState.isInitialized(25L)).thenReturn(false);
		when(persistence.lockBettingWallet(25L))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.initializeIfAbsent(25L))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("BETTING wallet not found");
	}
}
