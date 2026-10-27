package com.ssafy.bidlive.wallet.application;

import static com.ssafy.bidlive.wallet.application.PointLedgerQueryErrorCode.INVALID_CURSOR;
import static com.ssafy.bidlive.wallet.application.PointLedgerQueryErrorCode.INVALID_LEDGER_FILTER;
import static com.ssafy.bidlive.wallet.application.PointLedgerQueryErrorCode.UNAUTHORIZED;
import static com.ssafy.bidlive.wallet.application.PointLedgerQueryErrorCode.WALLET_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import com.ssafy.bidlive.wallet.domain.PointLedgerReferenceType;
import com.ssafy.bidlive.wallet.domain.PointLedgerTransactionType;
import com.ssafy.bidlive.wallet.domain.WalletType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PointLedgerQueryServiceTest {

	@Mock
	private PointLedgerQueryPersistence persistence;

	private PointLedgerQueryService service;

	@BeforeEach
	void setUp() {
		service = new PointLedgerQueryService(persistence);
	}

	@Test
	void appliesFiltersAndCreatesServerDescription() {
		when(persistence.findWalletTypes(25L))
			.thenReturn(EnumSet.allOf(WalletType.class));
		when(persistence.find(any())).thenReturn(new PointLedgerQueryPage(
			List.of(new PointLedgerQueryRow(
				1001L,
				WalletType.TRADE,
				PointLedgerTransactionType.HOLD,
				-82_000L,
				82_000L,
				118_000L,
				82_000L,
				PointLedgerReferenceType.BID,
				351L,
				Instant.parse("2026-07-20T12:12:44Z")
			)),
			null,
			false
		));

		PointLedgerQueryResult result = service.query(
			25L,
			"trade",
			"hold",
			null,
			20
		);

		assertThat(result.items()).singleElement()
			.satisfies(item -> {
				assertThat(item.description())
					.isEqualTo("경매 입찰 포인트 보류");
				assertThat(item.availableChange()).isEqualTo(-82_000L);
				assertThat(item.heldChange()).isEqualTo(82_000L);
				assertThat(item.referenceType())
					.isEqualTo(PointLedgerReferenceType.BID);
			});
		ArgumentCaptor<PointLedgerQueryCriteria> criteria =
			ArgumentCaptor.forClass(PointLedgerQueryCriteria.class);
		verify(persistence).find(criteria.capture());
		assertThat(criteria.getValue().walletType())
			.isEqualTo(WalletType.TRADE);
		assertThat(criteria.getValue().transactionType())
			.isEqualTo(PointLedgerTransactionType.HOLD);
		assertThat(criteria.getValue().size()).isEqualTo(20);
	}

	@Test
	void requiresBothWalletsWhenWalletFilterIsOmitted() {
		when(persistence.findWalletTypes(25L))
			.thenReturn(EnumSet.of(WalletType.TRADE));

		assertError(
			() -> service.query(25L, null, null, null, null),
			WALLET_NOT_FOUND
		);

		verify(persistence, never()).find(any());
	}

	@Test
	void onlyRequiresTheFilteredWallet() {
		when(persistence.findWalletTypes(25L))
			.thenReturn(EnumSet.of(WalletType.TRADE));
		when(persistence.find(any())).thenReturn(
			new PointLedgerQueryPage(List.of(), null, false)
		);

		PointLedgerQueryResult result = service.query(
			25L,
			"TRADE",
			null,
			null,
			null
		);

		assertThat(result.items()).isEmpty();
	}

	@Test
	void rejectsInvalidAuthenticationBeforePersistenceAccess() {
		assertError(
			() -> service.query(null, null, null, null, null),
			UNAUTHORIZED
		);
		assertError(
			() -> service.query(0L, null, null, null, null),
			UNAUTHORIZED
		);

		verifyNoInteractions(persistence);
	}

	@Test
	void rejectsInvalidFiltersCursorAndSize() {
		assertError(
			() -> service.query(25L, "CASH", null, null, 20),
			INVALID_LEDGER_FILTER
		);
		assertError(
			() -> service.query(25L, null, "UNKNOWN", null, 20),
			INVALID_LEDGER_FILTER
		);
		assertError(
			() -> service.query(25L, null, null, "broken", 20),
			INVALID_CURSOR
		);
		assertError(
			() -> service.query(25L, null, null, null, 101),
			INVALID_LEDGER_FILTER
		);

		verifyNoInteractions(persistence);
	}

	private void assertError(
		org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
		PointLedgerQueryErrorCode errorCode
	) {
		assertThatThrownBy(callable)
			.isInstanceOfSatisfying(
				PointLedgerQueryException.class,
				exception -> assertThat(exception.getErrorCode())
					.isEqualTo(errorCode)
			);
	}
}
