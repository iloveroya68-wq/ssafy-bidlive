package com.ssafy.bidlive.auction.application;

import java.util.List;
import java.util.Optional;

/** 경매 결과 조회에 필요한 읽기 전용 포트. 쓰기는 없다 — 정산은 다른 모듈이 한다. */
public interface AuctionResultQueryPersistence {

	/** 공개·미삭제 방만 찾는다. 비공개 방은 존재 자체를 감춰야 하므로 여기서 비어 온다 */
	Optional<AuctionResultHeaderRow> findHeader(long auctionId);

	/** 마스킹 전 입찰 전체. 금액 내림차순 — 낙찰가(2등가)가 어디서 나왔는지 보이게 한다 */
	List<MaskedBidRow> findBids(long auctionId);

	Optional<MyBidRow> findMyBid(long auctionId, long memberId);

	Optional<MyPredictionRow> findMyPrediction(long auctionId, long memberId);

	/** 밴드별 집계. 정산된 예측이 없으면 빈 목록 */
	List<PredictionBandRow> findPredictionBands(long auctionId);
}
