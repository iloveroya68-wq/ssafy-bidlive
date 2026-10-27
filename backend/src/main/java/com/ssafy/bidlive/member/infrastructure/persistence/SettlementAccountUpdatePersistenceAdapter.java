package com.ssafy.bidlive.member.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.member.application.EncryptedAccountNumber;
import com.ssafy.bidlive.member.application.SettlementAccountUpdatePersistence;
import com.ssafy.bidlive.member.application.SettlementAccountUpdateSnapshot;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** 회원별 정산 계좌 한 행을 잠금 조회하고 등록·변경한다. */
@Repository
public class SettlementAccountUpdatePersistenceAdapter
	implements SettlementAccountUpdatePersistence {

	private static final String SELECT_FOR_UPDATE = """
		SELECT
			bank_code,
			encrypted_account_number,
			encryption_iv,
			encryption_key_version,
			masked_account_number,
			account_holder_name,
			verified,
			updated_at
		FROM settlement_account
		WHERE member_id = :memberId
		FOR UPDATE
		""";

	private static final String INSERT_ACCOUNT = """
		INSERT INTO settlement_account (
			member_id,
			bank_code,
			encrypted_account_number,
			encryption_iv,
			encryption_key_version,
			masked_account_number,
			account_holder_name,
			verified
		)
		VALUES (
			:memberId,
			:bankCode,
			:encryptedAccountNumber,
			:encryptionIv,
			:encryptionKeyVersion,
			:maskedAccountNumber,
			:accountHolderName,
			FALSE
		)
		""";

	private static final String UPDATE_ACCOUNT = """
		UPDATE settlement_account
		SET
			bank_code = :bankCode,
			encrypted_account_number = :encryptedAccountNumber,
			encryption_iv = :encryptionIv,
			encryption_key_version = :encryptionKeyVersion,
			masked_account_number = :maskedAccountNumber,
			account_holder_name = :accountHolderName,
			verified = FALSE
		WHERE member_id = :memberId
		""";

	private final EntityManager entityManager;

	public SettlementAccountUpdatePersistenceAdapter(
		EntityManager entityManager
	) {
		this.entityManager = entityManager;
	}

	@Override
	public Optional<SettlementAccountUpdateSnapshot> findByMemberIdForUpdate(
		Long memberId
	) {
		List<?> rows = entityManager.createNativeQuery(SELECT_FOR_UPDATE)
			.setParameter("memberId", memberId)
			.setMaxResults(1)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(toSnapshot((Object[]) rows.getFirst()));
	}

	@Override
	public SettlementAccountUpdateSnapshot save(
		Long memberId,
		String bankCode,
		EncryptedAccountNumber encryptedAccountNumber,
		String maskedAccountNumber,
		String accountHolderName
	) {
		int updatedRows = entityManager.createNativeQuery(UPDATE_ACCOUNT)
			.setParameter("memberId", memberId)
			.setParameter("bankCode", bankCode)
			.setParameter(
				"encryptedAccountNumber",
				encryptedAccountNumber.ciphertext()
			)
			.setParameter("encryptionIv", encryptedAccountNumber.iv())
			.setParameter(
				"encryptionKeyVersion",
				encryptedAccountNumber.keyVersion()
			)
			.setParameter("maskedAccountNumber", maskedAccountNumber)
			.setParameter("accountHolderName", accountHolderName)
			.executeUpdate();
		if (updatedRows == 0) {
			entityManager.createNativeQuery(INSERT_ACCOUNT)
				.setParameter("memberId", memberId)
				.setParameter("bankCode", bankCode)
				.setParameter(
					"encryptedAccountNumber",
					encryptedAccountNumber.ciphertext()
				)
				.setParameter("encryptionIv", encryptedAccountNumber.iv())
				.setParameter(
					"encryptionKeyVersion",
					encryptedAccountNumber.keyVersion()
				)
				.setParameter("maskedAccountNumber", maskedAccountNumber)
				.setParameter("accountHolderName", accountHolderName)
				.executeUpdate();
		}
		return findByMemberIdForUpdate(memberId)
			.orElseThrow(() -> new IllegalStateException(
				"saved settlement account is unavailable"
			));
	}

	private SettlementAccountUpdateSnapshot toSnapshot(Object[] row) {
		return new SettlementAccountUpdateSnapshot(
			(String) row[0],
			(String) row[1],
			(String) row[2],
			(String) row[3],
			(String) row[4],
			(String) row[5],
			(Boolean) row[6],
			toInstant(row[7])
		);
	}

	private Instant toInstant(Object value) {
		return switch (value) {
			case Instant instant -> instant;
			case Timestamp timestamp -> timestamp.toInstant();
			case OffsetDateTime offsetDateTime -> offsetDateTime.toInstant();
			default -> throw new IllegalStateException(
				"지원하지 않는 시각 타입: " + value.getClass()
			);
		};
	}
}
