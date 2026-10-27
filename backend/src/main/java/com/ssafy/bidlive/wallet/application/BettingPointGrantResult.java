package com.ssafy.bidlive.wallet.application;

import java.time.Instant;
import java.util.UUID;

import com.ssafy.bidlive.wallet.domain.BettingPointGrantType;

/** 일반 베팅 포인트 지급 또는 중복 조회 결과다. */
public record BettingPointGrantResult(
	UUID grantId,
	Long memberId,
	BettingPointGrantType grantType,
	long amount,
	long balanceAfter,
	Long ledgerId,
	boolean duplicated,
	Instant grantedAt
) {
}
