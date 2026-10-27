package com.ssafy.bidlive.auction.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.auction.application.AuctionResultHeaderRow;
import com.ssafy.bidlive.auction.application.AuctionResultQueryPersistence;
import com.ssafy.bidlive.auction.application.MaskedBidRow;
import com.ssafy.bidlive.auction.application.MyBidRow;
import com.ssafy.bidlive.auction.application.MyPredictionRow;
import com.ssafy.bidlive.auction.application.PredictionBandRow;
import com.ssafy.bidlive.auction.application.PredictionBands;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL에서 경매 결과와 참여자별 정산을 읽는다.
 *
 * <p>{@code auction_result}는 BID-07 정상 정산과 DEAL-09 강제 종료가 작성하며, 이 어댑터는
 * 커밋된 결과만 조회 응답으로 변환한다.
 */
@Repository
public class JpaAuctionResultQueryAdapter implements AuctionResultQueryPersistence {

	/**
	 * 비공개·삭제된 방은 아예 안 잡힌다 — 결과 조회에서도 방의 존재를 감춘다.
	 * ({@code JpaAuctionRoomListQueryAdapter}의 {@code PUBLIC_ROOM}과 같은 조건이다.)
	 *
	 * <p><b>{@code live_snapshot}은 media 도메인 소유지만 여기서 직접 조인한다</b> —
	 * MEM-02 낙찰 이력({@code JpaWonAuctionHistoryQueryAdapter})이 이미 같은 방식이라 관례를 따랐다.
	 * 별도 조회로 빼면 결과 한 건에 쿼리가 하나 더 붙고, 그 조회가 죽으면 결과 화면까지 같이 죽는다.
	 * {@code auction_id}가 UNIQUE라 조인해도 행이 늘지 않는다(경매당 스냅샷 1건, V9).
	 */
	private static final String SELECT_HEADER = """
		SELECT
			ar.auction_room_id,
			p.seller_member_id,
			res.result_status,
			res.termination_reason,
			res.final_price,
			res.winner_participant_id,
			winner.nickname,
			snap.image_url
		FROM auction_room ar
		JOIN product p ON p.product_id = ar.product_id
		LEFT JOIN auction_result res ON res.auction_room_id = ar.auction_room_id
		LEFT JOIN auction_participant wp
			ON wp.auction_participant_id = res.winner_participant_id
		LEFT JOIN member winner ON winner.member_id = wp.member_id
		LEFT JOIN live_snapshot snap ON snap.auction_id = ar.auction_room_id
		WHERE ar.auction_room_id = :auctionId
		  AND ar.visibility = 'PUBLIC'
		  AND ar.deleted_at IS NULL
		""";

	/**
	 * <b>참여자당 최신 1건</b>만 내려간다({@code DISTINCT ON}). 상향 입찰로 행이 쌓이는데
	 * 이력을 전부 주면 프론트가 {@code bids.length}를 "N명"으로, 정렬 순서를 순위로 쓰므로
	 * <b>5명 방이 12명이 되고 순위가 밀린다</b>. 본인 옛 입찰도 "나"로 여러 줄 나온다(MR !226 리뷰 4).
	 * 유효 입찰의 정의도 {@code SELECT_MY_BID}(최신 1건)와 같아야 한다.
	 *
	 * <p>바깥에서 금액 내림차순으로 다시 세운다 — 낙찰가(2등가)가 어느 줄에서 나왔는지 보이게.
	 */
	private static final String SELECT_BIDS = """
		SELECT member_id, nickname, amount, submitted_at
		FROM (
			SELECT DISTINCT ON (ap.auction_participant_id)
				m.member_id,
				m.nickname,
				b.amount,
				b.submitted_at
			FROM bid b
			JOIN auction_participant ap ON ap.auction_participant_id = b.auction_participant_id
			JOIN member m ON m.member_id = ap.member_id
			WHERE ap.auction_room_id = :auctionId
			ORDER BY ap.auction_participant_id, b.sequence_no DESC
		) latest
		ORDER BY amount DESC, submitted_at ASC
		""";

	/**
	 * 본인 입찰 <b>한 건</b>. 상향 입찰로 여러 행이 쌓이므로 최신(가장 큰 {@code sequence_no})만 본다 —
	 * 홀드도 그 금액 기준이라 옛 행을 집으면 해제액이 틀린다.
	 */
	private static final String SELECT_MY_BID = """
		SELECT
			b.bid_id,
			b.amount,
			(res.winning_bid_id = b.bid_id)
		FROM bid b
		JOIN auction_participant ap ON ap.auction_participant_id = b.auction_participant_id
		LEFT JOIN auction_result res ON res.auction_room_id = ap.auction_room_id
		WHERE ap.auction_room_id = :auctionId
		  AND ap.member_id = :memberId
		ORDER BY b.sequence_no DESC
		LIMIT 1
		""";

	/** 예측은 방당 1회라({@code prediction.auction_participant_id} UNIQUE) 정렬이 필요 없다 */
	private static final String SELECT_MY_PREDICTION = """
		SELECT
			pr.prediction_id,
			pr.predicted_price,
			pr.stake_amount,
			result.result_status,
			result.reward_multiplier,
			result.payout_amount
		FROM prediction pr
		JOIN auction_participant ap ON ap.auction_participant_id = pr.auction_participant_id
		LEFT JOIN prediction_result result ON result.prediction_id = pr.prediction_id
		WHERE ap.auction_room_id = :auctionId
		  AND ap.member_id = :memberId
		""";

	/**
	 * 밴드별 집계. <b>개별 예측가는 뽑지 않는다</b> — 인원과 지급 합계뿐이다(명세 §규칙).
	 *
	 * <p>밴드 분류는 저장된 값을 쓴다(정산 시점의 정책이 거기 박혀 있다). <b>{@code result_status}까지
	 * 함께 묶는 이유</b>는 {@code REFUNDED}가 오차 밴드가 아니라 환불 상태이기 때문이다 —
	 * PRED-02 집계 어댑터도 둘을 함께 본다(MR !226 리뷰 2).
	 */
	private static final String SELECT_BANDS = """
		SELECT
			result.result_status,
			result.reward_multiplier,
			COUNT(*),
			COALESCE(SUM(result.payout_amount), 0)
		FROM prediction_result result
		JOIN prediction pr ON pr.prediction_id = result.prediction_id
		JOIN auction_participant ap ON ap.auction_participant_id = pr.auction_participant_id
		WHERE ap.auction_room_id = :auctionId
		GROUP BY result.result_status, result.reward_multiplier
		""";

	private final EntityManager entityManager;

	public JpaAuctionResultQueryAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public Optional<AuctionResultHeaderRow> findHeader(long auctionId) {
		List<?> rows = entityManager.createNativeQuery(SELECT_HEADER)
			.setParameter("auctionId", auctionId)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Object[] row = (Object[]) rows.get(0);
		return Optional.of(new AuctionResultHeaderRow(
			toLong(row[0]),
			toLong(row[1]),
			(String) row[2],
			(String) row[3],
			row[4] == null ? null : toLong(row[4]),
			row[5] == null ? null : toLong(row[5]),
			(String) row[6],
			(String) row[7]
		));
	}

	@Override
	public List<MaskedBidRow> findBids(long auctionId) {
		List<?> rows = entityManager.createNativeQuery(SELECT_BIDS)
			.setParameter("auctionId", auctionId)
			.getResultList();
		return rows.stream()
			.map(Object[].class::cast)
			.map(row -> new MaskedBidRow(
				toLong(row[0]),
				(String) row[1],
				toLong(row[2]),
				toInstant(row[3])
			))
			.toList();
	}

	@Override
	public Optional<MyBidRow> findMyBid(long auctionId, long memberId) {
		List<?> rows = entityManager.createNativeQuery(SELECT_MY_BID)
			.setParameter("auctionId", auctionId)
			.setParameter("memberId", memberId)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Object[] row = (Object[]) rows.get(0);
		return Optional.of(new MyBidRow(toLong(row[1]), Boolean.TRUE.equals(row[2])));
	}

	@Override
	public Optional<MyPredictionRow> findMyPrediction(long auctionId, long memberId) {
		List<?> rows = entityManager.createNativeQuery(SELECT_MY_PREDICTION)
			.setParameter("auctionId", auctionId)
			.setParameter("memberId", memberId)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Object[] row = (Object[]) rows.get(0);
		return Optional.of(new MyPredictionRow(
			toLong(row[0]),
			toLong(row[1]),
			toLong(row[2]),
			(String) row[3],
			row[4] == null ? null : new BigDecimal(row[4].toString()),
			row[5] == null ? null : toLong(row[5])
		));
	}

	@Override
	public List<PredictionBandRow> findPredictionBands(long auctionId) {
		List<?> rows = entityManager.createNativeQuery(SELECT_BANDS)
			.setParameter("auctionId", auctionId)
			.getResultList();
		return rows.stream()
			.map(Object[].class::cast)
			// 환불은 오차 밴드가 아니다 — result_status가 REFUNDED인 행은 여기서 걸러 낸다
			.filter(row -> !"REFUNDED".equals(row[0]))
			.map(row -> {
				BigDecimal multiplier = new BigDecimal(row[1].toString());
				return new PredictionBandRow(
					PredictionBands.nameOf((String) row[0], multiplier),
					toLong(row[2]),
					multiplier,
					toLong(row[3])
				);
			})
			// 알 수 없는 배수는 조용히 MISS로 만들지 않고 집계에서 뺀다
			.filter(row -> row.band() != null)
			.toList();
	}

	private static long toLong(Object value) {
		return ((Number) value).longValue();
	}

	private static Instant toInstant(Object value) {
		if (value instanceof Timestamp timestamp) {
			return timestamp.toInstant();
		}
		return (Instant) value;
	}
}
