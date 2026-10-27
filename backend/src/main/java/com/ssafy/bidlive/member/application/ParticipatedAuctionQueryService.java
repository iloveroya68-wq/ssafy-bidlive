package com.ssafy.bidlive.member.application;

import java.util.List;

import com.ssafy.bidlive.auction.application.InvalidParticipatedAuctionCursorException;
import com.ssafy.bidlive.auction.application.ParticipatedAuctionHistoryItem;
import com.ssafy.bidlive.auction.application.ParticipatedAuctionHistoryQuery;
import com.ssafy.bidlive.auction.application.ParticipatedAuctionHistorySlice;
import com.ssafy.bidlive.member.domain.ParticipatedAuctionQueryErrorCode;
import org.springframework.stereotype.Service;

/** 인증 회원의 입찰·예측 참여 경매를 조회하고 API 결과로 변환한다. */
@Service
public class ParticipatedAuctionQueryService {

	private static final int DEFAULT_SIZE = 8;
	private static final int MIN_SIZE = 1;
	private static final int MAX_SIZE = 20;

	private final ParticipatedAuctionHistoryQuery historyQuery;

	public ParticipatedAuctionQueryService(
		ParticipatedAuctionHistoryQuery historyQuery
	) {
		this.historyQuery = historyQuery;
	}

	public ParticipatedAuctionQueryResult query(
		Long memberId,
		String cursor,
		Integer size
	) {
		if (memberId == null || memberId <= 0) {
			throw new ParticipatedAuctionQueryException(
				ParticipatedAuctionQueryErrorCode.UNAUTHORIZED
			);
		}
		try {
			ParticipatedAuctionHistorySlice slice = historyQuery.query(
				memberId,
				cursor,
				clampSize(size)
			);
			return new ParticipatedAuctionQueryResult(
				toItems(slice.items()),
				slice.nextCursor(),
				slice.hasNext()
			);
		} catch (InvalidParticipatedAuctionCursorException exception) {
			throw new ParticipatedAuctionQueryException(
				ParticipatedAuctionQueryErrorCode.INVALID_CURSOR,
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

	private List<ParticipatedAuctionItemResult> toItems(
		List<ParticipatedAuctionHistoryItem> items
	) {
		return items.stream()
			.map(this::toItem)
			.toList();
	}

	private ParticipatedAuctionItemResult toItem(
		ParticipatedAuctionHistoryItem item
	) {
		return new ParticipatedAuctionItemResult(
			item.auctionId(),
			item.participationType(),
			item.productTitle(),
			item.imageUrl(),
			item.roomStatus(),
			item.resultType(),
			item.result(),
			item.resultLabel(),
			item.finalPrice(),
			item.myFinalBid(),
			item.holdReleasedAmount(),
			item.predictedPrice(),
			item.errorRate(),
			item.errorBand(),
			item.stakeAmount(),
			item.payoutAmount(),
			item.refundAmount(),
			item.endedAt()
		);
	}
}
