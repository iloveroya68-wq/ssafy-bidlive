package com.ssafy.bidlive.wallet.application;

import com.ssafy.bidlive.wallet.domain.PointLedgerReferenceType;

/** 거래 포인트 홀드·해제·정산에 공통으로 사용하는 내부 명령이다. */
public record TradePointOperationCommand(
	PointLedgerReferenceType referenceType,
	Long referenceId,
	Long memberId,
	long amount,
	String idempotencyKey
) {
}
