package com.ssafy.bidlive.auction.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.ssafy.bidlive.auction.application.WonAuctionHistoryCriteria;
import com.ssafy.bidlive.auction.application.WonAuctionHistoryPage;
import com.ssafy.bidlive.auction.application.WonAuctionHistoryPersistence;
import com.ssafy.bidlive.auction.application.WonAuctionHistoryRow;
import com.ssafy.bidlive.auction.domain.WonAuctionHistoryCursor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

/** 경매 결과·거래·스냅샷을 조합해 회원이 낙찰 받은 경매 이력을 조회한다. */
@Repository
public class JpaWonAuctionHistoryQueryAdapter
	implements WonAuctionHistoryPersistence {

	private static final String SELECT_HISTORY = """
		SELECT
			ar.auction_room_id,
			deal.deal_id,
			product.title,
			(
				SELECT image.image_url
				FROM product_image image
				WHERE image.product_id = product.product_id
				  AND image.moderation_status <> 'BLOCKED'
				ORDER BY image.is_thumbnail DESC, image.sort_order ASC
				LIMIT 1
			),
			seller.nickname,
			deal.final_price,
			winning_bid.amount,
			deal.deal_status,
			COALESCE(
				NULLIF(BTRIM(deal.snapshot_image_url), ''),
				live_snapshot.image_url
			),
			ar.ended_at
		FROM auction_result auction_result
		JOIN auction_room ar
		  ON ar.auction_room_id = auction_result.auction_room_id
		JOIN auction_participant winner
		  ON winner.auction_participant_id =
			auction_result.winner_participant_id
		JOIN bid winning_bid
		  ON winning_bid.bid_id = auction_result.winning_bid_id
		JOIN product product ON product.product_id = ar.product_id
		JOIN member seller
		  ON seller.member_id = product.seller_member_id
		JOIN deal deal
		  ON deal.auction_result_id = auction_result.auction_result_id
		LEFT JOIN live_snapshot live_snapshot
		  ON live_snapshot.auction_id = ar.auction_room_id
		WHERE auction_result.result_status = 'SOLD'
		  AND winner.member_id = :memberId
		  AND deal.buyer_member_id = :memberId
		  AND ar.ended_at IS NOT NULL
		""";

	private static final String ORDER_BY = """
		ORDER BY ar.ended_at DESC, ar.auction_room_id DESC
		""";

	private final EntityManager entityManager;

	public JpaWonAuctionHistoryQueryAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public WonAuctionHistoryPage find(WonAuctionHistoryCriteria criteria) {
		String cursorCondition = criteria.cursor() == null
			? ""
			: """

				AND (
					ar.ended_at,
					ar.auction_room_id
				) < (
					CAST(:cursorEndedAt AS TIMESTAMPTZ),
					:cursorAuctionId
				)
				""";
		Query query = entityManager.createNativeQuery(
			SELECT_HISTORY + cursorCondition + ORDER_BY
		);
		query.setParameter("memberId", criteria.memberId());
		if (criteria.cursor() != null) {
			query.setParameter(
				"cursorEndedAt",
				criteria.cursor().endedAt().toString()
			);
			query.setParameter(
				"cursorAuctionId",
				criteria.cursor().auctionId()
			);
		}
		query.setMaxResults(criteria.size() + 1);

		List<WonAuctionHistoryRow> rows = new ArrayList<>(
			query.getResultList().stream()
				.map(JpaWonAuctionHistoryQueryAdapter::toRow)
				.toList()
		);
		boolean hasNext = rows.size() > criteria.size();
		if (hasNext) {
			rows = rows.subList(0, criteria.size());
		}
		String nextCursor = hasNext
			? cursorOf(rows.get(rows.size() - 1)).encode()
			: null;
		return new WonAuctionHistoryPage(rows, nextCursor, hasNext);
	}

	private static WonAuctionHistoryRow toRow(Object value) {
		Object[] row = (Object[]) value;
		return new WonAuctionHistoryRow(
			toLong(row[0]),
			toLong(row[1]),
			(String) row[2],
			(String) row[3],
			(String) row[4],
			toLong(row[5]),
			toLong(row[6]),
			(String) row[7],
			(String) row[8],
			toInstant(row[9])
		);
	}

	private static WonAuctionHistoryCursor cursorOf(
		WonAuctionHistoryRow row
	) {
		return new WonAuctionHistoryCursor(
			row.endedAt(),
			row.auctionId()
		);
	}

	private static long toLong(Object value) {
		return ((Number) value).longValue();
	}

	private static Instant toInstant(Object value) {
		return switch (value) {
			case Instant instant -> instant;
			case Timestamp timestamp -> timestamp.toInstant();
			case OffsetDateTime offsetDateTime -> offsetDateTime.toInstant();
			default -> throw new IllegalStateException(
				"지원하지 않는 시각 타입: " + value.getClass()
			);
		};
	}
}
