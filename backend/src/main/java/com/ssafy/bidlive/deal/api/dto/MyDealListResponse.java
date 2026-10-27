package com.ssafy.bidlive.deal.api.dto;

import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.deal.application.DealListItem;
import com.ssafy.bidlive.deal.application.DealListPage;

/** DEAL-01 응답. 명세 Response Field와 1:1 */
public record MyDealListResponse(List<Item> items, String nextCursor, boolean hasNext) {

	public static MyDealListResponse from(DealListPage page) {
		return new MyDealListResponse(
			page.items().stream().map(Item::from).toList(),
			page.nextCursor(),
			page.hasNext()
		);
	}

	public record Item(
		long dealId,
		long auctionId,
		String role,
		String productName,
		String thumbnailUrl,
		long finalPrice,
		String status,
		String counterpartyNickname,
		Instant settledAt
	) {

		static Item from(DealListItem item) {
			return new Item(
				item.dealId(),
				item.auctionId(),
				item.role().name(),
				item.productName(),
				item.thumbnailUrl(),
				item.finalPrice(),
				item.status(),
				item.counterpartyNickname(),
				item.settledAt()
			);
		}
	}
}
