package com.ssafy.bidlive.deal.infrastructure.persistence;

import java.util.List;
import java.util.OptionalLong;

import com.ssafy.bidlive.deal.application.AuctionDealCreationPersistence;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/** SOLD 결과의 당사자와 최종가를 읽어 초기 에스크로 거래를 멱등 생성한다. */
@Repository
public class JpaAuctionDealCreationAdapter
	implements AuctionDealCreationPersistence {

	private static final String CREATE_OR_FIND = """
		WITH inserted AS (
			INSERT INTO deal (
				auction_result_id,
				seller_member_id,
				buyer_member_id,
				final_price,
				escrow_amount,
				escrow_status,
				deal_status
			)
			SELECT
				result.auction_result_id,
				product.seller_member_id,
				winner.member_id,
				result.final_price,
				result.final_price,
				'HELD',
				'WAITING_ADDRESS'
			FROM auction_result result
			JOIN auction_room room
				ON room.auction_room_id = result.auction_room_id
			JOIN product ON product.product_id = room.product_id
			JOIN auction_participant winner
				ON winner.auction_participant_id =
					result.winner_participant_id
			WHERE result.auction_result_id = :auctionResultId
				AND result.result_status = 'SOLD'
				AND result.final_price IS NOT NULL
			ON CONFLICT (auction_result_id) DO NOTHING
			RETURNING deal_id
		)
		SELECT deal_id FROM inserted
		UNION ALL
		SELECT deal.deal_id
		FROM deal
		WHERE deal.auction_result_id = :auctionResultId
			AND NOT EXISTS (SELECT 1 FROM inserted)
		LIMIT 1
		""";

	private final EntityManager entityManager;

	public JpaAuctionDealCreationAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public OptionalLong createOrFind(long auctionResultId) {
		List<?> rows = entityManager.createNativeQuery(CREATE_OR_FIND)
			.setParameter("auctionResultId", auctionResultId)
			.getResultList();
		if (rows.isEmpty()) {
			return OptionalLong.empty();
		}
		return OptionalLong.of(((Number) rows.getFirst()).longValue());
	}
}
