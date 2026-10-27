package com.ssafy.bidlive.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.SettlementAccountQueryErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 정산 계좌의 은행명 변환과 미등록·실패 처리를 검증한다. */
class SettlementAccountQueryServiceTest {

	private MemberPersistence memberPersistence;
	private SettlementAccountQueryPersistence accountPersistence;
	private SettlementAccountQueryService service;

	@BeforeEach
	void setUp() {
		memberPersistence = mock(MemberPersistence.class);
		accountPersistence = mock(SettlementAccountQueryPersistence.class);
		service = new SettlementAccountQueryService(
			memberPersistence,
			accountPersistence
		);
	}

	@Test
	void returnsMaskedAccountWithServerBankName() {
		Instant updatedAt = Instant.parse("2026-07-27T09:00:00Z");
		when(memberPersistence.findById(25L))
			.thenReturn(Optional.of(mock(Member.class)));
		when(accountPersistence.findByMemberId(25L))
			.thenReturn(Optional.of(new SettlementAccountSnapshot(
				"004",
				"123-****-**789",
				"신희원",
				false,
				updatedAt
			)));

		SettlementAccountQueryResult result = service.query(25L);

		assertThat(result.account())
			.satisfies(account -> {
				assertThat(account.bankCode()).isEqualTo("004");
				assertThat(account.bankName()).isEqualTo("KB국민은행");
				assertThat(account.maskedAccountNumber())
					.isEqualTo("123-****-**789");
				assertThat(account.accountHolderName()).isEqualTo("신희원");
				assertThat(account.verified()).isFalse();
				assertThat(account.updatedAt()).isEqualTo(updatedAt);
			});
	}

	@Test
	void returnsNullAccountWhenMemberHasNotRegisteredOne() {
		when(memberPersistence.findById(25L))
			.thenReturn(Optional.of(mock(Member.class)));
		when(accountPersistence.findByMemberId(25L))
			.thenReturn(Optional.empty());

		SettlementAccountQueryResult result = service.query(25L);

		assertThat(result.account()).isNull();
	}

	@Test
	void rejectsInvalidOrMissingMember() {
		assertError(
			() -> service.query(null),
			SettlementAccountQueryErrorCode.UNAUTHORIZED
		);

		when(memberPersistence.findById(25L)).thenReturn(Optional.empty());
		assertError(
			() -> service.query(25L),
			SettlementAccountQueryErrorCode.MEMBER_NOT_FOUND
		);
	}

	@Test
	void translatesPersistenceFailure() {
		when(memberPersistence.findById(25L))
			.thenReturn(Optional.of(mock(Member.class)));
		when(accountPersistence.findByMemberId(25L))
			.thenThrow(new IllegalStateException("database unavailable"));

		assertError(
			() -> service.query(25L),
			SettlementAccountQueryErrorCode.SETTLEMENT_ACCOUNT_UNAVAILABLE
		);
	}

	@Test
	void translatesUnsupportedBankCodeFailure() {
		when(memberPersistence.findById(25L))
			.thenReturn(Optional.of(mock(Member.class)));
		when(accountPersistence.findByMemberId(25L))
			.thenReturn(Optional.of(new SettlementAccountSnapshot(
				"999",
				"***",
				"신희원",
				false,
				Instant.parse("2026-07-27T09:00:00Z")
			)));

		assertError(
			() -> service.query(25L),
			SettlementAccountQueryErrorCode.SETTLEMENT_ACCOUNT_UNAVAILABLE
		);
	}

	private void assertError(
		org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
		SettlementAccountQueryErrorCode errorCode
	) {
		assertThatThrownBy(callable)
			.isInstanceOf(SettlementAccountQueryException.class)
			.satisfies(exception -> assertThat(
				((SettlementAccountQueryException) exception).getErrorCode()
			).isEqualTo(errorCode));
	}
}
