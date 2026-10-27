package com.ssafy.bidlive.member.application;

import java.util.List;

import com.ssafy.bidlive.auction.application.InvalidWonAuctionCursorException;
import com.ssafy.bidlive.auction.application.WonAuctionHistoryItem;
import com.ssafy.bidlive.auction.application.WonAuctionHistoryQuery;
import com.ssafy.bidlive.auction.application.WonAuctionHistorySlice;
import com.ssafy.bidlive.member.domain.WonAuctionQueryErrorCode;
import org.springframework.stereotype.Service;

/** 인증 회원의 낙찰 경매를 조회하고 현재 필요한 구매자 액션을 계산한다. */
@Service
public class WonAuctionQueryService {

	private static final int DEFAULT_SIZE = 8;
	private static final int MIN_SIZE = 1;
	private static final int MAX_SIZE = 20;

	private final WonAuctionHistoryQuery historyQuery;

	public WonAuctionQueryService(WonAuctionHistoryQuery historyQuery) {
		this.historyQuery = historyQuery;
	}

	public WonAuctionQueryResult query(
		Long memberId,
		String cursor,
		Integer size
	) {
		if (memberId == null || memberId <= 0) {
			throw new WonAuctionQueryException(
				WonAuctionQueryErrorCode.UNAUTHORIZED
			);
		}
		try {
			WonAuctionHistorySlice slice = historyQuery.query(
				memberId,
				cursor,
				clampSize(size)
			);
			return new WonAuctionQueryResult(
				toItems(slice.items()),
				slice.nextCursor(),
				slice.hasNext()
			);
		} catch (InvalidWonAuctionCursorException exception) {
			throw new WonAuctionQueryException(
				WonAuctionQueryErrorCode.INVALID_CURSOR,
				exception
			);
		}
	}

	private int clampSize(Integer size) {
		if (size == null) {
			return DEFAULT_SIZE;
		}
		return Math.clamp(size, MIN_SIZE, MAX_SIZE);
	}

	private List<WonAuctionItemResult> toItems(
		List<WonAuctionHistoryItem> items
	) {
		return items.stream()
			.map(this::toItem)
			.toList();
	}

	private WonAuctionItemResult toItem(WonAuctionHistoryItem item) {
		BuyerAction action = actionFor(item.dealStatus());
		return new WonAuctionItemResult(
			item.auctionId(),
			item.dealId(),
			item.productTitle(),
			item.imageUrl(),
			item.sellerNickname(),
			item.finalPrice(),
			item.myFinalBid(),
			item.dealStatus(),
			action != null,
			action == null ? null : action.code(),
			action == null ? null : action.label(),
			item.snapshotUrl(),
			item.endedAt()
		);
	}

	private BuyerAction actionFor(String dealStatus) {
		return switch (dealStatus) {
			case "WAITING_ADDRESS" -> new BuyerAction(
				"SUBMIT_ADDRESS",
				"배송지 입력"
			);
			case "WAITING_RECEIPT" -> new BuyerAction(
				"CONFIRM_RECEIPT",
				"수령 확인"
			);
			default -> null;
		};
	}

	private record BuyerAction(String code, String label) {
	}
}
