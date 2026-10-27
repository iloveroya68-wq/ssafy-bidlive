package com.ssafy.bidlive.auction.application;

import java.util.List;
import java.util.Set;

import com.ssafy.bidlive.auction.domain.HostedAuctionHistoryCursor;
import com.ssafy.bidlive.auction.domain.SellerNicknameMask;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 개최 경매 커서를 검증하고 경매 종료 결과와 닉네임 공개 규칙을 적용한다. */
@Service
public class HostedAuctionHistoryQueryService implements HostedAuctionHistoryQuery {

	private static final Set<String> PUBLIC_RESULT_TYPES = Set.of(
		"NO_BID",
		"HOST_LEFT",
		"FORCED"
	);

	private final HostedAuctionHistoryPersistence persistence;

	public HostedAuctionHistoryQueryService(
		HostedAuctionHistoryPersistence persistence
	) {
		this.persistence = persistence;
	}

	@Override
	@Transactional(readOnly = true)
	public HostedAuctionHistorySlice query(
		long sellerId,
		String cursor,
		int size
	) {
		HostedAuctionHistoryPage page = persistence.find(
			new HostedAuctionHistoryCriteria(
				sellerId,
				decodeCursor(cursor),
				size
			)
		);
		return new HostedAuctionHistorySlice(
			toItems(page.rows()),
			page.nextCursor(),
			page.hasNext()
		);
	}

	private HostedAuctionHistoryCursor decodeCursor(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return null;
		}
		try {
			return HostedAuctionHistoryCursor.decode(cursor.trim());
		} catch (IllegalArgumentException exception) {
			throw new InvalidHostedAuctionCursorException(exception);
		}
	}

	private List<HostedAuctionHistoryItem> toItems(
		List<HostedAuctionHistoryRow> rows
	) {
		return rows.stream()
			.map(row -> new HostedAuctionHistoryItem(
				row.auctionId(),
				row.dealId(),
				row.productTitle(),
				row.imageUrl(),
				row.roomStatus(),
				resultType(row),
				row.finalPrice(),
				maskWinner(row.winnerNickname()),
				row.dealStatus(),
				row.bidCount(),
				row.participantCount(),
				row.predictionParticipantCount(),
				row.scheduledAt(),
				row.startedAt(),
				row.endedAt()
			))
			.toList();
	}

	private String resultType(HostedAuctionHistoryRow row) {
		if (row.resultStatus() == null) {
			return null;
		}
		if ("SOLD".equals(row.resultStatus())) {
			return "SOLD";
		}
		if (PUBLIC_RESULT_TYPES.contains(row.terminationReason())) {
			return row.terminationReason();
		}
		if ("NO_SALE".equals(row.resultStatus())) {
			return "NO_BID";
		}
		if ("CANCELLED".equals(row.resultStatus())) {
			return "FORCED";
		}
		return null;
	}

	private String maskWinner(String winnerNickname) {
		return winnerNickname == null
			? null
			: SellerNicknameMask.of(winnerNickname);
	}
}
