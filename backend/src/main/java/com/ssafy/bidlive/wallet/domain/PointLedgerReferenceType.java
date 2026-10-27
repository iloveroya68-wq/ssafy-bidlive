package com.ssafy.bidlive.wallet.domain;

/** 포인트 증감이 발생한 원인 도메인이다. */
public enum PointLedgerReferenceType {
	BID,
	AUCTION,
	DEAL,
	PREDICTION,
	PAYMENT,
	REWARD,
	ADMIN
}
