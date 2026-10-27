package com.ssafy.bidlive.wallet.application;

import static com.ssafy.bidlive.wallet.application.WalletQueryErrorCode.MEMBER_NOT_FOUND;
import static com.ssafy.bidlive.wallet.application.WalletQueryErrorCode.UNAUTHORIZED;
import static com.ssafy.bidlive.wallet.application.WalletQueryErrorCode.WALLET_NOT_FOUND;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.wallet.application.WalletQueryPersistence.WalletBalanceSnapshot;
import com.ssafy.bidlive.wallet.application.WalletQueryResult.WalletSummary;
import com.ssafy.bidlive.wallet.domain.WalletType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인증 회원의 거래·베팅 지갑 상태를 함께 조회한다. */
@Service
public class WalletQueryService {

	private final MemberPersistence memberPersistence;
	private final WalletQueryPersistence walletPersistence;

	public WalletQueryService(
		MemberPersistence memberPersistence,
		WalletQueryPersistence walletPersistence
	) {
		this.memberPersistence = memberPersistence;
		this.walletPersistence = walletPersistence;
	}

	@Transactional(readOnly = true)
	public WalletQueryResult query(Long memberId) {
		validateMemberId(memberId);
		if (memberPersistence.findById(memberId).isEmpty()) {
			throw error(MEMBER_NOT_FOUND);
		}

		Map<WalletType, WalletBalanceSnapshot> wallets = indexByType(
			walletPersistence.findAllByMemberId(memberId)
		);
		WalletBalanceSnapshot tradeWallet = requiredWallet(
			wallets,
			WalletType.TRADE
		);
		WalletBalanceSnapshot bettingWallet = requiredWallet(
			wallets,
			WalletType.BETTING
		);
		return new WalletQueryResult(
			toSummary(tradeWallet),
			toSummary(bettingWallet)
		);
	}

	private Map<WalletType, WalletBalanceSnapshot> indexByType(
		List<WalletBalanceSnapshot> snapshots
	) {
		Map<WalletType, WalletBalanceSnapshot> wallets =
			new EnumMap<>(WalletType.class);
		for (WalletBalanceSnapshot snapshot : snapshots) {
			WalletBalanceSnapshot duplicate = wallets.put(
				snapshot.walletType(),
				snapshot
			);
			if (duplicate != null) {
				throw new IllegalStateException(
					"Duplicate wallet type for member"
				);
			}
		}
		return wallets;
	}

	private WalletBalanceSnapshot requiredWallet(
		Map<WalletType, WalletBalanceSnapshot> wallets,
		WalletType walletType
	) {
		WalletBalanceSnapshot wallet = wallets.get(walletType);
		if (wallet == null) {
			throw error(WALLET_NOT_FOUND);
		}
		return wallet;
	}

	private WalletSummary toSummary(WalletBalanceSnapshot snapshot) {
		if (snapshot.availableBalance() < 0 || snapshot.heldBalance() < 0) {
			throw new IllegalStateException(
				"Wallet balances must not be negative"
			);
		}
		long balance = Math.addExact(
			snapshot.availableBalance(),
			snapshot.heldBalance()
		);
		return new WalletSummary(
			balance,
			snapshot.heldBalance(),
			snapshot.availableBalance()
		);
	}

	private void validateMemberId(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw error(UNAUTHORIZED);
		}
	}

	private WalletQueryException error(WalletQueryErrorCode errorCode) {
		return new WalletQueryException(errorCode);
	}
}
