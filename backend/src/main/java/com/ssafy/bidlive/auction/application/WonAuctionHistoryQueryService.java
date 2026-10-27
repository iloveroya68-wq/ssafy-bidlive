package com.ssafy.bidlive.auction.application;

import java.util.List;

import com.ssafy.bidlive.auction.domain.SellerNicknameMask;
import com.ssafy.bidlive.auction.domain.WonAuctionHistoryCursor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 낙찰 경매 커서를 검증하고 판매자 닉네임 마스킹 정책을 적용한다. */
@Service
public class WonAuctionHistoryQueryService
	implements WonAuctionHistoryQuery {

	private final WonAuctionHistoryPersistence persistence;

	public WonAuctionHistoryQueryService(
		WonAuctionHistoryPersistence persistence
	) {
		this.persistence = persistence;
	}

	@Override
	@Transactional(readOnly = true)
	public WonAuctionHistorySlice query(
		long memberId,
		String cursor,
		int size
	) {
		WonAuctionHistoryPage page = persistence.find(
			new WonAuctionHistoryCriteria(
				memberId,
				decodeCursor(cursor),
				size
			)
		);
		return new WonAuctionHistorySlice(
			toItems(page.rows()),
			page.nextCursor(),
			page.hasNext()
		);
	}

	private WonAuctionHistoryCursor decodeCursor(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return null;
		}
		try {
			return WonAuctionHistoryCursor.decode(cursor.trim());
		} catch (IllegalArgumentException exception) {
			throw new InvalidWonAuctionCursorException(exception);
		}
	}

	private List<WonAuctionHistoryItem> toItems(
		List<WonAuctionHistoryRow> rows
	) {
		return rows.stream()
			.map(row -> new WonAuctionHistoryItem(
				row.auctionId(),
				row.dealId(),
				row.productTitle(),
				row.imageUrl(),
				SellerNicknameMask.of(row.sellerNickname()),
				row.finalPrice(),
				row.myFinalBid(),
				row.dealStatus(),
				row.snapshotUrl(),
				row.endedAt()
			))
			.toList();
	}
}
