package com.ssafy.bidlive.auction.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.ssafy.bidlive.auction.application.HostedAuctionHistoryCriteria;
import com.ssafy.bidlive.auction.application.HostedAuctionHistoryPage;
import com.ssafy.bidlive.auction.application.HostedAuctionHistoryPersistence;
import com.ssafy.bidlive.auction.application.HostedAuctionHistoryRow;
import com.ssafy.bidlive.auction.domain.HostedAuctionHistoryCursor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

/**
 * 경매·입찰·예측·거래 테이블을 조합해 판매자의 개최 경매 이력을 조회한다.
 *
 * <p>삭제된 방(`deleted_at IS NOT NULL`)은 제외한다. 방 삭제는 소프트 삭제라 행이 남는데,
 * 이 조건이 없으면 지운 방이 `WAITING`인 채로 계속 딸려 나온다 — 헤더의 「진행 콘솔」
 * 바로가기가 그 방을 "끝나지 않은 내 방"으로 골라, 눌러 보면 삭제·입장이 전부
 * `AUCTION_NOT_FOUND`로 떨어졌다. 삭제는 `WAITING`일 때만 통과하므로(`AuctionRoomDeleteService`)
 * 이 조건이 종료된 방송을 가리는 일은 없다.
 */
@Repository
public class JpaHostedAuctionHistoryQueryAdapter
	implements HostedAuctionHistoryPersistence {

	private static final String SELECT_HISTORY = """
		SELECT
			ar.auction_room_id,
			d.deal_id,
			p.title,
			(
				SELECT image.image_url
				FROM product_image image
				WHERE image.product_id = p.product_id
				  AND image.moderation_status <> 'BLOCKED'
				ORDER BY image.is_thumbnail DESC, image.sort_order ASC
				LIMIT 1
			),
			ar.status,
			result.result_status,
			result.termination_reason,
			result.final_price,
			winner.nickname,
			d.deal_status,
			(
				SELECT COUNT(*)
				FROM bid history_bid
				JOIN auction_participant bidder
				  ON bidder.auction_participant_id =
					history_bid.auction_participant_id
				WHERE bidder.auction_room_id = ar.auction_room_id
			),
			(
				SELECT COUNT(DISTINCT bidder.member_id)
				FROM auction_participant bidder
				JOIN bid history_bid
				  ON history_bid.auction_participant_id =
					bidder.auction_participant_id
				WHERE bidder.auction_room_id = ar.auction_room_id
				  AND bidder.participation_type = 'BIDDER'
			),
			(
				SELECT COUNT(DISTINCT predictor.member_id)
				FROM auction_participant predictor
				JOIN prediction history_prediction
				  ON history_prediction.auction_participant_id =
					predictor.auction_participant_id
				WHERE predictor.auction_room_id = ar.auction_room_id
				  AND predictor.participation_type = 'PREDICTOR'
			),
			ar.scheduled_at,
			ar.started_at,
			ar.ended_at,
			COALESCE(ar.scheduled_at, ar.created_at)
		FROM auction_room ar
		JOIN product p ON p.product_id = ar.product_id
		LEFT JOIN auction_result result
		  ON result.auction_room_id = ar.auction_room_id
		LEFT JOIN auction_participant winner_participant
		  ON winner_participant.auction_participant_id =
			result.winner_participant_id
		LEFT JOIN member winner
		  ON winner.member_id = winner_participant.member_id
		LEFT JOIN deal d ON d.auction_result_id = result.auction_result_id
		WHERE p.seller_member_id = :sellerId
		  AND ar.deleted_at IS NULL
		""";

	private static final String ORDER_BY = """
		ORDER BY COALESCE(ar.scheduled_at, ar.created_at) DESC,
			ar.auction_room_id DESC
		""";

	private final EntityManager entityManager;

	public JpaHostedAuctionHistoryQueryAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public HostedAuctionHistoryPage find(HostedAuctionHistoryCriteria criteria) {
		String cursorCondition = criteria.cursor() == null
			? ""
			: """

				AND (
					COALESCE(ar.scheduled_at, ar.created_at),
					ar.auction_room_id
				) < (
					CAST(:cursorSortedAt AS TIMESTAMPTZ),
					:cursorAuctionId
				)
				""";
		Query query = entityManager.createNativeQuery(
			SELECT_HISTORY + cursorCondition + ORDER_BY
		);
		query.setParameter("sellerId", criteria.sellerId());
		if (criteria.cursor() != null) {
			query.setParameter(
				"cursorSortedAt",
				criteria.cursor().sortedAt().toString()
			);
			query.setParameter(
				"cursorAuctionId",
				criteria.cursor().auctionId()
			);
		}
		query.setMaxResults(criteria.size() + 1);

		List<HostedAuctionHistoryRow> rows = new ArrayList<>(
			query.getResultList().stream()
				.map(JpaHostedAuctionHistoryQueryAdapter::toRow)
				.toList()
		);
		boolean hasNext = rows.size() > criteria.size();
		if (hasNext) {
			rows = rows.subList(0, criteria.size());
		}
		String nextCursor = hasNext
			? cursorOf(rows.get(rows.size() - 1)).encode()
			: null;
		return new HostedAuctionHistoryPage(rows, nextCursor, hasNext);
	}

	private static HostedAuctionHistoryRow toRow(Object value) {
		Object[] row = (Object[]) value;
		return new HostedAuctionHistoryRow(
			toLong(row[0]),
			toNullableLong(row[1]),
			(String) row[2],
			(String) row[3],
			(String) row[4],
			(String) row[5],
			(String) row[6],
			toNullableLong(row[7]),
			(String) row[8],
			(String) row[9],
			toLong(row[10]),
			toLong(row[11]),
			toLong(row[12]),
			toInstant(row[13]),
			toInstant(row[14]),
			toInstant(row[15]),
			toInstant(row[16])
		);
	}

	private static HostedAuctionHistoryCursor cursorOf(
		HostedAuctionHistoryRow row
	) {
		return new HostedAuctionHistoryCursor(row.sortedAt(), row.auctionId());
	}

	private static long toLong(Object value) {
		return ((Number) value).longValue();
	}

	private static Long toNullableLong(Object value) {
		return value == null ? null : toLong(value);
	}

	private static Instant toInstant(Object value) {
		return switch (value) {
			case null -> null;
			case Instant instant -> instant;
			case Timestamp timestamp -> timestamp.toInstant();
			case OffsetDateTime offsetDateTime -> offsetDateTime.toInstant();
			default -> throw new IllegalStateException(
				"지원하지 않는 시각 타입: " + value.getClass()
			);
		};
	}
}
