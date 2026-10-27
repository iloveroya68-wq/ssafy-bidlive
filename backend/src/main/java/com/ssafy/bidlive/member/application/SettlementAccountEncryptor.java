package com.ssafy.bidlive.member.application;

/** 계좌번호 원문을 애플리케이션 밖으로 노출하지 않고 암복호화한다. */
public interface SettlementAccountEncryptor {

	EncryptedAccountNumber encrypt(
		Long memberId,
		String bankCode,
		String accountNumber
	);

	String decrypt(
		Long memberId,
		String bankCode,
		EncryptedAccountNumber encryptedAccountNumber
	);
}
