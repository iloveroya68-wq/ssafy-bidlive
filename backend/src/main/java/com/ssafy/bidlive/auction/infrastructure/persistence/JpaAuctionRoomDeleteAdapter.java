package com.ssafy.bidlive.auction.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.auction.application.AuctionRoomDeletePersistence;
import com.ssafy.bidlive.auction.application.AuctionRoomDeletionTarget;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** 경매방 삭제 판정에 필요한 사실을 한 번에 읽고, 논리 삭제를 표시한다. */
@Repository
public class JpaAuctionRoomDeleteAdapter implements AuctionRoomDeletePersistence {

	private static final String FIND_TARGET = """
		SELECT
			ar.auction_room_id,
			p.seller_member_id,
			ar.status,
			ar.visibility,
			(ar.deleted_at IS NOT NULL),
			EXISTS (
				SELECT 1
				FROM auction_participant ap
				WHERE ap.auction_room_id = ar.auction_room_id
			)
		FROM auction_room ar
		JOIN product p ON p.product_id = ar.product_id
		WHERE ar.auction_room_id = :auctionId
		""";

	private static final String MARK_DELETED = """
		UPDATE auction_room
		SET deleted_at = NOW()
		WHERE auction_room_id = :auctionId
			AND deleted_at IS NULL
		""";

	private final EntityManager entityManager;

	public JpaAuctionRoomDeleteAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public Optional<AuctionRoomDeletionTarget> findDeletionTarget(long auctionId) {
		List<?> rows = entityManager.createNativeQuery(FIND_TARGET)
			.setParameter("auctionId", auctionId)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Object[] row = (Object[]) rows.getFirst();
		return Optional.of(new AuctionRoomDeletionTarget(
			((Number) row[0]).longValue(),
			((Number) row[1]).longValue(),
			(String) row[2],
			(String) row[3],
			(Boolean) row[4],
			(Boolean) row[5]
		));
	}

	@Override
	public int markDeleted(long auctionId) {
		return entityManager.createNativeQuery(MARK_DELETED)
			.setParameter("auctionId", auctionId)
			.executeUpdate();
	}
}
