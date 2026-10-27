package com.ssafy.bidlive.wallet.infrastructure.persistence;

import java.util.List;

import com.ssafy.bidlive.wallet.application.WalletPersistence;
import com.ssafy.bidlive.wallet.domain.Wallet;
import org.springframework.stereotype.Repository;

/** WalletPersistence 계약을 Spring Data JPA로 구현한다. */
@Repository
public class WalletPersistenceAdapter implements WalletPersistence {

	private final WalletRepository repository;

	public WalletPersistenceAdapter(WalletRepository repository) {
		this.repository = repository;
	}

	@Override
	public void saveAllAndFlush(List<Wallet> wallets) {
		repository.saveAll(wallets);
		repository.flush();
	}
}
