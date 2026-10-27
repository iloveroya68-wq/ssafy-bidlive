package com.ssafy.bidlive.auction.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.ssafy.bidlive.auction.application.AuctionExtensionPersistence;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** Redis가 확정한 연장 횟수를 기준으로 PostgreSQL 마감 상태를 단조 증가시킨다. */
@Repository
public class JpaAuctionExtensionPersistenceAdapter
	implements AuctionExtensionPersistence {

	private static final String SYNCHRONIZE_FINAL_STATE = """
		UPDATE auction_room
		SET ends_at = CAST(:endAt AS TIMESTAMPTZ),
			extension_count = :extensionCount
		WHERE auction_room_id = :auctionId
			AND extension_count <= :extensionCount
		""";

	private static final String FIND_EXTENSION_COUNT = """
		SELECT extension_count
		FROM auction_room
		WHERE auction_room_id = :auctionId
		""";

	private final EntityManager entityManager;

	public JpaAuctionExtensionPersistenceAdapter(
		EntityManager entityManager
	) {
		this.entityManager = Objects.requireNonNull(
			entityManager,
			"entityManager"
		);
	}

	@Override
	public void synchronizeFinalState(
		long auctionId,
		Instant endAt,
		int extensionCount
	) {
		if (auctionId <= 0) {
			throw new IllegalArgumentException("auctionId must be positive");
		}
		Objects.requireNonNull(endAt, "endAt");
		if (extensionCount < 1 || extensionCount > 3) {
			throw new IllegalArgumentException(
				"extensionCount must be between one and three"
			);
		}

		int updated = entityManager.createNativeQuery(
				SYNCHRONIZE_FINAL_STATE
			)
			.setParameter("endAt", endAt.toString())
			.setParameter("extensionCount", extensionCount)
			.setParameter("auctionId", auctionId)
			.executeUpdate();
		if (updated == 1) {
			return;
		}

		List<?> counts = entityManager.createNativeQuery(
				FIND_EXTENSION_COUNT
			)
			.setParameter("auctionId", auctionId)
			.getResultList();
		if (counts.isEmpty()) {
			throw new IllegalStateException(
				"PostgreSQL auction room is missing: " + auctionId
			);
		}
		int persistedCount = ((Number) counts.getFirst()).intValue();
		if (persistedCount < extensionCount) {
			throw new IllegalStateException(
				"PostgreSQL did not synchronize auction extension state: "
					+ auctionId
			);
		}
	}
}
