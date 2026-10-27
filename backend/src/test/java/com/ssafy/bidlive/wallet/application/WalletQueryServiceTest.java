package com.ssafy.bidlive.wallet.application;

import static com.ssafy.bidlive.wallet.application.WalletQueryErrorCode.MEMBER_NOT_FOUND;
import static com.ssafy.bidlive.wallet.application.WalletQueryErrorCode.UNAUTHORIZED;
import static com.ssafy.bidlive.wallet.application.WalletQueryErrorCode.WALLET_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.wallet.application.WalletQueryPersistence.WalletBalanceSnapshot;
import com.ssafy.bidlive.wallet.domain.WalletType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 이중 지갑 조회의 타입 매핑, 총액 계산과 실패 조건을 검증한다. */
@ExtendWith(MockitoExtension.class)
class WalletQueryServiceTest {

	@Mock
	private MemberPersistence memberPersistence;

	@Mock
	private WalletQueryPersistence walletPersistence;

	private WalletQueryService service;

	@BeforeEach
	void setUp() {
		service = new WalletQueryService(
			memberPersistence,
			walletPersistence
		);
	}

	@Test
	void returnsTradeAndBettingWalletSummariesByType() {
		when(memberPersistence.findById(25L))
			.thenReturn(Optional.of(mock(Member.class)));
		when(walletPersistence.findAllByMemberId(25L))
			.thenReturn(List.of(
				new WalletBalanceSnapshot(
					WalletType.BETTING,
					4_500L,
					500L
				),
				new WalletBalanceSnapshot(
					WalletType.TRADE,
					118_000L,
					82_000L
				)
			));

		WalletQueryResult result = service.query(25L);

		assertThat(result.tradeWallet().balance()).isEqualTo(200_000L);
		assertThat(result.tradeWallet().heldAmount()).isEqualTo(82_000L);
		assertThat(result.tradeWallet().availableAmount())
			.isEqualTo(118_000L);
		assertThat(result.bettingWallet().balance()).isEqualTo(5_000L);
		assertThat(result.bettingWallet().heldAmount()).isEqualTo(500L);
		assertThat(result.bettingWallet().availableAmount())
			.isEqualTo(4_500L);
	}

	@Test
	void rejectsInvalidAuthenticationBeforePersistenceAccess() {
		assertError(null, UNAUTHORIZED);
		assertError(0L, UNAUTHORIZED);

		verifyNoInteractions(memberPersistence, walletPersistence);
	}

	@Test
	void rejectsMissingMemberBeforeWalletLookup() {
		when(memberPersistence.findById(25L)).thenReturn(Optional.empty());

		assertError(25L, MEMBER_NOT_FOUND);

		verifyNoInteractions(walletPersistence);
	}

	@Test
	void rejectsWhenEitherRequiredWalletIsMissing() {
		when(memberPersistence.findById(25L))
			.thenReturn(Optional.of(mock(Member.class)));
		when(walletPersistence.findAllByMemberId(25L))
			.thenReturn(List.of(new WalletBalanceSnapshot(
				WalletType.TRADE,
				100_000L,
				0L
			)));

		assertError(25L, WALLET_NOT_FOUND);
	}

	private void assertError(
		Long memberId,
		WalletQueryErrorCode errorCode
	) {
		assertThatThrownBy(() -> service.query(memberId))
			.isInstanceOfSatisfying(
				WalletQueryException.class,
				exception -> assertThat(exception.getErrorCode())
					.isEqualTo(errorCode)
			);
	}
}
