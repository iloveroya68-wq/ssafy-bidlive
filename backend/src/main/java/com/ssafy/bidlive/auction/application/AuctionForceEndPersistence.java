package com.ssafy.bidlive.auction.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.auction.domain.AuctionTerminationReason;

/** 낙찰 없는 종료(운영 강제·판매자)가 건드리는 경매방·결과·홀드 원장 접근이다. */
public interface AuctionForceEndPersistence {

	Optional<ForceEndTarget> findTarget(long auctionId);

	/**
	 * `WAITING`·`LIVE`인 방만 `ENDED`로 전이한다.
	 *
	 * <p>조건부 UPDATE라는 점이 중복 실행 방지의 핵심이다 — 판정과 전이 사이에 다른 요청이
	 * 먼저 종료하면 갱신 행이 0이 되고, 서비스가 그것을 「이미 종료됨」으로 읽는다.
	 * 방송 시작(ROOM-05)이 쓰는 방식과 같다.
	 *
	 * @return 갱신된 행 수(0 또는 1)
	 */
	int markEnded(long auctionId, Instant endedAt);

	/**
	 * 종료 결과 행을 만든다. 이미 있으면 만들지 않고 기존 id를 돌려준다 —
	 * `auction_room_id`가 UNIQUE라 재실행이 중복 행을 만들지 않는다.
	 *
	 * <p>사유를 인자로 받는다 — 운영 종료(`FORCED`)와 판매자 종료(`HOST_LEFT`)가 같은 흐름을 쓰지만
	 * 결과 행의 `result_status`·`termination_reason`은 갈린다. 그 짝은 {@link AuctionTerminationReason}이
	 * 들고 있다.
	 */
	long createResultIfAbsent(
		long auctionId,
		ResultCounts counts,
		AuctionTerminationReason reason
	);

	/** 이 경매의 입찰 홀드 중 아직 풀리지 않은 것. 이미 0인 건 제외한다. */
	List<OutstandingBidHold> findOutstandingBidHolds(long auctionId);

	ResultCounts countBids(long auctionId);

	/**
	 * 종료 대상 판정에 필요한 값.
	 *
	 * <p>`sellerMemberId`는 판매자 종료(ROOM-09)의 권한 판정에만 쓴다 — 운영 종료는 경로 자체가
	 * `hasRole("ADMIN")`이라 이 값을 보지 않는다.
	 */
	record ForceEndTarget(
		long auctionId,
		long sellerMemberId,
		String status,
		boolean deleted
	) {
	}

	/** `auction_result`의 NOT NULL 집계 컬럼. */
	record ResultCounts(
		int validBidderCount,
		int totalBidCount
	) {
	}

	/** 되돌려 줄 홀드 하나. `remainingHold`는 HOLD·RELEASE·SETTLEMENT 합계다. */
	record OutstandingBidHold(
		long bidId,
		long memberId,
		long remainingHold
	) {
	}
}
