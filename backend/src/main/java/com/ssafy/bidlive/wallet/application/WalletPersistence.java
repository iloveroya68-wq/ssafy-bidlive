package com.ssafy.bidlive.wallet.application;

import java.util.List;

import com.ssafy.bidlive.wallet.domain.Wallet;

/** 지갑 Application Service가 사용하는 영속성 Port다. */
public interface WalletPersistence {

	void saveAllAndFlush(List<Wallet> wallets);
}
