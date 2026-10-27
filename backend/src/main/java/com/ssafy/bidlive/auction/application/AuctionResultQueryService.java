package com.ssafy.bidlive.auction.application;

import static com.ssafy.bidlive.auction.domain.AuctionResultErrorCode.AUCTION_NOT_FOUND;
import static com.ssafy.bidlive.auction.domain.AuctionResultErrorCode.PREDICTION_RESULT_NOT_AVAILABLE;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.ssafy.bidlive.auction.domain.AuctionResultViewerRole;
import com.ssafy.bidlive.auction.domain.SellerNicknameMask;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DEAL-06·DEAL-08 경매 결과 조회.
 *
 * <p><b>판정 순서가 곧 정보 노출 정책이다.</b> 방을 못 찾으면 404(비공개 방도 여기로 떨어진다),
 * 방은 있는데 정산이 안 끝났으면 409다. 순서를 뒤집으면 "없는 방"과 "아직 안 끝난 방"이 구분돼
 * 비공개 방의 존재가 새어 나간다.
 *
 * <p>마스킹은 전부 여기서 한다 — 화면이 원문을 받아 가리는 구조면 개발자 도구로 걷어낼 수 있다
 * (AGENTS.md 규칙 6).
 *
 * <p><b>정산 값은 다시 계산하지 않는다.</b> 적중 여부·배수는 정산기가 저장한 것을 옮기기만 한다.
 * 다시 계산하면 정산 시점의 정책과 갈리고, 실제로 갈렸다 — {@code error_rate}는 <b>퍼센트</b>인데
 * 비율로 읽어 전원 미적중이 됐다(MR !226 리뷰 1).
 */
@Service
public class AuctionResultQueryService {

	/** 본인 행에 붙는 라벨. 프론트가 이 값으로 "내 입찰" 강조를 건다 */
	private static final String MY_LABEL = "나";

	/** 유찰 사유. 프론트 유니온이 이 셋뿐이라 서버에서 좁혀 준다 — 집계가 다른 값을 넣어도 새지 않게 */
	private static final Set<String> NO_SALE_REASONS = Set.of("NO_BID", "HOST_LEFT", "FORCED");

	/** 강제 종료는 재등록 대신 운영 검토 안내를 띄운다(명세 「종료 사유」표·§규칙) */
	private static final String FORCED = "FORCED";

	private static final String REFUNDED = "REFUNDED";

	private final AuctionResultQueryPersistence persistence;

	public AuctionResultQueryService(AuctionResultQueryPersistence persistence) {
		this.persistence = persistence;
	}

	@Transactional(readOnly = true)
	public AuctionResultQueryResult getResult(long auctionId, Long viewerMemberId) {
		AuctionResultHeaderRow header = persistence.findHeader(auctionId)
			.orElseThrow(() -> new AuctionResultQueryException(AUCTION_NOT_FOUND));
		if (!header.settled()) {
			throw new AuctionResultQueryException(PREDICTION_RESULT_NOT_AVAILABLE);
		}

		Optional<MyBidRow> myBid = viewerMemberId == null
			? Optional.empty()
			: persistence.findMyBid(auctionId, viewerMemberId);
		Optional<MyPredictionRow> myPrediction = viewerMemberId == null
			? Optional.empty()
			: persistence.findMyPrediction(auctionId, viewerMemberId);

		AuctionResultViewerRole role = resolveRole(header, viewerMemberId, myBid, myPrediction);

		if (!header.sold()) {
			String reason = noSaleReason(header.terminationReason());
			return AuctionResultQueryResult.noSale(
				header.auctionId(),
				reason,
				role,
				myBid.map(bid -> BidSettlement.released(bid.amount())).orElse(null),
				myPrediction.map(AuctionResultQueryService::refundedPrediction).orElse(null),
				role == AuctionResultViewerRole.SELLER && !FORCED.equals(reason)
			);
		}

		return AuctionResultQueryResult.sold(
			header.auctionId(),
			role,
			winnerLabel(header, role),
			header.finalPrice(),
			myBid.map(bid -> settleBid(bid, header.finalPrice())).orElse(null),
			myPrediction.flatMap(AuctionResultQueryService::settlePrediction).orElse(null),
			maskedBids(auctionId, viewerMemberId),
			PredictionBands.fill(persistence.findPredictionBands(auctionId)),
			// LIVE-06 낙찰 순간 스냅샷. 판매자가 안 올렸으면 null이고 화면은 플레이스홀더로 남는다
			header.snapshotUrl()
		);
	}

	/**
	 * {@code termination_reason}에는 CHECK 제약이 없다. 집계가 다른 문자열을 넣으면 프론트 유니온을
	 * 조용히 벗어나므로 서버에서 3값으로 좁힌다 — 모르는 값은 재등록 CTA가 없는 쪽으로 떨어뜨린다.
	 */
	private static String noSaleReason(String terminationReason) {
		return NO_SALE_REASONS.contains(terminationReason) ? terminationReason : FORCED;
	}

	/**
	 * 판매자 → 낙찰자 → 미낙찰자 → 예측자 → 관전자 순으로 확정한다.
	 * 판매자는 참여 자체를 못 하므로 맨 앞이고, 입찰·예측은 배타라 서로를 배제한다(규칙 7).
	 */
	private static AuctionResultViewerRole resolveRole(
		AuctionResultHeaderRow header,
		Long viewerMemberId,
		Optional<MyBidRow> myBid,
		Optional<MyPredictionRow> myPrediction
	) {
		if (viewerMemberId == null) {
			return AuctionResultViewerRole.SPECTATOR;
		}
		if (viewerMemberId == header.sellerMemberId()) {
			return AuctionResultViewerRole.SELLER;
		}
		if (myBid.isPresent()) {
			return myBid.get().winner()
				? AuctionResultViewerRole.WINNING_BIDDER
				: AuctionResultViewerRole.LOSING_BIDDER;
		}
		if (myPrediction.isPresent()) {
			return AuctionResultViewerRole.PREDICTOR;
		}
		return AuctionResultViewerRole.SPECTATOR;
	}

	/** 낙찰자 본인이면 "나", 아니면 마스킹된 닉네임 */
	private static String winnerLabel(AuctionResultHeaderRow header, AuctionResultViewerRole role) {
		if (role == AuctionResultViewerRole.WINNING_BIDDER) {
			return MY_LABEL;
		}
		return SellerNicknameMask.of(header.winnerNickname());
	}

	private List<MaskedBidEntry> maskedBids(long auctionId, Long viewerMemberId) {
		return persistence.findBids(auctionId).stream()
			.map(row -> new MaskedBidEntry(
				viewerMemberId != null && row.memberId() == viewerMemberId
					? MY_LABEL
					: SellerNicknameMask.of(row.nickname()),
				row.amount(),
				row.submittedAt()
			))
			.toList();
	}

	/**
	 * 비크리 — 낙찰자는 제출가가 아니라 <b>2등가</b>를 낸다. 그래서 낙찰자에게도 해제액이 있다
	 * (제출가 − 결제액). 미낙찰자는 제출가 전액이 풀린다.
	 */
	private static BidSettlement settleBid(MyBidRow bid, long finalPrice) {
		if (!bid.winner()) {
			return BidSettlement.released(bid.amount());
		}
		return new BidSettlement("WON", bid.amount(), finalPrice, bid.amount() - finalPrice);
	}

	/**
	 * 저장된 정산 결과를 옮기기만 한다 — 적중 판정을 여기서 다시 하지 않는다.
	 *
	 * <p><b>정산 행이 아직 없으면 통째로 비운다.</b> 낙찰은 났는데 예측 정산이 안 끝난 구간에서
	 * {@code result}가 없는 정산 객체를 내보내면, 계약상 필수인 필드가 빠진 채로 나간다
	 * (프론트 타입에서 {@code result}는 필수다). 「아직 없다」는 필드 누락이 아니라 객체 부재로 말한다.
	 */
	private static Optional<PredictionSettlement> settlePrediction(MyPredictionRow row) {
		if (row.resultStatus() == null) {
			return Optional.empty();
		}
		if (REFUNDED.equals(row.resultStatus())) {
			// 정상 경로로는 안 온다 — 정산기의 `refunded()`가 `completionType != SOLD`라 낙찰 건에는
			// 환불 행이 생기지 않는다. 그래도 들어오면 유찰과 같은 모양으로 준다:
			// 배수·지급액을 함께 내보내면 화면에 정산 정책과 무관한 중복 문구가 뜬다.
			return Optional.of(refundedPrediction(row));
		}
		return Optional.of(new PredictionSettlement(
			row.predictionId(),
			row.predictedPrice(),
			row.stakeAmount(),
			row.resultStatus(),
			row.rewardMultiplier() == null
				? null
				: PredictionBands.nameOf(row.resultStatus(), row.rewardMultiplier()),
			row.rewardMultiplier(),
			row.payoutAmount(),
			null
		));
	}

	/**
	 * 유찰·강제 종료는 사유와 무관하게 스테이크를 전액 환불한다(명세 「종료 사유」표).
	 * 정산 행이 아직 없을 수도 있으므로 저장값을 기다리지 않고 환불로 확정한다.
	 */
	private static PredictionSettlement refundedPrediction(MyPredictionRow row) {
		return new PredictionSettlement(
			row.predictionId(),
			row.predictedPrice(),
			row.stakeAmount(),
			REFUNDED,
			null,
			null,
			null,
			row.stakeAmount()
		);
	}
}
