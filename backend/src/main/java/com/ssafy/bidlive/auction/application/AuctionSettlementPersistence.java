package com.ssafy.bidlive.auction.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import com.ssafy.bidlive.auction.domain.VickreyBid;
import com.ssafy.bidlive.auction.domain.VickreySettlementOutcome;

/** BID-07이 경매방을 잠그고 최종 입찰을 읽어 정상 마감 결과를 저장하는 계약이다. */
public interface AuctionSettlementPersistence {

	Optional<SettlementTarget> lockTarget(long auctionId);

	List<FinalBid> findFinalBids(long auctionId);

	Optional<StoredResult> findStoredResult(long auctionId);

	List<OutstandingBidHold> findOutstandingBidHolds(long auctionId);

	BidCounts countBids(long auctionId);

	OptionalLong createResultIfAbsent(ResultWrite result);

	/** LIVE인 경매만 ENDED로 전이하고 실제 종료 시각을 기록한다. */
	int markEnded(long auctionId, Instant endedAt);

	/** 정산 시작 전 잠근 경매방의 PostgreSQL 기준 상태다. */
	record SettlementTarget(
		long auctionId,
		String status,
		long reservePrice,
		Instant endAt,
		boolean resultExists
	) {
	}

	/** 회원별 마지막 상향 입찰과 결과 FK 저장에 필요한 식별자다. */
	record FinalBid(
		long bidId,
		long participantId,
		long memberId,
		String nickname,
		long amount,
		Instant submittedAt
	) {

		public VickreyBid toVickreyBid() {
			return new VickreyBid(
				bidId,
				memberId,
				amount,
				submittedAt
			);
		}
	}

	/** 재시도가 포인트를 다시 건드리지 않고 Redis 완료만 복구할 때 읽는 결과다. */
	record StoredResult(
		long auctionResultId,
		String resultStatus,
		String terminationReason,
		Long winnerMemberId,
		String winnerNickname,
		Long highestBidAmount,
		Long secondHighestBidAmount,
		Long finalPrice,
		Instant settledAt
	) {
	}

	/** 아직 차감하거나 돌려줘야 하는 입찰별 거래 포인트 홀드다. */
	record OutstandingBidHold(
		long bidId,
		long memberId,
		long remainingHold
	) {

		public OutstandingBidHold {
			if (bidId <= 0 || memberId <= 0 || remainingHold <= 0) {
				throw new IllegalArgumentException(
					"outstanding bid hold values must be positive"
				);
			}
		}
	}

	/** 결과 테이블의 참여 회원 수와 전체 상향 입찰 건수다. */
	record BidCounts(int validBidderCount, int totalBidCount) {

		public BidCounts {
			if (validBidderCount < 0
				|| totalBidCount < validBidderCount) {
				throw new IllegalArgumentException(
					"bid counts must be non-negative and ordered"
				);
			}
		}
	}

	/** 정상 낙찰 또는 무입찰 유찰로 저장할 auction_result 값이다. */
	record ResultWrite(
		long auctionId,
		VickreySettlementOutcome outcome,
		Long winnerParticipantId,
		Long winningBidId,
		Long highestBidAmount,
		Long secondHighestBidAmount,
		Long finalPrice,
		int validBidderCount,
		int totalBidCount
	) {

		public ResultWrite {
			if (auctionId <= 0) {
				throw new IllegalArgumentException(
					"auctionId must be positive"
				);
			}
			if (outcome == null) {
				throw new IllegalArgumentException(
					"outcome must not be null"
				);
			}
			new BidCounts(validBidderCount, totalBidCount);
			if (outcome == VickreySettlementOutcome.SOLD) {
				validateSold(
					winnerParticipantId,
					winningBidId,
					highestBidAmount,
					secondHighestBidAmount,
					finalPrice,
					validBidderCount
				);
			} else {
				validateNoSale(
					winnerParticipantId,
					winningBidId,
					highestBidAmount,
					secondHighestBidAmount,
					finalPrice,
					validBidderCount,
					totalBidCount
				);
			}
		}

		private static void validateSold(
			Long winnerParticipantId,
			Long winningBidId,
			Long highestBidAmount,
			Long secondHighestBidAmount,
			Long finalPrice,
			int validBidderCount
		) {
			if (winnerParticipantId == null || winnerParticipantId <= 0
				|| winningBidId == null || winningBidId <= 0
				|| highestBidAmount == null || highestBidAmount <= 0
				|| finalPrice == null || finalPrice <= 0
				|| validBidderCount <= 0) {
				throw new IllegalArgumentException(
					"sold result must have winner, prices, and bidders"
				);
			}
			if (secondHighestBidAmount != null
				&& secondHighestBidAmount <= 0) {
				throw new IllegalArgumentException(
					"secondHighestBidAmount must be positive when present"
				);
			}
			if (validBidderCount == 1 && secondHighestBidAmount != null
				|| validBidderCount > 1 && secondHighestBidAmount == null) {
				throw new IllegalArgumentException(
					"second price presence must match bidder count"
				);
			}
		}

		private static void validateNoSale(
			Long winnerParticipantId,
			Long winningBidId,
			Long highestBidAmount,
			Long secondHighestBidAmount,
			Long finalPrice,
			int validBidderCount,
			int totalBidCount
		) {
			if (winnerParticipantId != null
				|| winningBidId != null
				|| highestBidAmount != null
				|| secondHighestBidAmount != null
				|| finalPrice != null
				|| validBidderCount != 0
				|| totalBidCount != 0) {
				throw new IllegalArgumentException(
					"no-sale result must not have bids or prices"
				);
			}
		}
	}
}
