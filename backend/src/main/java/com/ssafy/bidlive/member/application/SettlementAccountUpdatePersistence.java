package com.ssafy.bidlive.member.application;

import java.util.Optional;

/** 정산 계좌의 잠금 조회와 회원별 단일 행 저장을 추상화한다. */
public interface SettlementAccountUpdatePersistence {

	Optional<SettlementAccountUpdateSnapshot> findByMemberIdForUpdate(
		Long memberId
	);

	SettlementAccountUpdateSnapshot save(
		Long memberId,
		String bankCode,
		EncryptedAccountNumber encryptedAccountNumber,
		String maskedAccountNumber,
		String accountHolderName
	);
}
