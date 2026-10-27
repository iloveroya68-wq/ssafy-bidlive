package com.ssafy.bidlive.wallet.application;

import java.util.Set;

import com.ssafy.bidlive.wallet.domain.WalletType;

public interface PointLedgerQueryPersistence {

	Set<WalletType> findWalletTypes(long memberId);

	PointLedgerQueryPage find(PointLedgerQueryCriteria criteria);
}
