package com.ssafy.bidlive.wallet.application;

import java.time.Instant;

import com.ssafy.bidlive.wallet.domain.BettingPointGrantType;

/** 내부 모듈이 일반 베팅 포인트 보상을 요청할 때 사용하는 명령이다. */
public record BettingPointGrantCommand(
	Long memberId,
	BettingPointGrantType grantType,
	String sourceId,
	long amount,
	String policyVersion,
	Instant occurredAt
) {
}
