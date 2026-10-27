package com.ssafy.bidlive.member.application;

/** 암호화된 계좌번호와 복호화에 필요한 메타데이터다. */
public record EncryptedAccountNumber(
	String ciphertext,
	String iv,
	String keyVersion
) {
}
