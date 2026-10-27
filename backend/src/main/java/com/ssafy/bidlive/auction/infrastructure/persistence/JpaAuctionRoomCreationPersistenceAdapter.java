package com.ssafy.bidlive.auction.infrastructure.persistence;

import java.time.OffsetDateTime;

import com.ssafy.bidlive.auction.application.AuctionRoomCreationPersistence;
import com.ssafy.bidlive.auction.domain.AuctionStartType;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** 검증된 생성 정보를 PostgreSQL auction_room 행으로 저장합니다. */
@Repository
public class JpaAuctionRoomCreationPersistenceAdapter
	implements AuctionRoomCreationPersistence {

	private static final String AUCTION_ROOM_INSERT_QUERY = """
		INSERT INTO auction_room (
			product_id,
			reserve_price,
			duration_minutes,
			start_type,
			scheduled_at,
			status,
			created_at,
			updated_at
		)
		VALUES (
			:productId,
			:reservePrice,
			:durationMinutes,
			:startType,
			:scheduledAt,
			:status,
			:createdAt,
			:createdAt
		)
		RETURNING auction_room_id
		""";

	private final EntityManager entityManager;

	public JpaAuctionRoomCreationPersistenceAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public Long save(
		Long productId,
		long reservePrice,
		int durationMinutes,
		AuctionStartType startType,
		OffsetDateTime scheduledAt,
		AuctionStatus status,
		OffsetDateTime createdAt
	) {
		Number auctionId = (Number) entityManager.createNativeQuery(
				AUCTION_ROOM_INSERT_QUERY)
			.setParameter("productId", productId)
			.setParameter("reservePrice", reservePrice)
			.setParameter("durationMinutes", durationMinutes)
			.setParameter("startType", startType.name())
			.setParameter("scheduledAt", scheduledAt)
			.setParameter("status", status.name())
			.setParameter("createdAt", createdAt)
			.getSingleResult();
		return auctionId.longValue();
	}
}
