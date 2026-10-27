package com.ssafy.bidlive.member.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.ssafy.bidlive.member.application.EncryptedAccountNumber;
import com.ssafy.bidlive.member.application.SettlementAccountEncryptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** AES-GCM으로 계좌번호의 기밀성과 무결성을 함께 보호한다. */
@Component
public class AesGcmSettlementAccountEncryptor
	implements SettlementAccountEncryptor {

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final String KEY_ALGORITHM = "AES";
	private static final int REQUIRED_KEY_BYTES = 32;
	private static final int IV_BYTES = 12;
	private static final int AUTHENTICATION_TAG_BITS = 128;

	private final String encodedKey;
	private final String keyVersion;
	private final SecureRandom secureRandom;

	public AesGcmSettlementAccountEncryptor(
		@Value("${SETTLEMENT_ACCOUNT_ENCRYPTION_KEY_BASE64:}")
		String encodedKey,
		@Value("${SETTLEMENT_ACCOUNT_ENCRYPTION_KEY_VERSION:v1}")
		String keyVersion
	) {
		this.encodedKey = encodedKey;
		this.keyVersion = keyVersion;
		this.secureRandom = new SecureRandom();
	}

	@Override
	public EncryptedAccountNumber encrypt(
		Long memberId,
		String bankCode,
		String accountNumber
	) {
		try {
			byte[] iv = new byte[IV_BYTES];
			secureRandom.nextBytes(iv);
			Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv);
			cipher.updateAAD(aad(memberId, bankCode));
			byte[] ciphertext = cipher.doFinal(
				accountNumber.getBytes(StandardCharsets.UTF_8)
			);
			return new EncryptedAccountNumber(
				Base64.getEncoder().encodeToString(ciphertext),
				Base64.getEncoder().encodeToString(iv),
				validatedKeyVersion()
			);
		} catch (GeneralSecurityException | IllegalArgumentException exception) {
			throw encryptionFailure(exception);
		}
	}

	@Override
	public String decrypt(
		Long memberId,
		String bankCode,
		EncryptedAccountNumber encryptedAccountNumber
	) {
		try {
			if (!validatedKeyVersion().equals(
				encryptedAccountNumber.keyVersion()
			)) {
				throw new IllegalStateException(
					"settlement account encryption key version is unavailable"
				);
			}
			byte[] iv = Base64.getDecoder().decode(
				encryptedAccountNumber.iv()
			);
			if (iv.length != IV_BYTES) {
				throw new IllegalStateException(
					"settlement account encryption IV is invalid"
				);
			}
			Cipher cipher = cipher(Cipher.DECRYPT_MODE, iv);
			cipher.updateAAD(aad(memberId, bankCode));
			byte[] plaintext = cipher.doFinal(
				Base64.getDecoder().decode(
					encryptedAccountNumber.ciphertext()
				)
			);
			return new String(plaintext, StandardCharsets.UTF_8);
		} catch (GeneralSecurityException | IllegalArgumentException exception) {
			throw encryptionFailure(exception);
		}
	}

	private Cipher cipher(int mode, byte[] iv)
		throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance(TRANSFORMATION);
		cipher.init(
			mode,
			secretKey(),
			new GCMParameterSpec(AUTHENTICATION_TAG_BITS, iv)
		);
		return cipher;
	}

	private SecretKey secretKey() {
		if (encodedKey == null || encodedKey.isBlank()) {
			throw new IllegalStateException(
				"settlement account encryption key is unavailable"
			);
		}
		byte[] key;
		try {
			key = Base64.getDecoder().decode(encodedKey);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException(
				"settlement account encryption key is invalid",
				exception
			);
		}
		if (key.length != REQUIRED_KEY_BYTES) {
			throw new IllegalStateException(
				"settlement account encryption key must be 32 bytes"
			);
		}
		return new SecretKeySpec(key, KEY_ALGORITHM);
	}

	private String validatedKeyVersion() {
		if (keyVersion == null || keyVersion.isBlank()) {
			throw new IllegalStateException(
				"settlement account encryption key version is unavailable"
			);
		}
		return keyVersion.trim();
	}

	private byte[] aad(Long memberId, String bankCode) {
		if (memberId == null || bankCode == null) {
			throw new IllegalArgumentException(
				"settlement account encryption context is invalid"
			);
		}
		return (memberId + ":" + bankCode)
			.getBytes(StandardCharsets.UTF_8);
	}

	private IllegalStateException encryptionFailure(Throwable cause) {
		if (cause instanceof IllegalStateException illegalStateException) {
			return illegalStateException;
		}
		return new IllegalStateException(
			"settlement account encryption operation failed",
			cause
		);
	}
}
