package com.ssafy.bidlive.auction.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import com.ssafy.bidlive.auction.application.AuctionRoomDetailPersistence;
import com.ssafy.bidlive.auction.application.AuctionRoomDetailRow;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** PostgreSQL에서 경매방·상품·판매자와 활성 알림 신청 수를 한 번에 조회합니다. */
@Repository
public class JpaAuctionRoomDetailQueryAdapter
	implements AuctionRoomDetailPersistence {

	private static final String SELECT_DETAIL = """
		SELECT
			ar.auction_room_id,
			p.seller_member_id,
			p.title,
			ar.status,
			ar.reserve_price,
			ar.scheduled_at,
			ar.duration_minutes,
			ar.visibility,
			ar.deleted_at,
			p.product_id,
			p.title,
			p.description,
			pc.code,
			(
				SELECT image.image_url
				FROM product_image image
				WHERE image.product_id = p.product_id
				  AND image.moderation_status <> 'BLOCKED'
				ORDER BY image.is_thumbnail DESC, image.sort_order ASC
				LIMIT 1
			),
			p.moderation_status,
			seller.nickname,
			(
				SELECT COUNT(*)
				FROM auction_room_notification_subscription subscription
				WHERE subscription.auction_room_id = ar.auction_room_id
				  AND subscription.is_active = TRUE
			)
		FROM auction_room ar
		JOIN product p ON p.product_id = ar.product_id
		JOIN product_category pc ON pc.category_id = p.category_id
		JOIN member seller ON seller.member_id = p.seller_member_id
		WHERE ar.auction_room_id = :auctionId
		""";

	private final EntityManager entityManager;

	public JpaAuctionRoomDetailQueryAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public Optional<AuctionRoomDetailRow> findByAuctionId(long auctionId) {
		return entityManager.createNativeQuery(SELECT_DETAIL)
			.setParameter("auctionId", auctionId)
			.getResultList()
			.stream()
			.findFirst()
			.map(JpaAuctionRoomDetailQueryAdapter::toRow);
	}

	private static AuctionRoomDetailRow toRow(Object value) {
		Object[] row = (Object[]) value;
		return new AuctionRoomDetailRow(
			((Number) row[0]).longValue(),
			((Number) row[1]).longValue(),
			(String) row[2],
			AuctionStatus.valueOf((String) row[3]),
			((Number) row[4]).longValue(),
			toInstant(row[5]),
			((Number) row[6]).intValue(),
			(String) row[7],
			toInstant(row[8]),
			((Number) row[9]).longValue(),
			(String) row[10],
			(String) row[11],
			(String) row[12],
			(String) row[13],
			(String) row[14],
			(String) row[15],
			Math.toIntExact(((Number) row[16]).longValue())
		);
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
