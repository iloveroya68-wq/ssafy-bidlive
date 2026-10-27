package com.ssafy.bidlive.auction.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.ssafy.bidlive.auction.application.ParticipatedAuctionHistoryCriteria;
import com.ssafy.bidlive.auction.application.ParticipatedAuctionHistoryPage;
import com.ssafy.bidlive.auction.application.ParticipatedAuctionHistoryPersistence;
import com.ssafy.bidlive.auction.application.ParticipatedAuctionHistoryRow;
import com.ssafy.bidlive.auction.domain.ParticipatedAuctionHistoryCursor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

/** 경매·입찰·예측 결과를 조합해 회원의 참여 경매 이력을 조회한다. */
@Repository
public class JpaParticipatedAuctionHistoryQueryAdapter
	implements ParticipatedAuctionHistoryPersistence {

	private static final String SELECT_HISTORY = """
		SELECT
			ar.auction_room_id,
			participant.participation_type,
			product.title,
			(
				SELECT image.image_url
				FROM product_image image
				WHERE image.product_id = product.product_id
				  AND image.moderation_status <> 'BLOCKED'
				ORDER BY image.is_thumbnail DESC, image.sort_order ASC
				LIMIT 1
			),
			ar.status,
			auction_result.result_status,
			auction_result.termination_reason,
			auction_result.final_price,
			(
				SELECT member_bid.amount
				FROM bid member_bid
				WHERE member_bid.auction_participant_id =
					participant.auction_participant_id
				ORDER BY member_bid.sequence_no DESC
				LIMIT 1
			),
			prediction.predicted_price,
			prediction.stake_amount,
			prediction_result.result_status,
			prediction_result.error_rate,
			prediction_result.reward_multiplier,
			prediction_result.payout_amount,
			ar.ended_at,
			COALESCE(
				ar.ended_at,
				TIMESTAMPTZ '9999-12-31 23:59:59+00'
			)
		FROM auction_participant participant
		JOIN auction_room ar
		  ON ar.auction_room_id = participant.auction_room_id
		JOIN product product ON product.product_id = ar.product_id
		LEFT JOIN auction_result auction_result
		  ON auction_result.auction_room_id = ar.auction_room_id
		LEFT JOIN prediction prediction
		  ON prediction.auction_participant_id =
			participant.auction_participant_id
		LEFT JOIN prediction_result prediction_result
		  ON prediction_result.prediction_id = prediction.prediction_id
		WHERE participant.member_id = :memberId
		  AND (
			(
				participant.participation_type = 'BIDDER'
				AND EXISTS (
					SELECT 1
					FROM bid participation_bid
					WHERE participation_bid.auction_participant_id =
						participant.auction_participant_id
				)
			)
			OR (
				participant.participation_type = 'PREDICTOR'
				AND prediction.prediction_id IS NOT NULL
			)
		  )
		  AND (
			auction_result.result_status IS NULL
			OR auction_result.result_status <> 'SOLD'
			OR auction_result.winner_participant_id <>
				participant.auction_participant_id
		  )
		""";

	private static final String ORDER_BY = """
		ORDER BY COALESCE(
				ar.ended_at,
				TIMESTAMPTZ '9999-12-31 23:59:59+00'
			) DESC,
			ar.auction_room_id DESC
		""";

	private final EntityManager entityManager;

	public JpaParticipatedAuctionHistoryQueryAdapter(
		EntityManager entityManager
	) {
		this.entityManager = entityManager;
	}

	@Override
	public ParticipatedAuctionHistoryPage find(
		ParticipatedAuctionHistoryCriteria criteria
	) {
		String cursorCondition = criteria.cursor() == null
			? ""
			: """

				AND (
					COALESCE(
						ar.ended_at,
						TIMESTAMPTZ '9999-12-31 23:59:59+00'
					),
					ar.auction_room_id
				) < (
					CAST(:cursorSortedAt AS TIMESTAMPTZ),
					:cursorAuctionId
				)
				""";
		Query query = entityManager.createNativeQuery(
			SELECT_HISTORY + cursorCondition + ORDER_BY
		);
		query.setParameter("memberId", criteria.memberId());
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

		List<ParticipatedAuctionHistoryRow> rows = new ArrayList<>(
			query.getResultList().stream()
				.map(JpaParticipatedAuctionHistoryQueryAdapter::toRow)
				.toList()
		);
		boolean hasNext = rows.size() > criteria.size();
		if (hasNext) {
			rows = rows.subList(0, criteria.size());
		}
		String nextCursor = hasNext
			? cursorOf(rows.get(rows.size() - 1)).encode()
			: null;
		return new ParticipatedAuctionHistoryPage(rows, nextCursor, hasNext);
	}

	private static ParticipatedAuctionHistoryRow toRow(Object value) {
		Object[] row = (Object[]) value;
		return new ParticipatedAuctionHistoryRow(
			toLong(row[0]),
			(String) row[1],
			(String) row[2],
			(String) row[3],
			(String) row[4],
			(String) row[5],
			(String) row[6],
			toNullableLong(row[7]),
			toNullableLong(row[8]),
			toNullableLong(row[9]),
			toNullableLong(row[10]),
			(String) row[11],
			toBigDecimal(row[12]),
			toBigDecimal(row[13]),
			toNullableLong(row[14]),
			toInstant(row[15]),
			toInstant(row[16])
		);
	}

	private static ParticipatedAuctionHistoryCursor cursorOf(
		ParticipatedAuctionHistoryRow row
	) {
		return new ParticipatedAuctionHistoryCursor(
			row.sortedAt(),
			row.auctionId()
		);
	}

	private static long toLong(Object value) {
		return ((Number) value).longValue();
	}

	private static Long toNullableLong(Object value) {
		return value == null ? null : toLong(value);
	}

	private static BigDecimal toBigDecimal(Object value) {
		return value == null ? null : new BigDecimal(value.toString());
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
