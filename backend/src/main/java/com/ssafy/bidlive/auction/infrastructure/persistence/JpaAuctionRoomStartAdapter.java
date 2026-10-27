package com.ssafy.bidlive.auction.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.auction.application.AuctionRoomStartPersistence;
import com.ssafy.bidlive.auction.application.AuctionRoomStartTarget;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** 방송 시작 판정에 필요한 값을 읽고, 대기 중인 방만 조건부로 `LIVE`로 전이한다. */
@Repository
public class JpaAuctionRoomStartAdapter implements AuctionRoomStartPersistence {

	private static final String FIND_TARGET = """
		SELECT
			ar.auction_room_id,
			p.seller_member_id,
			ar.status,
			ar.visibility,
			(ar.deleted_at IS NOT NULL),
			ar.reserve_price,
			ar.duration_minutes,
			p.moderation_status
		FROM auction_room ar
		JOIN product p ON p.product_id = ar.product_id
		WHERE ar.auction_room_id = :auctionId
		""";

	/**
	 * `status = 'WAITING'` 조건이 중복 시작 방지의 핵심이다. 판정과 전이 사이에 다른 요청이 먼저
	 * 시작하면 갱신 행이 0이 되고, 서비스가 그것을 `AUCTION_ALREADY_STARTED`로 읽는다.
	 */
	private static final String MARK_STARTED = """
		UPDATE auction_room
		SET status = :liveStatus,
			started_at = CAST(:startedAt AS TIMESTAMPTZ),
			ends_at = CAST(:endAt AS TIMESTAMPTZ)
		WHERE auction_room_id = :auctionId
			AND status = :waitingStatus
			AND deleted_at IS NULL
		""";

	private final EntityManager entityManager;

	public JpaAuctionRoomStartAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public Optional<AuctionRoomStartTarget> findStartTarget(long auctionId) {
		List<?> rows = entityManager.createNativeQuery(FIND_TARGET)
			.setParameter("auctionId", auctionId)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Object[] row = (Object[]) rows.getFirst();
		return Optional.of(new AuctionRoomStartTarget(
			((Number) row[0]).longValue(),
			((Number) row[1]).longValue(),
			(String) row[2],
			(String) row[3],
			(Boolean) row[4],
			((Number) row[5]).longValue(),
			((Number) row[6]).intValue(),
			(String) row[7]
		));
	}

	@Override
	public int markStarted(long auctionId, Instant startedAt, Instant endAt) {
		return entityManager.createNativeQuery(MARK_STARTED)
			.setParameter("liveStatus", AuctionStatus.LIVE.name())
			.setParameter("startedAt", startedAt.toString())
			.setParameter("endAt", endAt.toString())
			.setParameter("auctionId", auctionId)
			.setParameter("waitingStatus", AuctionStatus.WAITING.name())
			.executeUpdate();
	}
}
