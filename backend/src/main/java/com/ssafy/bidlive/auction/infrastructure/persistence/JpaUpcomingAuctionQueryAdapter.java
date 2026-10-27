package com.ssafy.bidlive.auction.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import com.ssafy.bidlive.auction.application.UpcomingAuctionPersistence;
import com.ssafy.bidlive.auction.application.UpcomingAuctionRow;
import com.ssafy.bidlive.auction.application.UpcomingAuctionSnapshot;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** PostgreSQL에서 한 시간 이내 시작 예정인 공개 대기 경매방을 조회합니다. */
@Repository
public class JpaUpcomingAuctionQueryAdapter
	implements UpcomingAuctionPersistence {

	private static final String PUBLIC_UPCOMING_CONDITIONS = """
		ar.status = 'WAITING'
		AND ar.scheduled_at > :startsAfter
		AND ar.scheduled_at <= :startsAtOrBefore
		AND ar.visibility = 'PUBLIC'
		AND ar.deleted_at IS NULL
		AND p.moderation_status <> 'BLOCKED'
		""";

	private static final String SELECT_UPCOMING = """
		SELECT
			ar.auction_room_id,
			p.title,
			(
				SELECT image.image_url
				FROM product_image image
				WHERE image.product_id = p.product_id
				  AND image.moderation_status <> 'BLOCKED'
				ORDER BY image.is_thumbnail DESC, image.sort_order ASC
				LIMIT 1
			),
			ar.scheduled_at,
			ar.status
		FROM auction_room ar
		JOIN product p ON p.product_id = ar.product_id
		WHERE
		""" + PUBLIC_UPCOMING_CONDITIONS + """
		ORDER BY ar.scheduled_at ASC, ar.auction_room_id ASC
		""";

	private static final String COUNT_UPCOMING = """
		SELECT COUNT(*)
		FROM auction_room ar
		JOIN product p ON p.product_id = ar.product_id
		WHERE
		""" + PUBLIC_UPCOMING_CONDITIONS;

	private final EntityManager entityManager;

	public JpaUpcomingAuctionQueryAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public UpcomingAuctionSnapshot findUpcoming(
		Instant startsAfter,
		Instant startsAtOrBefore,
		int limit
	) {
		List<UpcomingAuctionRow> rows = entityManager
			.createNativeQuery(SELECT_UPCOMING)
			.setParameter("startsAfter", startsAfter)
			.setParameter("startsAtOrBefore", startsAtOrBefore)
			.setMaxResults(limit)
			.getResultList()
			.stream()
			.map(JpaUpcomingAuctionQueryAdapter::toRow)
			.toList();

		long totalCount = ((Number) entityManager
			.createNativeQuery(COUNT_UPCOMING)
			.setParameter("startsAfter", startsAfter)
			.setParameter("startsAtOrBefore", startsAtOrBefore)
			.getSingleResult())
			.longValue();

		return new UpcomingAuctionSnapshot(rows, totalCount);
	}

	private static UpcomingAuctionRow toRow(Object value) {
		Object[] row = (Object[]) value;
		return new UpcomingAuctionRow(
			((Number) row[0]).longValue(),
			(String) row[1],
			(String) row[2],
			toInstant(row[3]),
			AuctionStatus.valueOf((String) row[4])
		);
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
