package com.ssafy.bidlive.member.application;

import java.util.List;

import com.ssafy.bidlive.auction.application.HostedAuctionHistoryItem;
import com.ssafy.bidlive.auction.application.HostedAuctionHistoryQuery;
import com.ssafy.bidlive.auction.application.HostedAuctionHistorySlice;
import com.ssafy.bidlive.auction.application.InvalidHostedAuctionCursorException;
import com.ssafy.bidlive.member.domain.HostedAuctionQueryErrorCode;
import org.springframework.stereotype.Service;

/** 인증 판매자의 개최 경매 이력을 조회하고 현재 필요한 판매자 액션을 계산한다. */
@Service
public class HostedAuctionQueryService {

	private static final int DEFAULT_SIZE = 8;
	private static final int MIN_SIZE = 1;
	private static final int MAX_SIZE = 20;
	private static final String WAITING_SHIPMENT = "WAITING_SHIPMENT";

	private final HostedAuctionHistoryQuery historyQuery;

	public HostedAuctionQueryService(HostedAuctionHistoryQuery historyQuery) {
		this.historyQuery = historyQuery;
	}

	public HostedAuctionQueryResult query(
		Long memberId,
		String cursor,
		Integer size
	) {
		if (memberId == null || memberId <= 0) {
			throw new HostedAuctionQueryException(
				HostedAuctionQueryErrorCode.UNAUTHORIZED
			);
		}
		try {
			HostedAuctionHistorySlice slice = historyQuery.query(
				memberId,
				cursor,
				clampSize(size)
			);
			return new HostedAuctionQueryResult(
				toItems(slice.items()),
				slice.nextCursor(),
				slice.hasNext()
			);
		} catch (InvalidHostedAuctionCursorException exception) {
			throw new HostedAuctionQueryException(
				HostedAuctionQueryErrorCode.INVALID_CURSOR,
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

	private List<HostedAuctionItemResult> toItems(
		List<HostedAuctionHistoryItem> items
	) {
		return items.stream()
			.map(this::toItem)
			.toList();
	}

	private HostedAuctionItemResult toItem(HostedAuctionHistoryItem item) {
		boolean actionRequired = WAITING_SHIPMENT.equals(item.dealStatus());
		return new HostedAuctionItemResult(
			item.auctionId(),
			item.dealId(),
			item.productTitle(),
			item.imageUrl(),
			item.roomStatus(),
			item.resultType(),
			item.finalPrice(),
			item.winnerNickname(),
			item.dealStatus(),
			actionRequired,
			actionRequired ? "CONFIRM_SHIPMENT" : null,
			actionRequired ? "발송 확인" : null,
			item.bidCount(),
			item.participantCount(),
			item.predictionParticipantCount(),
			item.scheduledAt(),
			item.startedAt(),
			item.endedAt()
		);
	}
}
