package com.ssafy.bidlive.member.application;

import static com.ssafy.bidlive.member.domain.SettlementAccountUpdateErrorCode.INVALID_ACCOUNT_NUMBER;
import static com.ssafy.bidlive.member.domain.SettlementAccountUpdateErrorCode.INVALID_BANK_CODE;
import static com.ssafy.bidlive.member.domain.SettlementAccountUpdateErrorCode.MEMBER_NOT_FOUND;
import static com.ssafy.bidlive.member.domain.SettlementAccountUpdateErrorCode.SETTLEMENT_ACCOUNT_UPDATE_FAILED;
import static com.ssafy.bidlive.member.domain.SettlementAccountUpdateErrorCode.UNAUTHORIZED;

import com.ssafy.bidlive.member.domain.SettlementAccountPolicy;
import com.ssafy.bidlive.member.domain.SettlementAccountUpdateErrorCode;
import com.ssafy.bidlive.member.domain.SettlementBank;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 본인 정산 계좌를 암호화해 한 행으로 등록하거나 변경한다. */
@Service
public class SettlementAccountUpdateService {

	private final MemberPersistence memberPersistence;
	private final SettlementAccountUpdatePersistence accountPersistence;
	private final SettlementAccountEncryptor accountEncryptor;

	public SettlementAccountUpdateService(
		MemberPersistence memberPersistence,
		SettlementAccountUpdatePersistence accountPersistence,
		SettlementAccountEncryptor accountEncryptor
	) {
		this.memberPersistence = memberPersistence;
		this.accountPersistence = accountPersistence;
		this.accountEncryptor = accountEncryptor;
	}

	@Transactional
	public SettlementAccountResult update(
		Long memberId,
		SettlementAccountUpdateCommand command
	) {
		validateMemberId(memberId);
		try {
			ValidatedAccount account = validate(command);
			if (memberPersistence.findByIdForUpdate(memberId).isEmpty()) {
				throw error(MEMBER_NOT_FOUND);
			}

			var existing = accountPersistence.findByMemberIdForUpdate(
				memberId
			);
			if (existing.isPresent()
				&& isSameAccount(memberId, account, existing.get())) {
				return toResult(existing.get());
			}

			EncryptedAccountNumber encryptedAccountNumber =
				accountEncryptor.encrypt(
					memberId,
					account.bank().code(),
					account.accountNumber()
				);
			SettlementAccountUpdateSnapshot saved =
				accountPersistence.save(
					memberId,
					account.bank().code(),
					encryptedAccountNumber,
					SettlementAccountPolicy.maskAccountNumber(
						account.accountNumber()
					),
					account.accountHolderName()
				);
			return toResult(saved);
		} catch (SettlementAccountUpdateException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new SettlementAccountUpdateException(
				SETTLEMENT_ACCOUNT_UPDATE_FAILED,
				exception
			);
		}
	}

	private ValidatedAccount validate(
		SettlementAccountUpdateCommand command
	) {
		if (command == null) {
			throw error(INVALID_ACCOUNT_NUMBER);
		}
		SettlementBank bank = bank(command.bankCode());
		String accountNumber =
			SettlementAccountPolicy.normalizeAccountNumber(
				command.accountNumber()
			);
		String accountHolderName =
			SettlementAccountPolicy.normalizeAccountHolderName(
				command.accountHolderName()
			);
		if (!SettlementAccountPolicy.isValidAccountNumber(accountNumber)
			|| !SettlementAccountPolicy.isValidAccountHolderName(
				accountHolderName
			)) {
			throw error(INVALID_ACCOUNT_NUMBER);
		}
		return new ValidatedAccount(
			bank,
			accountNumber,
			accountHolderName
		);
	}

	private SettlementBank bank(String bankCode) {
		if (bankCode == null || bankCode.isBlank()) {
			throw error(INVALID_BANK_CODE);
		}
		try {
			return SettlementBank.fromCode(bankCode.trim());
		} catch (IllegalArgumentException exception) {
			throw new SettlementAccountUpdateException(
				INVALID_BANK_CODE,
				exception
			);
		}
	}

	private boolean isSameAccount(
		Long memberId,
		ValidatedAccount account,
		SettlementAccountUpdateSnapshot existing
	) {
		if (!existing.hasEncryptedAccountNumber()
			|| !existing.bankCode().equals(account.bank().code())
			|| !existing.accountHolderName().equals(
				account.accountHolderName()
			)) {
			return false;
		}
		String decryptedAccountNumber = accountEncryptor.decrypt(
			memberId,
			existing.bankCode(),
			existing.encryptedAccountNumberValue()
		);
		return decryptedAccountNumber.equals(account.accountNumber());
	}

	private SettlementAccountResult toResult(
		SettlementAccountUpdateSnapshot snapshot
	) {
		SettlementBank bank = SettlementBank.fromCode(snapshot.bankCode());
		return new SettlementAccountResult(
			bank.code(),
			bank.displayName(),
			snapshot.maskedAccountNumber(),
			snapshot.accountHolderName(),
			snapshot.verified(),
			snapshot.updatedAt()
		);
	}

	private void validateMemberId(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw error(UNAUTHORIZED);
		}
	}

	private SettlementAccountUpdateException error(
		SettlementAccountUpdateErrorCode errorCode
	) {
		return new SettlementAccountUpdateException(errorCode);
	}

	private record ValidatedAccount(
		SettlementBank bank,
		String accountNumber,
		String accountHolderName
	) {
	}
}
