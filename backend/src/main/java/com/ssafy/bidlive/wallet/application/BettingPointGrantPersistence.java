package com.ssafy.bidlive.wallet.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.ssafy.bidlive.member.domain.MemberStatus;
import com.ssafy.bidlive.wallet.domain.BettingPointGrantType;

/** 일반 보상 이력, BETTING 지갑과 GRANT 원장을 다루는 영속성 Port다. */
public interface BettingPointGrantPersistence {

	void lockGrantKey(
		Long memberId,
		BettingPointGrantType grantType,
		String sourceId
	);

	Optional<GrantRecord> findByBusinessKey(
		Long memberId,
		BettingPointGrantType grantType,
		String sourceId
	);

	Optional<MemberRecord> lockMember(Long memberId);

	Optional<LockedBettingWallet> lockBettingWallet(Long memberId);

	GrantRecord save(NewGrant grant);

	record MemberRecord(
		Long memberId,
		MemberStatus status
	) {
	}

	record LockedBettingWallet(
		Long walletId,
		Long memberId,
		long availableBalance,
		long heldBalance
	) {
	}

	record NewGrant(
		UUID grantId,
		Long walletId,
		Long memberId,
		BettingPointGrantType grantType,
		String sourceId,
		long amount,
		String policyVersion,
		Instant occurredAt,
		long availableBalanceAfter,
		long heldBalanceAfter
	) {
	}

	record GrantRecord(
		UUID grantId,
		Long memberId,
		BettingPointGrantType grantType,
		String sourceId,
		long amount,
		String policyVersion,
		long balanceAfter,
		Long ledgerId,
		Instant grantedAt
	) {
	}
}
