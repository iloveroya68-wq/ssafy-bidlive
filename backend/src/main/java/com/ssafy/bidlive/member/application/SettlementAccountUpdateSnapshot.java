package com.ssafy.bidlive.member.application;

import java.time.Instant;

/** 등록·변경 판단에 필요한 정산 계좌 저장 상태다. */
public record SettlementAccountUpdateSnapshot(
	String bankCode,
	String encryptedAccountNumber,
	String encryptionIv,
	String encryptionKeyVersion,
	String maskedAccountNumber,
	String accountHolderName,
	boolean verified,
	Instant updatedAt
) {

	public boolean hasEncryptedAccountNumber() {
		return encryptedAccountNumber != null
			&& encryptionIv != null
			&& encryptionKeyVersion != null;
	}

	public EncryptedAccountNumber encryptedAccountNumberValue() {
		if (!hasEncryptedAccountNumber()) {
			throw new IllegalStateException(
				"encrypted account number is unavailable"
			);
		}
		return new EncryptedAccountNumber(
			encryptedAccountNumber,
			encryptionIv,
			encryptionKeyVersion
		);
	}
}
