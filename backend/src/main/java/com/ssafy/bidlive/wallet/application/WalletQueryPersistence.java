package com.ssafy.bidlive.wallet.application;

import java.util.List;

import com.ssafy.bidlive.wallet.domain.WalletType;

/** 회원의 거래·베팅 지갑 잔액을 조회하는 영속성 Port다. */
public interface WalletQueryPersistence {

	List<WalletBalanceSnapshot> findAllByMemberId(Long memberId);

	record WalletBalanceSnapshot(
		WalletType walletType,
		long availableBalance,
		long heldBalance
	) {
	}
}
