package com.ssafy.bidlive.member.application;

import static com.ssafy.bidlive.member.domain.SettlementAccountUpdateErrorCode.INVALID_ACCOUNT_NUMBER;
import static com.ssafy.bidlive.member.domain.SettlementAccountUpdateErrorCode.INVALID_BANK_CODE;
import static com.ssafy.bidlive.member.domain.SettlementAccountUpdateErrorCode.MEMBER_NOT_FOUND;
import static com.ssafy.bidlive.member.domain.SettlementAccountUpdateErrorCode.SETTLEMENT_ACCOUNT_UPDATE_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import com.ssafy.bidlive.member.domain.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 정산 계좌 등록·변경의 검증, 암호화, 멱등 저장과 실패 변환을 확인한다. */
@ExtendWith(MockitoExtension.class)
class SettlementAccountUpdateServiceTest {

	private static final Long MEMBER_ID = 25L;
	private static final Instant UPDATED_AT =
		Instant.parse("2026-07-27T09:00:00Z");

	@Mock
	private MemberPersistence memberPersistence;

	@Mock
	private SettlementAccountUpdatePersistence accountPersistence;

	@Mock
	private SettlementAccountEncryptor accountEncryptor;

	private SettlementAccountUpdateService service;

	@BeforeEach
	void setUp() {
		service = new SettlementAccountUpdateService(
			memberPersistence,
			accountPersistence,
			accountEncryptor
		);
	}

	@Test
	void normalizesEncryptsAndSavesAccount() {
		Member member = Member.createOAuthMember();
		EncryptedAccountNumber encrypted =
			new EncryptedAccountNumber("ciphertext", "iv", "v1");
		SettlementAccountUpdateSnapshot saved = snapshot(
			"004",
			"ciphertext",
			"iv",
			"v1",
			"123-****-**012",
			"신 희원",
			false
		);
		when(memberPersistence.findByIdForUpdate(MEMBER_ID))
			.thenReturn(Optional.of(member));
		when(accountPersistence.findByMemberIdForUpdate(MEMBER_ID))
			.thenReturn(Optional.empty());
		when(accountEncryptor.encrypt(
			MEMBER_ID,
			"004",
			"123456789012"
		)).thenReturn(encrypted);
		when(accountPersistence.save(
			MEMBER_ID,
			"004",
			encrypted,
			"123-****-**012",
			"신 희원"
		)).thenReturn(saved);

		SettlementAccountResult result = service.update(
			MEMBER_ID,
			new SettlementAccountUpdateCommand(
				"004",
				"123-456-789-012",
				" 신  희원 "
			)
		);

		assertThat(result)
			.extracting(
				SettlementAccountResult::bankName,
				SettlementAccountResult::maskedAccountNumber,
				SettlementAccountResult::accountHolderName,
				SettlementAccountResult::verified
			)
			.containsExactly(
				"KB국민은행",
				"123-****-**012",
				"신 희원",
				false
			);
	}

	@Test
	void returnsExistingSnapshotWithoutReencryptingSameAccount() {
		Member member = Member.createOAuthMember();
		SettlementAccountUpdateSnapshot existing = snapshot(
			"004",
			"ciphertext",
			"iv",
			"v1",
			"123-****-**012",
			"신희원",
			true
		);
		when(memberPersistence.findByIdForUpdate(MEMBER_ID))
			.thenReturn(Optional.of(member));
		when(accountPersistence.findByMemberIdForUpdate(MEMBER_ID))
			.thenReturn(Optional.of(existing));
		when(accountEncryptor.decrypt(
			MEMBER_ID,
			"004",
			existing.encryptedAccountNumberValue()
		)).thenReturn("123456789012");

		SettlementAccountResult result = service.update(
			MEMBER_ID,
			new SettlementAccountUpdateCommand(
				"004",
				"123456789012",
				"신희원"
			)
		);

		assertThat(result.updatedAt()).isEqualTo(UPDATED_AT);
		assertThat(result.verified()).isTrue();
		verify(accountEncryptor, never()).encrypt(
			any(),
			anyString(),
			anyString()
		);
		verify(accountPersistence, never()).save(
			any(),
			anyString(),
			any(),
			anyString(),
			anyString()
		);
	}

	@Test
	void rejectsUnsupportedBankAndInvalidAccountInformation() {
		assertThatThrownBy(() ->
			service.update(
				MEMBER_ID,
				new SettlementAccountUpdateCommand(
					"999",
					"123456789012",
					"신희원"
				)
			)
		).isInstanceOfSatisfying(
			SettlementAccountUpdateException.class,
			exception -> assertThat(exception.getErrorCode())
				.isEqualTo(INVALID_BANK_CODE)
		);

		assertThatThrownBy(() ->
			service.update(
				MEMBER_ID,
				new SettlementAccountUpdateCommand(
					"004",
					"1234",
					" "
				)
			)
		).isInstanceOfSatisfying(
			SettlementAccountUpdateException.class,
			exception -> assertThat(exception.getErrorCode())
				.isEqualTo(INVALID_ACCOUNT_NUMBER)
		);
	}

	@Test
	void rejectsMissingMember() {
		when(memberPersistence.findByIdForUpdate(MEMBER_ID))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() ->
			service.update(MEMBER_ID, validCommand())
		).isInstanceOfSatisfying(
			SettlementAccountUpdateException.class,
			exception -> assertThat(exception.getErrorCode())
				.isEqualTo(MEMBER_NOT_FOUND)
		);
	}

	@Test
	void convertsEncryptionFailureToServiceUnavailable() {
		when(memberPersistence.findByIdForUpdate(MEMBER_ID))
			.thenReturn(Optional.of(Member.createOAuthMember()));
		when(accountPersistence.findByMemberIdForUpdate(MEMBER_ID))
			.thenReturn(Optional.empty());
		when(accountEncryptor.encrypt(
			MEMBER_ID,
			"004",
			"123456789012"
		)).thenThrow(new IllegalStateException("key unavailable"));

		assertThatThrownBy(() ->
			service.update(MEMBER_ID, validCommand())
		).isInstanceOfSatisfying(
			SettlementAccountUpdateException.class,
			exception -> assertThat(exception.getErrorCode())
				.isEqualTo(SETTLEMENT_ACCOUNT_UPDATE_FAILED)
		);
	}

	private SettlementAccountUpdateCommand validCommand() {
		return new SettlementAccountUpdateCommand(
			"004",
			"123456789012",
			"신희원"
		);
	}

	private SettlementAccountUpdateSnapshot snapshot(
		String bankCode,
		String encryptedAccountNumber,
		String encryptionIv,
		String encryptionKeyVersion,
		String maskedAccountNumber,
		String accountHolderName,
		boolean verified
	) {
		return new SettlementAccountUpdateSnapshot(
			bankCode,
			encryptedAccountNumber,
			encryptionIv,
			encryptionKeyVersion,
			maskedAccountNumber,
			accountHolderName,
			verified,
			UPDATED_AT
		);
	}
}
