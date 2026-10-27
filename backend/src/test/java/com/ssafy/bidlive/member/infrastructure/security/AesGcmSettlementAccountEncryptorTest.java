package com.ssafy.bidlive.member.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.ssafy.bidlive.member.application.EncryptedAccountNumber;
import org.junit.jupiter.api.Test;

/** 계좌번호 AES-GCM 암복호화와 문맥 결합·키 검증을 확인한다. */
class AesGcmSettlementAccountEncryptorTest {

	private static final String KEY_BASE64 = Base64.getEncoder()
		.encodeToString(
			"0123456789abcdef0123456789abcdef"
				.getBytes(StandardCharsets.UTF_8)
		);

	@Test
	void encryptsAndDecryptsWithRandomIv() {
		AesGcmSettlementAccountEncryptor encryptor =
			new AesGcmSettlementAccountEncryptor(KEY_BASE64, "v1");

		EncryptedAccountNumber first = encryptor.encrypt(
			25L,
			"004",
			"123456789012"
		);
		EncryptedAccountNumber second = encryptor.encrypt(
			25L,
			"004",
			"123456789012"
		);

		assertThat(first.ciphertext()).doesNotContain("123456789012");
		assertThat(first.iv()).isNotEqualTo(second.iv());
		assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
		assertThat(encryptor.decrypt(25L, "004", first))
			.isEqualTo("123456789012");
	}

	@Test
	void rejectsDifferentMemberBankOrKeyVersion() {
		AesGcmSettlementAccountEncryptor encryptor =
			new AesGcmSettlementAccountEncryptor(KEY_BASE64, "v1");
		EncryptedAccountNumber encrypted = encryptor.encrypt(
			25L,
			"004",
			"123456789012"
		);

		assertThatThrownBy(() ->
			encryptor.decrypt(26L, "004", encrypted)
		).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() ->
			encryptor.decrypt(25L, "088", encrypted)
		).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() ->
			encryptor.decrypt(
				25L,
				"004",
				new EncryptedAccountNumber(
					encrypted.ciphertext(),
					encrypted.iv(),
					"v2"
				)
			)
		).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void rejectsMissingOrWrongSizedKey() {
		AesGcmSettlementAccountEncryptor missingKey =
			new AesGcmSettlementAccountEncryptor("", "v1");
		AesGcmSettlementAccountEncryptor shortKey =
			new AesGcmSettlementAccountEncryptor(
				Base64.getEncoder().encodeToString(new byte[16]),
				"v1"
			);

		assertThatThrownBy(() ->
			missingKey.encrypt(25L, "004", "123456789012")
		).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() ->
			shortKey.encrypt(25L, "004", "123456789012")
		).isInstanceOf(IllegalStateException.class);
	}
}
