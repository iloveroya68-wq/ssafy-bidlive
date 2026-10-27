package com.ssafy.bidlive.auction.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.auction.application.AuctionForceEndPersistence;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import com.ssafy.bidlive.auction.domain.AuctionTerminationReason;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** 강제 종료 대상을 조건부로 `ENDED`로 전이하고 결과 행·미해제 홀드를 다룬다. */
@Repository
public class JpaAuctionForceEndAdapter implements AuctionForceEndPersistence {

	/**
	 * 판매자 판정에 쓸 `seller_member_id`는 `product`에 있다 — 방송 시작(ROOM-05) 어댑터와 같은 조인이다.
	 * 운영 강제 종료는 이 값을 보지 않지만, 두 종료가 같은 대상 조회를 쓰므로 함께 읽는다.
	 */
	private static final String FIND_TARGET = """
		SELECT
			ar.auction_room_id,
			p.seller_member_id,
			ar.status,
			(ar.deleted_at IS NOT NULL)
		FROM auction_room ar
		JOIN product p ON p.product_id = ar.product_id
		WHERE ar.auction_room_id = :auctionId
		""";

	/**
	 * `status IN ('WAITING','LIVE')`가 중복 종료 방지의 핵심이다. 이미 `ENDED`면 0행이 되고
	 * 서비스가 그것을 「이미 종료됨」으로 읽는다. 삭제된 방도 여기서 걸러진다.
	 *
	 * <p>⚠️ <b>`ended_at`이지 `ends_at`이 아니다.</b> 둘은 다른 컬럼이다 —
	 * `ends_at`은 <b>예정</b> 마감(ROOM-05가 `startedAt + duration`으로 채운다)이고
	 * `ended_at`은 <b>실제</b> 종료 시각이다. 바꿔 쓰면 두 가지가 한꺼번에 깨진다.
	 * <ul>
	 *   <li>`ended_at`이 NULL로 남아 참여·주최·낙찰 이력 어댑터의
	 *       `COALESCE(ar.ended_at, '9999-12-31')` 정렬에서 <b>센티넬 자리로 가 목록 맨 위에
	 *       영구 고정</b>되고, 같은 식을 쓰는 커서 페이징도 함께 어긋난다</li>
	 *   <li>예정 마감이 실제 종료 시각으로 덮여 사라진다</li>
	 * </ul>
	 * 목록 응답의 `endedAt`도 「종료 전에는 null」이 계약이라 프론트가 안 끝난 방으로 읽는다.
	 */
	private static final String MARK_ENDED = """
		UPDATE auction_room
		SET status = :endedStatus,
			ended_at = CAST(:endedAt AS TIMESTAMPTZ)
		WHERE auction_room_id = :auctionId
			AND status IN (:waitingStatus, :liveStatus)
			AND deleted_at IS NULL
		""";

	/**
	 * `auction_room_id`가 UNIQUE라 `DO NOTHING`이 재실행을 흡수한다.
	 * `RETURNING`은 삽입된 행에만 나오므로, 없으면 아래 SELECT로 기존 id를 찾는다.
	 */
	private static final String INSERT_RESULT = """
		INSERT INTO auction_result (
			auction_room_id,
			result_status,
			termination_reason,
			valid_bidder_count,
			total_bid_count
		)
		VALUES (
			:auctionId,
			:resultStatus,
			:terminationReason,
			:validBidderCount,
			:totalBidCount
		)
		ON CONFLICT (auction_room_id) DO NOTHING
		RETURNING auction_result_id
		""";

	private static final String FIND_RESULT_ID = """
		SELECT auction_result_id
		FROM auction_result
		WHERE auction_room_id = :auctionId
		""";

	private static final String COUNT_BIDS = """
		SELECT
			COUNT(DISTINCT b.auction_participant_id),
			COUNT(*)
		FROM bid b
		JOIN auction_participant ap
			ON ap.auction_participant_id = b.auction_participant_id
		WHERE ap.auction_room_id = :auctionId
		""";

	/**
	 * 이 경매의 입찰 홀드 잔액을 입찰 단위로 합산한다. 합계가 0 이하면 이미 풀렸거나 정산된
	 * 것이라 제외한다 — `TradePointOperationService.release`가 `HOLD_NOT_FOUND`로 던지는 자리다.
	 *
	 * <p>입찰이 Postgres에 남기 시작한 뒤에야 성립하는 질의다(S15P11A704-402). 그 전 데이터는
	 * `bid` 행이 없어 홀드도 잡히지 않는다.
	 */
	private static final String FIND_OUTSTANDING_HOLDS = """
		SELECT
			b.bid_id,
			w.member_id,
			SUM(tpl.held_delta)
		FROM trade_point_ledger tpl
		JOIN wallet w ON w.wallet_id = tpl.wallet_id
		JOIN bid b ON b.bid_id = tpl.reference_id
		JOIN auction_participant ap
			ON ap.auction_participant_id = b.auction_participant_id
		WHERE tpl.reference_type = 'BID'
			AND tpl.transaction_type IN ('HOLD', 'RELEASE', 'SETTLEMENT')
			AND ap.auction_room_id = :auctionId
		GROUP BY b.bid_id, w.member_id
		HAVING SUM(tpl.held_delta) > 0
		ORDER BY b.bid_id
		""";

	private final EntityManager entityManager;

	public JpaAuctionForceEndAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public Optional<ForceEndTarget> findTarget(long auctionId) {
		List<?> rows = entityManager.createNativeQuery(FIND_TARGET)
			.setParameter("auctionId", auctionId)
			.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Object[] row = (Object[]) rows.getFirst();
		return Optional.of(new ForceEndTarget(
			((Number) row[0]).longValue(),
			((Number) row[1]).longValue(),
			(String) row[2],
			(Boolean) row[3]
		));
	}

	@Override
	public int markEnded(long auctionId, Instant endedAt) {
		return entityManager.createNativeQuery(MARK_ENDED)
			.setParameter("endedStatus", AuctionStatus.ENDED.name())
			.setParameter("endedAt", endedAt.toString())
			.setParameter("auctionId", auctionId)
			.setParameter("waitingStatus", AuctionStatus.WAITING.name())
			.setParameter("liveStatus", AuctionStatus.LIVE.name())
			.executeUpdate();
	}

	@Override
	public long createResultIfAbsent(
		long auctionId,
		ResultCounts counts,
		AuctionTerminationReason reason
	) {
		List<?> inserted = entityManager.createNativeQuery(INSERT_RESULT)
			.setParameter("auctionId", auctionId)
			.setParameter("resultStatus", reason.resultStatus())
			.setParameter("terminationReason", reason.terminationReason())
			.setParameter("validBidderCount", counts.validBidderCount())
			.setParameter("totalBidCount", counts.totalBidCount())
			.getResultList();
		if (!inserted.isEmpty()) {
			return ((Number) inserted.getFirst()).longValue();
		}
		return ((Number) entityManager.createNativeQuery(FIND_RESULT_ID)
			.setParameter("auctionId", auctionId)
			.getSingleResult()).longValue();
	}

	@Override
	public ResultCounts countBids(long auctionId) {
		Object[] row = (Object[]) entityManager.createNativeQuery(COUNT_BIDS)
			.setParameter("auctionId", auctionId)
			.getSingleResult();
		return new ResultCounts(
			((Number) row[0]).intValue(),
			((Number) row[1]).intValue()
		);
	}

	@Override
	public List<OutstandingBidHold> findOutstandingBidHolds(long auctionId) {
		List<?> rows = entityManager.createNativeQuery(FIND_OUTSTANDING_HOLDS)
			.setParameter("auctionId", auctionId)
			.getResultList();
		return rows.stream()
			.map(Object[].class::cast)
			.map(row -> new OutstandingBidHold(
				((Number) row[0]).longValue(),
				((Number) row[1]).longValue(),
				((Number) row[2]).longValue()
			))
			.toList();
	}
}
