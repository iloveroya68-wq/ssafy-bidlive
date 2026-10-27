package com.ssafy.bidlive.wallet.application;

import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.wallet.domain.Wallet;
import com.ssafy.bidlive.wallet.domain.BettingPointGrantPolicy;
import com.ssafy.bidlive.wallet.domain.BettingPointGrantType;
import com.ssafy.bidlive.wallet.domain.WalletType;
import org.springframework.stereotype.Service;

/** 신규 회원의 거래·베팅 기본 지갑을 초기화하고 가입 보너스를 지급한다. */
@Service
public class WalletInitializationService {

	private final WalletPersistence walletPersistence;
	private final BettingPointGrantService bettingPointGrantService;

	public WalletInitializationService(
		WalletPersistence walletPersistence,
		BettingPointGrantService bettingPointGrantService
	) {
		this.walletPersistence = walletPersistence;
		this.bettingPointGrantService = bettingPointGrantService;
	}

	public void initializeFor(Long memberId) {
		walletPersistence.saveAllAndFlush(List.of(
			Wallet.empty(memberId, WalletType.TRADE),
			Wallet.empty(memberId, WalletType.BETTING)
		));
		bettingPointGrantService.grant(new BettingPointGrantCommand(
			memberId,
			BettingPointGrantType.SIGNUP,
			"MEMBER:" + memberId,
			BettingPointGrantPolicy.amountFor(BettingPointGrantType.SIGNUP),
			BettingPointGrantPolicy.VERSION,
			Instant.now()
		));
	}
}
