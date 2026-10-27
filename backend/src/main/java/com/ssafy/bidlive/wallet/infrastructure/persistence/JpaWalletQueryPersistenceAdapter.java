package com.ssafy.bidlive.wallet.infrastructure.persistence;

import java.util.List;

import com.ssafy.bidlive.wallet.application.WalletQueryPersistence;
import com.ssafy.bidlive.wallet.domain.WalletType;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** PostgreSQL에서 회원의 거래·베팅 지갑 잔액을 조회한다. */
@Repository
public class JpaWalletQueryPersistenceAdapter
	implements WalletQueryPersistence {

	private static final String SELECT_WALLETS = """
		SELECT
			wallet_type,
			available_balance,
			held_balance
		FROM wallet
		WHERE member_id = :memberId
		""";

	private final EntityManager entityManager;

	public JpaWalletQueryPersistenceAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public List<WalletBalanceSnapshot> findAllByMemberId(Long memberId) {
		return entityManager.createNativeQuery(SELECT_WALLETS)
			.setParameter("memberId", memberId)
			.getResultList()
			.stream()
			.map(row -> toSnapshot((Object[]) row))
			.toList();
	}

	private WalletBalanceSnapshot toSnapshot(Object[] row) {
		return new WalletBalanceSnapshot(
			WalletType.valueOf((String) row[0]),
			((Number) row[1]).longValue(),
			((Number) row[2]).longValue()
		);
	}
}
