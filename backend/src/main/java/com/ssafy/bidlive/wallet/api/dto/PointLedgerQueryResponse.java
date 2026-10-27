package com.ssafy.bidlive.wallet.api.dto;

import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.wallet.application.PointLedgerQueryResult;
import com.ssafy.bidlive.wallet.domain.PointLedgerReferenceType;
import com.ssafy.bidlive.wallet.domain.PointLedgerTransactionType;
import com.ssafy.bidlive.wallet.domain.WalletType;

public record PointLedgerQueryResponse(
	List<Item> items,
	String nextCursor,
	boolean hasNext
) {

	public static PointLedgerQueryResponse from(
		PointLedgerQueryResult result
	) {
		return new PointLedgerQueryResponse(
			result.items().stream()
				.map(Item::from)
				.toList(),
			result.nextCursor(),
			result.hasNext()
		);
	}

	public record Item(
		long ledgerId,
		WalletType walletType,
		PointLedgerTransactionType transactionType,
		long availableChange,
		long heldChange,
		long availableBalanceAfter,
		long heldBalanceAfter,
		String description,
		PointLedgerReferenceType referenceType,
		Long referenceId,
		Instant createdAt
	) {

		private static Item from(PointLedgerQueryResult.Item item) {
			return new Item(
				item.ledgerId(),
				item.walletType(),
				item.transactionType(),
				item.availableChange(),
				item.heldChange(),
				item.availableBalanceAfter(),
				item.heldBalanceAfter(),
				item.description(),
				item.referenceType(),
				item.referenceId(),
				item.createdAt()
			);
		}
	}
}
